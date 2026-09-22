# Triage labels

The skills refer to five canonical triage roles. This file maps those
roles to the label strings in this repo's issue tracker, Linear (see
`issue-tracker.md`).

| Label in mattpocock/skills | Label in our tracker | Meaning                                       |
| -------------------------- | -------------------- | --------------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue       |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information      |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an unattended (AFK) agent |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation                 |
| `wontfix`                  | `wontfix`            | Will not be actioned                          |

When a skill mentions a role, for example "apply the AFK-ready triage
label", use the label string from this table.

These five labels exist as workspace labels in Linear, created 2026-09-21.
`save_issue` applies only labels that already exist. If a label is renamed
or deleted, fix it before the next `/triage`, `/to-spec`, or `/to-tickets`
run: either update the right-hand column here, or recreate the label in
Linear under Settings > Labels or with the `save_issue_label` tool.

`/triage` also uses two category roles. Linear already has labels for
them, so use those rather than creating lowercase duplicates:

| Role in mattpocock/skills | Label in our tracker | Meaning                                                              |
| ------------------------- | -------------------- | -------------------------------------------------------------------- |
| `bug`                     | `Bug`                | Something is broken                                                  |
| `enhancement`             | `Feature`            | A new capability. Use `Improvement` for a change to existing behaviour |

If the tracker's vocabulary changes, edit the right-hand columns to match.
