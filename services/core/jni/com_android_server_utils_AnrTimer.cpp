/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <inttypes.h>
#include <pthread.h>
#include <regex.h>
#include <sys/stat.h>
#include <sys/timerfd.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <list>
#include <map>
#include <memory>
#include <semaphore>
#include <set>
#include <string>
#include <vector>

#define LOG_TAG "AnrTimerService"
#define ATRACE_TAG ATRACE_TAG_ACTIVITY_MANAGER
#define ANR_TIMER_TRACK "AnrTimerTrack"

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <android_runtime/AndroidRuntime.h>
#include <core_jni_helpers.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedPrimitiveArray.h>
#include <processgroup/processgroup.h>
#include <utils/Log.h>
#include <utils/Mutex.h>
#include <utils/Timers.h>
#include <utils/Trace.h>

using ::android::base::StringPrintf;


// Native support is unavailable on WIN32 platforms.  This macro preemptively disables it.
#ifdef _WIN32
#define NATIVE_SUPPORT 0
#else
#define NATIVE_SUPPORT 1
#endif

namespace android {

// using namespace android;

// Almost nothing in this module needs to be in the android namespace.
namespace {

// If not on a Posix system, create stub timerfd methods.  These are defined to allow
// compilation.  They are not functional.  Also, they do not leak outside this compilation unit.
#ifdef _WIN32
int timer_create() {
  return -1;
}
int timer_settime(int, int, void const *, void *) {
  return -1;
}
#else
int timer_create() {
  return timerfd_create(CLOCK_MONOTONIC, TFD_CLOEXEC);
}
int timer_settime(int fd, int flags, const struct itimerspec *new_value,
                  struct itimerspec *_Nullable old_value) {
  return timerfd_settime(fd, flags, new_value, old_value);
}
#endif

// A local debug flag that gates a set of log messages for debug only.  This is normally const
// false so the debug statements are not included in the image.  The flag can be set true in a
// unit test image to debug test failures.
const bool DEBUG_TIMER = false;

// Enable error logging.
const bool DEBUG_ERROR = true;

// The current process.  This is cached here on startup.
const pid_t sThisProcess = getpid();

// Token for ANR warning. The value should be same as defined in the AnrTimer class for ANR warning.
const int ANR_WARNING_TOKEN = 0x4157;

// Return true if the process exists and false if we cannot know.
bool processExists(pid_t pid) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "/proc/%d", pid);
    struct stat buff;
    return stat(path, &buff) == 0;
}

// Return the name of the process whose pid is the input.  If the process does not exist, the
// name will "notfound".
std::string getProcessName(pid_t pid) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    FILE* cmdline = fopen(path, "r");
    if (cmdline != nullptr) {
        char name[PATH_MAX];
        char const *retval = fgets(name, sizeof(name), cmdline);
        fclose(cmdline);
        if (retval == nullptr) {
            return std::string("unknown");
        } else {
            return std::string(name);
        }
    } else {
        return std::string("notfound");
    }
}

/**
 * This is the abstract interface to the system clock and timers that run against the system
 * clock. There are two variants: the standard Posix timer that runs on Android and a test
 * variant that gives full control over time advancement to the test code.
 */
class Clock {
public:
    // Create a clock and all necessary infrastructure.
    Clock() {}

    virtual ~Clock() = default;

    // Stop the clock and release system resources, as necessary. Threads in waitForTimer() will
    // be released with the return value of "false".
    virtual void stop() = 0;

    // Set a timer to expire at the given relative time. The offset is in nanoseconds.  Negative
    // times are discarded.  This returns 0 on success and -1 on error. waitForTimer() is used
    // to wait for the timer to expire.
    virtual int setTimer(nsecs_t) = 0;

    // Turn off the timer and mark it "not expired", if it was expired.  Any thread in
    // waitForTimer() will continue to wait until setTimer() is called.
    virtual void clearTimer() = 0;

    // Wait for the timer to expire.  Returns true if the timer expired as expected and false
    // otherwise.  The function returns true immediately if it is called when the timer is
    // already expired.  False means the timer was stopped or an OS error occurred.
    virtual bool waitForTimer() = 0;

    // Get the current time, in nanoseconds, as understood by this instance.
    virtual nsecs_t getCurrentTime() = 0;

    // Set the current time.  Return true if it worked (test mode) and false otherwise.
    virtual bool setCurrentTime(nsecs_t) = 0;

    // True on debug.  Useful for test development and debugging.
    virtual bool isDebug() const = 0;

private:
    Clock(const Clock&) = delete;
};

/**
 * This variant is fully functional using posix timers.  It is based on CLOCK_MONOTONIC.
 */
class ClockPosix : public Clock {
public:
    ClockPosix() {
        timerFd_ = timer_create();
    }

    ~ClockPosix() {
        stop();
    }

    int setTimer(nsecs_t delay) {
        if (!running()) return 0;

        if (delay < 0) {
            errno = EINVAL;
            return -1;
        }

        time_t sec = nanoseconds_to_seconds(delay);
        time_t ns = delay - seconds_to_nanoseconds(sec);
        struct itimerspec setting = {
                .it_interval = {0, 0},
                .it_value = {sec, ns},
        };
        return timer_settime(timerFd_, 0, &setting, nullptr);
    }

