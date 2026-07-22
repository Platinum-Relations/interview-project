package com.reconciliation.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Development conveniences. Wiping data truncates every reconciliation table
 * and restarts identity columns so the next import is run 1 again.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final JdbcTemplate jdbcTemplate;

    public AdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DeleteMapping("/runs")
    @Transactional
    public Map<String, Object> deleteAllRuns() {
        long runs = count("import_run");
        long items = count("reconciliation_item");
        long quarantined = count("quarantined_row");
        long settlements = count("settlement_row");

        // deleteAll() leaves identity sequences alone; truncate + RESTART IDENTITY
        // is the full wipe so the next import is runId 1 again.
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE settlement_row RESTART IDENTITY");
            jdbcTemplate.execute("TRUNCATE TABLE quarantined_row RESTART IDENTITY");
            jdbcTemplate.execute("TRUNCATE TABLE reconciliation_item RESTART IDENTITY");
            jdbcTemplate.execute("TRUNCATE TABLE import_run RESTART IDENTITY");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }

        log.info(
                "Dev reset: wiped {} runs, {} items, {} settlement rows, {} quarantined rows; identity counters restarted",
                runs, items, settlements, quarantined);
        return Map.of(
                "deletedRuns", runs,
                "deletedItems", items,
                "deletedSettlementRows", settlements,
                "deletedQuarantinedRows", quarantined,
                "identityRestarted", true);
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value != null ? value : 0L;
    }
}
