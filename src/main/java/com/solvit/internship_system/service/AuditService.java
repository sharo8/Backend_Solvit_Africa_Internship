package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.AuditLog;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.repository.AuditLogRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    public void log(Long userId, String action, String entityType, Long entityId, String oldValue, String newValue, String ip, String userAgent) {
        final String userEmail = userId != null
                ? userRepository.findById(userId).map(User::getEmail).orElse(null)
                : null;
        AuditLog log = AuditLog.builder()
                .user(userId != null ? userRepository.getReferenceById(userId) : null)
                .userEmail(userEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build();
        auditLogRepository.save(log);
    }

    public Page<AuditLog> getByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<AuditLog> getByAction(String action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
    }

    public Page<AuditLog> getByEntity(String entityType, Long entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }
}
