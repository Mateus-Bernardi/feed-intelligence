package com.feedintelligence.observability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExecutionLedgerService {

    private final ExecutionLedgerRepository repository;

    // Abre um registro de execução
    public ExecutionLedger start(String jobName) {
        ExecutionLedger ledger = ExecutionLedger.builder()
                .jobName(jobName)
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .build();
        return repository.save(ledger);
    }

    // Fecha o registro com sucesso
    public void finish(ExecutionLedger ledger, int found, int skipped) {
        ledger.setFinishedAt(LocalDateTime.now());
        ledger.setStatus("SUCCESS");
        ledger.setArticlesFound(found);
        ledger.setArticlesSkipped(skipped);
        repository.save(ledger);
    }

    // Fecha o registro com falha
    public void fail(ExecutionLedger ledger, String errors) {
        ledger.setFinishedAt(LocalDateTime.now());
        ledger.setStatus("FAILED");
        ledger.setErrors(errors);
        repository.save(ledger);
    }
}