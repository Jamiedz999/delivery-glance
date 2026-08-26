## Where things live

Two homes, one rule: **the repo says why the work is shaped this way; GitHub says what to build and how far along it is.**

```
CONTEXT.md                        domain glossary — the vocabulary to write in
docs/testing.md                   the risk matrix: every claim, the command proving it, the limits
docs/adr/                         resolved product and architecture decisions (02–12)
docs/planning/
├── map.md                        scope + readiness index
└── implementation/               TECHNICAL-BASELINE.md, ISSUE-WORKFLOW.md, INCIDENTAL-FINDINGS.md
```

Consequences worth stating outright:

- **The GitHub Issue is the implementation spec**, in full. Nothing in this repository duplicates it. An Issue is read where the work happens, and a spec that lives one click away is a spec that gets skimmed.
- **Later Backlog lives in GitHub Issues** under the `Later Backlog` milestone (#27–#33). ADRs and the roadmap are records — reasoned positions with reasons attached.
- **Execution state lives only in GitHub** (the `ready` label, open/closed, milestones, dependency edges).
- ADR numeric prefixes (`03`) are stable IDs from the original planning sequence; keep them. The `DG-0NN` implementation IDs no longer appear in Issue titles, but they remain the branch and commit key and are recorded in each Issue's footer, because merged history is full of them.
- **`implementation/INCIDENTAL-FINDINGS.md` is a note pad, not a queue.** Real problems spotted while implementing something else land there and never become Issues; one earns a plain PR when somebody picks it up. Entries leave by being deleted, not by being ticked.

## Agent skills

### Issue tracker

GitHub Issues in the `delivery-glance` repo (`Jamiedz999/delivery-glance`); uses the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Four label strings: `needs-triage`, `needs-info`, `ready` (covers both agent- and human-ready), `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

Each ADR is a Question/Answer record and keeps a `Portfolio Core scope update` callout naming what Core actually implements. Read that callout before treating the rest of the ADR as scope — most ADRs deliberately preserve full-product design that Core does not build.

### Commit and PR conventions

Do not put AI attribution in a commit message or a PR body. This bans two things: the `Co-Authored-By: Claude ...` trailer, and the "🤖 Generated with [Claude Code]" footer. A commit records one author, and that author is the repository owner.

The trailer is not cheap to remove later. It is public text on every commit page, and GitHub keeps a copy on `refs/pull/*` that a force-push cannot reach. Keep it out in the first place.

### Output Files
Keep project-related files, including code, ADRs, and planning docs, in the project directory. Put any extra generated artifacts for demonstration or explanation (e.g. test scripts, HTML demos) in `../document/`, never `/tmp`.


### Suggested Task
在 '/implement' 的过程中，主动记录下在执行当前任务中发现的问题，这些问题和主线任务无关，但是值得记录下来后续解决。Write them to `docs/planning/implementation/INCIDENTAL-FINDINGS.md`, one entry each saying what is wrong, how it was found, and why it was not fixed on the spot. A PR body is not a record — that is how ADR 04's scope callout stayed wrong for a Sprint. Do not open an Issue for one; the Issue queue is fixed at the nine implementation Issues and takes no additions.
