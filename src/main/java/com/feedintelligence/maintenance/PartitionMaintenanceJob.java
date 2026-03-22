package com.feedintelligence.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionMaintenanceJob {

    private final JdbcTemplate jdbcTemplate;

    // Roda todo dia às 01h
    @Scheduled(cron = "0 0 1 * * *")
    public void ensureNextMonthPartition() {
        try {
            LocalDate nextMonth = LocalDate.now()
                    .plusMonths(1)
                    .withDayOfMonth(1);

            String partitionName = "articles_"
                    + nextMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"));

            String start = nextMonth.toString();
            String end = nextMonth.plusMonths(1).toString();

            String sql = """
                    CREATE TABLE IF NOT EXISTS %s PARTITION OF articles
                    FOR VALUES FROM ('%s') TO ('%s')
                    """.formatted(partitionName, start, end);

            jdbcTemplate.execute(sql);
            log.info("Partition ensured: {}", partitionName);

        } catch (Exception e) {
            log.error("PartitionMaintenanceJob failed: {}", e.getMessage(), e);
        }
    }
}