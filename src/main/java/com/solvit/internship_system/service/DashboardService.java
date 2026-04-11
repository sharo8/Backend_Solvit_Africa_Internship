package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final TaskRepository taskRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final FeedbackRepository feedbackRepository;
    private final PerformanceScoreRepository performanceScoreRepository;

    public Map<String, Object> getKpis(Long userId, Role role) {
        Map<String, Object> kpis = new HashMap<>();
        if (role == Role.INTERN) {
            LocalDate weekStart = LocalDate.now().minusDays(7);
            LocalDate today = LocalDate.now();
            long attendances = attendanceRepository.findForUserInDateRange(userId, weekStart, today).size();
            long totalTasks = taskRepository.countByActiveTrueAndAssignee_IdAndStatusIn(userId,
                    java.util.Set.of(com.solvit.internship_system.entity.Task.TaskStatus.IN_REVIEW, com.solvit.internship_system.entity.Task.TaskStatus.VALIDATED));
            kpis.put("attendanceCountLast7Days", attendances);
            kpis.put("tasksCompleted", totalTasks);
        }
        if (role == Role.SUPERVISOR || role == Role.ADMIN) {
            kpis.put("totalInterns", userRepository.findByRoleAndActiveTrue(Role.INTERN).size());
            kpis.put("pendingLeaveRequests", leaveRequestRepository.findByStatusOrderByCreatedAtDesc(
                    com.solvit.internship_system.entity.LeaveRequest.LeaveStatus.PENDING,
                    org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements());
            kpis.put("atRiskInterns", performanceScoreRepository.findByAtRiskTrue().size());
        }
        if (role == Role.ADMIN) {
            kpis.put("totalUsers", userRepository.count());
            kpis.put("totalSupervisors", userRepository.findByRoleAndActiveTrue(Role.SUPERVISOR).size());
        }
        return kpis;
    }
}
