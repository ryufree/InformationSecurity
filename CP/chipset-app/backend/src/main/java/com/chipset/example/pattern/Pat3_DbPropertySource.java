package com.chipset.example.pattern;

import com.chipset.example.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * =====================================================================
 * Pattern 3 : DB → Spring Environment PropertySource 등록
 * =====================================================================
 * [목적]
 *   DB에 저장된 값을 Spring Environment에 PropertySource로 등록하면,
 *   기존 @Value 어노테이션을 코드 변경 없이 그대로 사용할 수 있다.
 *
 * [동작 방식]
 *   애플리케이션 기동 완료(@PostConstruct) 후 DB에서 설정을 읽어
 *   Spring Environment의 가장 높은 우선순위로 추가한다.
 *   → @Value 필드는 이미 주입이 끝난 상태이므로 @Value에는 반영 안됨
 *   → 그러나 Environment.getProperty() 호출이나
 *      @ConfigurationProperties 리프레시에는 활용 가능
 *
 * [한계 및 해결책]
 *   @Value 필드 주입은 빈 초기화 시 1회 실행되므로 이미 완료됨.
 *   → 빈 초기화 전에 DB PropertySource를 등록해야 @Value에 반영됨
 *   → 그러려면 EnvironmentPostProcessor(raw JDBC 필요)를 사용해야 함
 *   → Pattern 3는 @Value 투명 교체가 아닌 Environment 조회용으로 활용
 *
 * [권장 사용처]
 *   - Environment.getProperty("key") 직접 호출
 *   - 테스트에서 DB 설정 override
 *   - 설정 확인/디버깅 목적의 관리 API
 * =====================================================================
 */
@Configuration
public class Pat3_DbPropertySource {

    private static final String DB_PROPERTY_SOURCE_NAME = "dbPropertySource";

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private ConfigurableEnvironment environment;

    /**
     * 애플리케이션 기동 완료 후 DB 설정을 Spring Environment에 추가.
     * application.properties 보다 높은 우선순위로 등록.
     */
    @PostConstruct
    public void registerDbPropertySource() {
        Map<String, String> dbProps = sysConfigService.loadAsMap();

        // String → Object 변환 (MapPropertySource는 Map<String, Object> 요구)
        Map<String, Object> propMap = new java.util.LinkedHashMap<>(dbProps);

        MapPropertySource dbSource = new MapPropertySource(DB_PROPERTY_SOURCE_NAME, propMap);

        // addFirst: application.properties 보다 높은 우선순위
        environment.getPropertySources().addFirst(dbSource);

        System.out.println("[Pat3] DB PropertySource 등록 완료: " + dbProps.size() + "개 설정");
    }

    /**
     * DB PropertySource를 갱신한다.
     * 호출 시 최신 DB 값으로 Environment를 업데이트.
     * (기존에 @Value로 주입된 필드에는 반영 안됨 - @Value는 빈 초기화 시 1회 고정)
     */
    public void refresh() {
        environment.getPropertySources().remove(DB_PROPERTY_SOURCE_NAME);
        registerDbPropertySource();
        System.out.println("[Pat3] DB PropertySource 갱신 완료");
    }

    /**
     * Environment를 통해 설정값 조회 예시.
     * DB PropertySource가 등록된 후에는 DB 값이 우선 반환됨.
     */
    public String getFromEnvironment(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }
}
