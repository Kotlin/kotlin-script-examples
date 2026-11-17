# Kotlin Scripting Examples: `.mcp.kts` — a script definition with IntelliJ IDEA support

An end-to-end example of a script definition whose API is *not known until the script is compiled*,
and of shipping that definition to IntelliJ IDEA so the editor offers the same completion and type
checking as the command line.

A `.mcp.kts` script declares a [Model Context Protocol](https://modelcontextprotocol.io) server:

```kotlin
@file:McpServer("youtrack", "https://youtrack.jetbrains.com/mcp")

youtrack.searchIssues(query = "project: KT #Unresolved").issues.forEach { println(it.summary) }
```

During *refinement* the definition connects to that server, reads its tool schemas, generates Kotlin
bindings for them, and hands them to the compiler. `youtrack.searchIssues(...)` resolves, completes
and type-checks — in `kotlinc`, in a scripting host, and in the IDE — against an API that only
exists because a remote server was asked what it can do.

## What it demonstrates

- **`refineConfiguration { onAnnotations(...) }`** driving real work: a network probe, code
  generation, and a file written to disk, all inside compilation configuration refinement.
- **`importScripts`** as a delivery mechanism: the generated bindings are themselves Kotlin scripts,
  compiled alongside the user's script rather than pre-built into a jar.
- **`providedProperties`** in both phases — declared as types at compile time, and supplied as
  reflectively instantiated values at evaluation time (`refineConfigurationBeforeEvaluate`).
- **A pinned, deterministic compile classpath** (`updateClasspath` over a fixed anchor list) instead
  of inheriting whatever the host happens to have loaded.
- **IDE integration** via `ScriptDefinitionsProvider`, including the part that is easy to get wrong:
  a file the definition writes with plain IO is invisible to the IDE until the VFS is refreshed, and
  a synchronous refresh is refused under a read lock.

## Building

This example is a **standalone Gradle build**, not part of the root `kotlin-script-examples` project.
It is deliberately not linked: it pins its own Kotlin version, and its plugin module pulls a ~1.5 GB
IDE distribution.

The plugin targets IntelliJ IDEA 2026.3 (build 263), and `runIde` starts it.

It has no wrapper script of its own; drive it from the repository root:

```bash
./gradlew -p jvm/mcp-kts buildPlugin   # the installable plugin zip
./gradlew -p jvm/mcp-kts runIde        # a sandbox IDE with the plugin installed
```

Only the tasks that compile against the IDE (`buildPlugin`, `runIde`, `:mcp-intellij-plugin:build`)
download the distribution; the first of them takes a while, and subsequent builds reuse it.

The plugin zip lands in `mcp-intellij-plugin/build/distributions/`; install it with
`Settings | Plugins | gear icon | Install Plugin from Disk...`.

### Opening it in an IDE

Because it is a separate build, IntelliJ IDEA will not pick it up from the root project. Either:

- **open it on its own** — `File | Open...`, select `jvm/mcp-kts`, and open it as a project; or
- **attach it to the root project** — with `kotlin-script-examples` open, go to the Gradle tool
  window, press **+** (*Link Gradle Project*) and select `jvm/mcp-kts/build.gradle.kts`.

Either way the first sync resolves `mcp-intellij-plugin`, and that is when the IDE distribution is
downloaded. To work only on the scripting side, unlink the project again or exclude that module.

## Trying it

Start the demo server — it needs no build, only the `kotlin` runner from a Kotlin compiler
distribution:

```bash
kotlin jvm/mcp-kts/samples/demo-server.main.kts   # port 3000, or pass one as the argument
```

Then open [`samples/hello.mcp.kts`](samples/hello.mcp.kts) in the `runIde` sandbox.
[`samples/idea.mcp.kts`](samples/idea.mcp.kts) talks to IntelliJ's own MCP server, which wants its
auth token and, with several projects open, the project path:

```kotlin
@file:McpServer(
    name = "idea",
    url = "http://127.0.0.1:64342/sse",
    envHeaders = [
        "IJ_MCP_AUTH_TOKEN=IJ_MCP_AUTH_TOKEN",
        "IJ_MCP_SERVER_PROJECT_PATH=IJ_MCP_SERVER_PROJECT_PATH",
    ]
)
```

```bash
export IJ_MCP_AUTH_TOKEN="$(cat "<IDE config dir>/global_mcp_server_token.txt")"   # see Settings | Tools | MCP Server
export IJ_MCP_SERVER_PROJECT_PATH=/path/to/project
./gradlew -p jvm/mcp-kts runIde   # from the same shell, so the IDE sees both
```

`headers = ["Name=Value"]` are baked into the generated bindings, which sit next to the script — do
not put secrets there. `envHeaders = ["Name=ENV_VARIABLE"]` are read from the environment at call
time, and an unset variable leaves the header out. A bearer token is read the same way from
`MCP_KTS_TOKEN_<SERVERNAME>`.

Transport follows the URL: paths ending in `/sse` use SSE, everything else streamable HTTP. `stdio`
is not supported.

## Generated bindings

The generated bindings are the source of truth for a script's typed API. They are written next to
the script and travel with it (commit them or git-ignore them, as you prefer):

```
foo.mcp.kts
mcp-kts-generated/foo/<ServerName>Bindings.mcp.kts
```

Every refinement re-probes the server:

- reachable, schema unchanged: bindings left as-is
- reachable, schema changed: bindings rewritten, with a warning
- unreachable, bindings already exist: existing bindings reused with a warning, so a generated script
  still compiles offline
- unreachable, no bindings yet: compilation fails with a clear error — open the script once while the
  server is running

There are no lockfiles and no separate schema cache; the generated file itself records the last
accepted schema. When the script has no on-disk location (an in-memory source, say), the bindings
fall back to the cache directory instead of a sibling folder.

Override that cache directory (`~/.cache/kotlin-mcp-kts/` by default) with `-Dmcp.kts.cache.dir=...`
or `MCP_KTS_CACHE_DIR`. Pass `-Dmcp.kts.printBindings=true` to print the generated bindings during
refinement.
