# Coding Rules

> Code standards for the ANI-KUTA Android app.

## Language & Stack
- **Kotlin** as the primary language (pending user confirmation of exact stack).
- **Jetpack Compose** for UI (recommended for modern, customizable UI).
- **Min SDK / Target SDK**: to be decided in Phase 1.

## Structure
- One module = one responsibility.
- Each module has a `README.md` describing: purpose, inputs, outputs, dependencies.
- Package naming: `com.anikuta.<module>` (pending confirmation of app id).

## Naming Convention (canonical)
| Thing | Convention | Example |
|-------|-----------|--------|
| Repo / project display | `ANI-KUTA` (uppercase, hyphen) | `ANI-KUTA` |
| Gradle module | `:lower:case:colon` | `:core:ui`, `:feature:home` |
| Kotlin package | `com.anikuta.<module>` (lowercase, dot) | `com.anikuta.core.ui` |
| App ID (applicationId) | `com.anikuta` (pending confirm) | `com.anikuta` |
| CI artifact name | `anikuta-apk` | `anikuta-apk` |
| Workflow file | `build-apk.yml` | `build-apk.yml` |

Always use these forms to avoid drift. If changed, update this table first.

## Frontend / Backend Separation
- **Frontend (UI layer):** renders data, handles user interaction only. No direct data fetching logic baked in.
- **Backend (data layer):** fetches, processes, stores data. Exposes clean interfaces to the UI.
- They communicate via **defined contracts** (interfaces / repositories), so UI can be swapped or customized without touching data logic.
- Customization hooks (themes, layouts, behavior toggles) live in the UI layer.

## Code Style
- Consistent naming. Meaningful names.
- Small functions. Single responsibility.
- Comments explain *why*, not *what*.
- No dead code. No leftover TODOs without a tracked issue.

## Documentation
- Every public module/class has KDoc.
- Architecture decisions recorded in `AGENT-CONTEXT/memory/decisions.md`.

## Testing
- Unit tests for core logic (data layer, repositories, use cases).
- No tests required for pure UI boilerplate, but critical flows should have UI tests.

## Version Control
- See `rules/git-rules.md`.
