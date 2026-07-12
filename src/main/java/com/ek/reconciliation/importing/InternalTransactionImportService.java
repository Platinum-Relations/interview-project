package com.ek.reconciliation.importing;

import com.ek.reconciliation.api.ImportResponse;
import com.ek.reconciliation.reference.TransactionTypes;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

@Service
public class InternalTransactionImportService {

    private static final String SOURCE_TYPE = "INTERNAL_TRANSACTIONS";
    private final Path defaultSourcePath;
    private final JdbcTemplate jdbcTemplate;

    public InternalTransactionImportService(
            JdbcTemplate jdbcTemplate,
            @Value("${reconciliation.imports.internal-transactions.directory}") String defaultSourceDirectory,
            @Value("${reconciliation.imports.internal-transactions.filename}") String defaultSourceFilename
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.defaultSourcePath = Path.of(defaultSourceDirectory, defaultSourceFilename);
    }


    @Transactional
    public ImportResponse populateFromDefaultCsv() {
        return populateFromCsv(defaultSourcePath);
    }

    @Transactional
    public ImportResponse populateFromCsv(Path sourcePath) {
        long importBatchId = createImportBatch(sourcePath);

        int validRowCount = 0;
        int quarantinedRowCount = 0;
        BigDecimal grossSalesAmount = new BigDecimal("0.00");
        BigDecimal grossRefundAmount = new BigDecimal("0.00");

        // Development-friendly behavior: make the endpoint repeatable.
        jdbcTemplate.update("delete from internal_transaction");

        try (Reader reader = Files.newBufferedReader(sourcePath)) {
            // split these up for ease in debugger if needed, not fond of too much "cleverness" that makes
            // debugging chained methods even more difficult.
            Iterable<CSVRecord> records;
            records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true).get()
                    .parse(reader);

            for (CSVRecord record : records) {
                try {
                    InternalTransactionRow row = parseRow(record);
                    insertInternalTransaction(importBatchId, row);
                    validRowCount++;
                    // add to total gross SALEs if SALE type and same for REFUNDs
                    if (TransactionTypes.SALE.name().equalsIgnoreCase(row.transactionType)) {
                        grossSalesAmount = grossSalesAmount.add(row.grossAmount);
                    } else if (TransactionTypes.REFUND.name().equalsIgnoreCase(row.transactionType)) {
                        grossRefundAmount = grossRefundAmount.add(row.grossAmount);
                    }
                } catch (Exception ex) {
                    quarantineRecord(importBatchId, record, ex.getMessage());
                    quarantinedRowCount++;
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to import internal transactions from " + sourcePath, ex);
        }

        updateImportBatchCounts(importBatchId, validRowCount, quarantinedRowCount);

        return new ImportResponse(
                importBatchId,
                SOURCE_TYPE,
                sourcePath.toString(),
                validRowCount,
                quarantinedRowCount,
                grossSalesAmount,
                grossRefundAmount
        );
    }

    private long createImportBatch(Path sourcePath) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO import_batch (dataset_name, source_type, source_path) VALUES (?, ?, ?)",
                    new String[]{"id"}
            );

            ps.setString(1, "default");
            ps.setString(2, SOURCE_TYPE);
            ps.setString(3, sourcePath.toString());
            return ps;
        }, keyHolder);

        // return result
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private InternalTransactionRow parseRow(CSVRecord record) {
        String internalTxnId = required(record, "internal_txn_id");
        String merchantId = required(record, "merchant_id");
        String merchantRef = required(record, "merchant_ref");
        String cardType = required(record, "card_type");
        String cardLast4 = required(record, "card_last4");
        BigDecimal grossAmount = parseBigDecimal(required(record, "gross_amount"), "gross_amount");
        String currency = required(record, "currency");
        String transactionType = required(record, "type");
        Instant capturedAt = parseInstant(required(record, "captured_at"), "captured_at");

        validateCurrency(currency);
        validateTransactionType(transactionType);
        validateCardLast4(cardLast4);

        return new InternalTransactionRow(
                internalTxnId,
                merchantId,
                merchantRef,
                cardType,
                cardLast4,
                grossAmount,
                currency,
                transactionType,
                capturedAt
        );
    }

    private String required(CSVRecord record, String columnName) {
        String value = record.get(columnName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required column value: " + columnName);
        }
        return value.trim();
    }

    private BigDecimal parseBigDecimal(String value, String columnName) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal value for " + columnName + ": " + value);
        }
    }

    private Instant parseInstant(String value, String columnName) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid timestamp value for " + columnName + ": " + value);
        }
    }

    private void validateCurrency(String currency) {
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
    }

    private void validateTransactionType(String transactionType) {
        if (!"SALE".equals(transactionType) && !"REFUND".equals(transactionType)) {
            throw new IllegalArgumentException("Unsupported transaction type: " + transactionType);
        }
    }

    private void validateCardLast4(String cardLast4) {
        if (!cardLast4.matches("\\d{4}")) {
            throw new IllegalArgumentException("card_last4 must contain exactly 4 digits: " + cardLast4);
        }
    }

    private void insertInternalTransaction(long importBatchId, InternalTransactionRow row) {
        jdbcTemplate.update("""
                insert into internal_transaction (
                    internal_txn_id,
                    import_batch_id,
                    merchant_id,
                    merchant_ref,
                    card_type,
                    card_last4,
                    gross_amount,
                    currency,
                    transaction_type,
                    captured_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.internalTxnId(),
                importBatchId,
                row.merchantId(),
                row.merchantRef(),
                row.cardType(),
                row.cardLast4(),
                row.grossAmount(),
                row.currency(),
                row.transactionType(),
                Timestamp.from(row.capturedAt())
        );
    }

    private void quarantineRecord(long importBatchId, CSVRecord record, String reason) {
        jdbcTemplate.update("""
                insert into quarantined_record (
                    import_batch_id,
                    source_type,
                    source_row_number,
                    source_record_key,
                    raw_payload,
                    reason
                )
                values (?, ?, ?, ?, ?, ?)
                """,
                importBatchId,
                SOURCE_TYPE,
                Math.toIntExact(record.getRecordNumber() + 1),
                safeRecordKey(record),
                record.toString(),
                reason
        );
    }

    private String safeRecordKey(CSVRecord record) {
        try {
            return record.get("internal_txn_id");
        } catch (Exception ex) {
            return null;
        }
    }

    private void updateImportBatchCounts(long importBatchId, int validRowCount, int quarantinedRowCount) {
        jdbcTemplate.update("""
                update import_batch
                set valid_row_count = ?, quarantined_row_count = ?
                where id = ?
                """,
                validRowCount,
                quarantinedRowCount,
                importBatchId
        );
    }

    private record InternalTransactionRow(
            String internalTxnId,
            String merchantId,
            String merchantRef,
            String cardType,
            String cardLast4,
            BigDecimal grossAmount,
            String currency,
            String transactionType,
            Instant capturedAt
    ) {
    }
}
