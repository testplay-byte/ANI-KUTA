# App Metadata Table

## `app_metadata`
Key-value store for app-level flags (schema version, migration flags, etc.).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `key` | TEXT | PRIMARY KEY | Flag key |
| `value` | TEXT | NOT NULL | Flag value |

**Why**: Simple key-value store for app-level state that doesn't warrant a dedicated table (schema version, one-shot migration flags, feature flags).

**Queries**: `setMetadata` (INSERT OR REPLACE), `getMetadata` (SELECT by key).
