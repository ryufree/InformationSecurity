-- ============================================================
-- Properties 조회 쿼리 v4
--
-- refrc1 컬럼 JSON 구조:
--   {"dev":"dev-search:8080","live1":"live1-search:8080","rc":"rc-search:8080"}
--
-- 다중 프로파일 우선순위:
--   -Dspring.profiles.active=dev,batch
--   → 동일 키 존재시 batch(뒤에 선언) 값 우선 반환
-- ============================================================

-- ─── 1. 단일 프로파일 조회 ────────────────────────────────
-- :key     = property key  ex) maru.batch.noti.email.cron
-- :profile = 프로파일명    ex) dev

SELECT
  JSON_VALUE(c."refrc1", CONCAT('$.', :profile)) AS property_value
FROM "maruadm"."maru_pl_comm_cd" c
WHERE c."comm_type_cd" = 'maru_properties'
AND   c."kor_cd_nm"    = :key
AND   c."del_yn"       = 'N';

-- ─── 2. 다중 프로파일 조회 (우선순위 적용) ─────────────────
-- :key          = property key
-- :profile_list = 'dev,batch'  → batch 우선 반환
--
-- 원리: INSTR('dev,batch', 'batch') = 5 > INSTR('dev,batch', 'dev') = 1
--       → 뒤에 위치할수록 INSTR 값이 크다 → ORDER BY DESC → 우선순위 높음

SELECT property_value
FROM (
  SELECT
    JSON_VALUE(c."refrc1", CONCAT('$.', p.profile_nm)) AS property_value,
    INSTR(:profile_list, p.profile_nm) AS priority
  FROM "maruadm"."maru_pl_comm_cd" c
  CROSS JOIN (
    -- :profile_list = 'dev,batch' 를 행으로 분리
    SELECT TRIM(REGEXP_SUBSTR(:profile_list, '[^,]+', 1, LEVEL)) AS profile_nm
    FROM DUAL
    CONNECT BY LEVEL <= REGEXP_COUNT(:profile_list, ',') + 1
  ) p
  WHERE c."comm_type_cd" = 'maru_properties'
  AND   c."kor_cd_nm"    = :key
  AND   c."del_yn"       = 'N'
  -- 해당 프로파일 키가 JSON에 실제로 존재하는 경우만
  AND   JSON_VALUE(c."refrc1", CONCAT('$.', p.profile_nm)) IS NOT NULL
)
WHERE ROWNUM = 1
ORDER BY priority DESC;

-- ─── 3. 전체 키 조회 (특정 프로파일 기준) ──────────────────
-- :profile = dev  → dev 프로파일의 모든 property 반환

SELECT
  c."kor_cd_nm"  AS property_key,
  JSON_VALUE(c."refrc1", CONCAT('$.', :profile)) AS property_value
FROM "maruadm"."maru_pl_comm_cd" c
WHERE c."comm_type_cd" = 'maru_properties'
AND   c."del_yn"       = 'N'
AND   JSON_VALUE(c."refrc1", CONCAT('$.', :profile)) IS NOT NULL
ORDER BY c."kor_cd_nm";

-- ─── 4. 다중 프로파일 전체 키 조회 ─────────────────────────
-- :profile_list = 'dev,batch'  → batch 우선, 전체 key-value 반환

SELECT property_key, property_value
FROM (
  SELECT
    c."kor_cd_nm" AS property_key,
    JSON_VALUE(c."refrc1", CONCAT('$.', p.profile_nm)) AS property_value,
    INSTR(:profile_list, p.profile_nm) AS priority,
    ROW_NUMBER() OVER (
      PARTITION BY c."kor_cd_nm"
      ORDER BY INSTR(:profile_list, p.profile_nm) DESC
    ) AS rn
  FROM "maruadm"."maru_pl_comm_cd" c
  CROSS JOIN (
    SELECT TRIM(REGEXP_SUBSTR(:profile_list, '[^,]+', 1, LEVEL)) AS profile_nm
    FROM DUAL
    CONNECT BY LEVEL <= REGEXP_COUNT(:profile_list, ',') + 1
  ) p
  WHERE c."comm_type_cd" = 'maru_properties'
  AND   c."del_yn"       = 'N'
  AND   JSON_VALUE(c."refrc1", CONCAT('$.', p.profile_nm)) IS NOT NULL
)
WHERE rn = 1
ORDER BY property_key;