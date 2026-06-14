---
name: merge-to-main
description: >-
  Merge branch v1 into main and push both branches for AiDecisionMakingAgenticAI.
  Use when the user asks to merge to main, sync main with v1, or publish to both branches.
---

# Merge v1 → main (AiDecisionMakingAgenticAI)

## Branch model

| Branch | Role |
|--------|------|
| `v1` | Active development / integration |
| `main` | Stable mirror; keep aligned with `v1` when user requests merge |

## Prerequisites

- Changes committed on `v1` (or the source branch the user specifies).
- `v1` pushed to `origin` if others rely on remote state.

## Workflow

```bash
cd /Users/songxianggu/Project/AiDecisionMakingAgenticAI

# 1. Ensure v1 is clean and pushed
git checkout v1
git status
git push origin v1

# 2. Merge into main
git checkout main
git pull origin main
git merge v1 -m "Merge branch 'v1' into main"

# 3. Push main
git push origin main

# 4. Return to v1
git checkout v1
git status -sb
```

Fast-forward is expected when `main` has no unique commits. If merge conflicts occur, resolve in working tree, `git add`, `git commit`, then push.

## Safety

- **NEVER** force-push `main`.
- **NEVER** rewrite published history on `main` without explicit user request.
- Do not merge unrelated local changes (stash or commit first).

## Verify both branches

```bash
git rev-parse v1 main
git log --oneline -1 v1
git log --oneline -1 main
```

`v1` and `main` should point to the same commit after a fast-forward merge.

## Optional: confirm on GitHub

```bash
gh repo view --json defaultBranchRef 2>/dev/null || true
```

Report the commit SHA and that both `origin/v1` and `origin/main` were updated.
