# Working in this repository

**The repository says why the work is shaped this way. GitHub says what to build and how far along
it is.** Nothing here duplicates a GitHub Issue, and nothing here records progress.

```
CONTEXT.md          the glossary — the words to write in, and what this product will never do
docs/adr/           decisions that are already made, and why
docs/testing.md     every claim this project makes, and the command that proves it
docs/planning/      the locked technical stack; problems found in passing
```

## Before you change anything

1. Read `CONTEXT.md`. Use its words in your code, your commits, your Issue titles and your replies.
   Where a name in the glossary differs from the name in the code, the entry says so — the code name
   stays until somebody renames it deliberately.
2. Read the ADRs that touch the area you are working in. They are short.
3. If your work contradicts an ADR, say so out loud rather than quietly overriding it: *"This
   contradicts ADR 06, but it is worth reopening because…"*
4. If you need a word that is not in `CONTEXT.md`, that is a signal. Either you are inventing
   language this project does not use, or there is a real gap worth adding.

## Issues live on GitHub

Use the `gh` CLI. The repo is `Jamiedz999/delivery-glance`.

**The Issue body is the specification, in full.** Nothing in this repository duplicates one. So is
the execution state: open or closed, the milestone, the dependency edges.

There is one label, **`ready`**: the Issue is specified well enough to start, by a person or by an
agent. Everything else a label might say is already somewhere that cannot drift — the milestone says
which release, GitHub's dependency edges say what is blocking, and the Issue's own header says which
part of the stack.

New Issues are welcome. Version 1 is finished; what remains is a normal backlog.

## Problems found in passing

While working on one thing you will notice another. Write it in
`docs/planning/implementation/INCIDENTAL-FINDINGS.md`: what is wrong, how you found it, and why you
did not fix it there and then. Do not widen the change you are making to fix it.

That file is a holding area. Entries leave it by being opened as an Issue, or by being fixed.

## Commits and pull requests

**No AI attribution.** No `Co-Authored-By: Claude` trailer, and no "🤖 Generated with Claude Code"
footer. A commit records one author, and that is the repository owner.

This is not cheap to undo. It is public text on every commit page, and GitHub keeps a copy on
`refs/pull/*` that a force-push cannot reach. Keep it out in the first place.

Name a branch and its commits after the change itself — `add-recipient-sse-refresh`. No ID prefix.
Older merged pull requests carry a `DG-0NN` planning ID; that is history, not a rule to continue.

## Where files go

Code, ADRs and planning docs go in this repository. Anything generated to demonstrate or explain
something — a test script, an HTML demo, a report — goes in `../document/`, never `/tmp`.
