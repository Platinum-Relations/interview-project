package com.ek.reconciliation;

import com.ek.reconciliation.api.ImportResponse;
import com.ek.reconciliation.importing.InternalTransactionImportService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SettlementReconciliationApplicationTests {

    private final InternalTransactionImportService internalTransactionImportService;
    private final JdbcTemplate jdbcTemplate;
    private final Path configuredInternalTransactionsPath;

    @Autowired
    SettlementReconciliationApplicationTests(
            InternalTransactionImportService internalTransactionImportService,
            JdbcTemplate jdbcTemplate,
            @Value("${reconciliation.imports.internal-transactions.directory}") String directory,
            @Value("${reconciliation.imports.internal-transactions.filename}") String filename
    ) {
        this.internalTransactionImportService = internalTransactionImportService;
        this.jdbcTemplate = jdbcTemplate;
        this.configuredInternalTransactionsPath = Path.of(directory, filename);
    }

    @Test
    void internalTransactionCountMatchesValidRowsFromConfiguredImportFile() throws Exception {
        int csvRecordCount = countConfiguredCsvRecords();

        ImportResponse response = internalTransactionImportService.populateFromDefaultCsv();

        Integer internalTransactionCount = jdbcTemplate.queryForObject(
                "select count(*) from internal_transaction",
                Integer.class
        );

        assertThat(internalTransactionCount)
                .isEqualTo(response.validRowCount());

        assertThat(response.validRowCount() + response.quarantinedRowCount())
                .isEqualTo(csvRecordCount);
    }

    private int countConfiguredCsvRecords() throws Exception {
        try (Reader reader = Files.newBufferedReader(configuredInternalTransactionsPath)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .get()
                    .parse(reader);

            int count = 0;
            for (CSVRecord ignored : records) {
                count++;
            }
            return count;
        }
    }
}