package com.RevHire.controller;

import java.util.List;
import java.util.ArrayList;

import com.RevHire.dto.JobDTO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.RevHire.entity.Job;
import com.RevHire.service.JobService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Controller
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private static final Logger logger = LogManager.getLogger(JobController.class);

    private final JobService jobService;

    @GetMapping
    @ResponseBody
    public List<JobDTO> viewAllJobs() {
        logger.info("Fetching all open jobs");
        return jobService.getAllOpenJobs();
    }

    @GetMapping("/create")
    public String showCreateJobPage(final HttpSession session, final Model model) {
        logger.info("Opening create job page");

        if (session.getAttribute("loggedInUser") == null) {
            logger.warn("Unauthorized access to create job page");
            return "redirect:/auth/login";
        }
        return "employer/jobs/create-job";
    }

    @PostMapping("/create/{userId}")
    @ResponseBody
    public ResponseEntity<?> createJob(@PathVariable final Long userId, @RequestBody final Job job) {
        logger.info("Creating job '{}' for userId {}", job.getTitle(), userId);
        return ResponseEntity.ok(jobService.createJob(job, userId));
    }

    @GetMapping("/search")
    public String searchJobs(
            @RequestParam(required = false) final String title,
            @RequestParam(required = false) final String location,
            @RequestParam(required = false) final Integer experience,
            @RequestParam(required = false) final String companyName,
            @RequestParam(required = false) final Double minSalary,
            @RequestParam(required = false) final Double maxSalary,
            @RequestParam(required = false) final String jobType,
            @RequestParam(required = false) final String sort,
            final Model model
    ) {
        logger.info("Searching jobs with filters: title={}, location={}, experience={}, company={}, jobType={}", title, location, experience, companyName, jobType);

        final List<JobDTO> jobs = new ArrayList<>(jobService.searchJobs(
                title,
                location,
                experience,
                companyName,
                minSalary,
                maxSalary,
                jobType
        ));
        if (sort != null) {
            logger.debug("Sorting jobs by {}", sort);
            switch (sort) {
                case "company":
                    jobs.sort((a, b) -> a.getCompanyName().compareToIgnoreCase(b.getCompanyName()));
                    break;

                case "salary":
                    jobs.sort((a, b) -> {
                        if (a.getSalaryMax() == null && b.getSalaryMax() == null) return 0;
                        if (a.getSalaryMax() == null) return -1;
                        if (b.getSalaryMax() == null) return 1;
                        return a.getSalaryMax().compareTo(b.getSalaryMax());
                    });
                    break;

                case "jobType":
                    jobs.sort((a, b) ->
                            a.getJobType().compareToIgnoreCase(b.getJobType())
                    );
                    break;
            }
        }
        model.addAttribute("jobs", jobs);
        logger.info("Search completed. Total jobs found: {}", jobs.size());
        return "jobs/search-results";
    }

    @GetMapping("/manage")
    public String showManageJobsPage(final HttpSession session) {
        logger.info("Opening manage jobs page");

        if (session.getAttribute("loggedInUser") == null) {
            logger.warn("Unauthorized access to manage jobs page");
            return "redirect:/auth/login";
        }

        return "employer/jobs/manage-jobs";
    }

    @GetMapping("/jobs/{userId}")
    @ResponseBody
    public ResponseEntity<List<JobDTO>> getEmployerJobs(@PathVariable final Long userId, @RequestParam(required = false) final String sort) {
        logger.info("Fetching jobs for employer userId {} with sort {}", userId, sort);

        return ResponseEntity.ok(jobService.getEmployerJobsSorted(userId, sort));
    }

    @DeleteMapping("/jobs/{jobId}")
    @ResponseBody
    public ResponseEntity<?> deleteJob(@PathVariable final Long jobId) {
        logger.warn("Deleting job with ID {}", jobId);
        jobService.deleteJob(jobId);
        return ResponseEntity.ok("Deleted successfully");
    }

        @PutMapping("/jobs/toggle/{jobId}")
    @ResponseBody
    public ResponseEntity<JobDTO> toggleJob(@PathVariable final Long jobId) {
        logger.info("Toggling job status for jobId {}", jobId);
        return ResponseEntity.ok(jobService.toggleJobStatus(jobId));
    }

    @GetMapping("/jobs/edit/{jobId}")
    public String showEditJobPage(@PathVariable final Long jobId, final Model model, final HttpSession session) {
        logger.info("Opening edit job page for jobId {}", jobId);

        if (session.getAttribute("loggedInUser") == null) {
            logger.warn("Unauthorized access to edit job page");
            return "redirect:/auth/login";
        }

        model.addAttribute("jobId", jobId);
        return "employer/jobs/edit-job";
    }

    @GetMapping("/get/{jobId}")
    @ResponseBody
    public ResponseEntity<JobDTO> getJobById(@PathVariable final Long jobId) {
        logger.info("Fetching job details for jobId {}", jobId);
        return ResponseEntity.ok(jobService.getJobById(jobId));
    }

    @PutMapping("/update/{jobId}")
    @ResponseBody
    public ResponseEntity<?> updateJob(@PathVariable final Long jobId, @RequestBody final Job job) {
        logger.info("Updating job with ID {}", jobId);
        return ResponseEntity.ok(jobService.updateJob(jobId, job));
    }
}