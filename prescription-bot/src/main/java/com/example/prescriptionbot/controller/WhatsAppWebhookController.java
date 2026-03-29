package com.example.prescriptionbot.controller;

import com.example.prescriptionbot.adapter.WhatsAppMessagingAdapter;
import com.example.prescriptionbot.dto.whatsapp.WhatsAppWebhookPayload;
import com.example.prescriptionbot.service.ChatbotLogicService;
import com.example.prescriptionbot.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    @Value("${whatsapp.verify-token}")
    private String verifyToken;

    @Autowired
    private ChatbotLogicService chatbotLogicService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private WhatsAppMessagingAdapter whatsappAdapter;

    /**
     * Webhook verification for Meta (GET request)
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        log.info("WhatsApp Webhook Verification: mode={}, token={}", mode, token);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook Verified!");
            return ResponseEntity.ok(challenge);
        } else {
            log.warn("Webhook Verification Failed!");
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * Receiving messages from Meta (POST request)
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody WhatsAppWebhookPayload payload) {
        log.info("Received WhatsApp Webhook Payload: {}", payload);

        try {
            if (payload.getEntry() != null) {
                for (WhatsAppWebhookPayload.Entry entry : payload.getEntry()) {
                    if (entry.getChanges() != null) {
                        for (WhatsAppWebhookPayload.Change change : entry.getChanges()) {
                            processValue(change.getValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing WhatsApp webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void processValue(WhatsAppWebhookPayload.Value value) {
        if (value.getMessages() == null)
            return;

        for (WhatsAppWebhookPayload.Message message : value.getMessages()) {
            String chatId = message.getFrom();
            String fromUser = "WhatsApp User"; // Meta doesn't always send the name in the message object directly

            log.info("WhatsApp Message from {}: type={}", chatId, message.getType());

            // Rate Limiting
            if (!rateLimitService.tryAcquire(chatId)) {
                log.warn("Rate limit hit for WhatsApp chatId={}", chatId);
                return;
            }

            try {
                if ("text".equals(message.getType()) && message.getText() != null) {
                    chatbotLogicService.handleText(chatId, fromUser, message.getText().getBody(), whatsappAdapter);
                } else if ("image".equals(message.getType()) && message.getImage() != null) {
                    chatbotLogicService.handleImage(chatId, fromUser, message.getImage().getId(), whatsappAdapter);
                } else if ("document".equals(message.getType()) && message.getDocument() != null) {
                    chatbotLogicService.handleDocument(chatId, fromUser,
                            message.getDocument().getId(),
                            message.getDocument().getFilename(),
                            message.getDocument().getMime_type(),
                            whatsappAdapter);
                } else if ("interactive".equals(message.getType()) && message.getInteractive() != null) {
                    if ("button_reply".equals(message.getInteractive().getType())) {
                        String callbackData = message.getInteractive().getButtonReply().getId();
                        chatbotLogicService.handleCallback(chatId, callbackData, whatsappAdapter);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process message from {}: {}", chatId, e.getMessage());
            } finally {
                rateLimitService.release(chatId);
            }
        }
    }
}
