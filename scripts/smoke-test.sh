#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FindIT - end-to-end smoke test against a running instance.
#
# Usage:
#   ./scripts/smoke-test.sh [base-url]
#
# Default base URL: http://localhost:8080
#
# The script exercises the full acceptance workflow of the PRD:
#   register -> sign in -> create lost report -> create found report ->
#   match suggestions -> submit claim -> admin review -> mark returned.
#
# It needs a running application and an administrator account. The
# administrator credentials are read from the environment:
#   ADMIN_EMAIL / ADMIN_PASSWORD
# ---------------------------------------------------------------------------
set -u

BASE_URL="${1:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@findit.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
RUN_ID="$(date +%s)"

WORK_DIR="$(mktemp -d)"
ADMIN_COOKIES="$WORK_DIR/admin.cookies"

PASS=0
FAIL=0

cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

log()  { printf '%s\n' "$*"; }
ok()   { PASS=$((PASS+1)); printf '  [PASS] %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  [FAIL] %s\n' "$1"; }

csrf_token() {
  # $1 = cookie jar
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$1" | tail -n 1
}

api() {
  # api METHOD URL COOKIES [DATA] [EXTRA_HEADER]
  local method="$1" url="$2" cookies="$3" data="${4:-}" extra="${5:-}"
  local token
  token="$(csrf_token "$cookies")"
  if [ -n "$data" ]; then
    curl -sS -X "$method" "$BASE_URL$url" -b "$cookies" -c "$cookies" \
      -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $token" ${extra:+-H "$extra"} \
      -d "$data"
  else
    curl -sS -X "$method" "$BASE_URL$url" -b "$cookies" -c "$cookies" \
      -H "X-XSRF-TOKEN: $token" ${extra:+-H "$extra"}
  fi
}

api_status() {
  local method="$1" url="$2" cookies="$3" data="${4:-}"
  local token
  token="$(csrf_token "$cookies")"
  if [ -n "$data" ]; then
    curl -sS -o /dev/null -w '%{http_code}' -X "$method" "$BASE_URL$url" -b "$cookies" -c "$cookies" \
      -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $token" -d "$data"
  else
    curl -sS -o /dev/null -w '%{http_code}' -X "$method" "$BASE_URL$url" -b "$cookies" -c "$cookies" \
      -H "X-XSRF-TOKEN: $token"
  fi
}

api_form() {
  # api_form URL COOKIES FORM_BODY - application/x-www-form-urlencoded
  local url="$1" cookies="$2" data="$3"
  local token
  token="$(csrf_token "$cookies")"
  curl -sS -X POST "$BASE_URL$url" -b "$cookies" -c "$cookies" \
    -H "Content-Type: application/x-www-form-urlencoded" -H "X-XSRF-TOKEN: $token" -d "$data"
}

json_field() {
  # json_field JSON KEY  -> prints the raw value of the first matching key
  printf '%s' "$1" | grep -oE "\"$2\"[[:space:]]*:[[:space:]]*(\"[^\"]*\"|[0-9]+|true|false|null)" \
    | head -n 1 | sed -E "s/\"$2\"[[:space:]]*:[[:space:]]*//; s/^\"//; s/\"$//"
}

log "== FindIT smoke test against $BASE_URL =="

# --- 0. health -------------------------------------------------------------
HEALTH="$(curl -sS "$BASE_URL/api/health")"
if printf '%s' "$HEALTH" | grep -q '"status":"UP"'; then
  ok "health endpoint reports UP"
else
  bad "health endpoint did not report UP: $HEALTH"
fi

# --- 1. register three students -------------------------------------------
#  * OWNER  creates the lost report
#  * FINDER creates the found report
#  * CLAIMANT claims the found report
register_and_login() {
  local cookies="$1" email="$2" name="$3"
  curl -sS -c "$cookies" "$BASE_URL/api/auth/csrf" > /dev/null
  api POST /api/auth/register "$cookies" \
    "{\"email\":\"$email\",\"displayName\":\"$name\",\"password\":\"Sm0keTestPass123\"}" > /dev/null
  api_form /api/auth/login "$cookies" "email=$email&password=Sm0keTestPass123"
}

OWNER_COOKIES="$WORK_DIR/owner.cookies"
FINDER_COOKIES="$WORK_DIR/finder.cookies"
CLAIMANT_COOKIES="$WORK_DIR/claimant.cookies"
OWNER_EMAIL="owner.$RUN_ID@findit.local"
FINDER_EMAIL="finder.$RUN_ID@findit.local"
CLAIMANT_EMAIL="claimant.$RUN_ID@findit.local"

