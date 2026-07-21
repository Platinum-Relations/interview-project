package com.reconciliation.config;

import com.reconciliation.engine.ReconciliationEngine;
import com.reconciliation.engine.ReconciliationPolicy;
import com.reconciliation.fees.FeeCalculator;
import com.reconciliation.fees.FeeSchedule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Wires the pure domain engine into the Spring context. */
@Configuration
public class ReconciliationConfig {

    @Bean
    public FeeSchedule feeSchedule(@Value("${reconciliation.fee-schedule}") Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FeeSchedule.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load fee schedule from " + resource, e);
        }
    }

    @Bean
    public FeeCalculator feeCalculator(FeeSchedule feeSchedule) {
        return new FeeCalculator(feeSchedule);
    }

    @Bean
    public ReconciliationPolicy reconciliationPolicy() {
        return ReconciliationPolicy.standard();
    }

    @Bean
    public ReconciliationEngine reconciliationEngine(FeeCalculator feeCalculator, ReconciliationPolicy policy) {
        return new ReconciliationEngine(feeCalculator, policy);
    }
}
