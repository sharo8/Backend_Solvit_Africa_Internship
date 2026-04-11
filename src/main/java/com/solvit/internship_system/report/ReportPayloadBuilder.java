package com.solvit.internship_system.report;

import com.solvit.internship_system.dto.ai.AiPerformanceEvaluationDto;
import com.solvit.internship_system.entity.*;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.repository.*;
import com.solvit.internship_system.service.AiInsightsService;
import com.solvit.internship_system.report.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportPayloadBuilder {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final AttendanceRepository attendanceRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final EvaluationRepository evaluationRepository;
    private final FeedbackRepository feedbackRepository;
    private final LearningPathRepository learningPathRepository;
    private final AiInsightsService aiInsightsService;

    @Transactional(readOnly = true)
    public ReportPayload build(ReportRequest req, String reference) {
        ReportType type = ReportType.fromApi(req.getReportType());
        ReportDateRange dr = ReportDateResolver.resolve(req);
        String gen = TS.format(Instant.now());
        return switch (type) {
            case ATTENDANCE_SUMMARY -> attendanceSummary(req, dr, reference, gen);
            case INTERN_INDIVIDUAL -> internIndividual(req, dr, reference, gen);
            case SUPERVISOR -> supervisorReport(req, dr, reference, gen);
            case GROUP -> groupReport(req, dr, reference, gen);
            case PROJECT -> projectReport(req, dr, reference, gen);
            case TASK -> taskReport(req, dr, reference, gen);
            case EVALUATION -> evaluationReport(req, dr, reference, gen);
            case FEEDBACK -> feedbackReport(req, dr, reference, gen);
            case LEARNING_PATH -> learningPathReport(req, dr, reference, gen);
            case AI_PERFORMANCE -> aiPerformanceReport(req, dr, reference, gen);
            case WEEKLY -> weeklySynth(req, dr, reference, gen);
            case MONTHLY -> monthlySynth(req, dr, reference, gen);
            case QUARTERLY -> quarterlySynth(req, dr, reference, gen);
            case ANNUAL -> annualSynth(req, dr, reference, gen);
            case GROUP_COMPARISON -> groupComparison(req, dr, reference, gen);
        };
    }

    private ReportPayload attendanceSummary(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        List<User> interns = filterInterns(req, userRepository.findByRoleAndActiveTrue(Role.INTERN));
        if (interns.isEmpty()) {
            return emptyPayload("ATTENDANCE SUMMARY REPORT", dr, ref, gen, "No interns match filters.");
        }
        List<Attendance> all = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(dr.from(), dr.to());
        Set<Long> idSet = interns.stream().map(User::getId).collect(Collectors.toSet());
        all = all.stream().filter(a -> idSet.contains(a.getUser().getId())).toList();

        Map<Long, String> groupOf = primaryGroupNameByIntern();
        Map<Long, String> supOf = supervisorNameByIntern();

        long totalPresent = all.stream().filter(a -> isPresentLike(a.getStatus())).count();
        long totalAbsent = all.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
        long totalLate = all.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        long denom = Math.max(1, all.size());
        double globalRate = 100.0 * totalPresent / denom;

        List<KpiEntry> kpis = List.of(
                KpiEntry.builder().label("Total interns").value(String.valueOf(interns.size())).build(),
                KpiEntry.builder().label("Global presence rate").value(String.format(Locale.US, "%.1f%%", globalRate)).build(),
                KpiEntry.builder().label("Marked attendances").value(String.valueOf(all.size())).build(),
                KpiEntry.builder().label("Absences / Late").value(totalAbsent + " / " + totalLate).build()
        );

        List<List<String>> rows = new ArrayList<>();
        for (User u : interns) {
            List<Attendance> ia = all.stream().filter(a -> a.getUser().getId().equals(u.getId())).toList();
            long p = ia.stream().filter(a -> isPresentLike(a.getStatus())).count();
            long ab = ia.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
            long la = ia.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int days = (int) ChronoUnit.DAYS.between(dr.from(), dr.to()) + 1;
            double rate = days > 0 ? (100.0 * p / days) : 0;
            String status = rate >= 70 ? "Excellent" : (rate >= 50 ? "At Risk" : "Critical");
            rows.add(List.of(
                    fullName(u),
                    groupOf.getOrDefault(u.getId(), "—"),
                    supOf.getOrDefault(u.getId(), "—"),
                    String.valueOf(p),
                    String.valueOf(ab),
                    String.valueOf(la),
                    String.format(Locale.US, "%.1f%%", rate),
                    status
            ));
        }

        Map<String, Double> byGroup = new LinkedHashMap<>();
        for (User u : interns) {
            String g = groupOf.getOrDefault(u.getId(), "Unassigned");
            List<Attendance> ia = all.stream().filter(a -> a.getUser().getId().equals(u.getId())).toList();
            long p = ia.stream().filter(a -> isPresentLike(a.getStatus())).count();
            int days = (int) ChronoUnit.DAYS.between(dr.from(), dr.to()) + 1;
            double r = days > 0 ? 100.0 * p / days : 0;
            byGroup.merge(g, r, Double::sum);
        }
        Map<String, Double> groupAvg = new LinkedHashMap<>();
        Map<String, Long> cnt = interns.stream().collect(Collectors.groupingBy(u -> groupOf.getOrDefault(u.getId(), "Unassigned"), Collectors.counting()));
        for (Map.Entry<String, Double> e : byGroup.entrySet()) {
            long c = cnt.getOrDefault(e.getKey(), 1L);
            groupAvg.put(e.getKey(), e.getValue() / c);
        }

        Map<String, Double> bySup = new LinkedHashMap<>();
        Map<String, Long> cntS = new HashMap<>();
        for (User u : interns) {
            String s = supOf.getOrDefault(u.getId(), "Unassigned");
            List<Attendance> ia = all.stream().filter(a -> a.getUser().getId().equals(u.getId())).toList();
            long p = ia.stream().filter(a -> isPresentLike(a.getStatus())).count();
            int days = (int) ChronoUnit.DAYS.between(dr.from(), dr.to()) + 1;
            double r = days > 0 ? 100.0 * p / days : 0;
            bySup.merge(s, r, Double::sum);
            cntS.merge(s, 1L, Long::sum);
        }
        Map<String, Double> supAvg = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : bySup.entrySet()) {
            long c = cntS.getOrDefault(e.getKey(), 1L);
            supAvg.put(e.getKey(), e.getValue() / c);
        }

        List<String> notes = new ArrayList<>();
        for (List<String> row : rows) {
            try {
                double rt = Double.parseDouble(row.get(6).replace("%", ""));
                if (rt < 70) {
                    notes.add("Flag: " + row.get(0) + " below 70% attendance (" + row.get(6) + ").");
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        if (notes.isEmpty()) {
            notes.add("No interns under 70% attendance threshold for this period.");
        }

        return ReportPayload.builder()
                .pdfMainTitle("ATTENDANCE SUMMARY REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(kpis)
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("Intern attendance detail")
                        .headers(List.of("Intern", "Group", "Supervisor", "Present", "Absent", "Late", "Rate", "Status"))
                        .rows(rows)
                        .build()))
                .horizontalBarCharts(List.of(
                        HorizontalBarChartSpec.builder().title("Avg attendance % by group").values(groupAvg).barHexColor("#2563eb")
                                .valueMode(HorizontalBarValueMode.PERCENT).build(),
                        HorizontalBarChartSpec.builder().title("Avg attendance % by supervisor").values(supAvg).barHexColor("#7c3aed")
                                .valueMode(HorizontalBarValueMode.PERCENT).build()
                ))
                .notes(notes)
                .build();
    }

    private ReportPayload internIndividual(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        Long id = req.getInternId();
        if (id == null) {
            throw new BadRequestException("internId is required for individual intern report");
        }
        User u = userRepository.findById(id).orElseThrow(() -> new BadRequestException("Intern not found"));
        if (u.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        InternProfile prof = internProfileRepository.findByUser_Id(id).orElse(null);
        AiPerformanceEvaluationDto ai = aiInsightsService.evaluateInternPerformance(id);

        List<Attendance> att = attendanceRepository.findByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateDesc(id, dr.from(), dr.to());
        List<Task> tasks = taskRepository.findByActiveTrue().stream()
                .filter(t -> t.getAssignee() != null && t.getAssignee().getId().equals(id))
                .filter(t -> inRangeTask(t, dr))
                .toList();
        List<Evaluation> evals = evaluationRepository.findAll().stream()
                .filter(e -> e.isActive() && e.getIntern().getId().equals(id))
                .filter(e -> e.getEvaluationDate() != null && !e.getEvaluationDate().isBefore(dr.from()) && !e.getEvaluationDate().isAfter(dr.to()))
                .toList();
        List<Feedback> fbs = feedbackRepository.findByIntern_IdOrderByCreatedAtDesc(id).stream()
                .filter(f -> !f.getCreatedAt().isBefore(dr.from().atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .filter(f -> !f.getCreatedAt().isAfter(dr.to().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()))
                .toList();
        List<LearningPath> paths = learningPathRepository.findByUser_IdOrderByCreatedAtDesc(id);
        List<Project> projects = projectRepository.findByActiveTrue().stream()
                .filter(p -> p.getAssignedInterns() != null && p.getAssignedInterns().stream().anyMatch(i -> i.getId().equals(id)))
                .toList();

        double evalAvg = evals.stream()
                .map(Evaluation::getOverallScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().orElse(0);

        List<KpiEntry> kpis = List.of(
                KpiEntry.builder().label("Composite AI score").value(String.format(Locale.US, "%.1f", ai.getCompositeScore())).build(),
                KpiEntry.builder().label("Attendance score").value(String.format(Locale.US, "%.1f", ai.getAttendanceScore())).build(),
                KpiEntry.builder().label("Task completion score").value(String.format(Locale.US, "%.1f", ai.getTaskCompletionScore())).build(),
                KpiEntry.builder().label("Avg evaluation").value(String.format(Locale.US, "%.1f", evalAvg)).build()
        );

        List<List<String>> taskRows = new ArrayList<>();
        for (Task t : tasks) {
            taskRows.add(List.of(
                    t.getTitle(),
                    String.valueOf(t.getStatus()),
                    t.getDueDate() != null ? t.getDueDate().toString() : "",
                    t.getCompletedAt() != null ? t.getCompletedAt().toString() : ""
            ));
        }

        List<List<String>> evalRows = new ArrayList<>();
        for (Evaluation e : evals) {
            evalRows.add(List.of(
                    e.getType().name(),
                    fullName(e.getEvaluator()),
                    e.getOverallScore() != null ? String.valueOf(e.getOverallScore()) : "",
                    Optional.ofNullable(e.getSupervisorComment()).orElse("").replace("\n", " ")
            ));
        }

        List<List<String>> fbRows = new ArrayList<>();
        for (Feedback f : fbs) {
            fbRows.add(List.of(
                    fullName(f.getSupervisor()),
                    f.getFeedbackType().name(),
                    f.getSentimentLabel() != null ? f.getSentimentLabel() : "",
                    truncate(f.getComment(), 120)
            ));
        }

        List<List<String>> lpRows = new ArrayList<>();
        for (LearningPath lp : paths) {
            int pct = lp.getProgressPercent() != null ? lp.getProgressPercent() : 0;
            lpRows.add(List.of(lp.getTitle(), String.valueOf(pct), lp.isRecommendedFromSkillGap() ? "Recommended" : "Assigned"));
        }

        List<List<String>> prRows = new ArrayList<>();
        for (Project p : projects) {
            prRows.add(List.of(p.getTitle(), String.valueOf(p.getStatus()), p.getEndDate() != null ? p.getEndDate().toString() : ""));
        }

        List<String> notes = new ArrayList<>(ai.getRecommendations());
        notes.addAll(ai.getSkillGaps().stream().map(g -> "Skill gap: " + g).toList());

        return ReportPayload.builder()
                .pdfMainTitle("INTERN INDIVIDUAL REPORT — " + fullName(u).toUpperCase(Locale.ROOT))
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(kpis)
                .tables(List.of(
                        headerTable("Profile", List.of("Field", "Value"), List.of(
                                List.of("Group", primaryGroupNameByIntern().getOrDefault(id, "—")),
                                List.of("Supervisor", supervisorNameByIntern().getOrDefault(id, "—")),
                                List.of("Internship", (prof != null && prof.getInternshipStartDate() != null ? prof.getInternshipStartDate() : "—")
                                        + " → " + (prof != null && prof.getInternshipEndDate() != null ? prof.getInternshipEndDate() : "—"))
                        )),
                        ReportTableSection.builder().sectionTitle("Tasks").headers(List.of("Title", "Status", "Due", "Completed")).rows(taskRows).build(),
                        ReportTableSection.builder().sectionTitle("Evaluations").headers(List.of("Type", "Evaluator", "Score", "Comment")).rows(evalRows).build(),
                        ReportTableSection.builder().sectionTitle("Learning paths").headers(List.of("Module", "Progress %", "Type")).rows(lpRows).build(),
                        ReportTableSection.builder().sectionTitle("Projects").headers(List.of("Title", "Status", "End")).rows(prRows).build(),
                        ReportTableSection.builder().sectionTitle("Feedback excerpts").headers(List.of("From", "Type", "Sentiment", "Excerpt")).rows(fbRows).build()
                ))
                .notes(notes)
                .build();
    }

    private ReportTableSection headerTable(String title, List<String> headers, List<List<String>> rows) {
        return ReportTableSection.builder().sectionTitle(title).headers(headers).rows(rows).build();
    }

    private ReportPayload supervisorReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        Long sid = req.getSupervisorId();
        if (sid == null) {
            throw new BadRequestException("supervisorId is required");
        }
        User sup = userRepository.findById(sid).orElseThrow(() -> new BadRequestException("Supervisor not found"));
        List<InternProfile> profiles = internProfileRepository.findBySupervisorUserId(sid);
        List<User> interns = profiles.stream().map(InternProfile::getUser).filter(Objects::nonNull).toList();

        List<List<String>> rows = new ArrayList<>();
        for (User u : interns) {
            AiPerformanceEvaluationDto ai = aiInsightsService.evaluateInternPerformance(u.getId());
            long taskDone = taskRepository.countByActiveTrueAndAssignee_IdAndStatus(u.getId(), Task.TaskStatus.VALIDATED);
            rows.add(List.of(fullName(u), primaryGroupNameByIntern().getOrDefault(u.getId(), "—"),
                    String.format(Locale.US, "%.1f", ai.getCompositeScore()),
                    String.format(Locale.US, "%.1f", ai.getAttendanceScore()),
                    String.valueOf(taskDone),
                    ai.getRiskLevel()));
        }

        List<Project> projs = projectRepository.findByActiveTrue().stream()
                .filter(p -> p.getSupervisor() != null && p.getSupervisor().getId().equals(sid))
                .toList();
        long completed = projs.stream().filter(p -> p.getStatus() == Project.ProjectStatus.COMPLETED).count();
        long late = projs.stream().filter(p -> p.getStatus() == Project.ProjectStatus.ACTIVE && p.getEndDate() != null && p.getEndDate().isBefore(LocalDate.now())).count();

        List<KpiEntry> kpis = List.of(
                KpiEntry.builder().label("Supervised interns").value(String.valueOf(interns.size())).build(),
                KpiEntry.builder().label("Projects completed").value(String.valueOf(completed)).build(),
                KpiEntry.builder().label("Projects overdue").value(String.valueOf(late)).build(),
                KpiEntry.builder().label("Active projects").value(String.valueOf(projs.stream().filter(p -> p.getStatus() == Project.ProjectStatus.ACTIVE).count())).build()
        );

        List<List<String>> pr = projs.stream().map(p -> List.of(
                p.getTitle(), String.valueOf(p.getStatus()),
                p.getEndDate() != null ? p.getEndDate().toString() : ""
        )).toList();

        List<Evaluation> evs = evaluationRepository.findAll().stream()
                .filter(Evaluation::isActive)
                .filter(e -> e.getEvaluator().getId().equals(sid))
                .filter(e -> e.getEvaluationDate() != null && !e.getEvaluationDate().isBefore(dr.from()) && !e.getEvaluationDate().isAfter(dr.to()))
                .toList();
        List<List<String>> evRows = evs.stream().map(e -> List.of(
                fullName(e.getIntern()), e.getType().name(),
                e.getOverallScore() != null ? String.valueOf(e.getOverallScore()) : "",
                e.getEvaluationDate() != null ? e.getEvaluationDate().toString() : ""
        )).toList();

        return ReportPayload.builder()
                .pdfMainTitle("SUPERVISOR REPORT — " + fullName(sup).toUpperCase(Locale.ROOT))
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(kpis)
                .tables(List.of(
                        ReportTableSection.builder().sectionTitle("Interns under supervision").headers(
                                List.of("Intern", "Group", "AI score", "Attendance scr.", "Tasks done", "Risk")).rows(rows).build(),
                        ReportTableSection.builder().sectionTitle("Projects").headers(List.of("Title", "Status", "End")).rows(pr).build(),
                        ReportTableSection.builder().sectionTitle("Evaluations given").headers(List.of("Intern", "Type", "Score", "Date")).rows(evRows).build()
                ))
                .notes(List.of("Performance comparison uses live AI multi-source scoring for the selected period context."))
                .build();
    }

    private ReportPayload groupReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        Long gid = req.getGroupId();
        if (gid == null) {
            throw new BadRequestException("groupId is required");
        }
        ProjectGroup g = projectGroupRepository.findByIdAndActiveTrueWithInterns(gid).orElseThrow(() -> new BadRequestException("Group not found"));
        List<User> interns = g.getInterns() != null ? new ArrayList<>(g.getInterns()) : List.of();

        List<List<String>> rows = new ArrayList<>();
        User top = null;
        double topScore = -1;
        User worstAtt = null;
        double worstRate = 999;
        for (User u : interns) {
            AiPerformanceEvaluationDto ai = aiInsightsService.evaluateInternPerformance(u.getId());
            long done = taskRepository.countByActiveTrueAndAssignee_IdAndStatus(u.getId(), Task.TaskStatus.VALIDATED);
            List<Attendance> aa = attendanceRepository.findByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateDesc(u.getId(), dr.from(), dr.to());
            long p = aa.stream().filter(a -> isPresentLike(a.getStatus())).count();
            int days = (int) ChronoUnit.DAYS.between(dr.from(), dr.to()) + 1;
            double rate = days > 0 ? 100.0 * p / days : 0;
            rows.add(List.of(fullName(u), "INTERN", String.format(Locale.US, "%.1f%%", rate), String.valueOf(done), String.format(Locale.US, "%.1f", ai.getCompositeScore())));
            if (ai.getCompositeScore() > topScore) {
                topScore = ai.getCompositeScore();
                top = u;
            }
            if (rate < worstRate) {
                worstRate = rate;
                worstAtt = u;
            }
        }

        List<Project> groupProjs = projectRepository.findByActiveTrue().stream()
                .filter(p -> p.getGroup() != null && p.getGroup().getId().equals(gid))
                .toList();
        List<List<String>> pr = groupProjs.stream().map(p -> List.of(
                p.getTitle(), String.valueOf(p.getStatus()), "—",
                p.getEndDate() != null ? p.getEndDate().toString() : "",
                p.getSupervisor() != null ? fullName(p.getSupervisor()) : "—"
        )).toList();

        Map<String, Double> perfBars = new LinkedHashMap<>();
        for (User u : interns) {
            AiPerformanceEvaluationDto ai = aiInsightsService.evaluateInternPerformance(u.getId());
            perfBars.put(fullName(u), ai.getCompositeScore());
        }

        List<KpiEntry> kpis = List.of(
                KpiEntry.builder().label("Members").value(String.valueOf(interns.size())).build(),
                KpiEntry.builder().label("Active projects").value(String.valueOf(groupProjs.stream().filter(p -> p.getStatus() == Project.ProjectStatus.ACTIVE).count())).build(),
                KpiEntry.builder().label("Group name").value(g.getName()).build(),
                KpiEntry.builder().label("Supervisor").value(g.getSupervisor() != null ? fullName(g.getSupervisor()) : "—").build()
        );

        List<String> notes = new ArrayList<>();
        if (top != null) {
            notes.add("Top performer: " + fullName(top) + " (composite " + String.format(Locale.US, "%.1f", topScore) + ").");
        }
        if (worstAtt != null && worstRate < 80) {
            notes.add("Attendance alert: " + fullName(worstAtt) + " at " + String.format(Locale.US, "%.1f%%", worstRate) + ".");
        }

        return ReportPayload.builder()
                .pdfMainTitle("GROUP REPORT — " + g.getName().toUpperCase(Locale.ROOT))
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(kpis)
                .tables(List.of(
                        ReportTableSection.builder().sectionTitle("Members").headers(
                                List.of("Intern", "Role", "Attendance %", "Tasks done", "AI score")).rows(rows).build(),
                        ReportTableSection.builder().sectionTitle("Group projects").headers(
                                List.of("Title", "Status", "Progress", "Deadline", "Lead")).rows(pr).build()
                ))
                .horizontalBarCharts(List.of(HorizontalBarChartSpec.builder()
                        .title("Individual AI composite (horizontal bars)")
                        .values(perfBars)
                        .barHexColor("#2563eb")
                        .valueMode(HorizontalBarValueMode.SCORE)
                        .build()))
                .notes(notes)
                .build();
    }

    private ReportPayload projectReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        List<Project> all = projectRepository.findByActiveTrue();
        List<Project> inPeriod = all.stream().filter(p -> projectTouchesRange(p, dr)).toList();

        long unassigned = inPeriod.stream().filter(p -> (p.getAssignedInterns() == null || p.getAssignedInterns().isEmpty()) && p.getGroup() == null).count();
        long overdue = inPeriod.stream().filter(p -> p.getStatus() == Project.ProjectStatus.ACTIVE && p.getEndDate() != null && p.getEndDate().isBefore(LocalDate.now())).count();
        long completed = inPeriod.stream().filter(p -> p.getStatus() == Project.ProjectStatus.COMPLETED).count();
        long active = inPeriod.stream().filter(p -> p.getStatus() == Project.ProjectStatus.ACTIVE).count();

        Map<String, Double> pie = new LinkedHashMap<>();
        pie.put("ACTIVE", (double) active);
        pie.put("COMPLETED", (double) completed);
        pie.put("ON_HOLD", (double) inPeriod.stream().filter(p -> p.getStatus() == Project.ProjectStatus.ON_HOLD).count());
        pie.put("UNASSIGNED", (double) unassigned);

        List<List<String>> rows = new ArrayList<>();
        for (Project p : inPeriod) {
            String assign = "—";
            if (p.getGroup() != null) {
                assign = "Group: " + p.getGroup().getName();
            } else if (p.getAssignedInterns() != null && !p.getAssignedInterns().isEmpty()) {
                assign = p.getAssignedInterns().stream().map(this::fullName).collect(Collectors.joining(", "));
            }
            int lateDays = 0;
            if (p.getEndDate() != null && p.getStatus() != Project.ProjectStatus.COMPLETED && LocalDate.now().isAfter(p.getEndDate())) {
                lateDays = (int) ChronoUnit.DAYS.between(p.getEndDate(), LocalDate.now());
            }
            rows.add(List.of(
                    p.getTitle(),
                    String.valueOf(p.getStatus()),
                    assign,
                    p.getSupervisor() != null ? fullName(p.getSupervisor()) : "—",
                    p.getStartDate() != null ? p.getStartDate().toString() : "",
                    p.getEndDate() != null ? p.getEndDate().toString() : "",
                    p.getStatus() == Project.ProjectStatus.COMPLETED ? "OK" : "",
                    String.valueOf(p.getStatus()),
                    lateDays > 0 ? String.valueOf(lateDays) : "0"
            ));
        }

        return ReportPayload.builder()
                .pdfMainTitle("PROJECTS REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("In scope").value(String.valueOf(inPeriod.size())).build(),
                        KpiEntry.builder().label("Completed").value(String.valueOf(completed)).build(),
                        KpiEntry.builder().label("Overdue").value(String.valueOf(overdue)).build(),
                        KpiEntry.builder().label("Unassigned").value(String.valueOf(unassigned)).build()
                ))
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("All projects (filtered)")
                        .headers(List.of("Name", "Type/Status", "Group/Intern", "Supervisor", "Start", "Deadline", "Closed", "Status", "Late days"))
                        .rows(rows)
                        .build()))
                .horizontalBarCharts(List.of(HorizontalBarChartSpec.builder()
                        .title("Status distribution (bar view)")
                        .values(pie)
                        .barHexColor("#2563eb")
                        .valueMode(HorizontalBarValueMode.COUNT)
                        .build()))
                .notes(List.of("Unassigned = no group and no assigned interns on project record."))
                .build();
    }

    private ReportPayload taskReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        List<Task> tasks = taskRepository.findByActiveTrue().stream().filter(t -> inRangeTask(t, dr)).toList();
        if (req.getInternId() != null) {
            tasks = tasks.stream().filter(t -> t.getAssignee() != null && t.getAssignee().getId().equals(req.getInternId())).toList();
        }
        long done = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.VALIDATED).count();
        long overdue = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.OVERDUE || (t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now())
                && t.getStatus() != Task.TaskStatus.VALIDATED && t.getStatus() != Task.TaskStatus.CANCELLED)).count();
        long pending = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.PENDING || t.getStatus() == Task.TaskStatus.IN_PROGRESS).count();

        List<List<String>> rows = new ArrayList<>();
        for (Task t : tasks) {
            int late = 0;
            if (t.getDueDate() != null && t.getCompletedAt() == null && LocalDate.now().isAfter(t.getDueDate())
                    && t.getStatus() != Task.TaskStatus.VALIDATED) {
                late = (int) ChronoUnit.DAYS.between(t.getDueDate(), LocalDate.now());
            }
            rows.add(List.of(
                    t.getTitle(),
                    t.getAssignee() != null ? fullName(t.getAssignee()) : "—",
                    t.getCohortGroup() != null ? t.getCohortGroup().getName() : "—",
                    t.getProject() != null ? t.getProject().getTitle() : "—",
                    t.getPriority() != null ? t.getPriority().name() : "",
                    t.getDueDate() != null ? t.getDueDate().toString() : "",
                    t.getCompletedAt() != null ? t.getCompletedAt().toString() : "",
                    String.valueOf(t.getStatus()),
                    late > 0 ? String.valueOf(late) : "0"
            ));
        }

        return ReportPayload.builder()
                .pdfMainTitle("TASKS REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Total tasks").value(String.valueOf(tasks.size())).build(),
                        KpiEntry.builder().label("Completed").value(String.valueOf(done)).build(),
                        KpiEntry.builder().label("Overdue").value(String.valueOf(overdue)).build(),
                        KpiEntry.builder().label("Pending / active").value(String.valueOf(pending)).build()
                ))
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("Task lines")
                        .headers(List.of("Task", "Assignee", "Group", "Project", "Priority", "Deadline", "Completed", "Status", "Late days"))
                        .rows(rows)
                        .build()))
                .notes(List.of("Overdue flag uses due date vs today when not validated."))
                .build();
    }

    private ReportPayload evaluationReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        List<Evaluation> list = evaluationRepository.findAll().stream()
                .filter(Evaluation::isActive)
                .filter(e -> e.getEvaluationDate() != null && !e.getEvaluationDate().isBefore(dr.from()) && !e.getEvaluationDate().isAfter(dr.to()))
                .toList();
        if (req.getInternId() != null) {
            list = list.stream().filter(e -> e.getIntern().getId().equals(req.getInternId())).toList();
        }
        if (req.getGroupId() != null) {
            list = list.stream().filter(e -> e.getGroup() != null && e.getGroup().getId().equals(req.getGroupId())).toList();
        }

        IntSummaryStatistics stats = list.stream().map(Evaluation::getOverallScore).filter(Objects::nonNull).mapToInt(Integer::intValue).summaryStatistics();

        List<List<String>> rows = new ArrayList<>();
        for (Evaluation e : list) {
            String typeLabel = mapEvalType(e.getType());
            rows.add(List.of(
                    fullName(e.getIntern()),
                    fullName(e.getEvaluator()),
                    typeLabel,
                    e.getOverallScore() != null ? String.valueOf(e.getOverallScore()) : "",
                    scoresDetail(e),
                    e.getEvaluationDate() != null ? e.getEvaluationDate().toString() : ""
            ));
        }

        return ReportPayload.builder()
                .pdfMainTitle("EVALUATIONS REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Total").value(String.valueOf(list.size())).build(),
                        KpiEntry.builder().label("Average").value(list.isEmpty() ? "—" : String.format(Locale.US, "%.1f", stats.getAverage())).build(),
                        KpiEntry.builder().label("Max").value(stats.getCount() == 0 ? "—" : String.valueOf(stats.getMax())).build(),
                        KpiEntry.builder().label("Min").value(stats.getCount() == 0 ? "—" : String.valueOf(stats.getMin())).build()
                ))
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("Evaluations")
                        .headers(List.of("Intern", "Evaluator", "Type", "Overall", "Criteria (T/C/A/I)", "Date"))
                        .rows(rows)
                        .build()))
                .notes(List.of("Type maps program evaluation kinds to self/peer/supervisor style labels where applicable."))
                .build();
    }

    private static String mapEvalType(Evaluation.EvaluationType t) {
        return switch (t) {
            case MID_TERM -> "Supervisor (mid-term)";
            case FINAL -> "Supervisor (final)";
            case SPOT_CHECK -> "Supervisor (spot)";
        };
    }

    private static String scoresDetail(Evaluation e) {
        return String.format(Locale.US, "%d/%d/%d/%d",
                nz(e.getTechnicalScore()), nz(e.getCommunicationScore()),
                nz(e.getAttendanceScore()), nz(e.getInitiativeScore()));
    }

    private static int nz(Integer x) {
        return x != null ? x : 0;
    }

    private ReportPayload feedbackReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        Instant s = dr.from().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant e = dr.to().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        List<Feedback> list = feedbackRepository.findByCreatedAtBetween(s, e);
        long pos = list.stream().filter(f -> "POSITIVE".equalsIgnoreCase(f.getSentimentLabel())).count();
        long neg = list.stream().filter(f -> "NEGATIVE".equalsIgnoreCase(f.getSentimentLabel())).count();
        long neu = list.size() - pos - neg;

        Map<Long, Long> bySup = list.stream().collect(Collectors.groupingBy(f -> f.getSupervisor().getId(), Collectors.counting()));
        Map<String, Double> supBar = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> en : bySup.entrySet()) {
            userRepository.findById(en.getKey()).ifPresent(u -> supBar.put(fullName(u), en.getValue().doubleValue()));
        }

        List<List<String>> rows = list.stream().map(f -> List.of(
                fullName(f.getSupervisor()),
                fullName(f.getIntern()),
                f.getFeedbackType().name(),
                f.getCreatedAt().toString(),
                truncate(f.getComment(), 100)
        )).toList();

        Set<Long> received = list.stream().map(f -> f.getIntern().getId()).collect(Collectors.toSet());
        List<User> interns = userRepository.findByRoleAndActiveTrue(Role.INTERN);
        List<String> noFb = interns.stream().filter(u -> !received.contains(u.getId())).map(this::fullName).limit(20).toList();

        return ReportPayload.builder()
                .pdfMainTitle("FEEDBACK REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Total feedbacks").value(String.valueOf(list.size())).build(),
                        KpiEntry.builder().label("Positive").value(String.valueOf(pos)).build(),
                        KpiEntry.builder().label("Neutral/other").value(String.valueOf(neu)).build(),
                        KpiEntry.builder().label("Negative").value(String.valueOf(neg)).build()
                ))
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("Feedback lines")
                        .headers(List.of("Supervisor", "Intern", "Type", "Date", "Excerpt"))
                        .rows(rows)
                        .build()))
                .horizontalBarCharts(List.of(HorizontalBarChartSpec.builder()
                        .title("Feedbacks given by supervisor (count)")
                        .values(supBar)
                        .barHexColor("#7c3aed")
                        .valueMode(HorizontalBarValueMode.COUNT)
                        .build()))
                .notes(List.of("Interns with no feedback in period (sample): " + (noFb.isEmpty() ? "none" : String.join(", ", noFb))))
                .build();
    }

    private ReportPayload learningPathReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        List<LearningPath> all = learningPathRepository.findAll();
        long completed = all.stream().filter(lp -> lp.getProgressPercent() != null && lp.getProgressPercent() >= 100).count();
        long inProg = all.stream().filter(lp -> lp.getProgressPercent() != null && lp.getProgressPercent() > 0 && lp.getProgressPercent() < 100).count();
        long notStarted = all.stream().filter(lp -> lp.getProgressPercent() == null || lp.getProgressPercent() == 0).count();

        List<List<String>> rows = all.stream().map(lp -> List.of(
                lp.getUser() != null ? fullName(lp.getUser()) : "—",
                lp.getTitle(),
                lp.getProgressPercent() != null ? String.valueOf(lp.getProgressPercent()) : "0",
                lp.getCreatedAt() != null ? lp.getCreatedAt().toString() : "",
                "",
                lp.getProgressPercent() != null && lp.getProgressPercent() >= 100 ? "Completed" : (lp.getProgressPercent() != null && lp.getProgressPercent() > 0 ? "In progress" : "Not started")
        )).toList();

        return ReportPayload.builder()
                .pdfMainTitle("LEARNING PATHS & SKILLS REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Total modules").value(String.valueOf(all.size())).build(),
                        KpiEntry.builder().label("Completed").value(String.valueOf(completed)).build(),
                        KpiEntry.builder().label("In progress").value(String.valueOf(inProg)).build(),
                        KpiEntry.builder().label("Not started").value(String.valueOf(notStarted)).build()
                ))
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("Learning paths")
                        .headers(List.of("Intern", "Module", "Progress %", "Started", "End", "Status"))
                        .rows(rows)
                        .build()))
                .notes(List.of("Skill gaps: see AI Performance report for AI-identified gaps by intern."))
                .build();
    }

    private ReportPayload aiPerformanceReport(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        List<AiPerformanceEvaluationDto> list = aiInsightsService.evaluateAllActiveInterns();
        int rank = 1;
        List<List<String>> rows = new ArrayList<>();
        for (AiPerformanceEvaluationDto a : list) {
            rows.add(List.of(
                    String.valueOf(rank++),
                    a.getInternName(),
                    String.format(Locale.US, "%.1f", a.getCompositeScore()),
                    String.format(Locale.US, "%.1f", a.getAttendanceScore()),
                    String.format(Locale.US, "%.1f", a.getTaskCompletionScore()),
                    String.format(Locale.US, "%.1f", a.getSkillDevelopmentScore()),
                    String.format(Locale.US, "%.1f", a.getEngagementScore()),
                    a.getRiskLevel(),
                    String.join("; ", a.getRecommendations())
            ));
        }
        long high = list.stream().filter(a -> "HIGH".equals(a.getRiskLevel())).count();

        return ReportPayload.builder()
                .pdfMainTitle("AI PERFORMANCE REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Interns scored").value(String.valueOf(list.size())).build(),
                        KpiEntry.builder().label("High risk").value(String.valueOf(high)).build(),
                        KpiEntry.builder().label("Data window").value("Live signals (see period note)").build(),
                        KpiEntry.builder().label("Engine").value("SOLVIT rule-based AI").build()
                ))
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("Composite ranking")
                        .headers(List.of("Rank", "Intern", "Composite", "Att.", "Tasks", "Skills", "Engage.", "Risk", "Recommendations"))
                        .rows(rows)
                        .build()))
                .notes(List.of("HIGH risk interns require urgent follow-up; recommendations are generated from multi-source signals."))
                .build();
    }

    private ReportPayload weeklySynth(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        ReportDateRange w = ReportDateResolver.weekContaining(req.getDateFrom() != null ? req.getDateFrom() : LocalDate.now());
        List<Attendance> att = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(w.from(), w.to());
        List<Task> tasksCreated = taskRepository.findByActiveTrue().stream()
                .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(w.from().atStartOfDay(ZoneId.systemDefault()).toInstant())
                        && !t.getCreatedAt().isAfter(w.to().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()))
                .toList();
        long tasksDone = tasksCreated.stream().filter(t -> t.getStatus() == Task.TaskStatus.VALIDATED).count();
        List<Feedback> fb = feedbackRepository.findByCreatedAtBetween(
                w.from().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                w.to().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
        List<Project> newProj = projectRepository.findByActiveTrue().stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(w.from().atStartOfDay(ZoneId.systemDefault()).toInstant())
                        && !p.getCreatedAt().isAfter(w.to().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()))
                .toList();

        return ReportPayload.builder()
                .pdfMainTitle("WEEKLY EXECUTIVE SUMMARY")
                .periodDescription(w.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Attendance rows").value(String.valueOf(att.size())).build(),
                        KpiEntry.builder().label("Tasks touched (created week)").value(String.valueOf(tasksCreated.size())).build(),
                        KpiEntry.builder().label("Tasks validated").value(String.valueOf(tasksDone)).build(),
                        KpiEntry.builder().label("Feedbacks").value(String.valueOf(fb.size())).build()
                ))
                .tables(List.of(
                        ReportTableSection.builder().sectionTitle("New projects this week").headers(List.of("Title", "Status"))
                                .rows(newProj.stream().map(p -> List.of(p.getTitle(), String.valueOf(p.getStatus()))).toList())
                                .build()
                ))
                .notes(List.of("Alerts: review HIGH risk interns on AI Performance report; check overdue tasks list."))
                .build();
    }

    private ReportPayload monthlySynth(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        ReportDateRange m = ReportDateResolver.monthOf(
                req.getYear() != null && req.getMonth() != null
                        ? LocalDate.of(req.getYear(), req.getMonth(), 1)
                        : LocalDate.now());
        List<AiPerformanceEvaluationDto> ai = aiInsightsService.evaluateAllActiveInterns();
        Optional<AiPerformanceEvaluationDto> mvp = ai.stream().max(Comparator.comparingDouble(AiPerformanceEvaluationDto::getCompositeScore));

        Map<String, Double> groupScore = new HashMap<>();
        for (ProjectGroup g : projectGroupRepository.findAllActiveWithInternsAndSupervisor()) {
            if (g.getInterns() == null) continue;
            double sum = 0;
            int c = 0;
            for (User u : g.getInterns()) {
                Optional<AiPerformanceEvaluationDto> o = ai.stream().filter(x -> x.getInternId().equals(u.getId())).findFirst();
                if (o.isPresent()) {
                    sum += o.get().getCompositeScore();
                    c++;
                }
            }
            if (c > 0) {
                groupScore.put(g.getName(), sum / c);
            }
        }
        Optional<Map.Entry<String, Double>> bestG = groupScore.entrySet().stream().max(Map.Entry.comparingByValue());

        Map<String, Double> topGroupsByAi = new LinkedHashMap<>();
        groupScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(12)
                .forEach(e -> topGroupsByAi.put(e.getKey(), e.getValue()));

        return ReportPayload.builder()
                .pdfMainTitle("MONTHLY SUMMARY REPORT")
                .periodDescription(m.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Interns (active)").value(String.valueOf(ai.size())).build(),
                        KpiEntry.builder().label("MVP (AI composite)").value(mvp.map(AiPerformanceEvaluationDto::getInternName).orElse("—")).build(),
                        KpiEntry.builder().label("Best group (avg AI)").value(bestG.map(Map.Entry::getKey).orElse("—")).build(),
                        KpiEntry.builder().label("Month").value(m.label()).build()
                ))
                .tables(List.of())
                .horizontalBarCharts(topGroupsByAi.isEmpty() ? List.of() : List.of(HorizontalBarChartSpec.builder()
                        .title("Average AI composite by group (top groups)")
                        .values(topGroupsByAi)
                        .barHexColor("#2563eb")
                        .valueMode(HorizontalBarValueMode.SCORE)
                        .build()))
                .notes(List.of(
                        "Compare attendance vs previous month using Analytics dashboard trend.",
                        "Supervisor of the month can be derived from feedback volume (see Feedback report)."))
                .build();
    }

    private ReportPayload quarterlySynth(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        YearMonth endYm = YearMonth.from(dr.to());
        Map<String, Double> bars = new LinkedHashMap<>();
        for (int i = 2; i >= 0; i--) {
            YearMonth ym = endYm.minusMonths(i);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();
            List<Attendance> a = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(monthStart, monthEnd);
            long p = a.stream().filter(x -> isPresentLike(x.getStatus())).count();
            double rate = a.isEmpty() ? 0 : 100.0 * p / a.size();
            bars.put(ym.toString(), rate);
        }

        return ReportPayload.builder()
                .pdfMainTitle("QUARTERLY TREND REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Quarter").value(dr.label()).build(),
                        KpiEntry.builder().label("Projects (active DB)").value(String.valueOf(projectRepository.findByActiveTrue().size())).build(),
                        KpiEntry.builder().label("Interns").value(String.valueOf(userRepository.findByRoleAndActiveTrue(Role.INTERN).size())).build(),
                        KpiEntry.builder().label("Tasks").value(String.valueOf(taskRepository.countByActiveTrue())).build()
                ))
                .tables(List.of())
                .horizontalBarCharts(List.of(HorizontalBarChartSpec.builder()
                        .title("Attendance presence ratio by month (approx.)")
                        .values(bars)
                        .barHexColor("#2563eb")
                        .valueMode(HorizontalBarValueMode.PERCENT)
                        .build()))
                .notes(List.of("AI score trend: run monthly exports and compare composite averages; intern join/leave tracked via user createdAt in Users admin."))
                .build();
    }

    private ReportPayload annualSynth(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        int y = req.getYear() != null ? req.getYear() : LocalDate.now().getYear();
        List<AiPerformanceEvaluationDto> ai = aiInsightsService.evaluateAllActiveInterns();
        List<AiPerformanceEvaluationDto> top3 = ai.stream().sorted(Comparator.comparingDouble(AiPerformanceEvaluationDto::getCompositeScore).reversed()).limit(3).toList();

        List<List<String>> medals = new ArrayList<>();
        int i = 1;
        for (AiPerformanceEvaluationDto a : top3) {
            medals.add(List.of(String.valueOf(i++), a.getInternName(), String.format(Locale.US, "%.1f", a.getCompositeScore()), "Annual top performer"));
        }

        return ReportPayload.builder()
                .pdfMainTitle("ANNUAL REPORT " + y)
                .periodDescription("Year " + y)
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Total interns (active)").value(String.valueOf(ai.size())).build(),
                        KpiEntry.builder().label("Year").value(String.valueOf(y)).build(),
                        KpiEntry.builder().label("Projects (active)").value(String.valueOf(projectRepository.findByActiveTrue().size())).build(),
                        KpiEntry.builder().label("Attendance records (year)").value(String.valueOf(
                                attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateAsc(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)).size()))
                                .build()
                ))
                .tables(List.of(ReportTableSection.builder()
                        .sectionTitle("Top 3 interns (AI composite)")
                        .headers(List.of("Rank", "Intern", "Score", "Badge"))
                        .rows(medals)
                        .build()))
                .notes(List.of(
                        "Executive summary: use KPIs above with Monthly/Quarterly exports for board packs.",
                        "Next-year recommendations: invest in learning paths for recurring skill gaps (AI Performance report)."))
                .build();
    }

    private ReportPayload groupComparison(ReportRequest req, ReportDateRange dr, String ref, String gen) {
        List<Long> ids = req.getComparisonGroupIds();
        if (ids == null || ids.size() < 2) {
            List<ProjectGroup> all = projectGroupRepository.findAllActiveWithInternsAndSupervisor();
            ids = all.stream().map(ProjectGroup::getId).limit(2).toList();
        }
        if (ids.size() < 2) {
            return emptyPayload("GROUP COMPARISON REPORT", dr, ref, gen, "Need at least two groups to compare.");
        }
        Map<String, Double> attendanceByGroup = new LinkedHashMap<>();
        Map<String, Double> aiByGroup = new LinkedHashMap<>();
        Map<String, Double> tasksByGroup = new LinkedHashMap<>();

        for (Long gid : ids) {
            ProjectGroup g = projectGroupRepository.findByIdAndActiveTrueWithInterns(gid).orElse(null);
            if (g == null) continue;
            List<User> interns = g.getInterns() != null ? g.getInterns() : List.of();
            double sumAi = 0;
            int c = 0;
            long valTasks = 0;
            long pres = 0;
            long tot = 0;
            for (User u : interns) {
                AiPerformanceEvaluationDto ai = aiInsightsService.evaluateInternPerformance(u.getId());
                sumAi += ai.getCompositeScore();
                c++;
                valTasks += taskRepository.countByActiveTrueAndAssignee_IdAndStatus(u.getId(), Task.TaskStatus.VALIDATED);
                List<Attendance> aa = attendanceRepository.findByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateDesc(u.getId(), dr.from(), dr.to());
                tot += aa.size();
                pres += aa.stream().filter(a -> isPresentLike(a.getStatus())).count();
            }
            aiByGroup.put(g.getName(), c == 0 ? 0 : sumAi / c);
            tasksByGroup.put(g.getName(), (double) valTasks);
            attendanceByGroup.put(g.getName(), tot == 0 ? 0 : 100.0 * pres / tot);
        }

        List<List<String>> overviewRows = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (Long gid : ids) {
            projectGroupRepository.findById(gid).ifPresent(g -> {
                int internN = g.getInterns() != null ? g.getInterns().size() : 0;
                long projN = projectRepository.countByActiveTrueAndGroup_Id(gid);
                overviewRows.add(List.of(
                        g.getName(),
                        String.valueOf(internN),
                        String.valueOf(projN)));
                rows.add(List.of(
                        g.getName(),
                        String.format(Locale.US, "%.1f", attendanceByGroup.getOrDefault(g.getName(), 0d)),
                        String.format(Locale.US, "%.0f", tasksByGroup.getOrDefault(g.getName(), 0d)),
                        String.format(Locale.US, "%.1f", aiByGroup.getOrDefault(g.getName(), 0d)),
                        String.valueOf(projN)));
            });
        }

        Optional<String> leader = aiByGroup.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);

        return ReportPayload.builder()
                .pdfMainTitle("GROUP COMPARISON REPORT")
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(
                        KpiEntry.builder().label("Groups compared").value(String.valueOf(ids.size())).build(),
                        KpiEntry.builder().label("Leader (AI avg)").value(leader.orElse("—")).build(),
                        KpiEntry.builder().label("Metric window").value(dr.label()).build(),
                        KpiEntry.builder().label("Project detail").value("Projects report (#5)").build()
                ))
                .tables(List.of(
                        ReportTableSection.builder()
                                .sectionTitle("Group overview")
                                .headers(List.of("Group", "Interns", "Projects (on group)"))
                                .rows(overviewRows)
                                .build(),
                        ReportTableSection.builder()
                                .sectionTitle("Side-by-side metrics")
                                .headers(List.of("Group", "Attendance %", "Tasks done", "AI avg", "Projects (on group)"))
                                .rows(rows)
                                .build()))
                .horizontalBarCharts(List.of(
                        HorizontalBarChartSpec.builder().title("AI composite by group").values(aiByGroup).barHexColor("#2563eb")
                                .valueMode(HorizontalBarValueMode.SCORE).build(),
                        HorizontalBarChartSpec.builder().title("Attendance % by group").values(attendanceByGroup).barHexColor("#7c3aed")
                                .valueMode(HorizontalBarValueMode.PERCENT).build(),
                        HorizontalBarChartSpec.builder().title("Validated tasks by group").values(tasksByGroup).barHexColor("#0d9488")
                                .valueMode(HorizontalBarValueMode.COUNT).build()
                ))
                .notes(List.of("Best AI composite (this window): " + leader.orElse("n/a")
                        + ". Use Attendance summary (#1) for intern-level attendance and Projects (#5) for collaboration."))
                .build();
    }

    private ReportPayload emptyPayload(String title, ReportDateRange dr, String ref, String gen, String note) {
        return ReportPayload.builder()
                .pdfMainTitle(title)
                .periodDescription(dr.label())
                .reference(ref)
                .generatedAtText(gen)
                .kpis(List.of(KpiEntry.builder().label("Status").value("No data").build()))
                .notes(List.of(note))
                .build();
    }

    private List<User> filterInterns(ReportRequest req, List<User> interns) {
        List<User> out = new ArrayList<>(interns);
        if (req.getInternId() != null) {
            out = out.stream().filter(u -> u.getId().equals(req.getInternId())).toList();
        }
        if (req.getSupervisorId() != null) {
            Set<Long> allowed = internProfileRepository.findBySupervisorUserId(req.getSupervisorId()).stream()
                    .map(p -> p.getUser().getId())
                    .collect(Collectors.toSet());
            out = out.stream().filter(u -> allowed.contains(u.getId())).toList();
        }
        if (req.getGroupId() != null) {
            ProjectGroup g = projectGroupRepository.findByIdAndActiveTrueWithInterns(req.getGroupId()).orElse(null);
            if (g != null && g.getInterns() != null) {
                Set<Long> ids = g.getInterns().stream().map(User::getId).collect(Collectors.toSet());
                out = out.stream().filter(u -> ids.contains(u.getId())).toList();
            }
        }
        return out;
    }

    private Map<Long, String> primaryGroupNameByIntern() {
        Map<Long, String> m = new HashMap<>();
        for (ProjectGroup g : projectGroupRepository.findAllActiveWithInternsAndSupervisor()) {
            if (g.getInterns() == null) continue;
            for (User u : g.getInterns()) {
                m.putIfAbsent(u.getId(), g.getName());
            }
        }
        return m;
    }

    private Map<Long, String> supervisorNameByIntern() {
        Map<Long, String> m = new HashMap<>();
        for (InternProfile p : internProfileRepository.findAll()) {
            if (p.getUser() == null || p.getSupervisorUserId() == null) continue;
            userRepository.findById(p.getSupervisorUserId()).ifPresent(s -> m.put(p.getUser().getId(), fullName(s)));
        }
        return m;
    }

    private static boolean isPresentLike(Attendance.AttendanceStatus s) {
        if (s == null) return false;
        return s == Attendance.AttendanceStatus.PRESENT
                || s == Attendance.AttendanceStatus.VALIDATED
                || s == Attendance.AttendanceStatus.REMOTE
                || s == Attendance.AttendanceStatus.EXCUSED;
    }

    private static boolean inRangeTask(Task t, ReportDateRange dr) {
        if (t.getDueDate() != null) {
            return !t.getDueDate().isBefore(dr.from()) && !t.getDueDate().isAfter(dr.to());
        }
        if (t.getCreatedAt() != null) {
            LocalDate cd = t.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            return !cd.isBefore(dr.from()) && !cd.isAfter(dr.to());
        }
        return true;
    }

    private static boolean projectTouchesRange(Project p, ReportDateRange dr) {
        if (p.getStartDate() != null && p.getStartDate().isAfter(dr.to())) {
            return false;
        }
        if (p.getEndDate() != null && p.getEndDate().isBefore(dr.from())) {
            return false;
        }
        return true;
    }

    private String fullName(User u) {
        return u.getFirstName() + " " + u.getLastName();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
