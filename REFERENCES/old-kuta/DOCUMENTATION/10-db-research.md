# 10 — Database Research: Room vs SQLDelight

> **Task ID:** 2-DB
> **Recommendation:** **Stay on SQLDelight (2.x).** See §Recommendation for reasoning.
> Sources: old project source under `REFERENCES/old-kuta/ANIKUTA/core/database/`, web research
> (Mar 2025 – Mar 2026 articles), direct inspection of `quickdesh/Animiru` (master) and
> `aniyomiorg/aniyomi` (main) `gradle/libs.versions.toml`.

---

## Project Requirements

The DB layer must support:

1. **Multi-extension ecosystems** (Aniyomi, Mangayomi, sora, cloudstream, kotatsu) — each
   source row must record which ecosystem it came from (source-provenance columns).
2. **Multi-content-type** — anime (video) now; manga (image) + novels (text) later, modularly.
   Each content type gets its own table family. ADR-009 reserves a separate manga DB.
3. **Two-tier identity system** (ADR-050) — `local_id` (per-source) + `content_id` (per-content,
   survives source switches). Heavily indexed; subject to backfill migrations.
4. **Backup / restore** — export/import across ecosystems; **Aniyomi `.tachibk` protobuf
   restore-only compat** is a hard requirement.
5. **User activity tracking** — watch history, ad views, stats. New tables added over time.
6. **Modular Gradle** — `:core:database` owns schema; `:data:*` modules implement repositories
   that call queries. DB code never imported from `:feature:*` or `:app`.
7. **Highly customizable UI** — preferences, theme tokens, layout options (mostly
   `:core:preferences`, but some persisted in DB).
8. **Kotlin + Jetpack Compose**, minSdk 24, targetSdk 35. Reactive everywhere (`Flow`/`StateFlow`).
9. **Frequent schema evolution** — adding extension ecosystems + content types + activity
   tables means 5–10+ migrations over the project's lifetime.

---

## Old Project Analysis

Source: `REFERENCES/old-kuta/ANIKUTA/core/database/`.

### Stack

- **SQLDelight 2.0.2** (`app.cash.sqldelight`) — declared in `gradle/libs.versions.toml`.
- Driver: `AndroidSqliteDriver` (file `anikuta.db`).
- SQLite engine: **requery `sqlite-android` 3.45.0** + `androidx.sqlite:sqlite-framework:2.4.0`.
- Dialect: SQLite 3.38+ (`libs.sqldelight.dialects.sql`).
- Coroutines ext: `app.cash.sqldelight:coroutines-extensions-jvm` — `asFlow().mapToList(io)`.
- Paging: `app.cash.sqldelight:androidx-paging3-extensions`.

### Schema (6 tables, schema version 3)

| Table | Columns | Queries | Notes |
|---|---|---|---|
| `animes` | ~30 | ~25 | Heavily denormalized. Two-tier identity (`local_id`, `content_id`) + full source provenance (`system`, `repo_url`, `extension_pkg_name`, `extension_version_*`, `is_nsfw`, `source_name`, …) + ADR-024 status-tracking (`release_date`, `last_refresh`, `last_metadata_fetch`, `next_episode_check`) + library columns (`anilist_id`, `cover_color`, `score`, `total_episodes`, `last_watched`, `next_airing_episode`). |
| `episodes` | ~20 | ~9 | FK `anime_id → animes(_id) ON DELETE CASCADE`. Unique index on `(anime_id, episode_number)`. Anime-specific `fillermark`, `summary`, `preview_url`. |
| `categories` | 5 | ~10 | Seeds Default category (id=1) via `INSERT OR IGNORE`. |
| `anime_category` | 4 | ~9 | Junction table. FKs cascade. |
| `animehistory` | 5 | ~5 | `UNIQUE(anime_id, episode_id)`. Two FKs cascade. |
| `animetrack` | 9 | ~8 | `UNIQUE(anime_id, tracker_id)`. Tracker bindings per anime. |

