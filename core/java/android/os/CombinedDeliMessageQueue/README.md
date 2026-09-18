# DeliQueue: A Lock-Free Message Queue for Android

This document describes the data structure and algorithms for DeliQueue, a new
implementation of Android's `MessageQueue` API. It is designed for high
concurrency with minimal lock contention.

This document is for engineers who need to understand the internal workings of
the Android message queuing system, particularly its performance characteristics
and concurrency model.

[TOC]

## DeliQueue Overview

DeliQueue provides a message queue with the following characteristics:

*   **Concurrent Operations:** Supports multiple concurrent clients which may
    add, remove or check for the presence of messages.
*   **Ordered Execution:** Messages scheduled for the same time are executed in
    the order they were inserted.
*   **Sync Barriers:** Supports synchronization barriers to control the
    execution of synchronous messages.

The major difference between DeliQueue and the legacy `MessageQueue`
implementation is in concurrent operations. This is explained in more detail
later in this document.

## Problem Statement

The goal for this implementation is to address performance issues with the
existing message queue implementation used in Android. The legacy
implementation, found in `MessageQueue`, uses a single, global lock to protect
an ordered singly-linked list. This design leads to several performance issues:

*   **Insertion:** O(n) for most cases, as messages must be inserted in sorted
    order.
*   **Removal:** O(n), as the entire queue must be scanned.
*   **Polling:** O(1) in the typical case, but can degrade to O(n) when
    synchronization barriers are present.

The single lock can cause priority inversion, where a low-priority thread holds
the lock and blocks the high-priority UI thread. This can lead to dropped frames
and a poor user experience.

## Design Goals

The new implementation aims for the following performance characteristics:

*   **Insertion:** Amortized constant time, messages are not inserted into the
    heap (described below) until poll
*   **Polling:** O(log n)
*   **Removal:** O(n)

Crucially, the different operations listed above are mutually concurrent, not
mutually exclusive.

