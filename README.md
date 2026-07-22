# Settlement Reconciliation

A full-stack reconciliation app: it ingests the internal ledger (CSV) and the processor's settlement file (JSON), matches the two sides, verifies the fee math against the published schedule, classifies every break, and presents an ops dashboard for working them.

The original exercise statement is preserved in [EXERCISE.md](EXERCISE.md).

## Stack

- **Backend:** Java 21, Spring Boot, Spring Data JPA, H2 (file mode). The reconciliation engine is a pure domain module with no Spring dependencies.
- **Frontend:** React + TypeScript (Vite).
- **Persistence:** H2 in file mode at `backend/.data/` - results survive a restart with zero database setup.

## Running it

Prerequisites: **JDK 21** and **Node 20+**. Maven is not required (the Maven wrapper downloads it).

If `java -version` doesn't report 21, install it first. On macOS with Homebrew:

```bash
brew install openjdk@21
# Homebrew installs this JDK keg-only; put it on your PATH:
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
# then open a new terminal
```

Terminal 1 - backend (port 8080):

```bash
cd backend
./mvnw spring-boot:run
```

Terminal 2 - frontend (port 5173, proxies `/api` to the backend):

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173, choose `data/internal_transactions.csv` and `data/processor_settlement.json`, and click **Import and reconcile**. Re-importing the same files is idempotent - you get the existing run back.

Tests (unit + golden dataset + API integration):

```bash
cd backend
./mvnw test
```

## How it works

```
CSV + JSON  →  Ingest (validate, quarantine)  →  Reconciliation engine  →  H2  →  REST API  →  React dashboard
                                  fee_schedule.json ↗
```

### Ingest

Each row is parsed into a typed domain record. Rows that fail structural validation - missing fields, non-numeric amounts, non-USD currency, unknown card type, a refund with a positive amount - are quarantined with every failure reason and excluded from reconciliation. They are visible in the UI but never counted as breaks.

### Matching (in order)

1. **By `merchant_ref` + sign.** A refund reuses its sale's reference, so the sign of the settled amount separates the sale row from the refund row.
2. **Fallback for blank refs:** candidate key is `merchant_id + card_type + card_last4`, and the settled amount is compared against the candidate's **fee-adjusted expected net** (not its gross), within tolerance. Candidates inside the normal settlement window are preferred; ties break on closest capture date.
3. **Multi-row resolution:** several rows attributed to one capture are a **duplicate settlement** if each row repeats the expected net, and a **split settlement** if the rows sum to it.
4. **Orphan refunds** are detected against the ledger itself (a refund whose reference matches no sale), independently of whether the refund settled - a match-first pipeline would walk right past them.

### Fee verification

For each matched sale: `interchange = round(gross x pct + flat)`, `processor = round(gross x 0.3% + 0.05)`, each rounded to the cent (half-up) **before** deriving `expected_net = gross - interchange - processor`. Refunds settle at full negative gross with no fees.

A wrong settlement is classified by asking whether it is internally consistent with the fees the processor itself reported:

- **Amount mismatch** - `settled != gross - reported_fees`: the principal is wrong.
- **Fee discrepancy** - internally consistent, but the reported fees deviate from the schedule. A check that only compares settled against gross-minus-reported-fees passes these; comparing against `fee_schedule.json` catches them.

## The open questions - calls made

| Question | Decision | Why |
| --- | --- | --- |
| Amount tolerance | +/- $0.01 absolute | Reconstructing an expected net can differ by a cent from per-fee rounding; a cent absorbs that noise, anything more is a real break. Applied everywhere amounts are compared. |
| Date window | Match, but flag as **wide-window timing** | The money did arrive, so calling it unmatched would overstate exposure; hiding it would understate operational latency. It gets its own category, consistent with `test/EXPECTED.md`. |
| Split settlements | Handled | Rows under one reference that sum to the expected net (vs. each repeating it) are classified as splits, which keeps them out of the duplicate count. |

## Verification

The engine is validated by a **golden test** (`GoldenDatasetTest`) that runs the full pipeline against `test/` and asserts every count and money check in `test/EXPECTED.md` exactly - 8 clean matches, 1 of every break category, 5 quarantined rows, and all four money totals. Unit tests cover the fee math per card type, each matching pass, duplicate-vs-split disambiguation, and each classification rule; an API integration test covers upload → persist → report end-to-end.

Against the full `data/` set: 543 valid ledger rows and 546 valid settlement rows reconcile into 510 clean matches, 38 breaks across all seven categories plus 2 splits and 3 wide-window flags, with 5 rows quarantined.

## Design notes

- **Engine isolation.** `ReconciliationEngine`, `FeeCalculator`, and the parsers have no Spring, HTTP, or persistence awareness. Spring wires them in `ReconciliationConfig`; the service layer only orchestrates.
- **Money is `BigDecimal`** end to end. Amounts arrive as strings in both files and are never routed through floating point.
- **Idempotent imports.** A SHA-256 over both file contents identifies a run; re-importing identical files returns the existing run instead of duplicating it. Import history is kept per run.
- **Judgment calls are code.** Tolerance and the settlement window live in `ReconciliationPolicy`, injected into the engine, not scattered as magic numbers.

## What I'd do with more time

- Pagination and search on the breaks list (fine at ~550 rows; not at 500k).
- Assignment/workflow state on breaks (acknowledged, investigating, resolved) so it's a real ops queue.
- Multi-currency support - currently anything non-USD is quarantined by design.
- Flyway migrations instead of `ddl-auto=update` before this touched a shared environment.
- A cross-reference duplicate check: a duplicate settlement whose second row has a *blank* ref currently lands in unmatched-settlement rather than the duplicate bucket; the fallback pass could consider already-matched transactions too.
