# FindIT - Smart Campus Lost and Found Management System

FindIT is a campus lost and found service: students and staff report **lost** and **found**
items, the system suggests possible matches, claimants submit private evidence and a campus
administrator verifies the claim and records the handover.

> **A match score is a suggestion - never proof of ownership.**
> Every claim is reviewed by an administrator. This is a product rule of the system, not a
> setting.

The project is a Java / Spring Boot REST API with an HTML / CSS / JavaScript + Bootstrap 5
front end. It runs locally **without any Azure credentials** and is configured for Azure SQL
Database, Azure Blob Storage and Azure App Service through environment variables only.

---

## Table of contents

1. [Features](#features)
2. [Technology stack](#technology-stack)
3. [Prerequisites](#prerequisites)
4. [Quick start (local)](#quick-start-local)
5. [Running the tests](#running-the-tests)
6. [Configuration](#configuration)
7. [Demo walkthrough](#demo-walkthrough)
8. [Project layout](#project-layout)
9. [API overview](#api-overview)
10. [Matching rules](#matching-rules)
11. [Azure deployment](#azure-deployment)
12. [Security notes](#security-notes)
13. [Troubleshooting](#troubleshooting)
14. [Further documentation](#further-documentation)

---

## Features

| Area | What is implemented |
| --- | --- |
| Authentication | Registration, sign in, sign out, current user, BCrypt password hashing, server side session with a CSRF token, rate limited sign in |
| Roles | `USER` and `ADMIN`, enforced on every backend route and in the services |
| Reports | Create, edit (own + open only), close, view, list, search and filter lost and found reports |
| Reference data | Item categories and campus locations (seeded through Flyway) |
| Search | Keyword, type, category, location, date range, status, sorting, pagination, stable ordering, indexes on the filtered columns |
| Images | Private image storage behind an interface (local disk or Azure Blob), MIME + magic byte + size validation, server generated file names, images served only through an authorised endpoint |
| Matches | Deterministic, explainable rule based scoring (0-100) with a score breakdown and a "suggestion, not proof" notice |
| Claims | Private claim submission, duplicate claim prevention, own-claim view, withdrawal, administrator approval / rejection with a reason |
| Handover | Only administrators mark an item as returned; an approved claim is required unless the exception is documented; returned items accept no further claims |
| Administration | Dashboard with counters and queues, claim review, report moderation, audit trail |
| Quality | 61 automated tests (unit + integration), Flyway migrations, deterministic demo data, CI workflow, health endpoint |

---

## Technology stack

| Layer | Technology |
| --- | --- |
| Language / runtime | Java 21 (LTS, Gradle toolchain) |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Security 7, Spring Data JPA, Bean Validation) |
| Database | Azure SQL Database in production, H2 (file based) for local development, Flyway 12 migrations |
| Storage | Azure Blob Storage (private container) in production, local directory for development |
| Front end | HTML5, CSS3, vanilla JavaScript, Bootstrap 5.3 (vendored locally, no CDN required) |
| Build / CI | Gradle 9.6 with wrapper, GitHub Actions |
| Telemetry | Actuator health endpoint, Application Insights via the Java agent (optional) |

---

## Prerequisites

* **Java 21 or newer** (the Gradle toolchain downloads Temurin 21 automatically if needed)
* **No database, no Docker and no Azure account are required** for local development
* Optional: the Azure CLI for a deployment

Check your installation:

```bash
java -version     # 21 or newer
```

---

## Quick start (local)

1. **Clone / open the repository**

   ```bash
   cd FindIT
   ```

2. **Create your local environment file** (optional but recommended)

   ```bash
   cp .env.example .env
   ```

   The application reads `.env` automatically when it sits in the working directory.
   Set at least an administrator password:

   ```dotenv
   ADMIN_SEED_EMAIL=admin@findit.local
   ADMIN_SEED_PASSWORD=choose-your-own-password
   ```

   `.env` is ignored by Git - never commit it.

3. **Start the application**

   ```bash
   ./gradlew bootRun
   ```

   The first start creates the H2 database in `local-data/`, applies the Flyway migrations,
   seeds the reference data and (if `ADMIN_SEED_PASSWORD` is set) creates the administrator
   account plus three synthetic demo students.

4. **Open the application**

   | URL | Description |
   | --- | --- |
   | <http://localhost:8080/> | Landing page |
   | <http://localhost:8080/api/health> | Health endpoint (public) |

   The front end is served by the same application, so there is no CORS setup and only one
   process is needed.

5. **Stop the application** with `Ctrl+C`.

### Build an executable jar

```bash
./gradlew build          # compiles, runs the tests and creates build/libs/findit.jar
java -jar build/libs/findit.jar
```

---

## Running the tests

```bash
./gradlew test                       # all tests
./gradlew test --tests "*MatchScoringServiceTest"   # a single class
```

The HTML report is written to `build/reports/tests/test/index.html`.

| Test class | Focus |
| --- | --- |
| `MatchScoringServiceTest` | Score boundaries, normalisation, missing values, score bands |
| `ItemStatusPolicyTest` | Status transitions and who may edit / close / claim |
| `AuthenticationApiTest` | Registration, sign in, CSRF, safe error messages, role escalation |
| `ItemApiTest` | Report CRUD, validation, ownership, search, pagination, match suggestions |
| `ClaimApiTest` | Claim submission, duplicates, privacy, review, handover, returned items |
| `ImageUploadApiTest` | Image validation, authorisation, serving, front end delivery |

### End-to-end smoke test against a running instance

```bash
ADMIN_EMAIL=admin@findit.local ADMIN_PASSWORD='your-password' ./scripts/smoke-test.sh
```

The script registers three students, creates a lost and a found report, checks the match
suggestion, submits a claim, verifies that a normal user is denied the administrator
endpoints and that foreign claims and private details stay private, and finally reviews the
claim as administrator and marks the item as returned.

---

## Configuration

All configuration comes from `application.yml`, the active profile and environment
variables. Nothing sensitive is committed - see `.env.example`.

| Variable | Profile | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | all | `local` (default) or `azure` |
| `APP_BASE_URL` | all | Public base URL of the application |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | azure | Azure SQL Database connection |
| `AZURE_STORAGE_CONNECTION_STRING` | azure | Blob Storage connection string (optional) |
| `AZURE_STORAGE_ACCOUNT_NAME` | azure | Blob Storage account for managed identity (preferred) |
| `AZURE_STORAGE_CONTAINER` | azure | Name of the **private** blob container |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | azure | Optional telemetry |
| `SESSION_COOKIE_SECURE` | all | `true` when the application is served over HTTPS |
| `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD` | local / azure | Administrator seed - **no default password exists** |
| `DEMO_USER_PASSWORD` | local | Password of the synthetic demo students |
| `APP_SEED_ENABLED` | azure | `true` for one start to create the administrator on the server |
| `APP_CORS_ALLOWED_ORIGINS` | all | Only needed when the front end is hosted on another origin |

> `.env` is parsed as a Java properties file: a `#` starts a comment, so do not use `#`
> inside a secret value.

### Profiles

* **`local`** (default): H2 file database in `local-data/`, images in `local-data/images`,
  demo data seeded, `ddl-auto: validate`.
* **`azure`**: Azure SQL Database, Azure Blob Storage, no seed data, Flyway owns the schema
  (`ddl-auto: none`).

### Timestamps

All timestamps are stored in UTC. The front end renders them in the browser time zone.

---

## Demo walkthrough

1. Open <http://localhost:8080/> and create an account (or sign in as
   `demo.alice@findit.local` with the demo password from your `.env`).
2. **Report a lost item** - choose a category, the campus location, the date and describe the
   item as precisely as possible.
3. Open **Search items** and look for the item. A matching *found* report produces a
   **suggested match** with a score and a breakdown.
4. Open the found report and use **Claim this item**. Your explanation and verification
   answers stay private.
5. Sign in as the administrator (`ADMIN_SEED_EMAIL` / `ADMIN_SEED_PASSWORD`).
6. Open **Admin -> Claim queue**, read the evidence and approve or reject the claim with a
   reason.
7. After the approval, mark the item as **returned**. The report no longer accepts claims and
   the status history is final.

---

## Project layout

```
FindIT/
├── build.gradle                  Gradle build (Spring Boot, dependencies, packaging)
├── settings.gradle
├── gradle.properties             Memory friendly Gradle settings
├── gradlew / gradlew.bat         Gradle wrapper
├── frontend/                     Canonical front end (packaged into the jar)
│   ├── index.html, login.html, register.html, dashboard.html, report-form.html,
│   │   search.html, item.html, my-reports.html, my-claims.html, admin.html,
│   │   admin-claims.html, admin-items.html, help.html
│   ├── css/app.css               Application styles
│   ├── js/                       api.js, ui.js, auth.js, dashboard.js, report-form.js,
│   │                             search.js, item.js, my-reports.js, my-claims.js,
│   │                             admin.js, admin-claims.js, admin-items.js
│   └── vendor/                   Bootstrap 5.3 (vendored, works offline)
├── src/main/java/com/findit/     Backend
│   ├── config/                   Security, web, properties, seed data
│   ├── controller/               REST endpoints
│   ├── domain/                   JPA entities and enums
│   ├── dto/                      Request / response records
│   ├── exception/                Central error handling
│   ├── repository/               Spring Data repositories
│   ├── security/                 Principal, handlers, rate limiter
│   ├── service/                  Business logic (items, claims, matches, admin, audit)
│   └── storage/                  Image storage abstraction (local + Azure Blob)
├── src/main/resources/
│   ├── application*.yml          Configuration per profile
│   └── db/migration/             Flyway migrations (V1 schema, V2 reference data)
├── src/test/java/com/findit/     61 automated tests
├── scripts/smoke-test.sh         End-to-end check against a running instance
├── docs/                         Architecture, API, Azure guide, acceptance checklist
├── .github/workflows/            CI (build + test) and a manual Azure deployment template
├── .env.example                  Placeholder configuration
└── README.md
```

---

## API overview

All endpoints live under `/api`. Authentication is a session cookie (`JSESSIONID`,
`HttpOnly`) plus the CSRF token from the readable `XSRF-TOKEN` cookie in the
`X-XSRF-TOKEN` header for every state changing request.

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | public | Create an account (always `USER`) |
| `POST` | `/api/auth/login` | public | Sign in (form encoded `email`, `password`) |
| `POST` | `/api/auth/logout` | authenticated | Sign out |
| `GET` | `/api/auth/csrf` | public | Current CSRF token |
| `GET` | `/api/auth/me` | authenticated | The signed in user |
| `GET` | `/api/profile` / `PUT` | authenticated | Read and update the own profile |
| `GET` | `/api/categories`, `/api/locations` | authenticated | Reference data |
| `POST` | `/api/items` | authenticated | Create a report |
| `GET` | `/api/items` | authenticated | Search and filter reports |
| `GET` | `/api/items/{id}` | authenticated | Report details |
| `PUT` | `/api/items/{id}` | owner | Edit an own open report |
| `POST` | `/api/items/{id}/close` | owner / admin | Close a report |
| `GET` | `/api/items/{id}/matches` | authenticated | Scored match suggestions |
| `GET` | `/api/items/{id}/private-details` | owner / admin | Private verification detail |
| `POST` | `/api/items/{id}/image` | owner | Upload an image |
| `GET` | `/api/items/{id}/image` | authenticated | Read the image |
| `POST` | `/api/items/{id}/claims` | authenticated | Submit a private claim |
| `GET` | `/api/claims/mine`, `/api/claims/{id}` | claimant / admin | Own claims |
| `POST` | `/api/claims/{id}/withdraw` | claimant | Withdraw a pending claim |
| `GET` | `/api/admin/dashboard` | admin | Counters and work queues |
| `GET` | `/api/admin/claims` | admin | Claim queue |
| `POST` | `/api/admin/claims/{id}/approve` / `reject` | admin | Record a decision |
| `POST` | `/api/admin/items/{id}/mark-returned` | admin | Record the handover |
| `POST` | `/api/admin/items/{id}/moderate` | admin | Close, reject or re-open a report |
| `GET` | `/api/admin/audit-logs` | admin | Audit trail |
| `GET` | `/api/health` | public | Health information |

The detailed contract, including the error format, is in [`docs/API.md`](docs/API.md).

---

## Matching rules

The score is deterministic and explainable (maximum 100 points):

| Component | Points | Rule |
| --- | --- | --- |
| Category | 30 | 30 for the same category, otherwise 0 |
| Keywords | up to 30 | 30 x Jaccard similarity of the normalised keywords of "title + description" |
| Location | up to 20 | 20 for the identical location, up to 10 for a partially matching name |
| Date | up to 20 | 20 for the same day, minus 2 points per day, never below 0 |

Text is lower cased, punctuation and stop words are removed and simple plurals are reduced.
Bands: `HIGH` from 70, `MEDIUM` from 40, otherwise `LOW`.

---

## Azure deployment

**Final deployment state: the application IS deployed to Azure App Service and running** - see the deployment status below. The
configuration, the helper scripts, the deployment workflow template and the guide are
ready; creating the resources requires your Azure subscription and your approval.

The fastest path (about 45 minutes):

```bash
# 1. install the Azure CLI, then: az login
# 2. create the resources (asks for the SQL administrator password)
./scripts/azure-setup.sh --resource-group rg-findit --location westeurope \
    --app-name app-findit-<suffix> --sql-server sql-findit-<suffix> --admin-login finditadmin

# 3. build, publish and configure
./scripts/azure-deploy.sh --resource-group rg-findit --app-name app-findit-<suffix> \
    --sql-server sql-findit-<suffix> --storage-account stfindit<suffix> \
    --sql-username finditadmin --admin-email admin@findit.local --seed-admin

# 4. verify
ADMIN_EMAIL=admin@findit.local ADMIN_PASSWORD='<your password>' \
    ./scripts/smoke-test.sh https://app-findit-<suffix>.azurewebsites.net
```

The complete step-by-step guide - every command, every setting, the managed identity and
the role assignment, the migrations, troubleshooting and the cost control - is in
[`docs/AZURE_DEPLOYMENT.md`](docs/AZURE_DEPLOYMENT.md).

---

## Security notes

* Passwords are hashed with BCrypt (cost 10) and are never logged.
* Sign in errors do not reveal whether an email address exists.
* The role is never taken from the request body - registration always creates a `USER`.
* Private verification details, claim texts and contact details are only returned to the
  claimant and to administrators; they are never part of the public item DTO.
* Every state changing request requires a CSRF token; the session cookie is `HttpOnly`.
* Uploads are validated by content type, size and magic bytes, are stored under a server
  generated name and are served only through an authorised endpoint.
* The blob container is private; no public container access is enabled.
* Sign in and claim submission are rate limited.
* Error responses contain a safe message and never a stack trace.

---

## Troubleshooting

| Symptom | Solution |
| --- | --- |
| `Port 8080 was already in use` | Stop the other process or run with `SERVER_PORT=8081 ./gradlew bootRun` |
| `Address already in use` for the H2 file | Another instance still holds the file - stop it and delete `local-data/*.db` |
| No administrator account | Set `ADMIN_SEED_PASSWORD` and restart; the account is only created once |
| `403 Forbidden` on a state changing request | The CSRF token is missing or stale - reload the page and try again |
| Images are not found | Check `app.storage.local-dir` and that the directory is writable |
| The build fails to download dependencies | Check the network access to `repo1.maven.org` |
| Gradle runs out of memory | `gradle.properties` already limits the daemon to 900 MB; close other programs |

---

## Further documentation

* [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - layers, data model, workflows, design decisions
* [`docs/API.md`](docs/API.md) - endpoint reference and error format
* [`docs/AZURE_DEPLOYMENT.md`](docs/AZURE_DEPLOYMENT.md) - Azure resources, settings, costs, deployment
* [`docs/ACCEPTANCE_CHECKLIST.md`](docs/ACCEPTANCE_CHECKLIST.md) - manual acceptance test checklist
* [`IMPLEMENTATION_REPORT.md`](IMPLEMENTATION_REPORT.md) - what was built, what was tested, what is left
* [`FindIT_PRD.md`](FindIT_PRD.md) - the original product requirements (unchanged)
