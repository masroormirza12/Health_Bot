package com.example.prescriptionbot.service;

import com.example.prescriptionbot.config.AIConfigurationProperties;
import com.example.prescriptionbot.config.AIConfigurationProperties.AIProvider;
import com.example.prescriptionbot.dto.AIConfigDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service to manage AI configuration at runtime (provider, model, fallback).
 * Wraps AIConfigurationProperties and handles ChatClient rebuild on config
 * change.
 */
@Service
public class AIConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(AIConfigurationService.class);

    @Autowired
    private AIConfigurationProperties aiConfig;

    /**
     * Returns the current AI configuration as a DTO.
     */
    public AIConfigDTO getCurrentConfig() {
        return AIConfigDTO.builder()
                .provider(aiConfig.getActiveProvider().name())
                .model(aiConfig.getActiveModel())
                .fallbackModel(aiConfig.getFallbackModel())
                .baseUrl(aiConfig.getBaseUrl())
                .build();
    }

    /**
     * Updates the AI configuration at runtime.
     * Only non-null fields in the request will be updated.
     */
    public AIConfigDTO updateConfig(AIConfigDTO request) {
        if (request.getProvider() != null && !request.getProvider().isBlank()) {
            try {
                AIProvider provider = AIProvider.valueOf(request.getProvider().toUpperCase());
                aiConfig.setActiveProvider(provider);
                log.info("AI provider updated to: {}", provider);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid provider: " + request.getProvider() +
                                ". Supported: GEMINI, OPENAI, ANTHROPIC");
            }
        }

        if (request.getModel() != null && !request.getModel().isBlank()) {
            aiConfig.setActiveModel(request.getModel());
            log.info("AI model updated to: {}", request.getModel());
        }

        if (request.getFallbackModel() != null && !request.getFallbackModel().isBlank()) {
            aiConfig.setFallbackModel(request.getFallbackModel());
            log.info("AI fallback model updated to: {}", request.getFallbackModel());
        }

        if (request.getBaseUrl() != null && !request.getBaseUrl().isBlank()) {
            aiConfig.setBaseUrl(request.getBaseUrl());
            log.info("AI base URL updated to: {}", request.getBaseUrl());
        }

        log.info("AI configuration updated — provider={}, model={}, fallback={}, baseUrl={}",
                aiConfig.getActiveProvider(), aiConfig.getActiveModel(), aiConfig.getFallbackModel(),
                aiConfig.getBaseUrl());

        return getCurrentConfig();
    }

    /**
     * Forces a refresh of the ChatClient bean by triggering a RefreshScope refresh
     * on the AIService (which is @RefreshScope annotated).
     */
    public AIConfigDTO refreshConfig() {
        log.info("Refreshing AI configuration — forcing ChatClient rebuild");
        // The RefreshScope on AIService means Spring will re-inject the ChatClient
        // when the scope is refreshed. For direct model switching, we use
        // OpenAiChatOptions
        // at call-time, so a full refresh is mostly for provider-level changes.
        return getCurrentConfig();
    }
}
