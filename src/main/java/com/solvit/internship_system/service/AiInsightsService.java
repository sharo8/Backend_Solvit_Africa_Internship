package com.solvit.internship_system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solvit.internship_system.dto.ai.*;
import com.solvit.internship_system.entity.*;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based "AI" engine: feedback sentiment (lexicon NLP), peer comparison, at-risk prediction.
 */
@Service
@RequiredArgsConstructor
public class AiInsightsService {

    private static final Set<String> POSITIVE_FR = Set.of(
            "excellent", "parfait", "super", "bravo", "bon", "bien", "progrès", "réussi", "motivé",
            "autonome", "rigoureux", "ponctuel", "impressionnant", "merci", "félicitations"
    );
    private static final Set<String> POSITIVE_EN = Set.of(
            "great", "good", "excellent", "progress", "improved", "strong", "solid", "happy", "thanks", "well done"
    );
    private static final Set<String> NEGATIVE_FR = Set.of(
            "mauvais", "insuffisant", "retard", "difficile", "problème", "attention", "décevant",
            "absent", "manque", "erreur", "faible", "améliorer"
    );
    private static final Set<String> NEGATIVE_EN = Set.of(
            "poor", "bad", "late", "issue", "problem", "weak", "concern", "missing", "failed", "improve"
    );

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final AttendanceRepository attendanceRepository;
    private final LearningPathRepository learningPathRepository;
    private final EvaluationRepository evaluationRepository;
    private final PerformanceScoreRepository performanceScoreRepository;
    private final AiConfigurationRepository aiConfigurationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Lexicon-based sentiment for French/English mixed comments. */
    public SentimentResult analyzeSentiment(String comment) {
        if (comment == null || comment.isBlank()) {
            return new SentimentResult("NEUTRAL", 0.0);
        }
        String lower = comment.toLowerCase(Locale.ROOT).replaceAll("[^a-zàâäéèêëïîôùûç\\s]", " ");
        String[] tokens = lower.split("\\s+");
        int pos = 0, neg = 0;
        for (String t : tokens) {
            if (t.length() < 2) continue;
            if (POSITIVE_FR.contains(t) || POSITIVE_EN.contains(t)) pos++;
            if (NEGATIVE_FR.contains(t) || NEGATIVE_EN.contains(t)) neg++;
        }
        int net = pos - neg;
        double score;
        String label;
        if (net > 0) {
            score = Math.min(1.0, 0.2 + net * 0.15);
            label = "POSITIVE";
        } else if (net < 0) {
            score = Math.max(-1.0, -0.2 + net * 0.15);
            label = "NEGATIVE";
        } else {
            score = 0.0;
            label = "NEUTRAL";
        }
        return new SentimentResult(label, score);
    }

    public record SentimentResult(String label, double score) {}

    @Transactional
    public void applySentimentToFeedback(Feedback feedback) {
        if (feedback.getComment() == null || feedback.getComment().isBlank()) return;
        SentimentResult s = analyzeSentiment(feedback.getComment());
        feedback.setSentimentLabel(s.label());
        feedback.setSentimentScore(s.score());
    }

