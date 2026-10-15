# AI assistance log

1. Prompted an AI coding assistant to plan a React JS + Java Spring Boot payment settlement reconciliation application based on the provided assignment README.
2. Used the assistant to scaffold the frontend dashboard, file-import workflow, API endpoints, fee calculation, reconciliation classifications, persistence, and setup documentation.
3. Engineering decisions to verify and explain during review: fee rounding uses Java `BigDecimal` with HALF_UP; refunds have no fee deductions; malformed input is quarantined during import; matching and discrepancy classification are kept in the backend service rather than React; H2 file persistence keeps local setup lightweight.
4. Known limitation retained explicitly: complete split-settlement grouping and fee apportionment need additional implementation and tests. The fixture-by-fixture results still need to be verified against the assignment's `test/EXPECTED.md` when the original data bundle is available.
