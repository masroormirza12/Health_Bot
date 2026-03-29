package com.example.prescriptionbot.service;

import com.example.prescriptionbot.adapter.TelegramMessagingAdapter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Comparator;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Autowired
    private ChatbotLogicService chatbotLogicService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private TelegramMessagingAdapter messagingAdapter;

    public TelegramBotService(@Value("${telegram.bot.token}") String botToken) {
        super(botToken);
    }

    @PostConstruct
    public void initAdapter() {
        messagingAdapter.initialize(this, getBotToken());
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            String chatIdStr = String.valueOf(message.getChatId());
            String fromUser = message.getFrom() != null ? message.getFrom().getFirstName() : "unknown";

            log.info("Telegram Update: chatId={}, hasText={}, hasPhoto={}", chatIdStr, message.hasText(),
                    message.hasPhoto());

            if (!rateLimitService.tryAcquire(chatIdStr)) {
                if (rateLimitService.isBlocked(chatIdStr)) {
                    messagingAdapter.sendTextMessage(chatIdStr, "🚫 Your account has been blocked.");
                } else {
                    messagingAdapter.sendTextMessage(chatIdStr,
                            "⏳ Please wait, your previous request is still being processed.");
                }
                return;
            }

            try {
                if (message.hasText()) {
                    chatbotLogicService.handleText(chatIdStr, fromUser, message.getText(), messagingAdapter);
                } else if (message.hasPhoto()) {
                    String fileId = message.getPhoto().stream()
                            .max(Comparator
                                    .comparing(org.telegram.telegrambots.meta.api.objects.PhotoSize::getFileSize))
                            .map(org.telegram.telegrambots.meta.api.objects.PhotoSize::getFileId)
                            .orElse(null);
                    chatbotLogicService.handleImage(chatIdStr, fromUser, fileId, messagingAdapter);
                } else if (message.hasDocument()) {
                    chatbotLogicService.handleDocument(chatIdStr, fromUser,
                            message.getDocument().getFileId(),
                            message.getDocument().getFileName(),
                            message.getDocument().getMimeType(),
                            messagingAdapter);
                }
            } catch (Exception e) {
                log.error("Error in TelegramBotService: {}", e.getMessage(), e);
                messagingAdapter.sendTextMessage(chatIdStr, "An error occurred: " + e.getMessage());
            } finally {
                rateLimitService.release(chatIdStr);
            }
        } else if (update.hasCallbackQuery()) {
            String chatIdStr = String.valueOf(update.getCallbackQuery().getMessage().getChatId());
            String data = update.getCallbackQuery().getData();
            if (rateLimitService.tryAcquire(chatIdStr)) {
                try {
                    chatbotLogicService.handleCallback(chatIdStr, data, messagingAdapter);
                } finally {
                    rateLimitService.release(chatIdStr);
                }
            }
        }
    }
}
