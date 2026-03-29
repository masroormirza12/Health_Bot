package com.example.prescriptionbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Holds the active AI provider, model, and fallback model configuration.
 * Bound to application.yml under ai.config.* but mutable at runtime via
 * setters.
 */
@Component
@ConfigurationProperties(prefix = "ai.config")
public class AIConfigurationProperties {

    public enum AIProvider {
        GEMINI, OPENAI, ANTHROPIC
    }

    private AIProvider activeProvider = AIProvider.GEMINI;
    private String activeModel = "gpt-4o";
    private String fallbackModel = "gemini-3-flash-preview";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";

    public AIProvider getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(AIProvider activeProvider) {
        this.activeProvider = activeProvider;
    }

    public String getActiveModel() {
        return activeModel;
    }

    public void setActiveModel(String activeModel) {
        this.activeModel = activeModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public void setFallbackModel(String fallbackModel) {
        this.fallbackModel = fallbackModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
