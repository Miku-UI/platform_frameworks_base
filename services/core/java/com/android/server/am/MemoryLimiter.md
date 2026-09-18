# MemoryLimiter

MemoryLimiter is a system service that monitors and limits the memory usage of
application processes using Linux cgroups (v2). It provides a mechanism to
prevent individual apps from consuming excessive amounts of system memory, which
could otherwise lead to system-wide memory pressure and aggressive Out-Of-Memory
(OOM) killing of critical processes.

## How it Works

### Architecture

MemoryLimiter consists of a Java component within `system_server` and a native
component linked via JNI.

1.  **Java Layer (`MemoryLimiter.java`)**: Manages the high-level logic,
    including process state tracking and configuration. It communicates with the
    `ActivityManagerService` (AMS) to stay informed about process lifecycle
    events and state changes.
2.  **JNI Layer (`com_android_server_am_MemoryLimiter.cpp`)**: Interfaces with
    the Linux kernel's cgroup filesystem. It sets limits and uses `inotify` to
    monitor cgroup event files for limit breaches.
3.  **Cgroups**: MemoryLimiter leverages the `memory.high` and `memory.swap.max`
    attributes in the `memory` controller of cgroups v2.
    -   `memory.high`: A soft limit. When exceeded, the process is throttled and
        the kernel attempts to reclaim memory from it.
    -   `memory.swap.max`: Limits the amount of swap space the process can use.

### Process Monitoring

Only application processes (UID >= 10000) are monitored by default. System
processes are generally exempt to ensure core system stability.

MemoryLimiter assigns memory limits based on the process's current state:

-   **Visible Processes**: Processes that are currently perceptible to the user.
    Memory limits are defined by the configuration file.

-   **Not Visible Processes**: Background processes that are not currently
    interacting with or visible to the user.  Memory limits are defined by the
    configuration file.

-   **Cached Processes**: `memory.swap.max` is unlimited.  `memory.high` is left
    unchanged from whatever previous setting it had.

-   **Unrestricted Processes**: `memory.swap.max` and `memory.high` are both
    unlimited.

### Limit Details

This table maps process states (defined in `ActivityManger`) to memory limits:

| Process state            | Limit          |
| :---                     | :---           |
| UNKNOWN                  | <none>         |
| PERSISTENT               | Unrestricted   |
| PERSISTENT_UI            | Unrestricted   |
| TOP                      | Visible        |
| BOUND_TOP                | Visible        |
| FOREGROUND_SERVICE       | Not-visible    |
| BOUND_FOREGROUND_SERVICE | Not-visible    |
| IMPORTANT_FOREGROUND     | Visible        |
| IMPORTANT_BACKGROUND     | Not-visible    |
| TRANSIENT_BACKGROUND     | Not-visible    |
| BACKUP                   | Not-visible    |
| SERVICE                  | Not-visible    |
| RECEIVER                 | Not-visible    |
| TOP_SLEEPING             | Visible        |
| HEAVY_WEIGHT             | Not-visible    |
| HOME                     | Not-visible    |
| LAST_ACTIVITY            | Not-visible    |
| CACHED_ACTIVITY          | Cached         |
| CACHED_ACTIVITY_CLIENT   | Cached         |
| CACHED_RECENT            | Cached         |
| CACHED_EMPTY             | Cached         |
| NONEXISTENT              | <none>         |

### Over-limit Actions

When a process exceeds its assigned `memory.high` limit or its `memory.swap.max`
limit, the native layer notifies the Java layer. The Java layer generates a
statsd atom and a log message.  Atoms are generated once for each process.

If the sum of a process's anon memory and swap exceed the sum of `memory.high` and
`memory.swap.max` then MemoryLimiter will generate a third atom.  The process
will be notified via the ProfilingManager service and 30s later the process will
be killed.

## Configuration

MemoryLimiter is configured via an XML file located on the vendor partition.

-   **File Path**: `/vendor/etc/memory-limiter-config.xml`

The configuration file is optional but if it does not exist, MemoryLimiter is
disabled. If the file does exist but is unreadable or invalid, MemoryLimiter
throws a fatal exception.

