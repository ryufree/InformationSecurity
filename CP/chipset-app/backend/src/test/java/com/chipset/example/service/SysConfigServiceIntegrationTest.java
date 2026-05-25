package com.chipset.example.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysConfigService 통합 테스트 — @DbValue 대역 검증
 *
 * [@DbValue 와의 관계]
 *   maru 프로젝트의 @DbValue 처리기는 내부적으로 SysConfigService.getBoolean() 을 호출한다.
 *   따라서 이 테스트가 통과 = @DbValue 가 DB 에서 값을 정상적으로 읽음을 의미한다.
 *
 * [검증 항목]
 *   1. DB PROP_VALUE = 'true'  → getBoolean() = true
 *   2. DB PROP_VALUE = 'false' → getBoolean() = false
 *   3. DB 에 키 없음           → defaultValue 반환
 */
@SpringBootTest
class SysConfigServiceIntegrationTest {

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String KEY     = "maru.batch.mh.meeting.reminder.active";
    private static final String PROFILE = "common";   // SysConfigService 기본 profile

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM SYS_CONFIG WHERE PROP_KEY = ?", KEY);
    }

    // ── @DbValue active=true 검증 ──────────────────────────────────────

    @Test
    @DisplayName("DB active=true → getBoolean() true 반환 (@DbValue enabled 검증)")
    void getBoolean_DB값_true_반환() {
        // H2 DB 에 직접 삽입 — @DbValue 가 읽을 데이터
        insert(KEY, "true");

        boolean result = sysConfigService.getBoolean(KEY, false);

        assertThat(result).isTrue();
    }

    // ── @DbValue active=false 검증 ─────────────────────────────────────

    @Test
    @DisplayName("DB active=false → getBoolean() false 반환 (@DbValue disabled 검증)")
    void getBoolean_DB값_false_반환() {
        insert(KEY, "false");

        boolean result = sysConfigService.getBoolean(KEY, true);  // defaultValue=true 이지만 DB 값 우선

        assertThat(result).isFalse();
    }

    // ── DB 키 없을 때 defaultValue fallback 검증 ───────────────────────

    @Test
    @DisplayName("DB 에 키 없음 → defaultValue 반환")
    void getBoolean_키없음_defaultValue_반환() {
        // DB 에 아무것도 삽입 안 함

        boolean result = sysConfigService.getBoolean("non.existent.key", true);

        assertThat(result).isTrue();   // defaultValue 반환
    }

    // ── String / int 타입 검증 ──────────────────────────────────────────

    @Test
    @DisplayName("DB CRON 값 → getString() 정확한 cron 표현식 반환")
    void getString_DB에서_cron_반환() {
        String cronKey  = "maru.batch.mh.meeting.reminder.cron";
        String cronExpr = "0 0/5 * * * ?";
        jdbcTemplate.update(
            "INSERT INTO SYS_CONFIG (PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, USE_YN) VALUES (?,?,?,?,?,?,?)",
            PROFILE, cronKey, cronExpr, "CRON", "Y", "N", "Y"
        );

        String result = sysConfigService.getString(cronKey, "default");

        assertThat(result).isEqualTo(cronExpr);

        jdbcTemplate.update("DELETE FROM SYS_CONFIG WHERE PROP_KEY = ?", cronKey);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────

    private void insert(String key, String value) {
        jdbcTemplate.update(
            "INSERT INTO SYS_CONFIG (PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, USE_YN) VALUES (?,?,?,?,?,?,?)",
            PROFILE, key, value, "BOOLEAN", "Y", "N", "Y"
        );
    }
}
