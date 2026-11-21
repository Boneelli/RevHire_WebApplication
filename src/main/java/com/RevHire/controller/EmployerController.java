package com.RevHire.controller;

import com.RevHire.dto.EmployerDashboardDTO;
import com.RevHire.dto.EmployerProfileDTO;
import com.RevHire.entity.User;
import com.RevHire.service.EmployerService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/employer")
@RequiredArgsConstructor
public class EmployerController {

    private static final Logger logger = LogManager.getLogger(EmployerController.class);

    private final EmployerService employerService;

    //mwthod to create ir update profile
    @PostMapping("/profile/{userId}")
    public ResponseEntity<?> createOrUpdateProfile(@PathVariable final Long userId, final @RequestBody EmployerProfileDTO dto) {
        logger.info("Request received to create/update employer profile for userId: {}", userId);

        final EmployerProfileDTO updatedDto = employerService.createOrUpdateProfile(userId, dto);

        logger.info("Employer profile updated successfully for userId: {}", userId);
        return ResponseEntity.ok(updatedDto);
    }

    @GetMapping("/profile/edit")
    public String showEditProfilePage(final HttpSession session, final Model model) {
        logger.info("Opening employer profile edit page");

        final User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            logger.warn("Unauthorized access to edit profile page");
            return "redirect:/auth/login";
        }

        final EmployerProfileDTO profile = employerService.getProfile(user.getUserId());

        model.addAttribute("profile", profile != null ? profile : new EmployerProfileDTO());
        model.addAttribute("userId", user.getUserId());

        logger.info("Employer profile edit page loaded for userId: {}", user.getUserId());
        return "employer/edit-company-profile";
    }

    @GetMapping("/profile")
    public String viewProfilePage(final HttpSession session, final Model model) {
        logger.info("Opening employer company profile page");

        final User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            logger.warn("Unauthorized access to company profile page");
            return "redirect:/auth/login";
        }

        final EmployerProfileDTO profile = employerService.getProfile(user.getUserId());
        model.addAttribute("profile", profile);
        logger.info("Employer profile fetched successfully for userId: {}", user.getUserId());

        return "employer/company-profile";
    }

    @GetMapping("/dashboard/{employerId}")
    public String showDashboard(@PathVariable final Long employerId, final Model model) {
        logger.info("Fetching dashboard data for employerId: {}", employerId);
        final EmployerDashboardDTO dashboard = employerService.getDashboard(employerId);

        model.addAttribute("dashboard", dashboard);
        logger.info("Employer dashboard loaded successfully for employerId: {}", employerId);

        return "employer/dashboard";
    }
}