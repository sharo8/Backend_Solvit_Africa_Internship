package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.PerformanceScore;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.PerformanceScoreRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceScoreService {

    private final PerformanceScoreRepository performanceScoreRepository;
    private final UserRepository userRepository;

    public PerformanceScore create(Long internId, PerformanceScore score) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        score.setIntern(intern);
        return performanceScoreRepository.save(score);
    }

    public List<PerformanceScore> getByIntern(Long internId, int limit) {
        return performanceScoreRepository.findByIntern_IdOrderByCreatedAtDesc(internId, PageRequest.of(0, limit));
    }

    public List<PerformanceScore> getAtRisk() {
        return performanceScoreRepository.findByAtRiskTrue();
    }

    public PerformanceScore getById(Long id) {
        return performanceScoreRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("PerformanceScore", id));
    }
}
