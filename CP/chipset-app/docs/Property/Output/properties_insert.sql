-- ============================================================
-- maru_pl_comm_cd  INSERT SQL  v4
--
-- refrc1 하나에 JSON으로 모든 프로파일 값 저장
-- ex) {"dev":"dev-search:8080","live1":"live1-search:8080"}
--
-- 조회 시 JSON_VALUE(refrc1, '$.프로파일명') 으로 값 추출
-- ============================================================

-- ─────────────── 단독 키 ───────────────
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('rnSCsx8aIUEEKfsl','maru_properties',1,1,'maru.batch.monitor.mail.cron','{"dev": "0 0/5 * * * ?"}','N',1);
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('7dkyDo9F9ketFLTA','maru_properties',1,1,'maru.batch.sa.consumer.cron','{"batch": "0 0 5 * * ?"}','N',1);
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('Z0MUwtMJDPxYj55R','maru_properties',1,1,'maru.common.retry-count','{"default": "3"}','N',1);
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('DkW1lFHzIAR4ySo7','maru_properties',1,1,'maru.common.timeout','{"default": "30"}','N',1);
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('tO6eLrF1FXLfnZRJ','maru_properties',1,1,'maru.m02.mail.pop3.password','{"default": "mailpassword123"}','N',1);
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('o09I4QElEEpx4QJh','maru_properties',1,1,'maru.m02.mail.pop3.user','{"default": "maru"}','N',1);

-- ─────────────── 공통 키 ───────────────
-- key: clients.search  (4개 프로파일: dev, live1, rc, staging)
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('IBPXVLjEqThPPnZN','maru_properties',1,1,'clients.search','{"dev": "dev-search-server:8080", "live1": "live1-search-server:8080", "rc": "rc-search-server:8080", "staging": "staging-search-server:8080"}','N',1);

-- key: maru.batch.mh.meeting.reminder.active  (5개 프로파일: batch, dev, live1, rc, staging)
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('Q0uUEB2OFVZSxu4y','maru_properties',1,1,'maru.batch.mh.meeting.reminder.active','{"batch": "true", "dev": "true", "live1": "false", "rc": "true", "staging": "false"}','N',1);

-- key: maru.batch.mh.meeting.reminder.cron  (5개 프로파일: batch, dev, live1, rc, staging)
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('NN26y0JH8HW9lkgv','maru_properties',1,1,'maru.batch.mh.meeting.reminder.cron','{"batch": "0 0 9 * * ?", "dev": "0 0 9 * * ?", "live1": "0 0 10 * * ?", "rc": "0 30 9 * * ?", "staging": "0 0 11 * * ?"}','N',1);

-- key: maru.batch.noti.email.active  (5개 프로파일: batch, dev, live1, rc, staging)
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('yol1f106U7j2W2lm','maru_properties',1,1,'maru.batch.noti.email.active','{"batch": "true", "dev": "true", "live1": "false", "rc": "true", "staging": "true"}','N',1);

-- key: maru.batch.noti.email.cron  (5개 프로파일: batch, dev, live1, rc, staging)
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('kEbFkhxvxCw5392r','maru_properties',1,1,'maru.batch.noti.email.cron','{"batch": "0 0 5 * * ?", "dev": "0 0 5 * * ?", "live1": "0 0 6 * * ?", "rc": "0 0 7 * * ?", "staging": "0 0 8 * * ?"}','N',1);

-- key: maru.security.use-adsso-in-local  (2개 프로파일: dev, live1)
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('McPp3wskXpVvHrfr','maru_properties',1,1,'maru.security.use-adsso-in-local','{"dev": "dipfox33.id", "live1": "live1user.id"}','N',1);

-- key: maru.upload.max-file-size-mb  (5개 프로파일: batch, dev, live1, rc, staging)
INSERT INTO "maruadm"."maru_pl_comm_cd" ("cd_id","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn") VALUES ('ndkEAWjusgVWx2Z2','maru_properties',1,1,'maru.upload.max-file-size-mb','{"batch": "200", "dev": "100", "live1": "500", "rc": "300", "staging": "400"}','N',1);
