# FindIT - Preset Test Credentials

> **Live deployment:** <https://app-findit-vijay.azurewebsites.net>

> **Security notice:** Real preset credentials are kept in a single local credential file
> (`local/credentials.env`) which is **git-ignored** and must never be committed publicly.
> This document intentionally contains **no real values** - only placeholders - so that
> nothing sensitive is published.

## 1. Administrator (seed slot)

| Field | Value |
|---|---|
| Email | `<ADMIN_SEED_EMAIL>` |
| Password | `<ADMIN_SEED_PASSWORD>` |
| Role | `ADMIN` |

## 2. Demo student accounts (seed slot)

| Email | Password | Role | Purpose |
|---|---|---|---|
| `<DEMO_ALICE_EMAIL>` | `<DEMO_USER_PASSWORD>` | `USER` | Lost item owner (local) |
| `<DEMO_BEN_EMAIL>` | `<DEMO_USER_PASSWORD>` | `USER` | Finder / claimant (local) |
| `<DEMO_CLAUDE_EMAIL>` | `<DEMO_USER_PASSWORD>` | `USER` | Secondary demo user (local) |

## 3. Smoke test accounts (ephemeral, generated per run)

| Account | Email | Password | Purpose |
|---|---|---|---|
| Owner | `owner.<run-id>@findit.local` | `<SMOKE_TEST_PASSWORD>` | Creates the lost report |
| Finder | `finder.<run-id>@findit.local` | `<SMOKE_TEST_PASSWORD>` | Creates the found report |
| Claimant | `claimant.<run-id>@findit.local` | `<SMOKE_TEST_PASSWORD>` | Submits the claim |

## 4. Quick reference

| Check | Command / URL |
|---|---|
| Health | `GET https://app-findit-vijay.azurewebsites.net/api/health` |
| Sign in (admin) | `POST /api/auth/login` with `email=<ADMIN_SEED_EMAIL>` & `password=<ADMIN_SEED_PASSWORD>` |
| Sign in (demo) | `POST /api/auth/login` with `email=<DEMO_*_EMAIL>` & `password=<DEMO_USER_PASSWORD>` |
| Smoke test | `ADMIN_EMAIL=<ADMIN_SEED_EMAIL> ADMIN_PASSWORD=<ADMIN_SEED_PASSWORD> ./scripts/smoke-test.sh https://app-findit-vijay.azurewebsites.net` |

> **Security note:** These are **test-only** credentials. The administrator password is supplied
> through `ADMIN_SEED_PASSWORD` / the Azure App Service setting and there is deliberately no
> default password. Never reuse these passwords for production accounts.
