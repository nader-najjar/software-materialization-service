

4. **Implement service deployment and the scheduled runner.** Replace the deployment stub with deployment of the chosen service release to a separate runner workflow. That workflow runs the selected release; it does not build the service on every invocation. Keep scheduling disabled until live validation passes.
5. **Live-test the release flow and enable polling.** Verify a missing release builds and publishes, an existing release skips the build, and the runner executes the selected service release. Check unchanged inputs and a source push, then enable the five-minute schedule.
6. **Add register.** Provide a service operation and manual workflow for registering a materializer. Provision the repository variables and secrets it needs, and update the registry. App installation access must already include the relevant repositories; registration does not grant itself access.
7. **Add bounce.** Take a materializer repository input and publish a fresh execution of its current pinned inputs without advancing them. Keep direct GitHub UI retries rejected and point users to Bounce.
8. **Add rollback.** Let the user select an older saved version, make its pins active, and publish a fresh execution. Define how subsequent advancement resumes from that selection.
9. **Add PR dry runs.** Validate source and materializer PR revisions without changing live inputs. Reconcile materializer declaration changes, report results to the PR, and exclude deployment operations.
10. **Implement measured build/cache optimizations.** Follow the sequence below for builds that cannot reuse a release. Keep this separate from the release-lookup optimization in PR #23.

## Build/Cache Optimization Order

1. Add UB phase timings and establish cold, unchanged warm, and small-change baselines.
2. Investigate Nix setup costs, including the Java toolchain dependency chain.
3. Add suitable Nix and Gradle cache persistence; measure restore/save overhead too.
4. Enable and verify Gradle task-output caching.
5. Evaluate native-tool execution improvements: Gradle workers, local daemon reuse, JVM startup settings, and JavaScript check parallelism.
6. Evaluate JavaScript tool-specific caches and pnpm persistence where measurements justify them.
7. Reduce runtime size and improve packaging, copying, and compression.
8. Evaluate runner resources or persistent runners if the remaining bottlenecks justify them.

Do not add Actions-artifact expiry refreshes or redundant-upload-compression work to the release flow: release assets replace that temporary artifact store. Prebuilt service execution is part of deployment above, not a deferred performance stretch goal.

## The Full Path

```text
Materialization service
  └─ Publishes workflow with pinned inputs
      └─ GitHub schedules one job per matrix row
          └─ Runner checks out the source
              └─ Nix loads pinned Universal Build
                  └─ UB enters the project's Nix environment
                      └─ Native build tool
                          ├─ Resolves dependencies
                          ├─ Configures the build
                          ├─ Compiles
                          └─ Runs tests and checks
                      └─ UB assembles the release
                          ├─ Copies application artifacts
                          ├─ Includes runtime, when applicable
                          └─ Compresses the release
              └─ GitHub uploads the artifact
```

There are two separate questions at every level:

- Can we do the necessary work faster?
- Can we safely reuse work that was already completed?

Cache misses must still produce a correct build. Cached dependency files are not proof that compilation or tests can be skipped.

## Observed Baseline

