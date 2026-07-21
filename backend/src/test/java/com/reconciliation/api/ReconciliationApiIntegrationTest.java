package com.reconciliation.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API test against the test/ dataset: upload both files, then read
 * back the summary, breaks, merchant rollup, and quarantine. Uses an in-memory
 * H2 so runs don't pollute the app's file database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:apitest;DB_CLOSE_DELAY=-1"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReconciliationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockMultipartFile internalFile() throws Exception {
        return new MockMultipartFile("internal", "internal_transactions.csv", "text/csv",
                Files.readAllBytes(Path.of("../test/internal_transactions.csv")));
    }

    private MockMultipartFile settlementFile() throws Exception {
        return new MockMultipartFile("settlement", "processor_settlement.json", "application/json",
                Files.readAllBytes(Path.of("../test/processor_settlement.json")));
    }

    @Test
    @Order(1)
    void importCreatesARun() throws Exception {
        mockMvc.perform(multipart("/api/imports").file(internalFile()).file(settlementFile()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").isNumber())
                .andExpect(jsonPath("$.alreadyImported").value(false));
    }

    @Test
    @Order(2)
    void reimportingIdenticalFilesIsIdempotent() throws Exception {
        mockMvc.perform(multipart("/api/imports").file(internalFile()).file(settlementFile()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyImported").value(true));
    }

    @Test
    @Order(3)
    void latestSummaryReportsTheExpectedTable() throws Exception {
        mockMvc.perform(get("/api/runs/latest/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validInternalCount").value(15))
                .andExpect(jsonPath("$.validSettlementCount").value(17))
                .andExpect(jsonPath("$.quarantinedCount").value(5))
                .andExpect(jsonPath("$.actualSettled").value(5161.00))
                .andExpect(jsonPath("$.totalFeesReported").value(151.74))
                .andExpect(jsonPath("$.categories[?(@.classification=='CLEAN_MATCH')].count").value(8))
                .andExpect(jsonPath("$.categories[?(@.classification=='DUPLICATE_SETTLEMENT')].count").value(1))
                .andExpect(jsonPath("$.categories[?(@.classification=='SPLIT_SETTLEMENT')].count").value(1));
    }

    @Test
    @Order(4)
    void breaksEndpointFiltersALLCategoriesAndExposesBothSides() throws Exception {
        // 1 each: unmatched-internal, unmatched-settlement, amount, fee, duplicate,
        // orphan refund, split, wide-window = 8 non-clean items.
        mockMvc.perform(get("/api/runs/1/breaks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        mockMvc.perform(get("/api/runs/1/breaks").param("category", "DUPLICATE_SETTLEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].settlementRows", hasSize(2)))
                .andExpect(jsonPath("$[0].internalTxnId").isNotEmpty())
                .andExpect(jsonPath("$[0].reason").isNotEmpty());
    }

    @Test
    @Order(5)
    void merchantRollupAndQuarantineAreExposed() throws Exception {
        mockMvc.perform(get("/api/runs/1/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        mockMvc.perform(get("/api/runs/1/quarantine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    @Order(6)
    void unknownRunReturns404() throws Exception {
        mockMvc.perform(get("/api/runs/9999/summary"))
                .andExpect(status().isNotFound());
    }
}
