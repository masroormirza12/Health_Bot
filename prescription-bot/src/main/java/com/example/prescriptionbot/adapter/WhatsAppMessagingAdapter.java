package com.example.prescriptionbot.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class WhatsAppMessagingAdapter implements MessagingPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessagingAdapter.class);

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendTextMessage(String chatId, String text) {
        String url = String.format("https://graph.facebook.com/v19.0/%s/messages", phoneNumberId);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", chatId);
        body.put("type", "text");
        body.put("text", Map.of("body", text));

        sendPost(url, body);
    }

    @Override
    public void sendMessageWithButtons(String chatId, String text, List<List<ButtonOption>> rows) {
        String url = String.format("https://graph.facebook.com/v19.0/%s/messages", phoneNumberId);

        // WhatsApp interactive buttons are limited to 3 buttons max in a "button" type
        // message.
        // For more, we would need a "list" type. For now, we'll flatten the rows and
        // take the first 3.
        List<Map<String, Object>> buttons = new ArrayList<>();
        rows.stream().flatMap(List::stream).limit(3).forEach(option -> {
            Map<String, Object> btn = new HashMap<>();
            btn.put("type", "reply");
            btn.put("reply", Map.of(
                    "id", option.getCallbackData(),
                    "title", truncate(option.getLabel(), 20)));
            buttons.add(btn);
        });

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "button");
        interactive.put("body", Map.of("text", text));
        interactive.put("action", Map.of("buttons", buttons));

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", chatId);
        body.put("type", "interactive");
        body.put("interactive", interactive);

        sendPost(url, body);
    }

    @Override
    public byte[] downloadFile(String fileId) throws Exception {
        // Step 1: Get media URL from Meta
        String url = String.format("https://graph.facebook.com/v19.0/%s", fileId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class);
        String downloadUrl = (String) response.getBody().get("url");

        // Step 2: Download the actual bytes
        ResponseEntity<byte[]> fileResponse = restTemplate.exchange(downloadUrl, HttpMethod.GET, entity, byte[].class);
        return fileResponse.getBody();
    }

    @Override
    public String getPlatformName() {
        return "WhatsApp";
    }

    private void sendPost(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("WhatsApp message sent. Response: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message: {}", e.getMessage(), e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null)
            return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
