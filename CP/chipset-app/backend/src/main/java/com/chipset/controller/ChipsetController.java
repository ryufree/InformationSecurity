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

    /**
     * XLSX 업로드 → 자동 감지(SERVER/CLIENT/MOBILE/RAW_DATA) → DB 저장
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResult> upload(
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(chipsetService.upload(file));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(UploadResult.error("서버 오류: " + e.getMessage()));
        }
    }

    /**
     * 타입별 최신 매트릭스 조회 (SERVER / CLIENT / MOBILE)
     * GET /api/chipset/matrix?type=SERVER
     */
    @GetMapping("/matrix")
    public ResponseEntity<MatrixResponse> getMatrix(
            @RequestParam(value = "type", defaultValue = "SERVER") String type) {
        return ResponseEntity.ok(chipsetService.getMatrix(type.toUpperCase()));
    }

    /**
     * RAW_DATA 최신 조회
     * GET /api/chipset/rawdata
     */
    @GetMapping("/rawdata")
    public ResponseEntity<RawDataResponse> getRawData() {
        return ResponseEntity.ok(chipsetService.getRawData());
    }

    /**
     * 타입별 업로드 히스토리 목록
     * GET /api/chipset/history?type=SERVER
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChipsetUpload>> getHistory(
            @RequestParam(value = "type", defaultValue = "SERVER") String type) {
        return ResponseEntity.ok(chipsetService.getHistory(type.toUpperCase()));
    }

    /**
     * 특정 히스토리 버전 매트릭스 조회 (SERVER/CLIENT/MOBILE)
     */
    @GetMapping("/history/{uploadSeq}")
    public ResponseEntity<MatrixResponse> getHistoryMatrix(
            @PathVariable Long uploadSeq) {
        return ResponseEntity.ok(chipsetService.getHistoryMatrix(uploadSeq));
    }

    /**
     * 특정 히스토리 버전 RAW_DATA 조회
     */
    @GetMapping("/rawdata/history/{uploadSeq}")
    public ResponseEntity<RawDataResponse> getHistoryRawData(
            @PathVariable Long uploadSeq) {
        return ResponseEntity.ok(chipsetService.getHistoryRawData(uploadSeq));
    }
}
