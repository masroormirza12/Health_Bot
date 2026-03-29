package com.example.prescriptionbot.controller;

import com.example.prescriptionbot.dto.ApiResponse;
import com.example.prescriptionbot.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/ratelimit")
public class RateLimitController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitController.class);

    @Autowired
    private RateLimitService rateLimitService;

    @PostMapping("/block/{telegramUserId}")
    public ResponseEntity<ApiResponse<String>> blockUser(@PathVariable String telegramUserId) {
        log.info("POST /api/v1/admin/ratelimit/block/{}", telegramUserId);
        rateLimitService.blockUser(telegramUserId);
        return ResponseEntity.ok(ApiResponse.success("User " + telegramUserId + " blocked", null));
    }

    @DeleteMapping("/block/{telegramUserId}")
    public ResponseEntity<ApiResponse<String>> unblockUser(@PathVariable String telegramUserId) {
        log.info("DELETE /api/v1/admin/ratelimit/block/{}", telegramUserId);
        rateLimitService.unblockUser(telegramUserId);
        return ResponseEntity.ok(ApiResponse.success("User " + telegramUserId + " unblocked", null));
    }

    @GetMapping("/blocked")
    public ResponseEntity<ApiResponse<Set<String>>> getBlockedUsers() {
        log.info("GET /api/v1/admin/ratelimit/blocked");
        return ResponseEntity.ok(ApiResponse.success("Blocked users", rateLimitService.getBlockedUsers()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Set<String>>> getActiveUsers() {
        log.info("GET /api/v1/admin/ratelimit/active");
        return ResponseEntity.ok(ApiResponse.success("Active users", rateLimitService.getActiveUsers()));
    }

    @GetMapping("/status/{telegramUserId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getUserStatus(@PathVariable String telegramUserId) {
        log.info("GET /api/v1/admin/ratelimit/status/{}", telegramUserId);
        Map<String, Boolean> status = Map.of(
                "active", rateLimitService.isActive(telegramUserId),
                "blocked", rateLimitService.isBlocked(telegramUserId));
        return ResponseEntity.ok(ApiResponse.success("User status", status));
    }
}
