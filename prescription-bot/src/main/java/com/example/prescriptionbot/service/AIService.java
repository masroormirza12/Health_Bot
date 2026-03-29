package com.example.prescriptionbot.service;

import com.example.prescriptionbot.config.AIConfigurationProperties;
import com.example.prescriptionbot.dto.PrescriptionDataDTO;
import com.example.prescriptionbot.dto.QueryIntentDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.ai.openai.OpenAiChatOptions;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import com.example.prescriptionbot.dto.BloodTestExtractionDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RefreshScope
public class AIService {

        private static final Logger log = LoggerFactory.getLogger(AIService.class);

        @Autowired
        private ChatClient chatClient;

        @Autowired
        private AIConfigurationProperties aiConfig;

        @Autowired(required = false)
        private VectorStore vectorStore;

        public PrescriptionDataDTO extractPrescription(byte[] imageBytes) {
                String prompt = "Extract the following details from this medical prescription image: " +
                                "Patient Name, Hospital Name, Doctor Name, Date, and Medicine Details (name, dosage, frequency, duration). "
                                +
                                "RULES: " +
                                "1. If Patient Name or Medicines are completely missing, mark as 'UNKNOWN'. " +
                                "2. If Date is missing, default to '" + LocalDate.now() + "'. " +
                                "3. Any other missing fields should be set to 'DEMO'.";

                try {
                        return chatClient.prompt()
                                        .user(u -> u.text(prompt)
                                                        .media(new Media(MimeTypeUtils.IMAGE_JPEG,
                                                                        new ByteArrayResource(imageBytes))))
                                        .call()
                                        .entity(PrescriptionDataDTO.class);
                } catch (Exception e) {
                        log.warn("Primary AI call failed in extractPrescription: {}. Falling back to {}",
                                        e.getMessage(), aiConfig.getFallbackModel());
                        return chatClient.prompt()
                                        .user(u -> u.text(prompt)
                                                        .media(new Media(MimeTypeUtils.IMAGE_JPEG,
                                                                        new ByteArrayResource(imageBytes))))
                                        .options(OpenAiChatOptions.builder().withModel(aiConfig.getFallbackModel())
                                                        .build())
                                        .call()
                                        .entity(PrescriptionDataDTO.class);
                }
        }

        public QueryIntentDTO parseIntent(String query) {
                String prompt = "You are a medical assistant bot. Analyze the user query and extract structured intent.\n\n"
                                +
                                "User query: '" + query + "'\n\n" +
                                "Extract these fields:\n" +
                                "1. intentName — one of:\n" +
                                "   - GET_MEDICINES: user wants to see prescriptions or medicines (e.g. 'show my prescriptions', 'what medicines did Dr X give')\n"
                                +
                                "   - SEARCH_MEDICINE: user is searching for a specific medicine (e.g. 'am I taking paracetamol?', 'do I have crocin?')\n"
                                +
                                "   - GET_DOCTORS: user wants to see which doctors they visited (e.g. 'which doctors have I seen?')\n"
                                +
                                "   - GET_HOSPITALS: user wants to see which hospitals they visited (e.g. 'which hospitals have I been to?')\n"
                                +
                                "   - GET_PRESCRIPTION_IMAGE: user wants to see the original prescription image (e.g. 'show me my last prescription image')\n"
                                +
                                "   - GET_FAMILY_SUMMARY: user wants a medical summary — for ALL family or for a specific person (e.g. 'show family summary', 'give me my summary', 'my mother\\'s medical summary')\n"
                                +
                                "   - GET_FAMILY_TREE: user wants to see who all are linked to their account (e.g. 'show my family tree', 'who all are in my records?', 'list my family members')\n"
                                +
                                "   - GET_MEDICINE_TIMELINE: user wants medicines over a time period for a person (e.g. 'what was my mother on in January?')\n"
                                +
                                "   - GET_HEALTH_INSIGHTS: user wants AI analysis of their health based on medicines (e.g. 'what\\'s wrong with me?', 'analyze my health', 'give me health tips', 'what do my medicines mean?')\n"
                                +
                                "   - QUERY_TEST_RESULT: user asks for a specific diagnostic test value (e.g. 'what was my creatinine?', 'show my sugar levels', 'hemoglobin count')\n"
                                +
                                "   - UNKNOWN: cannot determine intent\n\n" +
                                "2. role — whose prescription is being asked about:\n" +
                                "   - SELF (default if not specified, or 'my', 'me', 'I')\n" +
                                "   - MOTHER (if they say 'mother', 'mom', 'mummy', 'amma')\n" +
                                "   - FATHER (if they say 'father', 'dad', 'daddy', 'abba')\n" +
                                "   - BROTHER (if they say 'brother', 'bro', 'bhai')\n" +
                                "   - OTHER (if they mention someone else)\n" +
                                "   - null ONLY if intent is GET_FAMILY_TREE (since it covers everyone)\n\n" +
                                "   IMPORTANT for GET_FAMILY_SUMMARY:\n" +
                                "   - If user says 'my summary' or 'give me my summary' → role = SELF\n" +
                                "   - If user says 'mother summary' → role = MOTHER\n" +
                                "   - If user says 'family summary' or 'everyone' → role = null (means ALL)\n\n" +
                                "3. doctorName — extract doctor name if mentioned, otherwise null\n" +
                                "4. startDate / endDate — ISO YYYY-MM-DD format based on time mentioned. Current year is "
                                + LocalDate.now().getYear()
                                + ". If 'last month' → compute dates. If no date mentioned → null.\n" +
                                "5. medicineName — extract medicine name if intent is SEARCH_MEDICINE, otherwise null\n"
                                +
                                "6. testName — extract test name (e.g. 'Creatinine', 'Sugar', 'HbA1c') if intent is QUERY_TEST_RESULT, otherwise null\n\n"
                                +
                                "Return ONLY the structured data, no explanation.";

                try {
                        return chatClient.prompt()
                                        .user(prompt)
                                        .call()
                                        .entity(QueryIntentDTO.class);
                } catch (Exception e) {
                        log.warn("Primary AI call failed in parseIntent: {}. Falling back to {}",
                                        e.getMessage(), aiConfig.getFallbackModel());
                        return chatClient.prompt()
                                        .user(prompt)
                                        .options(OpenAiChatOptions.builder().withModel(aiConfig.getFallbackModel())
                                                        .build())
                                        .call()
                                        .entity(QueryIntentDTO.class);
                }
        }

