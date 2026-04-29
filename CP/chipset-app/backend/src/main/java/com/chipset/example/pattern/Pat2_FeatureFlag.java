package com.chipset.example.pattern;

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
 * [Before]
 *   @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
 *   @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
 *   public BatchResult meetingEmailReminder() { ... }
 *   → @JobSchedulerTarget의 enabled 값이 컨텍스트 초기화 시 고정됨
 *
 * [After]
 *   메서드 내부에서 boolean 값을 DB에서 읽어 분기.
 *   배치 실행 시마다 최신 활성화 여부를 확인 → DB에서 false로 바꾸면 즉시 중단.
 *
 * [이점]
 *   - 서버 재시작 없이 배치 ON/OFF 가능
 *   - 장애 시 즉시 비활성화 (긴급 kill-switch 역할)
 *   - A/B 테스트, 점진적 기능 출시에 활용
 * =====================================================================
 */
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
