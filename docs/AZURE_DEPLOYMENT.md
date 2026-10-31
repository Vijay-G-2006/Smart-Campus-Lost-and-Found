# FindIT - Azure deployment guide (step by step)

This guide takes you from an empty Azure subscription to a running, verified FindIT
deployment. Follow the parts in order; every command is given in full, with the values you
have to replace marked as `<...>`.

> **Cost warning**
> Azure SQL Database, App Service and Blob Storage are **billable**. Part 2 creates them.
> The configuration below uses the cheapest sensible options (serverless SQL with
> auto-pause, a B1 App Service plan, hot-tier blob storage). Check the current prices in
> the [Azure pricing calculator](https://azure.microsoft.com/pricing/) before you start,
> and read [Part 11](#11-cost-control-and-teardown) for how to switch everything off.

---

## Table of contents

| Part | What you do | Time |
| --- | --- | --- |
| [0](#0-what-you-need) | Prerequisites | 10 min |
| [1](#1-sign-in-to-azure) | Sign in and pick a subscription | 2 min |
| [2](#2-create-the-azure-resources) | Create SQL, Storage and the App Service | 15 min |
| [3](#3-grant-the-app-service-access-to-blob-storage) | Managed identity + role | 5 min |
| [4](#4-build-the-application) | Produce the jar | 5 min |
| [5](#5-deploy-the-application) | Publish the jar and set the settings | 10 min |
| [6](#6-create-the-administrator-account) | One-time admin creation | 3 min |
| [7](#7-verify-the-deployment) | Health, smoke test, manual check | 10 min |
| [8](#8-database-migrations) | How the schema is created | 3 min |
| [9](#9-troubleshooting) | Error -> cause -> fix | - |
| [10](#10-updating-and-rolling-back) | New versions, rollback | 5 min |
| [11](#11-cost-control-and-teardown) | Reduce costs, delete everything | 5 min |

---

## 0. What you need

| Requirement | Notes |
| --- | --- |
| An Azure subscription | The free "Azure for Students" subscription is enough for an evaluation |
| The Azure CLI | <https://learn.microsoft.com/cli/azure/install-azure-cli> |
| A terminal | Linux, macOS or Windows (WSL) |
| Java 21+ and internet access | To build the jar locally (the Gradle wrapper downloads the JDK if needed) |
| About 45 minutes | Including the verification steps |

The Azure CLI is **not** installed on this machine. Install it first:

```bash
# Debian / Ubuntu
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# macOS
brew install azure-cli

# Windows (PowerShell)
winget install Microsoft.AzureCLI
```

Then:

```bash
az version
az login                 # opens a browser, sign in with your Azure account
az account list -o table # check which subscription is active
```

---

## 1. Sign in to Azure

```bash
az login
az account set --subscription "<subscription-name-or-id>"
az account show --query "{name:name, id:id, tenant:tenantId}" -o tsv
```

Remember the **subscription id** - the deployment workflow needs it.

---

## 2. Create the Azure resources

### 2.1 The fast path (recommended)

The repository contains a provisioning script that creates exactly what the application
needs and prints the values you have to configure afterwards.

```bash
cd FindIT
./scripts/azure-setup.sh \
    --resource-group rg-findit \
    --location westeurope \
    --app-name app-findit-<yoursuffix> \
    --sql-server sql-findit-<yoursuffix> \
    --admin-login finditadmin
```

The script asks for the SQL administrator password interactively (the input is hidden and
is **not** written to any file). It creates:

| Resource | Configuration | Why |
| --- | --- | --- |
| Resource group `rg-findit` | - | everything in one place, easy to delete |
| Azure SQL server | - | hosts the database |
| Azure SQL database `findit` | General Purpose, 0.5 vCore, **serverless with auto-pause** | cheapest option |
| Storage account `stfindit<suffix>` | Standard_LRS, hot tier, **public blob access disabled** | item images |
| Blob container `findit-item-images` | **private** | item images |
| App Service plan `plan-findit` | B1 | runs the jar |
| App Service `app-findit-<suffix>` | Linux, Java 21 | runs the application |

> The App Service name becomes the URL `https://app-findit-<yoursuffix>.azurewebsites.net`
> and must be globally unique. The storage account name must be globally unique too, is
> limited to 24 lowercase letters and digits and cannot contain a hyphen.

### 2.2 The manual path (Azure portal)

If you prefer the portal: create the same four resources in the same region.

1. **Resource group** -> Create -> name `rg-findit`, region `westeurope`.
2. **Azure SQL Server** -> Create -> name `sql-findit-<suffix>`, server admin login
   `finditadmin`, a strong password, `westeurope`, *Public* access (see the note below).
3. **SQL database** -> Create -> Server `sql-findit-<suffix>`, Database name `findit`,
   Compute type **Serverless**, Capacity **0.5 vCore**, minimum capacity **0.5**,
   Auto-pause **enabled**, delay **5 minutes**.
4. **Storage account** -> Create -> name `stfindit<suffix>`, performance Standard,
   redundancy LRS, **Allow blob public access: Disabled**, minimum TLS 1.2.
5. **Storage container** -> inside the storage account -> Containers -> Create ->
   name `findit-item-images`, **Private access level**.
6. **App Service plan** -> Create -> name `plan-findit`, region `westeurope`, SKU **B1**.
7. **Web App** -> Create -> name `app-findit-<suffix>`, runtime **Java 21** /
   *Java SE*, plan `plan-findit`.

### 2.3 Let the App Service reach the database

Azure SQL only accepts connections from explicitly allowed addresses.

```bash
# Quickest for an evaluation: allow connections from Azure services.
az sql server firewall-rule create \
    --resource-group rg-findit \
    --server sql-findit-<suffix> \
    --name AllowAzureServices \
    --start-ip-address 0.0.0.0 --end-ip-address 0.0.0.0
```

For a real deployment use a **private endpoint** instead of the firewall rule and delete
this rule afterwards.

---

## 3. Grant the App Service access to blob storage

The application stores the item images in Azure Blob Storage. Use the **managed identity**
of the App Service - no connection string, no secret.

```bash
# 1. Give the web app a system-assigned identity
az webapp identity assign --resource-group rg-findit --name app-findit-<suffix>

# 2. Read the principal id of that identity
PRINCIPAL_ID=$(az webapp identity show --resource-group rg-findit --name app-findit-<suffix> \
    --query principalId -o tsv)
echo "$PRINCIPAL_ID"

# 3. Grant "Storage Blob Data Contributor" on the storage account (data plane!)
STORAGE_ID=$(az storage account show --resource-group rg-findit --name stfindit<suffix> \
    --query id -o tsv)

az role assignment create \
    --assignee "$PRINCIPAL_ID" \
    --role "Storage Blob Data Contributor" \
    --scope "$STORAGE_ID"
```

The role assignment can take a minute to become effective. `Storage Blob Data Contributor`
is the **data plane** role; `Storage Blob Data Owner` would also work but grants more than
necessary.

---

## 4. Build the application

```bash
cd FindIT
./gradlew clean build          # runs the 61 tests and creates build/libs/findit.jar
ls -lh build/libs/findit.jar
```

The jar contains the backend **and** the front end, so a single file is deployed.

> Only `build/libs/findit.jar` is deployed. Never upload `findit-1.0.0-plain.jar` - it
> has no dependencies and will not start.

---

## 5. Deploy the application

### 5.1 The fast path (recommended)

```bash
./scripts/azure-deploy.sh \
    --resource-group rg-findit \
    --app-name app-findit-<suffix> \
    --sql-server sql-findit-<suffix> \
    --storage-account stfindit<suffix> \
    --sql-username finditadmin \
    --admin-email admin@findit.local \
    --seed-admin
```

The script builds the jar, publishes it, writes the application settings, restarts the app
and waits for `/api/health` to answer. It asks for the database password and the
administrator password interactively - nothing is written into a file in this repository.

### 5.2 The manual path (Azure CLI)

```bash
# 1. Publish the jar (Kudu OneDeploy renames it to app.jar in /home/site/wwwroot)
az webapp deploy \
    --resource-group rg-findit \
    --name app-findit-<suffix> \
    --src-path build/libs/findit.jar \
    --type jar

# 2. Write the application settings
az webapp config appsettings set \
    --resource-group rg-findit \
    --name app-findit-<suffix> \
    --settings \
      SPRING_PROFILES_ACTIVE=azure \
      APP_BASE_URL=https://app-findit-<suffix>.azurewebsites.net \
      DB_URL="jdbc:sqlserver://sql-findit-<suffix>.database.windows.net:1433;database=findit;encrypt=true;trustServerCertificate=false" \
      DB_USERNAME=finditadmin \
      DB_PASSWORD='<the SQL administrator password>' \
      AZURE_STORAGE_ACCOUNT_NAME=stfindit<suffix> \
      AZURE_STORAGE_CONTAINER=findit-item-images \
      SESSION_COOKIE_SECURE=true \
      JAVA_OPTS="-Xms256m -Xmx768m" \
      APP_SEED_ENABLED=false

# 3. Restart so the new settings are applied
az webapp restart --resource-group rg-findit --name app-findit-<suffix>
```

**These are the exact settings the application reads** (`application-azure.yml`):

| Setting | Value | Meaning |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `azure` | activates the Azure profile (Azure SQL + Azure Blob) |
| `APP_BASE_URL` | `https://<app>.azurewebsites.net` | public base URL |
| `DB_URL` | `jdbc:sqlserver://<server>.database.windows.net:1433;database=findit;encrypt=true;trustServerCertificate=false` | Azure SQL connection |
| `DB_USERNAME` / `DB_PASSWORD` | the SQL administrator | database login |
| `AZURE_STORAGE_ACCOUNT_NAME` | `stfindit<suffix>` | enables managed identity access to the blob storage |
| `AZURE_STORAGE_CONTAINER` | `findit-item-images` | the private container |
| `SESSION_COOKIE_SECURE` | `true` | the session cookie is only sent over HTTPS |
| `JAVA_OPTS` | `-Xms256m -Xmx768m` | JVM heap (the platform default is 70 % of the plan memory) |
| `APP_SEED_ENABLED` | `false` (or `true` once, see Part 6) | creates the administrator account |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | optional | telemetry, see Part 7.4 |

> Do **not** set `AZURE_STORAGE_CONNECTION_STRING` when you use the managed identity - the
> application prefers the connection string if it is present.

### 5.3 The GitHub Actions way (optional)

The repository contains `.github/workflows/deploy-azure.yml`. It is deliberately **manual**:

1. Push the project to a GitHub repository.
2. In the repository go to *Settings -> Secrets and variables -> Actions* and add:
   * `AZURE_CREDENTIALS` - the JSON of a service principal with the role
     *Website Contributor* on the App Service (or use the federated OIDC login of
     `azure/login@v2`, which is more secure and needs no password at all),
   * `AZURE_APP_SERVICE_NAME` - the name of the App Service.
3. In the repository go to *Actions -> Deploy to Azure (manual)*, run it and type
   `deploy` into the confirmation field.
4. The workflow builds the jar, publishes it and verifies `/api/health`.

---

## 6. Create the administrator account

There is **no default password** in the code - the administrator is created by a seed
process that only runs when you give it a password.

**Option A - one time through the application settings (recommended):**

```bash
az webapp config appsettings set \
    --resource-group rg-findit \
    --name app-findit-<suffix> \
    --settings APP_SEED_ENABLED=true ADMIN_SEED_EMAIL=admin@findit.local ADMIN_SEED_PASSWORD='<a strong password>'

az webapp restart --resource-group rg-findit --name app-findit-<suffix>

# wait for the start, then check the log for "Created administrator account"
az webapp log tail --resource-group rg-findit --name app-findit-<suffix>
```

**Then switch the seeding off again** (otherwise the password stays in the settings):

```bash
az webapp config appsettings set \
    --resource-group rg-findit \
    --name app-findit-<suffix> \
    --settings APP_SEED_ENABLED=false

az webapp config appsettings delete \
    --resource-group rg-findit \
    --name app-findit-<suffix> \
    --setting-names ADMIN_SEED_PASSWORD ADMIN_SEED_EMAIL
```

**Option B - a direct SQL statement** (the password has to be a BCrypt hash, so this is
only practical if you generate one; use Option A).

---

## 7. Verify the deployment

### 7.1 Health endpoint

```bash
curl -fsS https://app-findit-<suffix>.azurewebsites.net/api/health
```

Expected:

```json
{"status":"UP","application":"FindIT","profile":"azure","imageStorage":"azure-blob:findit-item-images","time":"..."}
```

If the answer is a `404` or a `500`, go to [Part 9](#9-troubleshooting).

### 7.2 The full smoke test

The repository contains an end-to-end check that runs against any URL:

```bash
ADMIN_EMAIL=admin@findit.local ADMIN_PASSWORD='<the administrator password>' \
  ./scripts/smoke-test.sh https://app-findit-<suffix>.azurewebsites.net
```

Expected: `== Result: 21 passed, 0 failed ==`. The script registers three throw-away
students, creates a lost and a found report, checks the match suggestion, submits a claim,
proves that a normal user is denied the administrator endpoints and that private data
stays private, and finally reviews the claim as administrator and marks the item as
returned.

### 7.3 The manual check

Open `https://app-findit-<suffix>.azurewebsites.net/` and walk through
[`docs/ACCEPTANCE_CHECKLIST.md`](ACCEPTANCE_CHECKLIST.md). At least check:

* the landing page and the navigation work,
* you can register, sign in and sign out,
* you can create a lost report and a found report,
* the found report shows a match suggestion with a score breakdown,
* you can submit a claim and see it under "My claims",
* the administrator sees the claim in the queue and can approve it and mark the item as
  returned,
* an image can be uploaded and is displayed.

### 7.4 Application Insights (optional)

```bash
az monitor app-insights component create \
    --resource-group rg-findit \
    --application insights-findit \
    --workspace /subscriptions/<sub>/resourceGroups/rg-findit/providers/Microsoft.OperationalInsights/workspaces/law-findit
```

Copy the **connection string** from the portal and add it as an application setting, then
attach the Java agent (no code change needed):

```bash
az webapp config appsettings set \
    --resource-group rg-findit --name app-findit-<suffix> \
    --settings APPLICATIONINSIGHTS_CONNECTION_STRING='<connection string>' \
              JAVA_OPTS="-Xms256m -Xmx768m -javaagent:/home/site/appsettings/AI-agent.jar"
```

---

## 8. Database migrations

Flyway runs **automatically on every start** of the application:

| Migration | What it does |
| --- | --- |
| `V1__create_core_schema.sql` | creates the tables, indexes, foreign keys and unique constraints |
| `V2__seed_reference_data.sql` | inserts the item categories and the campus locations |

The Azure profile renders the migration with the SQL Server dialect
(`IDENTITY(1,1)`, `BIT`, `DATETIME2`), so the same files work for the local H2 database and
for Azure SQL. Hibernate never changes the schema (`ddl-auto: none`).

Check that the migrations ran:

```bash
az webapp log tail --resource-group rg-findit --name app-findit-<suffix>
# look for: "Successfully applied 2 migrations ... now at version v2"
```

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `401` on every API call, the page does not load | The application did not start | `az webapp log tail -g rg-findit -n app-findit-<suffix>` and read the first error |
| `Login failed for user 'finditadmin'` | Wrong `DB_PASSWORD`, or the firewall rule is missing | Check the firewall rule (Part 2.3) and the setting |
| `The certificate chain is not trusted` / TLS error | The JDBC URL lost `encrypt=true` | Use the exact `DB_URL` from Part 5.2 |
| `Cannot create PoolableConnectionFactory` | Azure SQL paused (serverless) | Wait 30-60 s, the database resumes on the first connection |
| `Flyway ... Unsupported Database: Azure SQL` | The `flyway-sqlserver` module is missing | It is included in `build.gradle`; rebuild the jar |
| `BlobStorageException 403` in the log | The managed identity has no role | Repeat Part 3 and wait a minute |
| `The blob container could not be verified` | The container does not exist | `az storage container create --name findit-item-images --account-name stfindit<suffix> --public-access off` |
| `Address already in use` (locally only) | Another instance runs on 8080 | `SERVER_PORT=8081 ./gradlew bootRun` |
| The front end shows but the API returns `404` | The jar was deployed but the profile is wrong | `SPRING_PROFILES_ACTIVE=azure` must be set |
| `403 Forbidden` on a form submission | The CSRF token is missing | Reload the page - the token is issued with the page |
| The image upload fails | The container is missing or the role is missing | Part 3 and the container command above |
| The application is slow to start | The serverless database was paused | Normal for the first request after a pause (up to ~60 s) |

Useful commands:

```bash
az webapp log tail --resource-group rg-findit --name app-findit-<suffix>      # live log
az webapp log config --resource-group rg-findit --name app-findit-<suffix> \
    --docker-container-logging filesystem --level Verbose                     # verbose logging
az webapp restart --resource-group rg-findit --name app-findit-<suffix>       # restart
az webapp ssh --resource-group rg-findit --name app-findit-<suffix>           # shell in the container
```

---

## 10. Updating and rolling back

### Update

```bash
cd FindIT
git pull
./gradlew clean build
az webapp deploy -g rg-findit -n app-findit-<suffix> --src-path build/libs/findit.jar --type jar
az webapp restart -g rg-findit -n app-findit-<suffix>
```

### Roll back

App Service keeps the previous deployments:

```bash
az webapp deployment list-publishing-profiles -g rg-findit -n app-findit-<suffix> -o table
az webapp deployment list -g rg-findit -n app-findit-<suffix> -o table
```

The last working version can be restored from the deployment list in the portal
(*Deployments -> select the version -> Manage deployment -> Redeploy*).

Database migrations are **not** rolled back automatically - Flyway only moves forward.
Write a new migration (`V3__...sql`) for every schema change.

---

## 11. Cost control and teardown

### Reduce the cost

| Resource | Cheapest sensible setting |
| --- | --- |
| Azure SQL | Serverless, 0.5 vCore, auto-pause after 5 minutes (already set) |
| App Service | The **Free** plan (F1) is enough for an evaluation - but it stops when idle and has no custom deployment slot. B1 is the smallest always-on plan |
| Storage | Standard_LRS, hot tier; add a lifecycle rule that moves images older than 30 days to the cool tier |
| Application Insights | 30 day retention, daily sampling |

```bash
# Delete the resources completely - this is destructive, so it asks for a confirmation
az group delete --name rg-findit --yes
```

> The resource group contains the database. Delete it only when you really want to lose
> the data - export it first with `az sql db export`.

---

## 12. Checklist before you go live

- [ ] The SQL firewall only allows the App Service (or a private endpoint is used)
- [ ] `DB_PASSWORD` comes from Key Vault, or the settings are otherwise protected
- [ ] The blob container has **public access disabled**
- [ ] The managed identity only has `Storage Blob Data Contributor`
- [ ] `SESSION_COOKIE_SECURE=true`
- [ ] `APP_SEED_ENABLED=false` and `ADMIN_SEED_PASSWORD` are **not** in the settings any more
- [ ] An administrator account exists and you can sign in with it
- [ ] `/api/health` answers with `"status":"UP"` and `"profile":"azure"`
- [ ] The smoke test passes against the deployed URL
- [ ] The cost of the created resources has been checked
