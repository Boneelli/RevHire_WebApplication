package com.RevHire.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.RevHire.dto.ApplicationResponseDTO;
import com.RevHire.dto.EmployerApplicationDTO;
import com.RevHire.entity.*;
import com.RevHire.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import com.RevHire.service.NotificationService;
import com.RevHire.service.ApplicationService;

@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private static final Logger logger = LogManager.getLogger(ApplicationServiceImpl.class);

    private final ApplicationRepository applicationRepository;
    private final ApplicationNoteRepository applicationNoteRepository;
    private final JobRepository jobRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final ResumeRepository resumeRepository;
    private final NotificationService notificationService;
    private final EmployerProfileRepository employerRepository;
    private final ResumeFileRepository resumeFileRepository;

    @Override
    public Application applyJob(final Long jobId, final Long userId, final Long resumeId, final Long fileId, final String coverLetter){
        logger.info("Attempting to apply for jobId: {} by seekerId: {}", jobId, userId);

        if(applicationRepository.findByJobJobIdAndSeekerSeekerId(jobId, userId).isPresent()) {
            logger.warn("Seeker {} already applied for job {}", userId, jobId);
            throw new RuntimeException("Already applied for this job");
        }

        final Job job = jobRepository.findById(jobId).orElseThrow(() -> {
                    logger.error("Job not found with id: {}", jobId);
                    return new RuntimeException("Job not found with id: " + jobId);
                });

        final JobSeekerProfile seeker = seekerRepository.findByUserUserId(userId).orElseThrow(() -> {
                    logger.error("Seeker not found with id: {}", userId);
                    return new RuntimeException("Seeker not found with id: " + userId);
                });

        final Long seekerId=seeker.getSeekerId();

        final Resume resume = resumeRepository.findBySeeker_SeekerId(seekerId).orElseThrow(() -> new RuntimeException("Resume not found"));
        ResumeFile selectedFile = null;

        if (fileId != null) {
            selectedFile = resumeFileRepository.findById(fileId).orElseThrow(() -> new RuntimeException("Resume file not found"));

            if (!selectedFile.getResume().getResumeId().equals(resume.getResumeId())) {
                throw new RuntimeException("File does not belong to this user");
            }
        }

        if (!resume.getSeeker().getSeekerId().equals(seekerId)) {
            logger.error("Resume {} does not belong to seeker {}", resumeId, seekerId);
            throw new RuntimeException("Invalid resume selected for this seeker");
        }

        final Application application = new Application();
        application.setJob(job);
        application.setSeeker(seeker);
        application.setResume(resume);
        application.setCoverLetter(coverLetter);
        application.setStatus("APPLIED");

        final Application saved = applicationRepository.save(application);

        final Long employerUserId = job.getEmployer().getUser().getUserId();

        final String message = seeker.getFullName() + " applied for " + job.getTitle();

        notificationService.sendNotification(employerUserId, message);

        return saved;
    }

    public List<ApplicationResponseDTO> getApplicationsBySeeker(final Long seekerId) {
        logger.info("Fetching applications for userId: {}", seekerId);

        return applicationRepository.findBySeekerSeekerId(seekerId)
                .stream()
                .map(app -> new ApplicationResponseDTO(
                        app.getApplicationId(),
                        app.getSeeker().getFullName(),
                        app.getSeeker().getUser().getEmail(),
                        app.getJob().getTitle(),
                        app.getStatus(),
                        app.getAppliedDate()
                ))
                .toList();
    }

    @Override
    public void withdrawApplication(final Long applicationId, final String reason) {
        logger.info("Withdraw request received for applicationId: {}", applicationId);

        final Application app = applicationRepository.findById(applicationId).orElseThrow(() -> {
                    logger.error("Application not found with id: {}", applicationId);
                    return new RuntimeException("Application not found");
                });

        app.setStatus("WITHDRAWN");
        app.setWithdrawReason(reason);

        applicationRepository.save(app);

        logger.info("Application {} withdrawn successfully", applicationId);
    }

    @Override
    public Application updateStatus(final Long applicationId, final String status) {
        logger.info("Updating status for applicationId: {} to {}", applicationId, status);

        final Application app = applicationRepository.findById(applicationId).orElseThrow(() -> {
                    logger.error("Application not found with id: {}", applicationId);
                    return new RuntimeException("Application not found");
                });

        final List<String> validStatuses = List.of("APPLIED", "UNDER_REVIEW", "SHORTLISTED", "REJECTED", "WITHDRAWN");

        if(!validStatuses.contains(status)) {
            logger.warn("Invalid status attempted: {}", status);
            throw new RuntimeException("Invalid status value");
        }

        app.setStatus(status);
        final Application updated = applicationRepository.save(app);
        final Long seekerUserId = app.getSeeker().getUser().getUserId();
        String message;

        if(status.equals("SHORTLISTED")){
            message = "Your application for " + app.getJob().getTitle() + " was SHORTLISTED";
        }else if(status.equals("REJECTED")){
            message = "Your application for " + app.getJob().getTitle() + " was REJECTED";
        }else{
            message = "Your application status updated to " + status;
        }

        notificationService.sendNotification(seekerUserId, message);
        final Application updatedApplication = applicationRepository.save(app);
        logger.info("Application status updated successfully for applicationId: {}", applicationId);

        return updatedApplication;
    }

    @Override
    public List<ApplicationResponseDTO> getApplicationsByJob(final Long jobId) {
        logger.info("Fetching applications for jobId: {}", jobId);

        return applicationRepository.findByJobJobId(jobId)
                .stream()
                .map(app -> new ApplicationResponseDTO(
                        app.getApplicationId(),
                        app.getSeeker().getFullName(),
                        app.getSeeker().getUser().getEmail(),
                        app.getJob().getTitle(),
                        app.getStatus(),
                        app.getAppliedDate()
                ))
                .toList();
    }

    @Override
    public List<EmployerApplicationDTO> getApplicationsByEmployer(final Long userId) {
        logger.info("Fetching applications for employer userId: {}", userId);

        final EmployerProfile employer = employerRepository.findByUserUserId(userId).orElseThrow(() -> {
                    final String msg = "Employer profile not found for userId: " + userId;
                    logger.error(msg);
                    return new RuntimeException(msg);
                });

        return applicationRepository
                .findByJobEmployerEmployerId(employer.getEmployerId())
                .stream()
                .map(app -> new EmployerApplicationDTO(
                        app.getApplicationId(),
                        app.getJob().getTitle(),
                        app.getJob().getJobId(),
                        app.getSeeker().getFullName(),
                        app.getSeeker().getUser().getEmail(),
                        app.getStatus(),
                        app.getAppliedDate(),
                        app.getResume() != null ? app.getResume().getResumeId() : null
                ))
                .toList();
    }

    @Override
    public String addEmployerNotes(final Long applicationId, final Long employerId, final String noteText) {
        logger.info("Adding employer note for applicationId: {}", applicationId);

        final Application application = applicationRepository.findById(applicationId).orElseThrow(() -> {
                    logger.error("Application not found with id: {}", applicationId);
                    return new RuntimeException("Application not found");
                });

        final EmployerProfile employer = employerRepository.findById(employerId).orElseThrow(() -> {
                    logger.error("Employer not found with id: {}", employerId);
                    return new RuntimeException("Employer not found");
                });

        final ApplicationNote note = new ApplicationNote();
        note.setApplication(application);
        note.setEmployer(employer);
        note.setNoteText(noteText);
        note.setCreatedAt(LocalDateTime.now());

        applicationNoteRepository.save(note);
        logger.info("Employer note added successfully for applicationId: {}", applicationId);

        return "Note added successfully";
    }
}