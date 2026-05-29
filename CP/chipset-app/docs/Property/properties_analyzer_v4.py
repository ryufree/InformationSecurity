#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Properties 파일 분석기 v4
- 공통 키: refrc1 하나에 JSON으로 전부 저장
  refrc1 = {"dev":"dev-search:8080","live1":"live1-search:8080","rc":"rc-search:8080"}
- refrc2~5 사용 안 함 (프로파일 수 제한 없음)
- 다중 프로파일 우선순위: dev,batch → batch 값 반환
- cd 컬럼: DEFAULT(default 프로파일) / BATCH(batch* 프로파일) / COMMON(그 외)
"""

import os, re, csv, random, string, json
from collections import OrderedDict


# ─────────────────────────────────────────────
# 1. 유틸리티
# ─────────────────────────────────────────────
def create_unique_id(length=16):
    return ''.join(random.choice(string.ascii_letters + string.digits)
                   for _ in range(length))

def sq(v):
    """SQL 문자열 이스케이프 (single quote)"""
    return v.replace("'", "''") if v else ''

def get_cd_category(profile: str) -> str:
    """
    프로파일명 → cd 컬럼 카테고리 결정 규칙
      default        → 'DEFAULT'  (공통 기본값, 항상 로딩)
      batch / batch* → 'BATCH'    (배치 전용)
      그 외          → 'COMMON'   (dev / live1 / rc / staging 등)
    """
    if profile == 'default':
        return 'DEFAULT'
    if re.match(r'^batch', profile, re.IGNORECASE):
        return 'BATCH'
    return 'COMMON'


# ─────────────────────────────────────────────
# 2. Properties 파싱
# ─────────────────────────────────────────────
def parse_properties_file(filepath):
    result = OrderedDict()
    with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
        content = f.read()
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
    content = re.sub(r'\\\n\s*', '', content)
    for line in content.splitlines():
        s = line.strip()
        if not s or s.startswith('#') or s.startswith('!'):
            continue
        m = re.match(r'^([^=:]+?)\s*[=:]\s*(.*)', s)
        if m:
            key, val = m.group(1).strip(), m.group(2).strip()
            if key:
                result[key] = val
    return result

def collect_files(folder='.'):
    return sorted(f for f in os.listdir(folder) if f.endswith('.properties'))

def extract_profile(filename):
    name = filename.replace('.properties', '')
    if name == 'application':
        return 'default'
    m = re.match(r'application-(.+)', name)
    return m.group(1) if m else name


# ─────────────────────────────────────────────
# 3. 분석
# ─────────────────────────────────────────────
def analyze(folder='.'):
    files = collect_files(folder)
    if not files:
        print("❌ .properties 파일 없음")
        return None

    print(f"📂 발견 파일: {files}\n")
    profile_data = OrderedDict()
    for fname in files:
        p = extract_profile(fname)
        profile_data[p] = parse_properties_file(os.path.join(folder, fname))
        print(f"  ✅ {fname} → profile={p}, keys={len(profile_data[p])}")

    profiles = list(profile_data.keys())
    all_keys  = sorted(set(k for d in profile_data.values() for k in d))

    unique_keys = {}  # key → (profile, value)  — 단 하나의 파일에만 존재
    common_keys = {}  # key → OrderedDict{profile: value}  — 여러 파일에 존재

    for key in all_keys:
        found = OrderedDict(
            (p, profile_data[p][key]) for p in profiles if key in profile_data[p]
        )
        if len(found) == 1:
            unique_keys[key] = next(iter(found.items()))
        else:
            common_keys[key] = found

    return profiles, profile_data, unique_keys, common_keys


# ─────────────────────────────────────────────
# 4. CSV
# ─────────────────────────────────────────────
def write_csv(profiles, unique_keys, common_keys, output='properties_analysis.csv'):
    rows = []
    rows.append(['=== 단독 키 (하나의 파일에만 존재) ==='])
    rows.append(['cd_category', '프로파일', 'key', 'value'])
    for key, (profile, value) in sorted(unique_keys.items()):
        rows.append([get_cd_category(profile), profile, key, value])

    rows.append([])
    rows.append(['=== 공통 키 (여러 파일에 존재, cd=COMMON) ==='])
    rows.append(['key'] + profiles)
    for key, pmap in sorted(common_keys.items()):
        rows.append([key] + [pmap.get(p, '') for p in profiles])

    with open(output, 'w', newline='', encoding='utf-8-sig') as f:
        csv.writer(f).writerows(rows)
    print(f"\n📄 CSV: {output}  (단독={len(unique_keys)}, 공통={len(common_keys)})")


# ─────────────────────────────────────────────
# 5. INSERT SQL
#
# cd 컬럼 규칙:
#   단독 키 (unique): get_cd_category(profile) 로 결정
#     - default 프로파일 → 'DEFAULT'
#     - batch*  프로파일 → 'BATCH'
#     - 그 외            → 'COMMON'
#   공통 키 (common): 항상 'COMMON' (여러 프로파일에 걸침)
#
# refrc1 JSON 구조:
#   단독: {"default":"maru"}
#   공통: {"dev":"dev-search:8080","live1":"live1-search:8080",...}
# ─────────────────────────────────────────────
def write_insert_sql(unique_keys, common_keys, output='properties_insert.sql'):
    lines = [
        '-- ============================================================',
        '-- maru_pl_comm_cd  INSERT SQL  v4',
        '--',
        '-- cd 컬럼 카테고리:',
        '--   DEFAULT : default 프로파일 전용 (항상 로딩)',
        '--   BATCH   : batch / batch1 / batch2 등 배치 전용',
        '--   COMMON  : dev / live1 / rc / staging 및 공통 키',
        '--',
        '-- refrc1: JSON 으로 모든 프로파일 값 저장',
        "--   ex) {\"dev\":\"dev-search:8080\",\"live1\":\"live1-search:8080\"}",
        '--',
        "-- 조회: JSON_VALUE(refrc1, '$.프로파일명') 으로 값 추출",
        '-- 프로시저: SP_GET_PROPERTIES(profile_list) 로 일괄 조회',
        '-- ============================================================',
        '',
        '-- ─────────────── 단독 키 (cd = profile 카테고리) ───────────────',
    ]

    for key, (profile, value) in sorted(unique_keys.items()):
        cd_id    = create_unique_id()
        cd_cat   = get_cd_category(profile)
        json_val = json.dumps({profile: value}, ensure_ascii=False)
        lines.append(
            f'INSERT INTO "maruadm"."maru_pl_comm_cd" '
            f'("cd_id","cd","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES '
            f"('{cd_id}','{cd_cat}','maru_properties',1,1,'{sq(key)}','{sq(json_val)}','N',1);"
        )

    lines.append('')
    lines.append('-- ─────────────── 공통 키 (cd = COMMON, 여러 프로파일) ───────────────')

    for key, pmap in sorted(common_keys.items()):
        cd_id    = create_unique_id()
        json_val = json.dumps(dict(pmap), ensure_ascii=False)
        profiles_str = ', '.join(pmap.keys())
        lines.append(f'-- key: {key}  ({len(pmap)}개 프로파일: {profiles_str})')
        lines.append(
            f'INSERT INTO "maruadm"."maru_pl_comm_cd" '
            f'("cd_id","cd","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES '
            f"('{cd_id}','COMMON','maru_properties',1,1,'{sq(key)}','{sq(json_val)}','N',1);"
        )
        lines.append('')

    with open(output, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    print(f"📄 INSERT SQL: {output}")


# ─────────────────────────────────────────────
# 6. SELECT SQL
# ─────────────────────────────────────────────
def write_select_sql(output='properties_select.sql'):
    lines = [
        '-- ============================================================',
        '-- Properties 조회 쿼리 v4',
        '--',
        '-- refrc1 컬럼 JSON 구조:',
        '--   {"dev":"dev-search:8080","live1":"live1-search:8080","rc":"rc-search:8080"}',
        '--',
        '-- 다중 프로파일 우선순위:',
        '--   -Dspring.profiles.active=dev,batch',
        '--   → 동일 키 존재시 batch(뒤에 선언) 값 우선 반환',
        '--   → default 는 항상 최저 우선순위 (SP_GET_PROPERTIES 가 자동 포함)',
        '-- ============================================================',
        '',

        '-- ─── 1. 단일 프로파일 조회 ────────────────────────────────',
        '-- :key     = property key  ex) maru.batch.noti.email.cron',
        '-- :profile = 프로파일명    ex) dev',
        '',
        'SELECT',
        "  JSON_VALUE(c.\"refrc1\", CONCAT('$.', :profile)) AS property_value",
        'FROM "maruadm"."maru_pl_comm_cd" c',
        "WHERE c.\"comm_type_cd\" = 'maru_properties'",
        "AND   c.\"kor_cd_nm\"    = :key",
        "AND   c.\"del_yn\"       = 'N'",
        "AND   c.\"actv_yn\"      = 1;",
        '',

        '-- ─── 2. 다중 프로파일 조회 (우선순위 적용) ─────────────────',
        "-- :key          = property key",
        "-- :profile_list = 'dev,batch'  → batch 우선 반환 (default 자동 포함)",
        "--",
        "-- 원리: INSTR(',default,dev,batch,', ',batch,') > INSTR(..., ',dev,') > INSTR(..., ',default,')",
        "--       → 뒤에 위치할수록 INSTR 값이 크다 → ORDER BY DESC → 우선순위 높음",
        '--       → 콤마 감싸기로 부분 일치 방지 (batch vs batch1 구분)',
        '',
        'SELECT property_value',
        'FROM (',
        '  SELECT',
        "    JSON_VALUE(c.\"refrc1\", CONCAT('$.', p.profile_nm)) AS property_value,",
        "    INSTR(',' || 'default,' || :profile_list || ',', ',' || p.profile_nm || ',') AS priority",
        '  FROM "maruadm"."maru_pl_comm_cd" c',
        '  CROSS JOIN (',
        "    SELECT TRIM(REGEXP_SUBSTR('default,' || :profile_list, '[^,]+', 1, LEVEL)) AS profile_nm",
        '    FROM DUAL',
        "    CONNECT BY LEVEL <= REGEXP_COUNT('default,' || :profile_list, ',') + 1",
        '  ) p',
        "  WHERE c.\"comm_type_cd\" = 'maru_properties'",
        "  AND   c.\"kor_cd_nm\"    = :key",
        "  AND   c.\"del_yn\"       = 'N'",
        "  AND   c.\"actv_yn\"      = 1",
        "  AND   JSON_VALUE(c.\"refrc1\", CONCAT('$.', p.profile_nm)) IS NOT NULL",
        ')',
        'WHERE ROWNUM = 1',
        'ORDER BY priority DESC;',
        '',

        '-- ─── 3. 전체 키 조회 (특정 프로파일 기준) ──────────────────',
        '-- :profile = dev  → dev 프로파일의 모든 property 반환',
        '',
        'SELECT',
        '  c."kor_cd_nm"  AS property_key,',
        "  JSON_VALUE(c.\"refrc1\", CONCAT('$.', :profile)) AS property_value",
        'FROM "maruadm"."maru_pl_comm_cd" c',
        "WHERE c.\"comm_type_cd\" = 'maru_properties'",
        "AND   c.\"del_yn\"       = 'N'",
        "AND   c.\"actv_yn\"      = 1",
        "AND   JSON_VALUE(c.\"refrc1\", CONCAT('$.', :profile)) IS NOT NULL",
        'ORDER BY c."kor_cd_nm";',
        '',

        '-- ─── 4. 다중 프로파일 전체 키 조회 (SP_GET_PROPERTIES 참고용) ──',
        "-- :profile_list = 'dev,batch'  → batch 우선, 전체 key-value 반환",
        "-- default 는 자동 포함 (최저 우선순위)",
        '',
        'SELECT property_key, property_value',
        'FROM (',
        '  SELECT',
        '    c."kor_cd_nm" AS property_key,',
        "    JSON_VALUE(c.\"refrc1\", CONCAT('$.', p.profile_nm)) AS property_value,",
        "    INSTR(',' || 'default,' || :profile_list || ',', ',' || p.profile_nm || ',') AS priority,",
        '    ROW_NUMBER() OVER (',
        '      PARTITION BY c."kor_cd_nm"',
        "      ORDER BY INSTR(',' || 'default,' || :profile_list || ',', ',' || p.profile_nm || ',') DESC",
        '    ) AS rn',
        '  FROM "maruadm"."maru_pl_comm_cd" c',
        '  CROSS JOIN (',
        "    SELECT TRIM(REGEXP_SUBSTR('default,' || :profile_list, '[^,]+', 1, LEVEL)) AS profile_nm",
        '    FROM DUAL',
        "    CONNECT BY LEVEL <= REGEXP_COUNT('default,' || :profile_list, ',') + 1",
        '  ) p',
        "  WHERE c.\"comm_type_cd\" = 'maru_properties'",
        "  AND   c.\"del_yn\"       = 'N'",
        "  AND   c.\"actv_yn\"      = 1",
        "  AND   JSON_VALUE(c.\"refrc1\", CONCAT('$.', p.profile_nm)) IS NOT NULL",
        ')',
        'WHERE rn = 1',
        'ORDER BY property_key;',
    ]

    with open(output, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    print(f"📄 SELECT SQL: {output}")


# ─────────────────────────────────────────────
# 7. 메인
# ─────────────────────────────────────────────
if __name__ == '__main__':
    import sys
    folder = sys.argv[1] if len(sys.argv) > 1 else '.'
    print(f"🔍 분석 폴더: {os.path.abspath(folder)}\n")

    result = analyze(folder)
    if not result:
        sys.exit(1)

    profiles, profile_data, unique_keys, common_keys = result
    write_csv(profiles, unique_keys, common_keys)
    write_insert_sql(unique_keys, common_keys)
    write_select_sql()

    print(f"\n✅ 완료")
    print(f"   단독 키: {len(unique_keys)}개")
    print(f"   공통 키: {len(common_keys)}개")
    print(f"   ALTER TABLE 불필요 — refrc1 JSON 단일 컬럼으로 처리")
    print(f"   cd 컬럼: DEFAULT / BATCH / COMMON 자동 분류")
