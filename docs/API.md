# FindIT - API reference

Base URL: `http://localhost:8080` (local) or `https://<app>.azurewebsites.net` (Azure).

---

## Conventions

### Authentication

The API uses a **server side session**.

1. `GET /api/auth/csrf` (or any response) sets the readable cookie `XSRF-TOKEN`.
2. `POST /api/auth/login` (form encoded: `email`, `password`) creates the session and
   returns the `JSESSIONID` cookie.
3. Every state changing request (`POST`, `PUT`, `DELETE`) must send the token in the
   header `X-XSRF-TOKEN`. Missing or invalid tokens are answered with `403`.

### Content types

* Request bodies: `application/json`
* Sign in: `application/x-www-form-urlencoded` (handled by Spring Security)
* Image upload: `multipart/form-data` with the field name `file`

### Pagination

List endpoints return a stable envelope:

```json
{
  "content": [],
  "page": 0,
  "size": 12,
  "totalElements": 42,
  "totalPages": 4,
  "first": true,
  "last": false
}
```

`page` is zero based, `size` defaults to 12 (items) / 10 (claims) and is capped at 50.

### Error format

Every failure returns the same shape:

```json
{
  "timestamp": "2026-05-01T10:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/items",
  "fieldErrors": {
    "title": "Title must be between 3 and 100 characters"
  }
}
```

| Status | Meaning |
| --- | --- |
| `400` | Validation error or a rejected business rule |
| `401` | Not signed in (or wrong credentials) |
| `403` | Signed in but not allowed, or a missing CSRF token |
| `404` | Resource does not exist or must not be revealed |
| `409` | Conflict (duplicate claim, duplicate account, illegal state transition) |
| `413` | Upload too large |
| `429` | Rate limit exceeded |
| `500` | Unexpected server error (no stack trace in the response) |

---

## Authentication

### `POST /api/auth/register`

Public. Creates an account. The role is **always** `USER`, even if the body contains a
role.

```json
{
  "email": "student@college.edu",
  "displayName": "Student Name",
  "password": "at least 10 characters with a letter and a digit"
}
```

`201 Created` ->

```json
{
  "id": 7,
  "email": "student@college.edu",
  "displayName": "Student Name",
  "role": "USER",
  "active": true,
  "contactChannel": null,
  "createdAt": "2026-05-01T10:00:00Z"
}
```

Errors: `400` (validation), `409` (the address cannot be registered).

### `POST /api/auth/login`

Public. Form encoded `email` and `password`.

`200 OK` ->

```json
{ "user": { "...": "..." }, "csrfToken": "0f0a...-..." }
```

Errors: `401` with the message `Invalid email or password` (the message is identical for an
unknown address and a wrong password), `429` after too many attempts.

### `POST /api/auth/logout`

Authenticated. Invalidates the session and deletes the `JSESSIONID` cookie.

### `GET /api/auth/csrf`

Public. Returns `csrfToken`, `headerName` and `parameterName`.

### `GET /api/auth/me`

Authenticated. Returns the signed in user.

---

## Profile

### `GET /api/profile` / `PUT /api/profile`

Authenticated. The profile contains the display name and the optional contact channel. The
contact channel is only visible to the owner and to administrators.

---

## Reference data

### `GET /api/categories`

### `GET /api/locations`

```json
[{ "id": 1, "name": "Electronics", "description": "Phones, laptops, chargers ..." }]
```

---

## Items

### `POST /api/items`

Creates a lost or found report.

```json
{
  "type": "FOUND",
  "title": "Black folding umbrella",
  "categoryId": 1,
  "locationId": 4,
  "description": "Found a black folding umbrella with a wooden handle ...",
  "eventDate": "2026-05-01",
  "privateVerificationDetail": "The handle has a small scratch."
}
```

* `type` - `LOST` or `FOUND`, required
* `title` - 3 to 100 characters, required
* `description` - 10 to 2000 characters, required
* `eventDate` - the date of the loss / find, required
* `categoryId`, `locationId` - required and must exist
* `privateVerificationDetail` - optional, at most 1000 characters, **never returned by the
  public item endpoints**

`201 Created` -> the item (see below).

### `GET /api/items`

Search and filter. Query parameters:

| Parameter | Values |
| --- | --- |
| `q` | free text over title and description |
| `type` | `LOST`, `FOUND` |
| `status` | `OPEN`, `UNDER_REVIEW`, `CLAIMED`, `RETURNED`, `CLOSED`, `REJECTED` |
| `categoryId`, `locationId` | ids |
| `dateFrom`, `dateTo` | ISO dates (`yyyy-MM-dd`) |
| `sort` | `newest` (default), `oldest`, `event-date`, `event-date-asc`, `title` |
| `mine` | `true` for the reports of the signed in user |
| `page`, `size` | pagination |

Moderated (`REJECTED`) reports are only visible to their owner and to administrators.

### `GET /api/items/{id}`

```json
{
  "id": 12,
  "type": "FOUND",
  "title": "Black folding umbrella",
  "description": "Found a black folding umbrella ...",
  "categoryId": 1,
  "categoryName": "Electronics",
  "locationId": 4,
  "locationName": "Main Library",
  "eventDate": "2026-05-01",
  "status": "OPEN",
  "createdById": 3,
  "createdByDisplayName": "Student Name",
  "createdAt": "2026-05-01T10:05:00Z",
  "updatedAt": "2026-05-01T10:05:00Z",
  "hasImage": true,
  "imageUrl": "/api/items/12/image",
  "returnedAt": null,
  "closedAt": null,
  "owner": false,
  "canEdit": false,
  "canClose": false,
  "canClaim": true
}
```

