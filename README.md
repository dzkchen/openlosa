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
