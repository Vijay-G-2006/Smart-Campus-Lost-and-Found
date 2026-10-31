# Product Requirements Document (PRD)
## FindIT — Smart Campus Lost and Found Management System

**Document version:** 1.0  
**Status:** Implementation baseline  
**Project type:** Academic mini-project / Software Construction  
**Target platform:** Responsive web application  
**Cloud platform:** Microsoft Azure  
**Primary stack:** Java + Spring Boot REST API, HTML/CSS/JavaScript + Bootstrap, Azure SQL Database, Azure Blob Storage

---

## 1. Executive Summary

FindIT is a cloud-based campus lost-and-found system that helps students and staff report missing belongings, publish found items, discover possible matches, submit ownership claims, and track an item’s return. It replaces scattered messages, noticeboards, and informal chats with one searchable, auditable workflow.

The application must be useful as a student mini-project while demonstrating software-construction practices: requirements traceability, modular design, version control, automated testing, secure access control, CI/CD readiness, monitoring, and cloud deployment.

**Important product rule:** A match score is only a suggestion. It must never be treated as proof of ownership. Claims must be verified through a safe, privacy-conscious process by an authorized administrator.

## 2. Problem Statement

Campus lost-and-found processes are often fragmented. A person who loses an item may not know where to check; a person who finds an item may not know whom to contact; and public posts can expose personal information. FindIT provides a single searchable system with controlled visibility, claims, administrative review, and status tracking.

## 3. Goals and Success Criteria

### 3.1 Goals
1. Let registered users report lost and found items.
2. Let users search and filter visible reports.
3. Suggest possible matches using explainable rule-based scoring.
4. Let a user submit a private claim for a found item.
5. Let an administrator review claims and manage item handover status.
6. Provide an accessible, responsive interface.
7. Store structured data in Azure SQL Database and item images in private Azure Blob Storage.
8. Include automated tests, setup documentation, environment configuration, and CI workflow.
9. Make the application runnable locally without requiring Azure credentials; Azure services must be configurable and the local development path must be documented.

### 3.2 Success criteria
- A user can sign in, create a lost/found report, search reports, and view a report detail page.
- The application validates required fields and rejects invalid inputs.
- A found item can receive a claim; only an authorized admin can approve/reject it.
- An approved claim can lead to an item marked returned, with relevant status history.
- Users cannot view private claim answers or admin-only data belonging to others.
- Backend tests pass and the frontend/backend build succeeds.
- No secrets are committed to source control.
- A fresh developer can follow the README to run the project.

## 4. Scope

### 4.1 MVP — required
- Registration and login with secure password hashing.
- Roles: `USER` and `ADMIN`.
- User profile basics.
- Create, edit (own open reports), view, and close lost/found reports.
- Item categories and campus locations.
- Search and filters.
- Image upload with size/type validation and private storage abstraction.
- Rule-based match suggestions.
- Private claim submission and admin review.
- Item status workflow and return tracking.
- Admin dashboard with queues and basic counts.
- Input validation, authorization, error handling, logging, tests, seed/demo data, README, environment example, and GitHub Actions CI.
- Azure-ready configuration for App Service, Azure SQL, Blob Storage, and Application Insights.

### 4.2 Optional only after MVP works
- Microsoft Entra ID sign-in.
- Azure Functions for asynchronous match recalculation.
- Email notifications.
- Advanced analytics or machine-learning matching.
- Maps, chat, mobile app, or external identity verification.

Do not delay the MVP to implement optional features.

### 4.3 Out of scope
- Publicly displaying a claimant’s contact details.
- Automated final ownership decisions.
- Payments, delivery logistics, or campus-wide identity integrations that require unavailable credentials.
- Scraping social media or other external systems.
- Guaranteeing actual Azure deployment without an Azure subscription, permissions, and valid credentials.

## 5. Users and Roles

### 5.1 Guest (optional; read-only)
Can view only explicitly public, non-sensitive item summaries and use basic search. Cannot report, claim, or view private information. If guest access complicates the MVP, require login for all pages except the landing page.

### 5.2 Registered user (`USER`)
Can:
- Register and sign in.
- Create lost/found reports.
- Edit or close their own reports while they are open.
- Browse/search reports.
- View suggested matches.
- Submit claims for eligible found items.
- View their own reports and claim status.

