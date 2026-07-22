# AI / LLM Usage

## Tools used

Cursor (agent mode), used throughout: planning, implementation, tests, and docs. I directed the work and reviewed the decisions; I did not treat the agent as a black box.

## Key prompts (roughly in order)

1. **"No code. Go through the README of this project and let's make a plan to tackle it"** — plan first. That became [PLAN.md](PLAN.md), which I approved before any implementation.
2. **"Before we write any code, can you explain this exercise to me?"** — made it walk through the domain (gross vs net, fees, matching, break types, quarantine) so I could verify its understanding of the problem before letting it build anything.
3. Architecture check: UI imports both files, Java backend does the reconciliation. Also how to handle `PROMPTS.md` when most prompts are small and iterative (roll those up; quote the important ones).
4. Stack / setup choices while scaffolding (Java install size, Maven wrapper vs system Maven).
5. **"How about the meaningful commits along the way?"** — it had stacked work without committing. I stopped it and required layer-sized commits with tests.
6. Product follow-ups I asked for after the baseline worked: a dev-tools wipe that fully resets state (including run IDs), a source-data browser, match-rule badges on each row, and section navigation.
7. Hands-on testing in the browser after each feature; when something didn't behave (a button not wired up, the wipe not fully resetting), I reported exactly what I did and had it fix the root cause rather than patch symptoms.

Lots of small iterative prompts in between (fix this, style that, explain that error). Not listed one by one.

## Where it helped vs. where I steered it

- **Helped:** speed — scaffolding, entities, parsers, tests, CSS, wiring endpoints to the UI.
- **Helped:** proposing the golden test — an automated test that runs the full pipeline on `test/` and asserts every count and money total in `EXPECTED.md`, before trusting results on the big `data/` set. The engine reproduced the expected table on its first run.
- **Steered:** installs. It wanted system Maven; I asked about storage and we used the Maven wrapper instead.
- **Corrected:** wrong API test expectation (7 break items vs 8 — wide-window timing counts in the list). Engine was fine; the test was wrong.

## Decisions I made

- **Java/Spring Boot + React** — matches what the exercise says the team uses.
- **H2 file database** — results survive a restart with no Postgres setup for reviewers.
- **Commit as we go** — milestone commits during the build, not one dump at the end.
- **Ship the plan** — keep [PLAN.md](PLAN.md) in the repo so reviewers see what was approved before coding.
