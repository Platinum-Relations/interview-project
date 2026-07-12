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
import java.util.Optional;

/**
 * Service for importing internal transaction data from a CSV file.
 */
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

    /**
     * Populate internal transactions from a CSV file.
     * @implNote This method is not transactional, so it is expected that the caller will manage transactions.
     * @param sourcePath The path to the CSV file to import.
     * @return The import response.
     */
    @Transactional
    public ImportResponse populateFromCsv(Path sourcePath) {
        // init response
        ImportResponse result;

        // get batch ID and initialize counts, amounts
        long importBatchId = createImportBatch(sourcePath);

        int validRowCount = 0;
        int quarantinedRowCount = 0;
        BigDecimal grossSalesAmount = new BigDecimal("0.00");
        BigDecimal grossRefundAmount = new BigDecimal("0.00");

        // Development-friendly behavior: make the endpoint repeatable.
        jdbcTemplate.update("delete from reconciliation_break");
        jdbcTemplate.update("delete from reconciliation_match");
        jdbcTemplate.update("delete from reconciliation_run");
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

            // populate counts and amounts
            for (CSVRecord record : records) {
                try {
                    // get row and attempt insert, if successful, increment valid row count
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

        // populate response (also gives you an easy-to-use breakpoint and view in debugger instead of create
        // and return a new object at same time)
        result = new ImportResponse(
                importBatchId,
                SOURCE_TYPE,
                sourcePath.toString(),
                validRowCount,
                quarantinedRowCount,
                grossSalesAmount,
                grossRefundAmount
        );

        // done
        return result;
    }

    /** Create a new import batch record in the database.
     * @param sourcePath The path to the CSV file that was imported.
     * @return The ID of the newly imported batch.
     * @see JdbcTemplate
     */
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

    /** Parse a single row of internal transaction data from a CSV record.
     * @param record The CSV record.
     * @return The parsed internal transaction row.
     * @throws IllegalArgumentException A required field is missing or invalid.
     * @see CSVRecord#get(String)
     */
    private InternalTransactionRow parseRow(CSVRecord record) {
        // init result
        InternalTransactionRow result;

        // parse required fields
        String internalTxnId = required(record, "internal_txn_id");
        String merchantId = required(record, "merchant_id");
        String merchantRef = required(record, "merchant_ref");
        String cardType = required(record, "card_type");
        String cardLast4 = required(record, "card_last4");
        BigDecimal grossAmount = parseBigDecimal(required(record, "gross_amount"), "gross_amount");
        String currency = required(record, "currency");
        String transactionType = required(record, "type");
        Instant capturedAt = parseInstant(required(record, "captured_at"), "captured_at");

        // apply validations
        validateCurrency(currency);
        validateTransactionType(transactionType);
        validateCardLast4(cardLast4);

        // create result
        result = new InternalTransactionRow(
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

        // done
        return result;
    }

    /** Parse a string from a CSV record.
     * @param record The CSV record.
     * @param columnName The name of the column from which the value was extracted.
     * @return The parsed string.
     * @throws IllegalArgumentException if passed value is {@code null}, empty, or contains only Unicode whitespace codepoints
     * @see CSVRecord#get(String)
     * @see String#isBlank()
     */
    private String required(CSVRecord record, String columnName) {
        String value = record.get(columnName);

        // if null, empty, or only whitespace
        if (Optional.ofNullable(value).orElse("").isBlank()) {
            throw new IllegalArgumentException("Missing required column value: " + columnName);
        }

        // done
        return value.trim();
    }

    /** Parse a BigDecimal from a string.
     * @param value The string value to parse.
     * @param columnName The name of the column from which the value was extracted.
     * @return The parsed BigDecimal.
     * @throws IllegalArgumentException if the value cannot be parsed as a BigDecimal.
     * @see BigDecimal#BigDecimal(String)
     */
    private BigDecimal parseBigDecimal(String value, String columnName) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal value for " + columnName + ": " + value);
        }
    }

    /**
     * Parse an Instant from a string.
     * @param value The string value to parse.
     * @param columnName The name of the column from which the value was extracted.
     * @return The parsed Instant.
     * @throws IllegalArgumentException if the value cannot be parsed as an Instant.
     * @see Instant#parse(CharSequence)
     */
    private Instant parseInstant(String value, String columnName) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid timestamp value for " + columnName + ": " + value);
        }
    }

    /**
     * Validate that the currency is USD, which is the only supported currency as of this writing.
     * @implNote This method requires passed currency to be in uppercase to match the ISO currency trigraph.
     * @since 07/2026
     * @param currency The ISO currency trigraph to validate.
     * @throws IllegalArgumentException if the currency is not USD.
     */
    private void validateCurrency(String currency) {
        // FUTURE - need db table or enum of allowed currencies, usually in db on per-merchant basis, and check against Currency object trigraph
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
    }

    /**
     * Validate that the transaction type is a known, valid type in the TransactionTypes enum.
     * @param transactionType The transaction type to validate.
     * @see TransactionTypes
     * @throws IllegalArgumentException if the transaction type is not a known, valid type.
     */
    private void validateTransactionType(String transactionType) {
        // just check against members of the TransactionTypes enum
        try {
            TransactionTypes.valueOf(transactionType);
        } catch (IllegalArgumentException e) {
            // change message to be more helpful to the user
            throw new IllegalArgumentException("Unsupported transaction type: " + transactionType);
        }
    }

    /**
     * Validate that the PANs last4 is exactly 4 digits in length.
     * @param cardLast4
     * @throws IllegalArgumentException if the card last 4 digits are not exactly 4 (arabic) digits.
     */
    private void validateCardLast4(String cardLast4) {
        if (!cardLast4.matches("\\d{4}")) {
            throw new IllegalArgumentException("card_last4 must contain exactly 4 digits: " + cardLast4);
        }
    }

    /**
     * Insert a row of internal transaction data into the database.
     * @param importBatchId The ID of the import batch.
     * @param row The row of data to insert.
     * @see JdbcTemplate
     */
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

    /**
     * Quarantine a record that failed to import.
     * @param importBatchId Batch ID of the import.
     * @param record Record that failed to import.
     * @param reason Reason for quarantining.
     * @see JdbcTemplate
     * @see CSVRecord
     */
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

    /**
     * Update the counts of valid and quarantined rows in the import batch.
     * @param importBatchId The ID of the import batch.
     * @param validRowCount The number of valid rows.
     * @param quarantinedRowCount The number of quarantined rows.
     * @see JdbcTemplate
     */
    private void updateImportBatchCounts(long importBatchId, int validRowCount, int quarantinedRowCount) {
        // OMG
        // yes I know ID is unique (thankfully) and the SQL engine should thus infer there is only 1 row at most
        // to change, but it's a good habit to limit the number of rows affected by an update, not to mention
        // single row lookups that often are doing full table or full index scans.
        jdbcTemplate.update("""
                update import_batch
                set valid_row_count = ?, quarantined_row_count = ?
                where id = ?
                LIMIT 1
                """,
                validRowCount,
                quarantinedRowCount,
                importBatchId
        );
    }

    /**
     * Represents a row of internal transaction data.
     * @param internalTxnId Tran ID
     * @param merchantId Merchant ID
     * @param merchantRef Merchant Reference
     * @param cardType Card Type
     * @param cardLast4 Last 4 digits of card number
     * @param grossAmount Gross amount of transaction
     * @param currency Currency of transaction
     * @param transactionType Type of transaction
     * @param capturedAt Timestamp of transaction capture
     * @see TransactionTypes
     */
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
