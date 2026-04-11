package com.solvit.internship_system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solvit.internship_system.dto.dashboard.*;
import com.solvit.internship_system.entity.*;
import com.solvit.internship_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard metrics scoped to interns supervised by the given supervisor user id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupervisorDashboardService {

    private static final List<String> DEFAULT_SKILLS = List.of(
            "Communication", "Technical", "Teamwork", "Problem Solving", "Leadership", "Adaptability");
    private static final String[] MONTH_NAMES = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private final InternProfileRepository internProfileRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final TaskRepository taskRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final FeedbackRepository feedbackRepository;
    private final PerformanceScoreRepository performanceScoreRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Set<Long> supervisedInternIds(Long supervisorUserId) {
        return internProfileRepository.findBySupervisorUserId(supervisorUserId).stream()
                .map(p -> p.getUser() != null ? p.getUser().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public AdminKpisDto getKpis(Long supervisorUserId) {
        Set<Long> ids = supervisedInternIds(supervisorUserId);
        long totalInterns = ids.size();
        long atRiskInterns = performanceScoreRepository.findByAtRiskTrue().stream()
                .map(ps -> ps.getIntern().getId())
                .filter(ids::contains)
                .distinct()
                .count();
        long pendingLeaveRequests = leaveRequestRepository.findByStatusOrderByCreatedAtDesc(
                        LeaveRequest.LeaveStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 1000))
                .getContent().stream()
                .filter(lr -> lr.getUser() != null && ids.contains(lr.getUser().getId()))
                .count();

        List<User> interns = userRepository.findByRoleAndActiveTrue(Role.INTERN).stream()
                .filter(u -> ids.contains(u.getId()))
                .toList();

        double averageAttendanceRate = computeAverageAttendanceRate(ids);
        double averagePerformanceScore = computeAveragePerformanceScore(ids);
        double taskCompletionRate = computeTaskCompletionRate(ids);
        long activeInterns = countActiveInterns(interns);
        long completedInternships = countCompletedInternships(interns);

        return AdminKpisDto.builder()
                .totalUsers(totalInterns)
                .totalInterns(totalInterns)
                .totalSupervisors(1L)
                .atRiskInterns(atRiskInterns)
                .averageAttendanceRate(round(averageAttendanceRate, 1))
                .averagePerformanceScore(round(averagePerformanceScore, 1))
                .pendingLeaveRequests(pendingLeaveRequests)
                .taskCompletionRate(round(taskCompletionRate, 1))
                .activeInterns(activeInterns)
                .completedInternships(completedInternships)
                .build();
    }

    public List<AttendanceTrendDto> getAttendanceTrend(int days, Long supervisorUserId) {
        Set<Long> ids = supervisedInternIds(supervisorUserId);
        if (ids.isEmpty()) return Collections.emptyList();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(1, days - 1));
        List<Attendance> all = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(start, end).stream()
                .filter(a -> a.getUser() != null && ids.contains(a.getUser().getId()))
                .toList();
        Map<LocalDate, List<Attendance>> byDate = all.stream().collect(Collectors.groupingBy(Attendance::getAttendanceDate));

        List<AttendanceTrendDto> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<Attendance> list = byDate.getOrDefault(d, Collections.emptyList());
            long total = list.size();
            long present = list.stream()
                    .filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT || a.getStatus() == Attendance.AttendanceStatus.VALIDATED)
                    .count();
            double rate = total > 0 ? (100.0 * present / total) : 0;
            result.add(new AttendanceTrendDto(d.toString(), round(rate, 1)));
        }
        return result;
    }

    public List<PerformanceByCohortDto> getPerformanceByCohort(Long supervisorUserId) {
        Set<Long> ids = supervisedInternIds(supervisorUserId);
        if (ids.isEmpty()) return Collections.emptyList();
        List<PerformanceScore> scores = performanceScoreRepository.findAll().stream()
                .filter(ps -> ps.getIntern() != null && ids.contains(ps.getIntern().getId()))
                .toList();
        Map<Long, Double> latestScoreByIntern = scores.stream()
                .collect(Collectors.groupingBy(ps -> ps.getIntern().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(PerformanceScore::getCreatedAt)),
                                opt -> opt.map(PerformanceScore::getOverallScore).orElse(0.0))));

        Map<String, List<Double>> byCohort = new LinkedHashMap<>();
        for (InternProfile p : internProfileRepository.findBySupervisorUserId(supervisorUserId)) {
            if (p.getUser() == null) continue;
            Long uid = p.getUser().getId();
            Double score = latestScoreByIntern.get(uid);
            if (score == null) score = 0.0;
            String cohort = p.getInstitution() != null && !p.getInstitution().isBlank()
                    ? p.getInstitution() : "My interns";
            byCohort.computeIfAbsent(cohort, k -> new ArrayList<>()).add(score);
        }
        return byCohort.entrySet().stream()
                .map(e -> new PerformanceByCohortDto(e.getKey(),
                        round(e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0), 1)))
                .collect(Collectors.toList());
    }

    public List<TaskCompletionWeeklyDto> getTaskCompletionWeekly(Long supervisorUserId) {
        Set<Long> ids = supervisedInternIds(supervisorUserId);
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
        List<Task> tasks = taskRepository.findByActiveTrueAndDueDateBetween(start, end).stream()
                .filter(t -> t.getAssignee() != null && ids.contains(t.getAssignee().getId()))
                .toList();
        WeekFields wf = WeekFields.of(Locale.getDefault());
        Map<Integer, List<Task>> byWeek = tasks.stream().collect(Collectors.groupingBy(t -> t.getDueDate().get(wf.weekOfMonth())));

        List<TaskCompletionWeeklyDto> result = new ArrayList<>();
        int maxWeek = (int) Math.ceil((double) end.getDayOfMonth() / 7);
        for (int w = 1; w <= Math.max(4, maxWeek); w++) {
            List<Task> weekTasks = byWeek.getOrDefault(w, Collections.emptyList());
            long completed = weekTasks.stream()
                    .filter(t -> t.getStatus() == Task.TaskStatus.IN_REVIEW || t.getStatus() == Task.TaskStatus.VALIDATED)
                    .count();
            long overdue = weekTasks.stream()
                    .filter(t -> t.getDueDate().isBefore(now) && t.getStatus() != Task.TaskStatus.IN_REVIEW && t.getStatus() != Task.TaskStatus.VALIDATED)
                    .count();
            long pending = weekTasks.size() - completed - overdue;
            if (pending < 0) pending = 0;
            result.add(new TaskCompletionWeeklyDto("Week " + w, completed, pending, overdue));
        }
        return result;
    }

    public List<SkillDistributionDto> getSkillDistribution(Long supervisorUserId) {
        Set<Long> ids = supervisedInternIds(supervisorUserId);
        Map<String, List<Double>> bySkill = new LinkedHashMap<>();
        for (String s : DEFAULT_SKILLS) {
            bySkill.put(s, new ArrayList<>());
        }
        List<PerformanceScore> all = performanceScoreRepository.findAll().stream()
                .filter(ps -> ps.getIntern() != null && ids.contains(ps.getIntern().getId()))
                .toList();
        for (PerformanceScore ps : all) {
            if (ps.getSkillGapData() == null || ps.getSkillGapData().isBlank()) continue;
            try {
                Map<String, Object> map = objectMapper.readValue(ps.getSkillGapData(), new TypeReference<Map<String, Object>>() {});
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    String skill = e.getKey();
                    double val = e.getValue() instanceof Number ? ((Number) e.getValue()).doubleValue() : 0;
                    bySkill.computeIfAbsent(skill, k -> new ArrayList<>()).add(val);
                }
            } catch (Exception ex) {
                log.debug("Could not parse skillGapData: {}", ps.getSkillGapData());
            }
        }
        return bySkill.entrySet().stream()
                .map(e -> new SkillDistributionDto(e.getKey(),
                        e.getValue().isEmpty() ? 0 : e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)))
                .map(dto -> new SkillDistributionDto(dto.getSkill(), round(dto.getAverageScore(), 0)))
                .collect(Collectors.toList());
    }

    public List<AttendanceHistogramDto> getAttendanceHistogram(int month, int year, Long supervisorUserId) {
        Set<Long> ids = supervisedInternIds(supervisorUserId);
        if (ids.isEmpty()) return Collections.emptyList();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<Attendance> all = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(start, end).stream()
                .filter(a -> a.getUser() != null && ids.contains(a.getUser().getId()))
                .toList();
        Map<Long, long[]> perUser = new HashMap<>();
        for (Attendance a : all) {
            long uid = a.getUser().getId();
            perUser.putIfAbsent(uid, new long[2]);
            perUser.get(uid)[0]++;
            if (a.getStatus() == Attendance.AttendanceStatus.PRESENT || a.getStatus() == Attendance.AttendanceStatus.VALIDATED) {
                perUser.get(uid)[1]++;
            }
        }
        int workingDays = (int) start.datesUntil(end.plusDays(1)).filter(d -> d.getDayOfWeek().getValue() < 6).count();
        if (workingDays == 0) workingDays = 1;
        int[] buckets = new int[5];
        for (Long uid : ids) {
            long[] t = perUser.getOrDefault(uid, new long[]{0, 0});
            double rate = t[0] > 0 ? (100.0 * t[1] / t[0]) : 0;
            if (rate <= 20) buckets[0]++;
            else if (rate <= 40) buckets[1]++;
            else if (rate <= 60) buckets[2]++;
            else if (rate <= 80) buckets[3]++;
            else buckets[4]++;
        }
        return List.of(
                new AttendanceHistogramDto("0-20%", buckets[0]),
                new AttendanceHistogramDto("21-40%", buckets[1]),
                new AttendanceHistogramDto("41-60%", buckets[2]),
                new AttendanceHistogramDto("61-80%", buckets[3]),
                new AttendanceHistogramDto("81-100%", buckets[4])
        );
    }

    public List<MonthlyRegistrationsDto> getMonthlyRegistrations(int year, Long supervisorUserId) {
        Set<Long> ids = supervisedInternIds(supervisorUserId);
        Instant yearStart = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant yearEnd = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        List<User> interns = userRepository.findByRoleAndCreatedAtBetween(Role.INTERN, yearStart, yearEnd).stream()
                .filter(u -> ids.contains(u.getId()))
                .toList();
        Map<Integer, Long> internsByMonth = interns.stream()
                .filter(u -> u.getCreatedAt() != null)
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().atZone(ZoneId.systemDefault()).getMonth().getValue(), Collectors.counting()));
        List<MonthlyRegistrationsDto> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            result.add(new MonthlyRegistrationsDto(MONTH_NAMES[m - 1],
                    internsByMonth.getOrDefault(m, 0L),
                    0L));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<FeedbackScoresTrendDto> getFeedbackScoresTrend(int weeks, Long supervisorUserId) {
        try {
            Instant end = Instant.now();
            Instant start = end.minus(weeks, java.time.temporal.ChronoUnit.WEEKS);
            List<Feedback> all = feedbackRepository.findBySupervisor_IdAndCreatedAtBetween(supervisorUserId, start, end);
            WeekFields wf = WeekFields.of(Locale.getDefault());
            Map<String, Map<Feedback.FeedbackType, List<Integer>>> byWeek = new TreeMap<>();
            for (Feedback f : all) {
                if (f.getRatingScore() == null || f.getCreatedAt() == null || f.getFeedbackType() == null) continue;
                int weekNum = f.getCreatedAt().atZone(ZoneId.systemDefault()).get(wf.weekOfWeekBasedYear());
                String weekLabel = "Week " + weekNum;
                byWeek.computeIfAbsent(weekLabel, k -> new HashMap<>())
                        .computeIfAbsent(f.getFeedbackType(), k -> new ArrayList<>())
                        .add(f.getRatingScore());
            }
            List<FeedbackScoresTrendDto> result = new ArrayList<>();
            for (Map.Entry<String, Map<Feedback.FeedbackType, List<Integer>>> e : byWeek.entrySet()) {
                Map<Feedback.FeedbackType, List<Integer>> map = e.getValue();
                double supervisorScore = map.getOrDefault(Feedback.FeedbackType.SUPERVISOR, Collections.emptyList()).stream().mapToInt(i -> i).average().orElse(0);
                double selfScore = map.getOrDefault(Feedback.FeedbackType.SELF, Collections.emptyList()).stream().mapToInt(i -> i).average().orElse(0);
                double peerScore = map.getOrDefault(Feedback.FeedbackType.PEER, Collections.emptyList()).stream().mapToInt(i -> i).average().orElse(0);
                result.add(new FeedbackScoresTrendDto(e.getKey(), round(supervisorScore, 1), round(selfScore, 1), round(peerScore, 1)));
            }
            return result;
        } catch (Exception ex) {
            log.warn("supervisor getFeedbackScoresTrend failed: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private double computeAverageAttendanceRate(Set<Long> internIds) {
        if (internIds.isEmpty()) return 0;
        LocalDate start = LocalDate.now().minusDays(30);
        List<Attendance> all = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(start, LocalDate.now()).stream()
                .filter(a -> a.getUser() != null && internIds.contains(a.getUser().getId()))
                .toList();
        if (all.isEmpty()) return 0;
        long present = all.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT || a.getStatus() == Attendance.AttendanceStatus.VALIDATED).count();
        return 100.0 * present / all.size();
    }

    private double computeAveragePerformanceScore(Set<Long> internIds) {
        if (internIds.isEmpty()) return 0;
        List<PerformanceScore> all = performanceScoreRepository.findAll().stream()
                .filter(ps -> ps.getIntern() != null && internIds.contains(ps.getIntern().getId()))
                .toList();
        if (all.isEmpty()) return 0;
        Map<Long, Double> latest = all.stream().collect(Collectors.groupingBy(ps -> ps.getIntern().getId(),
                Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparing(PerformanceScore::getCreatedAt)),
                        opt -> opt.map(PerformanceScore::getOverallScore).orElse(0.0))));
        return latest.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double computeTaskCompletionRate(Set<Long> internIds) {
        if (internIds.isEmpty()) return 0;
        long total = taskRepository.findByActiveTrue().stream()
                .filter(t -> t.getAssignee() != null && internIds.contains(t.getAssignee().getId()))
                .count();
        if (total == 0) return 0;
        long done = taskRepository.findByActiveTrue().stream()
                .filter(t -> t.getAssignee() != null && internIds.contains(t.getAssignee().getId()))
                .filter(t -> t.getStatus() == Task.TaskStatus.IN_REVIEW || t.getStatus() == Task.TaskStatus.VALIDATED)
                .count();
        return 100.0 * done / total;
    }

    private long countActiveInterns(List<User> interns) {
        Set<Long> atRiskIds = performanceScoreRepository.findByAtRiskTrue().stream()
                .map(ps -> ps.getIntern().getId())
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now();
        long active = 0;
        for (User u : interns) {
            if (!u.isActive()) continue;
            InternProfile p = internProfileRepository.findByUser_Id(u.getId()).orElse(null);
            if (p != null && p.getInternshipEndDate() != null && p.getInternshipEndDate().isBefore(today)) continue;
            if (atRiskIds.contains(u.getId())) continue;
            active++;
        }
        return active;
    }

    private long countCompletedInternships(List<User> interns) {
        LocalDate today = LocalDate.now();
        long n = 0;
        for (User u : interns) {
            InternProfile p = internProfileRepository.findByUser_Id(u.getId()).orElse(null);
            if (p != null && p.getInternshipEndDate() != null && p.getInternshipEndDate().isBefore(today)) n++;
        }
        return n;
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
