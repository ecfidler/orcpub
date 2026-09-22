# Issue tracker: Linear

Issues, specs, and tickets for this repo are in Linear, workspace Orc
Alchemy (https://linear.app/orc-alchemy), team Orc Alchemy (key `ORC`).
Issue identifiers have the form `ORC-nn`. Use the Linear MCP server tools
(`mcp__Linear__*`) for every operation. There is no CLI. GitHub
(`ecfidler/orcpub`) hosts the code only. Do not use `gh issue` or GitHub
Issues for tracking.

## Where work goes

There are two projects, one per phase of the TypeScript rewrite described
in `CLAUDE.md`:

| Project | What it tracks | Repo |
| --- | --- | --- |
| PubDoor | Phase A: the compiled engine package `@dmv/pubdoor` | this repo |
| Alchemy 5e | Phase B: the new TypeScript and React app | its own repo |

New issues for work in this repo go in PubDoor unless the user says
otherwise. If the target project is unclear, ask.

The area labels are `Engine`, `Engine patch`, `Fixtures`, `Homebrew`,
`Import/Export`, `App UI`, `Backend`, `CI`, `Docs`, and `Decision`. The
type labels are `Bug`, `Feature`, and `Improvement`. Apply the area label
that fits. `triage-labels.md` maps the triage-state labels.

The workflow states are `Backlog`, `Todo`, `In Progress`, `In Review`,
`Done`, `Canceled`, and `Duplicate`. Move an issue to `In Progress` when
you start it and to `Done` when its acceptance criteria hold.

## Conventions

- **Create an issue.** Call `save_issue` with `team: "Orc Alchemy"`,
  `title`, `description` (Markdown, with literal newlines), `project`, and
  `labels`. Omit `id`.
- **Read an issue.** Call `get_issue` with `id: "ORC-nn"`, and
  `includeRelations: true` to see blocking relations. Then call
  `list_comments` with `issueId: "ORC-nn"` for the discussion.
- **List issues.** Call `list_issues` filtered by `team`, `project`,
  `state`, `label`, or `assignee`. For `assignee`, `"me"` is you and
  `null` is unassigned.
- **Comment on an issue.** Call `save_comment` with `issueId` and `body`.
- **Add or remove labels.** Call `save_issue` with `id` and `addLabels`,
  `removeLabels`, or both. The label must already exist in the workspace.
  `save_issue` does not create labels.
- **Change the state.** Call `save_issue` with `id` and `state`, for
  example `"Done"`.
- **Close an issue.** Set `state` to `Done` when the work is finished,
  `Canceled` when it will not be done, or `Duplicate`. Add a comment
  saying why.
- **Reference an issue from git.** Put the identifier (`ORC-nn`) in commit
  messages and in PR titles or bodies. Linear links the commit or PR to
  the issue.

## Pull requests as feature requests

**PRs as a request surface: no.** Set it to `yes` if this repo treats
external PRs as feature requests. `/triage` reads this flag.

PRs are on GitHub. If the flag is `yes`, read a PR with the GitHub MCP
tool `pull_request_read`, or with `gh pr view` where the `gh` CLI exists.
Keep the triage state on a linked Linear issue, not on the PR.

## When a skill says "publish to the issue tracker"

Create a Linear issue with `save_issue` in the relevant project. For a
spec, put the whole spec in `description`. For a set of tickets, create
them in dependency order, blockers first. Set each ticket's `blockedBy` to
the identifiers of the tickets that block it. Use these native blocking
relations, not a "Blocked by" line in the text.

## When a skill says "fetch the relevant ticket"

Call `get_issue` with the `ORC-nn` identifier, or with the identifier from
the issue URL. Then call `list_comments` for the discussion. The user
normally passes the identifier or URL directly.

## Wayfinding operations

`/wayfinder` uses these operations. The map is a single issue, and its
child issues are the tickets.

- **Map.** One issue in the relevant project, labelled `wayfinder:map`,
  whose body holds the Destination, Notes, Decisions so far, Not yet
  specified, and Out of scope sections. The labels `wayfinder:map`,
  `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, and
  `wayfinder:task` do not exist in Linear yet. Create them before the
  first wayfinder run.
- **Child ticket.** A Linear sub-issue of the map. Call `save_issue` with
  `parentId` set to the map's identifier and the question in
  `description`. Label it `wayfinder:<type>`.
- **Blocking.** Use Linear's native issue relations. Call `save_issue`
  with the child's `id` and `blockedBy: ["ORC-nn", ...]`. A ticket is
  unblocked when every blocker is `Done` or `Canceled`.
- **Frontier query.** Call `list_issues` with `parentId` set to the map.
  Drop children that are `Done` or `Canceled`, that have an assignee, or
  that still have an open blocker. To see blockers, call `get_issue` with
  `includeRelations: true`. The first remaining child in map order wins.
- **Claim.** Call `save_issue` with `id` and `assignee: "me"`. Make this
  the session's first write.
- **Resolve.** Post the answer on the ticket with `save_comment`, then set
  `state: "Done"` with `save_issue`. Then append a context pointer, a
  one-line gist plus a link, to the Decisions so far section of the map by
  updating the map's `description`. Use `patch` with an `insert_before` or
  `append` op rather than rewriting the whole body.
