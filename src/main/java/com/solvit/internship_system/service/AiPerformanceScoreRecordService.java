package com.solvit.internship_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solvit.internship_system.dto.ai.AiPerformanceEvaluationDto;
import com.solvit.internship_system.entity.AiPerformanceScoreRecord;
import com.solvit.internship_system.entity.InternRecord;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.AiPerformanceScoreRecordRepository;
import com.solvit.internship_system.repository.InternRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPerformanceScoreRecordService {
    private final AiPerformanceScoreRecordRepository scoreRecordRepository;
    private final InternRecordRepository internRecordRepository;
    private final AiInsightsService aiInsightsService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiPerformanceScoreRecord computeAndStore(Long internRecordId) {
        InternRecord intern = internRecordRepository.findById(internRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("InternRecord", internRecordId));
        AiPerformanceEvaluationDto evaluation = aiInsightsService.evaluateInternPerformance(intern.getUser().getId());

        AiPerformanceScoreRecord latest = scoreRecordRepository.findFirstByIntern_IdOrderByComputedAtDesc(internRecordId)
                .orElse(null);
        BigDecimal finalScore = bd(evaluation.getCompositeScore());
        BigDecimal previous = latest != null ? latest.getFinalScore() : null;
        BigDecimal delta = previous != null ? finalScore.subtract(previous).setScale(2, RoundingMode.HALF_UP) : null;
        AiPerformanceScoreRecord.ScoreTrend trend = delta == null
                ? AiPerformanceScoreRecord.ScoreTrend.STABLE
                : (delta.compareTo(BigDecimal.valueOf(1.0)) > 0 ? AiPerformanceScoreRecord.ScoreTrend.IMPROVING
                : (delta.compareTo(BigDecimal.valueOf(-1.0)) < 0 ? AiPerformanceScoreRecord.ScoreTrend.DECLINING
                : AiPerformanceScoreRecord.ScoreTrend.STABLE));

        Map<String, Object> insightPayload = new LinkedHashMap<>();
        insightPayload.put("earlyWarnings", evaluation.getEarlyWarnings());
        insightPayload.put("modelSummary", evaluation.getModelSummary());

        AiPerformanceScoreRecord row = AiPerformanceScoreRecord.builder()
                .intern(intern)
                .computedAt(Instant.now())
                .weekNumber(LocalDate.now().get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()))
                .attendanceScore(bd(evaluation.getAttendanceScore()))
                .taskCompletionScore(bd(evaluation.getTaskCompletionScore()))
                .workQualityScore(bd(evaluation.getTaskCompletionScore()))
                .technicalSkillsScore(bd(evaluation.getSkillDevelopmentScore()))
                .conductEngagementScore(bd(evaluation.getEngagementScore()))
                .finalScore(finalScore)
                .grade(toGrade(finalScore))
                .prediction(toPrediction(evaluation.getRiskLevel()))
                .predictionConfidence(predictionConfidence(latest))
                .insights(writeJson(insightPayload))
                .recommendations(writeJson(evaluation.getRecommendations()))
                .skillGaps(writeJson(evaluation.getSkillGaps()))
                .scoreTrend(trend)
                .previousScore(previous)
                .scoreChange(delta)
                .createdAt(Instant.now())
                .build();
        return scoreRecordRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<AiPerformanceScoreRecord> history(Long internRecordId) {
        try {
            return scoreRecordRepository.findByIntern_IdOrderByComputedAtDesc(internRecordId);
        } catch (DataAccessException ex) {
            log.error("[ai-v1] Failed to fetch score history for internRecordId={}", internRecordId, ex);
            return List.of();
        }
    }

    private AiPerformanceScoreRecord.Grade toGrade(BigDecimal score) {
        double s = score.doubleValue();
        if (s >= 85) return AiPerformanceScoreRecord.Grade.EXCELLENT;
        if (s >= 70) return AiPerformanceScoreRecord.Grade.GOOD;
        if (s >= 55) return AiPerformanceScoreRecord.Grade.SATISFACTORY;
        if (s >= 40) return AiPerformanceScoreRecord.Grade.NEEDS_IMPROVEMENT;
        return AiPerformanceScoreRecord.Grade.FAILING;
    }

    private AiPerformanceScoreRecord.Prediction toPrediction(String riskLevel) {
        if ("HIGH".equalsIgnoreCase(riskLevel)) return AiPerformanceScoreRecord.Prediction.CRITICAL;
        if ("MEDIUM".equalsIgnoreCase(riskLevel)) return AiPerformanceScoreRecord.Prediction.AT_RISK;
        return AiPerformanceScoreRecord.Prediction.ON_TRACK;
    }

    private BigDecimal predictionConfidence(AiPerformanceScoreRecord latest) {
        if (latest == null) {
            return BigDecimal.valueOf(0.55).setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(0.80).setScale(4, RoundingMode.HALF_UP);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static BigDecimal bd(Double value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
