package com.example.chipset.controller;

import com.example.chipset.dto.ChipsetUploadResponse;
import com.example.chipset.service.ChipsetExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chipset")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChipsetController {

    private final ChipsetExcelService chipsetExcelService;

    /**
     * Excel 업로드 → 파싱된 데이터 반환
     * 프론트에서 SheetJS로 직접 파싱하는 방식이 주방식이므로,
     * 이 API는 서버사이드 파싱 / DB 저장이 필요할 때 사용
     */
    @PostMapping("/upload")
    public ResponseEntity<ChipsetUploadResponse> upload(
            @RequestParam("file") MultipartFile file) {
        try {
            ChipsetUploadResponse response = chipsetExcelService.parseAndSave(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 저장된 데이터 조회 (필터링은 프론트에서 수행)
     */
    @GetMapping("/data")
    public ResponseEntity<?> getData() {
        return ResponseEntity.ok(chipsetExcelService.getAllData());
    }
}
