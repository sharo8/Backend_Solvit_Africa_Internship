package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.InternRecord;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.InternRecordRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InternRecordService {
    private final InternRecordRepository internRecordRepository;
    private final InternProfileRepository internProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public InternRecord create(InternRecord input) {
        if (input == null || input.getUser() == null || input.getUser().getId() == null) {
            throw new BadRequestException("Intern user is required");
        }
        User user = userRepository.findById(input.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", input.getUser().getId()));
        if (user.getRole() != Role.INTERN) {
            throw new BadRequestException("user_id must reference an INTERN");
        }
        if (internRecordRepository.findByUser_Id(user.getId()).isPresent()) {
            throw new BadRequestException("Intern record already exists for this user");
        }
        input.setUser(user);
        if (input.getSupervisor() != null && input.getSupervisor().getId() != null) {
            User sup = userRepository.findById(input.getSupervisor().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", input.getSupervisor().getId()));
            input.setSupervisor(sup);
        }
        return internRecordRepository.save(input);
    }

    @Transactional(readOnly = true)
    public InternRecord getById(Long id) {
        return internRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InternRecord", id));
    }

    @Transactional
    public List<InternRecord> listBySupervisor(Long supervisorId) {
        List<InternRecord> existing = internRecordRepository.findBySupervisor_IdAndStatus(supervisorId, InternRecord.InternStatus.ACTIVE);
        if (!existing.isEmpty()) {
            return existing;
        }
        List<InternProfile> profiles = internProfileRepository.findBySupervisorUserId(supervisorId);
        if (profiles.isEmpty()) {
            return existing;
        }
        for (InternProfile p : profiles) {
            if (p.getUser() == null || p.getUser().getId() == null) continue;
            ensureRecordForUser(p.getUser(), supervisorId);
        }
        return internRecordRepository.findBySupervisor_IdAndStatus(supervisorId, InternRecord.InternStatus.ACTIVE);
    }

    @Transactional
    public List<InternRecord> listAll() {
        List<InternRecord> existing = internRecordRepository.findAll();
        if (!existing.isEmpty()) {
            return existing;
        }
        List<User> internUsers = userRepository.findByRoleAndActiveTrue(Role.INTERN);
        for (User u : internUsers) {
            Long supervisorId = internProfileRepository.findByUser_Id(u.getId())
                    .map(InternProfile::getSupervisorUserId)
                    .orElse(null);
            ensureRecordForUser(u, supervisorId);
        }
        return internRecordRepository.findAll();
    }

    @Transactional
    public InternRecord update(Long id, InternRecord patch) {
        InternRecord current = internRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InternRecord", id));
        if (patch.getSupervisor() != null && patch.getSupervisor().getId() != null) {
            User sup = userRepository.findById(patch.getSupervisor().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", patch.getSupervisor().getId()));
            current.setSupervisor(sup);
        }
        if (patch.getStatus() != null) current.setStatus(patch.getStatus());
        if (patch.getPreferredLanguage() != null) current.setPreferredLanguage(patch.getPreferredLanguage());
        if (patch.getStartDate() != null) current.setStartDate(patch.getStartDate());
        if (patch.getEndDate() != null) current.setEndDate(patch.getEndDate());
        if (patch.getUniversity() != null) current.setUniversity(patch.getUniversity());
        if (patch.getDepartment() != null) current.setDepartment(patch.getDepartment());
        return internRecordRepository.save(current);
    }

    @Transactional
    public void delete(Long id) {
        InternRecord current = internRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InternRecord", id));
        current.setStatus(InternRecord.InternStatus.SUSPENDED);
        internRecordRepository.save(current);
    }

    private void ensureRecordForUser(User user, Long supervisorId) {
        if (user == null || user.getId() == null || user.getRole() != Role.INTERN) {
            return;
        }
        if (internRecordRepository.findByUser_Id(user.getId()).isPresent()) {
            return;
        }
        User supervisor = null;
        if (supervisorId != null) {
            supervisor = userRepository.findById(supervisorId).orElse(null);
        }
        InternRecord record = InternRecord.builder()
                .user(user)
                .supervisor(supervisor)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .status(InternRecord.InternStatus.ACTIVE)
                .preferredLanguage(InternRecord.PreferredLanguage.EN)
                .build();
        internRecordRepository.save(record);
    }
}
