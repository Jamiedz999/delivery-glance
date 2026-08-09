# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| --------------------------- | --------------------- | ----------------------------------------- |
| `needs-triage`              | `needs-triage`         | Maintainer needs to evaluate this issue  |
| `needs-info`                | `needs-info`           | Waiting on reporter for more information |
| `ready-for-agent`           | `ready`                | Fully specified, ready for an AFK agent  |
| `ready-for-human`           | `ready`                | Requires human implementation            |
| `wontfix`                   | `wontfix`              | Will not be actioned                     |

`ready-for-agent` and `ready-for-human` both collapse to the single `ready` label — this repo doesn't distinguish agent-ready from human-ready work at the label level.

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.
