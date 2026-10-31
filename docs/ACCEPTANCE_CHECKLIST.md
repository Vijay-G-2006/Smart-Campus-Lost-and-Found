# FindIT - manual acceptance checklist

Use this list to verify the running application, either locally or on Azure. Every step
refers to the PRD. The automated tests cover the same rules - this list verifies the
behaviour through the user interface.

## 0. Preparation

- [ ] `./gradlew bootRun` starts the application and `http://localhost:8080/api/health`
      answers with `"status":"UP"`
- [ ] The landing page explains the service and offers "Create an account" and "Sign in"
- [ ] `ADMIN_SEED_PASSWORD` was set before the first start, so an administrator exists

## 1. Registration and sign in (FR-01)

- [ ] A new account can be registered with a valid email and a password of at least 10
      characters
- [ ] A short password or an invalid email is rejected with a readable field message
- [ ] The same email address cannot be registered twice (message on the form, no technical
      detail)
- [ ] Sign in with correct credentials works and lands on the dashboard
- [ ] Sign in with a wrong password shows "Invalid email or password" - the same message
      appears for an unknown email address
- [ ] Sign out returns to the landing page and the protected pages are no longer accessible
- [ ] Opening a protected page without a session redirects to the sign in page

## 2. Roles (FR-01, 5.3)

- [ ] The navigation shows the "Admin" entry only for an administrator
- [ ] A normal user who opens `/api/admin/dashboard` directly receives `403`
- [ ] The registration form does not offer a role and the created account is a `USER`

## 3. Reports (FR-03)

- [ ] A lost report can be created with title, category, location, date and description
- [ ] A found report can be created with the same fields plus an optional private
      verification detail
- [ ] Missing or too short values are rejected with a message next to the field
- [ ] The report appears in "Search items" and in "My reports"
- [ ] The owner can edit the report while it is open; the edit button disappears afterwards
- [ ] The owner can close the report; the status badge changes to "Closed"
- [ ] A second user cannot edit the report (the backend answers `403`)

## 4. Search and filters (FR-04)

- [ ] A keyword search finds the report by title and by description
- [ ] The filters for type, category, location, date and status work together
- [ ] The result list is paginated and the page size is stable
- [ ] The public list never shows a private verification detail or a contact address

## 5. Images (FR-05)

- [ ] A JPG/PNG image of at most 5 MB can be uploaded by the owner
- [ ] A file that is not an accepted image type is rejected
- [ ] A text file renamed to `.png` is rejected (magic byte check)
- [ ] The image is displayed on the report page and is not reachable as a public static file

## 6. Match suggestions (FR-06)

- [ ] A lost report and a matching found report produce a suggestion
- [ ] The suggestion shows a score between 0 and 100, a band and the breakdown
      (category, keywords, location, date)
- [ ] The notice "suggested match only - a score is not proof of ownership" is visible
- [ ] A report of the own user is never suggested to that user

## 7. Claims (FR-07)

- [ ] A claim can be submitted for a found report with an explanation of at least 20
      characters
- [ ] A second claim of the same user for the same report is rejected
- [ ] The owner of the found report cannot claim it
- [ ] The claim appears in "My claims" with the status "Pending"
- [ ] The claim text is not visible to another user
- [ ] The claimant can withdraw a pending claim

## 8. Review and handover (FR-08, FR-09)

- [ ] The administrator sees the pending claim in the claim queue
- [ ] The administrator sees the explanation and the verification answers
- [ ] Approving the claim changes the status to "Approved" and the report to "Claimed"
- [ ] Rejecting without a reason is refused; with a reason the status becomes "Rejected"
- [ ] The administrator can mark the item as returned; the report shows "Returned"
- [ ] A returned report accepts no further claim
- [ ] The dashboard counters change accordingly
- [ ] The audit trail lists the registration, the decisions and the handover

## 9. Help, privacy and accessibility (FR-11)

- [ ] The help page explains the scoring, the privacy rules and the accessibility approach
- [ ] Every form control has a visible label
- [ ] The status badges carry a text label, not only a colour
- [ ] The layout works on a phone width (375 px) and on a desktop width
- [ ] The keyboard can reach every control and the focus is visible
- [ ] Empty states, loading states and error messages are shown where relevant

## 10. Azure readiness (optional, only with an Azure subscription)

- [ ] The application starts with `SPRING_PROFILES_ACTIVE=azure` and the documented settings
- [ ] The Flyway migrations run against Azure SQL without manual steps
- [ ] The images are stored in the private blob container
- [ ] `https://<app>.azurewebsites.net/api/health` answers with `"status":"UP"`
- [ ] The smoke test script passes against the deployed URL
