package com.RevHire.controller;

import com.RevHire.dto.ApplicationResponseDTO;
import com.RevHire.dto.EmployerApplicationDTO;
import com.RevHire.dto.NoteRequestDTO;
import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.RevHire.service.ApplicationService;

import java.util.List;

@Controller
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private static final Logger logger = LogManager.getLogger(ApplicationController.class);

    private final ApplicationService applicationService;

    @GetMapping("/jobseeker/jobs/apply/{jobId}")
    public String showApplyPage(@PathVariable final Long jobId, final Model model, final HttpSession session) {

        logger.info("Opening apply page for jobId: {}", jobId);

        final Object userId = session.getAttribute("userId");
        if (userId == null) {
            logger.warn("User not logged in. Redirecting to login page.");
            return "redirect:/auth/login";
        }

        model.addAttribute("jobId", jobId);
        model.addAttribute("userId", userId);
        model.addAttribute("applications", List.of());

        logger.info("Apply page loaded successfully for jobId: {}", jobId);

        return "jobseeker/applications";
    }

    @PostMapping("/submit-application")
    final public ResponseEntity<?> apply(final @RequestParam Long jobId,
                                   @RequestParam final Long userId,
                                   @RequestParam final Long resumeId,
                                   @RequestParam(required = false) final Long fileId,
                                   @RequestParam(required = false) final String coverLetter) {

        logger.info("Submitting application for jobId: {} by userId: {}", jobId, userId);

        try {

            applicationService.applyJob(jobId, userId, resumeId, fileId, coverLetter);

            return ResponseEntity.ok("Application submitted successfully");

        } catch (final RuntimeException e) {

            if (e.getMessage().contains("Already applied")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred");
        }
    }

    @GetMapping("/seeker/{seekerId}")
    public ResponseEntity<List<ApplicationResponseDTO>> getBySeeker(
            @PathVariable final Long seekerId) {

        logger.info("Fetching applications for userId: {}", seekerId);

        return ResponseEntity.ok(
                applicationService.getApplicationsBySeeker(seekerId)
        );
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<ApplicationResponseDTO>> getByJob(
            @PathVariable final Long jobId) {

        logger.info("Fetching applications for jobId: {}", jobId);

        return ResponseEntity.ok(
                applicationService.getApplicationsByJob(jobId)
        );
    }
//    Employeruserid feteching apllications for employeruserid
    @GetMapping("/employer/{employerUserId}")
    @ResponseBody
    public ResponseEntity<List<EmployerApplicationDTO>> getByEmployer(@PathVariable final Long employerUserId) {

        logger.info("Fetching applications for employerUserId: {}", employerUserId);

        return ResponseEntity.ok(
                applicationService.getApplicationsByEmployer(employerUserId)
        );
    }

    @PostMapping("/withdraw/{id}")
    public ResponseEntity<String> withdraw(@PathVariable final Long id, @RequestParam final String reason) {

        logger.warn("Application withdrawal requested. applicationId: {}", id);

        applicationService.withdrawApplication(id, reason);

        logger.info("Application withdrawn successfully. applicationId: {}", id);

        return ResponseEntity.ok("Application withdrawn successfully");
    }

    @PostMapping("/update-status/{id}")
    public ResponseEntity<String> updateStatus(@PathVariable final Long id,
                                               @RequestParam final String status) {

        logger.info("Updating application status. applicationId: {}, status: {}", id, status);

        applicationService.updateStatus(id, status);

        logger.info("Application status updated successfully. applicationId: {}", id);

        return ResponseEntity.ok("Application status updated successfully");
    }

    @PutMapping("/notes/{applicationId}")
    public ResponseEntity<?> addNotes(
            @PathVariable final Long applicationId,
            @RequestBody final NoteRequestDTO request) {

        logger.info("Adding employer notes to applicationId: {}", applicationId);

        return ResponseEntity.ok(
                applicationService.addEmployerNotes(
                        applicationId,
                        request.getEmployerId(),
                        request.getNoteText()
                )
        );
    }

    @GetMapping("/manage")
    public String showManageApplicationsPage(final HttpSession session) {

        logger.info("Opening manage applications page");

        if (session.getAttribute("loggedInUser") == null) {
            logger.warn("Unauthorized access to manage applications page");
            return "redirect:/auth/login";
        }

        return "employer/applications/manage-applications";
    }

    @GetMapping("/all")
    @ResponseBody
    public ResponseEntity<List<EmployerApplicationDTO>> getAllApplications(final HttpSession session) {

        logger.info("Fetching all applications for logged-in employer");

        final Object userObj = session.getAttribute("loggedInUser");

        if (userObj == null) {

            logger.warn("Unauthorized request for employer applications");

            return ResponseEntity.status(401).build();
        }

        final com.RevHire.entity.User user = (com.RevHire.entity.User) userObj;

        final Long employerUserId = user.getUserId();

        final List<EmployerApplicationDTO> applications =
                applicationService.getApplicationsByEmployer(employerUserId);

        logger.info("Total applications fetched: {}", applications.size());

        return ResponseEntity.ok(applications);
    }

    @GetMapping("/details/{applicationId}")
    @ResponseBody
    final public ResponseEntity<EmployerApplicationDTO> getApplicationDetails(
            @PathVariable final Long applicationId) {

        logger.info("Fetching application details for applicationId: {}", applicationId);

        final List<EmployerApplicationDTO> apps =
                applicationService.getApplicationsByEmployer(null);

        final EmployerApplicationDTO result = apps.stream()
                .filter(a -> a.getApplicationId().equals(applicationId))
                .findFirst()
                .orElseThrow(() -> {

                    logger.error("Application not found with ID: {}", applicationId);

                    return new RuntimeException("Application not found");
                });

        logger.info("Application details fetched successfully. applicationId: {}", applicationId);

        return ResponseEntity.ok(result);
    }
}