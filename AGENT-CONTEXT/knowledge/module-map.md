# Module Map

> Every module: name, job, dependencies. **Updated as modules are added.**

## Status: Draft (to be finalized in Phase 1)

## Proposed Modules

### Core Modules
| Module | Job | Depends On |
|--------|-----|------------|
| `:app` | App shell, DI setup, navigation host | all feature modules |
| `:core:ui` | Shared UI components + theme engine | — |
| `:core:design` | Theme tokens (color, type, shape, motion) | — |
| `:core:data` | Repositories (contract + impl) | `:core:network`, `:core:storage` |
| `:core:network` | API client + interceptors | — |
| `:core:storage` | Local persistence (Room) | — |
| `:core:common` | Shared utilities, error models | — |
| `:core:config` | App configuration + customization toggles | — |

### Feature Modules (examples — TBD based on app purpose)
| Module | Job | Depends On |
|--------|-----|------------|
| `:feature:home` | Home screen | `:core:ui`, `:core:data` |
| `:feature:settings` | Settings + customization UI | `:core:ui`, `:core:config` |
| `:feature:<...>` | (to be defined after we know app purpose) | |

## Rules
- Feature modules never depend on each other directly. Communicate via `:core` contracts or navigation.
- Core modules may depend on other core modules, but no cycles.
- Every module has a `README.md`.

## Visual Map
A live visual version of this map will be rendered in the web dashboard (Phase 2+).