    void clearTimer() {
        if (!running()) return;

        const struct itimerspec setting = {
                .it_interval = {0, 0},
                .it_value = {0, 0},
        };
        timer_settime(timerFd_, 0, &setting, nullptr);
    }

    bool waitForTimer() {
        if (!running()) return false;

        uint64_t token = 0;
        return read(timerFd_, &token, sizeof(token)) == sizeof(token);
    }

    void stop() {
        if (running()) {
            ::close(timerFd_);
            timerFd_ = -1;
        }
    }

    nsecs_t getCurrentTime() {
        return systemTime(SYSTEM_TIME_MONOTONIC);
    }

    bool setCurrentTime(nsecs_t) {
        return false;
    }

    bool isDebug() const {
        return false;
    }

private:
    bool running() const {
        return timerFd_ >= 0;
    }

    int timerFd_;
};

/**
 * A clock whose time is manually advanced.  This is used only for testing.
 */
class ClockTest : public Clock {
public:
    ClockTest() : now_(0), alarm_(0), lock_(0), running_(true) {}

    virtual ~ClockTest() {
        stop();
    }

    // Set a timer to expire at the given relative time.
    int setTimer(nsecs_t delay) {
        if (delay <= 0) {
            errno = EINVAL;
            return -1;
        }
        alarm_ = now_ + delay;
        maybeRelease();
        return 0;
    }

    // Clear the timer.
    void clearTimer() {
        alarm_ = 0;
    }

    // Wait for the timer to fire.  Returns true if the timer is running.
    bool waitForTimer() {
        if (running_ && !ready()) {
            lock_.acquire();
        }
        return running_;
    }

    // Stop the timer and release any waiters.
    void stop() {
        running_ = false;
        lock_.release();
    }

    // Get the current time.  This uses the same timebase as the timer.
    nsecs_t getCurrentTime() {
        return now_;
    }

    // Set the current time.  This does nothing unless in the test variant.
    bool setCurrentTime(nsecs_t now) {
        now_ = now;
        maybeRelease();
        return true;
    }

    bool isDebug() const {
        return true;
    }

private:
    // Return true if there is an expired alarm time.
    bool ready() const {
        return (alarm_ > 0 && alarm_ <= now_);
    }

    // Maybe release any waiters.
    void maybeRelease() {
        if (ready()) {
            lock_.release();
        }
    }

    // The current time.
    nsecs_t now_;

    // The current timeout.
    nsecs_t alarm_;

    // A semaphore: it is taken inside waitForTimer() and it is released in setCurrentTime()
    // when the new time is greater than or equal to the alarm.
    std::binary_semaphore lock_;

    // Set false to indicate that the clock is about to exit.
    bool running_;
};

/**
 * Actions that can be taken when a timer reaches a split point.
 * - None: Do nothing (the constructor default)
 * - Trace: Log the event for debugging
 * - Expire: Immediately expire the timer
 * - EarlyNotify: Send early notification to Java layer
 * - AnrWarning: Send ANR warning notification to the Java layer
 */
enum class SplitAction : uint8_t { None, Trace, Expire, EarlyNotify, AnrWarning };

/**
 * Represents a point during timer execution where an action should be taken.
 * Split points are defined as percentages of the total timeout.
 */
struct SplitPoint {
    static constexpr uint32_t NOTOKEN = 0;

    // Action to take at this point
    SplitAction action = SplitAction::None;

    // Percentage of timeout (0-100)
    uint8_t percent = 0;

    // Optional token for later identification
    int32_t token = NOTOKEN;

    /* natural sort order, by percent */
    bool operator<(const SplitPoint& r) const {
        return percent < r.percent;
    }

    /* The point is "active" if the action is not None. */
    bool enabled() const {
        return action != SplitAction::None;
    }
};

/**
 * This class encapsulates the anr timer service.  The service manages a list of individual
 * timers.  A timer is either Running or Expired.  Once started, a timer may be canceled or
 * accepted.  Both actions collect statistics about the timer and then delete it.  An expired
 * timer may also be discarded, which deletes the timer without collecting any statistics.
 *
 * All public methods in this class are thread-safe.
 */
class AnrTimerService {
  private:
    class ProcessStats;
    class Timer;

  public:

    // The class that actually runs the clock.
    class Ticker;

    // A timer is identified by a timer_id_t.  Timer IDs are unique in the moment.
    using timer_id_t = uint32_t;

    // A manifest constant.  No timer is ever created with this ID.
    static const timer_id_t NOTIMER = 0;

    // A notifier is called with a timer ID, the timer's tag, and the client's cookie.  The pid
    // and uid that were originally assigned to the timer are passed as well.  The elapsed time
    // is the time since the timer was scheduled.
    using notifier_t = bool (*)(timer_id_t, int pid, int uid, nsecs_t started, nsecs_t elapsed,
                                void* cookie, jweak object, bool expired, uint32_t token);

    enum Status {
        Invalid,
        Running,
        Expired,
        Canceled
    };

    /**
     * Create a timer service.  The service is initialized with a name used for logging.  The
     * constructor is also given the notifier callback, and two cookies for the callback: the
     * traditional void* and Java object pointer.  The remaining parameters are
     * configuration options.
     */
    AnrTimerService(const char* label, notifier_t notifier, void* cookie, jweak jtimer,
                    std::shared_ptr<Ticker>, bool extend, std::vector<SplitPoint> splits);

