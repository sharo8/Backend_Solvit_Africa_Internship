package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.LeaveRequest;
import com.solvit.internship_system.entity.Notification;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.LeaveRequestRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Transactional
    public LeaveRequest create(Long userId, LeaveRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        request.setUser(user);
        request.setStatus(LeaveRequest.LeaveStatus.PENDING);
        LeaveRequest saved = leaveRequestRepository.save(request);

        InternProfile profile = internProfileRepository.findByUser_Id(userId).orElse(null);
        if (profile != null && profile.getSupervisorUserId() != null) {
            userRepository.findById(profile.getSupervisorUserId()).ifPresent(supervisor -> {
                if (!supervisor.isActive()) {
                    return;
                }
                String internName = (user.getFirstName() != null ? user.getFirstName() : "") + " "
                        + (user.getLastName() != null ? user.getLastName() : "");
                internName = internName.trim();
                if (internName.isEmpty()) {
                    internName = "A team member";
                }
                String period = saved.getStartDate() + " → " + saved.getEndDate();
                String type = saved.getLeaveType() != null ? saved.getLeaveType().name() : "LEAVE";
                notificationService.create(
                        supervisor.getId(),
                        "Leave request — please review",
                        internName + " submitted a " + type + " leave request for " + period
                                + ". Open Leave approvals or Leave requests in SOLVIT Africa to take action.",
                        Notification.NotificationType.LEAVE_SUBMITTED,
                        "LeaveRequest",
                        saved.getId(),
                        false);
                emailService.sendLeaveRequestSubmittedToSupervisorEmail(supervisor, user, saved);
            });
        }

        return saved;
    }

    public LeaveRequest getById(Long id) {
        return leaveRequestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", id));
    }

    public Page<LeaveRequest> getByUser(Long userId, Pageable pageable) {
        return leaveRequestRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<LeaveRequest> getPending(Pageable pageable) {
        return leaveRequestRepository.findByStatusOrderByCreatedAtDesc(LeaveRequest.LeaveStatus.PENDING, pageable);
    }

    public Page<LeaveRequest> getAll(Pageable pageable) {
        return leaveRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<LeaveRequest> getByStatus(LeaveRequest.LeaveStatus status, Pageable pageable) {
        return leaveRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional
    public LeaveRequest approve(Long leaveId, Long approverId) {
        LeaveRequest l = getById(leaveId);
        l.setStatus(LeaveRequest.LeaveStatus.APPROVED);
        l.setApprovedBy(approverId);
        l.setApprovedAt(Instant.now());
        l = leaveRequestRepository.save(l);
        User intern = l.getUser();
        notificationService.create(intern.getId(), "Leave approved",
                "Your leave request has been approved.",
                Notification.NotificationType.LEAVE_APPROVED,
                "LeaveRequest", l.getId(), false);
        emailService.sendLeaveApprovedEmail(intern, l);
        return l;
    }

    @Transactional
    public LeaveRequest reject(Long leaveId, Long approverId, String reason) {
        LeaveRequest l = getById(leaveId);
        l.setStatus(LeaveRequest.LeaveStatus.REJECTED);
        l.setApprovedBy(approverId);
        l.setApprovedAt(Instant.now());
        l.setRejectionReason(reason);
        l = leaveRequestRepository.save(l);
        User intern = l.getUser();
        String msg = reason != null && !reason.isBlank() ? reason : "Your leave request has been rejected.";
        notificationService.create(intern.getId(), "Leave request not approved",
                msg,
                Notification.NotificationType.LEAVE_REJECTED, "LeaveRequest", l.getId(), false);
        emailService.sendLeaveRejectedEmail(intern, l, reason);
        return l;
    }
}
