# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to
the actual label strings used in this repo's issue tracker (Linear, see
`issue-tracker.md`).

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the
corresponding label string from this table.

These five labels exist as workspace-level labels in Linear (created 2026-09-21).
Linear only applies labels that already exist, so if one is ever renamed or deleted,
update the right-hand column here or recreate it (Settings > Labels, or the Linear
`save_issue_label` tool) before the next `/triage`, `/to-spec` or `/to-tickets` run.

`/triage` also uses two **category** roles. Linear already has labels for these, so map
onto them rather than creating lowercase duplicates:

| Role in mattpocock/skills | Label in our tracker | Meaning                                       |
| ------------------------- | -------------------- | --------------------------------------------- |
| `bug`                     | `Bug`                | Something is broken                           |
| `enhancement`             | `Feature`            | New capability (`Improvement` for a change to existing behaviour) |

Edit the right-hand columns to match whatever vocabulary you actually use.
