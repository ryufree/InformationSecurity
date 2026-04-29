package com.chipset.example.pattern;

import com.chipset.example.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.util.concurrent.ScheduledFuture;

/**
 * =====================================================================
 * Pattern 4 : SchedulingConfigurer - DB 크론 표현식 동적 스케줄러
 * =====================================================================
 * [적용 대상]
 *   maru.batch.mh.meeting.reminder.cron=0 0/5 * * * ?
 *
 * [Before - @Scheduled 방식]
 *   @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
 *   public void meetingEmailReminder() { ... }
 *   → 컨텍스트 초기화 시 cron 표현식이 1회 고정됨
 *   → DB에서 cron 값을 바꿔도 재시작 전까지는 반영 안됨
 *
 * [After - SchedulingConfigurer 방식]
 *   @Scheduled 어노테이션을 제거하고 SchedulingConfigurer를 구현.
 *   각 실행 후 다음 실행 시간을 DB에서 실시간으로 읽어 결정.
 *   → cron 값 변경 즉시 반영됨 (다음 실행부터)
 *
 * [동작 원리]
 *   CronTrigger.nextExecutionTime() 이 호출될 때마다
 *   DB에서 최신 cron 값을 읽어 새 CronTrigger 생성.
 *   → 실행마다 cron을 재평가하므로 완전한 동적 스케줄링 가능.
 *
 * [주의]
 *   cron 표현식이 잘못된 경우 IllegalArgumentException 발생.
 *   반드시 updateCron() 메서드로 유효성 검사 후 업데이트할 것.
 * =====================================================================
 */
@Configuration
@EnableScheduling
public class Pat4_DynamicScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(Pat4_DynamicScheduler.class);
    private static final String CRON_KEY   = "maru.batch.mh.meeting.reminder.cron";
    private static final String ACTIVE_KEY = "maru.batch.mh.meeting.reminder.active";
    private static final String DEFAULT_CRON = "0 0/5 * * * ?";

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> scheduledFuture;

    /**
     * 스프링이 @EnableScheduling 처리 시 이 메서드를 호출.
     * @Scheduled 어노테이션 대신 여기서 태스크를 직접 등록.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // Trigger 방식으로 등록: 매 실행 완료 후 다음 실행 시간을 동적으로 결정
        registrar.addTriggerTask(
            // ① 실행할 태스크 (Runnable)
            this::meetingEmailReminder,

            // ② 다음 실행 시간 결정 Trigger
            // nextExecutionTime() 은 각 실행 완료 직후 호출됨
            triggerContext -> {
                String cron = sysConfigService.getString(CRON_KEY, DEFAULT_CRON);
                log.debug("[Pat4] 다음 실행 cron: {}", cron);
                return new CronTrigger(cron).nextExecution(triggerContext);
            }
        );
        log.info("[Pat4] 동적 스케줄러 등록 완료 (cron from DB key={})", CRON_KEY);
    }

    /**
     * 실제 배치 로직.
     * active 플래그도 DB에서 읽어 Pattern 2와 결합.
     */
    private void meetingEmailReminder() {
        boolean active = sysConfigService.getBoolean(ACTIVE_KEY, true);
        String  cron   = sysConfigService.getString(CRON_KEY, DEFAULT_CRON);

        log.info("[Pat4] meetingEmailReminder 실행 | active={}, cron={}", active, cron);

        if (!active) {
            log.info("[Pat4] 배치 스킵 (active=false, DB에서 비활성화됨)");
            return;
        }

        // 실제 배치 로직
        log.info("[Pat4] 미팅 리마인더 이메일 발송 중...");
        // meetingReminderEmailService.sendReminders();
    }

    /**
     * 런타임에 크론 표현식 변경 + 즉시 재스케줄링.
     * REST API나 관리자 화면에서 호출.
     *
     * @param newCron 새 cron 표현식 (예: "0 0/10 * * * ?")
     */
    public void updateCron(String newCron) {
        // 1) 유효성 검사
        try {
            new CronTrigger(newCron);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 cron 표현식: " + newCron, e);
        }

        // 2) DB 업데이트
        int updated = sysConfigService.updateValue(CRON_KEY, newCron);
        if (updated == 0) {
            throw new IllegalStateException("DB 업데이트 실패: key=" + CRON_KEY);
        }

        // 3) 다음 실행부터 자동으로 새 cron 적용됨
        //    (configureTasks의 Trigger가 매 실행 후 DB에서 재읽기 때문)
        log.info("[Pat4] cron 변경 완료: {} → {}", CRON_KEY, newCron);
    }
}