OWNER_LOGIN="$(register_and_login "$OWNER_COOKIES" "$OWNER_EMAIL" "Smoke Owner")"
FINDER_LOGIN="$(register_and_login "$FINDER_COOKIES" "$FINDER_EMAIL" "Smoke Finder")"
CLAIMANT_LOGIN="$(register_and_login "$CLAIMANT_COOKIES" "$CLAIMANT_EMAIL" "Smoke Claimant")"
if printf '%s' "$OWNER_LOGIN" | grep -q '"email"' \
   && printf '%s' "$FINDER_LOGIN" | grep -q '"email"' \
   && printf '%s' "$CLAIMANT_LOGIN" | grep -q '"email"'; then
  ok "registered and signed in three students"
else
  bad "could not register / sign in the students"
  exit 1
fi

# --- 2. reference data -----------------------------------------------------
CATEGORIES="$(api GET /api/categories "$OWNER_COOKIES")"
LOCATIONS="$(api GET /api/locations "$OWNER_COOKIES")"
CATEGORY_ID="$(json_field "$CATEGORIES" "id")"
LOCATION_ID="$(json_field "$LOCATIONS" "id")"
if [ -n "$CATEGORY_ID" ] && [ -n "$LOCATION_ID" ]; then
  ok "reference data loaded (category $CATEGORY_ID, location $LOCATION_ID)"
else
  bad "could not load categories / locations"
  exit 1
fi

# --- 3. create a lost report (owner) ---------------------------------------
LOST="$(api POST /api/items "$OWNER_COOKIES" "{\"type\":\"LOST\",\"title\":\"Smoke test blue umbrella\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"description\":\"A blue folding umbrella with a wooden handle was left in the smoke test area.\",\"eventDate\":\"2026-05-01\"}")"
LOST_ID="$(json_field "$LOST" "id")"
if [ -n "$LOST_ID" ]; then
  ok "created lost report $LOST_ID"
else
  bad "could not create lost report: $LOST"
  exit 1
fi

# --- 4. create a matching found report (finder) ---------------------------
FOUND="$(api POST /api/items "$FINDER_COOKIES" "{\"type\":\"FOUND\",\"title\":\"Blue folding umbrella found\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"description\":\"Found a blue folding umbrella with a wooden handle in the smoke test area near the entrance.\",\"eventDate\":\"2026-05-01\",\"privateVerificationDetail\":\"Handle has a small scratch.\"}")"
FOUND_ID="$(json_field "$FOUND" "id")"
if [ -n "$FOUND_ID" ]; then
  ok "created found report $FOUND_ID"
else
  bad "could not create found report: $FOUND"
  exit 1
fi

# --- 6. private verification detail must not leak --------------------------
if printf '%s' "$FOUND" | grep -q "Handle has a small scratch"; then
  bad "private verification detail leaked into the item response"
else
  ok "private verification detail is not part of the public item response"
fi

# --- 7. match suggestions --------------------------------------------------
MATCHES="$(api GET "/api/items/$LOST_ID/matches" "$OWNER_COOKIES")"
if printf '%s' "$MATCHES" | grep -q "\"itemId\":$FOUND_ID"; then
  ok "match suggestion contains the found report"
  SCORE="$(printf '%s' "$MATCHES" | grep -oE '"score":[0-9]+' | head -n 1 | cut -d: -f2)"
  log "         score: ${SCORE:-?} / 100"
else
  bad "no match suggestion for the found report: $MATCHES"
fi
if printf '%s' "$MATCHES" | grep -q "not proof of ownership"; then
  ok "match suggestion carries the 'suggestion, not proof' notice"
else
  bad "match suggestion is missing the disclaimer"
fi

# --- 8. claim --------------------------------------------------------------
CLAIM="$(api POST "/api/items/$FOUND_ID/claims" "$CLAIMANT_COOKIES" \
  '{"explanation":"This is my umbrella, I lost it during the smoke test and it has a scratch on the handle.","verificationAnswers":"Blue fabric, wooden handle."}')"
CLAIM_ID="$(json_field "$CLAIM" "id")"
if [ -n "$CLAIM_ID" ]; then
  ok "submitted claim $CLAIM_ID"
else
  bad "could not submit claim: $CLAIM"
fi

# duplicate claim must be rejected
DUP_STATUS="$(api_status POST "/api/items/$FOUND_ID/claims" "$CLAIMANT_COOKIES" \
  '{"explanation":"This is my umbrella, I lost it during the smoke test and it has a scratch on the handle.","verificationAnswers":"Blue fabric, wooden handle."}')"
if [ "$DUP_STATUS" = "409" ]; then
  ok "duplicate claim is rejected with 409"
else
  bad "duplicate claim returned HTTP $DUP_STATUS (expected 409)"
fi

# --- 9. claim privacy ------------------------------------------------------
OWN_CLAIM_STATUS="$(api_status GET "/api/claims/$CLAIM_ID" "$CLAIMANT_COOKIES")"
if [ "$OWN_CLAIM_STATUS" = "200" ]; then
  ok "claimant can read their own claim"
