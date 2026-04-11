package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.analytics.*;
import com.solvit.internship_system.entity.*;
import com.solvit.internship_system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private static final String[] MONTH_ABBR = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final TaskRepository taskRepository;
    private final PerformanceScoreRepository performanceScoreRepository;
    private final FeedbackRepository feedbackRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LearningPathRepository learningPathRepository;
    private final InternProfileRepository internProfileRepository;
    private final AuditLogRepository auditLogRepository;

    public List<UsersByRoleDto> getUsersByRole() {
        List<UsersByRoleDto> result = new ArrayList<>();
        for (Role r : Role.values()) {
            long count = userRepository.findByRole(r).size();
            result.add(new UsersByRoleDto(r.name(), count));
        }
        return result;
    }

    public List<UserGrowthDto> getUserGrowth(int months) {
        Instant start = Instant.now().minus(months, ChronoUnit.MONTHS);
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().isBefore(start))
                .toList();
        Map<Integer, Map<Integer, Long>> yearMonth = new TreeMap<>();
        ZoneId zone = ZoneId.systemDefault();
        for (User u : users) {
            int y = u.getCreatedAt().atZone(zone).getYear();
            int m = u.getCreatedAt().atZone(zone).getMonthValue();
            yearMonth.computeIfAbsent(y, k -> new TreeMap<>()).merge(m, 1L, Long::sum);
        }
        List<UserGrowthDto> result = new ArrayList<>();
        yearMonth.forEach((y, monthCounts) ->
                monthCounts.forEach((m, count) ->
                        result.add(new UserGrowthDto(MONTH_ABBR[m - 1] + " " + y, count))));
        return result;
    }

    public List<AttendanceDailyDto> getAttendanceDaily(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        List<Attendance> list = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(start, end);
        Map<LocalDate, long[]> byDate = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            byDate.put(d, new long[3]);
        }
        for (Attendance a : list) {
            long[] arr = byDate.get(a.getAttendanceDate());
            if (arr == null) continue;
            if (a.getStatus() == Attendance.AttendanceStatus.PRESENT || a.getStatus() == Attendance.AttendanceStatus.VALIDATED) arr[0]++;
            else if (a.getStatus() == Attendance.AttendanceStatus.ABSENT) arr[1]++;
            else if (a.getStatus() == Attendance.AttendanceStatus.LATE) arr[2]++;
        }
        return byDate.entrySet().stream()
                .map(e -> new AttendanceDailyDto(e.getKey().toString(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    public List<AttendanceRateDistributionDto> getAttendanceRateDistribution() {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();
        List<User> interns = userRepository.findByRoleAndActiveTrue(Role.INTERN);
        List<Attendance> all = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(start, end);
        Map<Long, long[]> perUser = new HashMap<>();
        for (Attendance a : all) {
            long uid = a.getUser().getId();
            perUser.putIfAbsent(uid, new long[2]);
            perUser.get(uid)[0]++;
            if (a.getStatus() == Attendance.AttendanceStatus.PRESENT || a.getStatus() == Attendance.AttendanceStatus.VALIDATED) {
                perUser.get(uid)[1]++;
            }
        }
        int[] buckets = new int[5];
        for (User u : interns) {
            long[] t = perUser.getOrDefault(u.getId(), new long[]{0, 0});
            double rate = t[0] > 0 ? (100.0 * t[1] / t[0]) : 0;
            if (rate <= 20) buckets[0]++;
            else if (rate <= 40) buckets[1]++;
            else if (rate <= 60) buckets[2]++;
            else if (rate <= 80) buckets[3]++;
            else buckets[4]++;
        }
        return List.of(
                new AttendanceRateDistributionDto("0-20%", buckets[0]),
                new AttendanceRateDistributionDto("21-40%", buckets[1]),
                new AttendanceRateDistributionDto("41-60%", buckets[2]),
                new AttendanceRateDistributionDto("61-80%", buckets[3]),
                new AttendanceRateDistributionDto("81-100%", buckets[4])
        );
    }

    public List<TaskStatusSummaryDto> getTaskStatusSummary() {
        List<TaskStatusSummaryDto> result = new ArrayList<>();
        for (Task.TaskStatus s : Task.TaskStatus.values()) {
            result.add(new TaskStatusSummaryDto(s.name(), taskRepository.countByActiveTrueAndStatus(s)));
        }
        return result;
    }

    public List<TaskWeeklyTrendDto> getTaskWeeklyTrend(int weeks) {
        Instant start = Instant.now().minus(weeks * 7L, ChronoUnit.DAYS);
        List<Task> completed = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() == Task.TaskStatus.IN_REVIEW || t.getStatus() == Task.TaskStatus.VALIDATED)
                .filter(t -> t.getCompletedAt() != null && !t.getCompletedAt().isBefore(start))
                .toList();
        List<Task> all = taskRepository.findAll().stream()
                .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(start))
                .toList();
        ZoneId zone = ZoneId.systemDefault();
        Map<Integer, Long> completedByWeek = completed.stream()
                .collect(Collectors.groupingBy(t -> (int) ChronoUnit.WEEKS.between(start, t.getCompletedAt()), Collectors.counting()));
        Map<Integer, Long> assignedByWeek = all.stream()
                .collect(Collectors.groupingBy(t -> (int) ChronoUnit.WEEKS.between(start, t.getCreatedAt()), Collectors.counting()));
        List<TaskWeeklyTrendDto> result = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            result.add(new TaskWeeklyTrendDto("Week " + (w + 1),
                    completedByWeek.getOrDefault(w, 0L),
                    assignedByWeek.getOrDefault(w, 0L)));
        }
        return result;
    }

    public List<ScoreDistributionDto> getPerformanceScoreDistribution() {
        List<PerformanceScore> all = performanceScoreRepository.findAll();
        Map<Long, Double> latest = all.stream()
                .collect(Collectors.groupingBy(ps -> ps.getIntern().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(PerformanceScore::getCreatedAt)),
                                opt -> opt.map(PerformanceScore::getOverallScore).orElse(0.0))));
        int[] buckets = new int[5];
        for (Double score : latest.values()) {
            double s = score == null ? 0 : score;
            if (s <= 20) buckets[0]++;
            else if (s <= 40) buckets[1]++;
            else if (s <= 60) buckets[2]++;
            else if (s <= 80) buckets[3]++;
            else buckets[4]++;
        }
        return List.of(
                new ScoreDistributionDto("0-20", buckets[0]),
                new ScoreDistributionDto("21-40", buckets[1]),
                new ScoreDistributionDto("41-60", buckets[2]),
                new ScoreDistributionDto("61-80", buckets[3]),
                new ScoreDistributionDto("81-100", buckets[4])
        );
    }

    public List<PerformanceBySupervisorDto> getPerformanceBySupervisor() {
        List<InternProfile> profiles = internProfileRepository.findAll().stream()
                .filter(p -> p.getSupervisorUserId() != null)
                .toList();
        List<PerformanceScore> scores = performanceScoreRepository.findAll();
        Map<Long, Double> latestByIntern = scores.stream()
                .collect(Collectors.groupingBy(ps -> ps.getIntern().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(PerformanceScore::getCreatedAt)),
                                opt -> opt.map(PerformanceScore::getOverallScore).orElse(0.0))));
        Map<Long, List<Double>> bySupervisor = new HashMap<>();
        for (InternProfile p : profiles) {
            Double score = latestByIntern.get(p.getUser().getId());
            if (score == null) score = 0.0;
            bySupervisor.computeIfAbsent(p.getSupervisorUserId(), k -> new ArrayList<>()).add(score);
        }
        List<User> supervisors = userRepository.findAll().stream().filter(u -> u.getRole() == Role.SUPERVISOR).toList();
        Map<Long, User> userMap = supervisors.stream().collect(Collectors.toMap(User::getId, u -> u));
        return bySupervisor.entrySet().stream()
                .map(e -> {
                    User sup = userMap.get(e.getKey());
                    String name = sup != null ? sup.getFirstName() + " " + (sup.getLastName() != null && !sup.getLastName().isEmpty() ? sup.getLastName().substring(0, 1) + "." : "") : "Unknown";
                    double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    return new PerformanceBySupervisorDto(name, Math.round(avg * 10) / 10.0, e.getValue().size());
                })
                .toList();
    }

    public List<FeedbackScoresByTypeDto> getFeedbackScoresByType() {
        List<Feedback> all = feedbackRepository.findAll();
        Map<Feedback.FeedbackType, List<Integer>> byType = new EnumMap<>(Feedback.FeedbackType.class);
        for (Feedback f : all) {
            if (f.getRatingScore() == null) continue;
            byType.computeIfAbsent(f.getFeedbackType(), k -> new ArrayList<>()).add(f.getRatingScore());
        }
        return byType.entrySet().stream()
                .map(e -> {
                    double avg = e.getValue().stream().mapToInt(i -> i).average().orElse(0);
                    return new FeedbackScoresByTypeDto(e.getKey().name(), Math.round(avg * 10) / 10.0, e.getValue().size());
                })
                .toList();
    }

    public List<FeedbackMonthlyAvgDto> getFeedbackMonthlyAvg(int months) {
        Instant start = Instant.now().minus(months, ChronoUnit.MONTHS);
        List<Feedback> all = feedbackRepository.findByCreatedAtBetween(start, Instant.now());
        ZoneId zone = ZoneId.systemDefault();
        Map<Integer, Map<Feedback.FeedbackType, List<Integer>>> byMonth = new TreeMap<>();
        for (Feedback f : all) {
            if (f.getRatingScore() == null) continue;
            int m = f.getCreatedAt().atZone(zone).getMonthValue();
            byMonth.computeIfAbsent(m, k -> new EnumMap<>(Feedback.FeedbackType.class))
                    .computeIfAbsent(f.getFeedbackType(), k -> new ArrayList<>()).add(f.getRatingScore());
        }
        List<FeedbackMonthlyAvgDto> result = new ArrayList<>();
        byMonth.forEach((month, typeScores) -> {
            double sup = typeScores.getOrDefault(Feedback.FeedbackType.SUPERVISOR, Collections.emptyList()).stream().mapToInt(i -> i).average().orElse(0.0);
            double self = typeScores.getOrDefault(Feedback.FeedbackType.SELF, Collections.emptyList()).stream().mapToInt(i -> i).average().orElse(0.0);
            double peer = typeScores.getOrDefault(Feedback.FeedbackType.PEER, Collections.emptyList()).stream().mapToInt(i -> i).average().orElse(0.0);
            result.add(new FeedbackMonthlyAvgDto(MONTH_ABBR[month - 1], Math.round(sup * 10) / 10.0, Math.round(self * 10) / 10.0, Math.round(peer * 10) / 10.0));
        });
        return result;
    }

    public List<LeaveByTypeDto> getLeaveByType() {
        List<LeaveRequest> all = leaveRequestRepository.findAll();
        Map<LeaveRequest.LeaveType, Long> byType = all.stream().collect(Collectors.groupingBy(LeaveRequest::getLeaveType, Collectors.counting()));
        return byType.entrySet().stream()
                .map(e -> new LeaveByTypeDto(e.getKey().name(), e.getValue()))
                .toList();
    }

    public List<LeaveStatusMonthlyDto> getLeaveStatusMonthly(int months) {
        Instant start = Instant.now().minus(months, ChronoUnit.MONTHS);
        List<LeaveRequest> all = leaveRequestRepository.findAll().stream()
                .filter(l -> l.getCreatedAt() != null && !l.getCreatedAt().isBefore(start))
                .toList();
        ZoneId zone = ZoneId.systemDefault();
        Map<Integer, long[]> byMonth = new TreeMap<>();
        for (LeaveRequest l : all) {
            int m = l.getCreatedAt().atZone(zone).getMonthValue();
            byMonth.putIfAbsent(m, new long[3]);
            long[] arr = byMonth.get(m);
            if (l.getStatus() == LeaveRequest.LeaveStatus.APPROVED) arr[0]++;
            else if (l.getStatus() == LeaveRequest.LeaveStatus.REJECTED) arr[1]++;
            else if (l.getStatus() == LeaveRequest.LeaveStatus.PENDING) arr[2]++;
        }
        return byMonth.entrySet().stream()
                .map(e -> new LeaveStatusMonthlyDto(MONTH_ABBR[e.getKey() - 1], e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    public List<LearningCompletionByPathDto> getLearningCompletionByPath() {
        List<LearningPath> all = learningPathRepository.findAll();
        Map<String, List<Integer>> byTitle = new LinkedHashMap<>();
        for (LearningPath lp : all) {
            String title = lp.getTitle() != null ? lp.getTitle() : "Unnamed";
            int pct = lp.getProgressPercent() != null ? lp.getProgressPercent() : 0;
            byTitle.computeIfAbsent(title, k -> new ArrayList<>()).add(pct);
        }
        return byTitle.entrySet().stream()
                .map(e -> {
                    double avg = e.getValue().stream().mapToInt(i -> i).average().orElse(0);
                    return new LearningCompletionByPathDto(e.getKey(), Math.round(avg * 10) / 10.0, e.getValue().size());
                })
                .toList();
    }

    public List<AuditActionsByTypeDto> getAuditActionsByType() {
        List<AuditLog> all = auditLogRepository.findAll();
        Map<String, Long> byAction = all.stream().collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));
        return byAction.entrySet().stream()
                .map(e -> new AuditActionsByTypeDto(e.getKey(), e.getValue()))
                .toList();
    }

    public List<AuditDailyActivityDto> getAuditDailyActivity(int days) {
        Instant end = Instant.now();
        Instant start = end.minus(days, ChronoUnit.DAYS);
        List<AuditLog> all = auditLogRepository.findByCreatedAtBetween(start, end);
        ZoneId zone = ZoneId.systemDefault();
        Map<LocalDate, Long> byDate = all.stream()
                .collect(Collectors.groupingBy(a -> a.getCreatedAt().atZone(zone).toLocalDate(), Collectors.counting()));
        List<AuditDailyActivityDto> result = new ArrayList<>();
        for (LocalDate d = LocalDate.now().minusDays(days); !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
            result.add(new AuditDailyActivityDto(d.toString(), byDate.getOrDefault(d, 0L)));
        }
        return result;
    }
}
