package com.codeshield.repository;

import com.codeshield.domain.ApplicationAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAssetRepository extends JpaRepository<ApplicationAsset, Long> {
}
