package com.chipset.example.pattern;

import com.chipset.example.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * =====================================================================
 * Pattern 1 : @Value → SysConfigService 직접 호출로 교체
 * =====================================================================
 * [가장 단순, 1순위 권장]
 *
 * [Before - application.properties 사용]
 *   @Value("${server.port}")
 *   private String localPort;         ← 서버 기동 시 1회 주입, 이후 불변
 *
 *   @Value("${maru.upload.max-file-size-mb}")
 *   private int maxFileSizeMb;        ← 동일
 *
 * [After - DB 조회로 교체]
 *   필드 주입을 제거하고 메서드 내에서 sysConfigService.getString(...) 호출.
 *   메서드가 호출될 때마다 최신 DB 값을 읽는다.
 *
 * [주의]
 *   server.port는 이전 불가(RESTART_YN='Y').
 *   @Value로 유지하되 SysConfigService로 교체하지 않는다.
 * =====================================================================
 */
@Service
public class Pat1_DirectServiceCall {

    @Autowired
    private SysConfigService sysConfigService;

    // ── BEFORE: @Value 방식 (이전 전) ──────────────────────────────────

    /** @Value 방식: 서버 기동 시 1회 주입. 이후 변경 불가 */
    @Value("${server.port:9090}")
    private String localPort_BEFORE;

    /** @Value 방식: properties 파일 의존 */
    @Value("${maru.upload.max-file-size-mb:100}")
    private int maxFileSizeMb_BEFORE;

    @Value("${maru.email.sender:default@example.com}")
    private String emailSender_BEFORE;

    // ── AFTER: DB 조회 방식 (이전 후) ──────────────────────────────────

    /**
     * [이전 불가] server.port는 그대로 @Value 유지.
     * RESTART_YN='Y' 이므로 DB에 기록만 해두고 코드에서는 @Value 사용.
     */
    @Value("${server.port:9090}")
    private String localPort;   // 이 필드는 그대로 유지

    /**
     * [이전 완료] max-file-size-mb: DB에서 읽어 런타임 변경 반영.
     * @Value 필드 삭제, 메서드 호출 시마다 최신값 사용.
     */
    public int getMaxFileSizeMb() {
        return sysConfigService.getInt("maru.upload.max-file-size-mb", 100);
    }

    /**
     * [이전 완료] email.sender: DB에서 읽음.
     */
    public String getEmailSender() {
        return sysConfigService.getString("maru.email.sender", "default@example.com");
    }

    /**
     * 실제 업로드 처리 예시 (MeetingHubUploadService 패턴)
     */
    public String processUpload(byte[] fileData) {
        // @Value 대신 DB 조회 → 재시작 없이 한도 변경 가능
        int maxMb = getMaxFileSizeMb();

        if (fileData.length > maxMb * 1024 * 1024L) {
            return "파일 크기 초과: 최대 " + maxMb + "MB";
        }

        // server.port는 여전히 @Value 필드 사용 (이전 불가)
        return "업로드 완료 (port=" + localPort + ")";
    }
}
