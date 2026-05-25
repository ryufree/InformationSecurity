package com.chipset.example.processor;

import com.chipset.example.annotation.JobSchedulerTarget;
import com.chipset.example.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @JobSchedulerTarget 처리기
 *
 * [동작 원리 — @DbValue 구조와 동일]
 *   기동 시 : @JobSchedulerTarget 어노테이션이 붙은 메서드를 스캔 → 태스크 등록
 *   실행마다: enabled  키로 DB 조회 → false면 skip, true면 실행
 *             cron     키로 DB 조회 → 다음 실행 시간 결정 (동적 스케줄)
 *
 * [사용법]
 *   @JobSchedulerTarget(
 *       enabled = "${maru.batch.mh.meeting.reminder.active}",
 *       cron    = "${maru.batch.mh.meeting.reminder.cron}"
 *   )
 *   public void meetingEmailReminder() { ... }
 */
@Component
public class JobSchedulerTargetProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerTargetProcessor.class);

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private TaskScheduler taskScheduler;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // AOP 프록시일 경우 실제 클래스에서 어노테이션 탐색
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        for (Method method : targetClass.getMethods()) {
            JobSchedulerTarget ann = method.getAnnotation(JobSchedulerTarget.class);
            if (ann == null) continue;

            String enabledKey = extractKey(ann.enabled());
            String cronKey    = extractKey(ann.cron());

            log.info("[JobSchedulerTarget] 태스크 등록: method={}, enabledKey={}, cronKey={}",
                     method.getName(), enabledKey, cronKey);

            taskScheduler.schedule(
                // ① 실행마다 DB에서 enabled 조회
                () -> {
                    boolean active = sysConfigService.getBoolean(enabledKey, false);
                    if (!active) {
                        log.info("[JobSchedulerTarget] skip — {}=false (DB)", enabledKey);
                        return;
                    }
                    try {
                        method.invoke(bean);
                    } catch (Exception e) {
                        log.error("[JobSchedulerTarget] 실행 오류: {}", method.getName(), e);
                        throw new RuntimeException(e);
                    }
                },
                // ② 실행마다 DB에서 cron 조회 → 동적 스케줄 반영
                triggerContext -> {
                    String cronExpr = sysConfigService.getString(cronKey, "0 0/5 * * * ?");
                    return new CronTrigger(cronExpr).nextExecution(triggerContext);
                }
            );
        }
        return bean;
    }

    /** "${maru.batch.mh.meeting.reminder.active}" → "maru.batch.mh.meeting.reminder.active" */
    private String extractKey(String value) {
        return value.startsWith("${") ? value.substring(2, value.length() - 1) : value;
    }
}
