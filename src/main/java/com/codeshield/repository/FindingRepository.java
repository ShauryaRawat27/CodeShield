package com.codeshield.repository;

import com.codeshield.domain.Finding;
import com.codeshield.domain.FindingStatus;
import com.codeshield.domain.Severity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findTop8ByOrderByCreatedAtDesc();
    List<Finding> findAllByOrderByRiskScoreDescCreatedAtDesc();
    List<Finding> findByStatusOrderByRiskScoreDescCreatedAtDesc(FindingStatus status);
    long countByStatus(FindingStatus status);
    long countBySeverityAndStatus(Severity severity, FindingStatus status);
}
