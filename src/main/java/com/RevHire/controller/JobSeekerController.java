package com.RevHire.controller;

import com.RevHire.dto.ApplicationResponseDTO;
import com.RevHire.dto.FavoriteJobDTO;
import com.RevHire.dto.ResumeSaveDTO;
import com.RevHire.entity.*;
import com.RevHire.repository.*;
import com.RevHire.service.JobSeekerService;
import com.RevHire.service.impl.ApplicationServiceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@RestController
@RequestMapping("/api/jobseeker")
@RequiredArgsConstructor
public class JobSeekerController {

    private static final Logger logger = LogManager.getLogger(JobSeekerController.class);

    private final JobSeekerService jobSeekerService;
    private final ResumeRepository resumeRepo;
    private final FavoriteJobRepository favoriteJobRepo;
    private final JobSeekerProfileRepository profileRepo;
    private final ResumeEducationRepository educationRepo;
    private final ResumeExperienceRepository experienceRepo;
    private final ApplicationServiceImpl applicationService;
    private final ResumeCertificationRepository certificationRepo;
    private final ResumeProjectRepository projectRepo;
    private final ResumeSkillRepository resumeSkillRepo;

    @PostMapping("/profile/{userId}")
    public ResponseEntity<JobSeekerProfile> createProfile(@PathVariable Long userId, @RequestBody JobSeekerProfile profile) {
        logger.info("Creating profile for userId: {}", userId);

        return ResponseEntity.ok(jobSeekerService.createProfile(profile, userId));
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable Long userId) {
        Optional<JobSeekerProfile> profileOpt = jobSeekerService.getProfile(userId);

        if(profileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JobSeekerProfile profile = profileOpt.get();
        Map<String,Object> response = new HashMap<>();

        response.put("fullName", profile.getFullName());
        response.put("phone", profile.getPhone());
        response.put("location", profile.getLocation());

        Resume resume = resumeRepo
                .findTopBySeekerSeekerIdOrderByResumeIdDesc(profile.getSeekerId())
                .orElse(null);

        if(resume != null){
            response.put("experience", resume.getObjective());
            List<ResumeSkill> skills = resumeSkillRepo.findByResumeResumeId(resume.getResumeId());

            response.put("skills", skills
                            .stream()
                            .map(ResumeSkill::getSkillName)
                            .reduce((a,b)->a+","+b)
                            .orElse("")
            );

            List<ResumeCertification> certs = certificationRepo.findByResumeResumeId(resume.getResumeId());

            response.put("certifications", certs
                            .stream()
                            .map(ResumeCertification::getCertificationName)
                            .reduce((a,b)->a+","+b)
                            .orElse("")
            );

            List<ResumeEducation> edus = educationRepo.findByResumeResumeId(resume.getResumeId());

            if(!edus.isEmpty()){
                ResumeEducation e = edus.get(0);
                response.put("education", e.getDegree()+" - "+e.getInstitution());
            }
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable Long userId, @RequestBody Map<String,Object> data) {

        JobSeekerProfile profile = profileRepo.findByUserUserId(userId).orElseGet(() -> {
                    JobSeekerProfile newProfile = new JobSeekerProfile();

                    User user = new User();
                    user.setUserId(userId);

                    newProfile.setUser(user);
                    return newProfile;
                });

        profile.setFullName((String) data.get("fullName"));
        profile.setPhone((String) data.get("phone"));
        profile.setLocation((String) data.get("location"));

        if(data.get("employmentStatus") != null){
            profile.setCurrentEmploymentStatus((String) data.get("employmentStatus"));
            profile.setTotalExperience(parseExperience((String) data.get("employmentStatus")));
        }
        profileRepo.save(profile);

        Resume resume = resumeRepo
                .findTopBySeekerSeekerIdOrderByResumeIdDesc(profile.getSeekerId())
                .orElseGet(() -> {
                    Resume r = new Resume();
                    r.setSeeker(profile);
                    return resumeRepo.save(r);
                });

        resume.setObjective((String) data.get("experience"));
        resumeRepo.save(resume);

        int completion = calculateProfileCompletion(profile, resume);
        profile.setProfileCompletion(completion);
        profileRepo.save(profile);

        Long resumeId = resume.getResumeId();

        // [Avoid Spring repository call in loop or stream]: una sola chiamata al db fuori dal ciclo
        resumeSkillRepo.deleteByResumeResumeId(resumeId);
        String skillsStr = (String) data.get("skills");
        if (skillsStr != null && !skillsStr.isBlank()) {
            List<ResumeSkill> skills = Arrays.stream(skillsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(skillName -> {
                        ResumeSkill skill = new ResumeSkill();
                        skill.setResume(resume);
                        skill.setSkillName(skillName);
                        return skill;
                    })
                    .toList();
            resumeSkillRepo.saveAll(skills);
        }

        // [Avoid Spring repository call in loop or stream]: una sola chiamata al db fuori dal ciclo
        certificationRepo.deleteByResumeResumeId(resumeId);
        String certStr = (String) data.get("certifications");
        if (certStr != null && !certStr.isBlank()) {
            List<ResumeCertification> certifications = Arrays.stream(certStr.split(","))
                    .map(String::trim)
                    .filter(c -> !c.isEmpty())
                    .map(certName -> {
                        ResumeCertification cert = new ResumeCertification();
                        cert.setResume(resume);
                        cert.setCertificationName(certName);
                        return cert;
                    })
                    .toList();

            if (!certifications.isEmpty()) {
                certificationRepo.saveAll(certifications);
            }
        }

        educationRepo.deleteByResumeResumeId(resumeId);
        String educationVal = (String) data.get("education");

        if(educationVal != null && !educationVal.isEmpty()){
            ResumeEducation edu = new ResumeEducation();
            edu.setResume(resume);

            if(educationVal.contains(" - ")){
                String[] parts = educationVal.split(" - ");
                edu.setDegree(parts[0]);
                edu.setInstitution(parts[1]);
            }else{
                edu.setDegree(educationVal);
                edu.setInstitution("");
            }

            educationRepo.save(edu);
        }

        return ResponseEntity.ok(Map.of("message","Profile updated successfully"
        ));
    }

    @PostMapping("/resume/upload/{userId}")
    public ResponseEntity<?> uploadResume(@PathVariable Long userId, @RequestParam("file") MultipartFile file) {
        logger.info("Uploading resume for userId {}", userId);

        try {
            if (file.getSize() > 2 * 1024 * 1024) {
                logger.warn("File size exceeds limit for userId {}", userId);
                return ResponseEntity.badRequest().body(Map.of("error", "File size must be less than 2MB"));
            }

            ResumeFile savedFile = jobSeekerService.uploadResumeFile(userId, file);
            logger.info("Resume uploaded successfully: {}", savedFile.getFileName());
            return ResponseEntity.ok(Map.of("message", "Resume uploaded successfully", "fileName", savedFile.getFileName()));

        } catch (Exception e) {
            logger.error("Resume upload failed for userId {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/favorites/{seekerId}/{jobId}")
    public ResponseEntity<?> addFavorite(@PathVariable Long seekerId, @PathVariable Long jobId) {
        logger.info("Adding favorite job {} for seeker {}", jobId, seekerId);

        try {
            jobSeekerService.addFavoriteJob(seekerId, jobId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Job added to favorites successfully",
                    "jobId", jobId
            ));
        } catch (RuntimeException e) {
            logger.warn("Job already saved: jobId {}", jobId);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "error",
                    "message", "This job is already in your saved list."
            ));
        }
    }

    @GetMapping("/favorites/{seekerId}")
    public ResponseEntity<List<FavoriteJobDTO>> getFavorites(@PathVariable Long seekerId) {
        logger.info("Fetching favorite jobs for seekerId {}", seekerId);
        List<FavoriteJobDTO> favorites = jobSeekerService.getFavorites(seekerId);
        return ResponseEntity.ok(favorites);
    }

    @DeleteMapping("/favorites/{favId}")
    public void removeFavorite(@PathVariable Long favId) {
        logger.warn("Removing favorite job with id {}", favId);
        jobSeekerService.removeFavoriteJob(favId);
    }

    @GetMapping("/dashboard/{userId}")
    public ResponseEntity<?> getDashboard(@PathVariable Long userId) {
        logger.info("Loading dashboard for userId {}", userId);
        JobSeekerProfile profile = profileRepo.findByUserUserId(userId).orElseThrow(() -> new RuntimeException("Profile not found"));

        Long seekerId = profile.getSeekerId();
        long savedJobs = favoriteJobRepo.countBySeekerSeekerId(seekerId);

        List<ApplicationResponseDTO> applications = applicationService.getApplicationsBySeeker(seekerId);

        int totalApps = applications.size();

        List<ApplicationResponseDTO> recent = applications.stream().limit(5).toList();

        int score = profile.getProfileCompletion() != null ? profile.getProfileCompletion() : 0;

        Optional<Resume> resumeOpt = resumeRepo.findTopBySeekerSeekerIdOrderByResumeIdDesc(seekerId);

        if (resumeOpt.isPresent()) {
            score = calculateProfileCompletion(profile, resumeOpt.get());
            profile.setProfileCompletion(score);
            profileRepo.save(profile);
        }

        return ResponseEntity.ok(Map.of(
                "profileScore", score,
                "totalApplications", totalApps,
                "savedJobs", savedJobs,
                "recentApplications", recent
        ));
    }

    private int parseExperience(String status){
        switch(status){
            case "Fresher":
                return 0;
            case "0-2 Years Experience":
                return 1;
            case "3-5 Years Experience":
                return 4;
            case "6-10 Years Experience":
                return 8;
            case "10+ Years Experience":
                return 12;
            default: return 0;
        }
    }

    private int calculateProfileCompletion(JobSeekerProfile profile, Resume resume) {
        int score = 0;
        if(profile.getFullName()!=null && !profile.getFullName().isEmpty())
            score += 10;

        if(profile.getPhone()!=null && !profile.getPhone().isEmpty())
            score += 10;

        if(profile.getLocation()!=null && !profile.getLocation().isEmpty())
            score += 10;

        if(resume.getObjective()!=null && !resume.getObjective().isEmpty())
            score += 10;

        if(!educationRepo.findByResumeResumeId(resume.getResumeId()).isEmpty())
            score += 15;

        if(!experienceRepo.findByResumeResumeId(resume.getResumeId()).isEmpty())
            score += 15;

        if(!projectRepo.findByResumeResumeId(resume.getResumeId()).isEmpty())
            score += 10;

        if(!resumeSkillRepo.findByResumeResumeId(resume.getResumeId()).isEmpty())
            score += 10;

        if(!certificationRepo.findByResumeResumeId(resume.getResumeId()).isEmpty())
            score += 10;

        return score;
    }

    @PostMapping("/resume/save/{userId}")
    public ResponseEntity<?> saveResume(@PathVariable Long userId, @RequestBody ResumeSaveDTO dto) {
        try {
            JobSeekerProfile profile = profileRepo.findByUserUserId(userId).orElseThrow(() -> new RuntimeException("Profile not found"));

            Resume resume = resumeRepo.findTopBySeekerSeekerIdOrderByResumeIdDesc(profile.getSeekerId())
                    .orElseGet(() -> {
                        Resume r = new Resume();
                        r.setSeeker(profile);
                        return resumeRepo.save(r);
                    });

            resume.setObjective(dto.getObjective());
            resumeRepo.save(resume);
            Long resumeId = resume.getResumeId();

            resumeSkillRepo.deleteByResumeResumeId(resumeId);

            for(String skill : dto.getSkills()){
                ResumeSkill s = new ResumeSkill();
                s.setResume(resume);
                s.setSkillName(skill);
                resumeSkillRepo.save(s);
            }

            // [Avoid Spring repository call in loop or stream]: una sola chiamata al db fuori dal ciclo
            educationRepo.deleteByResumeResumeId(resumeId);
            if (dto.getEducations() != null && !dto.getEducations().isEmpty()) {
                List<ResumeEducation> educations = dto.getEducations().stream()
                        .map(e -> {
                            ResumeEducation edu = new ResumeEducation();
                            edu.setResume(resume);
                            edu.setInstitution(e.getInstitution());
                            edu.setDegree(e.getDegree());
                            return edu;
                        })
                        .toList();

                educationRepo.saveAll(educations);
            }

            // [Avoid Spring repository call in loop or stream]: una sola chiamata al db fuori dal ciclo
            experienceRepo.deleteByResumeResumeId(resumeId);
            if(dto.getExperiences()!=null && !dto.getExperiences().isEmpty()){
                List<ResumeExperience> experiences = dto.getExperiences().stream()
                        .map(e ->{
                            ResumeExperience exp=new ResumeExperience();
                            exp.setResume(resume);
                            exp.setCompanyName(e.getCompanyName());
                            exp.setRole(e.getRole());
                            return exp;
                        }).toList();
                experienceRepo.saveAll(experiences);
            }

            // [Avoid Spring repository call in loop or stream]: una sola chiamata al db fuori dal ciclo
            projectRepo.deleteByResumeResumeId(resumeId);
            if (dto.getProjects() != null && !dto.getProjects().isEmpty()) {
                List<ResumeProject> projects = dto.getProjects().stream()
                        .map(p -> {
                            ResumeProject proj = new ResumeProject();
                            proj.setResume(resume);
                            proj.setProjectTitle(p.getProjectTitle());
                            proj.setTechnologies(p.getTechnologies());
                            proj.setProjectLink(p.getProjectLink());
                            proj.setDescription(p.getDescription());
                            return proj;
                        })
                        .toList();

                projectRepo.saveAll(projects);
            }

            // [Avoid Spring repository call in loop or stream]: una sola chiamata al db fuori dal ciclo
            certificationRepo.deleteByResumeResumeId(resumeId);
            if(dto.getCertifications()!=null && !dto.getCertifications().isEmpty()){
                List<ResumeCertification> certifications = dto.getCertifications().stream()
                        .map(c ->{
                            ResumeCertification cert = new ResumeCertification();
                            cert.setResume(resume);
                            cert.setCertificationName(c.getCertificationName());
                            cert.setCompany(c.getCompany());
                            return cert;
                        })
                        .toList();

                certificationRepo.saveAll(certifications);
            }

            int completion = calculateProfileCompletion(profile, resume);
            profile.setProfileCompletion(completion);
            profileRepo.save(profile);

            return ResponseEntity.ok(Map.of("message","Resume saved successfully"));

        } catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error",e.getMessage()));
        }
    }

}