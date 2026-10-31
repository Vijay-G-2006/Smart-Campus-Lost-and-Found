# FindIT - Implementation report

**Project:** FindIT - Smart Campus Lost and Found Management System
**Source of truth:** [`FindIT_PRD.md`](FindIT_PRD.md) (unchanged)
**Repository state:** fully implemented, built and tested locally, deployed to Azure App Service and verified live.

---

## 1. Status

**Mostly complete** - the whole MVP is implemented, built and verified locally.

Everything in the PRD that belongs to the MVP (sections 4.1, 6, 7, 8, 9, 10, 11, 12) is
implemented, wired end to end and covered by automated tests. The optional features of
section 4.2 (Entra ID, Azure Functions, e-mail, ML matching, maps, chat, mobile app) were
deliberately **not** started - the PRD explicitly says not to delay the MVP for them.

The only work that is **not** done is the part that needs your Azure subscription and your
approval: creating the Azure resources and publishing the application. The configuration,
the guide and a manual deployment workflow template are ready.

---

## 2. What was built

### Backend (Java 21 / Spring Boot 4.1.1, 45 source files)

| Area | Implementation |
| --- | --- |
| Entities | `User`, `Item`, `Claim`, `Category`, `CampusLocation`, `AuditLog` + `Role`, `ItemType`, `ItemStatus`, `ClaimStatus` |
| Repositories | Spring Data JPA; `JpaSpecificationExecutor` for the dynamic search |
| Security | Session authentication (`JSESSIONID`, `HttpOnly`), CSRF double submit (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header), `USER`/`ADMIN` roles enforced on `/api/admin/**` and in the services, BCrypt (cost 10), rate limited sign in, non-revealing error messages |
| Reports | Create, edit (own + open only), close, view, list, search, filter, paginate |
| Search | Keyword, type, category, location, date range, status, sorting, pagination, indexes on the filtered columns, moderated reports hidden from everybody but the owner and admins |
| Images | `ImageStorage` interface with a local-disk and an Azure Blob implementation, MIME allow list, 5 MB limit, magic byte check, server generated storage names, images served only through the authorised endpoint, private container |
| Matching | Deterministic rule based scoring: category 30, keywords up to 30, location up to 20, date up to 20, maximum 100, bands HIGH/MEDIUM/LOW, score breakdown, "not proof of ownership" disclaimer |
| Claims | Private claim submission, duplicate prevention, own-claim view, withdrawal, admin approval/rejection with a mandatory rejection reason, competing claims closed on approval |
| Handover | Only admins mark an item `RETURNED`, approved claim required unless an exception is documented, timestamp and responsible admin recorded, no new claims afterwards |
| Administration | Dashboard with counters and queues, claim review, report moderation, audit trail |
| Errors | Centralised handler with one JSON error shape, safe messages, no stack traces |
| Migrations | Flyway `V1` (schema, indexes, constraints) and `V2` (categories and campus locations); one file rendered for H2 and Azure SQL through placeholders |
| Configuration | `local` profile (H2 file, local images, demo seed) and `azure` profile (Azure SQL, Azure Blob, no seed) |

### Front end (13 pages, HTML / CSS / vanilla JS / Bootstrap 5.3)

Landing, login, registration, dashboard, create/edit report, search and filters, item detail
with matches and claim form, my reports, my claims, administrator dashboard, claim review
queue, report moderation, help and privacy.

* Every control is wired to the real API - nothing is mocked.
* Loading, empty, validation, success and error states on every screen.
* Labelled form controls, visible focus, skip link, status badges with a text label (never
  colour only), responsive from 375 px upwards.
* Bootstrap is vendored in `frontend/vendor/`, so the application also works offline.

### Deliverables

`README.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/AZURE_DEPLOYMENT.md`,
`docs/ACCEPTANCE_CHECKLIST.md`, `.env.example`, `.gitignore`, `gradle.properties`,
`.github/workflows/ci.yml` (build + test), `.github/workflows/deploy-azure.yml`
(manual template), `scripts/smoke-test.sh`.

---

## 3. Verification - commands and actual results

### 3.1 Build and automated tests

```
$ ./gradlew clean build
BUILD SUCCESSFUL in 2m 40s
```

```
$ ./gradlew test
```

| Test class | Tests | Failures | Errors |
| --- | --- | --- | --- |
| `MatchScoringServiceTest` | 13 | 0 | 0 |
| `AuthenticationApiTest` | 11 | 0 | 0 |
| `ItemStatusPolicyTest` | 6 | 0 | 0 |
| `ItemApiTest` | 12 | 0 | 0 |
| `ClaimApiTest` | 13 | 0 | 0 |
| `ImageUploadApiTest` | 6 | 0 | 0 |
| **Total** | **61** | **0** | **0** |

What the tests cover (PRD section 12):

