# Local database sync

Use this when a teammate has pulled the latest code but the local MySQL data is
out of date or inconsistent. The script is idempotent: it applies missing schema
changes and replays the tracked importers without dropping the whole database.

## Normal repair

```powershell
git pull origin master
.\scripts\sync_latest_database.ps1
```

The script reads the MySQL connection from
`health-portal-backend/src/main/resources/jdbc.properties` by default.

## Repair article cover images too

If NEWS or KNOWLEDGE images are wrong or missing, run:

```powershell
.\scripts\sync_latest_database.ps1 -WithImages
```

Image matching may access the network and can take longer than the normal sync.

## Custom database connection

```powershell
.\scripts\sync_latest_database.ps1 `
  -DbHost localhost `
  -DbPort 3306 `
  -DbUser root `
  -DbPassword your_password `
  -DbName health_portal
```

## What it syncs

- V002 schema migration for CMS content and medical resources
- Portal user/apply compatibility migration
- Knowledge category compatibility migration
- Data governance issue migration when the required tables are present
- Health resources, hospitals, drug catalog, and vaccine data from tracked JSON
- Local policy library from `data/policies`
- News center data from tracked crawl results
- Health encyclopedia category relations
- Optional NEWS and KNOWLEDGE cover image matching

The script writes a timestamped backup to `db-backups/` when `mysqldump` is
available. It does not drop the database and does not clear user accounts,
applications, or manually created CMS records.

After the script finishes, restart the backend.
