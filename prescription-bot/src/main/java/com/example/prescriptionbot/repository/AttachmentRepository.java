package com.example.prescriptionbot.repository;

import com.example.prescriptionbot.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByPrescriptionId(Long prescriptionId);
}
