# Settlewise — Payment Settlement Reconciliation

A starter full-stack implementation for the payment settlement reconciliation take-home exercise. It includes a React/Vite operations UI and a Java 21 / Spring Boot API. The backend stores active imported datasets and reconciliation run snapshots in a file-backed H2 database so data survives application restarts.

## Included

- CSV internal transaction import and JSON processor settlement import.
- Per-row validation and quarantine reporting.
- Fee calculation using the assignment's published card schedule and `BigDecimal` HALF_UP rounding.
- Refunds with no fee deductions and orphan-refund checks.
- Reference matching plus fallback matching for blank references using merchant/card/currency/sign and expected net amount.
- Break categories for unmatched records, amount mismatch, fee discrepancy, duplicate settlement, orphan refund, and ambiguous matching.
- Dashboard, discrepancy investigation, merchant rollup, and import/run snapshot UI.
- REST endpoints under `/api/v1`.

## Requirements

- Java 21+
- Maven 3.9+ (or an IDE with Maven support)
- Node.js 20+ and npm

No external database is required. H2 stores its database under `backend/data/` by default.

## Run on Windows

1. Install Java 21, Maven 3.9+, and Node.js 20+.
2. Double-click `start-backend.bat` (or run it from Command Prompt). Keep that terminal open.
3. Double-click `start-frontend.bat` in a second terminal. The first run installs npm packages.
4. Open `http://localhost:5173`.

## Run the backend

From a terminal:

```bash
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080`. Health check: `http://localhost:8080/api/v1/health`.

To run backend tests:

```bash
cd backend
mvn test
```

## Run the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The frontend uses `http://localhost:8080/api/v1` by default. To use a different API URL, create `frontend/.env` with:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## Use the application

1. Start the backend, then the frontend.
2. Open **Import data**.
3. Upload `internal_transactions.csv` and `processor_settlement.json` from the assignment's `data/` directory (or use the smaller `test/` fixtures).
4. Review accepted and quarantined row counts.
5. Select **Run reconciliation**.
6. Review the Overview, Break investigation, Merchants, and Run history pages.

Each new import replaces the active source dataset. Existing reconciliation runs are retained as JSON snapshots in H2.

## API endpoints

- `GET /api/v1/health`
- `GET /api/v1/dashboard`
- `GET /api/v1/imports`
- `POST /api/v1/imports/internal-transactions` (`multipart/form-data`, field `file`)
- `POST /api/v1/imports/settlements` (`multipart/form-data`, field `file`)
- `POST /api/v1/reconciliations`
- `GET /api/v1/reconciliations/latest`
- `GET /api/v1/reconciliations/{runId}`
- `GET /api/v1/reconciliations/{runId}/breaks?category=FEE_DISCREPANCY&page=0&size=25`
- `GET /api/v1/reconciliations/{runId}/merchants`

## Reconciliation choices and current limitations

- Fee amounts are rounded independently to two decimal places using HALF_UP before deriving expected sale settlement.
- Refund expected settlement is the full negative gross; expected refund fees are zero.
- Matching prefers merchant reference + merchant ID + amount sign/type. Fallback matching is restricted to blank-reference settlement rows and checks merchant, card last four, card type, currency, sign, and fee-adjusted expected net.
- Amount tolerance is two cents. Settlement date is currently retained for audit but not used as a hard matching cutoff, which permits the exercise's wide-window timing cases.
- Multiple indistinguishable candidates are surfaced as ambiguous. Basic repeated-settlement detection is included. Full multi-row split settlement aggregation and fee apportionment are not yet implemented; ambiguous cases should be reviewed rather than assumed clean.
- This is a take-home starter, not a production payment system. Before production, add authentication/authorization, formal audit events, configurable fee schedule ingestion, richer import idempotency keys, robust duplicate/split grouping, pagination for dashboard aggregation, and a full fixture-by-fixture acceptance suite against `test/EXPECTED.md`.

## Assignment data

The original assignment dataset was not bundled in this generated archive. Copy the provided `data/` and `test/` directories into the repository root if you want to keep the fixtures alongside the code. The app accepts those files through the upload UI.
