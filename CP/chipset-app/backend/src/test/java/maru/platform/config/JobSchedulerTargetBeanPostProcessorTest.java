package maru.platform.config;

import maru.platform.annotation.JobSchedulerTarget;
import maru.platform.dto.PropertiesResult;
import maru.platform.mapper.PropertiesMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/**
 * JobSchedulerTargetBeanPostProcessor 단위 테스트
 *
 * [테스트 전략]
 *   실제 스케줄러 없이 ArgumentCaptor 로 Runnable/Trigger 를 꺼내 즉시 실행.
 *   DB 조회는 PropertiesMapper Mock 으로 대체한다.
 *
 * [검증 항목]
 *   Positive: enabled=true → 메서드 실행, enabled=false → skip, DB cron 동적 조회,
 *             DB null 시 기본값 fallback, 프로파일 해석, ${...} 키 추출, 어노테이션 없는 빈 무시
 *   Negative: 메서드 예외 → RuntimeException 전파, 잘못된 cron → IllegalArgumentException
 *
 * [ObjectProvider 설정]
 *   ObjectProvider<PropertiesMapper>, ObjectProvider<TaskScheduler> 는 @InjectMocks 로
 *   자동 주입되지 않으므로 @BeforeEach 에서 processor 를 직접 생성한다.
 *   두 provider stub 은 lenient() 로 선언한다.
 *   → p07(@JobSchedulerTarget 없는 빈) 테스트에서 provider 가 호출되지 않아
 *     STRICT_STUBS 위반이 발생하는 것을 방지하기 위함.
 */
@ExtendWith(MockitoExtension.class)
class JobSchedulerTargetBeanPostProcessorTest {

    @Mock private PropertiesMapper                 propertiesMapper;
    @Mock private ObjectProvider<PropertiesMapper> propertiesMapperProvider;
    @Mock private TaskScheduler                    taskScheduler;
    @Mock private ObjectProvider<TaskScheduler>    taskSchedulerProvider;
    @Mock private Environment                      environment;

    private JobSchedulerTargetBeanPostProcessor processor;

    @Captor private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor private ArgumentCaptor<Trigger>  triggerCaptor;

    private static final String ENABLED_KEY = "batch.job.active";
    private static final String CRON_KEY    = "batch.job.cron";

    @BeforeEach
    void setUp() {
        lenient().when(propertiesMapperProvider.getObject()).thenReturn(propertiesMapper);
        lenient().when(taskSchedulerProvider.getObject()).thenReturn(taskScheduler);
        processor = new JobSchedulerTargetBeanPostProcessor(
                propertiesMapperProvider, taskSchedulerProvider, environment);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private PropertiesResult dbResult(String refrc2) {
        PropertiesResult r = new PropertiesResult();
        ReflectionTestUtils.setField(r, "refrc2", refrc2);
        return r;
    }

    /** 등록된 Runnable 꺼내서 즉시 실행 */
    private void runCapturedRunnable() {
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run();
    }

    /** 등록된 Trigger 꺼내서 nextExecution 호출 후 결과 반환 */
    private Instant triggerNext() {
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        TriggerContext ctx = mock(TriggerContext.class);
        given(ctx.getClock()).willReturn(Clock.systemDefaultZone());
        return triggerCaptor.getValue().nextExecution(ctx);
    }

    // ── POSITIVE ────────────────────────────────────────────────────────

    @Test
    @DisplayName("[P] enabled=true → 배치 메서드 실행됨")
    void p01_enabled_true_배치_실행() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(ENABLED_KEY, "common")).willReturn(dbResult("true"));

        TestBatch bean = new TestBatch();
        processor.postProcessAfterInitialization(bean, "testBatch");
        runCapturedRunnable();

