package com.feedintelligence.observability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExecutionLedgerRepository extends JpaRepository<ExecutionLedger, UUID> {
}