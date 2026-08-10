# Core implementation Issue workflow

Status: current

## Why this queue exists

The planning tree holds four kinds of file, and only one of them is a queue:

| Location | What it is | Becomes a GitHub Issue? |
| --- | --- | --- |
| `docs/adr/` | resolved product and architecture decisions | never |
| `docs/planning/research/`, `prototypes/` | evidence and visuals behind those decisions | never |
| `docs/planning/future-work/` | designed but deliberately deferred increments | never, until after Core Acceptance |
| `docs/planning/issues/` | the bounded implementation queue, DG-020…DG-028 | yes, one at a time |

Only the last row is an instruction to write code. Opening the other three as Issues turns a finished record into a permanently open ticket, which is noise for anyone reading the repository.

The numeric prefix is a local planning ID. A GitHub Issue does not need the same number; keep the ID in its title, for example `[DG-020] Scaffold the full-stack walking skeleton`.

## Labels

Create these GitHub labels before handing work to an Agent:

- state: `ready`, `blocked`
- scope: `core`
- Sprint: `sprint-1`, `sprint-2`, `sprint-3`, `sprint-4`
- area: `foundation`, `full-stack`, `backend`, `frontend`, `security`, `realtime`, `testing`, `documentation`

Derive an Issue's labels from its spec: `core`, plus `sprint-<Sprint>`, plus one label per entry in `Area`, plus exactly one state label. Do not put both `ready` and `blocked` on one Issue, and remove `ready` when the Issue closes — a closed Issue still carrying `ready` is exactly the kind of drift this file exists to prevent.

**State exists only on GitHub.** The spec files carry no status field, so there is nothing to keep in sync and nothing that can silently go stale. To learn whether DG-023 is startable, read its labels and its `Blocked by` chain — not a Markdown header. `Status:` on an ADR (`resolved`) or a Future Work file (`future`) is document lifecycle, not execution state, and stays where it is.

## Definition of Ready

An implementation Issue receives `ready` only when all of these are true:

1. Every `Blocked by` Issue is merged and closed.
2. Its product decision and technical baseline links are present.
3. It has one demonstrable outcome, ordered work, explicit interfaces, executable acceptance commands and explicit non-goals.
4. It fits roughly two to six focused hours; if not, split it before assigning it.
5. No unresolved product choice, repository access, paid account or secret is required to implement and test it locally.
6. The previous deployed increment still works at the start of the Issue.

Exactly one Issue is `ready` at a time. Do not label the whole queue `ready`; that lets Agents build later assumptions in parallel before the dependency that was supposed to establish them exists.

## Definition of Done

An implementation Issue is Done only when:

- its acceptance commands pass from a clean checkout;
- tests cover the risk named in the Issue rather than only line coverage;
- no non-goal or Future Work feature was added;
- user-facing or operational behaviour changed by the Issue is documented;
- the PR links the local planning ID and includes concise evidence (test output, screenshot, or demo path as applicable); and
- CI is green and reviewer feedback is resolved before merge.

## GitHub handoff pattern

Commit the planning files first. Then create a GitHub Issue that **summarises** the specification and **links** to it. A summary is safe to duplicate — if it drifts, nobody implements the wrong thing. A specification is not, so it exists exactly once, in the repository.

An Issue body carries four things and nothing else:

```markdown
A pre-provisioned Dispatcher can sign in, create a Delivery, list Deliveries, reopen a
persisted detail after restart, and cancel it while it is still awaiting a Courier.
This completes the Sprint 1 vertical slice.

Full specification, at this revision — this is the merge gate, not the summary above:
https://github.com/Jamiedz999/delivery-glance/blob/<sha>/docs/planning/issues/21-add-authenticated-delivery-slice.md

Acceptance:
    ./mvnw -f server verify
    npm --prefix web test

Not in this PR: Courier duty and Location Sharing (DG-022), or any Future Work.
```

Use a commit SHA in the permalink, not `main`, so the Agent implements a specification that cannot move underneath it. Never paste a second copy of the specification into the Issue body.

Assign the Agent one GitHub Issue and one branch. The Agent may make normal implementation choices inside the Issue, but any new product behaviour or new infrastructure dependency returns to backlog refinement rather than silently expanding the PR.

### When the specification turns out to be wrong

This happens, and how it is handled decides whether the repository stays trustworthy.

Change the file in `docs/planning/issues/` as a commit in the same PR, with a message saying what was wrong and why the new rule is better. Do not record the decision in an Issue comment and then code against it — a comment cannot be reviewed, cannot be diffed, and will not answer "why is it like this?" six months from now.

Issue comments carry process: being blocked, needing a choice made. Decisions belong in the specification, or in `docs/adr/` when they change product or architecture beyond this one Issue.

### Creating an Issue and promoting it are two different steps

The queue is visible on GitHub from the start: every implementation Issue exists, carries its labels, and is wired into the real dependency graph. What is rationed is not visibility but *authorisation to start*.

**Exactly one Issue is `ready` at any moment.** That is the constraint that matters, because it stops an Agent building on assumptions a dependency has not yet established. An open-but-blocked Issue cannot cause that; only a `ready` label can.

Splitting the two steps also avoids a trap. A specification pinned into an Issue body today is often not the specification that gets implemented four Sprints later — the "fix the spec in the PR" rule above guarantees the file will change. A SHA permalink written at creation time would then point at a stale revision and fail silently. So the pin is applied at promotion, when it is about to be used.

#### Step 1 — create (all Issues, up front)

1. Title `[DG-0NN] <title>`; body carries the Outcome summary, the non-goals, and a plain `main` link to the spec — **no SHA permalink, no acceptance commands yet**.
2. Label from the spec: `core`, `sprint-<Sprint>`, one label per `Area`, plus `blocked`.
3. Wire the real dependency edge from its `Blocked by` line, so GitHub itself reports the blockage (see `docs/agents/issue-tracker.md`). The label is a convenience; the dependency edge is the truth.

#### Step 2 — promote (one at a time, when its blocker merges)

1. Confirm the predecessor is merged and closed, and that the deployed increment still works.
2. Edit the body: replace the `main` link with a permalink pinned to the current `main` SHA, and add the acceptance commands. State plainly that the pinned spec, not the summary, is the merge gate.
3. Swap `blocked` for `ready`.
4. Give the Agent that one Issue on one feature branch.
5. Merge only after its acceptance commands pass from a clean checkout and CI is green.
6. Close the Issue **and remove `ready`**, then promote only its immediate successor.

At the end of each Sprint, run the whole cumulative demo before promoting the next Sprint.

The planning-only initial commit is the honest point to publish the repository; the first implementation PR is DG-020. GitHub's numeric Issue ID may be `#1`, which is why the stable planning key stays `DG-020`.

## Sprint release gates

The code Issues do not contain deployment credentials. A Sprint is considered demonstrated only after its cumulative checks pass and the current image is deployed to the user-owned target:

- Sprint 1: signed-in Dispatcher creates and reopens a persisted Delivery.
- Sprint 2: Dispatcher directly assigns an Eligible Courier; Courier completes handoff; a double assignment is rejected.
- Sprint 3: a phone opens a Tracking Link and follows status plus fresh/stale location; this is the MVP gate.
- Sprint 4: another person reproduces both E2E journeys from the README; this is Core Acceptance and the resume gate.

Repository URL, hosting credentials, public hostname and production tile configuration are human-supplied release inputs. They do not justify adding infrastructure to the application.
