package com.RevHire.service.impl;

import com.RevHire.dto.FavoriteJobDTO;
import com.RevHire.dto.JobDTO;
import com.RevHire.entity.*;
import com.RevHire.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JobSeekerServiceImplTest {

    @Mock
    private JobSeekerProfileRepository profileRepo;

    @Mock
    private ResumeRepository resumeRepo;

    @Mock
    private ResumeFileRepository resumeFileRepo;

    @Mock
    private FavoriteJobRepository favoriteJobRepo;

    @Mock
    private NotificationRepository notificationRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private JobRepository jobRepo;

    @Mock
    private ResumeSkillRepository resumeSkillRepo;

    @InjectMocks
    private JobSeekerServiceImpl jobSeekerService;

    private User user;
    private JobSeekerProfile profile;
    private Job job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setUserId(1L);
        user.setEmail("seeker@test.com");

        profile = new JobSeekerProfile();
        profile.setSeekerId(1L);
        profile.setFullName("John Doe");
        profile.setUser(user);

        job = new Job();
        job.setJobId(1L);
        job.setTitle("Java Developer");
        job.setLocation("Bangalore");
        job.setSalaryMin(BigDecimal.valueOf(30000));
        job.setSalaryMax(BigDecimal.valueOf(50000));
        job.setJobType("FULL_TIME");
        job.setStatus("OPEN");
    }

    @Test
    void testCreateProfileSuccess() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(profileRepo.save(profile)).thenReturn(profile);

        JobSeekerProfile result = jobSeekerService.createProfile(profile, 1L);

        assertNotNull(result);
        assertEquals("John Doe", result.getFullName());
        verify(profileRepo).save(profile);
    }

    @Test
    void testCreateProfileUserNotFound() {
        when(userRepo.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> jobSeekerService.createProfile(profile, 1L));
    }

    @Test
    void testUpdateProfileSuccess() {
        when(profileRepo.findById(1L)).thenReturn(Optional.of(profile));
        when(profileRepo.save(profile)).thenReturn(profile);

        JobSeekerProfile updated = jobSeekerService.updateProfile(1L, profile);

        assertNotNull(updated);
        verify(profileRepo).save(profile);
    }

    @Test
    void testUpdateProfileNotFound() {
        when(profileRepo.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> jobSeekerService.updateProfile(1L, profile));
    }

    @Test
    void testGetOrCreateResumeExisting() {
        Resume resume = new Resume();
        resume.setSeeker(profile);

        when(profileRepo.findByUserUserId(1L)).thenReturn(Optional.of(profile));
        when(resumeRepo.findBySeekerSeekerId(1L)).thenReturn(Optional.of(resume));

        Resume result = jobSeekerService.getOrCreateResume(1L);
        assertNotNull(result);
    }

    @Test
    void testGetOrCreateResumeNew() {
        when(profileRepo.findByUserUserId(1L)).thenReturn(Optional.of(profile));
        when(resumeRepo.findBySeekerSeekerId(1L)).thenReturn(Optional.empty());
        when(resumeRepo.save(any(Resume.class))).thenReturn(new Resume());

        Resume result = jobSeekerService.getOrCreateResume(1L);
        assertNotNull(result);
        verify(resumeRepo).save(any(Resume.class));
    }

    @Test
    void testGetOrCreateResumeProfileNotFound() {
        when(profileRepo.findByUserUserId(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> jobSeekerService.getOrCreateResume(1L));
    }

    @Test
    void testAddFavoriteJobAlreadyExists() {
        when(profileRepo.findByUserUserId(1L)).thenReturn(Optional.of(profile));
        when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
        when(favoriteJobRepo.existsBySeekerSeekerIdAndJobJobId(1L, 1L)).thenReturn(true);

        assertThrows(RuntimeException.class,
                () -> jobSeekerService.addFavoriteJob(1L, 1L));
    }

    @Test
    void testGetFavorites() {
        FavoriteJobDTO dto = new FavoriteJobDTO(1L, job.getJobId(), job.getTitle(), job.getLocation(),
                job.getSalaryMin(), job.getSalaryMax(), job.getJobType(), job.getStatus(), "Tech Corp");

        when(favoriteJobRepo.findFavoriteDtosBySeekerId(1L)).thenReturn(List.of(dto));

        List<FavoriteJobDTO> favorites = jobSeekerService.getFavorites(1L);

        assertEquals(1, favorites.size());
        assertEquals("Java Developer", favorites.get(0).getTitle());
    }

    @Test
    void testRemoveFavoriteJob() {
        jobSeekerService.removeFavoriteJob(1L);
        verify(favoriteJobRepo).deleteById(1L);
    }

    @Test
    void testMarkNotificationAsReadSuccess() {
        Notification notification = new Notification();
        notification.setNotificationId(1L);
        notification.setIsRead(false);

        when(notificationRepo.findById(1L)).thenReturn(Optional.of(notification));

        jobSeekerService.markNotificationAsRead(1L);

        assertTrue(notification.getIsRead());
        verify(notificationRepo).save(notification);
    }

    @Test
    void testMarkNotificationAsReadNotFound() {
        when(notificationRepo.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> jobSeekerService.markNotificationAsRead(1L));
    }

    @Test
    void testSearchJobs() {
        when(jobRepo.findAdvanced(anyString(), anyString(), anyInt(),
                anyString(), any(BigDecimal.class), any(BigDecimal.class),
                anyString(), eq("OPEN"))).thenReturn(List.of(job));

        List<JobDTO> results = jobSeekerService.searchJobs(
                "Java", "Bangalore", 2, "BTech", 20000.0, 60000.0, "FULL_TIME"
        );

        assertEquals(1, results.size());
        assertEquals("Java Developer", results.get(0).getTitle());
    }

    @Test
    void testGetRecommendedJobsWithSkills() {
        job.setTitle("Java Developer");

        when(jobRepo.findAll()).thenReturn(List.of(job));

        List<Job> recommended = jobSeekerService.getRecommendedJobs(List.of("java"));
        assertEquals(1, recommended.size());
    }

    @Test
    void testGetRecommendedJobsNoSkills() {
        List<Job> recommended = jobSeekerService.getRecommendedJobs(List.of());
        assertTrue(recommended.isEmpty());
    }

    @Test
    void testGetRecommendedJobsNullSkills() {
        List<Job> recommended = jobSeekerService.getRecommendedJobs(null);
        assertTrue(recommended.isEmpty());
    }
}