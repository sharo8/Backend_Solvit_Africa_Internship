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

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InternProfileService {

    private final InternProfileRepository profileRepository;
    private final UserRepository userRepository;

    /**
     * Returns existing intern profile or creates a minimal row for {@link Role#INTERN} users.
     * Initializes collection mappings so JSON serialization does not trigger lazy errors.
     */
    @Transactional
    public InternProfile getByUserIdOrCreate(Long userId) {
        Optional<InternProfile> existing = profileRepository.findDetailByUser_Id(userId);
        if (existing.isPresent()) {
            InternProfile p = existing.get();
            touchCollections(p);
            return p;
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getRole() != Role.INTERN) {
            throw new ResourceNotFoundException("Intern profile not found for user: " + userId);
        }
        InternProfile created = InternProfile.builder().user(user).build();
        created.setProfileCompletenessPercent(computeCompleteness(created));
        InternProfile saved = profileRepository.save(created);
        touchCollections(saved);
        return saved;
    }

    private void touchCollections(InternProfile p) {
        if (p.getSkills() != null) {
            p.getSkills().size();
        }
        if (p.getLearningObjectives() != null) {
            p.getLearningObjectives().size();
        }
    }

    public InternProfile getByUserId(Long userId) {
        return profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Intern profile not found for user: " + userId));
    }

    public InternProfile getById(Long id) {
        return profileRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("InternProfile", id));
    }

    @Transactional
    public InternProfile createOrUpdate(Long userId, InternProfile updates) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        InternProfile profile = profileRepository.findByUser_Id(userId).orElse(null);
        if (profile == null) {
            profile = InternProfile.builder().user(user).build();
        }
        if (updates.getBio() != null) profile.setBio(updates.getBio());
        if (updates.getAcademicBackground() != null) profile.setAcademicBackground(updates.getAcademicBackground());
        if (updates.getCareerGoals() != null) profile.setCareerGoals(updates.getCareerGoals());
        if (updates.getInstitution() != null) profile.setInstitution(updates.getInstitution());
        if (updates.getCvUrl() != null) profile.setCvUrl(updates.getCvUrl());
        if (updates.getTranscriptUrl() != null) profile.setTranscriptUrl(updates.getTranscriptUrl());
        if (updates.getIdPhotoUrl() != null) profile.setIdPhotoUrl(updates.getIdPhotoUrl());
        if (updates.getCompanyName() != null) profile.setCompanyName(updates.getCompanyName());
        if (updates.getSupervisorUserId() != null) profile.setSupervisorUserId(updates.getSupervisorUserId());
        if (updates.getInternshipStartDate() != null) profile.setInternshipStartDate(updates.getInternshipStartDate());
        if (updates.getInternshipEndDate() != null) profile.setInternshipEndDate(updates.getInternshipEndDate());
        if (updates.getSkills() != null) profile.setSkills(updates.getSkills());
        if (updates.getLearningObjectives() != null) profile.setLearningObjectives(updates.getLearningObjectives());
        profile.setProfileCompletenessPercent(computeCompleteness(profile));
        return profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<InternProfile> getBySupervisor(Long supervisorUserId) {
        List<InternProfile> list = profileRepository.findBySupervisorUserId(supervisorUserId);
        for (InternProfile p : list) {
            touchCollections(p);
        }
        return list;
    }

    private int computeCompleteness(InternProfile p) {
        int score = 0;
        if (p.getBio() != null && !p.getBio().isBlank()) score += 15;
        if (p.getInstitution() != null && !p.getInstitution().isBlank()) score += 10;
        if (p.getCvUrl() != null && !p.getCvUrl().isBlank()) score += 20;
        if (p.getCompanyName() != null && !p.getCompanyName().isBlank()) score += 15;
        if (p.getInternshipStartDate() != null) score += 10;
        if (p.getInternshipEndDate() != null) score += 10;
        if (p.getSkills() != null && !p.getSkills().isEmpty()) score += 10;
        if (p.getLearningObjectives() != null && !p.getLearningObjectives().isEmpty()) score += 10;
        return Math.min(100, score);
    }
}
