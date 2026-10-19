package com.codeshield.repository;

import com.codeshield.domain.ScanRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanRunRepository extends JpaRepository<ScanRun, Long> {
    List<ScanRun> findTop10ByOrderByStartedAtDesc();
}
