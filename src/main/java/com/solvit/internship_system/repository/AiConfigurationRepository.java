package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.AiConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiConfigurationRepository extends JpaRepository<AiConfiguration, Long> {
    List<AiConfiguration> findByConfigKeyIn(List<String> keys);
}
