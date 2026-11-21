package com.RevHire.controller;

import com.RevHire.dto.ApplicationResponseDTO;
import com.RevHire.dto.FavoriteJobDTO;
import com.RevHire.dto.JobDTO;
import com.RevHire.dto.ProfileUpdateDTO;
import com.RevHire.entity.JobSeekerProfile;
import com.RevHire.entity.Resume;
import com.RevHire.entity.ResumeSkill;
import com.RevHire.entity.User;
import com.RevHire.repository.JobSeekerProfileRepository;
import com.RevHire.repository.ResumeRepository;
import com.RevHire.repository.ResumeSkillRepository;
import com.RevHire.repository.UserRepository;
import com.RevHire.service.ApplicationService;
import com.RevHire.service.JobSeekerService;
import com.RevHire.service.ResumeService;
import com.RevHire.service.impl.JobServiceImpl;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Controller
@RequestMapping("/jobseeker")
@RequiredArgsConstructor
public class JobSeekerUIController {

    private static final Logger logger = LogManager.getLogger(JobSeekerUIController.class);

    private final JobSeekerService jobSeekerService;
    private final ResumeService resumeService;
    private final ResumeRepository resumeRepo;
    private final JobServiceImpl jobService;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;
    private final ResumeSkillRepository resumeSkillRepo;
    private final JobSeekerProfileRepository profileRepo;

    @GetMapping("/dashboard/{userId}")
    public String dashboard(@PathVariable Long userId, Model model, HttpSession session) {
        logger.info("Loading dashboard for userId {}", userId);

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        JobSeekerProfile profile = jobSeekerService.getProfile(userId).orElse(null);

        String displayName = "Seeker";
        int score = 0;

        if (profile != null) {
            displayName = (profile.getFullName() != null && !profile.getFullName().trim().isEmpty())
                    ? profile.getFullName() : "Seeker";
            score = profile.getProfileCompletion() != null ? profile.getProfileCompletion() : 0;
        }

        session.setAttribute("userId", user.getUserId());
        session.setAttribute("userName", displayName);
        session.setAttribute("userInitial", displayName.substring(0, 1).toUpperCase());
        session.setAttribute("userEmail", user.getEmail());

        model.addAttribute("userId", userId);
        model.addAttribute("userName", displayName);
        model.addAttribute("profileScore", score);
        model.addAttribute("activePage", "dashboard");

        return "jobseeker/dashboard";
    }

    @GetMapping("/profile/manage")
    public String manageProfile(HttpSession session, Model model) {
        logger.info("Opening manage profile page");

        if (session.getAttribute("userId") == null) {
            logger.warn("Unauthorized access to profile manage page");
            return "redirect:/auth/login";
        }

        model.addAttribute("activePage", "profile");
        return "jobseeker/profile";
    }

    @GetMapping("/edit-profile")
    public String editProfile(HttpSession session) {
        logger.info("Opening edit profile page");

        if (session.getAttribute("userId") == null) {
            logger.warn("Unauthorized edit profile access");
            return "redirect:/auth/login";
        }

        return "jobseeker/edit-profile";
    }

    @GetMapping("/resume/builder")
    public String showResumeBuilder(HttpSession session, Model model) {
        logger.info("Opening resume builder");

        if (session.getAttribute("userId") == null) {
            logger.warn("Unauthorized resume builder access");
            return "redirect:/auth/login";
        }

        model.addAttribute("activePage", "builder");
        return "jobseeker/resume-builder";
    }

    @GetMapping("/resume/upload")
    public String showUploadPage(HttpSession session, Model model) {
        logger.info("Opening resume upload page");

        if (session.getAttribute("userId") == null) {
            logger.warn("Unauthorized resume upload access");
            return "redirect:/auth/login";
        }

        model.addAttribute("activePage", "upload");
        return "jobseeker/resume-upload";
    }

