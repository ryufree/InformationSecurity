package com.chipset.example.processor;

import com.chipset.example.annotation.JobSchedulerTarget;
import com.chipset.example.service.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * JobSchedulerTargetProcessor 단위 테스트
 *
 * [테스트 전략]
 *   실제 스케줄러 없이 Runnable/Trigger 를 ArgumentCaptor 로 직접 꺼내
 *   즉시 실행함으로써 DB 조회 결과에 따른 동작을 검증한다.
 *
 * [검증 항목]
 *   1. enabled=true  → 배치 메서드 실행됨
 *   2. enabled=false → 배치 메서드 skip
 *   3. cron          → DB 값으로 다음 실행 시간 결정
 */
@ExtendWith(MockitoExtension.class)
class JobSchedulerTargetProcessorTest {

    @Mock    private SysConfigService sysConfigService;
    @Mock    private TaskScheduler    taskScheduler;
    @InjectMocks private JobSchedulerTargetProcessor processor;

    @Captor  private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor  private ArgumentCaptor<Trigger>  triggerCaptor;

    private static final String ENABLED_KEY  = "maru.batch.mh.meeting.reminder.active";
    private static final String CRON_KEY     = "maru.batch.mh.meeting.reminder.cron";
    private static final String DEFAULT_CRON = "0 0/5 * * * ?";

    // ── enabled 테스트 ─────────────────────────────────────────────────

    @Test
    @DisplayName("enabled=true → 배치 메서드 실행됨")
    void enabled_true_배치_실행() {
        // given
        TestBatch bean = new TestBatch();
        given(sysConfigService.getBoolean(ENABLED_KEY, false)).willReturn(true);

        // when: 프로세서가 태스크 등록
        processor.postProcessAfterInitialization(bean, "testBatch");

        // 등록된 Runnable 꺼내서 즉시 실행 (실제 스케줄러 불필요)
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run();

        // then
        assertThat(bean.executed).isTrue();
    }

    @Test
    @DisplayName("enabled=false → 배치 메서드 skip")
    void enabled_false_배치_스킵() {
        // given
        TestBatch bean = new TestBatch();
        given(sysConfigService.getBoolean(ENABLED_KEY, false)).willReturn(false);

        // when
        processor.postProcessAfterInitialization(bean, "testBatch");
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run();

        // then: 배치 메서드 호출 안됨
        assertThat(bean.executed).isFalse();
    }

    // ── cron 테스트 ────────────────────────────────────────────────────

    @Test
    @DisplayName("cron — DB에서 읽은 값으로 다음 실행 시간 결정")
    void cron_DB에서_동적_조회() {
        // given
        TestBatch bean = new TestBatch();
        String customCron = "0 0 9 * * ?";  // 매일 09:00 으로 변경
        given(sysConfigService.getString(CRON_KEY, DEFAULT_CRON)).willReturn(customCron);

        // when: 프로세서가 Trigger 등록
        processor.postProcessAfterInitialization(bean, "testBatch");
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());

        // Trigger 꺼내서 직접 nextExecution 호출
        TriggerContext mockContext = mock(TriggerContext.class);
        Instant nextTime = triggerCaptor.getValue().nextExecution(mockContext);

        // then: 다음 실행 시간이 계산됨 (cron 파싱 성공)
        assertThat(nextTime).isNotNull();
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
}
