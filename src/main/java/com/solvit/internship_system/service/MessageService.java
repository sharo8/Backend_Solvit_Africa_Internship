package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Message;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.MessageRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final NotificationService notificationService;

    @Transactional
    public Message send(Long senderId, Long receiverId, String content) {
        User sender = userRepository.findById(senderId).orElseThrow(() -> new ResourceNotFoundException("User", senderId));
        User receiver = userRepository.findById(receiverId).orElseThrow(() -> new ResourceNotFoundException("User", receiverId));
        validateMessagingScope(sender, receiver);
        Message m = Message.builder().sender(sender).receiver(receiver).content(content).build();
        m = messageRepository.save(m);
        notificationService.create(receiverId, "New Message", "You have a new message.",
                com.solvit.internship_system.entity.Notification.NotificationType.MESSAGE, "Message", m.getId(), true);
        return m;
    }

    public Page<Message> getConversation(Long user1Id, Long user2Id, Pageable pageable) {
        User u1 = userRepository.findById(user1Id).orElseThrow(() -> new ResourceNotFoundException("User", user1Id));
        User u2 = userRepository.findById(user2Id).orElseThrow(() -> new ResourceNotFoundException("User", user2Id));
        validateMessagingScope(u1, u2);
        return messageRepository.findConversation(user1Id, user2Id, pageable);
    }

    public long countUnread(Long userId) {
        return messageRepository.countByReceiver_IdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markAsRead(Long messageId, Long userId) {
        messageRepository.findById(messageId).ifPresent(m -> {
            if (m.getReceiver().getId().equals(userId) && m.getReadAt() == null) {
                m.setReadAt(java.time.Instant.now());
                messageRepository.save(m);
            }
        });
    }

    @Transactional
    public Message updateMessage(Long messageId, Long userId, String newContent) {
        if (newContent == null || newContent.isBlank()) {
            throw new BadRequestException("Message content is required");
        }
        String trimmed = newContent.trim();
        if (trimmed.length() > 2000) {
            throw new BadRequestException("Message is too long (max 2000 characters)");
        }
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));
        if (!m.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("You can only edit your own messages");
        }
        m.setContent(trimmed);
        m.setUpdatedAt(java.time.Instant.now());
        return messageRepository.save(m);
    }

    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));
        if (!m.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own messages");
        }
        messageRepository.delete(m);
    }

    public java.util.List<User> getAllowedContacts(Long userId) {
        User actor = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (actor.getRole() == Role.SUPERVISOR) {
            return internProfileRepository.findBySupervisorUserId(userId).stream()
                    .map(p -> p.getUser())
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        if (actor.getRole() == Role.INTERN) {
            return internProfileRepository.findByUser_Id(userId)
                    .flatMap(p -> p.getSupervisorUserId() != null ? userRepository.findById(p.getSupervisorUserId()) : java.util.Optional.empty())
                    .stream()
                    .toList();
        }
        return java.util.List.of();
    }

    private void validateMessagingScope(User sender, User receiver) {
        if (sender.getRole() == Role.ADMIN || sender.getRole() == Role.HR) {
            return;
        }
        if (sender.getRole() == Role.SUPERVISOR && receiver.getRole() == Role.INTERN) {
            boolean ok = internProfileRepository.findByUser_Id(receiver.getId())
                    .map(p -> sender.getId().equals(p.getSupervisorUserId()))
                    .orElse(false);
            if (ok) return;
        }
        if (sender.getRole() == Role.INTERN && receiver.getRole() == Role.SUPERVISOR) {
            boolean ok = internProfileRepository.findByUser_Id(sender.getId())
                    .map(p -> receiver.getId().equals(p.getSupervisorUserId()))
                    .orElse(false);
            if (ok) return;
        }
        throw new AccessDeniedException("You can only message users in your scope");
    }
}