    // Delete the service and clean up memory.
    ~AnrTimerService();

    // Start a timer and return the associated timer ID.  It does not matter if the same pid/uid
    // are already in the running list.  Once start() is called, one of cancel(), accept(), or
    // discard() must be called to clean up the internal data structures.
    timer_id_t start(int pid, int uid, nsecs_t timeout);

    // Cancel a timer and remove it from all lists.  This is called when the event being timed
    // has occurred.  If the timer was Running, the function returns true.  The other
    // possibilities are that the timer was Expired or non-existent; in both cases, the function
    // returns false.
    bool cancel(timer_id_t timerId);

    // Accept a timer.  This is called when the upper layers accept that a timer has expired.
    // If the timer was Expired, the function returns true.  Anything else is an error and the
    // function returns false.
    bool accept(timer_id_t timerId);

    // Discard a timer without collecting any statistics.  This is called when the upper layers
    // recognize that a timer expired but decide the expiration is not significant.  If the
    // timer was Expired, the function returns true.  The other possibilities are tha the timer
    // was Running or non-existing; in both cases, the function returns false.
    bool discard(timer_id_t timerId);

    // A timer has expired.
    void expire(timer_id_t);

    // Return the Java object associated with this instance.
    jweak jtimer() const {
        return notifierObject_;
    }

    // Set the time in the current Clock.  This has no effect if the instance is not in test
    // mode.
    bool setCurrentTime(nsecs_t);

    // Return the per-instance statistics.
    std::vector<std::string> getDump() const;

  private:
    // The service cannot be copied.
    AnrTimerService(const AnrTimerService&) = delete;

    // Insert a timer into the running list.  The lock must be held by the caller.
    void insertLocked(const Timer&);

    // Remove a timer from the lists and return it. The lock must be held by the caller.
    Timer removeLocked(timer_id_t timerId);

    // Return a string representation of a status value.
    static const char* statusString(Status);

    // Return the current time.  This comes from the Ticker, which may be using a synthetic
    // clock.
    nsecs_t now() const;

    // The name of this service, for logging.
    const std::string label_;

    // The callback that is invoked when a timer expires.
    const notifier_t notifier_;

    // The two cookies passed to the notifier.
    void* notifierCookie_;
    jweak notifierObject_;

    // True if extensions can be granted to expired timers.
    const bool extend_;

    // The global lock
    mutable Mutex lock_;

    // The list of all timers that are still running.  This is sorted by ID for fast lookup.
    std::set<Timer> running_;

    // The maximum number of active timers.
    size_t maxRunning_;

    // Simple counters
    struct Counters {
        // The number of timers started, canceled, accepted, discarded, and expired.
        size_t started;
        size_t canceled;
        size_t accepted;
        size_t discarded;
        size_t expired;
        size_t extended;

        // The number of times there were zero active timers.
        size_t drained;

        // The number of times a protocol error was seen.
        size_t error;
    };

    Counters counters_;

    // The clock used by this AnrTimerService.
    std::shared_ptr<Ticker> ticker_;

    // Default split points for any timer in this service
    std::vector<SplitPoint> defaultSplits_;
};

class AnrTimerService::ProcessStats {
  public:
    nsecs_t cpu_time;
    nsecs_t cpu_delay;

    ProcessStats() :
            cpu_time(0),
            cpu_delay(0) {
    }

    // Collect all statistics for a process.  Return true if the fill succeeded and false if it
    // did not.  If there is any problem, the statistics are zeroed.
    bool fill(int pid) {
        cpu_time = 0;
        cpu_delay = 0;

        char path[PATH_MAX];
        snprintf(path, sizeof(path), "/proc/%u/schedstat", pid);
        ::android::base::unique_fd fd(open(path, O_RDONLY | O_CLOEXEC));
        if (!fd.ok()) {
            return false;
        }
        char buffer[128];
        ssize_t len = read(fd, buffer, sizeof(buffer));
        if (len <= 0) {
            return false;
        }
        if (len >= sizeof(buffer)) {
            ALOGE("proc file too big: %s", path);
            return false;
        }
        buffer[len] = 0;
        unsigned long t1;
        unsigned long t2;
        if (sscanf(buffer, "%lu %lu", &t1, &t2) != 2) {
            return false;
        }
        cpu_time = t1;
        cpu_delay = t2;
        return true;
    }
};

class AnrTimerService::Timer {
  public:
    // A unique ID assigned when the Timer is created.
    const timer_id_t id;

    // The creation parameters.  The timeout is the original, relative timeout.
    const int pid;
    const int uid;
    // The time at which the timer was started.
    const nsecs_t started;
    // The relative time from started at which the timer expires.
    const nsecs_t timeout;
    // True if the timer may be extended.
    const bool extend;
    // The splits and actions to take before the timer expire
    const std::vector<SplitPoint>* splits;
    // index of the next split to fire
    uint8_t nextSplit;

    // The state of this timer.
    Status status;

    // The scheduled timeout.  This is an absolute time.  It may be extended.
    nsecs_t scheduled;

    // The action to be taken at the scheduled timeout.
    SplitAction action;

    // The token associated with the scheduled timeout.
    int32_t token;

    // True if this timer has been extended.
    bool extended;

