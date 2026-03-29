package com.example.prescriptionbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingPrescription {
    private PrescriptionDataDTO data;
    private String s3Key;
    private Long existingPrescriptionId;
    private Long savedPrescriptionId; // ID after it's saved in DB, so we can link attachments
    private com.example.prescriptionbot.entity.AttachmentType pendingAttachmentType;

    /**
     * State machine for the pending prescription flow:
     * AWAITING_EDIT_OR_RELATION - user can reply with EDIT or a RelationType
     * AWAITING_EDIT_DATA - user is editing fields (doctor/hospital/patient/date)
     * AWAITING_RELATION - user must provide a RelationType to finalize
     */
    @Builder.Default
    private PendingState state = PendingState.AWAITING_EDIT_OR_RELATION;

    public enum PendingState {
        AWAITING_EDIT_OR_RELATION,
        AWAITING_EDIT_DATA,
        AWAITING_RELATION,
        AWAITING_ATTACHMENT_CHOICE,
        AWAITING_ATTACHMENT_UPLOAD
    }
}
