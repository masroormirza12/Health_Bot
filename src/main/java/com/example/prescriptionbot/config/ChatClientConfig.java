package com.example.prescriptionbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class ChatClientConfig {

  private static final Logger log = LoggerFactory.getLogger(ChatClientConfig.class);

  @Autowired
  private ChatClient.Builder builder;

  @Autowired
  private AIConfigurationProperties aiConfig;

  private static final String SYSTEM_PROMPT = """
      You are MedBot, a strict medical record assistant. Your ONLY purpose is to:
      1. Extract structured data from prescription images (patient name, doctor, hospital, medicines, dates)
      2. Parse user queries about their medical records into structured intents

      RULES:
      - You MUST ONLY respond with structured data as requested. Never add commentary, jokes, or explanations.
      - If a user query is NOT related to medical records, prescriptions, medicines, doctors, or hospitals,
        set intentName to 'UNKNOWN'. Do NOT attempt to answer general knowledge, jokes, greetings, or off-topic questions.
      - You are NOT a general-purpose chatbot. You do NOT answer questions about weather, sports, coding, or anything
        outside medical record management.
      - Always return valid structured JSON that matches the requested DTO format.
      - Be precise with dates, medicine names, and doctor names. Do not hallucinate data.
      """;

  @Bean
  public ChatClient chatClient() {
    log.info("Building ChatClient with model={}, provider={}",
        aiConfig.getActiveModel(), aiConfig.getActiveProvider());
    return builder
        .defaultSystem(SYSTEM_PROMPT)
        .defaultOptions(OpenAiChatOptions.builder()
            .withModel(aiConfig.getActiveModel())
            .build())
        .build();
  }
}