    // Bookkeeping for extensions.  The initial state of the process.  This is collected only if
    // the timer is extensible.
    ProcessStats initial;

    // The default constructor is used to create timers that are Invalid, representing the "not
    // found" condition when a collection is searched.
    Timer() : Timer(NOTIMER) { }

    // This constructor creates a timer with the specified id and everything else set to
    // "empty".  This can be used as the argument to find().
    Timer(timer_id_t id)
          : id(id),
            pid(0),
            uid(0),
            started(0),
            timeout(0),
            extend(false),
            splits(nullptr),
            nextSplit(0),
            status(Invalid),
            scheduled(0),
            action(SplitAction::None),
            token(0),
            extended(false) {}

    // Create a new timer.  This starts the timer.
    Timer(int pid, int uid, nsecs_t timeout, bool extend, nsecs_t now,
          const std::vector<SplitPoint>* splits)
          : id(nextId()),
            pid(pid),
            uid(uid),
            started(now),
            timeout(timeout),
            extend(extend),
            splits(splits),
            nextSplit(0),
            status(Running),
            scheduled(0),
            action(SplitAction::None),
            token(0),
            extended(false) {
        if (extend && pid != 0) {
            initial.fill(pid);
        }
        schedule();
    }

    // Schedule a timeout and record that action to be taken at the timeout.
    void schedule() {
        if (nextSplit >= splits->size()) {
            scheduled = started + timeout;
            action = SplitAction::Expire;
            token = 0;
        } else {
            scheduled = started + (timeout * splits->at(nextSplit).percent) / 100;
            action = splits->at(nextSplit).action;
            token = splits->at(nextSplit).token;
            nextSplit++;
        }
    }

    // Start a timer.  This interface exists to generate log messages, if enabled.
    void start() {
        event("start", /* verbose= */ true);
    }

    // Cancel a timer.
    void cancel() {
        ALOGW_IF(DEBUG_ERROR && status != Running, "error: canceling %s", toString().c_str());
        status = Canceled;
        event("cancel");
    }

    // Expire a timer.  Return the action to be taken and a token.  (The token is only relevant
    // to EarlyNotify actions).  If the timer is eligible for extensions or if this was a Trace
    // event, the returned action will be None.  In all cases the scheduled timeout is updated.
    // When the timer has been exhausted, its status is Expired.
    SplitPoint expire() {
        // Save the current state.  It will be overwritten if schedule() is called.
        SplitPoint current = {action, 0, token};

        // It is guaranteed that there is a terminal Expire action.  Also, regardless of the
        // length of the split vector, an expire always exhausts the timer.
        switch (action) {
            case SplitAction::Trace:
                event("split");
                schedule();
                return current;

            case SplitAction::EarlyNotify:
                event("early");
                schedule();
                return current;

            case SplitAction::AnrWarning:
                event("anrWarning");
                schedule();
                return current;

            case SplitAction::None:
                ALOGE("Illegal SplitAction::None action in timer");
                status = Expired;
                return {SplitAction::Expire, 0, token};

            case SplitAction::Expire:
                event("expire");
                break;
        }

        nsecs_t extension = 0;
        if (extend && !extended) {
            // Only one extension is permitted.
            extended = true;
            ProcessStats current;
            current.fill(pid);
            extension = current.cpu_delay - initial.cpu_delay;
            if (extension < 0) extension = 0;
            if (extension > timeout) extension = timeout;
        }
        if (extension == 0) {
            status = Expired;
            event("expire");
        } else {
            scheduled += extension;
            event("extend");
        }
        return {status == Expired ? SplitAction::Expire : SplitAction::None, 0, token};
    }

    // Accept a timeout.  This does nothing other than log the state machine change.
    void accept() {
        event("accept");
    }

    // Discard a timeout.
    void discard() {
        status = Canceled;
        event("discard");
    }

    // Return true if this timer corresponds to a running process.
    bool alive() const {
        return processExists(pid);
    }

    // Timers are sorted by id, which is unique.  This provides fast lookups.
    bool operator<(Timer const &r) const {
        return id < r.id;
    }

    bool operator==(timer_id_t r) const {
        return id == r;
    }

    std::string toString() const {
        return StringPrintf("id=%d pid=%d uid=%d status=%s",
                            id, pid, uid, statusString(status));
    }

    std::string toString(nsecs_t now) const {
        uint32_t ms = nanoseconds_to_milliseconds(now - scheduled);
        return StringPrintf("id=%d pid=%d uid=%d status=%s scheduled=%ums",
                            id, pid, uid, statusString(status), -ms);
    }

  private:
    // A mask to ensure uint32_t timer IDs do not wrap to negative values when converted to
    // int32_t values.
    static constexpr uint32_t kIdMask = 0x7f'ff'ff'ff;

    /**
     * Collect the name of the process.
     */
    std::string getName() const {
        return getProcessName(pid);
    }

    // Get the next free ID.  NOTIMER is never returned.
    static timer_id_t nextId() {
        timer_id_t id = (idGen.fetch_add(1) & kIdMask);
        while (id == NOTIMER) {
            id = (idGen.fetch_add(1) & kIdMask);
        }
        return id;
    }

    // Log an event, non-verbose.
    void event(const char* tag) {
        event(tag, false);
    }

