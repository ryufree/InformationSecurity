package maru.platform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import maru.platform.annotation.JobSchedulerTarget;
import maru.platform.dto.PropertiesResult;
import maru.platform.mapper.PropertiesMapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobSchedulerTargetBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<PropertiesMapper> propertiesMapperProvider;
    private final ObjectProvider<TaskScheduler> taskSchedulerProvider;
    private final Environment environment;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // AOP 프록시일 경우 실제 클래스에서 어노테이션 탐색
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        for (Method method : targetClass.getMethods()) {
            JobSchedulerTarget ann = method.getAnnotation(JobSchedulerTarget.class);
            if (ann == null) continue;

            String enabledKey = extractKey(ann.enabled());
            String cronKey    = extractKey(ann.cron());

            log.info("[JobSchedulerTarget] 태스크 등록: method={}, enabledKey={}, cronKey={}",
                     method.getName(), enabledKey, cronKey);

            taskSchedulerProvider.getObject().schedule(
                // ① 실행마다 DB에서 enabled 조회
                () -> {
                    String profile = resolveActiveProfile();
                    PropertiesResult result = propertiesMapperProvider.getObject().findByKey(enabledKey, profile);
                    String enabledStr = (result != null)
                        ? result.resolveValue("false")
                        : "false";
                    if (!Boolean.parseBoolean(enabledStr)) {
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
                    String profile = resolveActiveProfile();
                    PropertiesResult result = propertiesMapperProvider.getObject().findByKey(cronKey, profile);
                    String cronExpr = (result != null)
                        ? result.resolveValue("0 0/5 * * * ?")
                        : "0 0/5 * * * ?";
                    return new CronTrigger(cronExpr).nextExecution(triggerContext);
                }
            );
        }
        return bean;
    }

    private String resolveActiveProfile() {
        String[] profiles = environment.getActiveProfiles();
        return (profiles.length > 0) ? profiles[0] : "common";
    }

    /** "${maru.batch.mh.meeting.reminder.active}" → "maru.batch.mh.meeting.reminder.active" */
    private String extractKey(String value) {
        return value.startsWith("${") ? value.substring(2, value.length() - 1) : value;
    }
}