`owner`, `canEdit`, `canClose` and `canClaim` describe what the **calling** user may do.

### `PUT /api/items/{id}`

Only the owner, only while the report is `OPEN`. The type cannot be changed.

### `POST /api/items/{id}/close`

The owner or an administrator. Sets the status to `CLOSED`.

### `GET /api/items/{id}/matches`

```json
[
  {
    "itemId": 19,
    "itemType": "FOUND",
    "title": "Black folding umbrella",
    "categoryName": "Electronics",
    "locationName": "Main Library",
    "eventDate": "2026-05-01",
    "score": 94,
    "band": "HIGH",
    "breakdown": {
      "categoryPoints": 30,
      "descriptionPoints": 26,
      "locationPoints": 20,
      "datePoints": 18,
      "total": 94,
      "explanation": "same category (+30), keyword overlap (+26), same location (+20), date proximity (+18)"
    },
    "disclaimer": "Suggested match only - a score is not proof of ownership. An administrator must verify every claim manually."
  }
]
```

### `GET /api/items/{id}/private-details`

Only the owner and administrators. Returns the private verification detail and the contact
channel of the reporter.

```json
{
  "itemId": 12,
  "privateVerificationDetail": "The handle has a small scratch.",
  "contactChannel": "student at college.edu",
  "createdByEmail": "student@college.edu"
}
```

### `POST /api/items/{id}/image`

Only the owner, only while the report is open. `multipart/form-data`, field `file`.
Accepted: `image/jpeg`, `image/png`, `image/webp`, `image/gif`, up to 5 MB. The file is also
checked against its magic bytes. The storage name is generated by the server.

### `GET /api/items/{id}/image`

Authenticated. Streams the image. The images are never exposed as a public static file.

---

## Claims

### `POST /api/items/{id}/claims`

Creates a private claim for a found report.

```json
{
  "explanation": "This is my umbrella, I lost it in the library ...",
  "verificationAnswers": "Blue fabric, wooden handle with a scratch."
}
```

* `explanation` - 20 to 2000 characters, required
* `verificationAnswers` - optional, at most 1000 characters

`201 Created` ->

```json
{
  "id": 5,
  "itemId": 19,
  "itemTitle": "Black folding umbrella",
  "claimantId": 8,
  "claimantDisplayName": "Claimant Name",
  "explanation": "This is my umbrella ...",
  "verificationAnswers": "Blue fabric, wooden handle with a scratch.",
  "status": "PENDING",
  "reviewedById": null,
  "reviewedByDisplayName": null,
  "reviewedAt": null,
  "reviewReason": null,
  "createdAt": "2026-05-01T11:00:00Z",
  "updatedAt": "2026-05-01T11:00:00Z",
  "canWithdraw": true
}
```

Errors: `403` (own report), `404` (unknown report), `409` (not claimable, duplicate claim,
rate limit).

### `GET /api/claims/mine`

The claims of the signed in user, with the confidential fields.

### `GET /api/claims/{id}`

Only the claimant and administrators.

### `POST /api/claims/{id}/withdraw`

Only the claimant, only while the claim is `PENDING`.

---

## Administration

All endpoints require the role `ADMIN`.

### `GET /api/admin/dashboard`

```json
{
  "counts": {
    "openLostReports": 12,
    "openFoundReports": 9,
    "pendingClaims": 3,
    "approvedClaims": 7,
    "returnedItems": 21,
    "closedReports": 4,
    "totalReports": 56,
    "totalUsers": 48
  },
  "pendingClaims": [],
  "recentlyUpdatedReports": []
}
```

### `GET /api/admin/claims`

Query parameters: `status`, `q` (title contains), `page`, `size`.

### `POST /api/admin/claims/{id}/approve`

Body: `{ "reason": "Ownership verified at the lost property office." }` (optional).
Sets the claim to `APPROVED` and the report to `CLAIMED`.

### `POST /api/admin/claims/{id}/reject`

Body: `{ "reason": "The description does not match." }` - **the reason is required**.
Sets the claim to `REJECTED`.

### `POST /api/admin/items/{id}/mark-returned`

Body: `{ "reason": "..." }`. The reason is required when no claim was approved (documented
administrative exception). Sets the report to `RETURNED` and records the timestamp and the
responsible administrator.

### `POST /api/admin/items/{id}/moderate`

```json
{ "action": "CLOSE", "reason": "Duplicate report." }
```

`action` is `CLOSE`, `REJECT` or `REOPEN`. A reason is required for `REJECT`.

### `GET /api/admin/audit-logs`

Query parameters: `page`, `size` (default 25).

```json
{
  "id": 91,
  "actorId": 1,
  "action": "CLAIM_APPROVED",
  "entityType": "CLAIM",
  "entityId": "5",
  "createdAt": "2026-05-01T12:00:00Z",
  "metadata": "{\"itemId\":\"19\"}"
}
```

---

## Health

### `GET /api/health`

Public.

```json
{
  "status": "UP",
  "application": "FindIT",
  "profile": "local",
  "imageStorage": "local:/home/user/FindIT/local-data/images",
  "time": "2026-05-01T12:00:00Z"
}
```

`GET /actuator/health` returns the standard Actuator health payload.
