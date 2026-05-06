---
name: "Keep application-dev.yaml in every module"
description: "Preserve per-module dev profile files even when no automation activates them"
type: feedback
---

# Keep application-dev.yaml in every module

Each module ships an `src/main/resources/application-dev.yaml`.
Keep it, even when nothing in the repo (Taskfile, compose,
README) currently activates the `dev` profile. Do not flag
these files as "unused config" or propose deleting them.

**Why:** The user wants a ready-to-use dev profile available
in every module so a reader can run `--spring.profiles.active=dev`
locally without having to author one. They are intentional
scaffolding, not dead code.

**How to apply:** When sweeping unused configuration,
exclude `application-dev.yaml` from the deletion list. If a
new module is added, give it an `application-dev.yaml` that
overrides whatever the dev experience needs (typically the
structured-logging format).
