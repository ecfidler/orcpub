---
type: Convention
title: Who owns each document in this fork
description: Which documents in ecfidler/orcpub came from upstream Dungeon Master's Vault and must not change, and which documents the fork owns.
tags: [docs, fork, conventions]
status: stable
generated: { by: claude-code/agent, at: 2026-10-05T23:00:00Z }
sources:
  - id: okf-profile
    resource: https://github.com/ecfidler/orc-alchemy/blob/main/docs/conventions/okf-profile.md
    title: How this project uses the Open Knowledge Format
---

# Summary

This fork has two sets of documents.

- **Upstream documents** came from Dungeon Master's Vault (OrcPub). Do not
  edit them. They do not follow the project documentation format.
- **Fork documents** belong to the PubDoor work. They follow the project
  format and the project writing standard.[^okf-profile]

Most project knowledge is in
[`ecfidler/orc-alchemy`](https://github.com/ecfidler/orc-alchemy/tree/main/docs),
under `docs/`. If a new document describes only the fork, put it in this
bundle, `docs/pubdoor/`. Otherwise, put it in orc-alchemy.

The fork starts after upstream commit `d42e05d` (2026-04-09). A document
that existed at that commit is an upstream document.

# Upstream documents

Do not edit these files. If an upstream document is wrong, write the
correction in a fork document and link to the upstream file.

- `README.md` and `CHANGELOG.md`
- `.github/ISSUE_TEMPLATE/*.md`
- `.github/PULL_REQUEST_TEMPLATE.md`, which the fork deleted
- `docs/*.md`, for example `docs/STACK.md` and
  `docs/ORCBREW_FILE_VALIDATION.md`
- `docs/migration/*.md`
- `docs/kb/README.md` and `docs/kb/datomic-crash-analysis.md`
- `test/docker/README.md`

Before this rule, the fork changed four upstream files. It edited
`README.md`, `docs/ORCBREW_FILE_VALIDATION.md`, and `docs/kb/README.md`,
and it deleted `.github/PULL_REQUEST_TEMPLATE.md`. Those changes stay. Do
not make more.

# Fork documents

| Path | Format | Note |
|---|---|---|
| `docs/pubdoor/` | OKF bundle | Fork-only knowledge. Start at [the bundle index](index.md). |
| `CLAUDE.md` | Plain markdown | Agent instructions. Tools read it without frontmatter. |
| `engine-js/README.md` | Plain markdown | The npm package README. npm shows frontmatter as text. |
| `fixtures/README.md` | Plain markdown | orc-alchemy keeps a copy. To change it, edit this file and copy it to orc-alchemy. |
| `docs/agents/*.md` | Plain markdown | Skills read these paths. Do not move them. |
| `docs/kb/srd-5.2-rules-delta.md` | Plain markdown | A stub. The document moved to orc-alchemy. |
| `docs/reports/2024-rules-support.md` | Plain markdown | A stub. The document moved to orc-alchemy. |
| `docs/ts-frontend-plan/README.md` | Plain markdown | A stub. Plan Set 1 moved to orc-alchemy. |
| `docs/ts-rewrite-plan/README.md` | Plain markdown | A stub. Plan Set 2 moved to orc-alchemy. |
| `.claude/skills/` | Skill format | Agent skills. |

[^okf-profile]: How this project uses the Open Knowledge Format
