-- ============================================================
-- SP_GET_PROPERTIES
-- Spring Boot 시작 시 DB Property 일괄 조회 프로시저
--
-- 역할:
--   1) 'default' 프로파일을 항상 최저 우선순위로 자동 포함
--   2) 다중 프로파일 우선순위: 콤마 뒤에 올수록 높은 우선순위
--      ex) profile_list='dev,batch' → batch > dev > default
--   3) 같은 key 가 여러 프로파일에 있으면 우선순위 높은 값 하나만 반환
--   4) SYS_REFCURSOR 로 (property_key, property_value) 쌍 반환
--
-- 우선순위 계산:
--   INSTR(',' || full_list || ',', ',' || profile_nm || ',')
--   → 콤마로 감싸서 부분 일치 방지 (batch vs batch1 구분)
--   → full_list = 'default,dev,batch'
--      INSTR(',default,dev,batch,', ',default,') = 1   ← 최저
--      INSTR(',default,dev,batch,', ',dev,')     = 9
--      INSTR(',default,dev,batch,', ',batch,')   = 14  ← 최고
--   → ORDER BY priority DESC → batch 값 우선
--
-- 사용 예:
--   DECLARE
--     v_cursor SYS_REFCURSOR;
--     v_key    VARCHAR2(500);
--     v_val    VARCHAR2(4000);
--   BEGIN
--     SP_GET_PROPERTIES('dev,batch', v_cursor);
--     LOOP
--       FETCH v_cursor INTO v_key, v_val;
--       EXIT WHEN v_cursor%NOTFOUND;
--       DBMS_OUTPUT.PUT_LINE(v_key || ' = ' || v_val);
--     END LOOP;
--     CLOSE v_cursor;
--   END;
-- ============================================================

CREATE OR REPLACE PROCEDURE maruadm.SP_GET_PROPERTIES(
    p_profile_list  IN  VARCHAR2,
    p_cursor        OUT SYS_REFCURSOR
)
AS
    v_full_list  VARCHAR2(4000);
BEGIN
    -- 'default' 를 항상 맨 앞에 추가 → 최저 우선순위 보장
    IF p_profile_list IS NULL OR TRIM(p_profile_list) = '' THEN
        v_full_list := 'default';
    ELSE
        v_full_list := 'default,' || LOWER(TRIM(p_profile_list));
    END IF;

    OPEN p_cursor FOR
        SELECT property_key, property_value
        FROM (
            SELECT
                c.kor_cd_nm  AS property_key,
                JSON_VALUE(c.refrc1, CONCAT('$.', p.profile_nm))  AS property_value,
                -- 콤마 감싸기로 부분 일치 방지: 'batch' ≠ 'batch1'
                INSTR(',' || v_full_list || ',', ',' || p.profile_nm || ',') AS priority,
                ROW_NUMBER() OVER (
                    PARTITION BY c.kor_cd_nm
                    ORDER BY INSTR(',' || v_full_list || ',', ',' || p.profile_nm || ',') DESC
                ) AS rn
            FROM maruadm.maru_pl_comm_cd c
            CROSS JOIN (
                -- v_full_list 콤마 분리 → profile 행으로 전개
                SELECT TRIM(REGEXP_SUBSTR(v_full_list, '[^,]+', 1, LEVEL)) AS profile_nm
                FROM DUAL
                CONNECT BY LEVEL <= REGEXP_COUNT(v_full_list, ',') + 1
            ) p
            WHERE c.comm_type_cd = 'maru_properties'
            AND   c.del_yn       = 'N'
            AND   c.actv_yn      = 1
            -- 해당 프로파일 키가 JSON 에 실제로 존재하는 경우만 처리
            AND   JSON_VALUE(c.refrc1, CONCAT('$.', p.profile_nm)) IS NOT NULL
        )
        -- key 당 우선순위 가장 높은 행 1개만 반환
        WHERE rn = 1
        ORDER BY property_key;

EXCEPTION
    WHEN OTHERS THEN
        -- OPEN 후 RAISE 는 커서가 열린 채로 예외가 전파되어 Oracle 측에 불완전한
        -- 커서 상태가 남는 문제가 있다.
        -- 이미 OPEN 된 커서가 있으면 닫고, 예외를 그대로 전파한다.
        -- Java 측 catch 블록이 RuntimeException 으로 감싸서 재던짐.
        IF p_cursor%ISOPEN THEN
            CLOSE p_cursor;
        END IF;
        RAISE;
END SP_GET_PROPERTIES;
/


-- ============================================================
-- 권한 부여 (애플리케이션 스키마 사용자에게 실행 권한)
-- ============================================================
-- GRANT EXECUTE ON maruadm.SP_GET_PROPERTIES TO <app_schema_user>;


-- ============================================================
-- 동작 검증 쿼리 (프로시저 없이 직접 실행)
-- :profile_list = 'dev,batch'
-- ============================================================
/*
SELECT property_key, property_value
FROM (
    SELECT
        c.kor_cd_nm  AS property_key,
        JSON_VALUE(c.refrc1, CONCAT('$.', p.profile_nm)) AS property_value,
        INSTR(',' || 'default,' || :profile_list || ',', ',' || p.profile_nm || ',') AS priority,
        ROW_NUMBER() OVER (
            PARTITION BY c.kor_cd_nm
            ORDER BY INSTR(',' || 'default,' || :profile_list || ',', ',' || p.profile_nm || ',') DESC
        ) AS rn
    FROM maruadm.maru_pl_comm_cd c
    CROSS JOIN (
        SELECT TRIM(REGEXP_SUBSTR('default,' || :profile_list, '[^,]+', 1, LEVEL)) AS profile_nm
        FROM DUAL
        CONNECT BY LEVEL <= REGEXP_COUNT('default,' || :profile_list, ',') + 1
    ) p
    WHERE c.comm_type_cd = 'maru_properties'
    AND   c.del_yn       = 'N'
    AND   c.actv_yn      = 1
    AND   JSON_VALUE(c.refrc1, CONCAT('$.', p.profile_nm)) IS NOT NULL
)
WHERE rn = 1
ORDER BY property_key;
*/
