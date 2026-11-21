package com.RevHire.service.impl;

import com.RevHire.entity.*;
import com.RevHire.repository.*;
import com.RevHire.service.ResumeService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final Logger logger = LogManager.getLogger(ResumeServiceImpl.class);

    private final ResumeRepository resumeRepo;
    private final ResumeEducationRepository educationRepo;
    private final ResumeExperienceRepository experienceRepo;
    private final ResumeSkillRepository skillRepo;
    private final ResumeFileRepository resumeFileRepo;
    private final ResumeCertificationRepository certRepo;
    private final ResumeProjectRepository projectRepo;
    private final JobSeekerProfileRepository profileRepo;
    private final JobSeekerProfileRepository seekerRepository;

    @Override
    public Resume createResume(final Resume resume) {
        logger.info("Creating resume for seeker");
        final Resume saved = resumeRepo.save(resume);
        logger.info("Resume created with ID: {}", saved.getResumeId());
        return saved;
    }

    @Override
    public Resume updateResume(final Long resumeId, final Resume updatedResume) {
        logger.info("Updating resume with ID: {}", resumeId);

        final Resume existing = resumeRepo.findById(resumeId).orElseThrow(() -> {
                    logger.error("Resume not found with ID: {}", resumeId);
                    return new RuntimeException("Resume not found");
                });

        existing.setObjective(updatedResume.getObjective());
        final Resume saved = resumeRepo.save(existing);
        logger.info("Resume updated successfully for ID: {}", resumeId);
        return saved;
    }

    @Override
    public void deleteResume(final Long resumeId) {
        logger.warn("Deleting resume with ID: {}", resumeId);
        resumeRepo.deleteById(resumeId);
    }

    @Override
    public Resume getResumeByUserId(final Long userId) {
        logger.info("Fetching resume for seekerId: {}", userId);

        final JobSeekerProfile seeker = seekerRepository.findByUserUserId(userId).orElseThrow(() -> {
                    logger.error("Seeker not found with id: {}", userId);
                    return new RuntimeException("Seeker not found with id: " + userId);
                });
        final Long seekerId=seeker.getSeekerId();
        final Resume resume = resumeRepo.findBySeeker_SeekerId(seekerId).orElseThrow(() -> {
                    logger.error("Resume not found for seekerId: {}", seekerId);
                    return new RuntimeException("Resume not found");
                });

        final List<ResumeFile> orderedFiles = resumeFileRepo.findByResume_ResumeIdOrderByUploadedAtDesc(resume.getResumeId());
        resume.setFiles(orderedFiles);
        logger.debug("Total resume files found: {}", orderedFiles.size());
        return resume;
    }

    @Override
    public ResumeEducation addEducation(final ResumeEducation education) {
        logger.info("Adding education to resume");
        return educationRepo.save(education);
    }

    @Override
    public List<ResumeEducation> getEducationByResume(final Long resumeId) {
        logger.info("Fetching education for resumeId: {}", resumeId);
        return educationRepo.findByResume_ResumeId(resumeId);
    }

    @Override
    public void deleteEducation(final Long educationId) {
        logger.warn("Deleting education with ID: {}", educationId);
        educationRepo.deleteById(educationId);
    }

    @Override
    public ResumeExperience addExperience(final ResumeExperience experience) {
        logger.info("Adding experience to resume");
        return experienceRepo.save(experience);
    }

    @Override
    public List<ResumeExperience> getExperienceByResume(final Long resumeId) {
        logger.info("Fetching experience for resumeId: {}", resumeId);
        return experienceRepo.findByResume_ResumeId(resumeId);
    }

    @Override
    public void deleteExperience(final Long experienceId) {
        logger.warn("Deleting experience with ID: {}", experienceId);
        experienceRepo.deleteById(experienceId);
    }

    @Override
    public ResumeSkill addSkill(final ResumeSkill skill) {
        logger.info("Adding skill to resume");
        return skillRepo.save(skill);
    }

    @Override
    public List<ResumeSkill> getSkillsByResume(final Long resumeId) {
        logger.info("Fetching skills for resumeId: {}", resumeId);
        return skillRepo.findByResume_ResumeId(resumeId);
    }

    @Override
    public void deleteSkill(final Long skillId) {
        logger.warn("Deleting skill with ID: {}", skillId);
        skillRepo.deleteById(skillId);
    }

    @Override
    @Transactional
    public void saveSkills(final Long resumeId, final List<ResumeSkill> skills) {
        logger.info("Saving skills for resumeId: {}", resumeId);

        final Resume resume = resumeRepo.findById(resumeId).orElseThrow(() -> {
                    logger.error("Resume not found while saving skills. resumeId: {}", resumeId);
                    return new RuntimeException("Resume not found");
                });

        skillRepo.deleteByResume(resume);

        for (final ResumeSkill skill : skills) {
            skill.setResume(resume);
        }

        skillRepo.saveAll(skills);
        logger.info("Skills saved successfully for resumeId: {}", resumeId);
    }

    @Override
    @Transactional
    public void saveResumeFile(final Long userId, final MultipartFile file) throws IOException {
        logger.info("Uploading resume file for userId: {}", userId);

        final Resume resume = resumeRepo.findBySeeker_User_UserId(userId).orElseThrow(() -> {
                    logger.error("Resume profile not found for userId: {}", userId);
                    return new RuntimeException("Resume profile not found");
                });

        final Path uploadPath = Paths.get("uploads");

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Uploads directory created");
        }

        final String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        final Path filePath = uploadPath.resolve(fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        final ResumeFile resumeFile = new ResumeFile();
        resumeFile.setFileName(file.getOriginalFilename());
        resumeFile.setFileSize(file.getSize());
        resumeFile.setResume(resume);
        resumeFile.setUploadedAt(LocalDateTime.now());
        resumeFile.setFilePath(filePath.toString());

        resumeFile.setFileType(file.getContentType().contains("pdf") ? "PDF" : "DOCX");
        resumeFileRepo.save(resumeFile);
        logger.info("Resume file uploaded successfully: {}", file.getOriginalFilename());
    }

    @Override
    @Transactional
    public void deleteResumeFile(final Long fileId) {
        logger.warn("Deleting resume file with ID: {}", fileId);
        if (resumeFileRepo.existsById(fileId)) {
            resumeFileRepo.deleteById(fileId);
            resumeFileRepo.flush();
        }
    }

    @Override
    public byte[] getResumeFileBytes(final Long seekerId) {
        logger.info("Fetching resume file bytes for seekerId: {}", seekerId);

        final Resume resume = resumeRepo.findBySeeker_SeekerId(seekerId).orElseThrow(() -> {
                    logger.error("No resume found for seekerId: {}", seekerId);
                    return new RuntimeException("No resume found");
                });

        final ResumeFile latestFile = resume.getFiles().stream()
                .max(Comparator.comparing(ResumeFile::getFileId))
                .orElseThrow(() -> {
                    logger.error("No resume file uploaded for seekerId: {}", seekerId);
                    return new RuntimeException("No resume file uploaded");
                });

        try {
            return Files.readAllBytes(Paths.get(latestFile.getFilePath()));
        } catch (final IOException e) {
            logger.error("Unable to read resume file", e);
            throw new RuntimeException("Unable to read resume file");
        }
    }

    @Override
    public ResponseEntity<Resource> downloadResumeFile(final Long userId) {
        logger.info("Downloading resume file for userId: {}", userId);

        final Resume resume = resumeRepo.findBySeekerSeekerId(userId)
                .orElseThrow(() -> {
                    logger.error("No resume found for userId: {}", userId);
                    return new RuntimeException("No resume found");
                });

        final ResumeFile latestFile = resume.getFiles().stream()
                .max(Comparator.comparing(ResumeFile::getFileId))
                .orElseThrow(() -> {
                    logger.error("No file metadata found for userId: {}", userId);
                    return new RuntimeException("No file metadata found");
                });

        try {
            final Path filePath = Paths.get(latestFile.getFilePath());
            final ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(filePath));
            logger.info("Resume file download prepared: {}", latestFile.getFileName());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(latestFile.getFileType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + latestFile.getFileName() + "\"")
                    .body(resource);

        } catch (final IOException e) {
            logger.error("Could not download resume file", e);
            throw new RuntimeException("Could not download file");
        }
    }

    @Override
    public ResumeCertification addCertification(final Long resumeId, final ResumeCertification cert) {
        logger.info("Adding certification to resumeId: {}", resumeId);

        final Resume resume = resumeRepo.findById(resumeId).orElseThrow(() -> {
                    logger.error("Resume not found for certification. resumeId: {}", resumeId);
                    return new RuntimeException("Resume not found");
                });

        cert.setResume(resume);
        return certRepo.save(cert);
    }

    @Override
    public List<ResumeCertification> getCertifications(final Long resumeId) {
        logger.info("Fetching certifications for resumeId: {}", resumeId);
        return certRepo.findByResumeResumeId(resumeId);
    }

    @Override
    public void deleteCertification(final Long certificationId) {
        logger.warn("Deleting certification with ID: {}", certificationId);
        certRepo.deleteById(certificationId);
    }

    @Override
    public ResumeProject addProject(final Long resumeId, final ResumeProject project) {
        logger.info("Adding project to resumeId: {}", resumeId);

        final Resume resume = resumeRepo.findById(resumeId).orElseThrow(() -> {
                    logger.error("Resume not found for project. resumeId: {}", resumeId);
                    return new RuntimeException("Resume not found");
                });

        project.setResume(resume);
        return projectRepo.save(project);
    }

    @Override
    public List<ResumeProject> getProjects(final Long resumeId) {
        logger.info("Fetching projects for resumeId: {}", resumeId);
        return projectRepo.findByResumeResumeId(resumeId);
    }

    @Override
    public void deleteProject(final Long projectId) {
        logger.warn("Deleting project with ID: {}", projectId);
        projectRepo.deleteById(projectId);
    }

    @Override
    public Resume getResumeById(final Long resumeId) {
        final Resume resume = resumeRepo.findById(resumeId).orElseThrow(() -> new RuntimeException("Resume not found"));
        final List<ResumeFile> orderedFiles = resumeFileRepo.findByResume_ResumeIdOrderByUploadedAtDesc(resumeId);
        resume.setFiles(orderedFiles);
        return resume;
    }

    @Override
    public String generateResumeHtml(final Long resumeId) {
        final Resume resume = resumeRepo.findById(resumeId).orElseThrow(() -> new RuntimeException("Resume not found"));

        final JobSeekerProfile profile = profileRepo.findById(resume.getSeeker().getSeekerId()).orElseThrow(() -> new RuntimeException("JobSeeker profile not found"));

        final List<ResumeEducation> educations = educationRepo.findByResume_ResumeId(resumeId);
        final List<ResumeExperience> experiences = experienceRepo.findByResume_ResumeId(resumeId);
        final List<ResumeSkill> skills = skillRepo.findByResume_ResumeId(resumeId);
        final List<ResumeProject> projects = projectRepo.findByResumeResumeId(resumeId);
        final List<ResumeCertification> certs = certRepo.findByResumeResumeId(resumeId);

        // [Initialize StringBuilder or StringBuffer with appropriate size]: capacità calcolata dinamicamente sui dati del CV
        final int objectiveLength = resume.getObjective() != null ? resume.getObjective().length() : 0;
        final int estimatedSize = 350 // tag HTML statici, intestazioni <h3>, stili inline e dati anagrafici
                + objectiveLength
                + (educations.size() * 80)   // ~80 char per ogni riga Education
                + (experiences.size() * 70)  // ~70 char per ogni riga Experience
                + (projects.size() * 160)    // ~160 char per ogni riga Project (titolo + descrizione)
                + (certs.size() * 75)        // ~75 char per ogni riga Certification
                + (skills.size() * 25);      // ~25 char per ogni riga Skill

        final StringBuilder html = new StringBuilder(estimatedSize);

        html.append("<html>");
        html.append("<body style='font-family:Arial; padding:40px;'>");

        html.append("<h1 style='text-align:center;'>")
                .append(profile.getFullName() != null ? profile.getFullName() : "Candidate")
                .append("</h1>");

        html.append("<p style='text-align:center;'>")
                .append(profile.getLocation() != null ? profile.getLocation() : "")
                .append(" | ")
                .append(profile.getPhone() != null ? profile.getPhone() : "")
                .append("</p>");

        html.append("<h3>Professional Summary</h3>");
        html.append("<p>").append(resume.getObjective()).append("</p>");

        html.append("<h3>Education</h3>");
        for (final ResumeEducation edu : educations) {
            html.append("<p><b>")
                    .append(edu.getDegree())
                    .append("</b> - ")
                    .append(edu.getInstitution())
                    .append("</p>");
        }

        html.append("<h3>Experience</h3>");
        for (final ResumeExperience exp : experiences) {
            html.append("<p><b>")
                    .append(exp.getRole())
                    .append("</b> - ")
                    .append(exp.getCompanyName())
                    .append("</p>");
        }

        html.append("<h3>Projects</h3>");
        for (final ResumeProject proj : projects) {
            html.append("<p><b>")
                    .append(proj.getProjectTitle())
                    .append("</b><br/>")
                    .append(proj.getDescription())
                    .append("</p>");
        }
        html.append("<h3>Certifications</h3>");
        for (final ResumeCertification cert : certs) {
            html.append("<p><b>")
                    .append(cert.getCertificationName())
                    .append("</b> - ")
                    .append(cert.getCompany())
                    .append("</p>");
        }

        html.append("<h3>Technical Skills</h3>");
        for (final ResumeSkill skill : skills) {
            html.append("<p>").append(skill.getSkillName()).append("</p>");
        }

        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    @Override
    public ResumeFile getResumeFile(final Long fileId) {
        logger.info("Fetching resume file metadata for fileId: {}", fileId);

        return resumeFileRepo.findById(fileId).orElseThrow(() -> {
                    logger.error("Resume file not found with ID: {}", fileId);
                    return new RuntimeException("File not found");});
    }
}