### Indexes (notable)

- Partial unique indexes: `idx_animes_anilist_id WHERE anilist_id IS NOT NULL`,
  `idx_animes_local_id WHERE local_id IS NOT NULL`, `idx_animes_source_url WHERE source_id != 0`.
- Non-unique: `idx_animes_content_id WHERE content_id IS NOT NULL`.
- `idx_episodes_anime_epnum` — unique on `(anime_id, episode_number)`.

### Migrations (2 `.sqm` files)

- **`1.sqm`** (v1→v2): 6 `ALTER TABLE animes ADD COLUMN` + 1 on `categories` + seed Default
  category + 1 `CREATE UNIQUE INDEX`. Additive only.
- **`2.sqm`** (v2→v3): 16 `ALTER TABLE` statements (two-tier identity + provenance on `animes`;
  ADR-024 columns on `episodes`) + 4 `CREATE INDEX` statements. **Critically, includes
  data-transformation SQL**: dedup `animes` by `(source_id, url)` keeping `MIN(_id)` before
  creating the unique index; same for `episodes` by `(anime_id, episode_number)`. This is
  raw SQL that runs in the migration — exactly the pattern that Room's `autoMigration`
  cannot express.

### Public API

- SQLDelight-generated `AnikutaDatabase` exposes `.animesQueries`, `.episodesQueries`,
  `.categoriesQueries`, `.animeCategoryQueries`, `.animehistoryQueries`, `.animetrackQueries`.
- `DatabaseDriverFactory(context).create(): SqlDriver`.

### Known SQLDelight limitations (from old project notes)

- **Parser doesn't recognize `OLD`/`NEW` in triggers** — Default-category deletion protection
  was moved to the app layer (`CategoryRepositoryImpl.delete` throws for id=1) because a
  `BEFORE DELETE` trigger couldn't be expressed. (Doc 04 §`:core:database` Notes.)

### Backup architecture (Doc 04 §`:core:backup`)

- **DB-agnostic.** A `BackupProvider` interface + `BackupEntry` sealed class with 10
  subclasses (`Library`, `AnimeDetails`, `Episodes`, `EpisodeMetadata`, `WatchProgress`,
  `SourceLinks`, `Tracker`, `Categories`, `Preferences`, `CoverImages`).
- Each provider reads from its data source (DB row type, preference store, etc.) and emits
  a serializable backup model.
- `BackupContainer` (serializable) is written as `.anikuta` (ZIP containing gzipped JSON) —
  not a raw DB file dump.
- **Aniyomi `.tachibk` restore-only compat**: parsed via `kotlinx-serialization-protobuf`
  into minimal `@Serializable` protobuf models in `format/aniyomi/`, then translated to
  `BackupContainer` by `AniyomiBackupTranslator`. This is **independent of the DB layer** —
  it operates on serializable backup models, not on DB row types.

### Multi-module usage

- `:core:database` owns the `.sq` files + driver factory. Exposes the generated
  `AnikutaDatabase` type.
- `:data:anime`, `:data:history` consume `AnikutaDatabase` directly (Koin-injected) and call
  `.animesQueries` etc. Mappers (`AnimeMapper`, `EpisodeMapper`, `CategoryMapper`,
  `HistoryMapper`) convert SQLDelight row types → domain models.
- This pattern works. No multi-module friction reported in the old project's docs.

---

## Room: Pros & Cons

### Pros (for THIS project)

1. **Industry standard for Android.** Largest community, most tutorials, most Stack Overflow
   answers. Faster onboarding for Android devs who haven't used SQLDelight.
2. **Auto-migrations** (`@Database(autoMigrations = [...])`) — Room generates migration SQL
   for simple `ALTER TABLE ADD COLUMN` and column renames from entity diffs. Convenient.
3. **First-class Android Studio support.** Refactoring an `@Entity` field renames the column
   everywhere; DAO methods are navigable Kotlin. IDE inspection catches issues.
