# Architecture

Hermes is the only brain, the app is the only body, and one phone-control MCP server is the only door to the device; every contract between them is defined once as a schema and generated for both Kotlin and Python.

## System at a glance

&#91;embedded content: system architecture · 4 layers, protocol on each link\]

The MCP server is the only path from Hermes to the device, the app reaches Hermes only through the gateway WebSocket, and the Termux services can restart or roll back either side.

## Protocol map

Each protocol has exactly one job, so no two paths do the same thing.

| Mechanism | Runs between | Used for | Not used for |
| --- | --- | --- | --- |
| **MCP** (stdio) | Hermes and the phone-control MCP server | Every device action: shell, ADB, root, Termux:API, and app-only calls forwarded to the app | Conversation, UI, or app-to-Hermes traffic |
| **Gateway WebSocket** (localhost) | Deskbot platform plugin and the app | User turns (text, images), streamed replies, control events, A2UI messages | Device actions (those go through MCP) |
| **A2UI** v1.0 | Carried on the gateway WebSocket | Interactive native screens Hermes composes; `action` events back | Plain spoken or text answers |
| **App bridge** (localhost HTTP or WebSocket, token) | MCP server and the app | Calls only the app can serve: camera, accessibility, overlay, notifications, mic | Anything a shell command can do |
| **Skills** (Hermes skill files) | Inside Hermes | Recipes that combine MCP tools with the method ladder, verify, and fall back | New device access; a skill never reaches the device except through MCP tools |
| **A2A** | Hermes and other agents | Reserved: a second bot, a PC agent, or another device agent talking to this one; A2UI can also ride on A2A | Anything inside this phone; Hermes `delegate_task` covers its own sub-agents |
| **OpenAI-compatible API server** | curl or test scripts and Hermes | Debugging and contract tests | Production app traffic |

A2A is not built at the start. Add it only when a second agent appears, and keep the same message schemas so nothing is repeated.

## Ownership

Every responsibility has one owner; the others call it and never copy it.

| Owner | Owns | Depends on | Must not do |
| --- | --- | --- | --- |
| **Hermes** | Reasoning, conversation, memory, skills, cron, model choice, web research, code editing, A2UI composition | Cloud model, MCP server, gateway plugin | Touch hardware or the device directly |
| **Deskbot gateway plugin** (Python, in Hermes) | App sessions, streaming, A2UI compile and validate, routing UI actions | Hermes plugin API, shared schemas | Run device commands |
| **Phone-control MCP server** (Python, Termux) | All device actions, the method ladder executors, deny-list, audit log | `CommandRunner`, app bridge, shared schemas | Reasoning or UI |
| **Android app** (Kotlin) | Mic, wake word, speech in and out, camera, accessibility, overlays, notifications, face, A2UI rendering, bridge server | Gateway WebSocket, Android APIs | Decide what to do; it only senses, shows, and executes |
| **Termux services** | Watchdog, boot start, self-update and rollback scripts, backups | Shell, root | Hold logic that belongs in Hermes or the app |
| **Root layer** (KernelSU modules) | Systemized app and permissions, bootguard, charge limit | KernelSU | Change at runtime except through the module-scout flow |

If Hermes is down, the app shows an error face and keeps retrying; if the app is down, Hermes still answers on Telegram and the watchdog restores the app.

## Where a new capability goes

Walk this order for every catalog feature and stop at the first yes.

1. **Hermes built-in?** Use it; add nothing (the removed list in the Feature catalog tab).
2. **A recipe over existing tools?** Write a **skill**.
3. **A new device action a shell, ADB, or root command can do?** Add an **MCP tool** backed by an existing executor.
4. **Needs an Android API only the app has** (camera, accessibility, overlay, mic)? Add an **app bridge capability** and expose it through one MCP tool.
5. **Needs a new screen?** Compose it with **A2UI** from the catalog; add a catalog component only if no existing one fits.
6. **Needs a new native component, service, or manifest entry?** Change the **app** through the self-update pipeline.
7. **Needs system-level change?** Make or add a **root module** through module-scout.
8. **Another agent?** Only then use **A2A**.

