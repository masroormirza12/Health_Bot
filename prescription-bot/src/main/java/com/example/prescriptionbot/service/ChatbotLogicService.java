package com.example.prescriptionbot.service;

import com.example.prescriptionbot.adapter.ButtonOption;
import com.example.prescriptionbot.adapter.MessagingPlatformAdapter;
import com.example.prescriptionbot.dto.MedicineDTO;
import com.example.prescriptionbot.dto.PendingPrescription;
import com.example.prescriptionbot.dto.PendingPrescription.PendingState;
import com.example.prescriptionbot.dto.PrescriptionDataDTO;
import com.example.prescriptionbot.entity.Prescription;
import com.example.prescriptionbot.entity.RelationType;
import com.example.prescriptionbot.entity.AttachmentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core bot logic service.
 * Manages conversational state and routes requests to appropriate services.
 * Note: Rate limiting is managed by the calling controllers/services.
 */
@Service
public class ChatbotLogicService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotLogicService.class);
    private static final Duration PENDING_STATE_TTL = Duration.ofHours(24);

    @Autowired
    private PrescriptionService prescriptionService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private S3Service s3Service;

    private final Map<String, PendingStateSession> pendingMap = new ConcurrentHashMap<>();

    private record PendingStateSession(PendingPrescription pending, Instant lastActivity) {}

    @Scheduled(fixedRate = 3600000)
    public void cleanupOldStates() {
        log.info("Running scheduled cleanup for pending states...");
        Instant now = Instant.now();
        pendingMap.entrySet().removeIf(entry -> 
            Duration.between(entry.getValue().lastActivity(), now).compareTo(PENDING_STATE_TTL) > 0);
    }

    private void updateActivity(String chatId, PendingPrescription pending) {
        pendingMap.put(chatId, new PendingStateSession(pending, Instant.now()));
    }

    private PendingPrescription getPending(String chatId) {
        PendingStateSession session = pendingMap.get(chatId);
        return session != null ? session.pending() : null;
    }

    public void handleText(String chatId, String fromUser, String text, MessagingPlatformAdapter adapter) {
        log.info("Handling text from {}: text='{}'", chatId, text);
        String lowerText = text.trim().toLowerCase();

        if ("/start".equals(text) || "START".equalsIgnoreCase(text)) {
            sendMessage(adapter, chatId, "Hello 👋 I am your Pharmacy Assistant. Start by uploading a medical record image!");
            return;
        }

        if (List.of("hi", "hello", "hey", "hii", "help").contains(lowerText)) {
            sendMessage(adapter, chatId,
                "Hello! 👋 How can I help you today " + fromUser + "?\n\n" +
                "If you want to upload a new prescription, please start by attaching the picture.\n\n" +
                "Otherwise, you can ask me things like:\n" +
                "💊 'Show my prescriptions'\n" +
                "🔍 'Am I taking paracetamol?'\n" +
                "👨‍⚕️ 'Which doctors have I visited?'\n" +
                "🏥 'Which hospitals have I been to?'\n" +
                "👨‍👩‍👧‍👦 'Show family summary'\n" +
                "🙋 'Give me my summary'\n" +
                "🌳 'Show my family tree'\n" +
                "📸 'Show my last prescription image'\n" +
                "📊 'What was my mother on in January?'\n" +
                "🧠 'Analyze my health'");
            return;
        }

        PendingPrescription pending = getPending(chatId);
        if (pending != null) {
            handlePendingResponse(chatId, fromUser, text, adapter);
            return;
        }

        if (text.trim().toUpperCase().startsWith("EDIT ")) {
            handleEditCommand(chatId, text.trim(), adapter);
            return;
        }

        try {
            String response = prescriptionService.handleQuery(chatId, text);
            sendMessage(adapter, chatId, response);
        } catch (Exception ex) {
            log.error("Internal error handling query", ex);
            sendMessage(adapter, chatId, "❌ Sorry, an error occurred.");
        }
    }

    public void handleImage(String chatId, String fromUser, String fileId, MessagingPlatformAdapter adapter) {
        log.info("Handling image: chatId={}, fileId={}", chatId, fileId);
        PendingPrescription current = getPending(chatId);
        if (current != null && current.getState() == PendingState.AWAITING_ATTACHMENT_UPLOAD) {
            handleAttachmentUpload(chatId, fromUser, fileId, "photo.jpg", "image/jpeg", adapter);
            return;
        }
        sendMessage(adapter, chatId, "⏳ Processing... Please wait.");
        try {
            byte[] imageBytes = adapter.downloadFile(fileId);
            PendingPrescription pending = prescriptionService.processImage(imageBytes, "image/jpeg");
            pending.setState(PendingState.AWAITING_EDIT_OR_RELATION);
            updateActivity(chatId, pending);
            
            PrescriptionDataDTO data = pending.getData();
            StringBuilder sb = new StringBuilder("📋 Extracted prescription details:\n");
            sb.append("👤 Patient: ").append(data.getPatientName()).append(flagMissing(data.getPatientName())).append("\n");
            sb.append("👨‍⚕️ Doctor: ").append(data.getDoctorName()).append(flagMissing(data.getDoctorName())).append("\n");
            sb.append("🏥 Hospital: ").append(data.getHospitalName()).append(flagMissing(data.getHospitalName())).append("\n\n");
            sb.append("✅ Please confirm relation: SELF / MOTHER / FATHER / BROTHER / OTHER");
            
            sendMessage(adapter, chatId, sb.toString());
        } catch (Exception e) {
            log.error("Error processing image", e);
            sendMessage(adapter, chatId, "❌ Failed to process the image.");
            pendingMap.remove(chatId);
        }
    }

    public void handleDocument(String chatId, String fromUser, String fileId, String fileName, String mimeType, MessagingPlatformAdapter adapter) {
        PendingPrescription pending = getPending(chatId);
        if (pending != null && pending.getState() == PendingState.AWAITING_ATTACHMENT_UPLOAD) {
            handleAttachmentUpload(chatId, fromUser, fileId, fileName, mimeType, adapter);
        } else {
            sendMessage(adapter, chatId, "📄 Document received, but I only accept images for new prescriptions.");
        }
    }

    public void handleCallback(String chatId, String data, MessagingPlatformAdapter adapter) {
        PendingPrescription pending = getPending(chatId);
        if (pending == null) return;
        
        if ("ATTACH_DONE".equals(data)) {
            pendingMap.remove(chatId);
            sendMessage(adapter, chatId, "👍 All done!");
            return;
        }
        AttachmentType type = "ATTACH_BLOOD_TEST".equals(data) ? AttachmentType.BLOOD_TEST : null;
        if (type != null) {
            pending.setPendingAttachmentType(type);
            pending.setState(PendingState.AWAITING_ATTACHMENT_UPLOAD);
            updateActivity(chatId, pending);
            sendMessage(adapter, chatId, "Please upload your " + type.name());
        }
    }

    private void handlePendingResponse(String chatId, String fromUser, String text, MessagingPlatformAdapter adapter) {
        PendingPrescription pending = getPending(chatId);
        if (pending == null) return;
        String trimmed = text.trim().toUpperCase();
        if ("CANCEL".equals(trimmed)) {
            pendingMap.remove(chatId);
            sendMessage(adapter, chatId, "❌ Cancelled.");
            return;
        }
        if (pending.getState() == PendingState.AWAITING_EDIT_OR_RELATION) {
            handleRelationConfirmation(chatId, fromUser, trimmed, pending, adapter);
        }
    }

    private void handleEditCommand(String chatId, String text, MessagingPlatformAdapter adapter) {
        try {
            Long pId = Long.parseLong(text.substring(5).trim());
            Prescription p = prescriptionService.getPrescriptionByIdAndUserId(pId, chatId);
            PendingPrescription pending = PendingPrescription.builder().s3Key(p.getS3Key()).existingPrescriptionId(p.getId()).state(PendingState.AWAITING_EDIT_DATA).build();
            updateActivity(chatId, pending);
            sendMessage(adapter, chatId, "✏️ Editing ID " + pId);
        } catch (Exception e) {
            sendMessage(adapter, chatId, "❌ Error: " + e.getMessage());
        }
    }

    private void handleRelationConfirmation(String chatId, String fromUser, String text, PendingPrescription pending, MessagingPlatformAdapter adapter) {
        try {
            RelationType r = RelationType.valueOf(text);
            prescriptionService.confirmAndSavePrescription(chatId, fromUser, r, pending);
            sendMessage(adapter, chatId, "✅ Saved!");
            pendingMap.remove(chatId);
        } catch (Exception e) {
            sendMessage(adapter, chatId, "❌ Invalid relation. Try: SELF / MOTHER / FATHER / BROTHER / OTHER");
        }
    }

    private void handleAttachmentUpload(String chatId, String fromUser, String fileId, String fileName, String mimeType, MessagingPlatformAdapter adapter) {
        PendingPrescription pending = getPending(chatId);
        if (pending == null) return;
        try {
            byte[] bytes = adapter.downloadFile(fileId);
            prescriptionService.processAttachment(pending.getSavedPrescriptionId(), pending.getPendingAttachmentType(), bytes, fileName, mimeType);
            sendMessage(adapter, chatId, "✅ Attachment saved!");
        } catch (Exception e) {
            log.error("Error saving attachment", e);
            sendMessage(adapter, chatId, "❌ Error saving attachment.");
        }
    }

    private void sendMessage(MessagingPlatformAdapter adapter, String chatId, String text) {
        adapter.sendTextMessage(chatId, text);
    }

    private boolean isMissing(String v) {
        return v == null || v.isBlank();
    }

    private String flagMissing(String v) {
        return isMissing(v) ? " ⚠️" : "";
    }
}