Cannot:
- Read another person’s private claims or verification answers.
- Approve claims.
- change roles or access admin routes.
- Mark items returned without admin authorization.

### 5.3 Administrator (`ADMIN`)
Can:
- View and manage reports and claims.
- Approve or reject claims with a recorded reason.
- Mark a verified item as returned and record handover status.
- Manage categories/locations if included.
- View basic dashboard statistics and audit history.
- Disable or moderate inappropriate reports.

Admin privileges must be enforced by the backend, not just hidden in the frontend.

## 6. Core User Journeys

### 6.1 Report a lost item
1. User signs in.
2. Opens “Report Lost Item”.
3. Enters title, category, description, approximate date, location, and optional image.
4. Backend validates the request and stores the report.
5. User sees confirmation and can track its status.
6. Matching service checks compatible found-item reports and shows suggestions.

### 6.2 Report a found item
1. User signs in and opens “Report Found Item”.
2. Enters item details, found date/location, and optional image.
3. User may include a non-public identifying detail for verification, which must not appear in public listing responses.
4. The report becomes searchable and matching suggestions are calculated.

### 6.3 Search and view
1. User searches by keyword and filters by item type, category, date, and location.
2. Results show only fields intended for general visibility.
3. User opens a detail page and sees status, description, date/location information, and any permitted image.
4. Contact information and verification-only details remain private.

### 6.4 Submit a claim
1. A signed-in user opens an eligible found-item report.
2. Selects “Claim this item”.
3. Supplies a private explanation and optional proof/verification answers.
4. The backend creates a pending claim and notifies/displays it to the admin queue.
5. The claimant can see only their own claim status.

### 6.5 Review and return
1. Admin reviews the claim and checks evidence through an appropriate campus process.
2. Admin approves or rejects the claim and records a reason.
3. On approval, the admin coordinates a safe handover without exposing private data publicly.
4. Admin marks the item `RETURNED`; the claim and item history are updated.
5. The report is no longer eligible for new claims.

## 7. Functional Requirements

Priority labels: **P0** = essential MVP; **P1** = important; **P2** = optional.

### FR-01 Authentication and authorization (P0)
- Provide registration, login, logout, and current-user endpoint.
- Hash passwords using a modern adaptive password encoder (Spring Security BCrypt or Argon2 where supported); never store plaintext passwords.
- Use secure session/cookie or token-based authentication with CSRF protections appropriate to the chosen mechanism.
- Enforce role checks on every protected backend endpoint.
- Validate credentials and return safe, non-revealing errors.
- Seed an admin only through explicit environment configuration or a documented local development seed process. Never hard-code a production admin password.

### FR-02 Profiles (P1)
- Show and update a user’s display name and optional campus contact channel.
- Only the owner and authorized admins can access private profile details.

### FR-03 Lost/found item reports (P0)
Each report must include:
- Unique ID.
- Type: `LOST` or `FOUND`.
- Title.
- Category.
- Description.
- Approximate event date.
- Campus location.
- Status.
- Creator ID.
- Created/updated timestamps.
- Optional image reference.
- Optional private verification detail for found items, never included in public DTOs.

Validation:
- Title, category, description, date, and location are required unless explicitly documented otherwise.
- Set sensible limits (for example title 3–100 characters, description 10–2000 characters).
- Reject unsupported image types and excessive file size.
- Do not trust user-provided IDs, role fields, or ownership fields.
- A user may edit/close only their own eligible reports; admins can moderate any report.

Suggested item statuses: `OPEN`, `UNDER_REVIEW`, `CLAIMED`, `RETURNED`, `CLOSED`, `REJECTED`. Document and enforce allowed transitions.

### FR-04 Search and filters (P0)
- Search titles/descriptions with safe parameterized queries.
- Filter by lost/found, category, location, date range, and status.
- Use pagination and stable sorting.
- Public result DTOs must omit private claim data, private verification details, credentials, and unnecessary personal data.

### FR-05 Image handling (P0)
- Store images in Azure Blob Storage in production, with a local-development storage option or a clean no-image mode.
- Use a private container by default. Do not make the container public.
- Use server-generated blob names; validate MIME type, extension, and size; never trust the original filename as a storage path.
- Provide images through short-lived authorized access or a backend-mediated endpoint. Do not expose account keys or connection strings.
- If local storage is used, keep uploads outside executable/static code paths and document cleanup.
- Make upload storage configurable behind an interface so the application can run without Azure credentials.

