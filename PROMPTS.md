# AI / LLM Usage

## Tools used

Free Trial of the AI Agent packaged with Intellij IDEA ultimate (which I have my own license for).
Figured I might as well try it out while challenging myself - what could go wrong - instead of using
`ChatGPT` or the more familiar `GitHub Copilot`.  I have heard miraculous things about Claude Code, but haven't gotten to it yet. Was told 
(by someone I trust who uses it religiously) without the $100/mo subscription it's too limited, so wait.  

Halfway through the exercise I ran out of free credits and bought the PRO version from Intellij. It's fairly capable, but 
the monthly credits were barely enough to get a few messages through.

I *did not* create a UI, both because I am not a designer and because it wouldn't end well if i couldn't police the AI assistant
and just let it run wild doing whatever it wanted without oversight.

## Key prompts

1. Asked the assistant to help build the assignment incrementally rather than all at once
   2. Requested a strategy for tracking AI prompts in `PROMPTS.md`
   3. Asked for initial recommendations on Java project structure and local persistence options
2. Clarified that 
   3. Existing `PROMPTS.md` should be used
   4. Assistant-provided updates should be limited to the `Key prompts` section
3. Agreed with a backend-first implementation plan
   4. React frontend deferred until later (_NOTE_: never done) 
   5. Specified a strong preference for `Gradle` over `Maven`.
4. Accepted the proposed base Java package for now, but rejected Hibernate/ORM in favor of jOOQ or plain JDBC
   5. To keep direct control over SQL statements and demonstrate database/query design.
5. Approved 
   6. Spring Boot with Gradle
   7. H2 file persistence
   8. Flyway migrations
   9. Spring JDBC/JdbcClient
   10. No ORM
   11. `decimal(19,4)` monetary columns; 
6. Asked the assistant to generate the initial backend project skeleton.
7. Verified the health endpoint and used that as a baseline that the Spring Boot application was running and reachable.
8. Reported a 500 error from the internal transaction populate endpoint caused by generated key handling in `createImportBatch`
   9. Asked the assistant to refactor the solution so repeated imports work when multiple `import_batch` records already exist.
9. Asked whether quarantined records in a single import run receive the same timestamp, and where the quarantine timestamp is set.
   10. (EK note: I would prefer a timestamp is pushed into the table for ALL records processed in a single transaction, rather than each record having a high resolution timestamp that differs from its neighbors)
10. Asked whether quarantined records are inserted within the same transaction and where the transaction/quarantine timestamp logic lives.
11. Asked the assistant to continue updating `PROMPTS.md` with the prompts used during the project.
12. Investigated the source of data used to populate the `internal_transaction` table after seeing unexpectedly high inserted row counts; identified the default CSV path and discussed possible working-directory/import-history causes.
13. Asked to externalize the internal transaction import directory and filename into application configuration while preserving the current values.
14. Noted that the IDE beta feature for applying code changes is close to correct but still needs review and cleanup.
15. Asked for an integration test verifying that the configured internal transaction CSV record count matches the import result and the resulting `internal_transaction` table count.
16. Debugged test setup issues around JUnit 5, Gradle test execution, deprecated Commons CSV builder usage, and Spring constructor injection for test dependencies.
17. Confirmed the import count integration test passes and provides the intended guardrail against wrong-file or unexpected-extra-record import bugs.
18. Planned the next TDD phase around `test/EXPECTED.md`, using the known-good `test/` dataset as acceptance-test input for import counts, quarantine counts, raw money totals, and reconciliation outcome summaries.
19. Resumed work on `SettlementReconciliationApplicationTests`, reviewed the stubbed settlement test, and asked the assistant to determine what production settlement import support was missing rather than mocking a nonexistent service.
20. Provided scratch notes for settlement import assertions: 19 input rows, 17 valid rows, 2 quarantined rows, total settled amount `5161.00`, and total fees `151.74`; asked the assistant to confirm those were covered.
21. Added a real processor settlement JSON import path with validation, quarantine handling, settlement sign derivation, row-count assertions, settlement total assertions, and a REST import endpoint.
22. Asked to move on to applying fees and recording them; implemented fee schedule loading from `fee_schedule.json`, expected interchange/processor fee calculation, expected net settlement calculation, and zero-fee refund handling.
23. Asked that `SALE` and `REFUND` string literals be replaced with `TransactionTypes` enum usage where transaction types are needed.
24. Reviewed user edits to `InternalTransactionImportService`, especially debugger-friendly result variables and expanded method comments; validated behavior with the test suite and called out portability/case-sensitivity concerns.
25. Asked what fee work remained; confirmed fee math, fee discrepancy detection, refund fee handling, and fee recording were in place, with suggested polish items left for later.
26. Asked to code classifications and breakdowns so evaluator-expected results are calculated and plainly reported; implemented a full reconciliation run that records `reconciliation_match`, writes actionable `reconciliation_break` rows, and returns summary counts and break details.
27. Added acceptance coverage matching `test/EXPECTED.md`: clean matches, unmatched internal, unmatched settlement, amount mismatch, fee discrepancy, duplicate settlement, orphan refund, split settlement, wide-window timing, and malformed quarantined counts.
28. Asked for curl command lines to exercise the app; documented the import and reconciliation POST sequence and corrected the health endpoint to `/api/health`.
29. Asked how settlement window timing and amount tolerance are handled; confirmed the T+1..3 window classification and the `0.01` absolute amount tolerance used for matching and comparison.

## Where it helped vs. where you steered it


### Hopes and Dreams
Hoping it will give me some project boilerplate, maybe I'll give it some small
jobs like turning the math calculations into Java functions, etc., at least for starters.

### Reality

Used it quite a bit. Where it helped, it did. Where it vexxed me was initially IDEA chose a `beta` version of the AI agent,
and it trashed my project a number of times when inserting files or applying code changes. This took time to undo, because the agent
wouldn't undo properly, and things degenerated.  The paid PRO version is much more stable and reliable.

## Decisions you made against its suggestion

| What Got Cut                              | Why                                                                                                                                                                                                                     | Additional Notes                                                                                                                                                |
|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Hibernate` or `JPA` (some "magic" `ORM`) | I <span style="color: red;">**loathe**</span> ORMs. Whether debugging for correctness and transaction scope, or performance tuning, I have never ever seen one *not* be an issue, especially as load and volume grow.   | Vastly prefer just `JDBC` and then `JOOQ` or even the old `Cayenne` since total control of statements and isolation/transaction levels is available and easy.   |
|                                           |                                                                                                                                                                                                                         |                                                                                                                                                                 |
|                                           |                                                                                                                                                                                                                         |                                                                                                                                                                 |
|                                           |                                                                                                                                                                                                                         |                                                                                                                                                                 |

The above decisions were made because of "sins of commission", but there are also "sins of omission". For example, although i wanted
to see if i could get the AI to generate javadocs and code comments in my style, that didn't work out so well, which is why I pointed out
which file I actually commented myself in another file.