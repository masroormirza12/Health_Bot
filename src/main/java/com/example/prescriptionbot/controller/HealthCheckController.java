package com.example.prescriptionbot.controller;

import com.example.prescriptionbot.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        log.info("GET /api/v1/health - health check requested");
        ResponseEntity<ApiResponse<String>> response = ResponseEntity
                .ok(ApiResponse.success("Application is running healthy", "OK"));
        log.info("GET /api/v1/health - returning status OK");
        return response;
    }
}