    // Log an event, guarded by the debug flag.
    void event(const char* tag, bool verbose) {
        if (verbose) {
            ALOGI_IF(DEBUG_TIMER, "event %s %s name=%s",
                     tag, toString().c_str(), getName().c_str());
        } else {
            ALOGI_IF(DEBUG_TIMER, "event %s id=%u", tag, id);
        }
    }

    static void traceEvent(const char* msg) {
        ATRACE_INSTANT_FOR_TRACK(ANR_TIMER_TRACK, msg);
    }

    // IDs start at 1.  A zero ID is invalid.
    static std::atomic<timer_id_t> idGen;
};

// IDs start at 1.
std::atomic<AnrTimerService::timer_id_t> AnrTimerService::Timer::idGen(1);

/**
 * Manage a set of timers and notify clients when there is a timeout.
 */
class AnrTimerService::Ticker {
  private:
    struct Entry {
        const nsecs_t scheduled;
        const timer_id_t id;
        AnrTimerService* service;

        Entry(nsecs_t scheduled, timer_id_t id, AnrTimerService* service) :
                scheduled(scheduled), id(id), service(service) {};

        bool operator<(const Entry& r) const {
            return scheduled == r.scheduled ? id < r.id : scheduled < r.scheduled;
        }
    };

  public:

    // Construct the ticker.  This creates the timerfd file descriptor and starts the monitor
    // thread.  The monitor thread is given a unique name.
    Ticker(std::unique_ptr<Clock> clock) : clock_(std::move(clock)), id_(idGen_.fetch_add(1)) {
        if (pthread_create(&watcher_, 0, run, this) != 0) {
            ALOGE("failed to start thread: %s", strerror(errno));
            watcher_ = 0;
            return;
        }

        // 16 is a magic number from the kernel.  Thread names may not be longer than this many
        // bytes, including the terminating null.  The snprintf() method will truncate properly.
        char name[16];
        snprintf(name, sizeof(name), "AnrTimerService");
        pthread_setname_np(watcher_, name);

        ready_ = true;
    }

    ~Ticker() {
        clock_->stop();
        pthread_join(watcher_, nullptr);
        watcher_ = 0;
    }

    // Return the current time, based on this Ticker's clock.
    nsecs_t now() const {
        return clock_->getCurrentTime();
    }

    // Insert a timer.  Unless canceled, the timer will expire at the scheduled time.  If it
    // expires, the service will be notified with the id.
    void insert(nsecs_t scheduled, timer_id_t id, AnrTimerService *service) {
        Entry e(scheduled, id, service);
        AutoMutex _l(lock_);
        timer_id_t front = headTimerId();
        running_.insert(e);
        if (front != headTimerId()) restartLocked();
        maxRunning_ = std::max(maxRunning_, running_.size());
    }

    // Remove a timer.  The timer is identified by its scheduled timeout and id.  Technically,
    // the id is sufficient (because timer IDs are unique) but using the timeout is more
    // efficient.
    void remove(nsecs_t scheduled, timer_id_t id) {
        Entry key(scheduled, id, 0);
        AutoMutex _l(lock_);
        auto found = running_.find(key);
        if (found != running_.end()) running_.erase(found);
        if (running_.empty()) drained_++;
    }

    // Remove every timer associated with the service.
    void remove(const AnrTimerService* service) {
        AutoMutex _l(lock_);
        for (auto i = running_.begin(); i != running_.end(); ) {
            if (i->service == service) {
                i = running_.erase(i);
            } else {
                i++;
            }
        }
    }

    // The unique ID of this particular ticker. Used for debug and logging.
    size_t id() const {
        return id_;
    }

    // Return the number of timers still running.
    size_t running() const {
        AutoMutex _l(lock_);
        return running_.size();
    }

    // Return the high-water mark of timers running.
    size_t maxRunning() const {
        AutoMutex _l(lock_);
        return maxRunning_;
    }

    // Set the current time of this ticker's clock.  Returns true on success (this ticker is
    // using a test clock) and false otherwise.
    bool setCurrentTime(nsecs_t now) {
        return clock_->setCurrentTime(now);
    }

  private:

    // Return the head of the running list.  The lock must be held by the caller.
    timer_id_t headTimerId() {
        return running_.empty() ? NOTIMER : running_.cbegin()->id;
    }

    // A simple wrapper that meets the requirements of pthread_create.
    static void* run(void* arg) {
        reinterpret_cast<Ticker*>(arg)->monitor();
        return 0;
    }

    // Loop (almost) forever.  Whenever the timer expires, expire as many entries as
    // possible.  The loop terminates when the read fails; this generally means that the
    // enclosing Ticker is being deleted and the thread has been canceled.  The thread must
    // exit.
    void monitor() {
        while (clock_->waitForTimer()) {
            // Move expired timers into the local ready list.  This is done inside
            // the lock.  Then, outside the lock, expire them.
            nsecs_t current = now();
            std::vector<Entry> ready;
            {
                AutoMutex _l(lock_);
                while (!running_.empty()) {
                    Entry timer = *(running_.begin());
                    if (timer.scheduled <= current) {
                        ready.push_back(timer);
                        running_.erase(running_.cbegin());
                    } else {
                        break;
                    }
                }
                restartLocked();
            }

            // Call the notifiers outside the lock.  Calling the notifiers with the lock held
            // can lead to deadlock, if the Java-side handler also takes a lock.  Note that the
            // timerfd is already running.
            for (auto i = ready.begin(); i != ready.end(); i++) {
                Entry e = *i;
                e.service->expire(e.id);
            }
        }
        // If the read fails, exit immediately without touching any further memory. The Ticker
        // is being closed.
    }

