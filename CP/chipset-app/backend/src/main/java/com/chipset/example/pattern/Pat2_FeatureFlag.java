package com.chipset.example.pattern;

import com.chipset.example.annotation.JobSchedulerTarget;
import com.chipset.example.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * =====================================================================
 * Pattern 2 : Feature Flag (boolean toggle) 패턴
 * =====================================================================
 * [적용 대상]
 *   maru.batch.mh.meeting.reminder.active=true  ← 이전 가능 ★1순위
 *   maru.feature.new-ui-enabled=false
 *
 * [현재 문제 — Before]
 *   @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
 *   → 처리기가 기동 시 1회 enabled 를 읽어 고정 → DB 변경 후 재시작 필요
 *
 * [해결 방법 선택]
 *   ① Pat2-A : 메서드 내부에서 sysConfigService.getBoolean() 직접 호출  (코드 변경 필요)
 *   ② Pat2-B : @JobSchedulerTarget 처리기를 @DbValue 구조로 수정         (어노테이션 유지)
 *              → 어노테이션은 그대로, 처리기만 "실행마다 DB 조회"로 변경
 *
 * [이점]
 *   - 서버 재시작 없이 배치 ON/OFF 가능
 *   - 장애 시 즉시 비활성화 (긴급 kill-switch 역할)
 * =====================================================================
 */

// ══════════════════════════════════════════════════════════════════════
// Pat2-A : 메서드 내부에서 직접 DB 조회
// ══════════════════════════════════════════════════════════════════════
@Service
class Pat2A_DirectCall {

    @Autowired
    private SysConfigService sysConfigService;

    // ── Before ────────────────────────────────────────────────────────
    // @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
    // @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
    // public void meetingEmailReminder_BEFORE() {
    //     // 기동 시 고정 → DB 변경해도 재시작 전까지 무시됨
    // }

    // ── After ─────────────────────────────────────────────────────────
    // @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
    public Object meetingEmailReminder() {
        boolean active = sysConfigService.getBoolean(
            "maru.batch.mh.meeting.reminder.active", true);  // ★ 실행마다 DB 조회

        if (!active) {
            return buildResult("SKIPPED", "배치 비활성화 (DB: active=false)");
        }
        return runBatch();
    }

    private Object runBatch()                              { return buildResult("SUCCESS", "완료"); }
    private Object buildResult(String status, String msg)  { return status + ": " + msg; }
}

// ══════════════════════════════════════════════════════════════════════
// Pat2-B : @JobSchedulerTarget 처리기를 @DbValue 구조로 수정
//          → 어노테이션 사용법은 Before 와 완전히 동일하게 유지
// ══════════════════════════════════════════════════════════════════════

/**
 * [사용 측 코드 — 변경 없음]
 * 처리기만 바꾸면 아래 어노테이션이 DB 값을 동적으로 반영한다.
 *
 *   @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
 *   @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
 *   public void meetingEmailReminder() { ... }
 */
@Service
class Pat2B_AnnotationStyle {

    // 처리기(Pat2B_Processor)가 이 어노테이션을 감지하여 DB 연동을 대신 처리.
    // 메서드 내부에 sysConfigService 호출 코드 불필요.

    @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}",
                        cron    = "${maru.batch.mh.meeting.reminder.cron}")
    public void meetingEmailReminder() {
        // 순수 배치 로직만 작성. active 체크는 처리기가 대신함.
        System.out.println("미팅 리마인더 이메일 발송");
    }
}

// ══════════════════════════════════════════════════════════════════════
// [공통 서비스] — 예제 코드용
// ══════════════════════════════════════════════════════════════════════
@Service
public class Pat2_FeatureFlag {

    @Autowired
    private SysConfigService sysConfigService;

    // ── Before: @JobSchedulerTarget 방식 (이전 전) ─────────────────────
    // @Profile({SpringProfile.BATCH_1})
    // @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
    // @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
    // public void meetingEmailReminder_BEFORE() {
    //     // 활성화 여부가 컨텍스트 초기화 시 고정됨 → 변경하려면 재시작 필요
    // }

    // ── After: DB boolean flag 방식 (이전 후) ──────────────────────────

    /**
     * [이전 완료] 미팅 알림 배치
     * active 플래그를 DB에서 읽어 실행 여부 결정.
     * cron 스케줄은 아직 @Scheduled 유지 → Pattern 4에서 완전 이전.
     */
    // @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
    public Object meetingEmailReminder() {
        // ★ 핵심: 실행마다 DB에서 최신 active 값을 읽는다
        boolean active = sysConfigService.getBoolean(
            "maru.batch.mh.meeting.reminder.active", true);

        if (!active) {
            // DB에서 false로 변경하면 즉시 이 분기 실행 → 재시작 불필요
            return buildResult("SKIPPED", "배치 비활성화 상태 (DB 설정값: active=false)");
        }

        // 실제 배치 로직
        return runBatch();
    }

    /**
     * 신규 UI 피처 플래그 예시.
     * maru.feature.new-ui-enabled 값에 따라 다른 화면 반환.
     */
    public String getHomeView(String userId) {
        boolean newUiEnabled = sysConfigService.getBoolean("maru.feature.new-ui-enabled", false);
        if (newUiEnabled) {
            return "new-home :: " + userId;   // 신규 UI
        }
        return "legacy-home :: " + userId;    // 기존 UI
    }

    /**
     * 이메일 재시도 횟수 동적 조회 예시.
     * DB에서 3 → 5로 변경하면 다음 실행부터 즉시 반영.
     */
    public boolean sendWithRetry(String to, String body) {
        int retryCount = sysConfigService.getInt("maru.batch.email.retry-count", 3);
        for (int i = 0; i <= retryCount; i++) {
            boolean ok = sendEmail(to, body);
            if (ok) return true;
        }
        return false;
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────
    private Object runBatch()                         { return buildResult("SUCCESS", "완료"); }
    private Object buildResult(String status, String msg) { return status + ": " + msg; }
    private boolean sendEmail(String to, String body) { return true; }
}
