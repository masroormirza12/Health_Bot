package com.example.prescriptionbot.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform-agnostic button option for interactive messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ButtonOption {
    private String label;
    private String callbackData;
}