The configuration file contains metadata (such as the minimum required RAM)
that is used to determine which, if any, of its parts apply to the current
system.  If no parts apply to the current system, MemoryLimiter is disabled.
This is not an error.

NOTE: A developer can locally bypass the need for a configuration file on the
vendor partition by enabling the trunk-stable flag

-    `com.android.serve.am.memory_limiter_force_on`.

### XML Format

The configuration file follows the schema defined in
`memory-limiter-config.xsd`.

```xml
<MemoryLimiterConfig>
  <version>1</version>
  <configList>
    <limitSet>
      <!-- Limits for a phone with at least 14G of ram: 8G/4G/4G/4G -->
      <minimumRequiredMemTotal>14336</minimumRequiredMemTotal>
      <memVisible>8192</memVisible>
      <memNotVisible>4096</memNotVisible>
      <swapVisible>4096</swapVisible>
      <swapNotVisible>4096</swapNotVisible>
    </limitSet>
    <limitSet>
      <!-- Limits for a phone with at least 10G of ram: 6G/3G/3G/3G -->
      <minimumRequiredMemTotal>10240</minimumRequiredMemTotal>
      <memVisible>6144</memVisible>
      <memNotVisible>3072</memNotVisible>
      <swapVisible>3072</swapVisible>
      <swapNotVisible>3072</swapNotVisible>
    </limitSet>
  </configList>
</MemoryLimiterConfig>
```
-   **version**: A positive integer identifying the configuration version.  This
    must be 1.
-   **configList**: The list of **LimitSet** elements.  There must be at least
    one element.

Each **LimitSet** element contains the following elements.  All memory values
are in units of MiB.

-   **minimumRequiredMemTotal**: The minimum required available memory for this
    entry to be valid.
-   **memVisible**: The memory allowed to visible processes.
-   **memNotVisible**: The memory allowed to not-visible processes.
-   **swapVisible**: The amount of swap allowed to visible processes.
-   **swapNotVisible**: The  amount of swap allowed to not-visible processes.

### Modifying Configuration

To change the system-wide limits:

1.  Modify `/vendor/etc/memory-limiter-config.xml`.
2.  Reboot the device or restart `system_server` for the changes to take effect.

Tip: Use the unit test `MemoryLimiterTests#testXmlConfig` to validate the format
of a new configuration file.  Temporarily replace `data/config-default.xml` with
the new configuration file and run the test.

## Shell Commands

The `am memory-limiter` command allows developers and testers to interact with
the service at runtime.

### Command Usage

```bash
am memory-limiter <SUBCOMMAND>
```

### Subcommands

#### `status`

Reports the current operational status of the MemoryLimiter.

```bash
$ adb shell am memory-limiter status
Memory limiter
  enabled                  monitoring=true          ignored=none
  visibleMem=1948MB        visibleSwap=974MB        notVisibleMem=974MB      notVisibleSwap=487MB
  started=36               watched=36               watch-failed=0           events=0
  processes=36             process-hwm=36
```

-   **monitoring**: Whether the limiter is actively watching processes.
-   **visibleMem / notVisibleMem**: The calculated absolute memory limits for
    each state.
-   **events**: The number of times a process has exceeded its limit.
-   **processes**: Current number of processes being monitored.

#### `ignore`

Temporarily excludes a UID or all processes from being limited. This is useful
for performance testing or when a specific app needs to be allowed to exceed its
limits without interference.

```bash
# Ignore a specific UID
$ adb shell am memory-limiter ignore 10087

# Ignore all processes (effectively disables limiting)
$ adb shell am memory-limiter ignore all

# Resume normal operation
$ adb shell am memory-limiter ignore none
```

#### `manual`

Overrides the calculated limits for a specific process (by PID) with a custom
percentage.

```bash
# Set a 10% limit for PID 1234
$ adb shell am memory-limiter manual 1234 10

# Remove the manual override for PID 1234
$ adb shell am memory-limiter manual 1234 none
```

**Note**: Manual overrides are applied to the process at its current lifecycle
state. If the process is restarted, or changes state (e.g. goes from background
to foreground), it will return to the configured limits based on its new state.
This is unless `memory-limiter ignore` is also in effect.
