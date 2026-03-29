package com.example.prescriptionbot.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Telegram implementation of {@link MessagingPlatformAdapter}.
 * Wraps the Telegram SDK calls for sending messages and downloading files.
 *
 * This adapter is injected into TelegramBotService after it has initialized,
 * using the bot's own DefaultAbsSender (which is itself, as it extends
 * TelegramLongPollingBot).
 */
@Component
public class TelegramMessagingAdapter implements MessagingPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramMessagingAdapter.class);

    /**
     * Reference to the Telegram bot sender. Set at runtime by TelegramBotService
     * after construction,
     * since TelegramBotService itself is the sender (extends TelegramLongPollingBot
     * -> DefaultAbsSender).
     */
    private DefaultAbsSender botSender;

    private String botToken;

    /**
     * Initialize the adapter with the bot's sender and token.
     * Called by TelegramBotService in its @PostConstruct or constructor.
     */
    public void initialize(DefaultAbsSender sender, String token) {
        this.botSender = sender;
        this.botToken = token;
        log.info("TelegramMessagingAdapter initialized");
    }

    @Override
    public void sendTextMessage(String chatId, String text) {
        log.debug("Sending text message to chatId={}: '{}'", chatId,
                text.substring(0, Math.min(text.length(), 80)));

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        try {
            botSender.execute(msg);
            log.debug("Message sent successfully to chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    @Override
    public void sendMessageWithButtons(String chatId, String text, List<List<ButtonOption>> rows) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (List<ButtonOption> row : rows) {
            List<InlineKeyboardButton> keyboardRow = new ArrayList<>();
            for (ButtonOption option : row) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(option.getLabel());
                button.setCallbackData(option.getCallbackData());
                keyboardRow.add(button);
            }
            keyboard.add(keyboardRow);
        }

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        try {
            botSender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message with buttons to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    @Override
    public byte[] downloadFile(String fileId) throws Exception {
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);
        File file = botSender.execute(getFileMethod);

        String fileUrl = file.getFileUrl(botToken);
        try (InputStream is = new URL(fileUrl).openStream()) {
            byte[] bytes = is.readAllBytes();
            log.info("Downloaded file: {} bytes, fileId={}", bytes.length, fileId);
            return bytes;
        }
    }

    @Override
    public String getPlatformName() {
        return "Telegram";
    }
}
