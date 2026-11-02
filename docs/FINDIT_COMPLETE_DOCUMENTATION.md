# FindIT — Complete Documentation

> **Complete, consolidated documentation** for the **FindIT Smart Campus Lost and Found
> Management System**.
>
> **Live deployment:** <https://app-findit-vijay.azurewebsites.net>
> **Final build state:** `BUILD SUCCESSFUL` · **Automated tests:** **61/61 passed** ·
> **End-to-end smoke test:** **21 passed / 0 failed**.

This file consolidates the full project documentation into a single reference. Individual
documents remain available at their canonical locations (see
[Section 15 — Documentation map](#15-documentation-map)).

---

## Table of contents

1. [Purpose and scope](#1-purpose-and-scope)
2. [Project overview](#2-project-overview)
3. [Features](#3-features)
4. [Technology stack](#4-technology-stack)
5. [Architecture](#5-architecture)
6. [Getting started (local)](#6-getting-started-local)
7. [Configuration](#7-configuration)
8. [API](#8-api)
9. [Matching rules](#9-matching-rules)
10. [Security](#10-security)
11. [Testing and verification](#11-testing-and-verification)
12. [Deployment — Azure final state](#12-deployment--azure-final-state)
13. [Demonstration and final status](#13-demonstration-and-final-status)
14. [Troubleshooting](#14-troubleshooting)
15. [Documentation map](#15-documentation-map)
16. [Appendix — credentials](#16-appendix--credentials)

---

## 1. Purpose and scope

FindIT is a campus lost and found service. Students and staff report **lost** and **found**
items; the system suggests possible matches between a lost report and a found report; a
claimant submits private evidence; a campus administrator verifies the claim and records the
handover.

**Product rule (never a setting):** *a match score is a suggestion — never proof of ownership.*
Every claim is reviewed by an administrator.

The project is a Java / Spring Boot REST API with an HTML / CSS / vanilla JavaScript +
Bootstrap 5 front end. It runs locally without any Azure credentials and is configured for
Azure SQL Database, Azure Blob Storage and Azure App Service through environment variables.

---

## 2. Project overview

| Aspect | Detail |
| --- | --- |
| Product | Smart Campus Lost and Found Management System |
| Backend | Java 21, Spring Boot 4.1.1, Spring Security 7, Spring Data JPA, Bean Validation |
| Front end | HTML5 / CSS3 / vanilla JS / Bootstrap 5.3 (vendored locally, no CDN) |
| Database | Azure SQL (production) · H2 file (local) — Flyway migrations |
| Storage | Azure Blob (private container) (production) · local directory (local) |
| Build | Gradle 9.6 wrapper, GitHub Actions CI |
| Runtime | Azure App Service (Linux, Java 21) |

---

## 3. Features

| Area | Implemented |
| --- | --- |
| Authentication | Registration, sign in, sign out, current user, BCrypt hashing, server-side session + CSRF, rate-limited sign in |
| Roles | `USER` and `ADMIN`, enforced on every backend route and in the services |
| Reports | Create, edit (own + open only), close, view, list, search and filter lost/found reports |
| Reference data | Item categories and campus locations, seeded via Flyway |
| Search | Keyword, type, category, location, date range, status, sorting, pagination, stable ordering, indexes |
| Images | Private storage behind an interface (local / Azure Blob), MIME + magic byte + size validation, server-generated file names, authorised serving endpoint |
| Matches | Deterministic, explainable scoring (0–100) with score breakdown + "suggestion, not proof" notice |
| Claims | Private submission, duplicate prevention, own-claim view, withdrawal, admin approve/reject with reason |
| Handover | Only admins mark returned; approved claim required (or documented exception); returned items accept no further claims |
| Administration | Dashboard counters/queues, claim review, report moderation, audit trail |
| Quality | 61 automated tests, Flyway migrations, deterministic demo data, CI, health endpoint |

---

## 4. Technology stack

| Layer | Technology |
| --- | --- |
| Language / runtime | Java 21 (LTS, Gradle toolchain) |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Security 7, Spring Data JPA, Bean Validation) |
| Database | Azure SQL Database (prod), H2 file-based (local), Flyway 12 |
| Storage | Azure Blob Storage (prod), local dir (local) |
| Front end | HTML5, CSS3, vanilla JavaScript, Bootstrap 5.3 (vendored) |
| Build / CI | Gradle 9.6 (wrapper), GitHub Actions |
| Telemetry | Actuator health endpoint; Application Insights via Java agent (optional) |

---

## 5. Architecture

- Single Spring Boot artifact serves the REST API and the static front end (`classpath:/static`); one process, no CORS needed.
- Layers: `config`, `controller`, `service`, `repository`, `domain`, `dto`, `exception`, `security`, `storage`.
- **Rule:** controllers never decide permissions or status transitions; services decide; routes are additionally guarded by Spring Security.
- Data model: users, categories, locations, items, claims, audit_logs; all FKs/uniques enforced by DB; Flyway owns schema (`ddl-auto: none` in Azure, `validate` locally).
- Report lifecycle: `OPEN → UNDER_REVIEW → CLAIMED → RETURNED (final)`, plus close/reject/re-open paths; `RETURNED` is final and accepts no further claims.
- Claims and handover: claims only on `FOUND` reports in `OPEN`/`UNDER_REVIEW`; admins review; approve → `CLAIMED` + competing claims rejected with reason; reject requires reason; handover only by admin, approved claim required (or documented exception).
- Security: server-side sessions (`JSESSIONID`, HttpOnly, SameSite=Lax, Secure when HTTPS) + double-submit CSRF (`XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header) + BCrypt + safe error messages + role never from body + private fields stripped from DTOs + upload validation + server-generated storage names + private blob container + rate limits + audit events.
- Requirement traceability: FR-01 … FR-11 mapped to implementations and tests.

See [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) for the full detail.

---

## 6. Getting started (local)

```bash
./gradlew bootRun          # start (H2 file DB in local-data/, Flyway, demo data)
java -jar build/libs/findit.jar
./gradlew build            # build + tests → build/libs/findit.jar
./gradlew test
ADMIN_EMAIL=admin@findit.local ADMIN_PASSWORD='your-password' ./scripts/smoke-test.sh
```

- Prerequisites: Java 21+ (Gradle toolchain supplies Temurin 21).
- No database / Docker / Azure account required locally.

---

## 7. Configuration

| Variable | Profile | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | all | `local` (default) or `azure` |
| `APP_BASE_URL` | all | Public base URL |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | azure | Azure SQL connection |
| `AZURE_STORAGE_CONNECTION_STRING` | azure | Blob connection string (optional) |
| `AZURE_STORAGE_ACCOUNT_NAME` | azure | Blob account for managed identity (preferred) |
| `AZURE_STORAGE_CONTAINER` | azure | Private blob container |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | azure | Optional telemetry |
| `SESSION_COOKIE_SECURE` | all | `true` over HTTPS |
| `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD` | local / azure | Admin seed — **no default password** |
| `DEMO_USER_PASSWORD` | local | Demo students password |
| `APP_SEED_ENABLED` | azure | `true` for one start to create the admin |
| `APP_CORS_ALLOWED_ORIGINS` | all | Only if front end is on another origin |

- Profiles: `local` (H2 file, images local, demo data, `ddl-auto: validate`); `azure` (Azure SQL, Blob, no seed, `ddl-auto: none`).
- Timestamps stored UTC; front end renders in browser timezone.

---

## 8. API

Base path `/api`; session cookie + CSRF; error format centralised in `exception`.

Key routes (full detail in [`docs/API.md`](API.md)):

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | public | Create account (always `USER`) |
| `POST` | `/api/auth/login` | public | Sign in |
| `POST` | `/api/auth/logout` | authenticated | Sign out |
| `GET` | `/api/auth/csrf` | public | CSRF token |
| `GET` | `/api/auth/me` | authenticated | Current user |
| `GET` | `/api/profile` / `PUT` | authenticated | Own profile |
| `GET` | `/api/categories`, `/api/locations` | authenticated | Reference data |
| `POST` | `/api/items` | authenticated | Create report |
| `GET` | `/api/items` | authenticated | Search / filter |
| `GET` | `/api/items/{id}` | authenticated | Report details |
| `PUT` | `/api/items/{id}` | owner | Edit own open report |
| `POST` | `/api/items/{id}/close` | owner / admin | Close report |
| `GET` | `/api/items/{id}/matches` | authenticated | Scored match suggestions |
| `GET` | `/api/items/{id}/private-details` | owner / admin | Private verification detail |
| `POST` | `/api/items/{id}/image` | owner | Upload image |
| `GET` | `/api/items/{id}/image` | authenticated | Read image |
| `POST` | `/api/items/{id}/claims` | authenticated | Submit claim |
| `GET` | `/api/claims/mine`, `/api/claims/{id}` | claimant / admin | Own claims |
| `POST` | `/api/claims/{id}/withdraw` | claimant | Withdraw pending claim |
| `GET` | `/api/admin/dashboard` | admin | Counters / queues |
| `GET` | `/api/admin/claims` | admin | Claim queue |
| `POST` | `/api/admin/claims/{id}/approve` / `reject` | admin | Decision |
| `POST` | `/api/admin/items/{id}/mark-returned` | admin | Handover |
| `POST` | `/api/admin/items/{id}/moderate` | admin | Report moderation |
| `GET` | `/api/admin/audit-logs` | admin | Audit trail |
| `GET` | `/api/health` | public | Health |

---

## 9. Matching rules

Score 0–100, deterministic and explainable:

| Component | Points | Rule |
| --- | --- | --- |
| Category | 0 / 30 | same category = 30, else 0 |
| Keywords | 0–30 | 30 × Jaccard similarity of normalised keywords of title + description |
| Location | 0–20 | identical location = 20; partial name match = up to 10 |
| Date | 0–20 | same day = 20, minus 2 per day, never below 0 |

Bands: `HIGH` ≥ 70, `MEDIUM` ≥ 40, otherwise `LOW`.

Text normalisation: lower case, punctuation removed, stop words removed, tokens < 3 chars removed, simple plurals reduced.

Candidates: open reports of the opposite type, excluding the report itself and the requester's own reports.

---

## 10. Security

- BCrypt (cost 10); passwords never logged.
- Sign-in errors do not reveal whether an email exists.
- Role never taken from request body; registration always creates `USER`.
- Private verification details / claim texts / contact details only returned to claimant and admins; never in public item DTO.
- CSRF on all state-changing requests; session cookie `HttpOnly` (+ `Secure` when HTTPS).
- Upload validation: content type allow-list, size limit (5 MB), magic byte check; server-generated storage names; private container; images streamed via authorised endpoint.
- Rate limiting: sign in (IP + email), claims (user + hour).
- Audit events recorded; audit metadata never contains secrets.

---

## 11. Testing and verification

- `./gradlew build` → **BUILD SUCCESSFUL**; **61/61 tests pass**.
- Test classes: `MatchScoringServiceTest`, `ItemStatusPolicyTest`, `AuthenticationApiTest`, `ItemApiTest`, `ClaimApiTest`, `ImageUploadApiTest`.
- Local smoke test: **21 passed / 0 failed** (`scripts/smoke-test.sh`).
- Live smoke test against Azure: **21 passed / 0 failed** (final health: `{"status":"UP",...}`).

---

## 12. Deployment — Azure final state

Final deployment state: **the application IS deployed to Azure App Service and running**.

| Resource | Name | Region | State |
| --- | --- | --- | --- |
| Resource group | `rg-findit` | eastasia | ✅ |
| Azure SQL server | `sql-findit-vijay` | eastasia | ✅ (admin `finditadmin`) |
| Azure SQL database | `findit` | eastasia | ✅ (GeneralPurpose / Gen5, 2 vCores) |
| Storage account | `stfinditvijay` | eastasia | ✅ (private container `findit-item-images`) |
| App Service plan | `plan-findit` | eastasia | ✅ (B1 Linux) |
| Web App | `app-findit-vijay` | eastasia | ✅ (Java 21) |

Final live URL: <https://app-findit-vijay.azurewebsites.net>

Final app settings: `SPRING_PROFILES_ACTIVE=azure`, `APP_BASE_URL=https://app-findit-vijay.azurewebsites.net`, `DB_URL=jdbc:sqlserver://sql-findit-vijay.database.windows.net:1433;database=findit;...`, `DB_USERNAME=finditadmin`, `AZURE_STORAGE_ACCOUNT_NAME=stfinditvijay`, `AZURE_STORAGE_CONTAINER=findit-item-images`, `SESSION_COOKIE_SECURE=true`, `JAVA_OPTS=-Xms256m -Xmx768m`, `APP_SEED_ENABLED=false`, `ADMIN_SEED_EMAIL=admin@findit.local`.

Final fixes applied during deployment push: registered `Microsoft.Sql` / `Microsoft.Storage` providers; fixed JDBC URL typo; created `AllowAzureServices` firewall rule; assigned Storage Blob Data Contributor RBAC; set `APP_SEED_ENABLED=true` + admin seed → smoke passed; switched back to `false` → healthy.

Full 12-part guide: [`docs/AZURE_DEPLOYMENT.md`](AZURE_DEPLOYMENT.md).

---

## 13. Demonstration and final status

See [`docs/FINAL_DEMONSTRATION.md`](FINAL_DEMONSTRATION.md) for the final live health check, final smoke test output, final front-end asset check and final Azure state.

Final status summary:

| Area | State |
| --- | --- |
| Backend implementation | ✅ Complete (45 source files) |
| Frontend implementation | ✅ Complete (13 pages, CSS, 12 JS modules, vendored Bootstrap) |
| Automated tests | ✅ **61/61 pass**, build `BUILD SUCCESSFUL` |
| Local smoke test | ✅ 21/21 passed |
| **Azure deployment** | ✅ **LIVE & VERIFIED** |
| Azure documentation | ✅ Updated to final deployed state |
| Seeding | ✅ Disabled after one-time admin creation |

**The FindIT Smart Campus Lost & Found Management System is fully built, tested, deployed to Azure, and verified live.**

---

## 14. Troubleshooting

| Symptom | Solution |
| --- | --- |
| `Port 8080 was already in use` | Stop other process or run `SERVER_PORT=8081 ./gradlew bootRun` |
| H2 file in use | Stop instance, delete `local-data/*.db` |
| No administrator account | Set `ADMIN_SEED_PASSWORD` and restart; created once |
| `403` on state changing request | Reload page for a fresh CSRF token |
| Images not found | Check `app.storage.local-dir` and writability |
| Build fails to download dependencies | Check network access to `repo1.maven.org` |
| Gradle out of memory | `gradle.properties` limits daemon to 900 MB |

---

## 15. Documentation map

| Document | Purpose |
| --- | --- |
| [`FindIT_PRD.md`](../FindIT_PRD.md) | Source of truth — original product requirements (unchanged) |
| [`README.md`](../README.md) | Project overview, features, stack, quick start, config, demo, API overview, deployment, security, troubleshooting |
| [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) | Layers, data model, workflows, design decisions, traceability |
| [`docs/API.md`](API.md) | Full endpoint reference and error format |
| [`docs/AZURE_DEPLOYMENT.md`](AZURE_DEPLOYMENT.md) | 12-part Azure guide: resources, settings, identity, RBAC, migrations, troubleshooting, costs |
| [`docs/ACCEPTANCE_CHECKLIST.md`](ACCEPTANCE_CHECKLIST.md) | Manual acceptance checklist |
| [`docs/DEMONSTRATION.md`](DEMONSTRATION.md) | Step-by-step live demonstration of all features |
| [`docs/FINAL_DEMONSTRATION.md`](FINAL_DEMONSTRATION.md) | Final demonstration & complete documentation snapshot |
| [`docs/PRESET_TEST_CREDENTIALS.md`](PRESET_TEST_CREDENTIALS.md) | Preset test credentials (placeholders; real values in the local git-ignored credential file) |
| [`IMPLEMENTATION_REPORT.md`](../IMPLEMENTATION_REPORT.md) | Status, what was built, verification results, Azure status, remaining actions, known limitations |
| [`docs/FINDIT_COMPLETE_DOCUMENTATION.md`](FINDIT_COMPLETE_DOCUMENTATION.md) | **This complete documentation file** |

---

## 16. Appendix — credentials

Real preset credentials are stored in a single **local, git-ignored** file
`local/credentials.env` and are never committed. Public documentation uses placeholders only.

| Slot | Placeholder |
| --- | --- |
| Administrator | `ADMIN_SEED_EMAIL` / `ADMIN_SEED_PASSWORD` |
| Demo students | `DEMO_USER_PASSWORD` |
| Smoke test | `SMOKE_TEST_PASSWORD` |

See [`docs/PRESET_TEST_CREDENTIALS.md`](PRESET_TEST_CREDENTIALS.md) for the placeholder table.
