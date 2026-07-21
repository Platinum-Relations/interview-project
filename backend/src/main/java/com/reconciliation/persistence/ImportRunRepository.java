package com.reconciliation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportRunRepository extends JpaRepository<ImportRunEntity, Long> {

    Optional<ImportRunEntity> findByContentHash(String contentHash);

    Optional<ImportRunEntity> findTopByOrderByImportedAtDesc();
}