    // Restart the ticker.  The caller must be holding the lock.  This method updates the
    // timerFd_ to expire at the time of the first Entry in the running list.  This method does
    // not check to see if the currently programmed expiration time is different from the
    // scheduled expiration time of the first entry.
    void restartLocked() {
        if (!running_.empty()) {
            const Entry x = *(running_.cbegin());
            nsecs_t delay = x.scheduled - now();
            // Force a minimum timeout of 10ns.
            if (delay < 10) delay = 10;
            clock_->setTimer(delay);
            restarted_++;
        } else {
            clock_->clearTimer();
            drained_++;
        }
    }

    // The usual lock.
    mutable Mutex lock_;

    // True if the object was initialized properly.  Android does not support throwing C++
    // exceptions, so clients should check this flag after constructing the object.  This is
    // effectively const after the instance has been created.
    bool ready_ = false;

    // The clock that is the basis for this ticker.
    std::unique_ptr<Clock> clock_;

    // The thread that monitors the timer.
    pthread_t watcher_ = 0;

    // The number of times the timer was restarted.
    size_t restarted_ = 0;

    // The number of times the timer list was exhausted.
    size_t drained_ = 0;

    // The highwater mark of timers that are running.
    size_t maxRunning_ = 0;

    // The list of timers that are scheduled.  This set is sorted by timeout and then by timer
    // ID.  A set is sufficient (as opposed to a multiset) because timer IDs are unique.
    std::set<Entry> running_;

    // A unique ID assigned to this instance.
    const size_t id_;

    // The ID generator.
    static std::atomic<size_t> idGen_;
};

std::atomic<size_t> AnrTimerService::Ticker::idGen_;

AnrTimerService::AnrTimerService(const char* label, notifier_t notifier, void* cookie, jweak jtimer,
                                 std::shared_ptr<Ticker> ticker, bool extend,
                                 std::vector<SplitPoint> splits)
      : label_(label),
        notifier_(notifier),
        notifierCookie_(cookie),
        notifierObject_(jtimer),
        extend_(extend),
        ticker_(ticker),
        defaultSplits_(std::move(splits)) {
    // Zero the statistics
    maxRunning_ = 0;
    memset(&counters_, 0, sizeof(counters_));

    ALOGI_IF(DEBUG_TIMER, "initialized %s", label);
}

AnrTimerService::~AnrTimerService() {
    AutoMutex _l(lock_);
    ticker_->remove(this);
}

const char* AnrTimerService::statusString(Status s) {
    switch (s) {
        case Invalid: return "invalid";
        case Running: return "running";
        case Expired: return "expired";
        case Canceled: return "canceled";
    }
    return "unknown";
}

AnrTimerService::timer_id_t AnrTimerService::start(int pid, int uid, nsecs_t timeout) {
    AutoMutex _l(lock_);
    Timer t(pid, uid, timeout, extend_, now(), &defaultSplits_);
    insertLocked(t);
    t.start();
    counters_.started++;
    return t.id;
}

bool AnrTimerService::cancel(timer_id_t timerId) {
    if (timerId == NOTIMER) return false;
    AutoMutex _l(lock_);
    Timer timer = removeLocked(timerId);

    bool result = timer.status == Running;
    if (timer.status != Invalid) {
        timer.cancel();
    } else {
        counters_.error++;
    }
    counters_.canceled++;
    return result;
}

bool AnrTimerService::accept(timer_id_t timerId) {
    if (timerId == NOTIMER) return false;
    AutoMutex _l(lock_);
    Timer timer = removeLocked(timerId);

    bool result = false;
    if (timer.status == Expired) {
        timer.accept();
        result = true;
    } else {
        counters_.error++;
    }
    counters_.accepted++;
    return result;
}

bool AnrTimerService::discard(timer_id_t timerId) {
    if (timerId == NOTIMER) return false;
    AutoMutex _l(lock_);
    Timer timer = removeLocked(timerId);

    bool result = timer.status == Expired;
    if (timer.status == Expired) {
        timer.discard();
    } else {
        counters_.error++;
    }
    counters_.discarded++;
    return result;
}

// Hold the lock in order to manage the running list.
void AnrTimerService::expire(timer_id_t timerId) {
    // Save the timer attributes for the notification
    int pid = 0;
    int uid = 0;
    nsecs_t started = 0;
    nsecs_t elapsed = 0;
    SplitPoint meta;
    bool expired = false;
    {
        AutoMutex _l(lock_);
        Timer t = removeLocked(timerId);
        if (t.status != Invalid) {
            meta = t.expire();
            expired = (t.status == Expired);
        }
        if (t.status == Invalid) {
            ALOGW_IF(DEBUG_ERROR, "error: expired invalid timer %u", timerId);
            return;
        } else {
            // The timer is either Running (because it was extended) or expired (and is awaiting an
            // accept or discard).
            insertLocked(t);
        }
        pid = t.pid;
        uid = t.uid;
        started = t.started;
        elapsed = now() - t.started;
    }

    if (expired) {
        counters_.expired++;
    } else {
        counters_.extended++;
    }

    // Deliver the notification outside of the lock.
    if (meta.action == SplitAction::Expire || meta.action == SplitAction::EarlyNotify ||
        meta.action == SplitAction::AnrWarning) {
        if (!notifier_(timerId, pid, uid, started, elapsed, notifierCookie_, notifierObject_,
                       expired, meta.token)) {
            // Notification failed, which means the listener will never call accept() or
            // discard().  Do not reinsert the timer.
            discard(timerId);
        }
    }
}

