package com.RevHire.benchmark.support;

import com.RevHire.entity.JobSeekerProfile;
import com.RevHire.entity.Resume;
import com.RevHire.entity.ResumeCertification;
import com.RevHire.entity.ResumeEducation;
import com.RevHire.entity.ResumeExperience;
import com.RevHire.entity.ResumeProject;
import com.RevHire.entity.ResumeSkill;
import com.RevHire.repository.JobSeekerProfileRepository;
import com.RevHire.repository.ResumeCertificationRepository;
import com.RevHire.repository.ResumeEducationRepository;
import com.RevHire.repository.ResumeExperienceRepository;
import com.RevHire.repository.ResumeFileRepository;
import com.RevHire.repository.ResumeProjectRepository;
import com.RevHire.repository.ResumeRepository;
import com.RevHire.repository.ResumeSkillRepository;
import com.RevHire.service.impl.ResumeServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.RevHire.benchmark.support.BenchSupport.stub;

/**
 * Costruisce un ResumeServiceImpl "vero" i cui repository sono finti e restituiscono
 * un CV con itemsPerSection elementi in ciascuna delle 5 sezioni
 * (formazione, esperienze, skill, progetti, certificazioni).
 * Il resumeId da usare e' sempre 1.
 *
 * I testi non contengono &, < o > perche' il PDF viene generato da HTML che deve essere XML valido.
 */
public final class ResumeFixture {

    public static final long RESUME_ID = 1L;

    private ResumeFixture() {
    }

    public static ResumeServiceImpl serviceWith(int itemsPerSection) {
        JobSeekerProfile profile = new JobSeekerProfile();
        profile.setSeekerId(1L);
        profile.setFullName("Mario Rossi");
        profile.setLocation("Salerno");
        profile.setPhone("+39 333 1234567");

        Resume resume = new Resume();
        resume.setResumeId(RESUME_ID);
        resume.setSeeker(profile);
        resume.setObjective("Sviluppatore software con esperienza in applicazioni web, "
                + "interessato a progetti Java e a pratiche di sviluppo sostenibile.");

        List<ResumeEducation> educations = new ArrayList<>(itemsPerSection);
        List<ResumeExperience> experiences = new ArrayList<>(itemsPerSection);
        List<ResumeSkill> skills = new ArrayList<>(itemsPerSection);
        List<ResumeProject> projects = new ArrayList<>(itemsPerSection);
        List<ResumeCertification> certifications = new ArrayList<>(itemsPerSection);

        for (int i = 0; i < itemsPerSection; i++) {
            ResumeEducation education = new ResumeEducation();
            education.setDegree("Laurea numero " + i);
            education.setInstitution("Universita " + i);
            educations.add(education);

            ResumeExperience experience = new ResumeExperience();
            experience.setRole("Sviluppatore " + i);
            experience.setCompanyName("Azienda " + i);
            experiences.add(experience);

            ResumeSkill skill = new ResumeSkill();
            skill.setSkillName("Competenza " + i);
            skills.add(skill);

            ResumeProject project = new ResumeProject();
            project.setProjectTitle("Progetto " + i);
            project.setDescription("Descrizione del progetto " + i
                    + ": applicazione web con Spring Boot, database relazionale e interfaccia Thymeleaf.");
            projects.add(project);

            ResumeCertification certification = new ResumeCertification();
            certification.setCertificationName("Certificazione " + i);
            certification.setCompany("Ente " + i);
            certifications.add(certification);
        }

        ResumeRepository resumeRepo = stub(ResumeRepository.class)
                .returns("findById", Optional.of(resume)).build();
        JobSeekerProfileRepository profileRepo = stub(JobSeekerProfileRepository.class)
                .returns("findById", Optional.of(profile)).build();
        ResumeEducationRepository educationRepo = stub(ResumeEducationRepository.class)
                .returns("findByResume_ResumeId", educations).build();
        ResumeExperienceRepository experienceRepo = stub(ResumeExperienceRepository.class)
                .returns("findByResume_ResumeId", experiences).build();
        ResumeSkillRepository skillRepo = stub(ResumeSkillRepository.class)
                .returns("findByResume_ResumeId", skills).build();
        ResumeProjectRepository projectRepo = stub(ResumeProjectRepository.class)
                .returns("findByResumeResumeId", projects).build();
        ResumeCertificationRepository certRepo = stub(ResumeCertificationRepository.class)
                .returns("findByResumeResumeId", certifications).build();
        ResumeFileRepository fileRepo = stub(ResumeFileRepository.class).build();

        // Stesso ordine dei campi final in ResumeServiceImpl
        return new ResumeServiceImpl(resumeRepo, educationRepo, experienceRepo, skillRepo,
                fileRepo, certRepo, projectRepo, profileRepo, profileRepo);
    }

    /** Numero di tag <p presenti nell'HTML generato per un CV con n elementi per sezione. */
    public static int expectedParagraphs(int itemsPerSection) {
        return 5 * itemsPerSection + 2;
    }
}