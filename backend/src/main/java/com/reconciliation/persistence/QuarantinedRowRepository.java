package com.reconciliation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuarantinedRowRepository extends JpaRepository<QuarantinedRowEntity, Long> {

    List<QuarantinedRowEntity> findByRunIdOrderById(Long runId);
}
