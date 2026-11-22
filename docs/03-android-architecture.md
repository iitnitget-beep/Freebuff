# Milestone 3 — Android Architecture & Technical Design

App working name: **CybNate Agent** (Android-native autonomous coding agent)
Stack: Kotlin · Jetpack Compose · Material 3 · MVVM · Coroutines/StateFlow · Room · DataStore ·
Hilt · WorkManager · Storage Access Framework · JGit

## Principles

1. Phone is the control interface; heavy execution is hybrid (on-device + optional worker).
2. Provider-agnostic agent core: adding a provider = new adapter, no engine rewrite.
3. Keys never leave the device except to the provider the user configured; Keystore-encrypted.
4. Every destructive action is permission-gated; nothing runs silently.
5. Original implementation only — concepts from public docs + Craft Anything patterns (MIT/Apache-2.0).

## Modules (Gradle)

Single `app` module for MVP velocity, with packages mirroring the target modules; split into
library modules once interfaces stabilise (settings.gradle.kts already lists them as commented
includes). Target modules:

- `app` — Compose UI, navigation, DI wiring
- `core` — Result/error types, logging, dispatchers, file sandbox policy
- `ai-engine` — Provider adapters (Anthropic, OpenAI, Gemini, OpenRouter, custom), streaming, tool-call normalisation
- `agent-runtime` — Agent loop, planner, subagents, hooks, permission engine, session/checkpoint manager
- `tool-execution` — Tool registry + implementations (read/write/edit/search/git/terminal/http)
- `terminal` — On-device sandbox shell + worker terminal client (streaming output)
- `file-manager` — SAF project access, file tree, editor, diff viewer
- `project-manager` — Projects, indexing, recent workspaces
- `git-integration` — JGit operations + GitHub API
- `mcp-client` — MCP HTTP client (+ worker stdio bridge), tool discovery, consent
- `skills-engine` — SKILL.md discovery/invocation
- `memory-engine` — Room: conversations, project memory, instructions, usage
- `provider-manager` — Provider configs, Keystore vault, connection tests, model catalog
- `build-system` — Toolchain detection, worker build/test dispatch, log analysis

## Agent loop

```
USER REQUEST → PROJECT ANALYSIS → PLANNING → TOOL SELECTION → EXECUTION
→ OBSERVATION → ERROR HANDLING → VERIFICATION → FINAL RESULT
```

- Planner emits a visible plan (steps with status). Each tool call is logged with inputs/outputs.
- Verification: re-read changed files, run available checks (on-device lint/syntax; worker build/test).
- Caps: max turns, max tokens, per-tool timeout, cancellation token. Stop button always available.

## Provider management (Phase 4)

- Multiple providers, switch anytime; per-provider: base URL, model, temperature, max tokens,
  custom headers, test connection.
- Storage: API keys in EncryptedSharedPreferences backed by Android Keystore; masked in UI;
  never logged; never sent to any backend except the configured provider endpoint.
- Adapters implement `AiProvider` (chat stream + tool calls + usage). OpenAI-compatible adapter
  covers OpenRouter/custom/local endpoints.

## Execution engine (Phase 7) — hybrid

| Tier | Runs | How |
|---|---|---|
| On-device | File ops, search, editor, JGit, HTTP, provider calls | App-private + SAF folders only |
| Worker (optional) | Builds, tests, installs, stdio MCP, Craft Anything | User-controlled server (e.g. existing CybNate backend) over authenticated HTTPS/WSS; pairing via QR/token |
| Termux (optional, advanced) | Local shell toolchains | Only if user installs Termux + grants access; never required |

## Security (Phase 9)

- Project directory jail: tools refuse paths outside the selected project (SAF tree URI).
- Command allowlist + approval kinds (mirrors Craft Anything: commands/web/push/delete/system).
- Backups before multi-file edits; git checkpoint option; full audit log in Room.
- Network: HTTPS only (except user-configured localhost worker); cleartext blocked by default.

## Screens (Phase 6)

Dashboard (projects, recents, provider status) · Workspace (streaming chat, live agent activity,
tool logs, diff approvals, stop/resume) · Explorer (tree, editor, search, diff) · Terminal
(streaming output) · Provider Settings · Agents (specialised agents, skills, history) ·
MCP & Skills · Memory & Context.

## Build & test

- CI (GitHub Actions) builds debug APK (no local Android SDK on this VM — verified 2026-11-22:
  no `java`, no SDK). Workflow: `.github/workflows/android.yml`.
- Test cases: Phase 10 list (create project, inspect repo, find bug, multi-file edit, install dep
  via worker, build, fix error, new feature, switch provider, restart persistence).