### FR-06 Match suggestions (P0)
- Compare open lost reports with open found reports.
- Implement a transparent, deterministic scoring algorithm:
  - Category similarity: 30 points.
  - Description/keyword similarity: up to 30 points.
  - Location similarity: up to 20 points.
  - Date proximity: up to 20 points.
  - Maximum score: 100.
- Normalize text, handle missing values, and document the scoring rules.
- Show a score band or score plus a clear “suggested match, not proof” notice.
- Never automatically approve a claim or expose hidden verification details.
- Provide unit tests for score boundaries and common cases.
- Matching may be computed on request or after report changes; choose the simpler reliable approach for MVP.

### FR-07 Claims (P0)
- A user may claim an eligible `FOUND` report that is not returned/closed.
- Prevent duplicate active claims by the same user for the same item.
- Keep claim explanation and verification answers private.
- Claim statuses: `PENDING`, `APPROVED`, `REJECTED`, `WITHDRAWN`.
- Claimant can view their own claim and status; admins can review all claims.
- Record reviewer, review timestamp, and rejection/approval reason where appropriate.
- Enforce claim status transitions in backend services.

### FR-08 Handover and return tracking (P0)
- Only admins can mark an item returned.
- Require an approved claim before marking a claimed item returned, unless an admin documents a valid exception.
- Record returned timestamp and responsible admin.
- Once returned, prevent new claims and ordinary edits that would corrupt the history.

### FR-09 Admin dashboard (P1)
- Counts for open lost reports, open found reports, pending claims, approved claims, returned items.
- Lists for pending claims and recently updated reports.
- Admin actions must have confirmation and show success/error feedback.

### FR-10 Audit events (P1)
Record security- and workflow-relevant events such as report moderation, claim decisions, role changes, and return status. Do not log passwords, tokens, private verification answers, or full secrets.

### FR-11 Help, privacy, and accessibility (P1)
- Include brief help text, privacy notice, empty states, loading states, and error messages.
- Use semantic HTML, keyboard-accessible controls, labels, and sufficient contrast.
- Do not show private contact details or exact sensitive information unnecessarily.

## 8. Non-Functional Requirements

### Security
- Follow least privilege and secure defaults.
- Validate inputs on the server.
- Use parameterized database operations/JPA safely; avoid SQL injection.
- Encode output appropriately; avoid unsafe HTML rendering.
- Apply authentication and authorization checks on the backend.
- Configure CORS narrowly if frontend and backend are separate.
- Keep secrets in environment variables or Azure App Service settings/Key Vault where available.
- Do not commit `.env`, credentials, service-principal secrets, connection strings, or real user data.
- Use HTTPS in production.
- Add rate limiting or basic abuse protection to authentication and claim endpoints if practical.
- Provide safe error responses; detailed stack traces belong only in development logs.
- Uploads must not permit executable content.
- Do not use real sensitive data in seed examples.

### Reliability and maintainability
- Layer the backend into controllers, services, repositories, entities, DTOs, validation, security, and configuration.
- Keep business rules out of controllers.
- Use migrations (Flyway preferred) rather than relying on automatic destructive schema updates.
- Provide deterministic seed data for local demo.
- Include centralized exception handling and structured logs.
- Keep local mode usable without Azure credentials.

### Performance
- Paginate item searches.
- Add indexes for frequently filtered columns.
- Avoid loading all items to calculate search results.
- MVP target: typical search requests should feel responsive for a small campus demo dataset; do not claim production-scale load testing unless actually performed.

### Accessibility and responsive design
- Support common mobile and desktop widths.
- Forms need visible labels, validation feedback, and keyboard access.
- Use responsive Bootstrap layout and avoid color-only status cues.

## 9. Technical Architecture

### Frontend
- HTML5, CSS3, JavaScript, Bootstrap 5.
- Responsive pages: landing/login/register, dashboard, report form, search/results, item details, my reports, my claims, admin dashboard, admin claim review, help/privacy.
- Use a small modular JavaScript structure; avoid unnecessary frontend frameworks.

### Backend
- Java LTS supported by the chosen Spring Boot release; Spring Boot REST API.
- Spring Web, Spring Security, Spring Data JPA, Bean Validation, Flyway, Azure SDK for Blob Storage where needed.
- OpenAPI/Swagger UI may be included for development.
- JUnit 5 and Spring Boot Test; use Mockito and Testcontainers/H2 where appropriate.
- Use DTOs instead of exposing persistence entities directly.

