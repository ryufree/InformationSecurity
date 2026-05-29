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
 *   → application.properties 파일이 이미 로딩된 상태이므로
 *     파일의 플래그 값을 environment.getProperty() 로 읽을 수 있음
 *   → @Value, @ConfigurationProperties 주입 전에 실행되므로
 *     DB 값이 파일 기반 값을 덮어씀 (addFirst)
 *
 * 활성화 제어:
 *   application.properties 에 아래 키를 추가하여 제어
 *     maru.db-property.enabled=true   → DB Property 로딩 활성 (기본값)
 *     maru.db-property.enabled=false  → DB Property 로딩 비활성, 파일 값만 사용
 *
 *   플래그 우선순위:
 *     JVM 인수 (-Dmaru.db-property.enabled=false) 로도 제어 가능 —
 *     이 플래그만큼은 DB 값보다 파일/JVM 인수가 우선이어야 하므로
 *     DB 에서 읽지 않고 파일/시스템 환경에서만 읽는다.
 *
 * Boot.java 등록 예:
 *   new SpringApplicationBuilder(Boot.class)
 *       .listeners(new DbPropertyApplicationListener())
 *       .run(arguments);
 */
public class DbPropertyApplicationListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** application.properties 에 설정하는 DB Property 활성화 플래그 키 */
    private static final String ENABLED_KEY     = "maru.db-property.enabled";
    /** 플래그 미설정 시 기본값 — true: DB 로딩 활성 */
    private static final String DEFAULT_ENABLED = "true";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        // ── 1. DB Property 로딩 활성화 여부 확인 ──────────────────────────
        // application.properties(또는 -Dmaru.db-property.enabled) 에서 읽음.
        // 이 플래그 자체는 DB 에서 읽지 않는다 (닭·달걀 문제 방지).
        String enabled = environment.getProperty(ENABLED_KEY, DEFAULT_ENABLED);
        if (!"true".equalsIgnoreCase(enabled.trim())) {
            // false → DB 로딩 완전 스킵, 파일 기반 PropertySource 만 사용
            return;
        }

        // ── 2. datasource 설정 확인 ────────────────────────────────────────
        String url      = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        String driver   = environment.getProperty("spring.datasource.driver-class-name");

        if (url == null || username == null || password == null || driver == null) {
            // datasource 미설정 → DB 로딩 스킵 (로컬 테스트 등)
            return;
        }

        // ── 3. 활성 프로파일 수집 ──────────────────────────────────────────
        // 빈 리스트면 프로시저가 'default' 만 포함하여 동작
        List<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        // ── 4. DB Property 로딩 & 등록 ────────────────────────────────────
        Map<String, Object> dbProperties =
                DbPropertyLoader.loadFromJdbc(url, username, password, driver, activeProfiles);

        if (!dbProperties.isEmpty()) {
            // addFirst: DB 값이 application*.properties 파일 값보다 우선 적용
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("dbProperties", dbProperties));
        }
    }
}