All five repository builds ran in parallel in [materializer run 43053713109](https://github.com/nader-najjar/pure-materializer/actions/runs/43053713109).

| Repository | Setup, build, and packaging step | Upload |
| --- | ---: | ---: |
| Pure Java | 158 seconds | 13 seconds |
| Pure Java Lambda | 150 seconds | 13 seconds |
| Pure JavaScript | 67 seconds | 4 seconds |
| Pure React | 54 seconds | 2 seconds |
| Pure Schematic | 59 seconds | 2 seconds |

These are rounded observations from one successful run, not benchmark averages. The composite build step includes Nix installation and toolchain setup, so it is not a measure of compilation time alone.

Pure Java's logs give this approximate breakdown:

| Interval | Time |
| --- | ---: |
| UB starts until Gradle starts | 72 seconds |
| Gradle build | 63 seconds |
| Runtime assembly and packaging after Gradle | 20 seconds |
| Artifact upload | 13 seconds |

The uploaded Java artifact was approximately 500 MB. The JavaScript artifact was approximately 75 MB.

For Pure JavaScript, dependency installation took about 2.5 seconds, the check sequence about 10.5 seconds, and Vitest reported about 1.14 seconds. These measurements argue for examining setup and packaging as well as the native build.

The [service run that published this workflow](https://github.com/nader-najjar/software-materialization-service/actions/runs/43053289401) spent approximately 129 seconds building the service and 17 seconds executing materialization. Reusing a prebuilt service release is a separate stretch goal.

## 1. Materialization and Complete Release Reuse

**Owner: materialization service.**

The service decides which repository revisions and platforms enter the workflow. The generated matrix builds the declared inputs; equivalent matrix rows are deduplicated within that workflow.

PR #23 implements reuse of an already-produced GitHub release asset. An existing asset skips checkout, build, and publication in generated build jobs; the matrix job still runs its guard and lookup steps.

That identity must cover the source revision, platform, build configuration, and tooling that affect the result. Repository name alone is insufficient, and a source SHA alone is insufficient if relevant external inputs can differ. The proposed release assets do not use the Actions-artifact expiry timer.

This belongs to the service because it decides whether a build is needed. It does not belong in a compiler cache.

**Planned implementation:**

- Reuse complete releases across materializations.
- Build the materialization service once per release and execute that artifact, instead of rebuilding the same pinned service for every invocation.

These are separate from making a normal build faster.

## 2. Workflow Scheduling and Runner Resources

**Owner: generated workflow and runner configuration.**

The matrix already runs repositories in parallel. Each job has its own runner and workspace; one job does not automatically inherit files downloaded by another.

The pipeline's finishing time is largely determined by the slowest build, rather than the sum of all five builds. Increasing matrix concurrency will not shorten an individual Java build when all rows already start together.

Non-cache options include:

- Faster CPUs for compilation, static analysis, and compression.
- More cores for work that can run concurrently.
- Enough memory to avoid garbage-collection pressure or swapping.
- Faster storage for dependency extraction and runtime copying.

More cores are not automatically useful. Excessive workers can compete for memory and CPU. Measure the actual bottleneck before changing runner size or worker counts.

Persistent runners can retain toolchains, caches, and warm processes, but introduce a separate runner-management responsibility. They are not required to improve the current hosted-runner flow.

## 3. Nix Bootstrap and Toolchain Setup

**Owner: Nix; CI persistence belongs to GitHub Actions.**

Before the native build starts, the runner must:

1. Fetch the pinned Universal Build source.
2. Obtain UB's dependencies, including its pinned Nix.
3. Evaluate the project's flake.
4. Obtain the JDK and Gradle, or Node and pnpm.
5. Build any Nix packages without available substitutes.

The logs show packages being downloaded from the public Nix binary cache. That already avoids compiling those packages from source. A fresh runner still pays for downloading and unpacking them.

Three kinds of state matter:

| State | What reuse avoids |
| --- | --- |
| Flake source and evaluation caches | Some repeated fetching and evaluation |
| Local Nix store | Downloading or rebuilding packages already present |
| Remote binary cache | Building packages whose outputs are available remotely |

A custom binary cache can hold our own Nix package outputs. Restoring a local store can reduce repeated downloads. Neither should be assumed faster without measuring transfer and extraction costs.

The Java logs include JDK 25 and JDK 21 downloads, plus Gradle derivations being built. Investigate the dependency chain before deciding whether either is unnecessary.

**Current boundary:** Nix provides the build environment, but the application build runs through `nix develop`. The resulting Gradle or pnpm outputs are not automatically application derivations in Nix's output cache. Native tools must handle reuse of that work.

References: [nix develop](https://nix.dev/manual/nix/latest/command-ref/new-cli/nix3-develop.html), [Nix binary cache store](https://nix.dev/manual/nix/latest/store/types/http-binary-cache-store.html).

## 4. Universal Build Orchestration

**Owner: Universal Build.**

UB currently enters the project environment separately to read `BUILD_COMMAND`, read `RELEASE_TYPE`, and run the build command. Reducing repeated entry and evaluation is a possible non-cache improvement. Its individual cost has not been measured.

Normal `build` removes the release output and previous release archive, not the entire native build workspace. Intermediate native outputs can survive local builds. The explicit clean command remains separate.

UB should own:

- Predictable environment entry and configuration.
- Stable working and output locations.
- Preserving useful intermediate state during normal builds.
- Invoking the native build.
- Packaging the release.
- Timings that separate environment setup, native build, and packaging.

UB should not recreate Gradle's task graph or TypeScript's dependency tracking.

A useful first instrumentation change would report elapsed time for each UB phase, so improvements can be evaluated without guessing from one large GitHub step.

## 5. Application Dependency Downloads

**Owner: Gradle or pnpm; CI persistence belongs to GitHub Actions.**

Nix supplies the tools. The native package manager supplies application and build-plugin dependencies.

| Tool | Cached data | What it does not establish |
| --- | --- | --- |
| Gradle | Maven dependencies, plugins, and metadata | That compilation or tests are unchanged |
| pnpm | Downloaded npm package contents | That typechecking, linting, tests, or bundling can be skipped |

Locally, these caches already survive on the machine. Disposable runners need explicit restore/save integration for reuse across runs.

Keep dependency resolution and lockfile enforcement even when cached packages are restored. A cache should supply available content; the package manager should still verify what the current project needs.

In the measured JavaScript run, installation took only 2.5 seconds. A cache restore and upload could cost more than it saves. pnpm explicitly notes that caching its store is not guaranteed to improve installation speed.

Reference: [pnpm CI guidance](https://pnpm.io/continuous-integration).

## 6. Gradle Configuration, Task Reuse, and Execution

**Owner: Java template's Gradle configuration.**

Several different mechanisms are often grouped together as "Gradle caching":

| Mechanism | Work avoided | Observed project configuration |
| --- | --- | --- |
| Dependency cache | Downloading libraries and plugins again | Local cache; no explicit workflow persistence |
| Configuration cache | Recomputing the configured task graph | Enabled |
| Up-to-date checks | Executing a task whose inputs and existing outputs are unchanged | Native Gradle behavior |
| Incremental compilation | Recompiling everything after a small change | Native compiler/build-tool behavior |
| Build cache | Recreating cacheable task outputs, including in a fresh workspace | Not explicitly enabled |
| Daemon | Repeated JVM startup and warmup | Explicitly disabled |

The configuration cache and build cache are separate. Enabling `org.gradle.caching=true` allows Gradle to reuse cacheable outputs according to each task's declared inputs. It does not make every task cacheable.

Before relying on task caching, verify that unchanged builds are correctly up to date and that tasks declare all relevant inputs. Test-result reuse requires the same discipline as compilation reuse.

Configuration caching also permits independent tasks within the same project to run concurrently. Simply adding `--parallel` is not a blanket new optimization for these single-project builds. Test-worker counts and memory settings are separate knobs and should be measured.

The daemon is most relevant to repeated local builds. The current comment attributes disabling it to changing Nix JDK paths. Gradle checks JVM compatibility before reusing daemons, so that rationale should be revisited. A warm daemon does not survive moving to a fresh hosted runner.

A separate startup experiment is to revisit `-Xshare:off` on the Gradle JVM. Both Java templates disable Class Data Sharing, which can reduce JVM startup time when enabled. Benchmark the Gradle-process setting separately from test-JVM flags and their agent compatibility. This is unmeasured and lower priority than the observed Nix setup and packaging costs. Reference: [Java 25 Class Data Sharing](https://docs.oracle.com/en/java/javase/25/vm/class-data-sharing.html).

The Java build includes compilation, unit tests, coverage verification, Checkstyle, SpotBugs, and distribution assembly. Optimize the slow tasks without silently dropping required checks. Task timestamps in a concurrent log do not establish each task's duration; use profiling for that.

References: [build cache](https://docs.gradle.org/current/userguide/build_cache.html), [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html), [Gradle performance](https://docs.gradle.org/current/userguide/performance.html), [daemon](https://docs.gradle.org/current/userguide/gradle_daemon.html).

## 7. JavaScript Checks, Tests, and Compilation

**Owner: JavaScript template scripts and tool configuration.**

The current scripts run installation, checks, tests, and compilation sequentially. The check sequence itself runs TypeScript, ESLint, dependency-cruiser, and Knip sequentially.

After installation, independent checks can potentially run concurrently. Any implementation must preserve failures, wait for required work, and respect shared output directories. Packaging that deletes or populates release outputs cannot race with compilation into those outputs.

Possible tool-specific reuse:

- TypeScript incremental state through `.tsbuildinfo`.
- ESLint's cache for unchanged files, with an appropriate strategy for restored checkouts.
- Existing package-manager stores for dependency content.

Dependency caching does not supply a general cache for test results or production bundles. Vite's development dependency cache should not be treated as a complete production-build cache.

The inspected TypeScript configuration does not enable incremental compilation. If enabled, retained build metadata must remain consistent with emitted outputs. UB clears release outputs, so blindly retaining metadata while deleting emitted files can break the intended reuse.

React's TypeScript check and Vite build are different operations: Vite does not replace typechecking. The non-React JavaScript check also includes tests and configuration files, while its emitting compilation targets application sources. Do not remove either as redundant without preserving its responsibilities.

References: [TypeScript incremental compilation](https://www.typescriptlang.org/tsconfig/incremental.html), [ESLint caching](https://eslint.org/docs/latest/use/command-line-interface#--cache), [Vite TypeScript behavior](https://vite.dev/guide/features.html#typescript).

## 8. Runtime Assembly and Release Compression

**Owner: project flake for runtime contents; UB for copying and packaging.**

Static releases package application files. Executable and container releases additionally include the runtime and its Nix dependency closure.

The Java runtime currently uses a headless JDK. Its approximately 500 MB uploaded release makes runtime selection worth examining: determine whether a smaller compatible runtime can satisfy the application without including development components.

UB builds the runtime package, copies its closure into the release, and compresses the whole release. Reusing the Nix runtime derivation does not automatically eliminate copying or archive creation.

Non-cache options include:

- Reduce the required runtime closure.
- Use faster compression settings.
- Evaluate parallel compression.
- Avoid unnecessary repeated copying.

Lower compression can save CPU while increasing upload size. Measure total packaging and transfer time, not just compressor speed.

UB's current container release packages the application, runtime, and Dockerfile. It does not build a Docker image at this stage, so Docker layer caching does not accelerate this existing step.

## 9. GitHub Artifact Upload (Historical Baseline)

PR #23 replaces this upload with a GitHub release asset upload. The compression proposal below applies only to the earlier Actions-artifact flow and is no longer an action item.

**Owner: generated workflow.**

UB already creates `release-artifact.tar.gz`. The upload action then uses its default compression level of 6, spending additional CPU on an already-compressed archive.

A focused proposal is to set `compression-level: 0` for this upload. That retains the existing release format and avoids redundant compression. The saving must be measured; it will not eliminate the network transfer itself.

The observed Java uploads took about 13 seconds each, compared with 2 seconds for the static releases.

Reference: [upload-artifact compression guidance](https://github.com/actions/upload-artifact#altering-compressions-level-speed-v-size).

## 10. GitHub Cache Persistence

**Owner: workflow/action integration, using native cache formats.**

GitHub restores and saves bytes. The native tool still decides whether those bytes are applicable to the current build.

A maintained Gradle caching integration can preserve Gradle User Home while Nix continues to supply Gradle and Java. This does not require switching toolchain installation to setup-java or installing another Gradle version.

Our generated builds run on the materializer repository's `execution` branch. Gradle's GitHub action defaults to cache writes only on the default branch, so the integration must deliberately permit writes for this execution flow.

All source repositories are checked out inside the materializer's workflow. They do not automatically get separate GitHub cache namespaces just because checkout targets different repositories. Keys must account for source identity where appropriate, platform, tool compatibility, and relevant inputs.

Additional constraints:

- Dependency-cache fallback can be useful because the package manager still resolves current dependencies.
- A generic cache hit must not be used as proof that an entire build can be skipped.
- GitHub branch visibility rules constrain which caches a run can restore.
- Parallel jobs need a deliberate save/key strategy; do not assume their cache contents merge.
- Configuration-cache persistence is distinct from dependency/task-cache persistence and needs separate compatibility and sensitive-data handling.
- Cache storage, transfer, extraction, and eviction have costs; avoid saving every generated directory by default.

References: [Gradle GitHub integration](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md), [GitHub cache behavior](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching).

## Responsibility Map

| Layer | Responsibility |
| --- | --- |
| Materialization service | Which releases to build; later, reuse complete releases |
| GitHub workflow | Runners, matrix, cache transport, artifact upload |
| Nix | Pinned tools and reuse of Nix-built packages |
| UB | Enter environment, preserve working state, invoke native build, package release |
| Native tools | Dependency resolution, task correctness, incremental work, task caches, internal parallelism |
| Project configuration | Required checks, runtime selection, tool-specific tuning |

## Measuring Each Optimization

Use the ordered action items at the top of this document. For each performance change, compare equivalent inputs and platforms, repeat measurements, and include cache restore/save time. Check a cold build, an unchanged warm build, and a small source change. Verify that artifacts and required checks remain correct.
