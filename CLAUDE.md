@AGENTS.md

`AGENTS.md` (imported above, inlined at launch) is the tool-agnostic
project brief and the rule SSOT — cite its rules as
`AGENTS.md#<anchor>`. The sections below are Claude Code-specific
surface only; add nothing here that another agent would need.

## Claude Code surface

| Agent (`.claude/agents/`) | When to use |
| --- | --- |
| `compose-launcher-reviewer` | After touching `ui/theme`, `ui/home`, `MainActivity`, manifest, build files, fonts, or `webmap/`; before opening a PR. Give it a diff, file list, or git scope (ref range / `--staged` / `--working`) — it resolves git scopes itself; an empty scope defaults to `git diff HEAD`, and a dispatch with no scope at all makes it ask. |
| `similar-app-researcher` | Before scoping any feature, to study how comparable car launchers and prior-art projects solve the same problem. |

Skill (`.claude/skills/`) frontmatter descriptions are the per-skill
SSOT. The skill surface is deliberately small — four load-bearing
procedure SSOTs (`verify-android-build`, `verify-on-emulator`,
`update-gradle-dependency`, `update-launcher-icon`); everything else
lives in the rules files and the living code (2026-07 consolidation).

The [`update-launcher-icon`](.claude/skills/update-launcher-icon/SKILL.md)
skill regenerates the adaptive launcher icon (background / foreground /
monochrome) from the root `logo.svg` brand mark; it auto-invokes when
either changes.

Settings: `.claude/settings.json` (committed) holds the allow /
deny permission lists; `settings.local.json` (gitignored) holds
per-machine overrides — machine-local absolute-path allowlist
entries belong there, never in the committed file.

## Memory

<!-- Guard: agents resolve the memory location here — keep portable. -->
Persisted decisions live in Claude Code's project memory directory
for this repo (`~/.claude/projects/<project-path-hash>/memory/`,
derived from the repo's absolute path on each machine). Read it
before re-litigating settled choices; update it on durable decisions.
