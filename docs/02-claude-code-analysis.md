# Milestone 2 — Claude Code Capability Analysis (public docs only)

Date: 2026-11-22 · Sources: code.claude.com docs (Extend Claude Code, features overview),
Craft Anything `docs/claude-code.md`, public headless-mode guides. No proprietary code copied.
This is a clean-room functional analysis for an original Android implementation.

## How Claude Code works (publicly documented)

Claude Code = model + harness. The harness provides built-in tools (file ops, search, execution,
web), an agentic loop, and an extension layer. Extensions plug into different loop points:

- **CLAUDE.md** — persistent context loaded every session (project conventions, standing rules).
- **Skills** — `SKILL.md` folders: on-demand knowledge/workflows, model-invoked by description
  match or user-invoked via `/name`. Description always in context; body loaded when used.
- **Subagents** — isolated context windows with own prompt/tools/model; return summaries only.
  Built-ins: Explore (read-only search), Plan, general-purpose. Custom: `.claude/agents/*.md`
  with frontmatter (name, description, tools allowlist, model, memory).
- **Hooks** — deterministic harness-run scripts/HTTP/MCP/prompt at lifecycle events
  (e.g. PreToolUse to block, PostToolUse to format/lint). The only non-model-triggered mechanism.
- **MCP** — external tools/data via Model Context Protocol (stdio local servers, HTTP remote,
  OAuth). Harness connects at startup; model calls tools. Config: `.mcp.json` / `claude mcp add`.
- **Plugins/marketplaces** — packaging for skills/subagents/hooks/MCP.
- **Headless mode** — `claude -p "<prompt>" --output-format stream-json --verbose` emits NDJSON
  events (system/assistant/user/result/stream_event); `--resume <session>` continues;
  `--allowedTools` / `--disallowedTools` / `--permission-mode` bound capabilities;
  `--permission-prompt-tool <mcp-tool>` delegates approvals; `--mcp-config` injects servers;
  `--bare` skips auto-discovery for reproducible CI. Auth order: cloud provider → env tokens →
  `ANTHROPIC_API_KEY` → apiKeyHelper → subscription login. In `-p` mode an env API key wins.

## Feature-by-feature: implementation + Android verdict

### A. Core coding intelligence
| Feature | How it works | Android implementation |
|---|---|---|
| Repo understanding | Model reads files via Read/Glob/Grep tools; context window holds selected files | File tools over SAF/app-private storage + on-device indexer (file tree, symbol grep). Works on-device for text ops. |
| Multi-file edits | Edit/Write tools with diffs; harness applies + shows changes | Kotlin file tools with diff preview + backup/rollback. Works on-device. |
| Bug detection / refactor | Model reasoning over code + test/build output loop | Same loop; build/test execution needs worker (see §C). |
| Context management | CLAUDE.md always-on; skills on-demand; subagent isolation; compaction | Mirror: project instructions file, skills engine, subagent contexts, token budgeter. Works on-device. |

### B. Autonomous agent system
| Feature | How it works | Android implementation |
|---|---|---|
| Agentic loop | Plan → tool call → observe → repeat until done/verified | Native Kotlin loop: REQUEST→ANALYSE→PLAN→TOOLS→OBSERVE→FIX→VERIFY. Core of this project. |
| Tool calling | Model emits structured tool calls; harness executes + returns results | Provider adapters normalise Anthropic/OpenAI/Gemini/OpenRouter tool formats. |
| Error recovery | Read logs, fix, re-run; max-turns/budget caps | Same; caps + timeouts + cancellation in agent-runtime. |
| Approval checkpoints | Permission modes + prompt tool; allow/ask/deny per tool/kind | Permission levels: Read-only / Ask before edit / Ask before exec / Allow approved. Approval cards in UI. |
| Long-running tasks | Background tasks, resume sessions | WorkManager + Room-persisted sessions; resume after app restart. |

### C. Terminal & dev tools
| Feature | How it works | Android implementation |
|---|---|---|
| Shell execution | Bash tool in project dir | **Hybrid:** limited on-device (app sandbox shell for file ops) + optional remote worker (user-controlled) for real builds. Never unrestricted OS access. |
| Git / GitHub | git CLI + gh/MCP | JGit on-device for status/diff/commit; GitHub via API/MCP. Push needs user token (Keystore). |
| Package install / build / test | npm/cargo/gradle via Bash | Worker only for desktop-grade toolchains. On-device: syntax checks only. This matches Craft Anything's toolchain catalogue approach. |
| Search | Grep/Glob tools | On-device regex/glob over project files. Works. |

### D. Advanced features
| Feature | How it works | Android implementation |
|---|---|---|
| Subagents | Separate loops, summaries back | Kotlin subagent runner with own context + tool allowlist. Works. |
| Skills | SKILL.md discovery + invocation | Skills engine: parse SKILL.md, inject on match/`/name`. Works. |
| MCP client | Connect servers, list/call tools | MCP client (HTTP direct; stdio via worker). Works with consent per server. |
| Hooks | Lifecycle events → script/HTTP/prompt | Hook engine: PreToolUse/PostToolUse/Stop events → Kotlin/HTTP actions. Works. |
| Memory | CLAUDE.md + auto memory + session history | Room: project memory, instructions, conversations, checkpoints. Works. |
| Checkpoints/rollback | Git + session resume | File backups + git checkpoints before major edits. Works. |

## Legal boundary (from Craft Anything docs/claude-code.md, Anthropic Legal page)

- Third parties must NOT offer Claude.ai login, route subscription credentials, or store tokens.
- End users MAY sign in to the unmodified `claude` binary with their own subscription.
- Products must not modify the binary, disable auth, or resell/meter usage.
- **Our Android app therefore:** uses user-provided **API keys** (Anthropic API, OpenAI, Gemini,
  OpenRouter, custom endpoints) with original orchestration. It does not embed Claude Code,
  does not offer Claude.ai login, and does not claim an API key grants Claude Code access.
  Optionally, a user-controlled worker may run their own unmodified `claude` CLI / Craft Anything.

## Android execution verdict (summary)

On-device: UI, file manager/editor, search, git (JGit), provider calls, agent loop, skills,
memory, MCP-HTTP, approvals. Worker (optional, user-controlled): shell builds, tests, package
installs, stdio MCP servers, Craft Anything Deep inspection. Basic chat + local project ops
never require the worker.
