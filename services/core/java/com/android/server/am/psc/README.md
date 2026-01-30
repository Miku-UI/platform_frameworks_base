# com.android.server.am.psc Package

This package is still WIP and tracked by b/425766486.

This package contains the `ProcessStateController` and related classes, responsible for managing and
determining the state and importance of Android processes, previously part of the
`com.android.server.am` package. This package encapsulates the logic for calculating OOM
(Out Of Memory) adjustment values and related process attributes like process state, process
capabilities, and scheduling group.

## Overview

The primary goal of this package is to:

- **Isolate OOM Adjuster Logic:** Separate the complex OOM adjustment logic from the broader
  `ActivityManager` codebase, improving modularity and maintainability.
- **Provide a Clear Interface:** Offer a well-defined public interface (`ProcessStateController`)
  for interacting with the OOM adjustment mechanism.
- **Centralize State Management:** Manage all process state that affects OOM adjustment and other
  process state within this package, ensuring consistency and simplifying updates.
