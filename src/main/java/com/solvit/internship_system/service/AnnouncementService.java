package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.AnnouncementUpsertRequest;
import com.solvit.internship_system.entity.Announcement;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.AnnouncementRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    @Transactional
    public Announcement create(Long authorId, AnnouncementUpsertRequest request) {
        User author = userRepository.findById(authorId).orElseThrow(() -> new ResourceNotFoundException("User", authorId));
        Announcement announcement = new Announcement();
        announcement.setAuthor(author);
        applyUpsert(announcement, request);
        return announcementRepository.save(announcement);
    }

    public Page<Announcement> getAll(Pageable pageable) {
        return announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc(pageable);
    }

    public Page<Announcement> getByRole(Role role, Pageable pageable) {
        return announcementRepository.findByTargetRoleIsNullOrTargetRoleOrderByPinnedDescCreatedAtDesc(role, pageable);
    }

    public Announcement getById(Long id) {
        return announcementRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Announcement", id));
    }

    @Transactional
    public Announcement update(Long id, AnnouncementUpsertRequest request) {
        Announcement existing = getById(id);
        applyUpsert(existing, request);
        return announcementRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Announcement existing = getById(id);
        announcementRepository.delete(existing);
    }

    public long countVisibleForRole(Role role) {
        return announcementRepository.countByTargetRoleIsNullOrTargetRole(role);
    }

    private void applyUpsert(Announcement announcement, AnnouncementUpsertRequest request) {
        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        if (title.isBlank()) {
            throw new BadRequestException("title is required");
        }
        announcement.setTitle(title.length() > 500 ? title.substring(0, 500) : title);
        announcement.setContent(request.getContent() != null ? request.getContent().trim() : null);
        announcement.setTargetRole(request.getTargetRole());
        announcement.setPinned(Boolean.TRUE.equals(request.getPinned()));
    }
}
