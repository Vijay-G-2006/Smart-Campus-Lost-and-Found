#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FindIT - Azure resource provisioning helper.
#
# THIS SCRIPT CREATES BILLABLE AZURE RESOURCES. It only creates resources and
# never deletes anything. Run it once, read the output, and keep the notes.
#
# Prerequisites:
#   * Azure CLI installed   : https://learn.microsoft.com/cli/azure/install-azure-cli
#   * Signed in              : az login
#   * A subscription with the rights to create resources
#
# Usage:
#   ./scripts/azure-setup.sh \
#       --resource-group rg-findit \
#       --location westeurope \
#       --app-name app-findit-<your-suffix> \
#       --sql-server sql-findit-<your-suffix> \
#       --admin-user finditadmin
#
# The password for the SQL administrator is asked for interactively - it is
# never stored in this script or in a file.
#
# Afterwards set the application settings with scripts/azure-deploy.sh.
# ---------------------------------------------------------------------------
set -euo pipefail

RESOURCE_GROUP=""
LOCATION=""
APP_NAME=""
SQL_SERVER=""
STORAGE_ACCOUNT=""
ADMIN_LOGIN=""
SQL_EDITION="GeneralPurpose"
SQL_CAPACITY="0.5"

usage() {
    grep '^#' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --resource-group) RESOURCE_GROUP="$2"; shift 2 ;;
        --location)       LOCATION="$2";       shift 2 ;;
        --app-name)       APP_NAME="$2";       shift 2 ;;
        --sql-server)     SQL_SERVER="$2";     shift 2 ;;
        --storage-account) STORAGE_ACCOUNT="$2"; shift 2 ;;
        --admin-login)    ADMIN_LOGIN="$2";    shift 2 ;;
        --help|-h)        usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

[[ -n "$RESOURCE_GROUP" && -n "$LOCATION" && -n "$APP_NAME" && -n "$SQL_SERVER" && -n "$ADMIN_LOGIN" ]] || usage

command -v az >/dev/null 2>&1 || { echo "The Azure CLI is not installed." >&2; exit 1; }

echo "== FindIT Azure setup =="
echo "This creates BILLABLE resources in the subscription:"
az account show --query "{name:name, id:id}" -o tsv || { echo "az login failed." >&2; exit 1; }
echo
read -r -p "Continue? [y/N] " answer
[[ "$answer" == "y" || "$answer" == "Y" ]] || { echo "Aborted - nothing was created."; exit 0; }

# Names must be globally unique: derive a storage account name if none was given.
if [[ -z "$STORAGE_ACCOUNT" ]]; then
    suffix=$(echo "$APP_NAME" | tr -cd 'a-z0-9' | cut -c1-16)
    STORAGE_ACCOUNT="stfindit${suffix}"
fi

echo
echo "1/6 Resource group: $RESOURCE_GROUP"
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none

echo "2/6 Azure SQL server: $SQL_SERVER"
read -r -s -p "   Administrator password (input hidden): " SQL_PASSWORD; echo
az sql server create \
    --name "$SQL_SERVER" \
    --resource-group "$RESOURCE_GROUP" \
    --location "$LOCATION" \
    --admin-user "$ADMIN_LOGIN" \
    --admin-password "$SQL_PASSWORD" \
    --output none

echo "3/6 Azure SQL database: findit (serverless, auto pause)"
az sql db create \
    --resource-group "$RESOURCE_GROUP" \
    --server "$SQL_SERVER" \
    --name findit \
    --edition "$SQL_EDITION" \
    --capacity "$SQL_CAPACITY" \
    --compute-model Serverless \
    --auto-pause-enabled true \
    --auto-pause-delay 5 \
    --min-capacity 0.5 \
    --output none

echo "4/6 Storage account: $STORAGE_ACCOUNT (public blob access disabled)"
az storage account create \
    --name "$STORAGE_ACCOUNT" \
    --resource-group "$RESOURCE_GROUP" \
    --location "$LOCATION" \
    --sku Standard_LRS \
    --access-tier Hot \
    --min-tls-version TLS1_2 \
    --allow-blob-public-access false \
    --output none

echo "5/6 Private blob container: findit-item-images"
az storage container create \
    --name findit-item-images \
    --account-name "$STORAGE_ACCOUNT" \
    --public-access off \
    --output none

echo "6/6 App Service plan and web app: $APP_NAME"
az appservice plan create \
    --name "plan-findit" \
    --resource-group "$RESOURCE_GROUP" \
    --location "$LOCATION" \
    --sku B1 \
    --output none

# The runtime string depends on the platform - take it from the platform itself.
JAVA_RUNTIME=$(az webapp list-runtimes --os linux | grep -iE '"java[^"]*21[^"]*"' | head -n 1 | sed -E 's/.*"(java[^"]*)".*/\1/I')
if [[ -z "$JAVA_RUNTIME" ]]; then
    JAVA_RUNTIME=$(az webapp list-runtimes --os linux | grep -iE 'java' | head -n 1 | sed -E 's/.*"(java[^"]*)".*/\1/I')
fi
echo "   Using the runtime stack: $JAVA_RUNTIME"

az webapp create \
    --resource-group "$RESOURCE_GROUP" \
    --plan "plan-findit" \
    --name "$APP_NAME" \
    --runtime "$JAVA_RUNTIME" \
    --output none

echo
echo "== Done. The following values are needed for the application settings =="
cat <<EOF
RESOURCE_GROUP=$RESOURCE_GROUP
APP_NAME=$APP_NAME
SQL_SERVER=$SQL_SERVER
STORAGE_ACCOUNT=$STORAGE_ACCOUNT
DB_URL=jdbc:sqlserver://$SQL_SERVER.database.windows.net:1433;database=findit;encrypt=true;trustServerCertificate=false
DB_USERNAME=$ADMIN_LOGIN
AZURE_STORAGE_ACCOUNT_NAME=$STORAGE_ACCOUNT
AZURE_STORAGE_CONTAINER=findit-item-images
EOF
echo
echo "Next: ./scripts/azure-deploy.sh --resource-group $RESOURCE_GROUP --app-name $APP_NAME"
echo "The SQL password is asked for again - do not save it in a file."
