package com.solvit.internship_system.scheduler;

import com.solvit.internship_system.service.AiInsightsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that refreshes AI-driven intern performance scores from multi-source signals.
 * Runs daily to keep risk flags, skill gaps, and recommendations current.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPerformanceRecomputeScheduler {

    private final AiInsightsService aiInsightsService;

    @Scheduled(cron = "0 30 1 * * *", zone = "Africa/Kigali")
    @Transactional
    public void recomputeAiPerformanceScoresNightly() {
        long started = System.currentTimeMillis();
        try {
            int updated = aiInsightsService.recomputePerformanceScoresForAllInterns();
            long elapsedMs = System.currentTimeMillis() - started;
            log.info("[AI-SCHEDULER] recompute complete: internsUpdated={}, elapsedMs={}", updated, elapsedMs);
        } catch (Exception ex) {
            log.error("[AI-SCHEDULER] recompute failed", ex);
        }
    }
}

