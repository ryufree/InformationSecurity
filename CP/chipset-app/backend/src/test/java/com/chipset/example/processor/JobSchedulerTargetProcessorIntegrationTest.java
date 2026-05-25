package com.chipset.example.processor;

import com.chipset.example.annotation.JobSchedulerTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * JobSchedulerTargetProcessor 통합 테스트
 *
 * [단위 테스트와의 차이]
 *   단위 테스트 : SysConfigService 를 Mock → 값이 DB 에서 오는지 보장 안 됨
 *   통합 테스트 : SysConfigService 가 실제 H2 DB 를 읽음 → DB 연동 검증
 *
 * [검증 흐름]
 *   H2 DB INSERT → SysConfigService(real) → Runnable → 배치 메서드 실행/skip
 *
 * [TaskScheduler 를 MockBean 으로 쓰는 이유]
 *   실제 스케줄러를 쓰면 cron 시간이 될 때까지 기다려야 함.
 *   Mock 으로 Runnable 을 바로 꺼내 즉시 실행 → 테스트를 결정적으로 만든다.
 */
@SpringBootTest
class JobSchedulerTargetProcessorIntegrationTest {

    @Autowired
    private JobSchedulerTargetProcessor processor;

    @MockBean                                       // 실제 스케줄링 방지, Runnable 캡처용
    private TaskScheduler taskScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ENABLED_KEY  = "maru.batch.mh.meeting.reminder.active";
    private static final String CRON_KEY     = "maru.batch.mh.meeting.reminder.cron";
    private static final String PROFILE      = "common";
    private static final String DEFAULT_CRON = "0 0/5 * * * ?";

    @BeforeEach
    void setUp() {
        // 컨텍스트 기동 시 발생한 startup 호출 초기화 → 테스트별 독립성 확보
        Mockito.reset(taskScheduler);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM SYS_CONFIG WHERE PROP_KEY IN (?, ?)", ENABLED_KEY, CRON_KEY);
    }

    // ── enabled 통합 테스트 ────────────────────────────────────────────

    @Test
    @DisplayName("DB active=true → 실제 DB 읽어 배치 실행됨")
    void enabled_true_실제DB에서_읽어_실행됨() {
        insertEnabled("true");
        insertCron(DEFAULT_CRON);

        TestBatch bean = new TestBatch();
        processor.postProcessAfterInitialization(bean, "testBatch");

        // TaskScheduler 에 등록된 Runnable 꺼내기
        ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(cap.capture(), any(Trigger.class));

        // Runnable 즉시 실행 → 내부에서 실제 H2 DB 조회 (active=true)
        cap.getValue().run();

        assertThat(bean.executed).isTrue();
    }

    @Test
    @DisplayName("DB active=false → 실제 DB 읽어 배치 skip")
    void enabled_false_실제DB에서_읽어_스킵됨() {
        insertEnabled("false");
        insertCron(DEFAULT_CRON);

        TestBatch bean = new TestBatch();
        processor.postProcessAfterInitialization(bean, "testBatch");

        ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(cap.capture(), any(Trigger.class));

        // Runnable 즉시 실행 → 내부에서 실제 H2 DB 조회 (active=false)
        cap.getValue().run();

        assertThat(bean.executed).isFalse();
    }

    // ── cron 통합 테스트 ───────────────────────────────────────────────

    @Test
    @DisplayName("DB cron 변경 → Trigger 가 새 cron 으로 다음 실행 시간 계산")
    void cron_변경값_실제DB에서_읽어_적용됨() {
        insertEnabled("true");
        insertCron("0 0 9 * * ?");     // 매일 09:00 으로 변경

        processor.postProcessAfterInitialization(new TestBatch(), "testBatch");

        // Trigger 꺼내기
        ArgumentCaptor<Trigger> triggerCap = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), triggerCap.capture());

        // getClock() 이 null 이면 CronTrigger 내부에서 NPE → Clock 명시 필요
        var mockContext = Mockito.mock(org.springframework.scheduling.TriggerContext.class);
        Mockito.when(mockContext.getClock()).thenReturn(Clock.systemDefaultZone());
        var nextTime = triggerCap.getValue().nextExecution(mockContext);

        assertThat(nextTime).isNotNull();   // cron 파싱 성공 + 다음 실행 시간 계산됨
    }

    // ── 테스트용 배치 빈 ───────────────────────────────────────────────

    static class TestBatch {
        boolean executed = false;

        @JobSchedulerTarget(
            enabled = "${maru.batch.mh.meeting.reminder.active}",
            cron    = "${maru.batch.mh.meeting.reminder.cron}"
        )
        public void run() {
            executed = true;
        }
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────

    private void insertEnabled(String value) {
        jdbcTemplate.update(
            "INSERT INTO SYS_CONFIG (PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, USE_YN) VALUES (?,?,?,?,?,?,?)",
            PROFILE, ENABLED_KEY, value, "BOOLEAN", "Y", "N", "Y"
        );
    }

    private void insertCron(String cron) {
        jdbcTemplate.update(
            "INSERT INTO SYS_CONFIG (PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, USE_YN) VALUES (?,?,?,?,?,?,?)",
            PROFILE, CRON_KEY, cron, "CRON", "Y", "N", "Y"
        );
    }
}
