# Skills Index

> Reusable reference docs the agent reads on demand. Each skill is **standalone** — no skill depends on another (per `CORE_RULES.md` §10).

## Current Skills

| File | Purpose |
|------|---------|
| `ponytail.md` | Lazy senior dev: simplest solution that works. YAGNI, stdlib-first, root-cause fixes. |
| `subagent-review.md` | How to use sub-agents to find flaws in plans. |

## Rules for Creating a New Skill

1. **Solid reason**: the skill must be reliable and genuinely useful. Not a vanity doc.
2. **Understand it fully** before writing it.
3. **Sub-agent review** if non-trivial. Verify findings before adding.
4. **Concrete examples**: every principle gets at least one real example from our stack (Kotlin/Android/Next.js). No generic philosophy.
5. **Standalone**: no dependency on other skills. Each skill works alone.
6. **Add to this index** after creating.

## What Does NOT Belong Here
- Generic advice ("write clean code").
- Duplicates of `CORE_RULES.md` content.
- Frameworks or test systems (we keep it simple — see `CORE_RULES.md` §10).
- Anything that's a dependency rather than a reference.
