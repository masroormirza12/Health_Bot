package com.example.prescriptionbot.repository;

import com.example.prescriptionbot.entity.BloodTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BloodTestResultRepository extends JpaRepository<BloodTestResult, Long> {
    List<BloodTestResult> findByAttachmentId(Long attachmentId);
}
