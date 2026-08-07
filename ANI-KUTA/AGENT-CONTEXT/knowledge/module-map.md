# Module Map

> Every module: name, job, dependencies. **Finalized in Phase 1.**
> The architecture **design/concept** (layer diagrams, module graph) lives in `architecture.md`.

## Status: Draft (finalized in Phase 1)

## Proposed Modules

### Core Modules
| Module | Job | Depends On |
|--------|-----|------------|
| `:app` | App shell, DI setup, navigation host | all feature modules |
| `:core:ui` | Shared UI components | `:core:design` |
| `:core:design` | Theme tokens (color, type, shape, motion) | — |
| `:core:data` | Repositories (contract + impl) | `:core:network`, `:core:storage` |
| `:core:network` | API client + interceptors | — |
| `:core:storage` | Local persistence (Room) | — |
| `:core:common` | Shared utilities, error models | — |
| `:core:config` | App configuration + customization toggles | — |

### Feature Modules (TBD based on app purpose)
| Module | Job | Depends On |
|--------|-----|------------|
| `:feature:home` | Home screen | `:core:ui`, `:core:data` |
| `:feature:settings` | Settings + customization UI | `:core:ui`, `:core:config` |
| `:feature:<...>` | (after Q1/Q2 answered) | |

## Rules
- Feature modules never depend on each other. Communicate via `:core` contracts or navigation.
- Core modules may depend on other core modules, but no cycles.
- Every module has a `README.md`.
