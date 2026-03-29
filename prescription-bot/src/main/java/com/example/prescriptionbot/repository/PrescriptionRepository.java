package com.example.prescriptionbot.repository;

import com.example.prescriptionbot.entity.Prescription;
import com.example.prescriptionbot.entity.RelationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

        // ── GET BY ID AND USER ──
        @Query("SELECT p FROM Prescription p JOIN p.person per JOIN per.user u WHERE p.id = :id AND u.telegramUserId = :telegramUserId")
        Optional<Prescription> findByIdAndUserTelegramId(@Param("id") Long id,
                        @Param("telegramUserId") String telegramUserId);

        // ── GET_MEDICINES: fixed query with JOIN FETCH, LOWER/LIKE, and optional role
        // ──
        @Query("SELECT DISTINCT p FROM Prescription p " +
                        "JOIN FETCH p.person per " +
                        "JOIN FETCH per.user u " +
                        "LEFT JOIN FETCH p.medicines " +
                        "WHERE u.telegramUserId = :telegramUserId " +
                        "AND (:role IS NULL OR per.relationType = :role) " +
                        "AND LOWER(p.doctorName) LIKE LOWER(CONCAT('%', :doctorName, '%')) " +
                        "AND p.visitDate >= :startDate " +
                        "AND p.visitDate <= :endDate")
        List<Prescription> findByFilters(
                        @Param("telegramUserId") String telegramUserId,
                        @Param("role") RelationType role,
                        @Param("doctorName") String doctorName,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // ── SEARCH_MEDICINE: find prescriptions containing a specific medicine ──
        @Query("SELECT DISTINCT p FROM Prescription p " +
                        "JOIN FETCH p.person per " +
                        "JOIN FETCH per.user u " +
                        "LEFT JOIN FETCH p.medicines m " +
                        "WHERE u.telegramUserId = :telegramUserId " +
                        "AND (:role IS NULL OR per.relationType = :role) " +
                        "AND LOWER(m.name) LIKE LOWER(CONCAT('%', :medicineName, '%'))")
        List<Prescription> findByMedicineName(
                        @Param("telegramUserId") String telegramUserId,
                        @Param("role") RelationType role,
                        @Param("medicineName") String medicineName);

        // ── GET_DOCTORS: distinct doctor names for a user ──
        @Query("SELECT DISTINCT p.doctorName FROM Prescription p " +
                        "JOIN p.person per " +
                        "JOIN per.user u " +
                        "WHERE u.telegramUserId = :telegramUserId " +
                        "AND p.doctorName IS NOT NULL")
        List<String> findDistinctDoctors(@Param("telegramUserId") String telegramUserId);

        // ── GET_HOSPITALS: distinct hospital names for a user ──
        @Query("SELECT DISTINCT p.hospitalName FROM Prescription p " +
                        "JOIN p.person per " +
                        "JOIN per.user u " +
                        "WHERE u.telegramUserId = :telegramUserId " +
                        "AND p.hospitalName IS NOT NULL")
        List<String> findDistinctHospitals(@Param("telegramUserId") String telegramUserId);

        // ── GET_FAMILY_SUMMARY: all prescriptions grouped by person (eager) ──
        @Query("SELECT DISTINCT p FROM Prescription p " +
                        "JOIN FETCH p.person per " +
                        "JOIN FETCH per.user u " +
                        "LEFT JOIN FETCH p.medicines " +
                        "WHERE u.telegramUserId = :telegramUserId " +
                        "ORDER BY per.relationType, p.visitDate DESC")
        List<Prescription> findAllForFamily(@Param("telegramUserId") String telegramUserId);

        // ── GET_PRESCRIPTION_IMAGE / GET_MEDICINE_TIMELINE: latest prescriptions ──
        @Query("SELECT DISTINCT p FROM Prescription p " +
                        "JOIN FETCH p.person per " +
                        "JOIN FETCH per.user u " +
                        "LEFT JOIN FETCH p.medicines " +
                        "WHERE u.telegramUserId = :telegramUserId " +
                        "AND (:role IS NULL OR per.relationType = :role) " +
                        "ORDER BY p.visitDate DESC")
        List<Prescription> findLatestByUser(
                        @Param("telegramUserId") String telegramUserId,
                        @Param("role") RelationType role);

        List<Prescription> findByPersonIdOrderByVisitDateDesc(Long personId);
}
