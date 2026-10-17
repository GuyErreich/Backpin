# Backpin branch rulesets (DRAFT, not applied)

- `backpin-dev.json`: protects `dev`. The semver app bypasses with `always`, because autosemver's finalize step commits the semver lock straight onto `dev` (Action-Semver-Control cli/finalize.py).
- `backpin-main.json`: protects `main`. The semver app bypasses with `pull_request` only, so it can merge its own promote PRs but can't push directly.

Both rulesets require a PR with Guy's approval (CODEOWNERS), resolved threads and the seven CI checks, and they block force-pushes and deletion. The PR title check isn't required until #16 is sorted out.

Before applying:
1. Replace `SEMVER_APP_ID` with the semver GitHub App's ID.
2. Confirm the seven check names on the first real CI run.

If staging-to-main auto-promote is ever turned on, autosemver will move `main` directly and the `main` ruleset will need an `always` bypass too.
