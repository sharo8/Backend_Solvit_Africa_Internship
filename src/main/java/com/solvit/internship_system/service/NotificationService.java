package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Notification;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.repository.NotificationRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public Notification create(Long userId, String title, String message,
                               Notification.NotificationType type, String entityType, Long entityId, boolean sendEmail) {
        User user = userRepository.findById(userId).orElseThrow();
        Notification n = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .notificationType(type)
                .relatedEntityType(entityType)
                .relatedEntityId(entityId)
                .emailSent(false)
                .build();
        n = notificationRepository.save(n);
        if (sendEmail) {
            emailService.sendNotificationEmail(user.getEmail(), title, message);
            n.setEmailSent(true);
            notificationRepository.save(n);
        }
        return n;
    }

    public Page<Notification> getByUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<Notification> getUnreadByUser(Long userId, Pageable pageable) {
        return notificationRepository.findUnreadByUserId(userId, pageable);
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUser_IdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(userId) && n.getReadAt() == null) {
                n.setReadAt(java.time.Instant.now());
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void sendWeeklyTaskSummaryToAllSupervisors() {
        for (User u : userRepository.findByRole(Role.SUPERVISOR)) {
            if (!u.isActive()) {
                continue;
            }
            create(
                    u.getId(),
                    "Weekly task summary",
                    "Review your interns' task progress and submissions for this week.",
                    Notification.NotificationType.SYSTEM,
                    "TaskSummary",
                    null,
                    false
            );
        }
    }
}
