package com.RevHire.service.impl;

import com.RevHire.dto.EmployerDashboardDTO;
import com.RevHire.repository.ApplicationRepository;
import com.RevHire.repository.JobRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.RevHire.dto.EmployerProfileDTO;
import com.RevHire.entity.EmployerProfile;
import com.RevHire.entity.User;
import com.RevHire.repository.EmployerProfileRepository;
import com.RevHire.repository.UserRepository;
import com.RevHire.service.EmployerService;
import org.springframework.web.server.ResponseStatusException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Service
@RequiredArgsConstructor
public class EmployerServiceImpl implements EmployerService {

    private static final Logger logger = LogManager.getLogger(EmployerServiceImpl.class);

    private final EmployerProfileRepository employerProfileRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;

    @Override
    @Transactional
    public EmployerProfileDTO createOrUpdateProfile(final Long userId, final EmployerProfileDTO dto) {
        logger.info("Creating or updating employer profile for userId: {}", userId);

        final User user = userRepository.findById(userId).orElseThrow(() -> {
                    logger.error("User not found with id: {}", userId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                });

        // [Use optional orElseGet instead of orElse]: istanzia EmployerProfile solo se assente
        final EmployerProfile profile = employerProfileRepository.findByUserUserId(userId).orElseGet(EmployerProfile::new);

        if (dto.getContactEmail() != null && !dto.getContactEmail().isEmpty()) {
            logger.info("Updating contact email for userId: {}", userId);
            user.setEmail(dto.getContactEmail());

            try {
                userRepository.save(user);
            } catch (final Exception e) {
                logger.error("Email already exists for another user: {}", dto.getContactEmail());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use by another account");
            }
        }

        profile.setUser(user);
        profile.setCompanyName(dto.getCompanyName());
        profile.setIndustry(dto.getIndustry());
        profile.setCompanySize(dto.getCompanySize());
        profile.setDescription(dto.getDescription());
        profile.setWebsite(dto.getWebsite());
        profile.setLocation(dto.getLocation());

        employerProfileRepository.save(profile);
        logger.info("Employer profile saved successfully for userId: {}", userId);
        return dto;
    }

    @Override
    public EmployerProfileDTO getProfile(final Long userId) {
        logger.info("Fetching employer profile for userId: {}", userId);

        final EmployerProfile profile = employerProfileRepository.findByUserUserId(userId).orElseThrow(() -> {
                    logger.error("Employer profile not found for userId: {}", userId);
                    return new RuntimeException("Profile not found");
                });

        final EmployerProfileDTO dto = new EmployerProfileDTO();
        dto.setCompanyName(profile.getCompanyName());
        dto.setIndustry(profile.getIndustry());
        dto.setCompanySize(profile.getCompanySize());
        dto.setDescription(profile.getDescription());
        dto.setWebsite(profile.getWebsite());
        dto.setLocation(profile.getLocation());

        if (profile.getUser() != null) {
            dto.setContactEmail(profile.getUser().getEmail());
        }

        logger.info("Employer profile retrieved successfully for userId: {}", userId);
        return dto;
    }

    public EmployerDashboardDTO getDashboard(Long userId) {
        logger.info("Fetching dashboard data for userId: {}", userId);

        final EmployerProfile profile = employerProfileRepository.findByUserUserId(userId).orElseGet(() -> {
                    logger.warn("Employer profile not found. Creating default profile for userId: {}", userId);
                    final User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
                    final EmployerProfile newProfile = new EmployerProfile();

                    newProfile.setUser(user);
                    newProfile.setCompanyName("Company");
                    newProfile.setIndustry("Not specified");
                    newProfile.setCompanySize(0);
                    newProfile.setLocation("Not specified");
                    newProfile.setDescription("");
                    newProfile.setWebsite("");

                    return employerProfileRepository.save(newProfile);
                });

        final Long employerId = profile.getEmployerId();

        logger.debug("EmployerId retrieved: {}", employerId);
        final Long totalJobs = jobRepository.countByEmployerEmployerId(employerId);
        final Long activeJobs = jobRepository.countByEmployerEmployerIdAndStatus(employerId, "OPEN");
        final Long totalApplications = applicationRepository.countByJob_Employer_EmployerId(employerId);
        final Long pendingReviews = applicationRepository.countByJob_Employer_EmployerIdAndStatus(employerId, "PENDING");
        final double completion = calculateCompletion(userId);
        logger.info("Dashboard statistics calculated for employerId: {}", employerId);

        return new EmployerDashboardDTO(
                totalJobs,
                activeJobs,
                totalApplications,
                pendingReviews,
                completion
        );
    }

    private double calculateCompletion(final Long employerId) {
        logger.debug("Calculating profile completion for employerId: {}", employerId);
        final EmployerProfile profile = employerProfileRepository.findByUserUserId(employerId).orElse(null);

        if (profile == null) {
            logger.warn("Profile not found while calculating completion for employerId: {}", employerId);
            return 0.0;
        }

        int count = 0;
        //[Use ++i instead of i++]
        if (profile.getCompanyName() != null && !profile.getCompanyName().isEmpty()) ++count;
        if (profile.getIndustry() != null && !profile.getIndustry().isEmpty()) ++count;
        if (profile.getWebsite() != null && !profile.getWebsite().isEmpty()) ++count;
        if (profile.getDescription() != null && !profile.getDescription().isEmpty()) ++count;

        final double completion = (count / 4.0) * 100.0;
        logger.debug("Profile completion calculated: {}%", completion);

        return completion;
    }
}