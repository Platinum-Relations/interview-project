package com.reconciliation.persistence;

import com.reconciliation.engine.Classification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItemEntity, Long> {

    List<ReconciliationItemEntity> findByRunIdOrderById(Long runId);

    List<ReconciliationItemEntity> findByRunIdAndClassificationOrderById(Long runId, Classification classification);
}
