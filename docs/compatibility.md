# Compatibility and behavior

The behavior contract is the spectator functionality described in the README. A build or successful startup is not a gameplay compatibility guarantee.

| Minecraft | Forge build | Build | Client startup | Dedicated server | Gameplay | Release |
| --- | --- | --- | --- | --- | --- | --- |
| 1.6.1 | 8.9.0.775 | Blocked | Not tested | Not tested | Not tested | Not ready |
| 1.6.2 | 9.10.1.871 | Pass | Pass | Pass | Not tested | Not ready |
| 1.6.3 pre-release | 9.11.0.878 | Pass | Pass | Pass* | Not tested | Not ready |
| 1.6.4 | 9.11.1.964 build; 9.11.1.1345 runtime | Pass | Pass | Pass | Not tested | Not ready |

Startup checks on 2026-11-15 used `0.1.0-dev`, Temurin Java 8u472, and Prism's per-instance legacy Java certificate configuration. *The 1.6.3 Forge server manifest requests LaunchWrapper 1.3 while its installer downloads 1.7; startup succeeded with 1.7 placed first on the server classpath. Before release, verify spectator mode transitions and permissions, flight and collision, camera following and exit, teleporting, read-only block and entity inventories, reconnect, and client/server synchronization. Check both client and dedicated-server startup for every target. Bytecode transforms must fail if required hooks are missing.

# Component conventions

`common/` contains Minecraft-independent code. `implementations/` contains reusable components named for their purpose and distinguishing API or behavior. A name describes the code, not a version range or an upgrade sequence. Each component explicitly lists its assessed target IDs in `versions.json`; consult the target status and table above for the level of verification. A component may support disjoint patches or span families. When an API changes inside a family, add a narrow component or adapter and select it explicitly. Target-only code lives under `targets/<version>/`.

| Component | Responsibility | Currently selected by buildable targets |
| --- | --- | --- |
| `forge-fml-spectator` | Forge mod, spectator behavior, client integration, and networking | 1.6.2, 1.6.3, 1.6.4 |
| `fml-spectator-transformer` | ASM hooks for spectator behavior | 1.6.2, 1.6.3, 1.6.4 |
| `snapshot-inventory` | Inventory snapshots and viewer | 1.6.2, 1.6.3, 1.6.4 |
| `spectator-packet-protocol` | Packet identifiers and shared protocol constants | 1.6.2, 1.6.3, 1.6.4 |
| `fml-core-without-sorting-index` | Core plugin interface without a sorting index | 1.6.2, 1.6.3 |

These are current code boundaries, not a claim that every class in a component has the same compatibility boundary. If a target needs different behavior, first extract the smallest practical interface and implementations, with names explaining their actual difference. For example, only after confirming the APIs, GUI integration could have event-based and bytecode-hook implementations. Keep the rest shared. If two implementations cannot be described clearly yet, investigate the difference before naming them; avoid `v1`/`v2` or family names that suggest unverified coverage.

## Roadmap: Minecraft 1.3–1.7

This is the intended investigation range, not a support claim. Only exact targets in the table above have been assessed. Determine Forge availability and suitable build tooling for each exact Minecraft release before adding it; do not assume every patch has a usable Forge release.

1. Finish 1.6.1 using the adapter work described below, then verify gameplay for all buildable 1.6 targets.
2. Inventory Forge releases, mappings, Java requirements, loader behavior, networking, and relevant game APIs for 1.5, 1.7, 1.4, and 1.3. Record exact candidates and evidence before choosing toolchains or ordering individual patches. This order prioritizes adjacent families; discoveries may change it.
3. For each candidate, add a target and compile existing components against its own mappings. Extract small adapters at observed API boundaries, including boundaries within a family. Record exact component assignments and avoid adding unverified targets to `compatibleTargets`.
4. Add the target to the build matrix and Prism workflow when its toolchain works. Check client, dedicated server, transformer hooks, gameplay, and multiplayer before marking it release-ready. Record missing behavior and tests for every target.
5. For feature changes, trace each affected capability through the target map and update all applicable implementations. Document intentional differences and validate affected targets; no Minecraft release is the reference implementation for the others.

`forge-fml-spectator`, `snapshot-inventory`, `spectator-packet-protocol`, and `fml-spectator-transformer` are compiled for 1.6.2, 1.6.3, and 1.6.4; `fml-core-without-sorting-index` supplies the older Forge core-plugin interface for 1.6.2 and 1.6.3. Their Forge loaders run transformers before deobfuscation; 1.6.4 sorts them after it. The transformer normalizes names for matching while retaining the original instructions. Successful startup validates hooks loaded at startup only; later-loaded classes and behavior still need in-game checks. Forge 1.6.1 lacks several later GUI, mouse, and render APIs, so it must not be added to this implementation's compatibility list until adapters are implemented and tested.

# Toolchains and research

Targets build separately with their own Forge mappings. 1.6.4 uses ForgeGradle 1 and Gradle 3; 1.6.2 and 1.6.3 use Voldeloom 2.4, Gradle 7.6.4, and Java 8. Forge's source archives are SHA-256 pinned. The mapping preparation removes five duplicate renderer bridge mappings that conflict in Tiny Remapper, analogous to Voldeloom's [own 1.6.4 workaround](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/blob/disaster-time/src/main/java/net/fabricmc/loom/mcp/Srg.java). This is a build-time transformation; real Forge runtime jars remain untouched. Voldeloom's authors describe [limitations and runtime-testing requirements](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom).

The 1.6.1 development artifact uses an older binpatch record format without the checksum field present in 1.6.2. The prepared build-only converter inserts a dummy field for Voldeloom, which ignores that checksum. Its source and universal Forge archives are checksum-pinned. The 1.6.1 build currently fails because game-facing APIs differ; the converter is not evidence of a working port.

To complete 1.6.1, extract the GUI-opening and mouse-input integration from `ClientProxy` behind a small adapter. The existing adapter can use Forge events; the 1.6.1 adapter needs independently verified bytecode hooks because those events do not exist there. Adapt player and entity rendering, skin lookup, and server forced-game-mode detection to the 1.6.1 APIs. Keep packet, inventory, and transformer components shared only after compiling them against 1.6.1 and exercising their actual behavior. Then verify client and server startup, all transformer matches, in-game spectator actions, and multiplayer before claiming support. Do not add 1.6.1 to a component's validated target list merely because its source compiles.

Forge publishes [1.6.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.6.1.html), [1.6.2](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.6.2.html), [1.6.3](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.6.3.html), and [1.6.4](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.6.4.html) builds. 1.6.3 is a pre-release, not a stable Minecraft release.