### Database
Production: Azure SQL Database.  
Local development: a documented local profile (H2 for easy setup or SQL Server-compatible local database if selected). Ensure migrations and behavior are compatible with Azure SQL; explain any local differences.

Suggested tables:
- `users`: id, display_name, email, password_hash, role, active, created_at, updated_at.
- `categories`: id, name, active.
- `locations`: id, name, description, active.
- `items`: id, type, title, description, category_id, location_id, event_date, status, created_by, image_blob_name, private_verification_detail, created_at, updated_at, returned_at, returned_by.
- `claims`: id, item_id, claimant_id, explanation, verification_answers, status, reviewed_by, reviewed_at, review_reason, created_at, updated_at.
- `matches`: id, lost_item_id, found_item_id, score, score_breakdown_json (or normalized fields), calculated_at.
- `audit_logs` (P1): id, actor_id, action, entity_type, entity_id, created_at, safe_metadata_json.

Use appropriate foreign keys, unique constraints, indexes, UTC timestamps, and migration scripts. Never return private columns in general item DTOs.

### Azure services
- **Azure App Service:** host Spring Boot backend and optionally serve frontend assets from the same app for simpler deployment.
- **Azure SQL Database:** production relational database.
- **Azure Blob Storage:** private item-image container.
- **Application Insights:** telemetry and diagnostic monitoring.
- **GitHub Actions:** CI build/test and deployment workflow template.
- **Azure Key Vault:** optional production secret storage if available.
- **Azure Functions:** optional future background matching; not required for MVP.
- **Microsoft Entra ID:** optional enterprise login; not required for MVP.

Architecture must support:
1. Local development with no Azure credentials.
2. Azure development/production through environment variables and a documented profile.
3. A clean deployment path that does not silently create billable resources.

### Configuration contract
Provide `.env.example` or an equivalent safe template with placeholders, never real secrets. Configuration should include only values actually used, for example:
- `SPRING_PROFILES_ACTIVE`
- `APP_BASE_URL`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `AZURE_STORAGE_CONNECTION_STRING` (optional; do not commit)
- `AZURE_STORAGE_ACCOUNT_NAME` (if using managed identity)
- `AZURE_STORAGE_CONTAINER`
- `ADMIN_SEED_EMAIL` (local/dev only)
- `ADMIN_SEED_PASSWORD` (local/dev only; require user to set)
- `APPLICATIONINSIGHTS_CONNECTION_STRING` (optional)
- `SESSION_COOKIE_SECURE` or equivalent security setting.

Prefer managed identity for Azure-to-Azure access where feasible. Never print secret values in logs.

## 10. Data and API Design

Use REST conventions, DTOs, validation, pagination, and consistent error responses. Exact route names can vary if documented.

