package com.example.prescriptionbot.controller;

import com.example.prescriptionbot.dto.ApiResponse;
import com.example.prescriptionbot.service.PrescriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionQueryController {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionQueryController.class);

    @Autowired
    private PrescriptionService prescriptionService;

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<String>> queryPrescriptions(
            @RequestParam String telegramUserId,
            @RequestBody Map<String, String> request) {

        String query = request.get("query");
        log.info("POST /api/v1/prescriptions/query - telegramUserId={}, query='{}'", telegramUserId, query);

        if (query == null || query.isBlank()) {
            log.warn("Empty query received for telegramUserId={}", telegramUserId);
            return ResponseEntity.badRequest().body(ApiResponse.error("Query cannot be empty"));
        }

        String result = prescriptionService.handleQuery(telegramUserId, query);
        log.info("Query completed for telegramUserId={}", telegramUserId);

        return ResponseEntity.ok(ApiResponse.success("Success", result));
    }

    @GetMapping("/{s3Key}/url")
    public ResponseEntity<ApiResponse<String>> getPrescriptionUrl(@PathVariable String s3Key) {
        log.info("GET /api/v1/prescriptions/{}/url", s3Key);

        String url = prescriptionService.generatePresignedUrl(s3Key);
        log.info("Generated pre-signed URL for s3Key={}", s3Key);

        return ResponseEntity.ok(ApiResponse.success("URL generated", url));
    }
}