    @GetMapping("/resume/view")
    public String viewResume(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        logger.info("Viewing resume for userId {}", userId);

        if (userId == null) {
            logger.warn("Unauthorized resume view attempt");
            return "redirect:/auth/login";
        }

        Optional<JobSeekerProfile> profileOpt = jobSeekerService.getProfile(userId);

        if (profileOpt.isEmpty()) {
            model.addAttribute("resume", null);
            return "jobseeker/view-resume";
        }

        JobSeekerProfile profile = profileOpt.get();
        model.addAttribute("profile", profile);
        Optional<Resume> resumeOpt = resumeRepo.findBySeekerSeekerId(profile.getSeekerId());

        if (resumeOpt.isEmpty()) {
            model.addAttribute("resume", null);
            return "jobseeker/view-resume";
        }

        Resume resume = resumeOpt.get();
        model.addAttribute("resume", resume);

        Long resumeId = resume.getResumeId();

        model.addAttribute("educations", resumeService.getEducationByResume(resumeId));
        model.addAttribute("experiences", resumeService.getExperienceByResume(resumeId));
        model.addAttribute("projects", resumeService.getProjects(resumeId));
        model.addAttribute("certifications", resumeService.getCertifications(resumeId));
        model.addAttribute("skills", resumeService.getSkillsByResume(resumeId));

        return "jobseeker/view-resume";
    }

    @GetMapping("/jobs/search")
    public String showSearchPage(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer experience,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) Double minSalary,
            @RequestParam(required = false) Double maxSalary,
            @RequestParam(required = false) String jobType,
            HttpSession session,
            Model model) {

        logger.info("Searching jobs with filters: title={}, location={}", title, location);

        if (session.getAttribute("userId") == null) {
            logger.warn("Unauthorized job search access");
            return "redirect:/auth/login";
        }

        List<JobDTO> filteredJobs = jobService.searchJobs(title, location, experience, education, minSalary, maxSalary, jobType);

        model.addAttribute("jobs", filteredJobs);
        model.addAttribute("activePage", "search");

        return "jobseeker/search-jobs";
    }

    @GetMapping("/applications")
    public String showApplicationsPage(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        logger.info("Fetching applications for userId {}", userId);

        if (userId == null){
            return "redirect:/auth/login";
        }

        jobSeekerService.getProfile(userId).ifPresent(profile -> {
            List<ApplicationResponseDTO> apps = applicationService.getApplicationsBySeeker(profile.getSeekerId());

            model.addAttribute("applications", apps);
            model.addAttribute("activePage", "applications");
        });

        return "jobseeker/applications";
    }

    @GetMapping("/jobs/saved")
    public String showSavedJobs(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        logger.info("Loading saved jobs for userId {}", userId);

        if (userId == null) {
            return "redirect:/auth/login";
        }

        jobSeekerService.getProfile(userId).ifPresent(profile -> {
            List<FavoriteJobDTO> favorites = jobSeekerService.getFavorites(profile.getSeekerId());

            model.addAttribute("savedJobs", favorites);
            model.addAttribute("activePage", "saved");
        });

        return "jobseeker/saved-jobs";
    }

    @Transactional
    public void updateFullProfile(Long userId, ProfileUpdateDTO dto) {
        logger.info("Updating full profile for userId {}", userId);
        JobSeekerProfile profile = profileRepo.findByUserUserId(userId).orElseThrow(() -> new RuntimeException("Profile not found"));

        profile.setFullName(dto.getFullName());
        profile.setPhone(dto.getPhone());
        profile.setLocation(dto.getLocation());
        profileRepo.save(profile);

        Resume resume = resumeRepo.findBySeekerSeekerId(profile.getSeekerId()).orElseGet(() -> {
                    Resume newR = new Resume();
                    newR.setSeeker(profile);
                    return resumeRepo.save(newR);
                });

        resume.setObjective(dto.getJobTitle());
        resumeRepo.save(resume);

        // [Avoid Spring repository call in loop or stream]: una sola chiamata al db fuori dal ciclo
        resumeSkillRepo.deleteByResumeResumeId(resume.getResumeId());
        if (dto.getSkills() != null && !dto.getSkills().trim().isEmpty()) {
            List<ResumeSkill> skills = Arrays.stream(dto.getSkills().split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .map(name -> {
                        ResumeSkill rs = new ResumeSkill();
                        rs.setResume(resume);
                        rs.setSkillName(name);
                        return rs;
                    })
                    .toList();

            if (!skills.isEmpty()) {
                resumeSkillRepo.saveAll(skills);
            }
        }

        int completion = calculateCompletion(profile, resume);
        profile.setProfileCompletion(completion);
        profileRepo.save(profile);
    }

    private int calculateCompletion(JobSeekerProfile p, Resume r) {
        int count = 0;

        if (p.getFullName() != null) count += 20;
        if (p.getPhone() != null) count += 20;
        if (p.getLocation() != null) count += 20;
        if (r.getObjective() != null) count += 20;

        if (!resumeSkillRepo.findByResumeResumeId(r.getResumeId()).isEmpty()) {
            count += 20;
        }
        return count;
    }
}