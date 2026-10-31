# FindIT — Step-by-Step Live Demonstration (All Features)

> **Live deployment:** <https://app-findit-vijay.azurewebsites.net>
> **Preset credentials:** [`docs/PRESET_TEST_CREDENTIALS.md`](PRESET_TEST_CREDENTIALS.md)

This document walks through **every feature** of the FindIT application step by step,
against the live Azure deployment.

---

## Step 0 — Health check

```
GET /api/health
→ HTTP 200
→ {"status":"UP","application":"FindIT","profile":"azure","imageStorage":"azure-blob:findit-item-images",...}
```

The API, the database (Azure SQL) and the blob storage (Azure) are all reachable.

---

## Step 1 — Landing page

1. Open <https://app-findit-vijay.azurewebsites.net/>.
2. The landing page renders (`HTTP 200`) with navigation to **Login**, **Register**, **Search items**.
3. A footer / help link is present; no asset fails to load (CSS / JS / Bootstrap all `200`).

---

## Step 2 — Register a new account

1. Open <https://app-findit-vijay.azurewebsites.net/register>.
2. Submit the registration form (email, display name, password).
3. The backend always creates a **`USER`** role — the role is never taken from the request body.

## Step 3 — Sign in / sign out / session / CSRF

1. Open <https://app-findit-vijay.azurewebsites.net/login> and sign in (form fields
   `email` + `password`).
2. A session cookie (`JSESSIONID`, `HttpOnly`) is issued.
3. A CSRF token is issued via `GET /api/auth/csrf` and must be sent as the `X-XSRF-TOKEN` header
   on every state-changing request.
4. State-changing requests without a valid CSRF token are rejected.
5. Sign out via `POST /api/auth/logout`, then sign in again.

---

## Step 4 — Dashboard

1. After signing in, open the **Dashboard** page.
2. The dashboard shows counters and work queues for the signed-in user.

---

## Step 5 — Create a lost report (student)

1. Open the **Create report** page (lost or found).
2. Fill in: type (`LOST` / `FOUND`), title, category, campus location, date, description.
3. Submit `POST /api/items`.
4. The report is stored and appears in the user's **My reports**.

## Step 6 — Create a found report + match suggestions (student)

1. As a second user (or sign out / register another account), create a **`FOUND`** report that
   matches the lost report.
2. As the owner, open the lost report and call `GET /api/items/{id}/matches`.
3. The response contains a deterministic score (max 100) with a category / keyword / location /
   date breakdown and the notice: *"suggestion, not proof of ownership"*.

## Step 7 — Submit a claim (claimant)

1. On the found report, open the **Claim** form (private explanation + verification answers).
2. Submit `POST /api/items/{id}/claims`.
3. The claim text / verification answers are **private** — they are not part of the public item DTO.

## Step 8 — Privacy checks

1. The claimant can read their own claim (`GET /api/claims/{id}` → `200`).
2. Another user cannot read a foreign claim (`403` / `404`).
3. Private verification details are not readable by other users (`403` / `404`), but the owner can
   read them of their own report (`200`).

## Step 9 — Admin workflow

1. Sign in as the administrator (`admin@findit.local` / `Vijay#null@10101`).
2. Open **Admin → Dashboard** (`GET /api/admin/dashboard`) → counters / queues.
3. Open the **Claim queue** and approve / reject the claim
   (`POST /api/admin/claims/{id}/approve` / `reject`) with a mandatory reason.
4. Mark the item as **returned**
   (`POST /api/admin/items/{id}/mark-returned`).

## Step 10 — Returned item rejects new claims

1. After an item is marked `RETURNED`, submitting another claim returns **`409`**.

## Step 11 — Security rules enforced during the demo

| Check | Expected |
|---|---|
| Normal user on `/api/admin/**` | `403` |
| Editing a foreign report | `403` |
| Reading a foreign claim / private details | `403` / `404` |
| Duplicate claim | `409` |
| Private verification detail in public response | **never leaked** |

## Step 12 — Logout

1. `POST /api/auth/logout`, then close the browser session.

---

## Final verification summary

- Health: `200 UP` · Frontend: serving · Smoke test: **21/21 passed**.

See also: [`docs/PRESET_TEST_CREDENTIALS.md`](PRESET_TEST_CREDENTIALS.md) and
[`docs/ACCEPTANCE_CHECKLIST.md`](ACCEPTANCE_CHECKLIST.md).
