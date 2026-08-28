# Core implementation Issue workflow

Status: current

## Why this queue exists

The planning tree holds four kinds of file, and only one of them is a queue:

**The nine GitHub Issues are the queue, and each Issue is its own specification in full.** Nothing in this repository duplicates one, and nothing in this repository is an instruction to write code:

| Location | What it is | Becomes a GitHub Issue? |
| --- | --- | --- |
| GitHub Issues | the bounded implementation queue — outcome, scope, acceptance, non-goals | it already is one |
| `docs/adr/` | resolved product and architecture decisions | never |
| [Later Backlog milestone](https://github.com/Jamiedz999/delivery-glance/milestone/5) | designed but deliberately deferred increments (#27–#33) | they already are Issues |

Opening any of the other rows as an Issue turns a finished record into a permanently open ticket, which is noise for anyone reading the repository.

Issue titles are plain. Name a branch and its commits after the change itself — a short descriptive slug such as `add-recipient-sse-refresh` — with no required ID prefix. Older merged pull requests carry a `DG-0NN` planning ID in their branch and commit subject; that is history, not a rule to continue.

## Labels and milestones

There is exactly one label: **`ready`**.

Everything else a label might have carried is already represented somewhere that cannot drift:

| Question | Where it is answered |
| --- | --- |
| Which Sprint is this? | the **milestone** — which also gives a progress view a label cannot |
| Is it blocked, and by what? | GitHub's **native dependency edge** |
| Which part of the stack? | the Area named in the Issue's header line |
| Is it Core work? | it is one of the nine Issues; the queue takes no additions |

A label that lands on every Issue is not a filter, and a label that lands on exactly one Issue is not a filter either. Both are just a second copy of something already true elsewhere, which is the failure this file exists to prevent. Do not reintroduce `core`, `blocked`, `sprint-*` or per-area labels; if a filter is genuinely needed later, add it then.

`ready` survives because it is the one thing nothing else knows. Zero open blockers does not mean startable — the Sprint gate may not have run, or the deployed increment may be broken. That is a human judgement, and this label is where it is recorded.

Apply `ready` to exactly one Issue at a time, and remove it when the Issue closes. A closed Issue still carrying `ready` is exactly the kind of drift this file exists to prevent.

**State exists only on GitHub**, and now so does the specification, so there is nothing to keep in sync and nothing that can silently go stale. To find what is startable, ask GitHub for the open Issue with no open blockers and the `ready` label. `Status:` on an ADR (`resolved`) or a Future Work file (`future`) is document lifecycle, not execution state, and stays where it is.

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
- the PR links its Issue and includes concise evidence (test output, screenshot, or demo path as applicable); and
- CI is green and reviewer feedback is resolved before merge.

## Handing an Issue to an Agent

Assign the Agent one GitHub Issue and one branch. The Agent may make normal implementation choices inside the Issue, but any new product behaviour or new infrastructure dependency returns to backlog refinement rather than silently expanding the PR.

The Issue body carries the outcome, what to read first, the ordered scope, the acceptance criteria and the explicit non-goals. There is no summary-plus-link indirection and no SHA permalink: the specification is the thing being read, so it cannot point at a stale revision of itself.

### When the specification turns out to be wrong

This happens, and how it is handled decides whether the repository stays trustworthy. Moving the specification into the Issue costs something real here, and the cost is paid deliberately rather than hidden: **an edited Issue body has no reviewable diff.** GitHub keeps an edit history, but nobody reviews it and no PR gates it.

So the rule is split by what the change actually is:

- **A change to product or architecture behaviour goes to `docs/adr/`** as a commit in the same PR, with a message saying what was wrong and why the new rule is better. That is diffable, reviewed and answers "why is it like this?" six months from now. The Issue is then edited to match.
- **A clarification that changes no behaviour** — wording, a missing acceptance command, an ambiguity — is edited into the Issue body directly, followed by a comment saying what changed and why.

Never code against a decision that exists only as an Issue comment. Comments carry process: being blocked, needing a choice made. Behaviour belongs in an ADR.

### Creating an Issue and promoting it are two different steps

The queue is visible on GitHub from the start: every implementation Issue exists, sits in its Sprint milestone, and is wired into the real dependency graph. What is rationed is not visibility but *authorisation to start*.

**Exactly one Issue is `ready` at any moment.** That is the constraint that matters, because it stops an Agent building on assumptions a dependency has not yet established. An open-but-blocked Issue cannot cause that; only a `ready` label can.

#### Step 1 — create (all Issues, up front)

1. A plain title, and a body carrying the full specification.
2. Set its milestone from the Sprint named in its header line. Apply no labels.
3. Wire the real dependency edge — `gh issue create --blocked-by <n>`, or the API in `docs/agents/issue-tracker.md`. This is what makes the Issue blocked; there is no `blocked` label to keep in sync.

#### Step 2 — promote (one at a time, when its blocker merges)

1. Confirm the predecessor is merged and closed, and that the deployed increment still works.
2. Add the `ready` label.
3. Give the Agent that one Issue on one feature branch.
4. Merge only after its acceptance commands pass from a clean checkout and CI is green.
5. Close the Issue **and remove `ready`**, then promote only its immediate successor.

At the end of each Sprint, run the whole cumulative demo before promoting the next Sprint.

## Sprint release gates

The code Issues do not contain deployment credentials. A Sprint is considered demonstrated only after its cumulative checks pass and the current image is deployed to the user-owned target:

- Sprint 1: signed-in Dispatcher creates and reopens a persisted Delivery.
- Sprint 2: Dispatcher directly assigns an Eligible Courier; Courier completes handoff; a double assignment is rejected.
- Sprint 3: a phone opens a Tracking Link and follows status plus fresh/stale location; this is the MVP gate.
- Sprint 4: another person reproduces both E2E journeys from the README; this is Core Acceptance and the resume gate.

Repository URL, hosting credentials, public hostname and production tile configuration are human-supplied release inputs. They do not justify adding infrastructure to the application.
