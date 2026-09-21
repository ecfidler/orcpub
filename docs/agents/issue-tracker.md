# Issue tracker: Linear

Issues, specs and tickets for this repo live in **Linear**: workspace **Orc Alchemy**
(https://linear.app/orc-alchemy), team **Orc Alchemy** (key `ORC`). Issues are
identified as `ORC-nn`. Use the Linear MCP server tools (`mcp__Linear__*`) for every
operation; there is no CLI. GitHub (`ecfidler/orcpub`) is the code host only: `gh issue`
and GitHub Issues are **not** used for tracking.

## Where work goes

Two projects, one per phase of the TypeScript rewrite (see `CLAUDE.md`):

| Project | What it tracks | Repo |
| --- | --- | --- |
| **PubDoor** | Phase A: the compiled engine package `@dmv/pubdoor` | this repo |
| **Alchemy 5e** | Phase B: the new TypeScript/React app | its own repo |

New issues for work in this repo go in **PubDoor** unless the user says otherwise. Ask
when the target project is unclear.

Area labels in use: `Engine`, `Engine patch`, `Fixtures`, `Homebrew`, `Import/Export`,
`App UI`, `Backend`, `CI`, `Docs`, `Decision`, plus the type labels `Bug`, `Feature`,
`Improvement`. Apply the area label that fits. The triage-state labels are mapped in
`triage-labels.md`.

Workflow states: `Backlog`, `Todo`, `In Progress`, `In Review`, `Done`, plus `Canceled`
and `Duplicate`. Move an issue to *In Progress* when starting it and to *Done* when its
acceptance criteria hold.

## Conventions

- **Create an issue**: `save_issue` with `team: "Orc Alchemy"`, `title`, `description`
  (Markdown, literal newlines), `project`, and `labels`. Omit `id`.
- **Read an issue**: `get_issue` with `id: "ORC-nn"` (`includeRelations: true` to see
  blocking edges), then `list_comments` with `issueId: "ORC-nn"` for the discussion.
- **List issues**: `list_issues` filtered by `team`, `project`, `state`, `label` or
  `assignee` (`"me"`, or `null` for unassigned).
- **Comment on an issue**: `save_comment` with `issueId` and `body`.
- **Apply / remove labels**: `save_issue` with `id` and `addLabels` / `removeLabels`.
  A label must already exist in the workspace; `save_issue` does not create it.
- **Change state**: `save_issue` with `id` and `state` (for example `"Done"`).
- **Close**: set `state` to `Done` (finished), `Canceled` (won't do) or `Duplicate`,
  with a comment saying why.
- **Reference from git**: put the identifier (`ORC-nn`) in commit messages and in PR
  titles or bodies. Linear links them to the issue automatically.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as
feature requests; `/triage` reads this flag.)_

PRs live on GitHub. When set to `yes`, read them with the GitHub MCP tools
(`pull_request_read`), or `gh pr view` where the `gh` CLI exists, and keep the triage
state on a linked Linear issue rather than on the PR.

## When a skill says "publish to the issue tracker"

Create a Linear issue with `save_issue` in the relevant project. For a spec, put the
whole spec in `description`. For a set of tickets, create them in dependency order
(blockers first) and wire each ticket's `blockedBy` to the identifiers of the tickets
that gate it: native blocking relations, not "Blocked by" text.

## When a skill says "fetch the relevant ticket"

`get_issue` with the `ORC-nn` identifier (or the identifier from the issue URL), then
`list_comments` for the discussion. The user will normally pass the identifier or URL
directly.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: one issue in the relevant project, labelled `wayfinder:map`, holding the
  Destination / Notes / Decisions-so-far / Not-yet-specified / Out-of-scope body. The
  labels `wayfinder:map`, `wayfinder:research`, `wayfinder:prototype`,
  `wayfinder:grilling` and `wayfinder:task` do not exist in Linear yet; create them
  before the first wayfinder run.
- **Child ticket**: a Linear sub-issue of the map: `save_issue` with `parentId` set to
  the map's identifier and the question in `description`. Label it `wayfinder:<type>`.
- **Blocking**: Linear's native issue relations. `save_issue` with `id` (the child) and
  `blockedBy: ["ORC-nn", ...]`. A ticket is unblocked when every blocker is `Done` or
  `Canceled`.
- **Frontier query**: `list_issues` with `parentId` set to the map, then drop children
  that are `Done` or `Canceled`, that have an assignee, or that still have an open
  blocker (`get_issue` with `includeRelations: true`). First in map order wins.
- **Claim**: `save_issue` with `id` and `assignee: "me"`, the session's first write.
- **Resolve**: `save_comment` with the answer on the ticket, `save_issue` with
  `state: "Done"`, then append a context pointer (gist + link) to the map's
  Decisions-so-far by updating the map's `description` (use `patch` with an
  `insert_before` or `append` op rather than rewriting the whole body).