## Single sources of truth

Each fact lives in one file, and everything else is generated from it, so Kotlin and Python never hold two hand-written copies.

| Source file | Defines | Generated from it |
| --- | --- | --- |
| `capabilities.yaml` | Every capability: id, inputs, outputs, executors in ladder order, verify step, tests | MCP tool definitions, app bridge interface, skill stubs, catalog docs, test skeletons |
| `protocol.schema.json` | Gateway WebSocket and app bridge messages | Kotlin data classes (kotlinx.serialization) and Python models (Pydantic) |
| `a2ui/catalog.json` | Deskbot A2UI components | Hermes A2UI skill, Compose component registry stubs, validation fixtures |
| `permissions.yaml` | Every permission and special access | Manifest entries, privapp and default-permissions XML, `grant_all.sh` |
| `config.yaml` (one per environment) | Ports, tokens, paths, feature flags | Typed config objects in both languages |
| `deny_list.yaml` | Blocked command patterns | Shell executor guard and its tests |
| expressions.yaml | Moods, bot states, reactions, intensities, and fallback chains for every persona pack | Face engine enum, Kotlin enum, Hermes expression skill, pack validator, render tests |

A CI check fails if generated files differ from what the generator produces, so nobody edits them by hand.

## App modules

&#91;embedded content: Android app module graph · :app, 7 feature modules, 6 core modules\]

Feature modules use only domain ports and shared UI components; `core:data` implements those ports with `core:network` and the generated `core:schema`, `:app` wires everything together through Hilt, and `core:testing` holds the one shared set of fakes used by every module's tests.

## Clean architecture and SOLID

Dependencies point inward only: presentation and data depend on domain, and domain depends on nothing but Kotlin.

| Layer | Contains | May depend on |
| --- | --- | --- |
| **Domain** (pure Kotlin) | Entities, use cases (one public `invoke` each), repository and port interfaces | Nothing Android, nothing else in the app |
| **Data** | Repository implementations, Android adapters, bridge server, WebSocket client, persistence | Domain interfaces, `core:*` |
| **Presentation** | ViewModels with a single `StateFlow` of UI state, Compose screens, A2UI host | Domain use cases, `core:ui` |

| Principle | Rule in this project |
| --- | --- |
| Single responsibility | One use case per action; one executor per method rung; one module per feature |
| Open/closed | New capabilities are new executor or use-case classes registered in DI; existing classes are not edited to add a case |
| Liskov substitution | Every `ActionExecutor` honors the same contract (same result type, same error model), so the ladder can swap them freely |
| Interface segregation | Small ports: `CameraPort`, `AccessibilityPort`, `OverlayPort`, `SpeechPort`, never one `DevicePort` with everything |
| Dependency inversion | Features depend on domain ports; data modules implement them; DI wires them |

**Enforcement:** a Konsist architecture test suite fails the build if domain imports Android, a feature imports another feature, or a use case has no matching test file. The Gradle module graph makes illegal imports fail to compile.

## Design patterns

Each pattern solves one named problem; none is used for its own sake.

