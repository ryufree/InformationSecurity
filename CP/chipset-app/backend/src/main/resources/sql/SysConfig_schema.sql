-- =====================================================================
-- SYS_CONFIG : application.properties → DB 이전 관리 테이블
-- =====================================================================
-- [설계 원칙]
-- EDITABLE_YN = 'Y' : 서버 재시작 없이 런타임 변경 가능
-- EDITABLE_YN = 'N' : 서버 재시작이 필요하거나 이전 불가
-- RESTART_YN  = 'Y' : Spring/Tomcat 기동 시점에만 적용됨 → DB 이전 불가
-- RESTART_YN  = 'N' : 애플리케이션 레이어에서 언제든 읽기 가능
--
-- [이전 가능 여부 판단 기준]
--  1. server.port, server.port.http
--     → Spring Boot가 EmbeddedServletContainer를 초기화하는 시점에 읽힘
--       ApplicationContext 생성 전 단계이므로 DB 연결 자체가 불가
--       → RESTART_YN='Y' : 이전 불가
--
--  2. server.tomcat.max-threads
--     → TomcatEmbeddedWebappClassLoader 초기화 시 1회 고정
--       → RESTART_YN='Y' : 이전 불가
--
--  3. security.require-ssl
--     → Spring Security HttpSecurity 설정은 ApplicationContext
--       초기화 시 1회만 실행됨. 이후 변경 불가
--       → RESTART_YN='Y' : 이전 불가
--
--  4. maru.batch.mh.meeting.reminder.active
--     → 배치 실행 메서드 내부에서 Service를 통해 읽으면 됨
--       → EDITABLE_YN='Y' : 이전 가능 (가장 쉬운 케이스, 우선 1순위)
--
--  5. maru.batch.mh.meeting.reminder.cron
--     → @Scheduled(cron="${...}") 은 컨텍스트 초기화 시 1회 고정됨
--       단, SchedulingConfigurer 패턴으로 재작성하면 동적 변경 가능
--       → EDITABLE_YN='Y', 단 코드 수정 필요 (Pattern 4 참고)
-- =====================================================================

-- 시퀀스
CREATE SEQUENCE SQ_SYS_CONFIG START WITH 1 INCREMENT BY 1;

-- 테이블
CREATE TABLE SYS_CONFIG (
    CONFIG_ID   NUMBER        NOT NULL,
    PROFILE     VARCHAR2(50)  NOT NULL,        -- dev / live1 / live2 / rc / batch1 / common
    PROP_KEY    VARCHAR2(200) NOT NULL,
    PROP_VALUE  VARCHAR2(2000),
    DATA_TYPE   VARCHAR2(20)  DEFAULT 'STRING', -- STRING / BOOLEAN / INTEGER / CRON
    EDITABLE_YN CHAR(1)       DEFAULT 'Y',      -- Y=런타임 변경 가능, N=재시작 필요
    RESTART_YN  CHAR(1)       DEFAULT 'N',      -- Y=서버 기동 시점 적용(이전 불가)
    DESCRIPTION VARCHAR2(500),
    USE_YN      CHAR(1)       DEFAULT 'Y',
    REG_DT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    MOD_DT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT PK_SYS_CONFIG       PRIMARY KEY (CONFIG_ID),
    CONSTRAINT UK_SYS_CONFIG_KEY   UNIQUE (PROFILE, PROP_KEY),
    CONSTRAINT CHK_EDITABLE_YN     CHECK (EDITABLE_YN IN ('Y','N')),
    CONSTRAINT CHK_RESTART_YN      CHECK (RESTART_YN  IN ('Y','N')),
    CONSTRAINT CHK_USE_YN          CHECK (USE_YN       IN ('Y','N'))
);

COMMENT ON COLUMN SYS_CONFIG.EDITABLE_YN IS 'Y=런타임 변경 가능, N=서버 재시작 필요';
COMMENT ON COLUMN SYS_CONFIG.RESTART_YN  IS 'Y=서버 기동 시점에만 적용(DB이전 불가)';

-- =====================================================================
-- 샘플 데이터 : application-dev.properties
-- 모두 RESTART_YN='Y' → 이전 불가 (기록 목적으로만 저장)
-- =====================================================================
INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'dev', 'server.port', '8443', 'INTEGER', 'N', 'Y',
        '[이전불가] Tomcat HTTPS 포트. ApplicationContext 생성 전 EmbeddedWebServer가 읽음');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'dev', 'server.port.http', '8001', 'INTEGER', 'N', 'Y',
        '[이전불가] HTTP 커넥터 포트. TomcatServletWebServerFactory 초기화 시 고정');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'dev', 'security.require-ssl', 'true', 'BOOLEAN', 'N', 'Y',
        '[이전불가] Spring Security SSL 강제 여부. HttpSecurity 설정은 컨텍스트 초기화 시 1회 실행');

-- =====================================================================
-- 샘플 데이터 : application-live1.properties
-- =====================================================================
INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'live1', 'server.port', '443', 'INTEGER', 'N', 'Y',
        '[이전불가] Tomcat HTTPS 포트');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'live1', 'server.port.http', '80', 'INTEGER', 'N', 'Y',
        '[이전불가] HTTP 커넥터 포트');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'live1', 'server.tomcat.max-threads', '3000', 'INTEGER', 'N', 'Y',
        '[이전불가] Tomcat 최대 스레드 수. WebServerFactoryCustomizer 시점에 고정');

-- =====================================================================
-- 샘플 데이터 : application-batch1.properties (이전 가능)
-- =====================================================================
INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'batch1', 'maru.batch.mh.meeting.reminder.active', 'true', 'BOOLEAN', 'Y', 'N',
        '[이전가능★1순위] 배치 실행 시 Service에서 직접 읽으면 재시작 없이 ON/OFF 제어 가능');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'batch1', 'maru.batch.mh.meeting.reminder.cron', '0 0/5 * * * ?', 'CRON', 'Y', 'N',
        '[이전가능★2순위/주의] @Scheduled 사용 중이면 Pat4_DynamicScheduler 패턴으로 재작성 필요');

-- =====================================================================
-- 샘플 데이터 : common (모든 프로파일 공통, 우선순위 낮음)
-- =====================================================================
INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'common', 'maru.upload.max-file-size-mb', '100', 'INTEGER', 'Y', 'N',
        '[이전가능] 업로드 최대 파일 크기(MB)');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'common', 'maru.meeting.join-url', 'https://meet.example.com', 'STRING', 'Y', 'N',
        '[이전가능] 미팅 참여 URL');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'common', 'maru.email.sender', 'noreply@example.com', 'STRING', 'Y', 'N',
        '[이전가능] 발신 이메일 주소');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'common', 'maru.feature.new-ui-enabled', 'false', 'BOOLEAN', 'Y', 'N',
        '[이전가능] 신규 UI 피처 플래그');

INSERT INTO SYS_CONFIG (CONFIG_ID, PROFILE, PROP_KEY, PROP_VALUE, DATA_TYPE, EDITABLE_YN, RESTART_YN, DESCRIPTION)
VALUES (SQ_SYS_CONFIG.NEXTVAL, 'common', 'maru.batch.email.retry-count', '3', 'INTEGER', 'Y', 'N',
        '[이전가능] 이메일 발송 실패 시 재시도 횟수');

COMMIT;
