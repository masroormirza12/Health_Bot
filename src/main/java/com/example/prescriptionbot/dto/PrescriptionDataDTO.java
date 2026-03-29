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
public class PrescriptionDataDTO {
    private String patientName;
    private String hospitalName;
    private String doctorName;
    private String date; // E.g., YYYY-MM-DD
    private List<MedicineDTO> medicines;
}
