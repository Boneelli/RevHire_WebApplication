package com.RevHire.service.impl;

import com.RevHire.dto.EmployerDashboardDTO;
import com.RevHire.dto.EmployerProfileDTO;
import com.RevHire.entity.EmployerProfile;
import com.RevHire.entity.User;
import com.RevHire.repository.ApplicationRepository;
import com.RevHire.repository.EmployerProfileRepository;
import com.RevHire.repository.JobRepository;
import com.RevHire.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EmployerServiceImplTest {

    @Mock
    private EmployerProfileRepository employerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private EmployerServiceImpl employerService;

    private User user;
    private EmployerProfile profile;
    private EmployerProfileDTO dto;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setUserId(1L);
        user.setEmail("employer@test.com");

        profile = new EmployerProfile();
        profile.setEmployerId(1L);
        profile.setUser(user);
        profile.setCompanyName("ABC Tech");
        profile.setIndustry("IT");
        profile.setWebsite("www.abctech.com");
        profile.setDescription("Software company");
        profile.setLocation("India");
        profile.setCompanySize(100);

        dto = new EmployerProfileDTO();
        dto.setCompanyName("ABC Tech");
        dto.setIndustry("IT");
        dto.setCompanySize(100);
        dto.setDescription("Software company");
        dto.setWebsite("www.abctech.com");
        dto.setLocation("India");
        dto.setContactEmail("employer@test.com");

        // conteggi di default a 0 per i test che non li verificano
        when(jobRepository.countJobsByEmployer(any()))
                .thenReturn(jobCounts(0L, 0L));
        when(applicationRepository.countApplicationsByEmployer(any(), any()))
                .thenReturn(applicationCounts(0L, 0L));
    }

    // implementazioni semplici delle proiezioni (niente mock annidati dentro when/thenReturn)
    private JobRepository.JobCounts jobCounts(final Long total, final Long active) {
        return new JobRepository.JobCounts() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getActive() {
                return active;
            }
        };
    }

    private ApplicationRepository.ApplicationCounts applicationCounts(final Long total, final Long pending) {
        return new ApplicationRepository.ApplicationCounts() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getPending() {
                return pending;
            }
        };
    }

    @Test
    void testCreateOrUpdateProfileSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(employerProfileRepository.findByUserUserId(1L))
                .thenReturn(Optional.of(profile));

        EmployerProfileDTO result = employerService.createOrUpdateProfile(1L, dto);

        assertNotNull(result);
        assertEquals("ABC Tech", result.getCompanyName());

        verify(userRepository).save(user);
        verify(employerProfileRepository).save(any(EmployerProfile.class));
    }

    @Test
    void testCreateOrUpdateProfileUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> employerService.createOrUpdateProfile(1L, dto));
    }

    @Test
    void testCreateOrUpdateProfileEmailConflict() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(employerProfileRepository.findByUserUserId(1L)).thenReturn(Optional.of(profile));
        doThrow(new RuntimeException()).when(userRepository).save(user);

        assertThrows(ResponseStatusException.class,
                () -> employerService.createOrUpdateProfile(1L, dto));
    }

    @Test
    void testGetProfileSuccess() {
        when(employerProfileRepository.findByUserUserId(1L))
                .thenReturn(Optional.of(profile));

        EmployerProfileDTO result = employerService.getProfile(1L);

        assertNotNull(result);
        assertEquals("ABC Tech", result.getCompanyName());
        assertEquals("IT", result.getIndustry());
        assertEquals("employer@test.com", result.getContactEmail());
    }

    @Test
    void testGetProfileNotFound() {
        when(employerProfileRepository.findByUserUserId(1L))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> employerService.getProfile(1L));
    }

    @Test
    void testGetDashboardWithExistingProfile() {
        when(employerProfileRepository.findByUserUserId(1L))
                .thenReturn(Optional.of(profile));

        when(jobRepository.countJobsByEmployer(1L)).thenReturn(jobCounts(5L, 3L));
        when(applicationRepository.countApplicationsByEmployer(1L, "PENDING"))
                .thenReturn(applicationCounts(20L, 4L));

        EmployerDashboardDTO dashboard = employerService.getDashboard(1L);

        assertNotNull(dashboard);
        assertEquals(5L, dashboard.getTotalJobs());
        assertEquals(3L, dashboard.getActiveJobs());
        assertEquals(20L, dashboard.getTotalApplications());
        assertEquals(4L, dashboard.getPendingReviews());
    }

    @Test
    void testGetDashboardProfileNotFoundCreatesDefault() {
        when(employerProfileRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        EmployerProfile defaultProfile = new EmployerProfile();
        defaultProfile.setEmployerId(2L);
        when(employerProfileRepository.save(any())).thenReturn(defaultProfile);

        EmployerDashboardDTO dashboard = employerService.getDashboard(1L);

        assertNotNull(dashboard);
        verify(employerProfileRepository).save(any());
    }

    @Test
    void testGetDashboardUserNotFoundThrows() {
        when(employerProfileRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> employerService.getDashboard(1L));
    }

    @Test
    void testCalculateCompletionFullProfile() {
        when(employerProfileRepository.findByUserUserId(1L)).thenReturn(Optional.of(profile));

        double completion = employerService.getDashboard(1L).getProfileCompletionPercentage();

        assertTrue(completion >= 0 && completion <= 100);
    }

    @Test
    void testCalculateCompletionEmptyProfile() {
        EmployerProfile emptyProfile = new EmployerProfile();
        when(employerProfileRepository.findByUserUserId(1L)).thenReturn(Optional.of(emptyProfile));

        double completion = employerService.getDashboard(1L).getProfileCompletionPercentage();

        assertEquals(0.0, completion);
    }
}