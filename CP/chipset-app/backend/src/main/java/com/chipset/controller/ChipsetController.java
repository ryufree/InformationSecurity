package com.chipset.controller;

import com.chipset.model.*;
import com.chipset.service.ChipsetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/chipset")
@RequiredArgsConstructor
public class ChipsetController {

    private final ChipsetService chipsetService;

    /** XLSX 업로드 → 파싱 → DB 저장 */
    @PostMapping("/upload")
    public ResponseEntity<UploadResult> upload(
            @RequestParam("file") MultipartFile file) {
        try {
            UploadResult result = chipsetService.upload(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(UploadResult.error("서버 오류: " + e.getMessage()));
        }
    }

    /** 현재(최신 업로드) 매트릭스 조회 */
    @GetMapping("/matrix")
    public ResponseEntity<MatrixResponse> getMatrix() {
        return ResponseEntity.ok(chipsetService.getMatrix());
    }

    /** 업로드 히스토리 목록 */
    @GetMapping("/history")
    public ResponseEntity<List<ChipsetUpload>> getHistory() {
        return ResponseEntity.ok(chipsetService.getHistory());
    }

    /** 특정 히스토리 버전 매트릭스 조회 */
    @GetMapping("/history/{uploadSeq}")
    public ResponseEntity<MatrixResponse> getHistoryMatrix(
            @PathVariable Long uploadSeq) {
        return ResponseEntity.ok(chipsetService.getHistoryMatrix(uploadSeq));
    }
}