Suggested endpoints:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/categories`
- `GET /api/locations`
- `POST /api/items`
- `GET /api/items`
- `GET /api/items/{id}`
- `PUT /api/items/{id}`
- `POST /api/items/{id}/close`
- `GET /api/items/{id}/matches`
- `POST /api/items/{id}/claims`
- `GET /api/claims/mine`
- `GET /api/admin/claims?status=PENDING`
- `POST /api/admin/claims/{id}/approve`
- `POST /api/admin/claims/{id}/reject`
- `POST /api/admin/items/{id}/mark-returned`
- `GET /api/admin/dashboard`
- `GET /api/admin/audit-logs` (P1)

For each endpoint, document authentication, role requirements, validation, response shape, and common error codes. Do not rely on the frontend for authorization.

## 11. UI / UX Requirements

Visual direction: clean, modern campus utility dashboard. Use a restrained palette, readable typography, consistent spacing, and clear status badges. Avoid excessive animation.

Required screens:
1. Landing page with concise explanation and sign-in/register actions.
2. Login and registration.
3. User dashboard with quick actions and recent activity.
4. Create/edit lost or found report.
5. Search and filter page with result cards/list.
6. Item detail page with status and suggested matches.
7. My reports.
8. My claims and claim details.
9. Admin dashboard.
10. Admin claim review and report moderation.
11. Help/privacy page.

Each screen should include sensible loading, empty, validation, success, and failure states. Do not include fake buttons or controls that do nothing. If a feature is intentionally deferred, label it as such.

## 12. Testing and Acceptance Criteria

### Required automated tests
- Unit tests for match scoring.
- Validation tests for report creation.
- Authentication and authorization tests.
- A normal user cannot access admin endpoints.
- A user cannot edit another user’s report.
- A user cannot read another user’s claim or private verification detail.
- Claim submission rejects invalid/duplicate claims.
- Admin approval/rejection changes status correctly.
- Returned items cannot receive new claims.
- API integration tests for primary workflow.
- Build and test commands documented.

### End-to-end acceptance scenario
1. Start the app locally using documented instructions.
2. Register/sign in as a normal user.
3. Create a lost report.
4. Create a found report with overlapping category/location/date/description.
5. Confirm the matching service suggests a match with an understandable score breakdown.
6. Submit a claim for the found item.
7. Sign in as an admin and review the claim.
8. Approve or reject the claim.
9. For an approved claim, mark the item returned.
10. Confirm public status and claim privacy rules remain correct.

### Definition of Done
- MVP features are implemented and wired end-to-end.
- Application builds successfully.
- Automated tests pass, or any failures are explicitly reported with cause.
- Frontend and backend communicate correctly.
- Local startup works using the README.
- Azure configuration is documented and does not require committed credentials.
- CI workflow exists and runs build/tests.
- No known critical authorization/privacy defect remains.
- A final implementation report lists completed work, commands run, test outcomes, any limitations, and exact manual steps the user must perform.

## 13. Software Construction / Course Traceability

- **Process and Agile:** backlog, prioritized MVP, incremental implementation, definition of done.
- **Requirements engineering:** functional/non-functional requirements, user stories, acceptance criteria.
- **Design:** architecture, use cases, sequence/workflow, ERD/database schema, API contract.
- **Testing and quality:** unit, integration, authorization, validation, and end-to-end workflow tests.
- **Configuration management:** Git, branches, commits, `.gitignore`, versioned migrations, GitHub Actions.
- **Maintenance and DevOps:** release notes, environment configuration, deployment guide, logging/monitoring.
- **Security:** least privilege, private claims, secure password handling, secrets management, safe uploads.

## 14. Suggested User Stories

- As a student, I want to report a lost item so that other campus users can help identify it.
- As a person who found an item, I want to report it without exposing private information.
- As a user, I want to search by category, location, date, and keywords.
- As a user, I want to see possible matches so that I can focus on relevant reports.
- As a claimant, I want to submit private evidence and track my claim.
- As an administrator, I want to review claims and record a decision.
- As an administrator, I want to track handover and mark an item returned.
- As a developer, I want repeatable local setup and automated tests.
- As a project evaluator, I want clear documentation and traceable requirements.

## 15. Risks and Mitigations

- **False match:** label scores as suggestions; require human verification.
- **Fraudulent claims:** keep identifying details private and require admin review.
- **Privacy exposure:** separate public DTOs from private entities; test authorization.
- **Cloud cost surprises:** no automatic resource creation/deployment; document pricing checks and require explicit approval.
- **Missing Azure credentials:** local profile and storage abstraction must still run.
- **Over-scoping:** implement MVP first; optional integrations only after acceptance tests pass.
- **Deployment mismatch:** use migrations, environment-specific config, and document exact Azure prerequisites.
- **Secrets leakage:** use placeholders, ignore local environment files, and never print secrets.

## 16. Delivery Plan

Recommended order:
1. Repository inspection and implementation plan.
2. Backend skeleton, schema migrations, auth, roles.
3. Item report CRUD, search/filter, frontend pages.
4. Private image storage abstraction and optional Azure Blob integration.
5. Matching engine and claims workflow.
6. Admin dashboard, audit events, return tracking.
7. Automated tests, accessibility/error states, seed/demo data.
8. README, `.env.example`, CI workflow, Azure deployment guide.
9. Run all available checks and produce a completion report.

## 17. Final Deliverables

- Source code for frontend and backend.
- Database migration scripts and safe local seed data.
- Automated tests.
- `.gitignore` and `.env.example`.
- README with local setup, configuration, test commands, and troubleshooting.
- Architecture and API documentation.
- GitHub Actions CI workflow.
- Azure deployment instructions and optional deployment workflow template.
- Short demo walkthrough and acceptance-test checklist.
- Final report distinguishing what is implemented, what was actually tested, what was deployed, and what still requires user action.
