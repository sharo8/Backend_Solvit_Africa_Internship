package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public Page<User> findAll(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    public Page<User> getByRole(Role role, Pageable pageable) {
        return userRepository.findByRole(role, pageable);
    }

    @Transactional
    public User updateProfile(Long userId, String firstName, String lastName, String universityId, boolean profileCompleted) {
        User user = getById(userId);
        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (universityId != null) user.setUniversityId(universityId);
        user.setProfileCompleted(profileCompleted);
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfilePhoto(Long userId, String profilePhotoUrl) {
        User user = getById(userId);
        user.setProfilePhotoUrl(profilePhotoUrl);
        return userRepository.save(user);
    }
}