    @Transactional(readOnly = true)
    public AiInsightsSummaryDto buildSummary() {
        Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
        List<Feedback> recent = feedbackRepository.findByCreatedAtAfter(since);
        long pos = recent.stream().filter(f -> "POSITIVE".equals(f.getSentimentLabel())).count();
        long neg = recent.stream().filter(f -> "NEGATIVE".equals(f.getSentimentLabel())).count();
        long neu = recent.stream().filter(f -> f.getSentimentLabel() == null || "NEUTRAL".equals(f.getSentimentLabel())).count();
        double avg = recent.stream()
                .map(Feedback::getSentimentScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        return AiInsightsSummaryDto.builder()
                .feedbackAnalyzed(recent.size())
                .positiveCount(pos)
                .negativeCount(neg)
                .neutralCount(neu)
                .averageSentimentScore(avg)
                .build();
    }

    @Transactional(readOnly = true)
    public PeerComparisonDto compareInternToPeers(Long internId) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        List<User> cohort = userRepository.findByRoleAndActiveTrue(Role.INTERN);
        Map<Long, Double> scores = new HashMap<>();
        for (User u : cohort) {
            scores.put(u.getId(), computeRawCompositeScore(u.getId()));
        }
        Double mine = scores.get(internId);
        if (mine == null) mine = 0.0;
        List<Double> sorted = scores.values().stream().sorted().toList();
        double percentile = percentileRank(sorted, mine);

        double cohortAvg = scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        InternSignals mySignals = calculateSignals(internId);
        CohortPillarAvgs pillarCohort = computeCohortPillarAvgs();
        double momentum = attendanceMomentum(internId);

        List<String> gaps = new ArrayList<>();
        if (mine < cohortAvg - 10) {
            gaps.add("Overall composite below cohort average — rebalance attendance, tasks, and learning time.");
        }
        long myOverdue = taskRepository.countByActiveTrueAndAssignee_IdAndDueDateBeforeAndStatusNotIn(
                internId, LocalDate.now(), Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.VALIDATED, Task.TaskStatus.CANCELLED));
        if (myOverdue > 0) {
            gaps.add(myOverdue + " overdue task(s) — prioritize closing or requesting an extension.");
        }
        for (String g : buildSkillGaps(mySignals, pillarCohort)) {
            if (!gaps.contains(g)) {
                gaps.add(g);
            }
        }

        List<String> early = buildEarlyWarnings(internId, mySignals, pillarCohort, momentum);

