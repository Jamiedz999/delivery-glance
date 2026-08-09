# Core implementation Issue workflow

Status: current

## Why this queue exists

Issues 01–12 are decision records. Issues 13–19 are optional Future Work. Neither group is an instruction to start coding the full product. Issues 20–28 are the bounded implementation queue for the four-Sprint Core.

The numeric prefix is a local planning ID. A GitHub Issue does not need the same number; keep the ID in its title, for example `[DG-020] Scaffold the full-stack walking skeleton`.

## Labels

Create these GitHub labels before handing work to an Agent:

- state: `ready`, `blocked`, `future`
- scope: `core`
- Sprint: `sprint-1`, `sprint-2`, `sprint-3`, `sprint-4`
- area: `foundation`, `full-stack`, `backend`, `frontend`, `security`, `realtime`, `testing`, `documentation`

Do not put both `ready` and `blocked` on one Issue. The Markdown `Status` records the planned initial state; after GitHub exists, its label and open/closed state are the execution status.

## Definition of Ready

An implementation Issue receives `ready` only when all of these are true:

1. Every `Blocked by` Issue is merged and closed.
2. Its product decision and technical baseline links are present.
3. It has one demonstrable outcome, ordered work, explicit interfaces, executable acceptance commands and explicit non-goals.
4. It fits roughly two to six focused hours; if not, split it before assigning it.
5. No unresolved product choice, repository access, paid account or secret is required to implement and test it locally.
6. The previous deployed increment still works at the start of the Issue.

Only Issue 20 meets this definition initially. Do not label the entire queue `ready`; that lets Agents accidentally build later assumptions in parallel.

## Definition of Done

An implementation Issue is Done only when:

- its acceptance commands pass from a clean checkout;
- tests cover the risk named in the Issue rather than only line coverage;
- no non-goal or Future Work feature was added;
- user-facing or operational behaviour changed by the Issue is documented;
- the PR links the local planning ID and includes concise evidence (test output, screenshot, or demo path as applicable); and
- CI is green and reviewer feedback is resolved before merge.

After merge, close the Issue, remove `ready`, and promote only its immediate successor from `blocked` to `ready`. At the end of each Sprint, run the whole cumulative demo before promoting the next Sprint.

## GitHub handoff pattern

Commit the planning files first. Then create a GitHub Issue whose body links to the committed specification instead of copying a second body that can drift:

```markdown
Implement DG-020 from `.scratch/recipient-delivery-tracking/issues/20-scaffold-full-stack-walking-skeleton.md` at the committed revision.

Use the linked acceptance commands as the merge gate. Do not start DG-021 or Future Work in this PR.
```

Add the repository permalink to that file after the first push; do not paste a second copy of its specification into the Issue body.

Assign the Agent one GitHub Issue and one branch. The Agent may make normal implementation choices inside the Issue, but any new product behaviour or new infrastructure dependency returns to backlog refinement rather than silently expanding the PR.

### First repository sequence

1. Create an empty `delivery-glance` GitHub repository without generated README, `.gitignore` or licence files, so the first push has no unrelated merge conflict.
2. Initialise this workspace as `main` and make one planning-only commit containing `CONTEXT.md` and `.scratch/recipient-delivery-tracking/`. Do not pretend the application skeleton already exists.
3. Push that commit, create the labels above, and create `[DG-020] Scaffold the full-stack walking skeleton` with a permalink to the committed DG-020 file.
4. Apply only `ready`, `core`, `sprint-1`, `foundation` and `full-stack`. Do not create DG-021 as ready.
5. Give the Agent DG-020 on a feature branch/PR. Merge only after its clean-checkout acceptance commands pass.
6. Close DG-020, remove `ready`, create/promote DG-021 with `ready`, and repeat one dependency at a time.

The planning-only initial commit is the honest point to publish the repository; the first implementation PR is DG-020. GitHub's numeric Issue ID may be `#1`, which is why the stable planning key stays `DG-020`.

## Sprint release gates

The code Issues do not contain deployment credentials. A Sprint is considered demonstrated only after its cumulative checks pass and the current image is deployed to the user-owned target:

- Sprint 1: signed-in Dispatcher creates and reopens a persisted Delivery.
- Sprint 2: Dispatcher directly assigns an Eligible Courier; Courier completes handoff; a double assignment is rejected.
- Sprint 3: a phone opens a Tracking Link and follows status plus fresh/stale location; this is the MVP gate.
- Sprint 4: another person reproduces both E2E journeys from the README; this is Core Acceptance and the resume gate.

Repository URL, hosting credentials, public hostname and production tile configuration are human-supplied release inputs. They do not justify adding infrastructure to the application.