        /**
         * Analyzes a person's medicines and provides health insights.
         */
        public String analyzeHealth(String personName, String medicinesSummary) {
                String prompt = "You are a medical health advisor. A patient named '" + personName +
                                "' has been prescribed the following medicines across their prescriptions:\n\n" +
                                medicinesSummary + "\n\n" +
                                "Based on these medicines, provide a SHORT and helpful response with:\n" +
                                "1. 🔍 **Likely Condition**: What health issue the person might have (1-2 lines)\n" +
                                "2. 💊 **Medicine Benefits**: What each medicine does (1 line per medicine, keep brief)\n"
                                +
                                "3. 💡 **Health Tips**: 3-4 practical tips to improve their condition\n" +
                                "4. ⚠️ **Caution**: Any important warnings about these medicines (1-2 lines)\n\n" +
                                "RULES:\n" +
                                "- Keep the TOTAL response under 300 words\n" +
                                "- Use simple language, no medical jargon\n" +
                                "- Add a disclaimer that this is AI-generated and not a substitute for medical advice\n"
                                +
                                "- Format with emojis for readability";

                try {
                        return chatClient.prompt()
                                        .user(prompt)
                                        .call()
                                        .content();
                } catch (Exception e) {
                        log.warn("Primary AI call failed in analyzeHealth: {}. Falling back to {}",
                                        e.getMessage(), aiConfig.getFallbackModel());
                        return chatClient.prompt()
                                        .user(prompt)
                                        .options(OpenAiChatOptions.builder().withModel(aiConfig.getFallbackModel())
                                                        .build())
                                        .call()
                                        .content();
                }
        }

        /**
         * Extracts structured key-value pairs from a Blood Test report (PDF or Image).
         */
        public BloodTestExtractionDTO extractBloodTestResults(byte[] fileBytes, String mimeType) {
                String prompt = "You are a specialized medical lab entity extractor. " +
                                "Extract all blood test results from the attached image into a structured format. " +
                                "Return the testName, resultValue, unit, and referenceRange for each test found. " +
                                "If a field is missing, return an empty string.";

                try {
                        return chatClient.prompt()
                                        .user(u -> u.text(prompt)
                                                        .media(new Media(MimeTypeUtils.IMAGE_JPEG, // assuming jpeg for
                                                                                                   // simplicity first,
                                                                                                   // ideally dynamic
                                                                        new ByteArrayResource(fileBytes))))
                                        .call()
                                        .entity(BloodTestExtractionDTO.class);
                } catch (Exception e) {
                        log.warn("Failed to extract blood test: {}. Retrying with fallback model...", e.getMessage());
                        return chatClient.prompt()
                                        .user(u -> u.text(prompt)
                                                        .media(new Media(MimeTypeUtils.IMAGE_JPEG,
                                                                        new ByteArrayResource(fileBytes))))
                                        .options(OpenAiChatOptions.builder().withModel(aiConfig.getFallbackModel())
                                                        .build())
                                        .call()
                                        .entity(BloodTestExtractionDTO.class);
                }
        }

        /**
         * Extracts text from a Discharge Summary PDF, chunks it, and saves it into the
         * VectorStore.
         */
        public void embedDischargeSummary(Long prescriptionId, byte[] pdfBytes) {
                if (vectorStore == null) {
                        log.warn("VectorStore is not configured. Skipping Discharge Summary embedding.");
                        return;
                }

                try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        String extractedText = stripper.getText(document);

                        if (extractedText == null || extractedText.isBlank()) {
                                log.warn("No text could be extracted from the Discharge Summary PDF for prescriptionId={}",
                                                prescriptionId);
                                return;
                        }

                        // Basic chunking: split by paragraphs
                        String[] paragraphs = extractedText.split("\\n\\s*\\n");
                        List<Document> documents = java.util.Arrays.stream(paragraphs)
                                        .map(String::trim)
                                        .filter(p -> p.length() > 50) // ignore tiny spurious chunks
                                        .map(p -> new Document(p, Map.of(
                                                        "prescriptionId", prescriptionId,
                                                        "source", "DischargeSummary")))
                                        .toList();

                        vectorStore.add(documents);
                        log.info("Successfully embedded {} chunks from Discharge Summary for prescriptionId={}",
                                        documents.size(), prescriptionId);

                } catch (Exception e) {
                        log.error("Failed to parse and embed Discharge Summary PDF: {}", e.getMessage(), e);
                }
        }
}
