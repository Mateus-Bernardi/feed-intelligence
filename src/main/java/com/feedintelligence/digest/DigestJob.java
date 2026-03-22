package com.feedintelligence.digest;

import com.feedintelligence.observability.ExecutionLedger;
import com.feedintelligence.observability.ExecutionLedgerService;
import com.feedintelligence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestJob {

    private final UserRepository userRepository;
    private final DigestService digestService;
    private final ExecutionLedgerService ledgerService;

    // Roda a cada hora — verifica quais usuários têm digest_hour == hora atual
    @Scheduled(cron = "0 0 * * * *")
    public void sendDigests() {
        int currentHour = LocalTime.now().getHour();
        log.info("DigestJob running for hour: {}", currentHour);

        ExecutionLedger ledger = ledgerService.start("DIGEST");
        int sent = 0;

        try {
            // Busca usuários cuja hora configurada é a hora atual
            var users = userRepository.findByDigestHourAndActiveTrue(currentHour);
            log.info("Found {} users scheduled for hour {}", users.size(), currentHour);

            for (var user : users) {
                try {
                    digestService.sendDigestForUser(user);
                    sent++;
                } catch (Exception e) {
                    log.error("Error sending digest for user {}: {}",
                            user.getEmail(), e.getMessage());
                }
            }

            // Processa a fila de retry independente da hora
            digestService.processRetryQueue();

            ledgerService.finish(ledger, sent, 0);
            log.info("DigestJob finished — {} digests sent", sent);

        } catch (Exception e) {
            ledgerService.fail(ledger, e.getMessage());
            log.error("DigestJob failed: {}", e.getMessage(), e);
        }
    }
}