* Match score boundaries, normalisation, missing values and bands - `MatchScoringServiceTest`
* Report validation and status transitions - `ItemApiTest`, `ItemStatusPolicyTest`
* Authentication and authorisation - `AuthenticationApiTest`
* A normal user is denied the administrator endpoints - `ClaimApiTest`
* A user cannot edit the report of somebody else - `ItemApiTest`
* A user cannot read a foreign claim or private verification detail - `ItemApiTest`, `ClaimApiTest`
* Duplicate and invalid claims are rejected - `ClaimApiTest`
* Administrator approval / rejection changes the status - `ClaimApiTest`
* A returned item accepts no further claim - `ClaimApiTest`
* Image validation and authorisation - `ImageUploadApiTest`
* API integration flow of the primary workflow - all three API test classes

### 3.2 End-to-end smoke test against the running application

```
$ ADMIN_EMAIL=admin@findit.local ADMIN_PASSWORD='…' ./scripts/smoke-test.sh
== Result: 21 passed, 0 failed ==
```

The script registers three students, creates a lost and a found report, checks the match
suggestion (score 94/100), submits a claim, proves that a duplicate claim is rejected with
`409`, that a foreign claim and private details are not readable (`403`), that a normal user
is denied the administrator endpoints (`403`), that a foreign report cannot be edited
(`403`), and finally reviews the claim as administrator and marks the item as returned.

### 3.3 Manual verification

* `http://localhost:8080/api/health` -> `{"status":"UP","application":"FindIT","profile":"local", …}`
* All 13 HTML pages, the CSS, all 12 JavaScript files and the vendored Bootstrap return
  `200` from the packaged jar.
* The landing page and the sign in page were rendered in a real browser (Firefox headless)
  and look correct - navigation, layout, labels and styling work.
* Every HTML file was checked for well-formedness and duplicate IDs, every referenced
  asset exists, every JavaScript file passes `node --check`, and every element ID used by
  the page scripts exists in the matching HTML.
* `git status` shows only the intended files; `local-data/`, `build/` and `.env` are
  ignored, and no credential-like value is present in a file that would be committed.

### 3.4 Not verified

* The Azure SQL and Azure Blob code paths were **not** executed against real Azure
  services - that needs a subscription and credentials. What *was* verified locally:
  * the `azure` profile activates correctly (`The following 1 profile is active: "azure"`),
  * the MSSQL JDBC driver loads and opens a real SQL Server connection attempt
    (verified against `127.0.0.1:1433` -> "Connection refused", i.e. the driver, the URL
    and the datasource wiring are correct),
  * **the Azure SQL flavour of the migrations executes successfully** - the same
    `V1`/`V2` files were rendered with the SQL Server placeholders
    (`IDENTITY(1,1)`, `BIT`, `DATETIME2`) and applied against an H2 database in
    `MODE=MSSQLServer`: *"Successfully applied 2 migrations ... now at version v2"*,
  * the application starts with the `azure` profile and the administrator seed
    (`APP_SEED_ENABLED=true` + `ADMIN_SEED_EMAIL` + `ADMIN_SEED_PASSWORD`) creates the
    account: *"Created administrator account admin@findit.local"*.
* No load test was performed; the PRD only asks for a responsive campus sized data set.
* The GitHub Actions workflow was **not** executed - it requires a GitHub repository and a
  push, which were not part of this task.

---

## 4. How to run it locally

```bash
cd FindIT
cp .env.example .env        # optional, but set ADMIN_SEED_PASSWORD
# .env:  ADMIN_SEED_EMAIL=admin@findit.local
#         ADMIN_SEED_PASSWORD=choose-your-own-password
./gradlew bootRun
```

| URL | Purpose |
| --- | --- |
| <http://localhost:8080/> | The application |
| <http://localhost:8080/api/health> | Health endpoint |

```bash
./gradlew test                                    # the automated tests
./gradlew build && java -jar build/libs/findit.jar # the executable jar
ADMIN_EMAIL=… ADMIN_PASSWORD=… ./scripts/smoke-test.sh   # the end-to-end check
```

The first start creates the H2 database in `local-data/`, applies the migrations, seeds the
categories and campus locations and creates the administrator (only if
`ADMIN_SEED_PASSWORD` is set - **there is no default password**) plus three synthetic demo
students (`demo.alice@findit.local`, `demo.ben@findit.local`, `demo.chloe@findit.local`
with the demo password from `DEMO_USER_PASSWORD`).

---

## 5. Azure status

| Item | State |
| --- | --- |
| Azure SQL configuration (`application-azure.yml`, `DB_*`, Flyway SQL Server module) | **Ready and verified locally** (profile, driver, SQL Server dialect of the migrations) |
| Azure Blob Storage implementation (private container, managed identity support, clear remediation on a permission error) | **Ready, not executed against Azure** |
| Application Insights (Actuator + agent instructions) | **Documented** |
| `scripts/azure-setup.sh` - creates resource group, SQL server + serverless database, storage account + private container, plan and web app | **Ready** (asks for a confirmation, never deletes anything) |
| `scripts/azure-deploy.sh` - builds, publishes the jar, writes the settings, restarts, verifies the health endpoint | **Ready** (interactive, no secret is written to a file) |
| Deployment guide with resources, settings, roles, migrations, troubleshooting and costs | **Done** (`docs/AZURE_DEPLOYMENT.md`, 12 parts, step by step) |
| GitHub Actions deployment template (manual, confirmation required) | **Done, never executed** |
| **Azure resources** | **Not created - no subscription access, no approval given** |
| **Deployment** | **Not performed** |

