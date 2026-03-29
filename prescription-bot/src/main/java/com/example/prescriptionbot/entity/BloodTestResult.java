package com.example.prescriptionbot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "blood_test_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloodTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    @Column(nullable = false)
    private String testName;

    private String resultValue;
    private String unit;
    private String referenceRange;
}
