package com.example.prescriptionbot.repository;

import com.example.prescriptionbot.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    // Search medicines by name across all prescriptions for a user
    @Query("SELECT m FROM Medicine m " +
            "JOIN m.prescription p " +
            "JOIN p.person per " +
            "JOIN per.user u " +
            "WHERE u.telegramUserId = :telegramUserId " +
            "AND LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Medicine> findByNameForUser(
            @Param("telegramUserId") Long telegramUserId,
            @Param("name") String name);
}