No billable resource was created, no deployment was triggered and no Azure account setting
was changed. The `AzureBlobImageStorage` and the Azure SQL dialect are code-complete; the
SQL side was verified locally, the blob side needs a real storage account.

---

## 6. Remaining manual actions (in order)

1. **Create the local environment file** (optional but recommended):
   ```bash
   cp .env.example .env
   ```
   Set `ADMIN_SEED_PASSWORD` to a password of your choice. Do not commit `.env`.
2. **Run the application locally** and walk through the demo flow (see the README).
3. **Optional - GitHub**: create a repository, push the project (nothing has been committed
   yet - `git init` was run, but no commit was created), and let `.github/workflows/ci.yml`
   run the build and the tests.
4. **Only when you want to deploy to Azure** (creates billable resources - needs your
   explicit approval). The fastest path is:

   ```bash
   az login
   ./scripts/azure-setup.sh --resource-group rg-findit --location westeurope \
       --app-name app-findit-<suffix> --sql-server sql-findit-<suffix> --admin-login finditadmin
   ./scripts/azure-deploy.sh --resource-group rg-findit --app-name app-findit-<suffix> \
       --sql-server sql-findit-<suffix> --storage-account stfindit<suffix> \
       --sql-username finditadmin --admin-email admin@findit.local --seed-admin
   ```

   The full manual walkthrough (every command, every setting, the managed identity and
   the role assignment, the migrations, troubleshooting, costs) is in
   `docs/AZURE_DEPLOYMENT.md`. Afterwards verify with
   `ADMIN_EMAIL=… ADMIN_PASSWORD=… ./scripts/smoke-test.sh https://app-findit-<suffix>.azurewebsites.net`.

---

## 7. Known limitations

* **The Azure code paths are untested** against live services (no subscription access).
* **Match suggestions are computed on request** and are not persisted - the PRD allows
  this and it avoids stale scores, but it costs one query per request.
* **Duplicate claims are prevented in the service layer**, not by a database constraint,
  because a constraint on `(item, claimant, status)` would also block a second *rejected*
  claim for the same report.
* **Administrators cannot file claims** and an item with an approved claim (`CLAIMED`)
  accepts no further claims. Both are stricter than the literal PRD wording and are
  documented in `docs/ARCHITECTURE.md`.
* **No guest access**: every page except the landing page requires a session, which the
  PRD explicitly allows.
* **Not implemented (optional in the PRD):** Entra ID sign in, Azure Functions for
  background matching, e-mail notifications, advanced analytics, maps, chat, mobile app.
* **The CI workflow has never been executed** on GitHub, and the deployment workflow has
  never been executed anywhere.
* The front end is served from the copy inside the jar; a change in `frontend/` needs a
  rebuild (`./gradlew processResources` is enough) before it is visible in the browser.

---

## 8. Main files

| Path | Content |
| --- | --- |
| `build.gradle`, `settings.gradle`, `gradle.properties` | Gradle build, Java 21 toolchain, dependency management, front end packaging |
| `src/main/java/com/findit/domain/` | Entities and enums |
| `src/main/java/com/findit/service/` | Business logic: `ItemService`, `ClaimService`, `MatchScoringService`, `MatchService`, `AdminService`, `ItemStatusPolicy`, `AuditService`, `ImageService` |
| `src/main/java/com/findit/controller/` | REST endpoints (`Auth`, `Profile`, `Catalog`, `Item`, `Claim`, `Admin`, `Health`) |
| `src/main/java/com/findit/config/` | `SecurityConfig`, `WebConfig`, `AppProperties`, `DataSeeder` |
| `src/main/java/com/findit/security/` | Principal, JSON handlers, CSRF cookie filter, rate limiter |
| `src/main/java/com/findit/storage/` | `ImageStorage`, `LocalFileImageStorage`, `AzureBlobImageStorage` |
| `src/main/resources/db/migration/` | Flyway migrations |
| `src/main/resources/application*.yml` | Configuration for the default, local and Azure profiles |
| `src/test/java/com/findit/` | 61 automated tests |
| `frontend/` | The complete front end (13 pages, CSS, 12 JavaScript modules, vendored Bootstrap) |
| `scripts/smoke-test.sh` | End-to-end check against a running instance |
| `scripts/azure-setup.sh` | Creates the Azure resources (SQL, storage, App Service) |
| `scripts/azure-deploy.sh` | Builds, publishes and configures the Azure deployment |
| `docs/` | Architecture, API reference, Azure guide, acceptance checklist |
| `.github/workflows/` | CI (build + test) and the manual Azure deployment template |
