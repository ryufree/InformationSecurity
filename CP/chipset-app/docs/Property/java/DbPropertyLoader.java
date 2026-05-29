package maru.config;

import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring Boot 기동 초기에 DB 에서 Property 를 일괄 로딩하는 클래스.
 *
 * 핵심 동작:
 *   1. 활성 프로파일 목록(소문자)을 Oracle 프로시저 SP_GET_PROPERTIES 에 전달
 *   2. 프로시저가 'default' 를 자동 포함하고, 우선순위(뒤에 선언된 프로파일 > 앞) 적용
 *   3. refrc1 JSON 파싱은 DB 측에서 JSON_VALUE 로 처리 → Java 는 단순 Map 빌딩만 담당
 *
 * 우선순위 예시:
 *   activeProfiles = ["dev", "batch"]
 *   → 프로시저 내부: default < dev < batch
 *   → maru.upload.max-file-size-mb 에 dev=100, batch=200 이면 200 반환
 */
public class DbPropertyLoader {

    private DbPropertyLoader() {}

    /**
     * @param url            spring.datasource.url
     * @param username       spring.datasource.username
     * @param password       spring.datasource.password
     * @param driver         spring.datasource.driver-class-name
     * @param activeProfiles Spring 활성 프로파일 목록 (대소문자 무관, 프로시저가 소문자로 처리)
     * @return property key → typed value 맵 (String / Boolean / Long / Double)
     */
    public static Map<String, Object> loadFromJdbc(
            String url, String username, String password, String driver,
            List<String> activeProfiles) {

        Map<String, Object> result = new HashMap<>();

        // activeProfiles 가 비어있어도 프로시저를 호출해야 한다.
        // 프로시저는 빈 문자열을 받으면 'default' 프로파일만 로딩하므로
        // application.properties(default 프로파일) 값이 DB에 있으면 정상 로딩된다.
        String profileList = (activeProfiles == null || activeProfiles.isEmpty())
                ? ""
                : activeProfiles.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.joining(","));

        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC 드라이버 로딩 실패: " + driver, e);
        }

        try (Connection conn = DriverManager.getConnection(url, username, password);
             CallableStatement cs = conn.prepareCall("{call maruadm.SP_GET_PROPERTIES(?, ?)}")) {

            cs.setString(1, profileList);
            cs.registerOutParameter(2, OracleTypes.CURSOR);
            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(2)) {
                while (rs.next()) {
                    String key   = rs.getString("property_key");
                    String value = rs.getString("property_value");
                    if (key != null && !key.isBlank()) {
                        result.put(key, convertValue(value));
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "DB Property 로딩 실패 [profiles=" + profileList + "]", e);
        }

        return result;
    }

    /**
     * String 값을 적절한 타입으로 변환.
     * 우선순위: null → Boolean → Long → Double → String
     */
    private static Object convertValue(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed))  return Boolean.TRUE;
        if ("false".equalsIgnoreCase(trimmed)) return Boolean.FALSE;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {}
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {}
        return value;
    }
}