void AnrTimerService::insertLocked(const Timer& t) {
    running_.insert(t);
    if (t.status == Running) {
        // Only forward running timers to the ticker.  Expired timers are handled separately.
        ticker_->insert(t.scheduled, t.id, this);
    }
    maxRunning_ = std::max(maxRunning_, running_.size());
}

AnrTimerService::Timer AnrTimerService::removeLocked(timer_id_t timerId) {
    Timer key(timerId);
    auto found = running_.find(key);
    if (found != running_.end()) {
        Timer result = *found;
        running_.erase(found);
        ticker_->remove(result.scheduled, result.id);
        if (running_.size() == 0) counters_.drained++;
        return result;
    }
    return Timer();
}

nsecs_t AnrTimerService::now() const {
    return ticker_->now();
}

bool AnrTimerService::setCurrentTime(nsecs_t now) {
    return ticker_->setCurrentTime(now);
}

std::vector<std::string> AnrTimerService::getDump() const {
    std::vector<std::string> r;
    AutoMutex _l(lock_);
    r.push_back(StringPrintf("started:%zu canceled:%zu accepted:%zu discarded:%zu expired:%zu",
                             counters_.started,
                             counters_.canceled,
                             counters_.accepted,
                             counters_.discarded,
                             counters_.expired));
    r.push_back(StringPrintf("extended:%zu drained:%zu error:%zu running:%zu maxRunning:%zu",
                             counters_.extended,
                             counters_.drained,
                             counters_.error,
                             running_.size(),
                             maxRunning_));
    r.push_back(StringPrintf("ticker:%zu ticking:%zu maxTicking:%zu",
                             ticker_->id(),
                             ticker_->running(),
                             ticker_->maxRunning()));
    return r;
}

/**
 * True if the native methods are supported in this process.  Native methods are supported only
 * if the initialization succeeds.
 */
bool nativeSupportEnabled = false;

/**
 * Singleton/globals for the anr timer.  Among other things, this includes a Ticker* and a use
 * count.  The JNI layer creates a single Ticker for all operational AnrTimers.  The Ticker is
 * created when the first AnrTimer is created; this means that the Ticker is only created if
 * native anr timers are used.
 */
static Mutex gAnrLock;
struct AnrArgs {
    jclass clazz = NULL;
    jmethodID func = NULL;
    jmethodID funcEarly = NULL;
    JavaVM* vm = NULL;
    std::shared_ptr<AnrTimerService::Ticker> ticker = nullptr;
};
static AnrArgs gAnrArgs;

// The cookie is the address of the AnrArgs object to which the notification should be sent.
static bool anrNotify(AnrTimerService::timer_id_t timerId, int pid, int uid, nsecs_t started,
                      nsecs_t elapsed, void* cookie, jweak jtimer, bool expired, uint32_t token) {
    AutoMutex _l(gAnrLock);
    AnrArgs* target = reinterpret_cast<AnrArgs* >(cookie);
    JNIEnv *env;
    if (target->vm->AttachCurrentThread(&env, 0) != JNI_OK) {
        ALOGE("failed to attach thread to JavaVM");
        return false;
    }
    jboolean r = false;
    jobject timer = env->NewGlobalRef(jtimer);
    if (timer != nullptr) {
        if (expired) {
            r = env->CallBooleanMethod(timer, target->func, timerId, pid, uid, ns2ms(started),
                                       ns2ms(elapsed));
        } else {
            env->CallVoidMethod(timer, target->funcEarly, timerId, pid, uid, ns2ms(elapsed), token);
            r = true;
        }

        env->DeleteGlobalRef(timer);
    }
    target->vm->DetachCurrentThread();
    return r;
}

jboolean anrTimerSupported(JNIEnv* env, jclass) {
    return nativeSupportEnabled;
}

