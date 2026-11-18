#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
RUN_ID="$(date +%s)"
USER_EMAIL="demo-user-${RUN_ID}@example.com"
UPDATED_USER_EMAIL="demo-user-updated-${RUN_ID}@example.com"
USER_PASSWORD="demo-password-123"

ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

TMP_DIR="$(mktemp -d)"
USER_COOKIES="${TMP_DIR}/user.cookies"
ADMIN_COOKIES="${TMP_DIR}/admin.cookies"
BODY_FILE="${TMP_DIR}/body"

trap 'rm -rf "$TMP_DIR"' EXIT

headline() {
    printf "\n\033[1;36m============================================================\033[0m\n"
    printf "\033[1;36m%s\033[0m\n" "$1"
    printf "\033[1;36m============================================================\033[0m\n"
}

step() {
    printf "\n\033[1;33m--- %s ---\033[0m\n" "$1"
}

pretty_body() {
    if [[ ! -s "$BODY_FILE" ]]; then
        echo "(empty response body)"
        return
    fi

    if python3 -m json.tool "$BODY_FILE" >/dev/null 2>&1; then
        python3 -m json.tool "$BODY_FILE"
    else
        cat "$BODY_FILE"
        echo
    fi
}

request() {
    local method="$1"
    local url="$2"
    shift 2

    local status
    status="$(curl -sS -o "$BODY_FILE" -w "%{http_code}" -X "$method" "$@" "${BASE_URL}${url}")"

    printf "\033[1m%s %s -> HTTP %s\033[0m\n" "$method" "$url" "$status"
    pretty_body
}

request_quiet_body() {
    local method="$1"
    local url="$2"
    shift 2

    local status
    status="$(curl -sS -o "$BODY_FILE" -w "%{http_code}" -X "$method" "$@" "${BASE_URL}${url}")"

    printf "\033[1m%s %s -> HTTP %s\033[0m\n" "$method" "$url" "$status"
}

json_value() {
    local path="$1"
    python3 - "$path" "$BODY_FILE" <<'PY'
import json
import sys

path = sys.argv[1].split(".")
filename = sys.argv[2]

with open(filename, encoding="utf-8") as f:
    value = json.load(f)

for part in path:
    if part.isdigit():
        value = value[int(part)]
    else:
        value = value[part]

if value is None:
    print("")
elif isinstance(value, (dict, list)):
    print(json.dumps(value))
else:
    print(value)
PY
}

get_csrf() {
    local cookie_file="$1"
    local status

    status="$(curl -sS -c "$cookie_file" -b "$cookie_file" -o "$BODY_FILE" -w "%{http_code}" "${BASE_URL}/api/auth/csrf")"

    if [[ "$status" != "200" ]]; then
        echo "Could not obtain CSRF token (HTTP $status)." >&2
        pretty_body >&2
        exit 1
    fi

    json_value "token"
}

headline "0. SERVER + API DOCUMENTATION"

step "Check server"
request_quiet_body GET "/api/articles?page=0&size=1"

step "OpenAPI JSON"
request_quiet_body GET "/v3/api-docs"

step "Swagger UI"
request_quiet_body GET "/swagger-ui.html"

headline "1. PUBLIC ARTICLE ENDPOINTS"

step "UC1 - View news feed"
request GET "/api/articles?page=0&size=3"

EXISTING_ARTICLE_ID="$(json_value "content.0.id" 2>/dev/null || true)"

if [[ -n "$EXISTING_ARTICLE_ID" ]]; then
    step "UC2 - View one article"
    request GET "/api/articles/${EXISTING_ARTICLE_ID}"
fi

step "UC3 - Search/filter articles"
request GET "/api/articles/search?keyword=Demo&page=0&size=5"

headline "2. AUTHENTICATION + OWN ACCOUNT"

step "Get CSRF token"
USER_CSRF="$(get_csrf "$USER_COOKIES")"
echo "CSRF token obtained."

step "UC4 - Register user"
request POST "/api/auth/register" \
    -c "$USER_COOKIES" -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}" \
    -H "Content-Type: application/json" \
    --data-binary "{\"email\":\"${USER_EMAIL}\",\"password\":\"${USER_PASSWORD}\"}"

step "UC5 - Log in"
request POST "/api/auth/login" \
    -c "$USER_COOKIES" -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "email=${USER_EMAIL}" \
    --data-urlencode "password=${USER_PASSWORD}"

USER_CSRF="$(get_csrf "$USER_COOKIES")"

step "UC6 - View own account"
request GET "/api/accounts/me" -b "$USER_COOKIES"

step "UC7 - Modify own account"
request PUT "/api/accounts/me" \
    -c "$USER_COOKIES" -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}" \
    -H "Content-Type: application/json" \
    --data-binary "{\"email\":\"${UPDATED_USER_EMAIL}\",\"password\":null}"

headline "3. USER ARTICLE ENDPOINTS"

step "UC13 - Add own article"
request POST "/api/articles" \
    -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}" \
    -H "Content-Type: application/json" \
    --data-binary "{\"title\":\"Demo user article ${RUN_ID}\",\"content\":\"Created by the endpoint demo script.\",\"url\":\"https://example.com/demo-user-${RUN_ID}\",\"category\":\"Demo\",\"publishedAt\":\"2026-11-18T12:00:00Z\"}"

USER_ARTICLE_ID="$(json_value "id")"
echo "Created user article: ${USER_ARTICLE_ID}"

step "View newly created article"
request GET "/api/articles/${USER_ARTICLE_ID}"

step "UC14 - Edit own article"
request PUT "/api/articles/${USER_ARTICLE_ID}" \
    -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}" \
    -H "Content-Type: application/json" \
    --data-binary "{\"title\":\"Updated demo user article ${RUN_ID}\",\"content\":\"This article was updated through UC14.\",\"url\":\"https://example.com/demo-user-${RUN_ID}\",\"category\":\"Demo\",\"publishedAt\":\"2026-11-18T12:00:00Z\"}"

headline "4. FAVOURITES"

step "UC9 - Save favourite"
request POST "/api/favourites/${USER_ARTICLE_ID}" \
    -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}"

