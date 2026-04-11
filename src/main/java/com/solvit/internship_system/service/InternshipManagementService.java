package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InternshipManagementService {

    private static final String ENTITY = "InternProfile";

    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final AuditService auditService;

    @Transactional
    public InternProfile extendInternship(Long internId, LocalDate newEndDate, Long actorId) {
        User u = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (u.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        InternProfile profile = internProfileRepository.findByUser_Id(internId)
                .orElseThrow(() -> new ResourceNotFoundException("Intern profile not found"));
        if (profile.getInternshipStartDate() == null) {
            throw new BadRequestException("Set internship start date on the profile first");
        }
        if (newEndDate.isBefore(profile.getInternshipStartDate())) {
            throw new BadRequestException("End date must be on or after the internship start date");
        }
        LocalDate oldEnd = profile.getInternshipEndDate();
        if (oldEnd != null && newEndDate.isAfter(oldEnd)) {
            profile.setReminder30dSentForEndDate(null);
            profile.setReminder7dSentForEndDate(null);
        }
        profile.setInternshipEndDate(newEndDate);
        profile = internProfileRepository.save(profile);
        auditService.log(actorId, "UPDATE", ENTITY, profile.getId(), null, "internship_extended", null, null);
        return profile;
    }

    @Transactional
    public InternProfile completeInternshipEarly(Long internId, LocalDate endDate, Long actorId) {
        User u = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (u.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        InternProfile profile = internProfileRepository.findByUser_Id(internId)
                .orElseThrow(() -> new ResourceNotFoundException("Intern profile not found"));
        if (profile.getInternshipStartDate() == null) {
            throw new BadRequestException("Set internship start date on the profile first");
        }
        LocalDate end = endDate != null ? endDate : AttendanceCalculationService.todayKigali();
        if (end.isBefore(profile.getInternshipStartDate())) {
            throw new BadRequestException("End date must be on or after the internship start date");
        }
        if (profile.getInternshipEndDate() != null && end.isAfter(profile.getInternshipEndDate())) {
            throw new BadRequestException("Use extend internship to set a later end date");
        }
        profile.setInternshipEndDate(end);
        profile.setReminder30dSentForEndDate(null);
        profile.setReminder7dSentForEndDate(null);
        profile = internProfileRepository.save(profile);
        auditService.log(actorId, "UPDATE", ENTITY, profile.getId(), null, "internship_completed_early", null, null);
        return profile;
    }
}
