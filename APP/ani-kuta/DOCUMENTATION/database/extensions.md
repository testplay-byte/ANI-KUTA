# Extensions Tables

## `installed_source`
Installed extension sources on the device.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `ecosystem` | TEXT | NOT NULL, PK part | "animiru", "mangayomi", etc. |
| `source_id` | TEXT | NOT NULL, PK part | Source ID within ecosystem |
| `name` | TEXT | NOT NULL | Display name |
| `version` | TEXT | NOT NULL | Extension version |
| `package_name` | TEXT | NOT NULL | Android package name (for install/uninstall) |
| `signature_fingerprint` | TEXT | | SHA-256 fingerprint (for trust verification) |
| `is_enabled` | INTEGER | NOT NULL DEFAULT 1 | 0 or 1 |
| `installed_at` | INTEGER | NOT NULL | Epoch millis |
| `last_updated_at` | INTEGER | | Epoch millis |

**PK**: `(ecosystem, source_id)`
**Index**: `idx_installed_source_package` ON `package_name`

**Why**: Persists installed extensions across app launches. `signature_fingerprint` enables trust verification (SHA-256). `package_name` enables PackageInstaller integration.

## `extension_repo`
Extension repositories (URLs that serve extension APKs). NO default repos — user adds their own.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `ecosystem` | TEXT | NOT NULL, PK part | "animiru", etc. |
| `url` | TEXT | NOT NULL, PK part | Repo URL |
| `name` | TEXT | NOT NULL | Display name |
| `added_at` | INTEGER | NOT NULL | Epoch millis |

**PK**: `(ecosystem, url)`

**Why**: Stores user-added extension repos. `ecosystem` distinguishes Animiru repos from future Mangayomi/Cloudstream repos.
