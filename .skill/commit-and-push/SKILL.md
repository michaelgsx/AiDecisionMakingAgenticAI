---
name: commit-and-push
description: >-
  Commit and push changes in AiDecisionMakingAgenticAI following git safety rules.
  Use when the user asks to commit, push, save work to remote, or publish a branch.
---

# Commit and push (AiDecisionMakingAgenticAI)

## Only commit when the user explicitly asks

If unclear, ask before committing.

## Pre-commit checks (parallel)

```bash
cd /Users/songxianggu/Project/AiDecisionMakingAgenticAI
git status
git diff && git diff --staged
git log --oneline -5
```

## Stage

- Include only files related to the task.
- **Never** stage: `backend/.env`, `db/.env`, `backend/target/`, secrets, local credentials.
- Do not stage unrelated local artifacts.

## Commit message

- 1–2 sentences; focus on **why**.
- Match repo style (imperative, concise English).
- Use HEREDOC:

```bash
git commit -m "$(cat <<'EOF'
Short summary sentence.

Optional second sentence with context.
EOF
)"
```

## Push

```bash
git push -u origin HEAD
```

Default working branch: **`v1`**. Push to the branch you committed on unless the user names another.

## Safety

- **NEVER** `git config` changes, `--force` push, or `--no-verify` unless the user explicitly requests.
- **NEVER** `git push --force` to `main`.
- **NEVER** `git commit --amend` unless all amend rules in user rules are satisfied.
- If a hook fails, fix and create a **new** commit (do not amend a failed commit).

## Verify

```bash
git status -sb
```

Confirm branch is up to date with `origin/<branch>`.