jlong anrTimerCreate(JNIEnv* env, jobject jtimer, jstring jname, jboolean extend, jintArray jperc,
                     jintArray jtok, jboolean testMode) {
    if (!nativeSupportEnabled) return 0;
    AutoMutex _l(gAnrLock);
    // Create a Posix ticker lazily.  This is a singleton that is shared by all non-test
    // timers.  However, every test timer gets its own ticker.
    std::shared_ptr<AnrTimerService::Ticker> ticker;
    if (testMode) {
        ticker.reset(new AnrTimerService::Ticker(std::unique_ptr<Clock>(new ClockTest())));
    } else {
        if (gAnrArgs.ticker.get() == nullptr) {
            gAnrArgs.ticker.reset(
                    new AnrTimerService::Ticker(std::unique_ptr<Clock>(new ClockPosix())));
        }
        ticker = gAnrArgs.ticker;
    }

    std::vector<SplitPoint> splits;
    if (jperc && jtok) {
        const ScopedIntArrayRO percents(env, jperc);
        const ScopedIntArrayRO tokens(env, jtok);

        // There is a size mismatch, return an error
        if (percents.size() != tokens.size()) return 0;

        const jsize n = percents.size();
        splits.reserve(n);

        for (jsize i = 0; i < n; ++i) {
            // Create a SplitPoint for each token and push it to the vector.
            if (tokens[i] == ANR_WARNING_TOKEN) {
                splits.emplace_back(SplitAction::AnrWarning, percents[i], tokens[i]);
            } else {
                // tokens[i] == TOKEN_LONG_METHOD_TRACING
                splits.emplace_back(SplitAction::EarlyNotify, percents[i], tokens[i]);
            }
        }
        std::sort(splits.begin(), splits.end());
    }

    ScopedUtfChars name(env, jname);
    jobject timer = env->NewWeakGlobalRef(jtimer);
    AnrTimerService* service = new AnrTimerService(name.c_str(), anrNotify, &gAnrArgs, timer,
                                                   ticker, extend, std::move(splits));
    return reinterpret_cast<jlong>(service);
}

AnrTimerService *toService(jlong pointer) {
    return reinterpret_cast<AnrTimerService*>(pointer);
}

jint anrTimerClose(JNIEnv* env, jclass, jlong ptr) {
    if (!nativeSupportEnabled) return -1;
    if (ptr == 0) return -1;
    AutoMutex _l(gAnrLock);
    AnrTimerService *s = toService(ptr);
    env->DeleteWeakGlobalRef(s->jtimer());
    delete s;
    return 0;
}

jint anrTimerStart(JNIEnv* env, jclass, jlong ptr, jint pid, jint uid, jlong timeout) {
    if (!nativeSupportEnabled) return 0;
    // On the Java side, timeouts are expressed in milliseconds and must be converted to
    // nanoseconds before being passed to the library code.
    return toService(ptr)->start(pid, uid, milliseconds_to_nanoseconds(timeout));
}

jboolean anrTimerCancel(JNIEnv* env, jclass, jlong ptr, jint timerId) {
    if (!nativeSupportEnabled) return false;
    return toService(ptr)->cancel(timerId);
}

jboolean anrTimerAccept(JNIEnv* env, jclass, jlong ptr, jint timerId) {
    if (!nativeSupportEnabled) return false;
    return toService(ptr)->accept(timerId);
}

jboolean anrTimerDiscard(JNIEnv* env, jclass, jlong ptr, jint timerId) {
    if (!nativeSupportEnabled) return false;
    return toService(ptr)->discard(timerId);
}

jobjectArray anrTimerDump(JNIEnv *env, jclass, jlong ptr) {
    if (!nativeSupportEnabled) return nullptr;
    std::vector<std::string> stats = toService(ptr)->getDump();
    jclass sclass = env->FindClass("java/lang/String");
    jobjectArray r = env->NewObjectArray(stats.size(), sclass, nullptr);
    for (size_t i = 0; i < stats.size(); i++) {
        env->SetObjectArrayElement(r, i, env->NewStringUTF(stats[i].c_str()));
    }
    return r;
}

jboolean anrTimerSetTime(JNIEnv* env, jclass, jlong ptr, jlong now) {
    if (!nativeSupportEnabled) return false;
    // On the Java side, timeouts are expressed in milliseconds and must be converted to
    // nanoseconds before being passed to the library code.
    return toService(ptr)->setCurrentTime(milliseconds_to_nanoseconds(now));
}

static const JNINativeMethod methods[] = {
        {"nativeAnrTimerSupported", "()Z", (void*)anrTimerSupported},
        {"nativeAnrTimerCreate", "(Ljava/lang/String;Z[I[IZ)J", (void*)anrTimerCreate},
        {"nativeAnrTimerClose", "(J)I", (void*)anrTimerClose},
        {"nativeAnrTimerStart", "(JIIJ)I", (void*)anrTimerStart},
        {"nativeAnrTimerCancel", "(JI)Z", (void*)anrTimerCancel},
        {"nativeAnrTimerAccept", "(JI)Z", (void*)anrTimerAccept},
        {"nativeAnrTimerDiscard", "(JI)Z", (void*)anrTimerDiscard},
        {"nativeAnrTimerDump", "(J)[Ljava/lang/String;", (void*)anrTimerDump},
        {"nativeAnrTimerSetTime", "(JJ)Z", (void*)anrTimerSetTime},
};

} // anonymous namespace

int register_android_server_utils_AnrTimer(JNIEnv* env)
{
    static const char* className = "com/android/server/utils/AnrTimer";
    jniRegisterNativeMethods(env, className, methods, NELEM(methods));

    nativeSupportEnabled = NATIVE_SUPPORT;

    // Do not perform any further initialization if native support is not enabled.
    if (!nativeSupportEnabled) return 0;

    jclass service = FindClassOrDie(env, className);
    gAnrArgs.clazz = MakeGlobalRefOrDie(env, service);
    gAnrArgs.func = env->GetMethodID(gAnrArgs.clazz, "expire", "(IIIJJ)Z");
    gAnrArgs.funcEarly = env->GetMethodID(gAnrArgs.clazz, "notifyEarly", "(IIIJI)V");

    env->GetJavaVM(&gAnrArgs.vm);

    return 0;
}

} // namespace android
