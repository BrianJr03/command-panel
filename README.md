# Command Panel

User-installable replacement for the AYN Thor's stock bottom-screen assistant, plus Brian's own additions.
Package `jr.brian.commandpannel`. Personal, non-commercial project.

| Path | Contents |
|---|---|
| `PLAN.md` | build plan, decisions, phase status |
| `app/` | Command Panel (`.debug` variant installs side by side) |
| `probe/` | Thor capability probe (`jr.brian.thorprobe`), debug tool only |
| `fork-port/` | Brian's own code from his BDSA fork, staged for integration (`fork-port/PORT_MANIFEST.md`) |
| `docs/reference/` | findings: stock teardown, probe results, fork breakdown, port manifest, licence compliance record |
| `scripts/dev.sh` | `build`, `install`, `run`, `test` for `app` or `probe` |

```sh
scripts/dev.sh run          # build, install and launch the app's debug build
scripts/dev.sh test         # unit tests
scripts/dev.sh run probe    # the capability probe
```
