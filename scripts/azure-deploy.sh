#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FindIT - build and deploy to an existing Azure App Service.
#
# This script does NOT create resources. It publishes a new version of the
# application to an App Service that already exists (see azure-setup.sh).
#
# Prerequisites:
#   * az login
#   * an existing App Service (Java SE / Java 21 runtime)
#
# Usage:
#   ./scripts/azure-deploy.sh --resource-group rg-findit --app-name app-findit-xyz \
#        [--admin-email admin@findit.local] [--seed-admin]
#
# The database password is asked for interactively and is sent straight to the
# App Service configuration - it is never written into a file in this repository.
#
# --seed-admin creates the administrator account on the first start. Leave it
# out for every later deployment.
# ---------------------------------------------------------------------------
set -euo pipefail

RESOURCE_GROUP=""
APP_NAME=""
SQL_SERVER=""
STORAGE_ACCOUNT=""
DB_NAME="findit"
ADMIN_EMAIL=""
SQL_USERNAME=""
SEED_ADMIN="false"

usage() {
    grep '^#' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --resource-group) RESOURCE_GROUP="$2"; shift 2 ;;
        --app-name)       APP_NAME="$2";       shift 2 ;;
        --sql-server)     SQL_SERVER="$2";     shift 2 ;;
        --storage-account) STORAGE_ACCOUNT="$2"; shift 2 ;;
        --db-name)        DB_NAME="$2";        shift 2 ;;
        --sql-username)   SQL_USERNAME="$2";   shift 2 ;;
        --admin-email)    ADMIN_EMAIL="$2";    shift 2 ;;
        --seed-admin)     SEED_ADMIN="true";   shift 2 ;;
        --help|-h)        usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

[[ -n "$RESOURCE_GROUP" && -n "$APP_NAME" ]] || usage
command -v az >/dev/null 2>&1 || { echo "The Azure CLI is not installed." >&2; exit 1; }

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -z "$SQL_SERVER" ]]; then
    SQL_SERVER=$(az webapp config appsettings list -g "$RESOURCE_GROUP" -n "$APP_NAME" \
        --query "[?name=='DB_URL'].value | [0]" -o tsv | sed -E 's#jdbc:sqlserver://([^.:]+).*#\1#')
fi
if [[ -z "$STORAGE_ACCOUNT" ]]; then
    STORAGE_ACCOUNT=$(az webapp config appsettings list -g "$RESOURCE_GROUP" -n "$APP_NAME" \
        --query "[?name=='AZURE_STORAGE_ACCOUNT_NAME'].value | [0]" -o tsv)
fi
if [[ -z "$SQL_USERNAME" ]]; then
    SQL_USERNAME=$(az webapp config appsettings list -g "$RESOURCE_GROUP" -n "$APP_NAME" \
        --query "[?name=='DB_USERNAME'].value | [0]" -o tsv)
fi

[[ -n "$SQL_SERVER" ]] || { echo "Could not determine the SQL server name - use --sql-server." >&2; exit 1; }

echo "== FindIT deployment to $APP_NAME ($RESOURCE_GROUP) =="

echo "1/5 Building the application"
./gradlew clean build --console=plain -x test
JAR="build/libs/findit.jar"
[[ -f "$JAR" ]] || { echo "The jar was not built." >&2; exit 1; }
ls -lh "$JAR"

echo "2/5 Publishing the jar to the App Service"
az webapp deploy --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
    --src-path "$JAR" --type jar --output none

echo "3/5 Configuring the application settings"
read -r -s -p "   Database password for '$SQL_USERNAME' (input hidden): " SQL_PASSWORD; echo

DB_URL="jdbc:sqlserver://${SQL_SERVER}.database.windows.net:1433;database=${DB_NAME};encrypt=true;trustServerCertificate=false"

SETTINGS=(
    "SPRING_PROFILES_ACTIVE=azure"
    "APP_BASE_URL=https://${APP_NAME}.azurewebsites.net"
    "DB_URL=${DB_URL}"
    "DB_USERNAME=${SQL_USERNAME}"
    "DB_PASSWORD=${SQL_PASSWORD}"
    "AZURE_STORAGE_ACCOUNT_NAME=${STORAGE_ACCOUNT}"
    "AZURE_STORAGE_CONTAINER=findit-item-images"
    "SESSION_COOKIE_SECURE=true"
    "JAVA_OPTS=-Xms256m -Xmx768m"
)

if [[ "$SEED_ADMIN" == "true" ]]; then
    read -r -s -p "   Administrator password for the FindIT admin account: " ADMIN_PASSWORD; echo
    echo "   The administrator account is created on this start (APP_SEED_ENABLED=true)."
    SETTINGS+=("APP_SEED_ENABLED=true")
    if [[ -n "$ADMIN_EMAIL" ]]; then
        SETTINGS+=("ADMIN_SEED_EMAIL=${ADMIN_EMAIL}")
    fi
    SETTINGS+=("ADMIN_SEED_PASSWORD=${ADMIN_PASSWORD}")
else
    SETTINGS+=("APP_SEED_ENABLED=false")
fi

az webapp config appsettings set --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
    --settings "${SETTINGS[@]}" --output none

echo "4/5 Restarting the application so the new settings take effect"
az webapp restart --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" --output none

echo "5/5 Verifying the health endpoint"
URL="https://${APP_NAME}.azurewebsites.net/api/health"
for attempt in $(seq 1 20); do
    if RESPONSE=$(curl -fsS "$URL" 2>/dev/null); then
        echo "   $RESPONSE"
        echo
        echo "== Deployment finished =="
        echo "URL:         https://${APP_NAME}.azurewebsites.net/"
        echo "Health:      $URL"
        echo
        echo "Verify the whole workflow against the deployment:"
        echo "  ADMIN_EMAIL=<your admin email> ADMIN_PASSWORD='<the password you entered>' \\"
        echo "    ./scripts/smoke-test.sh https://${APP_NAME}.azurewebsites.net"
        exit 0
    fi
    echo "   Waiting for the application to start (attempt $attempt/20)..."
    sleep 15
done

echo "The application did not answer on $URL." >&2
echo "Check the logs with:" >&2
echo "  az webapp log tail --resource-group $RESOURCE_GROUP --name $APP_NAME" >&2
exit 1
