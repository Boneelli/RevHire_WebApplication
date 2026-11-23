package com.RevHire.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.RevHire.dto.ApplicationResponseDTO;
import com.RevHire.dto.EmployerApplicationDTO;
import com.RevHire.entity.Application;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Logger logger = LogManager.getLogger(ApplicationRepository.class);

    List<Application> findBySeekerSeekerId(Long seekerId);

    List<Application> findByJobJobId(Long jobId);

    Optional<Application> findByJobJobIdAndSeekerSeekerId(Long jobId, Long seekerId);

    List<Application> findByJobEmployerEmployerId(Long employerId);

    Long countByJob_Employer_EmployerId(Long employerId);

    Long countByJob_Employer_EmployerIdAndStatus(Long employerId, String status);

    // NUOVA: candidature ricevute da un employer, in una sola query che restituisce direttamente il DTO
    @Query("""
            SELECT new com.RevHire.dto.EmployerApplicationDTO(
                a.applicationId, j.title, j.jobId, s.fullName, u.email,
                a.status, a.appliedDate, r.resumeId)
            FROM Application a
            JOIN a.job j
            JOIN a.seeker s
            JOIN s.user u
            LEFT JOIN a.resume r
            WHERE j.employer.employerId = :employerId
            """)
    List<EmployerApplicationDTO> findEmployerApplicationDTOs(@Param("employerId") Long employerId);

    // NUOVA: candidature di un seeker, in una sola query che restituisce direttamente il DTO
    @Query("""
            SELECT new com.RevHire.dto.ApplicationResponseDTO(
                a.applicationId, s.fullName, u.email, j.title, a.status, a.appliedDate)
            FROM Application a
            JOIN a.job j
            JOIN a.seeker s
            JOIN s.user u
            WHERE s.seekerId = :seekerId
            """)
    List<ApplicationResponseDTO> findSeekerApplicationDTOs(@Param("seekerId") Long seekerId);
}