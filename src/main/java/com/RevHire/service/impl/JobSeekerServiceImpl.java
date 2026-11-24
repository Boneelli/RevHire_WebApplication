package com.RevHire.service.impl;

import com.RevHire.dto.FavoriteJobDTO;
import com.RevHire.dto.JobDTO;
import com.RevHire.entity.*;
import com.RevHire.repository.*;
import com.RevHire.service.JobSeekerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class JobSeekerServiceImpl implements JobSeekerService {

    private static final Logger logger = LogManager.getLogger(JobSeekerServiceImpl.class);

    private final JobSeekerProfileRepository profileRepo;
    private final ResumeRepository resumeRepo;
    private final ResumeFileRepository resumeFileRepo;
    private final FavoriteJobRepository favoriteJobRepo;
    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final JobRepository jobRepo;
    private final ResumeSkillRepository resumeSkillRepo;
    private final JobSeekerProfileRepository seekerRepository;

    @Override
    public JobSeekerProfile createProfile(final JobSeekerProfile profile, final Long userId) {
        logger.info("Creating profile for userId: {}", userId);

        final User user = userRepo.findById(userId).orElseThrow(() -> {
            logger.error("User not found with ID: {}", userId);
            return new RuntimeException("User not found with ID: " + userId);
        });

        profile.setUser(user);
        final JobSeekerProfile savedProfile = profileRepo.save(profile);
        logger.info("Profile created successfully for userId: {}", userId);

        return savedProfile;
    }

    @Override
    public Optional<JobSeekerProfile> getProfile(final Long userId) {
        logger.info("Fetching profile for userId: {}", userId);
        return Optional.ofNullable(profileRepo.findByUserUserId(userId).orElse(null));
    }

    @Override
    public JobSeekerProfile updateProfile(final Long profileId, final JobSeekerProfile profile) {
        logger.info("Updating profile with ID: {}", profileId);

        final JobSeekerProfile existing = profileRepo.findById(profileId).orElseThrow(() -> {
            logger.error("Profile not found with ID: {}", profileId);
            return new RuntimeException("Profile not found");
        });

        existing.setFullName(profile.getFullName());
        existing.setPhone(profile.getPhone());
        existing.setLocation(profile.getLocation());
        existing.setCurrentEmploymentStatus(profile.getCurrentEmploymentStatus());
        existing.setTotalExperience(profile.getTotalExperience());
        existing.setProfileCompletion(profile.getProfileCompletion());

        final JobSeekerProfile updated = profileRepo.save(existing);
        logger.info("Profile updated successfully with ID: {}", profileId);
        return updated;
    }

    @Override
    public Resume getOrCreateResume(final Long userId) {
        logger.info("Fetching or creating resume for userId: {}", userId);

        final JobSeekerProfile profile = profileRepo.findByUserUserId(userId).orElseThrow(() -> {
            logger.error("Profile not found for userId: {}", userId);
            return new RuntimeException("Profile not found. Create profile first.");
        });

        return resumeRepo.findBySeekerSeekerId(profile.getSeekerId()).orElseGet(() -> {
            logger.info("Creating new resume for seekerId: {}", profile.getSeekerId());
            final Resume newResume = new Resume();
            newResume.setSeeker(profile);
            return resumeRepo.save(newResume);
        });
    }

    @Override
    public ResumeFile uploadResumeFile(final Long userId, final MultipartFile file) {
        logger.info("Uploading resume for userId: {}", userId);
        final JobSeekerProfile profile = profileRepo.findByUserUserId(userId)
                .orElseThrow(() -> {
                    logger.error("Profile not found while uploading resume for userId: {}", userId);
                    return new RuntimeException("Profile not found");
                });

        final Resume resume = getOrCreateResume(userId);

        try {
            final String projectPath = System.getProperty("user.dir");
            final String uploadDir = projectPath + File.separator + "uploads" + File.separator + "resumes";

            final File folder = new File(uploadDir);
            if (!folder.exists()) folder.mkdirs();

            final String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            final File destination = new File(uploadDir + File.separator + fileName);

            file.transferTo(destination);

            final ResumeFile resumeFile = new ResumeFile();
            resumeFile.setResume(resume);
            resumeFile.setFileName(fileName);
            resumeFile.setFilePath(destination.getAbsolutePath());
            resumeFile.setFileSize(file.getSize());

            final String originalName = file.getOriginalFilename();
            final String extension = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf(".") + 1).toUpperCase()
                    : "UNKNOWN";

            resumeFile.setFileType(extension);
            final ResumeFile savedFile = resumeFileRepo.save(resumeFile);

            logger.info("Resume uploaded successfully for userId: {}", userId);
            return savedFile;

        } catch (final IOException e) {
            logger.error("Error saving resume file for userId: {}", userId, e);
            throw new RuntimeException("FileSystem Error: Could not save file", e);
        }
    }

    @Override
    public FavoriteJob addFavoriteJob(final Long seekerId, final Long jobId) {
        logger.info("Adding favorite job. seekerId: {}, jobId: {}", seekerId, jobId);

        final JobSeekerProfile seeker = seekerRepository.findByUserUserId(seekerId).orElseThrow(() -> {
            logger.error("Seeker not found with ID: {}", seekerId);
            return new RuntimeException("Seeker not found");
        });

        final Job job = jobRepo.findById(jobId).orElseThrow(() -> {
            logger.error("Job not found with ID: {}", jobId);
            return new RuntimeException("Job not found");
        });

        if (favoriteJobRepo.existsBySeekerSeekerIdAndJobJobId(seekerId, jobId)) {
            logger.warn("Job already added to favorites. seekerId: {}, jobId: {}", seekerId, jobId);
            throw new RuntimeException("Already added to favorites");
        }

        final FavoriteJob fav = new FavoriteJob();
        fav.setSeeker(seeker);
        fav.setJob(job);

        final FavoriteJob saved = favoriteJobRepo.save(fav);
        logger.info("Favorite job added successfully. favId: {}", saved.getFavId());

        return saved;
    }

    @Override
    public List<FavoriteJobDTO> getFavorites(final Long seekerId) {
        logger.info("Fetching favorite jobs for seekerId: {}", seekerId);
        return favoriteJobRepo.findFavoriteDtosBySeekerId(seekerId);
    }

    @Override
    public void removeFavoriteJob(final Long favId) {
        logger.info("Removing favorite job with ID: {}", favId);
        favoriteJobRepo.deleteById(favId);
    }

    @Override
    public void markNotificationAsRead(final Long notificationId) {
        logger.info("Marking notification as read. notificationId: {}", notificationId);

        final Notification notification = notificationRepo.findById(notificationId).orElseThrow(() -> {
            logger.error("Notification not found with ID: {}", notificationId);
            return new RuntimeException("Notification not found");
        });

        notification.setIsRead(true);
        notificationRepo.save(notification);
    }

    @Override
    public List<JobDTO> searchJobs(final String title, final String location, final Integer exp, final String edu, final Double minSal, final Double maxSal, final String type) {
        logger.info("Searching jobs with filters");

        final BigDecimal bMin = (minSal != null) ? BigDecimal.valueOf(minSal) : BigDecimal.ZERO;

        final BigDecimal bMax = (maxSal != null) ? BigDecimal.valueOf(maxSal) : BigDecimal.valueOf(9999999);

        final List<Job> jobs = jobRepo.findAdvanced(
                title != null ? "%" + title + "%" : "%",
                location != null ? "%" + location + "%" : "%",
                exp != null ? exp : 0,
                edu != null ? "%" + edu + "%" : "%",
                bMin,
                bMax,
                type != null ? "%" + type + "%" : "%",
                "OPEN"
        );

        logger.info("Jobs found: {}", jobs.size());
        return jobs.stream().map(this::convertToDTO).toList();
    }

    private JobDTO convertToDTO(final Job job) {
        final String companyName = (job.getEmployer() != null) ? job.getEmployer().getCompanyName() : "Unknown Company";

        return new JobDTO(
                job.getJobId(),
                job.getTitle(),
                job.getLocation(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getJobType(),
                job.getStatus(),
                companyName
        );
    }

    @Override
    public List<Job> getRecommendedJobs(final List<String> skills) {
        logger.info("Fetching recommended jobs based on skills");

        if (skills == null || skills.isEmpty()) {
            logger.warn("No skills provided for job recommendation");
            return List.of();
        }

        final List<Job> allJobs = jobRepo.findAll();

        final List<Job> recommendedJobs = allJobs.stream()
                .filter(job ->
                        skills.stream().anyMatch(skill ->
                                job.getTitle() != null &&
                                        job.getTitle().toLowerCase().contains(skill.toLowerCase())
                        )
                )
                .limit(10)
                .toList();
        return recommendedJobs;
    }
}