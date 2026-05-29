package maru.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring Boot 기동 초기(Environment 준비 직후)에 DB Property 를 로딩하여
 * Spring Environment 의 최우선 PropertySource 로 등록.
 *
 * 등록 시점: ApplicationEnvironmentPreparedEvent
 *   → @Value, @ConfigurationProperties 주입 전에 실행되므로
 *     DB 값이 파일 기반 값을 덮어씀 (addFirst)
 *
 * 활성 프로파일이 없는 경우 (순수 default) 에도 동작:
 *   → 빈 리스트를 프로시저에 전달하면 'default' 프로파일만 로딩
 *
 * Boot.java 등록 예:
 *   new SpringApplicationBuilder(Boot.class)
 *       .listeners(new DbPropertyApplicationListener())
 *       .run(arguments);
 */
public class DbPropertyApplicationListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        // Spring 활성 프로파일 → 소문자 리스트 변환
        // 빈 리스트면 프로시저가 'default' 만 포함하여 동작
        List<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        String url      = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        String driver   = environment.getProperty("spring.datasource.driver-class-name");

        if (url == null || username == null || password == null || driver == null) {
            // datasource 설정 없음 → DB 로딩 스킵 (로컬 테스트 등)
            return;
        }

        Map<String, Object> dbProperties =
                DbPropertyLoader.loadFromJdbc(url, username, password, driver, activeProfiles);

        if (!dbProperties.isEmpty()) {
            // addFirst: DB 값이 application.properties 파일 값보다 우선 적용
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("dbProperties", dbProperties));
        }
    }
}
