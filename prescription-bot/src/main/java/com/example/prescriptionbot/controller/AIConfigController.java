package com.example.prescriptionbot.controller;

import com.example.prescriptionbot.dto.AIConfigDTO;
import com.example.prescriptionbot.dto.ApiResponse;
import com.example.prescriptionbot.service.AIConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing AI provider/model configuration at runtime.
 */
@RestController
@RequestMapping("/api/v1/ai/config")
public class AIConfigController {

    private static final Logger log = LoggerFactory.getLogger(AIConfigController.class);

    @Autowired
    private AIConfigurationService aiConfigurationService;

    /**
     * GET /api/v1/ai/config — Returns the current AI provider, model, and fallback
     * model.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AIConfigDTO>> getCurrentConfig() {
        log.info("GET /api/v1/ai/config — fetching current AI configuration");
        AIConfigDTO config = aiConfigurationService.getCurrentConfig();
        return ResponseEntity.ok(ApiResponse.success("Current AI configuration", config));
    }

    /**
     * PUT /api/v1/ai/config — Update provider, model, and/or fallback model at
     * runtime.
     * Only non-null fields in the request body will be updated.
     */
    @PutMapping
    public ResponseEntity<ApiResponse<AIConfigDTO>> updateConfig(@RequestBody AIConfigDTO request) {
        log.info("PUT /api/v1/ai/config — updating AI configuration: {}", request);
        try {
            AIConfigDTO updated = aiConfigurationService.updateConfig(request);
            return ResponseEntity.ok(ApiResponse.success("AI configuration updated successfully", updated));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid AI config update request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/v1/ai/config/refresh — Force rebuild the ChatClient with current
     * config.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AIConfigDTO>> refreshConfig() {
        log.info("POST /api/v1/ai/config/refresh — refreshing AI configuration");
        AIConfigDTO refreshed = aiConfigurationService.refreshConfig();
        return ResponseEntity.ok(ApiResponse.success("AI configuration refreshed", refreshed));
    }
}
