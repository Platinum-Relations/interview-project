# AI / LLM Usage

> Delete this note and fill in the sections below **if** you used an AI assistant.
> If you didn't use one, replace this file's contents with "No AI assistant was used."
> Either way is completely fine - see the README.

## Tools used

Free Trial of the AI Agent packaged with Intellij IDEA ultimate (which I have my own license for).
Figured I might as well try it out while challenging myself - what could go wrong - instead of using
ChatGPT.  I have heard miraculous things about Claude Code, but haven't gotten to it yet. Was told 
(by someone I trust who uses it religiously) without the $100/mo subscription it's too limited, so wait.
## Key prompts

_The main prompts you used, roughly in order. Paste them; summarize the long ones._

1. Asked the assistant to help build the assignment incrementally rather than all at once; requested a strategy for tracking AI prompts in `PROMPTS.md`; asked for initial recommendations on Java project structure and local persistence options for a small settlement reconciliation application.
2. Clarified that the existing `PROMPTS.md` should be used, and that assistant-provided updates should be limited to the `Key prompts` section rather than overwriting the whole file.
3. Agreed with a backend-first implementation plan, with React frontend deferred until later; specified a strong preference for Gradle over Maven.
4. Accepted the proposed base Java package for now, but rejected Hibernate/ORM in favor of jOOQ or plain JDBC to keep direct control over SQL statements and demonstrate database/query design.
5. Approved Spring Boot with Gradle, H2 file persistence, Flyway migrations, Spring JDBC/JdbcClient, no ORM, and `decimal(19,4)` monetary columns; asked the assistant to generate the initial backend project skeleton.
6. Asked whether conversation/project continuity survived an IntelliJ IDEA restart, and whether the work needed to be re-prompted from scratch.
7. Verified the health endpoint and used that as a baseline that the Spring Boot application was running and reachable.
8. Reported a 500 error from the internal transaction populate endpoint caused by generated key handling in `createImportBatch`; asked the assistant to refactor the solution so repeated imports work when multiple `import_batch` records already exist.
9. Asked whether quarantined records in a single import run receive the same timestamp, and where the quarantine timestamp is set.
10. Asked whether quarantined records are inserted within the same transaction and where the transaction/quarantine timestamp logic lives.
11. Asked the assistant to continue updating `PROMPTS.md` with the prompts used during the project.
12. Investigated the source of data used to populate the `internal_transaction` table after seeing unexpectedly high inserted row counts; identified the default CSV path and discussed possible working-directory/import-history causes.
13. Asked to externalize the internal transaction import directory and filename into application configuration while preserving the current values.
14. Noted that the IDE beta feature for applying code changes is close to correct but still needs review and cleanup.
15. Asked for an integration test verifying that the configured internal transaction CSV record count matches the import result and the resulting `internal_transaction` table count.
16. Debugged test setup issues around JUnit 5, Gradle test execution, deprecated Commons CSV builder usage, and Spring constructor injection for test dependencies.
17. Confirmed the import count integration test passes and provides the intended guardrail against wrong-file or unexpected-extra-record import bugs.
18. Planned the next TDD phase around `test/EXPECTED.md`, using the known-good `test/` dataset as acceptance-test input for import counts, quarantine counts, raw money totals, and reconciliation outcome summaries.

## Where it helped vs. where you steered it


### Hopes and Dreams
Hoping it will give me some project boilerplate, maybe I'll give it some small
jobs like turning the math calculations into Java functions, etc., at least for starters.

### Reality

#green _put actual utility here_

## Decisions you made against its suggestion

| What Got Cut                              | Why                                                                                                                                                                                                                     | Additional Notes                                                                                                                                                |
|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Hibernate` or `JPA` (some "magic" `ORM`) | I <span style="color: red;">**loathe**</span> ORMs. Whether debugging for correctness and transaction scope, or performance tuning, I have never ever seen one *not* be an issue, especially as load and volume grow.   | Vastly prefer just `JDBC` and then `JOOQ` or even the old `Cayenne` since total control of statements and isolation/transaction levels is available and easy.   |
|                                           |                                                                                                                                                                                                                         |                                                                                                                                                                 |
|                                           |                                                                                                                                                                                                                         |                                                                                                                                                                 |
|                                           |                                                                                                                                                                                                                         |                                                                                                                                                                 |