| Pattern | Where | Problem it solves |
| --- | --- | --- |
| **Singleton** (DI-scoped) | `CommandRunner`, `HermesConnection`, `CapabilityRegistry`, `AuditLog`, `BridgeServer`, `A2uiRenderer`; Hilt `@Singleton` in Kotlin, one container instance in Python | One shared connection or resource; DI scope, not a global `object`, so tests can swap in fakes |
| **Strategy** | `ShellExecutor`, `AdbExecutor`, `AccessibilityExecutor`, `VisionExecutor` behind `ActionExecutor` | Several ways to do the same action |
| **Chain of responsibility** | `MethodLadder` tries executors in order until one verifies | The shell-first, UI-last fallback |
| **Command** | Every tool call becomes a `DeviceCommand` with `execute`, `verify`, and `undo` | Uniform logging, retry, and rollback |
| **Decorator** | `AuditingExecutor`, `RetryingExecutor`, `TimingExecutor`, `DenyListExecutor` wrap any executor | Cross-cutting concerns without editing executors |
| **Facade** | `DeviceController` in the MCP server; `BotSession` in the app | One simple entry point over many subsystems |
| **Adapter** | Gateway platform adapter, Android API adapters, A2UI renderer host | Fit external APIs to our ports |
| **Repository** | Settings, skills cache, conversation state | Hide where data comes from |
| **Observer** | `StateFlow` and `SharedFlow` for bot state and device events | Push updates to UI and triggers |
| **State machine** | `BotState`: idle, listening, thinking, speaking, working, updating, error | Face and audio behavior with legal transitions only |
| **Registry plus factory** | `CapabilityRegistry` and `A2uiComponentFactory`, both filled from generated code | Look up executors and components by id without `when` blocks |
| **Template method** | `BaseSkillTest` and the self-update pipeline steps | Fixed order with replaceable steps |

## Python services structure

The MCP server and the gateway plugin share one core package, so command running, schemas, config, and logging exist once.

| Package | Contents | Depends on |
| --- | --- | --- |
| `deskbot_schema` | Generated Pydantic models from `protocol.schema.json` and `capabilities.yaml` | Nothing |
| `deskbot_core` | `CommandRunner` (sh, su, adb), deny-list guard, config loader, audit log, DI container | `deskbot_schema` |
| `deskbot_executors` | Shell, ADB, app-bridge, and vision executors; `MethodLadder`; decorators | `deskbot_core` |
| `deskbot_mcp` | MCP server; tools generated from the registry; `DeviceController` facade | `deskbot_executors` |
| `deskbot_gateway` | Hermes platform plugin; WebSocket server; A2UI compile and validate; action routing | `deskbot_core`, A2UI Python SDK |
| `deskbot_codegen` | Generators for Kotlin, Python, manifest, XML, and test skeletons | `deskbot_schema` |
| `deskbot_ops` | Watchdog, boot, backup, self-update, and rollback scripts | `deskbot_core` |

Layering matches the app: tool and plugin entry points are thin adapters over use-case functions, which depend on executor interfaces, never on subprocess calls directly. An import-linter contract enforces this.

## Testing strategy

Every class, tool, skill, schema, and screen has a test, and the build fails when one is missing.

| Level | What is tested | Tools |
| --- | --- | --- |
| Unit: domain | Every use case and entity, with fake ports | JUnit 5, MockK, Turbine, Kotest assertions |
| Unit: data | Repositories and adapters, with fake Android and fake bridge | JUnit 5, Robolectric |
| Unit: Python | Every executor, decorator, guard, and tool, with a fake `CommandRunner` | pytest, pytest-asyncio, Hypothesis |
| UI | ViewModel state, Compose screens, face states, A2UI components | Compose UI tests, Roborazzi screenshot tests |
| Contract | The same JSON fixtures pass schema validation and round-trip in both Kotlin and Python | Shared fixtures folder, both test suites |
| A2UI | Catalog validity, every component renders, every action reaches its handler | A2UI SDK validator, Compose tests |
| Architecture | Dependency rule, no feature-to-feature imports, a test per use case | Konsist, import-linter |
| Integration on device | MCP tool to executor to real Android, on the deskbot phone | Instrumented tests, pytest marked `device` |
| Skills | Each skill run end to end with its verify step | Nightly regression runner (section 8 of the plan) |
| End to end | Wake word to spoken answer, camera question, A2UI tap to action | Scripted scenarios with recorded audio |

**Gates:**

- Codegen writes a failing test skeleton for every new capability, tool, and component, so the gap is visible at once.
- Coverage of at least 90 percent on domain, executors, and core; the build fails below it. Mutation testing (PIT for Kotlin, mutmut for Python) runs nightly on domain and executors.
- The self-update pipeline (plan section 13.2) runs the full unit, contract, and architecture suites before any install, and the on-device suite after.
- Test doubles are shared from one `core:testing` module and one Python `fakes` package; tests never define their own copy of a fake.