else
  bad "reading own claim returned HTTP $OWN_CLAIM_STATUS"
fi

OTHER_CLAIM_STATUS="$(api_status GET "/api/claims/$CLAIM_ID" "$FINDER_COOKIES")"
if [ "$OTHER_CLAIM_STATUS" = "403" ] || [ "$OTHER_CLAIM_STATUS" = "404" ]; then
  ok "another user cannot read the claim (HTTP $OTHER_CLAIM_STATUS)"
else
  bad "reading a foreign claim returned HTTP $OTHER_CLAIM_STATUS (expected 403/404)"
fi

PRIVATE_STATUS="$(api_status GET "/api/items/$FOUND_ID/private-details" "$CLAIMANT_COOKIES")"
if [ "$PRIVATE_STATUS" = "403" ] || [ "$PRIVATE_STATUS" = "404" ]; then
  ok "private verification details are not readable by other users (HTTP $PRIVATE_STATUS)"
else
  bad "private details returned HTTP $PRIVATE_STATUS (expected 403/404)"
fi

OWNER_PRIVATE_STATUS="$(api_status GET "/api/items/$FOUND_ID/private-details" "$FINDER_COOKIES")"
if [ "$OWNER_PRIVATE_STATUS" = "200" ]; then
  ok "the owner can read the private details of their own report"
else
  bad "owner private details returned HTTP $OWNER_PRIVATE_STATUS (expected 200)"
fi

# --- 10. normal user must not reach admin endpoints ----------------------
ADMIN_STATUS="$(api_status GET /api/admin/dashboard "$OWNER_COOKIES")"
if [ "$ADMIN_STATUS" = "403" ]; then
  ok "normal user is denied on /api/admin/dashboard (403)"
else
  bad "/api/admin/dashboard returned HTTP $ADMIN_STATUS for a normal user (expected 403)"
fi

# a user must not be able to edit somebody else's report
EDIT_STATUS="$(api_status PUT "/api/items/$LOST_ID" "$FINDER_COOKIES" \
  "{\"title\":\"Hijacked title\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"description\":\"Trying to edit a report that belongs to somebody else.\",\"eventDate\":\"2026-05-01\"}")"
if [ "$EDIT_STATUS" = "403" ]; then
  ok "a user cannot edit another user's report (403)"
else
  bad "editing a foreign report returned HTTP $EDIT_STATUS (expected 403)"
fi

# --- 11. administrator reviews the claim ----------------------------------
if [ -z "$ADMIN_PASSWORD" ]; then
  log "  [SKIP] administrator checks - ADMIN_PASSWORD is not set"
else
  curl -sS -c "$ADMIN_COOKIES" "$BASE_URL/api/auth/csrf" > /dev/null
  ADMIN_LOGIN="$(api_form /api/auth/login "$ADMIN_COOKIES" "email=$ADMIN_EMAIL&password=$ADMIN_PASSWORD")"
  if printf '%s' "$ADMIN_LOGIN" | grep -q '"role":"ADMIN"'; then
    ok "administrator signed in"
    DASH="$(api GET /api/admin/dashboard "$ADMIN_COOKIES")"
    if printf '%s' "$DASH" | grep -q '"counts"'; then
      ok "administrator dashboard returns counts"
    else
      bad "administrator dashboard failed: $DASH"
    fi

    APPROVED="$(api POST "/api/admin/claims/$CLAIM_ID/approve" "$ADMIN_COOKIES" '{"reason":"Ownership verified by the campus office."}')"
    if printf '%s' "$APPROVED" | grep -q '"status":"APPROVED"'; then
      ok "administrator approved the claim"
    else
      bad "claim approval failed: $APPROVED"
    fi

    RETURNED="$(api POST "/api/admin/items/$FOUND_ID/mark-returned" "$ADMIN_COOKIES" '{"reason":"Handed over at the lost property office."}')"
    if printf '%s' "$RETURNED" | grep -q '"status":"RETURNED"'; then
      ok "administrator marked the item as returned"
    else
      bad "mark returned failed: $RETURNED"
    fi

    AFTER_STATUS="$(api_status POST "/api/items/$FOUND_ID/claims" "$CLAIMANT_COOKIES" \
      '{"explanation":"Another attempt to claim this returned item for the smoke test.","verificationAnswers":"none"}')"
    if [ "$AFTER_STATUS" = "409" ]; then
      ok "returned item does not accept new claims (409)"
    else
      bad "claim on a returned item returned HTTP $AFTER_STATUS (expected 409)"
    fi
  else
    bad "administrator sign in failed: $ADMIN_LOGIN"
  fi
fi

log ""
log "== Result: $PASS passed, $FAIL failed =="
[ "$FAIL" -eq 0 ]
