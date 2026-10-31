package com.RevHire.benchmark;

import com.RevHire.RevHireWebApplication;
import com.RevHire.dto.JobDTO;
import com.RevHire.entity.EmployerProfile;
import com.RevHire.entity.Job;
import com.RevHire.entity.Role;
import com.RevHire.entity.User;
import com.RevHire.repository.EmployerProfileRepository;
import com.RevHire.repository.JobRepository;
import com.RevHire.repository.UserRepository;
import com.RevHire.service.JobService;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Misura la funzionalità di restituzione dei job, sia senza filtri e sia con filtri sul titolo.
 *
 * Benchmark della ricerca/caricamento dei job (JobService) su H2 in memoria (per isolare la funzionalità).
 * Dati generati per ogni valore di numJobs (multiplo di 10):
 *  - 10 employer
 *  - 1 job su 10 e' CLOSED, gli altri OPEN
 *  - 1 job su 5 ha titolo "Java Developer ..." (tutti OPEN)
 *    => getAllOpenJobs() restituisce numJobs - numJobs/10 elementi
 *    => searchJobs("java", ...) restituisce numJobs/5 elementi
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class JobSearchBenchmark {

    private static final int NUM_EMPLOYERS = 10;

    private static final String[] TITLES = {
            "Java Developer", "Data Analyst", "Project Manager", "QA Engineer", "DevOps Engineer"
    };
    private static final String[] LOCATIONS = {
            "Roma", "Milano", "Napoli", "Torino", "Salerno"
    };

    @Param({"100", "1000", "10000"})
    public int numJobs;

    private ConfigurableApplicationContext context;
    private JobService jobService;

    @Setup(Level.Trial)
    public void setUp() {
        // Argomenti da riga di comando: hanno priorita' su application.properties
        // (che punta a Oracle), quindi sostituiscono datasource e dialetto.
        String[] args = {
                "--spring.datasource.url=jdbc:h2:mem:benchdb;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.show-sql=false",
                "--spring.jpa.properties.hibernate.format_sql=false",
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF"
        };

        context = new SpringApplicationBuilder(RevHireWebApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);

        seedData(
                context.getBean(UserRepository.class),
                context.getBean(EmployerProfileRepository.class),
                context.getBean(JobRepository.class)
        );

        jobService = context.getBean(JobService.class);

        // Controllo di correttezza: se la query non restituisce quello che ci
        // aspettiamo, il benchmark misurerebbe una cosa sbagliata.
        int expectedOpen = numJobs - numJobs / 10;
        int expectedJava = numJobs / 5;
        int open = jobService.getAllOpenJobs().size();
        int java = jobService.searchJobs("java", null, null, null, null, null, null).size();
        if (open != expectedOpen || java != expectedJava) {
            throw new IllegalStateException("Dati inattesi: open=" + open + " (atteso " + expectedOpen
                    + "), java=" + java + " (atteso " + expectedJava + ")");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /** Caricamento di tutti i job aperti. */
    @Benchmark
    public List<JobDTO> getAllOpenJobs() {
        return jobService.getAllOpenJobs();
    }

    /** Ricerca con un solo filtro sul titolo (match parziale, case-insensitive). */
    @Benchmark
    public List<JobDTO> searchJobsByTitle() {
        return jobService.searchJobs("java", null, null, null, null, null, null);
    }

    private void seedData(UserRepository userRepo,
                          EmployerProfileRepository employerRepo,
                          JobRepository jobRepo) {
        List<EmployerProfile> employers = new ArrayList<>();
        for (int e = 0; e < NUM_EMPLOYERS; e++) {
            User user = User.builder()
                    .email("employer" + e + "@bench.test")
                    .passwordHash("x")
                    .role(Role.EMPLOYER)
                    .securityQuestion("q")
                    .securityAnswerHash("a")
                    .build();
            user = userRepo.save(user);

            EmployerProfile profile = new EmployerProfile();
            profile.setUser(user);
            profile.setCompanyName("Company " + e);
            profile.setIndustry("IT");
            profile.setCompanySize(100);
            profile.setDescription("Benchmark employer");
            profile.setLocation(LOCATIONS[e % LOCATIONS.length]);
            employers.add(employerRepo.save(profile));
        }

        List<Job> jobs = new ArrayList<>(numJobs);
        for (int i = 0; i < numJobs; i++) {
            Job job = new Job();
            job.setEmployer(employers.get(i % NUM_EMPLOYERS));
            job.setTitle(TITLES[i % TITLES.length] + " #" + i);
            job.setDescription("Descrizione del job " + i);
            job.setExperienceRequired(i % 6);
            job.setEducationRequired("Laurea");
            job.setLocation(LOCATIONS[i % LOCATIONS.length]);
            job.setSalaryMin(BigDecimal.valueOf(25000 + (i % 10) * 1000));
            job.setSalaryMax(BigDecimal.valueOf(40000 + (i % 10) * 2000));
            job.setJobType(i % 2 == 0 ? "FULL_TIME" : "PART_TIME");
            job.setOpenings(1);
            job.setStatus(i % 10 == 9 ? "CLOSED" : "OPEN");
            job.setActive(true);
            jobs.add(job);
        }
        jobRepo.saveAll(jobs);
    }
}