package com.RevHire.service;

import com.RevHire.dto.ApplicationResponseDTO;
import com.RevHire.dto.EmployerApplicationDTO;
import com.RevHire.entity.Application;

import java.time.LocalDateTime;
import java.util.List;

public interface ApplicationService {

    public Application applyJob(final Long jobId,
                                final Long userId,
                                final Long resumeId,
                                final Long fileId,
                                final String coverLetter);

    public List<ApplicationResponseDTO> getApplicationsBySeeker(final Long seekerId);

    void withdrawApplication(final Long applicationId, final String reason);

    List<ApplicationResponseDTO> getApplicationsByJob(final Long jobId);

    List<EmployerApplicationDTO> getApplicationsByEmployer(final Long employerId);



    Application updateStatus(final Long applicationId, final String status);

    String addEmployerNotes(final Long applicationId,
                            final Long employerId,
                            final String noteText);
}
