# OpenLOSA

OpenLOSA is a personal internship tool

## Stack

- Backend: Java 21, Spring Boot 3, Maven, Flyway, MySQL 8
- Frontend: Vite, React, TypeScript, Tailwind CSS
- Repository layout: `backend/`, `frontend/`, `engine/`

## Requirements

- Java 21
- Maven 3.9+
- Node.js 22+
- Docker Desktop or another Docker-compatible runtime

## Dev Setup

Start MySQL:

```sh
docker compose up -d
```

Wait for the `mysql` service to report `healthy` before starting the backend.
On a first run, MySQL initialization can take a couple of minutes.

```sh
docker compose ps
```

Run the backend API:

```sh
cd backend
mvn spring-boot:run
```

Run the frontend dev server in another terminal:

```sh
cd frontend
npm install
npm run dev
```

Open the app at `http://127.0.0.1:5173`. The Vite dev server proxies `/api` to
the backend at `http://127.0.0.1:8080`.

Check the backend health endpoint:

```sh
curl http://127.0.0.1:8080/api/v1/health
```

Expected response:

```json
{"status":"OK"}
```

## Database

The default local database settings match `docker-compose.yml`:

- URL: `jdbc:mysql://127.0.0.1:3306/openlosa`
- User: `openlosa`
- Password: `openlosa`

Override them with environment variables when needed:

```sh
OPENLOSA_DB_URL=jdbc:mysql://127.0.0.1:3306/openlosa \
OPENLOSA_DB_USER=openlosa \
OPENLOSA_DB_PASSWORD=openlosa \
mvn spring-boot:run
```

Flyway migrations live in `backend/src/main/resources/db/migration`. Once a
migration has been applied to a shared or persistent database, add a new
migration instead of editing the old one.

## Application CSV Import

Use the Applications page's **Import CSV** action for the one-time spreadsheet
migration. The importer uses a fixed template and does not support column
mapping, so the header row must be exactly:

```csv
companyName,companyWebsite,companyNotes,roleTitle,postingUrl,location,status,appliedAt,source,salaryText,notes,favorite,tags
```

Example:

```csv
companyName,companyWebsite,companyNotes,roleTitle,postingUrl,location,status,appliedAt,source,salaryText,notes,favorite,tags
OpenAI,https://openai.com,,Software Engineer Intern,https://openai.com/jobs/swe,San Francisco,APPLIED,2026-01-15,MANUAL,$60/hr,Imported from spreadsheet,true,summer-2027;priority
Anthropic,https://anthropic.com,,ML Systems Intern,,Remote,,,,,Needs follow-up,,priority
```

Required columns: `companyName`, `roleTitle`.

Optional columns can be blank. `status` defaults to `SAVED`, `source` defaults
to `MANUAL`, and `favorite` defaults to false. Use ISO dates (`YYYY-MM-DD`) for
`appliedAt`; leave it blank for rows whose status is `SAVED`. Status values are
`SAVED`, `APPLIED`, `ONLINE_ASSESSMENT`, `PHONE_SCREEN`, `INTERVIEW`, `OFFER`,
`REJECTED`, `WITHDRAWN`, `GHOSTED`. Source values are `MANUAL`, `FEED`,
`PROSPECT`. Tags are semicolon-separated names and are created if they do not
already exist.

Fields containing commas, quotes, or line breaks must use standard CSV quoting:
wrap the field in double quotes and write literal quotes as two double quotes.
For example, use `"Imported from spreadsheet, needs review"` for a note with a
comma.

## Feed Engine

The optional feed engine runs the pinned upstream intern-engine immediately and
then every four hours. Start it with:

```sh
docker compose --profile engine up -d --build engine
```

OpenLOSA's `engine/config/config.json` is bind-mounted read-only over the
upstream `data/config.json`. The engine's complete data directory stays in the
`engine_data` named volume, preserving both upstream seed files and generated
state. After each successful cycle, `jobs.json` and `health.json` are copied
atomically to the gitignored host directory `engine/export/`, where the native
backend can read them.

Tune these fields in `engine/config/config.json`:

- `cycles`: exact cycle labels to retain and their output order, such as
  `["Summer 2027", "Fall 2026"]`.
- `regions`: `["US"]` for United States roles or `["Global"]` to disable
  location filtering.
- `role_scope`: `"tech"` for technical roles or `"all"` for all internships.
- `max_age_days`: discard older postings; `0` disables the age limit.
- `max_per_company`: cap roles per company and cycle; `0` disables the cap.
- `allowlist_only`: when `true`, retain only upstream priority-listed
  companies.
- `include_international`: when `true`, retain non-US roles in an additional
  international section.
- `section_limits`: cap output rows by cycle label. Keep its keys aligned with
  `cycles`; remove a key to leave that cycle uncapped.

These filtering defaults mirror the pinned upstream revision. When changing
`cycles`, also update matching `section_limits` keys.

Restart the engine to apply a config change immediately:

```sh
docker compose --profile engine restart engine
```

The cycle interval is separate from job filtering. Override the default
14,400 seconds (four hours) when starting the service:

```sh
OPENLOSA_ENGINE_INTERVAL_SECONDS=7200 \
docker compose --profile engine up -d engine
```

The backend ingests `engine/export/jobs.json` hourly after an initial 10-second
delay. Override the input path or schedule when needed:

```sh
OPENLOSA_ENGINE_JOBS_FILE=/absolute/path/to/jobs.json \
OPENLOSA_FEED_INGEST_INTERVAL=PT30M \
mvn spring-boot:run
```

Missing or malformed files are recorded without applying a closing strike. An
unchanged file is skipped only while the set of open postings already matches
the feed; if any open posting is absent from the feed, the ingest still runs so
the absence lands a strike, even when the engine re-emits a byte-identical
`jobs.json`. A posting the feed explicitly marks `is_open: false` closes on the
next ingest; one that merely disappears closes after it is absent from two
successive successful ingests. Reappearing postings reopen automatically.

If the engine dies, OpenLOSA keeps working fully — the feed simply goes stale.
`GET /api/v1/feed/health` reports the last ingest run, the last time engine data
was applied (`lastSuccessAt`), the open-job count, and whether the feed is
`stale`. The feed is considered fresh while either the last applied change or the
last confirmed-unchanged snapshot (a skip that still fingerprinted the file) is
within the freshness window; a byte-identical `jobs.json` re-emitted for days
still reads as fresh, while missing-file or lock-contention skips do not. The
window defaults to 24 hours (the engine's four-hour cycle plus the hourly ingest
with generous headroom) and is configurable:

```sh
OPENLOSA_FEED_STALE_AFTER_HOURS=24 \
mvn spring-boot:run
```

The feed page shows a non-blocking warning banner when the feed has never
ingested, has gone stale, or its most recent run failed; a healthy feed shows a
subtle "updated X ago" note instead.

## Verification

Run the same checks as CI:

```sh
cd backend
mvn verify
```

```sh
cd frontend
npm ci
npx tsc --noEmit
npm run build
```

`mvn verify` includes a Testcontainers-backed MySQL smoke test for the baseline
Flyway schema, so Docker must be running.

## License

OpenLOSA is available under the MIT License. See `LICENSE`.