step "UC10 - View own favourites"
request GET "/api/favourites?page=0&size=20" -b "$USER_COOKIES"

step "UC11 - Delete favourite"
request DELETE "/api/favourites/${USER_ARTICLE_ID}" \
    -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}"

headline "5. ADMIN ENDPOINTS"

if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
    echo "ADMIN_EMAIL / ADMIN_PASSWORD were not provided."
    echo "Skipping admin endpoints."
    echo
    echo "Run again with:"
    echo '  ADMIN_EMAIL="admin@example.com" ADMIN_PASSWORD="..." ./demo-endpoints.sh'
else
    step "Log in as existing administrator"
    ADMIN_CSRF="$(get_csrf "$ADMIN_COOKIES")"

    request POST "/api/auth/login" \
        -c "$ADMIN_COOKIES" -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        --data-urlencode "email=${ADMIN_EMAIL}" \
        --data-urlencode "password=${ADMIN_PASSWORD}"

    ADMIN_CSRF="$(get_csrf "$ADMIN_COOKIES")"

    step "UC12 - Create news source"
    request POST "/api/admin/newssources/create" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}" \
        -H "Content-Type: application/json" \
        --data-binary "{\"name\":\"Demo Source ${RUN_ID}\",\"url\":\"https://example.com/demo-source-${RUN_ID}\"}"

    SOURCE_ID="$(json_value "id")"
    echo "Created source: ${SOURCE_ID}"

    step "UC12 - List news sources"
    request GET "/api/admin/newssources/sources" -b "$ADMIN_COOKIES"

    step "UC12 - Update news source"
    request PUT "/api/admin/newssources/${SOURCE_ID}" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}" \
        -H "Content-Type: application/json" \
        --data-binary "{\"name\":\"Updated Demo Source ${RUN_ID}\",\"url\":\"https://example.com/demo-source-${RUN_ID}\"}"

    step "UC16 - Import article as administrator"
    request POST "/api/admin/articles/import" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}" \
        -H "Content-Type: application/json" \
        --data-binary "{\"sourceId\":\"${SOURCE_ID}\",\"articles\":[{\"title\":\"Imported demo article ${RUN_ID}\",\"content\":\"Imported through UC16.\",\"url\":\"https://example.com/import-${RUN_ID}\",\"category\":\"Demo\",\"publishedAt\":\"2026-11-18T13:00:00Z\"}]}"

    IMPORTED_ARTICLE_ID="$(json_value "0.id")"
    echo "Imported article: ${IMPORTED_ARTICLE_ID}"

    step "UC17 - Admin edits any article"
    request PUT "/api/admin/articles/${IMPORTED_ARTICLE_ID}" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}" \
        -H "Content-Type: application/json" \
        --data-binary "{\"title\":\"Admin-updated imported article ${RUN_ID}\",\"content\":\"Updated by an administrator.\",\"url\":\"https://example.com/import-${RUN_ID}\",\"category\":\"Demo\",\"publishedAt\":\"2026-11-18T13:00:00Z\"}"

    step "UC18 - Create another administrator"
    NEW_ADMIN_EMAIL="demo-admin-${RUN_ID}@example.com"
    NEW_ADMIN_UPDATED_EMAIL="demo-admin-updated-${RUN_ID}@example.com"

    request POST "/api/admin/accounts" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}" \
        -H "Content-Type: application/json" \
        --data-binary "{\"email\":\"${NEW_ADMIN_EMAIL}\",\"password\":\"demo-admin-password-123\"}"

    NEW_ADMIN_ID="$(json_value "id")"
    echo "Created admin account: ${NEW_ADMIN_ID}"

    step "UC18 - Modify another account"
    request PUT "/api/admin/accounts/${NEW_ADMIN_ID}" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}" \
        -H "Content-Type: application/json" \
        --data-binary "{\"email\":\"${NEW_ADMIN_UPDATED_EMAIL}\",\"password\":null}"

    step "UC17 - Admin deletes any article"
    request DELETE "/api/admin/articles/${IMPORTED_ARTICLE_ID}" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}"

    step "UC12 - Delete demo news source"
    request DELETE "/api/admin/newssources/${SOURCE_ID}" \
        -b "$ADMIN_COOKIES" \
        -H "X-CSRF-TOKEN: ${ADMIN_CSRF}"
fi

headline "6. CLEANUP USER DATA"

step "UC15 - Delete own article"
request DELETE "/api/articles/${USER_ARTICLE_ID}" \
    -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}"

step "UC8 - Delete own account"
request DELETE "/api/accounts/me" \
    -b "$USER_COOKIES" \
    -H "X-CSRF-TOKEN: ${USER_CSRF}"

headline "DONE"
echo "Endpoint walkthrough completed."
echo "Swagger UI: ${BASE_URL}/swagger-ui.html"
echo "OpenAPI JSON: ${BASE_URL}/v3/api-docs"