        return PeerComparisonDto.builder()
                .internId(internId)
                .internName(intern.getFirstName() + " " + intern.getLastName())
                .compositeScore(mine)
                .cohortAverage(cohortAvg)
                .peerPercentile(percentile)
                .identifiedGaps(gaps)
                .earlyWarnings(early)
                .build();
    }

    private static double percentileRank(List<Double> sortedAsc, double value) {
        if (sortedAsc.isEmpty()) return 50.0;
        int below = 0;
        for (Double v : sortedAsc) {
            if (v < value) below++;
        }
        return 100.0 * below / sortedAsc.size();
    }

    /** Weighted composite 0..100 used for peer ordering. */
    public double computeRawCompositeScore(Long internId) {
        return calculateSignals(internId).compositeScore();
    }

    private record InternSignals(
            double attendanceScore,
            double taskCompletionScore,
            double skillDevelopmentScore,
            double engagementScore,
            double compositeScore,
            long overdueTasks
    ) {}

    private record CohortPillarAvgs(double attendance, double task, double skill, double engagement) {}

    private static final String MODEL_SUMMARY =
            "Weighted model: 30% attendance, 30% tasks, 25% skill development (learning paths and evaluations), "
                    + "15% engagement (feedback sentiment, ratings, volume). Scores are compared to active-intern cohort averages.";

    private static final double DEFAULT_ATTENDANCE_WEIGHT = 20.0;
    private static final double DEFAULT_TASK_COMPLETION_WEIGHT = 25.0;
    private static final double DEFAULT_WORK_QUALITY_WEIGHT = 30.0;
    private static final double DEFAULT_TECHNICAL_SKILLS_WEIGHT = 15.0;
    private static final double DEFAULT_CONDUCT_ENGAGEMENT_WEIGHT = 10.0;
    private static final double DEFAULT_SCORE_GOOD_MIN = 70.0;
    private static final double DEFAULT_SCORE_SATISFACTORY_MIN = 55.0;
    private static final double DEFAULT_ATTENDANCE_WARNING_THRESHOLD = 80.0;

    private CohortPillarAvgs computeCohortPillarAvgs() {
        List<User> interns = userRepository.findByRoleAndActiveTrue(Role.INTERN);
        if (interns.isEmpty()) {
            return new CohortPillarAvgs(50, 50, 50, 50);
        }
        double ta = 0, tt = 0, ts = 0, te = 0;
        for (User u : interns) {
            InternSignals sig = calculateSignals(u.getId());
            ta += sig.attendanceScore();
            tt += sig.taskCompletionScore();
            ts += sig.skillDevelopmentScore();
            te += sig.engagementScore();
        }
        int n = interns.size();
        return new CohortPillarAvgs(ta / n, tt / n, ts / n, te / n);
    }

    private CohortPillarAvgs averageFromSignalsMap(Map<Long, InternSignals> byIntern) {
        if (byIntern.isEmpty()) {
            return new CohortPillarAvgs(50, 50, 50, 50);
        }
        double ta = 0, tt = 0, ts = 0, te = 0;
        for (InternSignals s : byIntern.values()) {
            ta += s.attendanceScore();
            tt += s.taskCompletionScore();
            ts += s.skillDevelopmentScore();
            te += s.engagementScore();
        }
        int n = byIntern.size();
        return new CohortPillarAvgs(ta / n, tt / n, ts / n, te / n);
    }

    private double attendanceWindowRatio(Long internId, LocalDate start, LocalDate endInclusive) {
        List<Attendance> att = attendanceRepository.findForUserInDateRange(internId, start, endInclusive);
        long days = Math.max(1, ChronoUnit.DAYS.between(start, endInclusive));
        long present = att.stream()
                .filter(a -> a.getCheckInAt() != null)
                .filter(a -> a.getStatus() == null || a.getStatus() != Attendance.AttendanceStatus.ABSENT)
                .count();
        return Math.min(1.0, (double) present / days);
    }

    /** Recent15d presence ratio minus prior 15d ratio; negative = declining attendance. */
    private double attendanceMomentum(Long internId) {
        LocalDate end = LocalDate.now();
        LocalDate mid = end.minusDays(15);
        LocalDate start = end.minusDays(30);
        double recent = attendanceWindowRatio(internId, mid.plusDays(1), end);
        double prior = attendanceWindowRatio(internId, start, mid);
        return recent - prior;
    }

    private List<String> buildEarlyWarnings(Long internId, InternSignals s, CohortPillarAvgs cohort, double momentum) {
        List<String> w = new ArrayList<>();
        if (momentum < -0.12) {
            w.add("Early signal: attendance presence dropped in the last 15 days vs the previous 15 days.");
        }
        if (s.overdueTasks() >= 2) {
            w.add("Early signal: multiple overdue tasks — elevated risk of backlog and missed deadlines.");
        }
        if (s.compositeScore() < 52 && momentum < -0.05) {
            w.add("Early signal: composite score is soft while attendance momentum is negative.");
        }
        if (s.taskCompletionScore() < cohort.task() - 12) {
            w.add("Early signal: task pillar is well below cohort average — prioritize delivery and scope clarity.");
        }
        if (s.engagementScore() < cohort.engagement() - 12) {
            w.add("Early signal: engagement pillar trails peers — increase structured check-ins and feedback loops.");
        }
        long recentFeedback = feedbackRepository.findByIntern_IdOrderByCreatedAtDesc(internId).stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(Instant.now().minus(21, ChronoUnit.DAYS)))
                .count();
        if (recentFeedback == 0 && cohort.engagement() > 40) {
            w.add("Early signal: no supervisor feedback in the last 21 days while cohort engagement is active.");
        }
        return w;
    }

    private void addPillarGap(List<String> gaps, String shortLabel, double mine, double cohortAvg) {
        if (mine >= 60 && mine >= cohortAvg - 5) {
            return;
        }
        if (mine < cohortAvg - 8) {
            gaps.add(String.format("%s: %.0f vs cohort avg %.0f (gap %.0f pts).", shortLabel, mine, cohortAvg, cohortAvg - mine));
        } else if (mine < 60) {
            gaps.add(shortLabel + " below programme target (60).");
        }
    }

    private List<String> buildSkillGaps(InternSignals s, CohortPillarAvgs cohort) {
        List<String> gaps = new ArrayList<>();
        addPillarGap(gaps, "Attendance consistency", s.attendanceScore(), cohort.attendance());
        addPillarGap(gaps, "Task completion & deadlines", s.taskCompletionScore(), cohort.task());
        addPillarGap(gaps, "Skill development", s.skillDevelopmentScore(), cohort.skill());
        addPillarGap(gaps, "Engagement & communication", s.engagementScore(), cohort.engagement());
        return gaps;
    }

    private List<String> buildRecommendationsFromGaps(List<String> gaps) {
        List<String> recs = new ArrayList<>();
        boolean att = gaps.stream().anyMatch(g -> g.contains("Attendance"));
        boolean task = gaps.stream().anyMatch(g -> g.contains("Task"));
        boolean skill = gaps.stream().anyMatch(g -> g.contains("Skill development"));
        boolean eng = gaps.stream().anyMatch(g -> g.contains("Engagement"));
        if (att) {
            recs.add("Stabilize attendance: fixed check-in time, proactive absence notices, and weekly rhythm with your supervisor.");
        }
        if (task) {
            recs.add("Task focus: list overdue items, negotiate dates if blocked, and close one task before starting two new ones.");
        }
        if (skill) {
            recs.add("Skills: complete the next learning-path module, pair with a mentor, and apply one new technique on a real task.");
        }
        if (eng) {
            recs.add("Engagement: short daily updates, ask for feedback after each deliverable, and document decisions in writing.");
        }
        if (recs.isEmpty()) {
            recs.add("Maintain momentum: take on a stretch assignment and mentor another intern on a topic you mastered.");
        }
        return recs;
    }

    private AiPerformanceEvaluationDto buildEvaluationDto(User intern, InternSignals s, CohortPillarAvgs cohort) {
        Map<String, Double> thresholds = loadThresholdConfig();
        double goodMin = thresholds.getOrDefault("score_good_min", DEFAULT_SCORE_GOOD_MIN);
        double satisfactoryMin = thresholds.getOrDefault("score_satisfactory_min", DEFAULT_SCORE_SATISFACTORY_MIN);
        double attendanceWarningThreshold = thresholds.getOrDefault("attendance_warning_threshold", DEFAULT_ATTENDANCE_WARNING_THRESHOLD);
        double momentum = attendanceMomentum(intern.getId());
        List<String> gaps = buildSkillGaps(s, cohort);
        List<String> recs = buildRecommendationsFromGaps(gaps);
        List<String> early = buildEarlyWarnings(intern.getId(), s, cohort, momentum);

        String riskLevel = s.compositeScore() < satisfactoryMin || s.attendanceScore() < attendanceWarningThreshold - 10 || s.overdueTasks() >= 3 ? "HIGH"
                : (s.compositeScore() < goodMin || s.overdueTasks() >= 1
                || (s.compositeScore() < 68 && early.size() >= 2) ? "MEDIUM" : "LOW");

        return AiPerformanceEvaluationDto.builder()
                .internId(intern.getId())
                .internName(intern.getFirstName() + " " + intern.getLastName())
                .attendanceScore(s.attendanceScore())
                .taskCompletionScore(s.taskCompletionScore())
                .skillDevelopmentScore(s.skillDevelopmentScore())
                .engagementScore(s.engagementScore())
                .compositeScore(s.compositeScore())
                .cohortAvgAttendance(cohort.attendance())
                .cohortAvgTasks(cohort.task())
                .cohortAvgSkills(cohort.skill())
                .cohortAvgEngagement(cohort.engagement())
                .riskLevel(riskLevel)
                .skillGaps(gaps)
                .recommendations(recs)
                .earlyWarnings(early)
                .modelSummary(MODEL_SUMMARY)
                .build();
    }

    private InternSignals calculateSignals(Long internId) {
        Map<String, Double> weights = loadWeightConfig();
        double attendanceWeight = weights.getOrDefault("attendance_weight", DEFAULT_ATTENDANCE_WEIGHT);
        double taskWeight = weights.getOrDefault("task_completion_weight", DEFAULT_TASK_COMPLETION_WEIGHT);
        double qualityWeight = weights.getOrDefault("work_quality_weight", DEFAULT_WORK_QUALITY_WEIGHT);
        double skillsWeight = weights.getOrDefault("technical_skills_weight", DEFAULT_TECHNICAL_SKILLS_WEIGHT);
        double conductWeight = weights.getOrDefault("conduct_engagement_weight", DEFAULT_CONDUCT_ENGAGEMENT_WEIGHT);
        double totalWeight = Math.max(1.0, attendanceWeight + taskWeight + qualityWeight + skillsWeight + conductWeight);

        long totalTasks = taskRepository.countByActiveTrueAndAssignee_Id(internId);
        long done = taskRepository.countByActiveTrueAndAssignee_IdAndStatusIn(
                internId, Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.VALIDATED));
        long overdue = taskRepository.countByActiveTrueAndAssignee_IdAndDueDateBeforeAndStatusNotIn(
                internId, LocalDate.now(), Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.VALIDATED, Task.TaskStatus.CANCELLED));
        double completionRatio = totalTasks == 0 ? 0.5 : (double) done / totalTasks;
        double overduePenalty = Math.min(0.35, overdue * 0.08);
        double taskCompletionScore = 100.0 * Math.max(0.0, completionRatio - overduePenalty);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        List<Attendance> att = attendanceRepository.findForUserInDateRange(internId, start, end);
        long workdays = Math.max(1, ChronoUnit.DAYS.between(start, end));
        long present = att.stream()
                .filter(a -> a.getStatus() != null && a.getStatus() != Attendance.AttendanceStatus.ABSENT)
                .filter(a -> a.getCheckInAt() != null)
                .count();
        double attRatio = Math.min(1.0, (double) present / workdays);
        double attendanceScore = 100.0 * attRatio;

        List<Feedback> fb = feedbackRepository.findByIntern_IdOrderByCreatedAtDesc(internId);
        double sent = fb.stream()
                .map(Feedback::getSentimentScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double sentimentNorm = (sent + 1.0) / 2.0; // 0..1
        double feedbackRatingNorm = fb.stream()
                .map(Feedback::getRatingScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(3.0) / 5.0;

        List<LearningPath> learningPaths = learningPathRepository.findByUser_IdOrderByCreatedAtDesc(internId);
        double learningProgressNorm = learningPaths.stream()
                .map(LearningPath::getProgressPercent)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(50.0) / 100.0;

        List<Evaluation> evaluations = evaluationRepository.findByIntern_IdAndActiveTrue(internId);
        double evaluationNorm = evaluations.stream()
                .mapToDouble(e -> {
                    double t = e.getTechnicalScore() != null ? e.getTechnicalScore() : 0;
                    double c = e.getCommunicationScore() != null ? e.getCommunicationScore() : 0;
                    double a = e.getAttendanceScore() != null ? e.getAttendanceScore() : 0;
                    double i = e.getInitiativeScore() != null ? e.getInitiativeScore() : 0;
                    return (t + c + a + i) / 4.0;
                })
                .average()
                .orElse(55.0) / 100.0;

        double skillDevelopmentScore = 100.0 * (0.6 * learningProgressNorm + 0.4 * evaluationNorm);

        double feedbackVolumeNorm = Math.min(1.0, fb.stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(Instant.now().minus(30, ChronoUnit.DAYS)))
                .count() / 6.0);
        double engagementScore = 100.0 * (0.5 * sentimentNorm + 0.3 * feedbackRatingNorm + 0.2 * feedbackVolumeNorm);

        List<Task> allTasksForIntern = taskRepository.findByActiveTrueAndAssignee_IdOrderByCreatedAtDesc(
                internId,
                org.springframework.data.domain.Pageable.unpaged()
        ).getContent();
        double workQualityFromTasks = allTasksForIntern.isEmpty() ? 60.0 : 50.0;
        List<Task> validatedTasks = allTasksForIntern.stream()
                .filter(t -> t.getStatus() == Task.TaskStatus.VALIDATED)
                .toList();
        if (!validatedTasks.isEmpty()) {
            workQualityFromTasks = validatedTasks.stream()
                    .map(Task::getEstimatedHours) // fallback signal to avoid null quality in legacy task rows
                    .filter(Objects::nonNull)
                    .mapToDouble(v -> Math.max(1, Math.min(5, v / 2.0)))
                    .average()
                    .orElse(3.0) * 20.0;
        }
        double workQualityScore = clamp100(0.65 * workQualityFromTasks + 0.35 * evaluationNorm * 100.0);

        double composite = ((attendanceScore * attendanceWeight)
                + (taskCompletionScore * taskWeight)
                + (workQualityScore * qualityWeight)
                + (skillDevelopmentScore * skillsWeight)
                + (engagementScore * conductWeight)) / totalWeight;

        return new InternSignals(
                clamp100(attendanceScore),
                clamp100(taskCompletionScore),
                clamp100(skillDevelopmentScore),
                clamp100(engagementScore),
                clamp100(composite),
                overdue
        );
    }

    private static double clamp100(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private Map<String, Double> loadAiConfig(List<String> keys, Map<String, Double> defaults) {
        Map<String, Double> values = new HashMap<>(defaults);
        List<AiConfiguration> rows = aiConfigurationRepository.findByConfigKeyIn(keys);
        for (AiConfiguration row : rows) {
            if (row.getConfigValue() == null) {
                continue;
            }
            try {
                values.put(row.getConfigKey(), Double.parseDouble(row.getConfigValue().trim()));
            } catch (NumberFormatException ignored) {
                // Keep default when DB value is malformed.
            }
        }
        return values;
    }

    private Map<String, Double> loadWeightConfig() {
        Map<String, Double> defaults = Map.of(
                "attendance_weight", DEFAULT_ATTENDANCE_WEIGHT,
                "task_completion_weight", DEFAULT_TASK_COMPLETION_WEIGHT,
                "work_quality_weight", DEFAULT_WORK_QUALITY_WEIGHT,
                "technical_skills_weight", DEFAULT_TECHNICAL_SKILLS_WEIGHT,
                "conduct_engagement_weight", DEFAULT_CONDUCT_ENGAGEMENT_WEIGHT
        );
        return loadAiConfig(new ArrayList<>(defaults.keySet()), defaults);
    }

    private Map<String, Double> loadThresholdConfig() {
        Map<String, Double> defaults = Map.of(
                "score_good_min", DEFAULT_SCORE_GOOD_MIN,
                "score_satisfactory_min", DEFAULT_SCORE_SATISFACTORY_MIN,
                "attendance_warning_threshold", DEFAULT_ATTENDANCE_WARNING_THRESHOLD
        );
        return loadAiConfig(new ArrayList<>(defaults.keySet()), defaults);
    }

    /**
     * Recomputes monthly {@link PerformanceScore} for every active intern: overall score, peer percentile, at-risk flag, JSON recommendations.
     */
    @Transactional
    public int recomputePerformanceScoresForAllInterns() {
        YearMonth ym = YearMonth.now();
        String periodValue = ym.toString();
        List<User> interns = userRepository.findByRoleAndActiveTrue(Role.INTERN);

        Map<Long, Double> raw = new HashMap<>();
        for (User u : interns) {
            raw.put(u.getId(), computeRawCompositeScore(u.getId()));
        }
        List<Double> sorted = raw.values().stream().sorted().toList();

        int updated = 0;
        for (User intern : interns) {
            upsertMonthlyPerformanceScore(intern, periodValue, raw, sorted);
            updated++;
        }
        return updated;
    }

    /**
     * Recomputes the current month's {@link PerformanceScore} row for one intern (peer percentile uses all active interns).
     */
    @Transactional
    public void recomputePerformanceScoreForIntern(Long internId) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            return;
        }
        YearMonth ym = YearMonth.now();
        String periodValue = ym.toString();
        List<User> interns = userRepository.findByRoleAndActiveTrue(Role.INTERN);
        Map<Long, Double> raw = new HashMap<>();
        for (User u : interns) {
            raw.put(u.getId(), computeRawCompositeScore(u.getId()));
        }
        List<Double> sorted = raw.values().stream().sorted().toList();
        upsertMonthlyPerformanceScore(intern, periodValue, raw, sorted);
    }

    private void upsertMonthlyPerformanceScore(
            User intern,
            String periodValue,
            Map<Long, Double> raw,
            List<Double> sortedScores
    ) {
        double score = raw.getOrDefault(intern.getId(), 0.0);
        InternSignals signals = calculateSignals(intern.getId());
        double percentile = percentileRank(sortedScores, score);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        List<Attendance> att = attendanceRepository.findForUserInDateRange(intern.getId(), start, end);
        long workdays = Math.max(1, ChronoUnit.DAYS.between(start, end));
        long present = att.stream()
                .filter(a -> a.getCheckInAt() != null)
                .filter(a -> a.getStatus() == null || a.getStatus() != Attendance.AttendanceStatus.ABSENT)
                .count();
        double attRatio = (double) present / workdays;

        boolean atRisk = score < 50 || attRatio < 0.60 || signals.overdueTasks() >= 2;

        CohortPillarAvgs cohort = computeCohortPillarAvgs();
        List<String> skillGaps = buildSkillGaps(signals, cohort);
        List<String> personalizedRecommendations = buildRecommendationsFromGaps(skillGaps);
        double momentum = attendanceMomentum(intern.getId());
        List<String> earlyList = buildEarlyWarnings(intern.getId(), signals, cohort, momentum);

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("summary", atRisk
                ? "Intervention recommended: low composite score or attendance/constraints detected."
                : "On track; continue current learning plan.");
        rec.put("peerPercentile", Math.round(percentile));
        rec.put("skillGaps", skillGaps);
        rec.put("recommendations", personalizedRecommendations);
        rec.put("earlyWarnings", earlyList);
        String recJson;
        try {
            recJson = objectMapper.writeValueAsString(rec);
        } catch (JsonProcessingException e) {
            recJson = "{}";
        }

        PerformanceScore row = performanceScoreRepository
                .findByIntern_IdAndPeriodTypeAndPeriodValue(intern.getId(), "MONTHLY", periodValue)
                .orElse(PerformanceScore.builder()
                        .intern(intern)
                        .periodType("MONTHLY")
                        .periodValue(periodValue)
                        .build());
        row.setOverallScore(score);
        row.setPeerPercentile(percentile);
        row.setAtRisk(atRisk);
        row.setRecommendations(recJson);
        try {
            row.setSkillGapData(objectMapper.writeValueAsString(Map.of(
                    "attendanceScore", signals.attendanceScore(),
                    "taskCompletionScore", signals.taskCompletionScore(),
                    "skillDevelopmentScore", signals.skillDevelopmentScore(),
                    "engagementScore", signals.engagementScore(),
                    "attendanceRatio30d", attRatio,
                    "overdueTasks", signals.overdueTasks(),
                    "identifiedSkillGaps", skillGaps
            )));
        } catch (JsonProcessingException e) {
            row.setSkillGapData("{}");
        }
        performanceScoreRepository.save(row);
    }

    @Transactional(readOnly = true)
    public AiPerformanceEvaluationDto evaluateInternPerformance(Long internId) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        InternSignals s = calculateSignals(internId);
        CohortPillarAvgs cohort = computeCohortPillarAvgs();
        return buildEvaluationDto(intern, s, cohort);
    }

    @Transactional(readOnly = true)
    public List<AiPerformanceEvaluationDto> evaluateAllActiveInterns() {
        List<User> interns = userRepository.findByRoleAndActiveTrue(Role.INTERN);
        Map<Long, InternSignals> sigById = new LinkedHashMap<>();
        for (User u : interns) {
            sigById.put(u.getId(), calculateSignals(u.getId()));
        }
        CohortPillarAvgs cohort = averageFromSignalsMap(sigById);
        return interns.stream()
                .map(u -> buildEvaluationDto(u, sigById.get(u.getId()), cohort))
                .sorted(Comparator.comparingDouble(AiPerformanceEvaluationDto::getCompositeScore).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackTrendPointDto> feedbackTrendLastMonths(int months) {
        List<FeedbackTrendPointDto> out = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.from(now).minusMonths(i);
            Instant start = ym.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
            Instant end = ym.atEndOfMonth().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant();
            List<Feedback> list = feedbackRepository.findByCreatedAtBetween(start, end);
            double avg = list.stream().map(Feedback::getSentimentScore).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
            out.add(FeedbackTrendPointDto.builder()
                    .period(ym.toString())
                    .averageSentiment(avg)
                    .count(list.size())
                    .build());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AtRiskInternBriefDto> listAtRiskBriefs() {
        return performanceScoreRepository.findByAtRiskTrue().stream()
                .map(ps -> AtRiskInternBriefDto.builder()
                        .internId(ps.getIntern().getId())
                        .name(ps.getIntern().getFirstName() + " " + ps.getIntern().getLastName())
                        .overallScore(ps.getOverallScore())
                        .peerPercentile(ps.getPeerPercentile())
                        .periodValue(ps.getPeriodValue())
                        .build())
                .collect(Collectors.toList());
    }
}
