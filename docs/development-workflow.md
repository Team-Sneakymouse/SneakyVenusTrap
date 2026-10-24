# Early-development workflow exception

Repository: `Team-Sneakymouse/SneakyVenusTrap`.

Approver: Momo, Discord message `1563544013930504234` in project thread `1563172540455850050`.

Approved instruction:

> since this is an early dev stage, there will be no more need for worktrees or PR's.
> Send it all straight to main

For this repository's early-development stage:

- Work in one existing checkout; do not create additional worktrees.
- Commit verified, scoped changes directly to `main` and push fast-forward. Do not create PRs.
- Preserve existing unrelated/dirty work and consolidate already-running work safely rather than deleting or resetting old checkouts.
- This exception overrides the usual feature-branch/PR workflow for this repository. It does not waive relevant checks, authorize force-pushes, or extend to other projects.
- A main push triggers the plugin publication workflow and can stage the resulting release on dev. Read back the exact remote commit and distinguish publication/staging from running-version verification.
- Production deployment and unrelated server/plugin changes remain outside this approval.

Keep this exception scoped to the early-development stage; use a later explicit team instruction to restore or replace the workflow.
