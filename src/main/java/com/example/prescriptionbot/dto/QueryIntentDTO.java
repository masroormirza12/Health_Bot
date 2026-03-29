package com.example.prescriptionbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryIntentDTO {
    /**
     * Supported intents:
     * GET_MEDICINES, SEARCH_MEDICINE, GET_DOCTORS,
     * GET_PRESCRIPTION_IMAGE, GET_FAMILY_SUMMARY,
     * GET_FAMILY_TREE, GET_MEDICINE_TIMELINE,
     * GET_HOSPITALS, GET_HEALTH_INSIGHTS,
     * QUERY_TEST_RESULT, UNKNOWN
     */
    private String intentName;
    private String doctorName;
    private String startDate; // ISO format: YYYY-MM-DD
    private String endDate; // ISO format: YYYY-MM-DD
    private String role; // SELF, MOTHER, FATHER, BROTHER, OTHER — null defaults to SELF
    private String medicineName; // for SEARCH_MEDICINE intent
    private String testName; // for QUERY_TEST_RESULT intent
}
