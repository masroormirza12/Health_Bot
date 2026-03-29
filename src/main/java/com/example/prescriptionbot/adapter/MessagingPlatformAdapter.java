package com.example.prescriptionbot.adapter;

import java.util.List;

/**
 * Platform-agnostic messaging adapter interface.
 * Implement this for each messaging platform (Telegram, WhatsApp, Slack, etc.)
 * to decouple business logic from platform-specific APIs.
 */
public interface MessagingPlatformAdapter {

    /**
     * Sends a plain text message to a chat.
     *
     * @param chatId the platform-specific chat identifier
     * @param text   the message text
     */
    void sendTextMessage(String chatId, String text);

    /**
     * Sends a message with inline buttons/options.
     *
     * @param chatId the platform-specific chat identifier
     * @param text   the message text
     * @param rows   list of button rows, where each row is a list of ButtonOptions
     */
    void sendMessageWithButtons(String chatId, String text, List<List<ButtonOption>> rows);

    /**
     * Downloads a file from the platform using its file identifier.
     *
     * @param fileId the platform-specific file identifier
     * @return the file content as bytes
     * @throws Exception if download fails
     */
    byte[] downloadFile(String fileId) throws Exception;

    /**
     * Returns the name of this messaging platform (e.g., "Telegram", "WhatsApp").
     */
    String getPlatformName();
}
