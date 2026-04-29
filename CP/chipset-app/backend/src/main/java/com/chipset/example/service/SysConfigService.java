package com.chipset.example.service;

import com.chipset.example.mapper.SysConfigMapper;
import com.chipset.example.model.SysConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DB에서 설정값을 읽는 핵심 서비스.
 *
 * [사용법 요약]
 *  - @Value 교체  : sysConfigService.getString("maru.email.sender", "default@example.com")
 *  - boolean flag : sysConfigService.getBoolean("maru.feature.new-ui-enabled", false)
 *  - 정수값       : sysConfigService.getInt("maru.upload.max-file-size-mb", 100)
 *  - 전체 Map     : sysConfigService.loadAsMap()  → PropertySource에 넣어 @Value 투명 교체 가능
 */
@Service
public class SysConfigService {

    private static final Logger log = LoggerFactory.getLogger(SysConfigService.class);

    @Autowired
    private SysConfigMapper sysConfigMapper;

    /** 현재 활성 프로파일 (없으면 common만 사용) */
    @Value("${spring.profiles.active:common}")
    private String activeProfile;

    // ── 기본 조회 (common fallback 자동 처리) ─────────────────────────

    /**
     * 키로 SysConfig 단건 조회.
     * 1) activeProfile에서 먼저 찾고, 없으면 common에서 찾는다.
     */
    public SysConfig get(String key) {
        SysConfig cfg = sysConfigMapper.selectByProfileAndKey(activeProfile, key);
        if (cfg == null) {
            cfg = sysConfigMapper.selectByProfileAndKey("common", key);
        }
        return cfg;
    }

    /** String 값 반환. 키 없으면 defaultValue */
    public String getString(String key, String defaultValue) {
        SysConfig cfg = get(key);
        return (cfg != null) ? cfg.asString(defaultValue) : defaultValue;
    }

    /** boolean 값 반환. 키 없으면 defaultValue */
    public boolean getBoolean(String key, boolean defaultValue) {
        SysConfig cfg = get(key);
        return (cfg != null) ? cfg.asBoolean() : defaultValue;
    }

    /** int 값 반환. 키 없으면 defaultValue */
    public int getInt(String key, int defaultValue) {
        SysConfig cfg = get(key);
        return (cfg != null) ? cfg.asInt(defaultValue) : defaultValue;
    }

    // ── 전체 Map 로딩 ──────────────────────────────────────────────────

    /**
     * 현재 프로파일 전체 설정을 Map으로 반환.
     * common → profile-specific 순으로 넣어 profile 값이 common을 override한다.
     * Pattern 3 (DbPropertySource) 에서 사용.
     */
    public Map<String, String> loadAsMap() {
        List<SysConfig> list = sysConfigMapper.selectByProfile(activeProfile);
        Map<String, String> map = new LinkedHashMap<>();
        // SQL에서 ORDER BY로 common 먼저 → 나중에 profile 값이 덮어씀
        for (SysConfig cfg : list) {
            map.put(cfg.getPropKey(), cfg.getPropValue());
        }
        log.debug("[SysConfig] {} 개 설정 로드 (profile={})", map.size(), activeProfile);
        return map;
    }

    // ── 런타임 변경 ────────────────────────────────────────────────────

    /**
     * 런타임 설정값 업데이트 (EDITABLE_YN='Y' 인 항목만 변경됨).
     * @return 업데이트 건수 (0이면 키 없거나 EDITABLE_YN='N')
     */
    public int updateValue(String key, String newValue) {
        int updated = sysConfigMapper.updateValue(activeProfile, key, newValue);
        if (updated > 0) {
            log.info("[SysConfig] 설정 변경: profile={}, key={}, value={}", activeProfile, key, newValue);
        } else {
            log.warn("[SysConfig] 변경 실패(키 없음 또는 EDITABLE_YN=N): key={}", key);
        }
        return updated;
    }
}
