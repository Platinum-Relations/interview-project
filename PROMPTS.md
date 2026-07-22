# AI / LLM Usage

## Tools used

Cursor (agent mode), used heavily throughout: planning, implementation, tests, and this writeup. All architectural decisions and judgment calls below were reviewed and directed by me.

## Key prompts

1. **"Go through the README of this project and let's make a plan to tackle it"** - before any code, the agent read the exercise, the test fixtures, and `EXPECTED.md`, and produced the implementation plan (ingest → fee engine → matching passes → classification → persistence/API → dashboard, validated by a golden test against `test/EXPECTED.md`).
2. **"Explain this exercise to me"** - had it walk through the domain (gross vs. net, why matching depends on the fee math, amount-mismatch vs. fee-discrepancy, duplicates vs. splits, orphan refunds) before building, so I could sanity-check its understanding and mine.
3. Stack and persistence decisions (see below), then: **build it milestone by milestone** - scaffold, typed ingest with quarantine, fee engine, reconciliation engine, persistence + REST API, React dashboard. Each milestone landed with its tests in a meaningful commit.
4. **"How about the meaningful commits along the way?"** - course-correction mid-build; the agent had produced several layers without committing. Result: layer-sized commits, each compiling with its tests included.
5. End-to-end verification: import the full `data/` set through the UI in a browser, verify the drill-down, kill and restart the backend to prove persistence.

Plus routine iterative prompts (fixing a Spring Boot 4 package move, adjusting a test expectation, styling) not individually listed.

## Where it helped vs. where you steered it

- **Helped most:** the golden test discipline. The agent proposed asserting every number in `test/EXPECTED.md` before touching `data/`, and the engine reproduced the expected table on its first run.
- **Helped:** boilerplate speed - entities, DTOs, parser scaffolding, CSS.
- **Steered:** tool installation. It reached for `brew install openjdk@21 maven`; I stopped it, asked for the storage footprint, and we cut Maven for the Maven wrapper (~300 MB saved, and reviewers don't need Maven installed either).
- **Steered:** it initially answered a side question of mine with actions instead of an answer (whether commits made in a clone look different from commits made in a fork - they don't; commits carry no remote information). I made it stop and explain before continuing.
- **Corrected:** its API integration test initially expected 7 break items for the test set; the correct number is 8 (wide-window timing is listed in the drill-down as well). The engine was right, the test expectation was wrong.

## Decisions you made against its suggestion

- None fundamental to the reconciliation logic; the significant decisions were choices it presented rather than fought for:
  - **Java/Spring Boot + React over full TypeScript** - matches the team's stack per the exercise, even though a TS stack would have been faster in my environment.
  - **Embedded file-mode H2 over Postgres/Supabase** - zero-setup persistence for reviewers running from the README.
  - **Commit cadence** - I insisted on milestone commits during the build rather than batching at the end.
