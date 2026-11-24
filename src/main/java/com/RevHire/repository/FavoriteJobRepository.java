package com.RevHire.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.RevHire.dto.FavoriteJobDTO;
import com.RevHire.entity.FavoriteJob;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoriteJobRepository extends JpaRepository<FavoriteJob, Long> {

    Logger logger = LogManager.getLogger(FavoriteJobRepository.class);

    List<FavoriteJob> findBySeekerSeekerId(Long seekerId);

    // Una sola query con join: restituisce direttamente i DTO, senza caricare
    // FavoriteJob, Job ed EmployerProfile come entita' (evita il N+1).
    @Query("""
            SELECT new com.RevHire.dto.FavoriteJobDTO(
                f.favId, j.jobId, j.title, j.location, j.salaryMin, j.salaryMax,
                j.jobType, j.status, e.companyName)
            FROM FavoriteJob f
            JOIN f.job j
            JOIN j.employer e
            WHERE f.seeker.seekerId = :seekerId
            ORDER BY f.favId
            """)
    List<FavoriteJobDTO> findFavoriteDtosBySeekerId(@Param("seekerId") Long seekerId);

    boolean existsBySeekerSeekerIdAndJobJobId(Long seekerId, Long jobId);

    long countBySeekerSeekerId(Long seekerId);

}