4. **Jetpack ecosystem fit.** If the rebuild adopts Hilt (per rebuild notes §Add #2), Room +
   Hilt is the more idiomatic pair. (Counter: Koin is still required for Aniyomi extension
   compat, so Hilt-only is unlikely — see rebuild notes §Key Decisions #2.)
5. **Mature KMP** — Room 2.7.0 (Apr 2025) is the first stable KMP release; Room 3.0
   (Mar 2026) drops KAPT, KSP-only, coroutines mandatory, full KMP. Sources:
   `developer.android.com/jetpack/androidx/releases/room`,
   `android-developers.googleblog.com/2026/03/room-30-modernizing-room.html`,
   `kmpship.app/blog/jetpack-libraries-kmp-support-2025` (Room 2.8.3 stable Oct 22 2025).
6. **No SQLDelight parser quirks.** Room delegates SQL to the actual SQLite engine — it
   doesn't have its own SQL grammar limitations like SQLDelight's `OLD`/`NEW` trigger issue.

### Cons (for THIS project)

1. **Massive porting cost.** Animiru (our base app) and Aniyomi (upstream) both use
   SQLDelight — verified by direct inspection of their `libs.versions.toml` (see §What Animiru
   Uses). The extension loader, source matcher, source-link stores, episode fetch gateway,
   backup providers, etc. are all written against SQLDelight row types. Switching to Room =
   rewriting the data layer of every ported component.
2. **autoMigration can't express the migration patterns this project needs.** The old
   project's `2.sqm` performs `DELETE FROM animes WHERE _id NOT IN (SELECT MIN(_id) ...)`
   dedup before creating a unique index. Room's `autoMigration` explicitly cannot do data
   transformations; you'd write a manual `Migration` object — losing the "auto" advantage.
3. **KSP build overhead.** Room uses KSP (Kotlin Symbol Processing) for compile-time query
   verification. SQLDelight uses a Kotlin compiler plugin — no annotation processing, faster
   incremental builds. (Sources: `medium.com/@ramadan123sayed/...` "NO ANNOTATION PROCESSING
   — Compiles faster. No KSP overhead.", `android-developers.googleblog.com/.../accelerated-
   kotlin-build-times-with...`.)
4. **Schema-as-annotations is less reviewable than schema-as-SQL.** A PR adding a column to
   `animes` in SQLDelight shows a `.sqm` file with `ALTER TABLE animes ADD COLUMN foo TEXT;`
   — instantly readable. In Room, the diff is a Kotlin property addition + a version bump +
   possibly an `autoMigration` spec; the actual SQL generated is invisible.
5. **Partial unique indexes need raw SQL.** The old project relies on partial unique indexes
   (`WHERE col IS NOT NULL`) for `local_id`, `anilist_id`, `source_id != 0`. Room's `@Index`
   annotation doesn't support partial indexes — you have to drop into a `Migration` callback
   and write the `CREATE UNIQUE INDEX ... WHERE ...` SQL by hand. That's the same SQL you'd
   write in SQLDelight, but now you have two places to look.
6. **Idiomatic mismatch with extension ecosystem.** Aniyomi extensions produce `SAnime`,
   `SEpisode` model objects that map naturally to flat SQLDelight rows. Room's `@Entity`
   data classes are also flat, so this isn't a hard blocker — but every example/extension
   we'd reference uses SQLDelight.

---

## SQLDelight: Pros & Cons

### Pros (for THIS project)

1. **The base app (Animiru) and upstream (Aniyomi) both use it.** Verified:
   - `quickdesh/Animiru` (master) `gradle/libs.versions.toml`: `sqldelight = "1.5.4"`,
     `com.squareup.sqldelight:android-driver`, etc.
   - `aniyomiorg/aniyomi` (main) `gradle/libs.versions.toml`: `sqldelight = "2.0.2"`,
     `app.cash.sqldelight:android-driver`, etc.
   The old ANIKUTA project (also based on Aniyomi) uses SQLDelight 2.0.2. **Staying on
   SQLDelight = maximum portability of extension/source/backup code.**
2. **The schema is already proven in SQLDelight.** 6 tables, 2 migrations, complex two-tier
   identity, partial unique indexes, dedup-before-unique-index — all working in production.
   Porting to the new project = copying `.sq` + `.sqm` files almost verbatim.
3. **`.sqm` migrations handle the project's real needs.** The 2.sqm dedup pattern
   (`DELETE WHERE _id NOT IN (SELECT MIN(_id) ... GROUP BY ...)`) is exactly the kind of
   data-aware migration that Room's autoMigration refuses to do. With SQLDelight, you just
   write the SQL.
4. **Faster incremental builds.** Compiler plugin, not KSP. Cited by multiple 2025/2026
   comparison articles as a real win, especially on multi-module projects.
5. **Mature KMP.** SQLDelight has been KMP-first since 1.x. If the project ever expands to
   iOS/desktop, no DB migration needed. (`slack-chats.kotlinlang.org` Aug 2025:
   "SQLDelight is the #1 go-to solution for database implementation in Kotlin Multiplatform
   projects.")
6. **Schema is reviewable SQL.** PRs show `ALTER TABLE`, `CREATE INDEX`, named queries —
   exactly what DBAs and senior reviewers expect. No hidden codegen.
7. **The backup architecture is already DB-agnostic.** `BackupProvider` / `BackupEntry`
   operate on serializable models, not DB rows. The Aniyomi `.tachibk` reader is independent
   (protobuf). Switching DBs gains nothing for backup.
8. **Aniyomi backup compat is library-based, not DB-based.** `kotlinx-serialization-protobuf`
   parses `.tachibk` into Kotlin models → translated to `BackupContainer` → written via
   providers. This works regardless of DB choice.

### Cons (for THIS project)

1. **Known parser quirk: no `OLD`/`NEW` in triggers.** The old project hit this and worked
   around it by enforcing Default-category deletion protection in the app layer. This is a
   minor limitation; SQLite triggers are rarely needed in this app's design.
2. **Multi-module schema generation can be finicky** in KMP setups (Stack Overflow Q
   "SQLDelight multiplatform not generating schema if it is in a separate module"; GitHub
   issue #1316). However, the old ANIKUTA project's pure-Android modular layout doesn't hit
   this — `:core:database` owns the schema, `:data:*` consumes the generated `*Queries`.
3. **Manual migrations for everything.** No `autoMigration` equivalent. Every schema change
   = write a `.sqm` file. For simple `ADD COLUMN` this is one line; for complex changes it's
   where you'd spend time either way.
4. **Smaller community than Room** (especially on Android). Fewer Stack Overflow answers,
   fewer blog tutorials. Counter: the Cash App team maintains it, the SQLDelight Slack is
   active, and the Aniyomi/Animiru ecosystem is a ready-made reference codebase.
5. **Slightly steeper learning curve for SQL-averse developers.** DAOs in Room feel more
   "Kotlin-native" than named queries in `.sq` files. Counter: the `.sq` syntax is
   self-explanatory (`selectByAnimeId: SELECT * FROM animes WHERE _id = :id;`), and the
   compile-time generation catches typos just like Room does.

---

## What Animiru Uses

Direct inspection of `https://raw.githubusercontent.com/quickdesh/Animiru/master/gradle/libs.versions.toml`:

```toml
[versions]
sqlite = "2.3.0-rc01"
sqldelight = "1.5.4"
# ...
[libraries]
sqldelight-android-driver = { module = "com.squareup.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "com.squareup.sqldelight:coroutines-extensions-jvm", version.ref = "sqldelight" }
sqldelight-android-paging = { module = "com.squareup.sqldelight:android-paging3-extensions", version.ref = "sqldelight" }
sqldelight-gradle = { module = "com.squareup.sqldelight:gradle-plugin", version.ref = "sqldelight" }
sqlite-framework = { module = "androidx.sqlite:sqlite-framework", version.ref = "sqlite" }
sqlite-ktx = { module = "androidx.sqlite:sqlite-ktx", version.ref = "sqlite" }
sqlite-android = "com.github.requery:sqlite-android:3.39.2"
```

**Animiru uses SQLDelight 1.5.4** (older `com.squareup.sqldelight` package naming — pre-Cash
App migration) with requery sqlite-android 3.39.2.

For comparison, the upstream **Aniyomi** (verified at
`https://raw.githubusercontent.com/aniyomiorg/aniyomi/main/gradle/libs.versions.toml`)
uses **SQLDelight 2.0.2** (`app.cash.sqldelight` — post-Cash App naming) with requery
sqlite-android 3.45.0. Same stack as the old ANIKUTA project.

**Implication:** Aniyomi migrated from SQLDelight 1.x (Animiru) to 2.x (Aniyomi current +
old ANIKUTA). For the new project, **SQLDelight 2.x is the right target** — it matches
Aniyomi's current stack, so Aniyomi extension code ports cleanly.

---

## Recommendation

### **Stay on SQLDelight (target 2.0.2+; consider latest 2.x stable at build time).**

### Reasoning (tied to project requirements)

| # | Requirement | SQLDelight fit | Room fit |
|---|---|---|---|
| 1 | Multi-extension ecosystems | **Strong.** Aniyomi/Animiru extensions are written against SQLDelight. Source-provenance columns map cleanly to flat rows. | Weak. Forces a porting layer between extension row types and Room entities. |
| 2 | Multi-content-type (modular) | **Strong.** Add a new `.sq` file per content type. ADR-009's separate manga DB = a second `create("MangaDatabase")` block. | OK. Add new `@Entity` classes per content type. No real advantage. |
| 3 | Two-tier identity + complex indexes | **Strong.** Partial unique indexes (`WHERE col IS NOT NULL`) are first-class SQL. Already proven in old project. | Weak. `@Index` doesn't support partial indexes — drop into raw `Migration` SQL. |
| 4 | Backup/restore + Aniyomi compat | **Neutral.** Backup is DB-agnostic (`BackupProvider` abstraction). Aniyomi `.tachibk` parsed via `kotlinx-serialization-protobuf`. | Neutral. Same architecture works. |
| 5 | User activity tracking (new tables over time) | **Strong.** New table = new `.sq` + new `*Queries`. Simple. | OK. New `@Entity` + `@Dao` + register in `@Database(entities = [...])`. |
| 6 | Modular Gradle (`:core:database` + `:data:*`) | **Proven.** Old project ships exactly this layout. | OK. Slightly cleaner module boundaries (entities can live in `:data:*`, `:core:database` aggregates) — but old project proves SQLDelight's pattern works. |
| 7 | Customizable UI (preferences in DB) | OK. Same as Room for simple key-value tables. | OK. Same. |
| 8 | Kotlin + Compose, reactive everywhere | **Strong.** `asFlow().mapToList(io)` is idiomatic. | **Strong.** `Flow<List<Entity>>` from DAOs is idiomatic. |
| 9 | Frequent schema evolution | **Strong.** `.sqm` files handle data-transforming migrations (dedup, backfill). Old project's `2.sqm` is a textbook example. | Weak. `autoMigration` can't do data transformations; manual `Migration` objects = same SQL as SQLDelight but in Kotlin strings. |

### Decisive factors

1. **The base app (Animiru) and upstream (Aniyomi) both use SQLDelight.** This single fact
   outweighs every other consideration. Every line of ported extension/source/backup code
   is written against SQLDelight row types. Switching to Room = rewriting the data layer of
   every ported component for zero functional benefit.
2. **The old ANIKUTA schema + 2 migrations are proven.** Porting = copy `.sq` + `.sqm`
   files. Switching = translate 6 tables, ~70 named queries, 2 migrations (including the
   data-transforming `2.sqm` dedup), rewrite 6 repository impls, rewrite 10 backup-provider
   mappers. Estimated 2–3 weeks of pure refactor work for no user-visible gain.
3. **The project's migration patterns (dedup-before-unique-index, identity backfill) are
   exactly what Room's `autoMigration` cannot express.** Staying on SQLDelight keeps these
   as one-line `.sqm` SQL statements.
4. **The "industry standard Room" argument is weak here.** The relevant "standard" for this
   project is the Aniyomi/Animiru extension ecosystem standard, which is SQLDelight. Room
   would be standard for a greenfield app with no upstream codebase — that's not this
   project.
5. **Build performance.** SQLDelight's compiler plugin (no KSP) = faster incremental builds
   on a 25+ module project. Real benefit at this scale.
6. **The rebuild notes' tentative D-009 decision to switch to Room was made without
   verifying that Animiru uses SQLDelight.** Now that we've verified it, the cost/benefit
   flips firmly toward staying.

### Caveats — when Room would be the right call

- If the team has **zero** SQLDelight experience and **strong** Room experience AND is
  willing to absorb the 2–3 week porting cost — Room's lower learning curve could pay back
  over years.
- If the project pivots away from Aniyomi extension compat entirely (no longer importing
  Aniyomi code) — Room becomes more viable.
- If a future requirement is true cross-platform with shared schema across iOS/Android AND
  the team prefers Room's annotation model — both libraries are KMP-stable now (Room since
  2.7.0 Apr 2025, SQLDelight since 1.x), so this is a wash.

None of these apply to the current project. **Stay on SQLDelight.**

### Recommended SQLDelight version

- Target **SQLDelight 2.0.2 or newer 2.x stable** at build time.
- Keep the requery `sqlite-android` bundled SQLite (newer than Android system SQLite —
  supports partial indexes, `INSERT OR REPLACE`, modern SQL features the old project relies
  on).
- Apply the `app.cash.sqldelight` Gradle plugin in `:core:database` only.

---

## Migration Considerations

### If staying on SQLDelight (recommended) — porting the old schema

1. **Copy the 6 `.sq` files verbatim** from `REFERENCES/old-kuta/ANIKUTA/core/database/src/
   main/sqldelight/.../` into the new project's `:core:database/src/main/sqldelight/.../`.
   Update the package path (`app.confused.anikuta.*` → new package).
2. **Copy the 2 `.sqm` migration files** (`1.sqm`, `2.sqm`) verbatim.
3. **Copy `DatabaseDriverFactory.kt`** with the new package and DB name (`anikuta.db` or
   whatever the new project decides).
4. **Audit each `.sq` file for redesign opportunities** per the rebuild notes:
   - The `animes` table is heavily denormalized (~30 cols). Consider splitting source
     provenance into a separate `source_provenance` table (one-to-many from `animes`).
     This is a schema redesign decision, not a DB-library decision — make it deliberately.
   - The `genre TEXT` comma-separated column should probably become a `anime_genre`
     junction table. Same redesign applies regardless of DB library.
5. **Port the `:data:anime` repository impls** (`AnimeRepositoryImpl`, `EpisodeRepositoryImpl`,
   `CategoryRepositoryImpl`) and mappers (`AnimeMapper`, etc.) — these call `.animesQueries`
   etc. on the generated `AnikutaDatabase`. Largely copy-paste.
6. **Port the `:core:backup` provider mappers** (`BackupMappers.kt`) — they map SQLDelight row
   types → backup models. Largely copy-paste.
7. **Total estimated effort: 3–5 days** for one engineer to port the data layer (vs. 2–3
   weeks if switching to Room).

### If switching to Room despite the recommendation

| Old SQLDelight construct | Room equivalent | Notes |
|---|---|---|
| `.sq` `CREATE TABLE` | `@Entity data class` | Direct translation. |
| Named query (`selectByAnimeId: SELECT ...`) | `@Query("SELECT ...") suspend fun selectByAnimeId(id: Long): Anime?` | Direct translation. |
| `:param` SQLDelight bind syntax | `:param` Room bind syntax | Same. |
| `INSERT OR REPLACE INTO ...` | `@Insert(onConflict = OnConflictStrategy.REPLACE)` | Direct. |
| `INSERT OR IGNORE INTO ...` | `@Insert(onConflict = OnConflictStrategy.IGNORE)` | Direct. |
| Partial unique index `CREATE UNIQUE INDEX ... WHERE col IS NOT NULL` | **No annotation equivalent.** Write the SQL in a `Migration` callback via `db.execSQL("CREATE UNIQUE INDEX ... WHERE ...")`. | Same SQL, different home. |
| `1.sqm` (additive `ALTER TABLE ADD COLUMN`) | `autoMigration(from = 1, to = 2)` — Room detects the new columns on `@Entity` and generates the `ALTER TABLE` statements. | Works for pure additive. |
| `2.sqm` (data-transforming dedup + `ALTER TABLE`) | **Manual `Migration(2, 3)` object** with `db.execSQL(...)` for each statement. Room's `autoMigration` cannot do `DELETE WHERE _id NOT IN (SELECT MIN(_id) ...)`. | Same SQL, in Kotlin strings instead of `.sqm` files. Loses the "auto" benefit. |
| `AndroidSqliteDriver` | `Room.databaseBuilder(...).build()` | Direct swap. |
| `asFlow().mapToList(io)` | `@Query(...) fun observeAll(): Flow<List<Anime>>` | Room returns `Flow` directly from DAO. |
| requery `sqlite-android` bundled SQLite | requery `sqlite-android` bundled SQLite (still useful — Android system SQLite on minSdk 24 lacks some features) | Same. |
| `:data:anime` calls `.animesQueries.selectAll()` | `:data:anime` calls `animeDao.selectAll()` | Rename pass. |
| 6 mappers (`AnimeMapper` etc.) | 6 mappers (`AnimeMapper` etc.) — map `@Entity` → domain model instead of SQLDelight row type | Largely the same code; column-access syntax changes. |
| 10 backup provider mappers | 10 backup provider mappers | Same. |
| `AnikutaDatabase.Schema` (for driver creation) | `@Database` class's `openHelper` | Different wiring. |

**Estimated effort: 2–3 weeks** for one engineer (data layer + migrations + backup mappers
+ repository impls), plus testing. This is pure cost with no functional gain.

### Decision log

- **2026-Q1 (this research):** Recommendation is **SQLDelight 2.x**. Supersedes the
  tentative D-009 decision cited in `09-rebuild-notes.md` §Add #3 ("Room instead of
  SQLDelight"). The D-009 decision was made before verifying that Animiru/Aniyomi use
  SQLDelight; now that we've verified it, the cost/benefit analysis flips.
- **Action item:** Update `09-rebuild-notes.md` §Add #3 and §Key Decisions #3 to reflect
  this recommendation. The module mapping (`:core:database` → `:core:storage` "Switch to
  Room") should be revised to `:core:database` → `:core:database` "Stay on SQLDelight 2.x".

---

## Sources

### Old project source

- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/sqldelight/.../animes.sq`
- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/sqldelight/.../episodes.sq`
- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/sqldelight/.../categories.sq`
- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/sqldelight/.../anime_category.sq`
- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/sqldelight/.../animehistory.sq`
- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/sqldelight/.../animetrack.sq`
- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/sqldelight/.../1.sqm`, `2.sqm`
- `REFERENCES/old-kuta/ANIKUTA/core/database/src/main/java/.../DatabaseDriverFactory.kt`
- `REFERENCES/old-kuta/ANIKUTA/core/database/build.gradle.kts`
- `REFERENCES/old-kuta/DOCUMENTATION/03-tech-stack.md` §4 Persistence stack
- `REFERENCES/old-kuta/DOCUMENTATION/04-core-modules.md` §`:core:database`, §`:core:backup`
- `REFERENCES/old-kuta/DOCUMENTATION/05-data-modules.md` §`:data:anime`
- `REFERENCES/old-kuta/DOCUMENTATION/09-rebuild-notes.md` §Add #3, §Key Decisions #3

### Web research (2025–2026)

- `proandroiddev.com/which-local-database-should-you-choose-in-2025-comparing-realm-sqldelight-and-room-...` (Jan 2025) — comparison of Realm/SQLDelight/Room; notes Room KMP since 2.7.0-alpha01 (May 2024), SQLDelight package rename to `app.cash.sqldelight` in 2.0 (Jul 2023).
- `docs.bswen.com/blog/2026-03-14-room-vs-sqldelight-kmp` (Mar 2026) — Room 3.0 vs SQLDelight for KMP; "for Android-first teams, Room offers the smoothest path to KMP. For SQL-savvy teams building truly cross-platform apps, SQLDelight provides [better SQL control]."
- `developer.android.com/kotlin/multiplatform/room` (Jul 2026) — official Room KMP setup guide.
- `developer.android.com/jetpack/androidx/releases/room` — Room 2.7.2 stable, 3.0 modernizing.
- `android-developers.googleblog.com/2026/03/room-30-modernizing-room.html` (Mar 2026) — Room 3.0 drops KAPT, KSP-only, coroutines mandatory.
- `kmpship.app/blog/jetpack-libraries-kmp-support-2025` (Oct 2025) — Room 2.8.3 stable Oct 22 2025; KMP for Android, iOS, JVM.
- `slack-chats.kotlinlang.org/t/29863703/...` (Aug 2025) — "SQLDelight is the #1 go-to solution for database implementation in Kotlin Multiplatform projects."
- `slack-chats.kotlinlang.org/t/23185342/...` (Oct 2024) — "Room 2.7.0 is now stable and it's the first official stable Room version supporting KMP."
- `medium.com/@ramadan123sayed/kmp-part-4-...` — SQLDelight: "NO ANNOTATION PROCESSING — Compiles faster. No KSP overhead."
- `medium.com/androiddevelopers/room-auto-migrations-d5370b0ca6eb` — Room auto-migrations; documents limits (no data transformations).
- `developer.android.com/training/data-storage/room/migrating-db-versions` — "Automatic migrations work for most basic schema changes" (implying manual for complex).
- `github.com/sqldelight/sqldelight/discussions/2476` (Jun 2025) — destructive migration support request; documents how SQLDelight migrations are wired.
- `thomaskioko.me/posts/sql-delight-migrations` (Mar 2025) — SQLDelight migration walkthrough.
- `stackoverflow.com/questions/67065918/sqldelight-multiplatform-not-generating-schema-if-it-is-in-a-separate-module` — known multi-module friction (KMP-specific).
- `github.com/square/sqldelight/issues/1316` (2019) — multi-module documentation request.
- `proandroiddev.com/configuring-multiple-sqlite-databases-in-android-with-sqldelight-2-...` (Mar 2025) — multiple DB files via SQLDelight (relevant for ADR-009 separate manga DB).

### Direct inspection

- `https://raw.githubusercontent.com/quickdesh/Animiru/master/gradle/libs.versions.toml` — **Animiru uses SQLDelight 1.5.4** (`com.squareup.sqldelight`) + requery sqlite-android 3.39.2.
- `https://raw.githubusercontent.com/aniyomiorg/aniyomi/main/gradle/libs.versions.toml` — **Aniyomi uses SQLDelight 2.0.2** (`app.cash.sqldelight`) + requery sqlite-android 3.45.0.