        assertThat(bean.executed).isTrue();
    }

    @Test
    @DisplayName("[P] enabled=false → 배치 메서드 skip")
    void p02_enabled_false_배치_skip() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(ENABLED_KEY, "common")).willReturn(dbResult("false"));

        TestBatch bean = new TestBatch();
        processor.postProcessAfterInitialization(bean, "testBatch");
        runCapturedRunnable();

        assertThat(bean.executed).isFalse();
    }

    @Test
    @DisplayName("[P] DB cron 값 → Trigger 가 해당 cron 으로 다음 실행 시간 계산됨")
    void p03_cron_DB_동적_조회() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(CRON_KEY, "common")).willReturn(dbResult("0 0 9 * * ?"));

        processor.postProcessAfterInitialization(new TestBatch(), "testBatch");
        Instant next = triggerNext();

        assertThat(next).isNotNull();
    }

    @Test
    @DisplayName("[P] DB enabled null → 기본값 false 처리 — 배치 skip")
    void p04_DB_enabled_null_기본값_false_skip() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(ENABLED_KEY, "common")).willReturn(null);

        TestBatch bean = new TestBatch();
        processor.postProcessAfterInitialization(bean, "testBatch");
        runCapturedRunnable();

        assertThat(bean.executed).isFalse();
    }

    @Test
    @DisplayName("[P] DB cron null → 기본값 '0 0/5 * * * ?' 로 다음 실행 시간 계산됨")
    void p05_DB_cron_null_기본값_cron_사용() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(CRON_KEY, "common")).willReturn(null);

        processor.postProcessAfterInitialization(new TestBatch(), "testBatch");
        Instant next = triggerNext();

        assertThat(next).isNotNull();
    }

    @Test
    @DisplayName("[P] 활성 프로파일 없을 때 → 'common' 으로 DB 조회")
    void p06_no_profile_common으로_DB_조회() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(ENABLED_KEY, "common")).willReturn(dbResult("true"));

        TestBatch bean = new TestBatch();
        processor.postProcessAfterInitialization(bean, "testBatch");
        runCapturedRunnable();

        then(propertiesMapper).should().findByKey(ENABLED_KEY, "common");
    }

    @Test
    @DisplayName("[P] @JobSchedulerTarget 없는 빈 → 스케줄러 등록 안함")
    void p07_no_annotation_빈_스케줄_미등록() {
        NoAnnotationBean bean = new NoAnnotationBean();
        processor.postProcessAfterInitialization(bean, "noAnnotationBean");

        then(taskScheduler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[P] '${...}' 형식 키 → key 추출 후 DB 조회됨")
    void p08_dollar_brace_key_추출() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        // PlainKeyBatch.enabled = "batch.job.active" (${} 없이 plain key)
        given(propertiesMapper.findByKey(ENABLED_KEY, "common")).willReturn(dbResult("true"));

        PlainKeyBatch bean = new PlainKeyBatch();
        processor.postProcessAfterInitialization(bean, "plainKeyBatch");
        runCapturedRunnable();

        then(propertiesMapper).should().findByKey(ENABLED_KEY, "common");
        assertThat(bean.executed).isTrue();
    }

    // ── NEGATIVE ────────────────────────────────────────────────────────

    @Test
    @DisplayName("[N] 배치 메서드 예외 발생 → RuntimeException 으로 감싸서 전파")
    void n01_method_예외_RuntimeException_전파() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(ENABLED_KEY, "common")).willReturn(dbResult("true"));

        ThrowingBatch bean = new ThrowingBatch();
        processor.postProcessAfterInitialization(bean, "throwingBatch");

        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        assertThatThrownBy(() -> runnableCaptor.getValue().run())
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("[N] 잘못된 cron 표현식 → nextExecution 호출 시 IllegalArgumentException")
    void n02_invalid_cron_표현식_예외() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey(CRON_KEY, "common")).willReturn(dbResult("INVALID_CRON"));

        processor.postProcessAfterInitialization(new TestBatch(), "testBatch");

        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        TriggerContext ctx = mock(TriggerContext.class);
        given(ctx.getClock()).willReturn(Clock.systemDefaultZone());

        assertThatThrownBy(() -> triggerCaptor.getValue().nextExecution(ctx))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test Beans ───────────────────────────────────────────────────────

    static class TestBatch {
        boolean executed = false;

        @JobSchedulerTarget(
            enabled = "${batch.job.active}",
            cron    = "${batch.job.cron}"
        )
        public void run() {
            executed = true;
        }
    }

    /** plain key (${} 없이) 를 사용하는 배치 빈 */
    static class PlainKeyBatch {
        boolean executed = false;

        @JobSchedulerTarget(
            enabled = "batch.job.active",
            cron    = "batch.job.cron"
        )
        public void run() {
            executed = true;
        }
    }

    /** 실행 시 예외를 던지는 배치 빈 */
    static class ThrowingBatch {
        @JobSchedulerTarget(
            enabled = "${batch.job.active}",
            cron    = "${batch.job.cron}"
        )
        public void run() {
            throw new IllegalStateException("의도적 예외");
        }
    }

    static class NoAnnotationBean {
        public void run() { }
    }
}