To achieve this, DeliQueue uses a lock-free design based on a
[Treiber stack](https://en.wikipedia.org/wiki/Treiber_stack) and a single
threaded [min-heap](https://en.wikipedia.org/wiki/Min-heap), minimizing the need
for expensive locks and reducing contention.

Message objects are no longer pooled for mutiple reasons:

1.  To avoid the [ABA problem](https://en.wikipedia.org/wiki/ABA_problem)
    problem with compare-and-swap (CAS) operations.
2.  On removal, messages are tombstoned instead of immediately removed. Thus a
    removed message can still be seen by threads iterating the message stack.
3.  Message pooling was initially introduced to avoid expensive stalls due to
    garbage collection. The garbage collector has improved drastically since
    that decision was made and pooling is no longer necessary.

## Data Structure

DeliQueue consists of two main data structures:

1.  **Treiber Stack:** A lock-free stack used for message submission. This is
    where concurrency is mediated, allowing multiple threads to add messages
    concurrently. DeliQueue uses two Treiber stacks. The `message stack` is
    where new messages are inserted. Messages stay in the `message stack` until
    delivery. DeliQueue also uses a Treiber stack to track messages that have
    been marked for removal, called the `freelist`.
2.  **Min-Heap:** A single looper thread adds messages from the Treiber stack
    into one of two min-heaps (one for synchronous messages and barriers, the
    other for asynchronous messages). Min-heaps sort work based on message
    timestamps.

The state of the queue is managed by several atomic variables:

*   A pointer to the top of the message stack.
*   A pointer to the top of the freelist.
*   An atomic variable for the looper thread's wakeup state (See
    [Thread Wakeup Coordination](#thread-wakeup-coordination))
*   A tagged reference count for managing the queue's lifecycle (quitting
    state).
*   A pointer to the earliest active sync barrier.
*   A sentinel node which is pushed to the top of the stack to indicate when the
    `MessageQueue` is quitting.

## Operations

### Adding a Message

To add a message, clients push it onto the message stack using a
compare-and-swap (CAS) operation.

For example, to add message `C` to a stack containing `A` and `B`:

1.  The initial stack is `Top -> B -> A`.
2.  The client creates node `C` and set its `next` pointer to the current top of
    the stack, `B`.
3.  It then use a CAS operation to atomically update the top of the stack to
    point to `C`.

The final stack is `Top -> C -> B -> A`.

After pushing the message, the client checks if the message is ready to run, in
which case it will wake up the looper thread.

There may be a `quitting` sentinel node at the top of the stack which prevents
insertion when the MessageQueue is trying to quit. See
[Quitting the Looper](#quitting-the-looper) for more details.

#### Sync Barriers

Adding a sync barrier is similar to adding a regular message. However, it is not
necessary to wake the looper thread immediately, as barriers only affect the
scheduling of subsequent synchronous messages.

### Removing a Message

To remove a message, DeliQueue iterates through the message stack and marks
matching messages as removed. This is a logical removal (or "tombstoning"); the
physical removal from the message stack and the min-heap is handled later by the
looper thread.

The removal process is as follows:

1.  **Iterate the stack:** The remover thread iterates through the message
    stack.
2.  **Mark for removal:** If a message matches the removal criteria, the thread
    uses a CAS operation to set an `isRemoved` flag on the message object.
3.  **Add to freelist:** Successfully marked messages are added to a "freelist"
    stack.

This design defers the physical removal and memory reclamation to the looper
thread. This avoids the complexity of lock-free doubly-linked list removal and
the need for extra allocations to emulate tagged pointers in Java.

To prevent retaining large user objects (like `Runnable`s) in removed messages,
the reference fields of a message are nulled out immediately after it is marked
for removal.

### Looper Thread Processing

The looper thread is responsible for inserting stack messages into the heap,
processing removals, and executing messages.

#### 1. Sweeping Messages into the Heap

The looper thread maintains:
*  two min-heaps: one for synchronous messages and one for asynchronous
messages.
*  A pointer to the message stack (called `looper processed`) which indicates
which message was most recently processed by the looper.

Even after being swept into a heap, Messages are still linked in the message
stack and are not unlinked until removal from the freelist, or delivery.

The `heap sweep` operation consists of the following steps:

1.  The looper reads the top of the message stack.
2.  It compares the top of stack to the `looper processed` pointer.
3.  If the pointers are not equal, the old `looper processed` pointer is
    recorded and `looper processed` is set to the top of the stack.
4.  Next, the looper walks all messages between the new and old `looper
    processed` pointers. These are the messages which were inserted after our
    last sweep.
5.  For each newly added message, the looper creates a backlink to the message
    before it. This will allow for O(1) removals from the `message stack`.
6.  Each new message is inserted into the appropriate min-heap based on its type
    (sync or async). Barriers always go into the sync heap.

#### 2. Processing Removals

Before executing any messages, the looper processes the freelist:

1.  It pops all nodes from the freelist.
2.  For each removed node, it unlinks it from the message stack.
3.  If present, the node is removed from the corresponding min-heap.

#### 3. Running Messages

After processing removals, the looper executes the next eligible message:

1.  It peeks at the top of both the sync and async heaps to find the message
    with the earliest scheduled time.
2.  If the message's scheduled time has arrived, it is removed from the heap.
3.  The looper uses a CAS operation to mark the message as removed (to prevent a
    race with a concurrent remover).
4.  If the CAS is successful, the message is unlinked from the message stack,
    then executed.

## Thread Wakeup Coordination

A 64-bit atomic variable coordinates the looper thread's sleep/wake state. This
variable can be interpreted in two ways, indicated by a tag bit:

*   **Timestamp:** The time when the looper thread should wake up, expressed in
    milliseconds shifted with a counter.
*   **Counter:** The number of new messages added since the looper last checked.

When the looper is about to sleep, it atomically sets the wakeup variable to a
timestamp corresponding to the next message's execution time. When a new message
is added, the adding thread checks this variable.

*   If the new message's deadline is earlier than the wakeup timestamp, the
    adding thread wakes the looper.
*   Otherwise, it increments a counter, and the looper will check the new
    messages when it wakes up naturally. The counter ensures that if a new
    message (possibly with a sooner deadline!) is added to the stack when the
    looper thread is about to wait, the CAS to enter waiting mode will fail, and
    the looper will process the new messages.

This mechanism prevents the looper from sleeping through a newly added message
with an earlier deadline, while avoiding unnecessary wakeups for messages
scheduled in the future.

### Sync Barrier Wakeup Logic

A second atomic variable is used to handle wakeups when a sync barrier is
active. This variable stores the timestamp of the earliest active sync barrier.
When a synchronous message is added, the submitting thread checks both the main
wakeup atomic and the sync barrier atomic to determine if the message is blocked
and whether the looper needs to be woken up.

## Quitting the Looper

The looper can be stopped using `quit()` or `quitSafely()`.

*   `quit()`: The looper exits as soon as the current task is finished. All
    pending messages are removed.
*   `quitSafely()`: The looper finishes all messages that are already due and
    then exits. Pending messages scheduled for the future are removed.

A sentinel node is pushed onto the stack to signal the quitting state. A
reference counting mechanism is used to safely manage a native allocation
associated with the `MessageQueue`. The looper thread will not free the native
allocation until all other threads have finished using it.

## References

*   [Min-Heap (Wikipedia)](https://en.wikipedia.org/wiki/Min-heap)
*   [Treiber Stack (Wikipedia)](https://en.wikipedia.org/wiki/Treiber_stack)
*   [MessageQueue (Android Developers)](https://developer.android.com/reference/android/os/MessageQueue)
*   [Looper (Android Developers)](https://developer.android.com/reference/android/os/Looper)
*   [Handler (Android Developers)](https://developer.android.com/reference/android/os/Handler)
