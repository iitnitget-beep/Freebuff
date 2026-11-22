# Milestone 1 — Craft Anything Analysis (storytold/craft-anything)

Date: 2026-11-22 · Source inspected by plain file read (no execution after 2026-11-22 system check)
Local copy: `~/workspace/tools/craft-anything/` · Version: 0.6.1 (Cargo.toml workspace.package)
License: MIT OR Apache-2.0 · Rust 1.97+ · Early alpha

## What Craft Anything actually is

Craft Anything is NOT a general coding-agent IDE. It is a **clean-room reverse-engineering + rebuild engine**:
point it at a website/app/game/file, it studies the original with inspection tools, has an external
coding agent build a new project, checks the result against the original, and refines.

- Desktop app (egui), CLI (`craft-anything-cli`), MCP server — one Rust engine drives all three.
- 320 tools total (300 engine + AI/setup layers), each exposed as CLI subcommand + MCP tool.
- Two modes: **Simple** (Study → Build → Check → Ready) and **Advanced** workbench (functions,
  pseudo-C, hex, call graphs, evidence ledger).
- **Clean room** (default): works only from visible behaviour (screenshots, visible text, layout).
  **Deep**: reads code/files with built-in engines (+ Ghidra if installed).

## Architecture (from Cargo.toml + docs/extending.md)

Workspace crates, layered L0–L6 (enforced by `cargo xtask layers`):

| Layer | Crates | Role |
|---|---|---|
| L0 | evidence, formats | Evidence records/confidence; standalone parsers (ELF/PE/Mach-O/WASM/DEX/APK/ASAR/.NET/Go…) |
| L1 | disasm, js | Own disassembler/decompiler; JS/Electron/web bundle analysis |
| L2 | analysis, platform | Analyzers; processes/PTY/sandbox/atomic files |
| L3 | engines | External engines: Ghidra, adb, CDP, Frida, JADX, Apktool… |
| L4 | engine | Session, projects (.cany), ToolSpec registry, settings, consent, inbox (approvals) |
| L5 | ai, mcp, setup | AI backends + rebuild workflow; MCP server; doctor/setup |
| L6 | ui-egui | Only crate allowed egui/eframe/winit/rfd |
| apps | craft-anything, craft-anything-cli, craft-anything-web | Desktop, CLI+MCP, browser viewer (WASM) |

Key seams (docs/extending.md): new tool = `ToolSpec` in `crates/engine/src/tools/<area>.rs`;
new target kind = `TargetKind` in `crates/ai/src/rebuild/kinds/`; new coding agent = `AgentAdapter`
in `crates/ai/src/cli_agents/adapters.rs`; toolchains in `crates/engine/src/toolchains.toml`.

## AI / agent capabilities

- **Builder agents (external CLIs, user's own sign-in):** Claude Code first (runs unmodified
  `claude -p --output-format stream-json --verbose`, `--resume`, `--mcp-config`,
  `--permission-prompt-tool mcp__craft-anything__core_action_approve`). Also Codex adapter
  (unverified live), Copilot CLI / Grok Build via ACP, other ACP agents (Gemini CLI, Qwen Code,
  opencode, goose, Hermes, Maki, Devin).
- **API backends for Q&A/analysis:** Anthropic, OpenAI, OpenRouter, xAI, local OpenAI-compatible.
  Building with API keys needs Craft Anything's own file-writing tool loop (listed as "Next" in STATUS.md).
- **Permissions:** read-only tools pre-allowed in Deep; everything else goes through approval tool
  (allow / ask / refuse per kind: commands, web, files outside project, push, delete, system).
  Approval cards: Allow once / Always for this project / Don't allow.
- **Plugins:** external MCP servers (stdio or Streamable HTTP, consent per call by default) and
  optional WASM components (`--features wasm-plugins`, sandboxed, 256 MiB + fuel limit).

## Installation (documented, previously verified)

Per README "Get started" (Rust 1.97+):

```sh
git clone https://github.com/storytold/craft-anything && cd craft-anything
cargo run --release -p craft-anything                # desktop app
cargo install --path apps/craft-anything-cli         # CLI
craft-anything-cli doctor                            # checks install/agents/engines
craft-anything-cli rebuild https://example.com "Recreate this website"
```

**Verification status:** On 2026-11-21 this VM built `craft-anything-cli 0.6.1` (Rust build exit 0,
binary at `target/debug/craft-anything-cli`; desktop GUI not built) — recorded in MEMORY.md.
On 2026-11-22 a new system execution-risk check fired on the repo contents, so the binary was
**not re-run** in this session. Re-verification (doctor / version / rebuild smoke test) is blocked
pending a fresh plain-words go-ahead from the user. No money spent.

## Android compatibility assessment

| Component | Reusable on Android? | Notes |
|---|---|---|
| Desktop egui UI (L6) | No — rebuild | egui desktop; Android needs Compose UI. Concepts (Simple/Advanced, approval cards, evidence) carry over. |
| CLI + MCP server (Rust) | Partially — via remote worker | Rust engine can run on a server/Termux-like env, not inside a normal Android app sandbox. Best: phone = control UI, worker = engine. |
| Evidence/formats/disasm (L0–L1, pure Rust, WASM-safe parts) | Yes, selectively | Parsers are pure Rust; L0–L2 have a WASM build (`cargo xtask wasm`). Could compile a subset to WASM or via JNI/UniFFI later. Not needed for MVP agent. |
| AI rebuild workflow (study→build→check) | Yes — as design pattern | Reimplement loop natively in Kotlin agent-runtime; optionally call Craft Anything CLI/MCP on the worker for Deep inspection. |
| ToolSpec registry + consent model | Yes — as design pattern | Mirror in Kotlin: tool registry, per-kind allow/ask/refuse, approval cards. |
| MCP client/server | Yes | Implement MCP client in Kotlin (stdio via worker, HTTP direct). |
| External engines (Ghidra, adb…) | Worker only | Desktop-grade tools cannot run in Android app sandbox. |

**Conclusion:** Do not port Craft Anything wholesale. Build a native Kotlin/Compose agent that
(1) implements its own provider adapters + agent loop + tools, (2) treats Craft Anything as an
optional remote Deep-inspection/rebuild backend via its CLI/MCP, and (3) reuses its proven
patterns: evidence records, approval kinds, toolchain catalogue, MCP plugin consent.

## Limitations to carry forward

- Early alpha; API-key building loop not yet live in Craft Anything itself.
- Clean room is an app-level rule, not an OS sandbox — agent still runs as the user.
- Desktop-first: no Android build target; Web build is a file viewer only.
