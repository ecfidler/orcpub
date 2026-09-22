# Domain docs

How the engineering skills use this repo's domain documentation when they explore the codebase.

## Read these first

- `CONTEXT.md` at the repo root.
- `CONTEXT-MAP.md` at the repo root, if it exists. It points at one `CONTEXT.md` per context. Read each one relevant to your topic.
- `docs/adr/`, the architecture decision records (ADRs). Read the ADRs that touch the area you are about to work in. In a multi-context repo, also check `src/<context>/docs/adr/` for context-scoped decisions.

If any of these files is missing, proceed. Do not mention the absence, and do not suggest creating the files up front. The `/domain-modeling` skill, which `/grill-with-docs` and `/improve-codebase-architecture` invoke, creates them when a term or decision is resolved.

## File structure

A single-context repo, the common case:

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

A multi-context repo, marked by a `CONTEXT-MAP.md` at the root:

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← system-wide decisions
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← context-specific decisions
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept, in an issue title, a refactor proposal, a hypothesis, or a test name, use the term as `CONTEXT.md` defines it. Do not use a synonym the glossary lists under _Avoid_.

If the glossary does not have the concept you need, one of two things is true. Either you are inventing language the project does not use, so reconsider, or the glossary has a gap, so note it for `/domain-modeling`.

## Flag ADR conflicts

If your output contradicts an existing ADR, say so rather than overriding the ADR without comment:

> _Contradicts ADR-0007 (event-sourced orders), but worth reopening because…_

## This repo

This repo uses the single-context layout. `CONTEXT.md` and `docs/adr/` do
not exist yet. `/domain-modeling` creates them when first needed. Until
then, the working vocabulary is in `CLAUDE.md` and in the plan documents
under `docs/ts-rewrite-plan/`: strict entity, engine facade, Phase A and
Phase B, and the compatibility quirks R1 to R10. The decisions already
taken are on the Linear issues labelled `Decision` and in the plan
overview document linked from `CLAUDE.md`. Use that vocabulary. Do not
invent new names for those things.
