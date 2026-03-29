package com.example.prescriptionbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloodTestExtractionDTO {
    private List<BloodTestRowDTO> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloodTestRowDTO {
        private String testName;
        private String resultValue;
        private String unit;
        private String referenceRange;
    }
}
