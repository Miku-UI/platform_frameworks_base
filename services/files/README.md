# Android FileService Architecture

The `FileService` is a dedicated system service designed to handle privileged,
long-running file operations—such as moving, copying, or deleting files on
behalf of Android applications. By offloading these potentially blocking I/O
tasks to a central system component, the platform ensures that operations are
performed safely, efficiently, and with proper lifecycle management. It also
helps to provide a "permissionless" set of APIs for specific operations and
avoid further propagation of overly permissive "MANAGE_EXTERNAL_STORAGE" style
permissions.

## System Overview

The architecture follows the standard Android Manager-Service pattern but
introduces a robust asynchronous execution model to handle the latency and
unpredictability of file system operations.

At a high level, the system is designed to decouple the *request* for an
operation from its *execution*. When a client application (via `FileManager`)
requests a file operation, the system does not block the calling thread.
Instead, it validates the request, queues it, and immediately returns a handle
(a `requestId`). This allows the client to remain responsive while the heavy
lifting happens in the background.

## Architectural Design

The system is composed of four distinct layers, each with a specific
responsibility:

### 1. The Client Interface
The `FileManager` serves as the entry point for applications. It abstracts the
IPC (Inter-Process Communication) complexities, presenting a clean API for
enqueueing operations and fetching result updates. It communicates with the
system server via the `IFileService` AIDL interface.

### 2. The Service Core (State Machine)
The `FileService` acts as the central brain of the system. It is responsible
for:
*   **Admission Control:** Deciding whether a request can be accepted based on
current system load.
*   **State Management:** Tracking the lifecycle of every operation from
inception to completion.
*   **Result Aggregation:** Maintaining a history of operation results that
clients can query.

### 3. The Dispatcher (Routing)
The `FileOperationDispatcher` provides an abstraction layer between the service
logic and the actual execution. It inspects the `FileOperationRequest`
(specifically the Source and Target) to determine the appropriate strategy for
handling the file operation. This design allows the system to support multiple
backends (e.g., `installd` for private app data vs. other mechanisms for shared
storage) without modifying the core service logic.

### 4. Processors (Workers)
Components like `InstalldProcessor` implement the `FileOperationProcessor`
interface. These are the workers that perform the actual I/O. They are designed
to be stateless regarding the request lifecycle; they simply process the task
and report results or errors back to the dispatcher.

## Operational Mechanics

### Request Ingestion and Backpressure
To maintain system stability, the `FileService` enforces strict admission
control. When a request receives a call to `enqueueOperation`, the service
checks the current load against a hard limit (`MAX_PENDING_REQUESTS`).
*   **Under Load:** If the system is saturated, it immediately rejects the
request with a `BUSY` error code. This creates a backpressure mechanism,
preventing the system server from running out of memory due to an unbounded
queue of pending file operations.
*   **Accepted:** If accepted, the service generates a unique ID, transitions
the request state to `QUEUED`, and schedules it for processing.

### Asynchronous Execution Pipeline
Once a request is accepted, it is handed off to the `FileOperationDispatcher`.
The dispatcher uses a background thread pool to process requests. This ensures
that the main thread of the system server is never blocked by file I/O.
1.  The request waits in the dispatcher's queue until a worker thread is
    available.
2.  The dispatcher selects the correct `FileOperationProcessor`.
3.  The processor runs the operation, periodically invoking a `StatusCallback`.

### State Management and History Retention
One of the most complex aspects of the `FileService` is managing the memory
footprint of result objects while ensuring clients never lose track of active
operations. The service uses a sophisticated dual-structure approach:

1.  **Active Queue (`mPendingRequests`):** A lightweight map tracking only the
currently running operations. This is used to enforce the admission control
limits.
2.  **History Cache (`mResults`):** A synchronized `LinkedHashMap` that stores
the `FileOperationResult` for both active and recently completed operations.

**The "Safe Eviction" Strategy:** To prevent memory leaks, the History Cache
has a configured maximum size (`MAX_HISTORY_SIZE`). However, a standard LRU
(Least Recently Used) cache could theoretically evict a result object for a
long-running operation that hasn't been polled recently. To solve this, the
service implements a custom eviction policy: **Active operations are strictly
protected.** The system will only evict the oldest entry if it has reached a
terminal state (`FINISHED` or `FAILED`). This guarantees that as long as an
operation is running, its result record is preserved in memory, even if the
history cache needs to temporarily grow beyond its soft limit.

## Result Reporting and Completion Notification

The system provides two mechanisms for clients to track the status of an enqueued
operation:

### 1. Polling (Pull Model)
Clients can use the `requestId` returned during enqueueing to poll for the
current state of the operation via `FileManager.fetchResult()`. This design
choice avoids the overhead and complexity of managing persistent callback
binders for clients that only need periodic or final updates.

### 2. Completion Broadcasts (Push Model)
For scenarios where an application needs to be proactively notified when an
operation finishes, the service supports a subscription-based broadcast
mechanism:

*   **Subscription:** Clients call
    `FileManager.registerCompletionListener(requestId)`.
*   **Delivery:** Upon reaching a terminal state (`FINISHED` or `FAILED`), the
    service sends an explicit broadcast (`ACTION_FILE_OPERATION_COMPLETED`)
    directly to the registered package.
*   **Payload:** The broadcast Intent contains the `requestId` and the final
    `FileOperationResult` object.

## Binder Transaction Limits

To adhere to Android Binder transaction size limits and prevent
`TransactionTooLargeException`, the system enforces specific constraints on the
data returned in `FileOperationResult`:

*   **Failure Reporting Cap:** The list of specific failure messages
(`getFailures()`) is hard-capped at **200 entries**. If an operation encounters
more than 200 individual file failures, only the first 200 are reported. The
operation status will still correctly reflect `STATUS_FAILED`.
*   **Terminal State Only:** To further conserve bandwidth, the list of
failures is **only populated** when the operation reaches a terminal state
(`FINISHED` or `FAILED`). Intermediate status updates will not contain partial
failure lists.
