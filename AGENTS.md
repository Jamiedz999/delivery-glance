## Where things live

Two homes, one rule: **the repo says what the work is; GitHub says how far along it is.**

```
CONTEXT.md                        domain glossary — the vocabulary to write in
docs/adr/                         resolved product and architecture decisions (02–11)
docs/planning/
├── map.md                        scope + readiness index over everything below
├── 12-rescope-to-resume-ready-core.md   the live Sprint roadmap
├── issues/                       implementation specs DG-020…DG-028
├── future-work/                  designed but deliberately deferred (13–19)
├── implementation/               TECHNICAL-BASELINE.md, ISSUE-WORKFLOW.md
├── research/                     evidence behind the decisions
└── prototypes/                   UI/flow prototypes and their briefs
```

Consequences worth stating outright:

- **Only `docs/planning/issues/` becomes GitHub Issues.** ADRs, research, prototypes and future work are records, not a backlog. Never open them as issues.
- **A GitHub Issue never carries a second copy of a spec** — it carries a summary, a permalink and the labels. If a spec must change, change the file in the PR.
- **Execution state lives only in GitHub** (labels, open/closed). The spec files do not track status.
- Numeric prefixes (`DG-020`, ADR `03`) are stable IDs from the original planning sequence. Keep them; don't renumber to `0001` style.

## Agent skills

### Issue tracker

GitHub Issues in the `delivery-glance` repo (`Jamiedz999/delivery-glance`); uses the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Four label strings: `needs-triage`, `needs-info`, `ready` (covers both agent- and human-ready), `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

Each ADR is a Question/Answer record and keeps a `Portfolio Core scope update` callout naming what Core actually implements. Read that callout before treating the rest of the ADR as scope — most ADRs deliberately preserve full-product design that Core does not build.

### Commit and PR conventions

Do not add the "🤖 Generated with [Claude Code]" line to commit messages or PR bodies. A plain `Co-Authored-By: Claude ...` trailer is fine if used elsewhere, but skip the emoji/link footer.
