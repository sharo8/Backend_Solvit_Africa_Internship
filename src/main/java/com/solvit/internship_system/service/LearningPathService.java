package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.ai.AiPerformanceEvaluationDto;
import com.solvit.internship_system.dto.learning.LearningPathRecommendationDto;
import com.solvit.internship_system.entity.LearningPath;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.LearningPathRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LearningPathService {

    private final LearningPathRepository learningPathRepository;
    private final UserRepository userRepository;
    private final AiInsightsService aiInsightsService;

    @Transactional
    public LearningPath create(Long userId, LearningPath path) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        path.setUser(user);
        return learningPathRepository.save(path);
    }

    public List<LearningPath> getByUser(Long userId) {
        return learningPathRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    public LearningPath getById(Long id) {
        return learningPathRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("LearningPath", id));
    }

    @Transactional
    public LearningPath updateProgress(Long id, Integer progressPercent) {
        LearningPath p = getById(id);
        p.setProgressPercent(progressPercent);
        return learningPathRepository.save(p);
    }

    @Transactional(readOnly = true)
    public Page<LearningPath> listAll(Pageable pageable) {
        return learningPathRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public LearningPath createForIntern(Long internUserId, LearningPath path) {
        User intern = userRepository.findById(internUserId).orElseThrow(() -> new ResourceNotFoundException("User", internUserId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("Learning paths can only be assigned to interns");
        }
        path.setId(null);
        path.setUser(intern);
        path.setCreatedAt(null);
        path.setUpdatedAt(null);
        return learningPathRepository.save(path);
    }

    @Transactional
    public void delete(Long id) {
        if (!learningPathRepository.existsById(id)) {
            throw new ResourceNotFoundException("LearningPath", id);
        }
        learningPathRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<LearningPathRecommendationDto> recommendForIntern(Long internUserId) {
        AiPerformanceEvaluationDto ai = aiInsightsService.evaluateInternPerformance(internUserId);
        Set<String> gaps = new LinkedHashSet<>(ai.getSkillGaps() == null ? List.of() : ai.getSkillGaps());
        List<LearningPathRecommendationDto> out = new ArrayList<>();
        for (String gap : gaps) {
            String g = gap.toLowerCase();
            if (g.contains("attendance")) {
                out.add(LearningPathRecommendationDto.builder()
                        .skillGap(gap)
                        .title("Attendance & Professional Discipline")
                        .description("Build consistent attendance habits and daily accountability.")
                        .externalUrl("https://www.coursera.org/learn/work-smarter-not-harder")
                        .build());
            } else if (g.contains("task")) {
                out.add(LearningPathRecommendationDto.builder()
                        .skillGap(gap)
                        .title("Task Planning and Deadline Management")
                        .description("Improve sprint planning, prioritization, and delivery consistency.")
                        .externalUrl("https://www.atlassian.com/agile/project-management")
                        .build());
            } else if (g.contains("skill")) {
                out.add(LearningPathRecommendationDto.builder()
                        .skillGap(gap)
                        .title("Core Technical Skill Development")
                        .description("Targeted technical upskilling path based on current evaluation profile.")
                        .externalUrl("https://www.freecodecamp.org/learn")
                        .build());
            } else if (g.contains("engagement") || g.contains("communication")) {
                out.add(LearningPathRecommendationDto.builder()
                        .skillGap(gap)
                        .title("Communication & Team Collaboration")
                        .description("Strengthen update cadence, communication clarity, and teamwork.")
                        .externalUrl("https://www.coursera.org/learn/wharton-communication-skills")
                        .build());
            }
        }
        if (out.isEmpty()) {
            out.add(LearningPathRecommendationDto.builder()
                    .skillGap("General development")
                    .title("Continuous Improvement Track")
                    .description("Maintain momentum with advanced internship growth objectives.")
                    .externalUrl("https://roadmap.sh")
                    .build());
        }
        return out;
    }

    @Transactional
    public int autoAssignRecommendedForIntern(Long internUserId) {
        User intern = userRepository.findById(internUserId).orElseThrow(() -> new ResourceNotFoundException("User", internUserId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("Learning paths can only be assigned to interns");
        }
        List<LearningPathRecommendationDto> recs = recommendForIntern(internUserId);
        int created = 0;
        for (LearningPathRecommendationDto rec : recs) {
            LearningPath p = LearningPath.builder()
                    .user(intern)
                    .title(rec.getTitle())
                    .description(rec.getDescription())
                    .externalUrl(rec.getExternalUrl())
                    .recommendedFromSkillGap(true)
                    .progressPercent(0)
                    .build();
            learningPathRepository.save(p);
            created++;
        }
        return created;
    }
}
