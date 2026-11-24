package com.RevHire.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.RevHire.dto.JobDTO;
import com.RevHire.entity.Job;

import org.springframework.data.repository.query.Param;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    Logger logger = LogManager.getLogger(JobRepository.class);

    List<Job> findByStatus(String status);

    // Proiezione diretta sui DTO: una query con join, solo le 8 colonne usate
    // (niente entita' Job complete, niente caricamento dell'employer).
    @Query("""
            SELECT new com.RevHire.dto.JobDTO(
                j.jobId, j.title, j.location, j.salaryMin, j.salaryMax,
                j.jobType, j.status, e.companyName)
            FROM Job j
            JOIN j.employer e
            WHERE j.status = :status
            """)
    List<JobDTO> findJobDtosByStatus(@Param("status") String status);

    List<Job> findByLocationContainingIgnoreCase(String location);

    List<Job> findByTitleContainingIgnoreCase(String title);

    List<Job> findBySalaryMinGreaterThanEqual(Long salary);

    List<Job> findByEmployerEmployerId(Long employerId);

    Long countByEmployerEmployerId(Long employerId);

    Long countByEmployerEmployerIdAndStatus(Long employerId, String status);

    // Conteggi del dashboard employer in una sola query (totale job e job OPEN).
    // COUNT(CASE ... END) non e' mai null: con zero righe restituisce 0.
    @Query("""
            SELECT COUNT(j) AS total,
                   COUNT(CASE WHEN j.status = 'OPEN' THEN 1 END) AS active
            FROM Job j
            WHERE j.employer.employerId = :employerId
            """)
    JobCounts countJobsByEmployer(@Param("employerId") Long employerId);

    interface JobCounts {
        Long getTotal();

        Long getActive();
    }

    List<Job> findByEmployerEmployerIdOrderByTitleAsc(Long employerId);

    List<Job> findByEmployerEmployerIdOrderByJobIdDesc(Long employerId);

    List<Job> findByTitleContainingIgnoreCaseAndLocationContainingIgnoreCaseAndStatus(
            String title,
            String location,
            String status
    );

    @Query("SELECT j FROM Job j WHERE " +
            "j.title LIKE :title AND " +
            "j.location LIKE :loc AND " +
            "j.experienceRequired >= :exp AND " +
            "j.educationRequired LIKE :edu AND " +
            "j.salaryMin >= :minSal AND " +
            "j.salaryMax <= :maxSal AND " +
            "j.jobType LIKE :type AND " +
            "j.status = :status")
    List<Job> findAdvanced(String title, String loc, Integer exp, String edu,
                           BigDecimal minSal, BigDecimal maxSal, String type, String status);

    @Query("""
SELECT j FROM Job j
WHERE (:title IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :title, '%')))
AND (:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%')))
AND (:experience IS NULL OR j.experienceRequired >= :experience)
AND (:education IS NULL OR LOWER(j.educationRequired) LIKE LOWER(CONCAT('%', :education, '%')))
AND (:minSalary IS NULL OR j.salaryMin >= :minSalary)
AND (:maxSalary IS NULL OR j.salaryMax <= :maxSalary)
AND (:jobType IS NULL OR LOWER(j.jobType) LIKE LOWER(CONCAT('%', :jobType, '%')))
AND j.status = 'OPEN'
""")
    List<Job> advancedSearch(
            String title,
            String location,
            Integer experience,
            String education,
            Double minSalary,
            Double maxSalary,
            String jobType
    );

    // Stessi filtri di advancedSearch, ma restituisce direttamente i DTO.
    @Query("""
SELECT new com.RevHire.dto.JobDTO(
    j.jobId, j.title, j.location, j.salaryMin, j.salaryMax,
    j.jobType, j.status, e.companyName)
FROM Job j
JOIN j.employer e
WHERE (:title IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :title, '%')))
AND (:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%')))
AND (:experience IS NULL OR j.experienceRequired >= :experience)
AND (:education IS NULL OR LOWER(j.educationRequired) LIKE LOWER(CONCAT('%', :education, '%')))
AND (:minSalary IS NULL OR j.salaryMin >= :minSalary)
AND (:maxSalary IS NULL OR j.salaryMax <= :maxSalary)
AND (:jobType IS NULL OR LOWER(j.jobType) LIKE LOWER(CONCAT('%', :jobType, '%')))
AND j.status = 'OPEN'
""")
    List<JobDTO> advancedSearchDtos(
            @Param("title") String title,
            @Param("location") String location,
            @Param("experience") Integer experience,
            @Param("education") String education,
            @Param("minSalary") Double minSalary,
            @Param("maxSalary") Double maxSalary,
            @Param("jobType") String jobType
    );

    @Query("""
SELECT j FROM Job j
WHERE (
LOWER(j.title) LIKE LOWER(CONCAT('%', :skill, '%'))
OR LOWER(j.educationRequired) LIKE LOWER(CONCAT('%', :skill, '%'))
)
AND j.status = 'OPEN'
ORDER BY j.createdAt DESC
""")
    List<Job> findRecommendedJobs(@Param("skill") String skill);
}