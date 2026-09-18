/**
 * Copyright 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define ATRACE_TAG ATRACE_TAG_ACTIVITY_MANAGER
#define LOG_TAG "MemoryLimiter"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <core_jni_helpers.h>
#include <cutils/misc.h>
#include <errno.h>
#include <inttypes.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <private/android_filesystem_config.h>
#include <processgroup/processgroup.h>
#include <pthread.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utils/Log.h>

#include <filesystem>
#include <map>
#include <mutex>
#include <optional>
#include <queue>
#include <string>

namespace android {

namespace {

// Enable debug messages.  Do not commit a true value.
const bool DEBUG = false;

// The poll interval for process scrubbing.  It is elevated to this point in the file because we
// like to see constants near the top.  Units are milliseconds.  The value is 5 minutes.
const int PID_POLL_PERIOD_MS = 5 * 60 * 1000;

// The poll interval for checking red-zone processes.  Units are milliseconds.  The value is 30
// seconds.
const int RED_POLL_PERIOD_MS = 30 * 1000;

// The poll interval in test mode.  This is meant to be fast so that tests complete quickly.
// The value is 1s.
const int TEST_POLL_PERIOD_MS = 1000;

/**
 * The different limits that this process monitors.
 */
enum class MonitoredLimit {
    // LINT.IfChange(limitTypes)
    kUnknown = 0,
    kMemoryHigh = 1,
    kSwapMax = 2,
    kAnonSwap = 3,
    // LINT.ThenChange(/services/core/java/com/android/server/am/MemoryLimiter.java:limitTypes)
};

/**
 * Return a string version of MonitoredLimit, for debug messages.  The function is often needed
 * for debug messages but generates a compiler error when the debug messages are removed.
 */
[[maybe_unused]]
char const* limitName(MonitoredLimit type) {
    switch (type) {
        case MonitoredLimit::kUnknown:
            return "unknown";
        case MonitoredLimit::kMemoryHigh:
            return "memHigh";
        case MonitoredLimit::kSwapMax:
            return "swapMax";
        case MonitoredLimit::kAnonSwap:
            return "anonSwap";
    }
    return "invalid";
}

/**
 * Process memory falls into three bands with respect to AnonSwap.  In the Cold band, process
 * memory use is low and rises can be monitored using cgroup events.  In the Okay band, process
 * memory is below the configure limit but is too high to be monitored with cgroup events (the
 * events have already fired).  In the Hot band, process memory is above the configured limit.
 */
enum class AnonSwapState {
    kCold,
    kOkay,
    kHot,
    kTriggered,
};

/**
 * Two special limit values.  These are not an enumeration because they are just special long
 * values.  However, order is important: the code treats all values that are less than
 * LIMIT_IS_IGNORED as LIMIT_IS_IGNORED.
 */
// LINT.IfChange(limitSpecials)
// A special value that means "configure the limit so that no limit is applied".  In practical
// terms, this means the limit is configured as the string "max".
static constexpr int64_t LIMIT_IS_DISABLED = -1;
// A special value that means skip configuring the limit.
static constexpr int64_t LIMIT_IS_IGNORED = -2;
// LINT.ThenChange(/services/core/java/com/android/server/am/MemoryLimiter.java:limitSpecials)

static_assert(LIMIT_IS_IGNORED < LIMIT_IS_DISABLED, "LIMIT_IS_IGNORED must be the lowest value");
static_assert(LIMIT_IS_IGNORED < 0 && LIMIT_IS_DISABLED < 0, "Limit specials must be negative");

// The margin for anonymous memory.  The cgroup memory.high limit is set to memHigh plus the
// margin once swap is full.  This lets a process run even when anon is at the memHigh limit.
const int64_t mMemHighMargin = 100 * (1024 * 1024); // 100MB

// Hysteresis for memory.high.  If a process is in the red zone (both memory.high and
// memory.swap.high have fired events), cgroup events are disabled and the process is polled for
// limit violations.  However, if the process memory drops <hysteresis> below the memory.high
// limit, polling stops and cgroup events are re-enabled.  The value is 10MB.  There is nothing
// magical about this value, except that 10MB is small enough to be useful for testing.
const int64_t mMemHighHysteresis = 10 * (1024 * 1024); // 10MB

// A convenience type declaration.
using wdmap_t = std::unordered_map<int, pid_t>;

// Enable the short form of StringPrintf.
using ::android::base::StringPrintf;

/**
 * A wrapper that throws a message constructed from a printf string.
 */
void throwRuntime(JNIEnv* env, char const* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char msg[1024];
    vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    jniThrowRuntimeException(env, msg);
}

/**
 * The struct for memory.events.  Member names match the strings defined by the kernel.
 */
struct MemoryEvents {
    int64_t low = 0;
    int64_t high = 0;
    int64_t max = 0;
    int64_t oom = 0;
    int64_t oom_kill = 0;
    int64_t oom_group_kill = 0;

    MemoryEvents() {}

    MemoryEvents(std::string const& data) {
        scan(data);
    }

    MemoryEvents(std::optional<std::string> const& data) {
        if (data) scan(*data);
    }

    // Populate the object from a string that is the contents of the data.  Return true on
    // success.
    bool scan(std::string const& data) {
        return sscanf(data.c_str(),
                      "low %" SCNd64 " high %" SCNd64 " max %" SCNd64 " oom %" SCNd64
                      " oom_kill %" SCNd64 " oom_group_kill %" SCNd64,
                      &low, &high, &max, &oom, &oom_kill, &oom_group_kill) == 6;
    }
};

/**
 * The struct for memory.stat.  Member names match the strings defined by the kernel.  The file
 * contains 56 entries but this feature only needs the first few.
 */
struct MemoryStat {
    int64_t anon = 0;
    int64_t shmem = 0;

    MemoryStat() {}

    MemoryStat(std::string const& data) {
        scan(data);
    }

    MemoryStat(std::optional<std::string> const& data) {
        if (data) scan(*data);
    }

    // Populate the object from a string that is the contents of the file.  Return true on
    // success.
    bool scan(std::string const& data) {
        if (sscanf(data.c_str(), " anon %" SCNd64, &anon) != 1) return false;
        char const* s = strstr(data.c_str(), "\nshmem ");
        if (s == nullptr) return false;
        if (sscanf(s, " shmem %" SCNd64, &shmem) != 1) return false;
        return true;
    }

    // A convenience function to extract the metric for total anonymous memory from a single
    // MemoryStat instance.
    int64_t anonMetric() const {
        return anon + shmem;
    }
};

/**
 * Statistics for the limiter.  All counts are int64_t to be compatible with Java long.
 */
struct Statistics {
    // The number of times start() was called.
    std::atomic<uint64_t> mStarted;

    // The number of times watch() was called and succeeded.
    std::atomic<uint64_t> mWatched;

    // The number of times watch() was called and failed.
    std::atomic<uint64_t> mWatchFailed;

    // The number of events that were generated.
    std::atomic<uint64_t> mEvents;

    // The number of false events, which are events triggered for an unsought statistic.
    std::atomic<uint64_t> mFalseEvents;

    // The current number of processes being watched.
    std::atomic<uint64_t> mProcesses;

    // The high watch mark for processes.  A process record exists only once watch() succeeds.
    std::atomic<uint64_t> mProcessesHwm;

    // The number of times a poll started knowing that there were red processes.
    std::atomic<uint64_t> mRedPoll;

    // The number of times a red Process was checked.
    std::atomic<uint64_t> mRedProcess;

    std::string toString() const {
        const char* fmt = "started=%" PRIu64 " watched=%" PRIu64 " watch-failed=%" PRIu64
                          " events=%" PRIu64 " false-events=%" PRIu64 "\n"
                          "processes=%" PRIu64 " process-hwm=%" PRIu64 " red-poll=%" PRIu64
                          " red-process=%" PRIu64 "\n";
        return StringPrintf(fmt, mStarted.load(), mWatched.load(), mWatchFailed.load(),
                            mEvents.load(), mFalseEvents.load(), mProcesses.load(),
                            mProcessesHwm.load(), mRedPoll.load(), mRedProcess.load());
    }
};

/**
 * A monitored process.
 */
class Process {
    // The cgroup paths of interest to a Process.
    enum class CgroupFile {
        kUnknown,
        kMemoryStat,  // The source of the current value for anon memory.
        kMemoryEvent, // The event count for memory.high violations.
        kMemoryHigh,  // The limit for memory.high.
        kSwapCurrent, // The current value for swap.
        kSwapMax,     // The absolute maximum for swap.
    };

    // A class that encapsulates the data for a single limit type.
    struct Watcher {
        Watcher(MonitoredLimit type, int64_t margin) : mType(type), mMargin(margin) {}

        // The limit type
        MonitoredLimit mType;

        // The limit margin.
        int64_t mMargin;

        // The watch descriptor.  Note that this is NOT owned by this class.
        int mWd = UNSET;

        // The baseline event count.
        int64_t mBaseline = 0;

        // True if an event has ever been see on this limit.
        bool mTriggered = false;
    };

public:
    Process(pid_t pid, uid_t uid) : mPid(pid), mUid(uid) {
        // Note that CgroupGetAttributePathForProcess() returns a success code, but success or
        // failure is a function of the running cgroup version (v1 or v2).  The strategy here is
        // that if the system is running cgroup v2 then the attribute will exist and the Process
        // will contain a non-empty path.  Otherwise, the process will contain an empty path.
        // The path is tested only in the isReady() method.
        std::string path;
        if (CgroupGetAttributePathForProcess("MemEvents", mUid, mPid, path)) {
            mCgroupRoot = std::filesystem::path(path).remove_filename();
        }
    }

    // A process owns nothing.  In particular, it does not own its watch descriptors, so the
    // destructor is just the default.
    ~Process() = default;

    pid_t getPid() const {
        return mPid;
    }

    uid_t getUid() const {
        return mUid;
    }

    // Return true if the process is ready to be monitored.  The implementation verifies the
    // presence of the cgroup files.
    bool isReady() const {
        return !mCgroupRoot.empty() && std::filesystem::exists(mCgroupRoot);
    }

    // Watch the events files.  Once the call succeeds, further calls quietly do nothing.
    void watch(int inotify_fd, wdmap_t& wdmap, Statistics& stats) {
        if (mMemWatcher.mWd == UNSET) {
            std::string cpath = cgroupPath(CgroupFile::kMemoryEvent);
            char const* path = cpath.c_str();
            int memWd = inotify_add_watch(inotify_fd, path, IN_MODIFY);
            if (memWd < 0) {
                ALOGE_IF(DEBUG, "add_watch(%s) failed: %s", path, strerror(errno));
                stats.mWatchFailed++;
            } else {
                mMemWatcher.mWd = memWd;
                wdmap[mMemWatcher.mWd] = mPid;
                mMemWatcher.mBaseline = getEventCount(MonitoredLimit::kMemoryHigh);
                mMemWatcher.mTriggered = false;
                stats.mWatched++;
            }
        }
    }

    // Stop watching the specified limit.
    void unwatch(int inotify_fd, wdmap_t& wdmap, MonitoredLimit type) {
        switch (type) {
            case MonitoredLimit::kUnknown:
                break;
            case MonitoredLimit::kMemoryHigh:
                mMemWatcher.mWd = unwatch(inotify_fd, wdmap, mMemWatcher.mWd);
                break;
            case MonitoredLimit::kSwapMax:
                break;
            case MonitoredLimit::kAnonSwap:
                break;
        }
    }

    // Stop watching all limits.  This does not change the initialized flag but it does remove
    // the watch descriptors from the inotify.  To resume watching, clear the initialized flag.
    void unwatch(int inotify_fd, wdmap_t& wdmap) {
        unwatch(inotify_fd, wdmap, MonitoredLimit::kMemoryHigh);
        unwatch(inotify_fd, wdmap, MonitoredLimit::kSwapMax);
    }

    // Return a string that identifies this process.
    std::string toString() const {
        return android::base::StringPrintf("pid=%d uid=%d mem=%d", mPid, mUid, mMemWatcher.mWd);
    }

    // Return true if the process is alive.  If the process is privileged (meaning, it's
    // system_server, use the kill() strategy because it is very fast.  Otherwise, test the
    // cgroup path. This is (relatively) expensive but it only occurs in test code.  It is also
    // definitive, since if the cgroup path cannot be seen then watching cannot occur.
    bool isAlive(bool privileged) const {
        if (privileged) {
            return kill(mPid, 0) == 0;
        } else {
            return !mCgroupRoot.empty() && std::filesystem::exists(mCgroupRoot);
        }
    }

    // Return the limit type that corresponds to the watch descriptor.
    MonitoredLimit getLimitType(int wd) const {
        if (wd == mMemWatcher.mWd) {
            return MonitoredLimit::kMemoryHigh;
        } else {
            return MonitoredLimit::kUnknown;
        }
    }

    // Return the configured limit for the specified type.  This is the last value that was
    // configured by the feature, which may be different from the value in the cgroup files if
    // another system process is also modifying cgroup files.
    int64_t getLimitValue(MonitoredLimit type) const {
        switch (type) {
            case MonitoredLimit::kUnknown:
                return 0;
            case MonitoredLimit::kMemoryHigh:
                return mMemoryHighLimit;
            case MonitoredLimit::kSwapMax:
                return mSwapMaxLimit;
            case MonitoredLimit::kAnonSwap:
                return mAnonSwapLimit;
        }
    }

    // Return the count of events associated with the limit.
    int64_t getEventCount(MonitoredLimit type) const {
        switch (type) {
            case MonitoredLimit::kUnknown:
                return 0;
            case MonitoredLimit::kMemoryHigh:
                return readMemoryEvents().high;
            case MonitoredLimit::kSwapMax:
                return 0;
            case MonitoredLimit::kAnonSwap:
                return 0;
        }
    }

    // Return the baseline associated with the limit.  The baseline is the count that was
    // recorded when the watch began.
    int64_t getEventBaseline(MonitoredLimit type) const {
        switch (type) {
            case MonitoredLimit::kUnknown:
                return 0;
            case MonitoredLimit::kMemoryHigh:
                return mMemWatcher.mBaseline;
            case MonitoredLimit::kSwapMax:
                return 0;
            case MonitoredLimit::kAnonSwap:
                return 0;
        }
    }

    // Return the value of the metric associated with the limit.
    int64_t getMetric(MonitoredLimit type) const {
        switch (type) {
            case MonitoredLimit::kUnknown:
                return 0;
            case MonitoredLimit::kMemoryHigh:
                return readMemoryStat().anon;
            case MonitoredLimit::kSwapMax:
                return readMetric(CgroupFile::kSwapCurrent);
            case MonitoredLimit::kAnonSwap:
                return readMemoryStat().anonMetric() + readMetric(CgroupFile::kSwapCurrent);
        }
    }

    // Add the second operand to the limit value. What is special about this is that a limit
    // value of DISABLED is never altered.
    static int64_t incrLimit(int64_t limit, int64_t incr) {
        return (limit == LIMIT_IS_DISABLED) ? limit : (limit + incr);
    }

    // Set the value for the specified limit.  There are two special cases for limits: the
    // "disabled" value is converted to "max" and the "ignored" value is skipped completely.
    void setLimit(MonitoredLimit type, int64_t limit) {
        if (limit <= LIMIT_IS_IGNORED) return;

        switch (type) {
            case MonitoredLimit::kUnknown:
                break;
            case MonitoredLimit::kMemoryHigh:
                mMemoryHighLimit = limit;
                if (mMemWatcher.mTriggered) {
                    // Add some margin to memHigh so that the CPU can run and perhaps shed anon
                    // memory before hitting the limit.
                    limit = incrLimit(limit, mMemWatcher.mMargin);
                }
                writeLimit(cgroupPath(CgroupFile::kMemoryHigh), limit);
                break;
            case MonitoredLimit::kSwapMax:
                mSwapMaxLimit = limit;
                writeLimit(cgroupPath(CgroupFile::kSwapMax), limit);
                break;
            case MonitoredLimit::kAnonSwap:
                mAnonSwapLimit = limit;
                break;
        }
    }

    // Set the process limits.
    void setLimits(int64_t memHigh, int64_t swapMax) {
        setLimit(MonitoredLimit::kMemoryHigh, memHigh);
        setLimit(MonitoredLimit::kSwapMax, swapMax);
        setLimit(MonitoredLimit::kAnonSwap, memHigh + swapMax);
    }

    // Reset the stored limits.  The primary purpose is to add the margin to memory.high if the
    // swap event has fired.  This is only called when either the memory.high or swap.max events
    // have just fired.
    void resetLimits() {
        setLimits(mMemoryHighLimit, mSwapMaxLimit);
    }

    // Set the "event" flag based on the limit.  If limitMode is false, the limit is set to
    // "max" before limits are disabled.
    void setExceeded(MonitoredLimit type, bool limitMode) {
        switch (type) {
            case MonitoredLimit::kUnknown:
                break;
            case MonitoredLimit::kMemoryHigh:
                if (!limitMode) {
                    setLimits(LIMIT_IS_DISABLED, LIMIT_IS_IGNORED);
                }
                mMemWatcher.mTriggered = true;
                break;
            case MonitoredLimit::kSwapMax:
                break;
            case MonitoredLimit::kAnonSwap:
                mAnonSwapTriggered = true;
                break;
        }
    }

    // Return true if the process is in the red zone.  A process is in the red zone if
    // memory.high and memory.swap.high have both hit their configured limits.  Once in the red
    // zone, the process is granted a margin of extra memory over the anon limit so that it can
    // continue to run.  MemoryLimiter cannot rely on cgroup events at this point, and resorts
    // to a periodic poll to see if any process is is overlimit.
    bool isRed() const {
        return mMemWatcher.mTriggered && !mAnonSwapTriggered;
    }

    // Check the AnonSwap metric against its limit.  There are three possible returns: Hot,
    // Okay, and Cold.  Hot means the limit has been exceeded.  Okay means the limit has not
    // been exceeded but the metric is high enough that polling is required.  Cold means the
    // metric is low enough that we can rely on cgroup events.
    AnonSwapState testAnonSwap() const {
        if (mAnonSwapTriggered) return AnonSwapState::kTriggered;
        int64_t metric = getMetric(MonitoredLimit::kAnonSwap);
        if (mAnonSwapLimit < 0) {
            // The limit is disabled.
            return AnonSwapState::kOkay;
        } else if (metric > mAnonSwapLimit) {
            return AnonSwapState::kHot;
        } else if (metric < (mMemoryHighLimit - mMemHighHysteresis)) {
            return AnonSwapState::kCold;
        } else {
            return AnonSwapState::kOkay;
        }
    }

private:
    // A constant that identifies a file descriptor or watch descriptor that is unset.  Posix
    // never creates a negative descriptor.
    static const int UNSET = -1;

    // The pid and uid being monitored.
    pid_t mPid;
    uid_t mUid;

    // The root of the cgroup file system for this process.
    std::filesystem::path mCgroupRoot;

    // The memory watcher.
    Watcher mMemWatcher = Watcher(MonitoredLimit::kMemoryHigh, mMemHighMargin);

    // The configured limit for memory high.  This is not necessarily the limit programmed into
    // the cgroup files.
    int64_t mMemoryHighLimit = LIMIT_IS_DISABLED;

    // The configured limit for swap max.
    int64_t mSwapMaxLimit = LIMIT_IS_DISABLED;

    // The limit of anon+swap.
    int64_t mAnonSwapLimit = LIMIT_IS_DISABLED;

    // True if the anon+swap has triggered.
    bool mAnonSwapTriggered = false;

    // A simple wrapper to stop watching a watch descriptor.  This does nothing if the watch
    // descriptor is unset.   The wrapper returns UNSET so that it can be used in an assignment.
    int unwatch(int inotify_fd, wdmap_t& wdmap, int wd) {
        if (wd >= 0) {
            inotify_rm_watch(inotify_fd, wd);
            wdmap.erase(wd);
        }
        return UNSET;
    }

    // Return the path to the cgroup file.
    std::string cgroupPath(CgroupFile file) const {
        switch (file) {
            case CgroupFile::kUnknown:
                return "/dev/null";
            case CgroupFile::kMemoryStat:
                return (mCgroupRoot / "memory.stat").string();
            case CgroupFile::kMemoryEvent:
                return (mCgroupRoot / "memory.events").string();
            case CgroupFile::kMemoryHigh:
                return (mCgroupRoot / "memory.high").string();
            case CgroupFile::kSwapCurrent:
                return (mCgroupRoot / "memory.swap.current").string();
            case CgroupFile::kSwapMax:
                return (mCgroupRoot / "memory.swap.max").string();
        }
    }

    // Return the content of a cgroup file as a string.
    std::optional<std::string> cgroupData(CgroupFile file) const {
        std::string content;
        if (!android::base::ReadFileToString(cgroupPath(file), &content, false)) {
            return std::nullopt;
        }
        return content;
    }

    // Read a single int64_t from the path.  Return 0 on error.
    int64_t readMetric(CgroupFile file) const {
        auto data = cgroupData(file);
        if (!data) return 0;
        errno = 0;
        int64_t value = strtoll(data->c_str(), nullptr, 10);
        return (errno == 0) ? value : 0;
    }

    MemoryStat readMemoryStat() const {
        return MemoryStat(cgroupData(CgroupFile::kMemoryStat));
    }

    MemoryEvents readMemoryEvents() const {
        return MemoryEvents(cgroupData(CgroupFile::kMemoryEvent));
    }

    // Return a limit string, suitable for writing to a cgroup limit file.  The key new behavior
    // is that the distinguished value LIMIT_IS_DISABLED is converted to "max".
    static std::string limitStr(int64_t limit) {
        return (limit == LIMIT_IS_DISABLED) ? "max" : std::to_string(limit);
    }

    // Write a limit to a cgroup file.  Any reclaim that is forced by lowering the limit will
    // occur synchronously.
    static bool writeLimit(std::string const& path, int64_t limit) {
        return android::base::WriteStringToFile(limitStr(limit), path.c_str());
    }
};

// The JVM information that supports callback notifications.  This class is valid in a single
// thread that remains attached for its lifetime.
class Callback {
    JavaVM* mVm = nullptr;
    jmethodID mFunc = nullptr;
    jobject mLimiter = nullptr;

public:
    // The constructor throws a Java exception on error.  If an exception is thrown, some
    // attributes will be null.
    Callback(JNIEnv* env, jobject jlimiter) {
        if (env->GetJavaVM(&mVm) != 0) {
            throwRuntime(env, "Callback: GetJavaVM() failed");
            return;
        }
        char const* class_name = "com/android/server/am/MemoryLimiter$ControllerEnabled";
        jclass service = env->FindClass(class_name);
        if (service == nullptr) {
            // Throws ClassFormatError, ClassCircularityError, NoClassDefFoundError,
            // OutOfMemoryError
            return;
        }
        mFunc = env->GetMethodID(service, "onLimitExceeded", "(IIIJJ)V");
        if (mFunc == nullptr) {
            // Throws NoSuchMethodError, ExceptionInInitializerError, or OutOfMemoryError
            return;
        }
        mLimiter = env->NewGlobalRef(jlimiter);
        if (mLimiter == nullptr) {
            throwRuntime(env, "failed to create global ref");
            return;
        }
    }

    // The copy constructor is disallowed.
    Callback(Callback const&) = delete;

    ~Callback() {
        // These pointers will be null only if the constructor encountered a failure and exited
        // early.
        if (mVm != nullptr && mLimiter != nullptr) {
            JNIEnv* env;
            if (mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
                env->DeleteGlobalRef(mLimiter);
            } else {
                ALOGE("~Callback() failed in GetEnv()");
            }
        }
    }

    void operator()(Process const& process, MonitoredLimit type) {
        JNIEnv* env;
        if (mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->CallVoidMethod(mLimiter, mFunc, process.getPid(), process.getUid(), type,
                                process.getLimitValue(MonitoredLimit::kMemoryHigh),
                                process.getLimitValue(MonitoredLimit::kSwapMax));
        } else {
            ALOGE("GetEnv() failed");
        }
    }
};

class Monitor {
    // A mutex to guard access to targets.
    mutable std::mutex mLock;

    // The callback
    Callback mCallback;

    // The Java VM, used by the monitor thread.
    JavaVM* mVm;

    // The inotify object that watches cgroup event files.
    base::unique_fd mInotifyFd;

    // The inter-thread communication pipe.
    base::unique_fd mEventFd;

    // The epoll object.
    base::unique_fd mEpollFd;

    // The polling thread.
    pthread_t mPoller;

    // The list of monitored processes, indexed by pid.
    std::unordered_map<pid_t, Process> mTargets;

    // The map from watch descriptors to pids.
    wdmap_t mWdMap;

    // Performance statistics.
    Statistics mStatistics;

    // Event FD commands.
    enum class Cmd {
        Error = 0,
        Stop = 1,
    };

public:
    Monitor(JNIEnv* env, jobject jlimiter, bool privileged, bool monitor, bool monitorSwap,
            bool limitMode, bool testMode)
          : mCallback(env, jlimiter),
            mSystem(privileged),
            mMonitor(monitor),
            mMonitorSwap(monitorSwap),
            mLimitMode(limitMode),
            mTestMode(testMode) {
        if (env->ExceptionCheck()) {
            // The callback has probably failed in its constructor.  Exit immediately.
            return;
        }

        if (!monitor) {
            // This is not an error.  The monitoring feature has been disabled, so do not create
            // the inotify object, epoll object, or monitoring thread.
            return;
        }

        if (env->GetJavaVM(&mVm) != 0) {
            throwRuntime(env, "Monitor: GetJavaVM() failed");
            return;
        }

        int ifd = inotify_init();
        if (ifd < 0) {
            throwRuntime(env, "inotify_init() failed: %s", strerror(errno));
            return;
        }
        mInotifyFd.reset(ifd);

        int efd = eventfd(0, EFD_CLOEXEC | EFD_SEMAPHORE);
        if (efd < 0) {
            throwRuntime(env, "eventfd() failed: %s", strerror(errno));
            return;
        }
        mEventFd.reset(efd);

        // See the man page for epoll_create(): the size argument is ignored except that it must
        // be non-zero.
        int pfd = epoll_create(1);
        if (pfd < 0) {
            throwRuntime(env, "epoll_create() failed: %s", strerror(errno));
            return;
        }
        mEpollFd.reset(pfd);

        // Add the two, permanent descriptors to epoll.
        struct epoll_event e_event = {.events = EPOLLIN, .data.u64 = 0};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mEventFd, &e_event) < 0) {
            throwRuntime(env, "epoll add(event) failed: %s", strerror(errno));
            return;
        }

        struct epoll_event i_event = {.events = EPOLLIN, .data.u64 = 1};
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mInotifyFd, &i_event) != 0) {
            throwRuntime(env, "epoll add(inotify) failed: %s", strerror(errno));
            return;
        }

        if (pthread_create(&mPoller, 0, &Monitor::startMonitoring, this) != 0) {
            throwRuntime(env, "pthread_create() failed: %s", strerror(errno));
            return;
        }
        pthread_setname_np(mPoller, "memory.limiter");
    }

    // No copy constructors or move constructors.
    Monitor(Monitor const&) = delete;

    ~Monitor() {
        if (mMonitor) {
            if (sendCommand(Cmd::Stop)) {
                pthread_join(mPoller, nullptr);
            } else {
                ALOGE("failed to send stop command: %s", strerror(errno));
            }
        }
    }

    // Return the pid associated with the descriptor.  A zero pid means "not found".
    pid_t lookup(int descriptor) {
        if (mWdMap.contains(descriptor)) {
            return mWdMap[descriptor];
        } else {
            return 0;
        }
    }

    // Prepare to watch a process.
    void start(int pid) {
        if (!mMonitor) return;
        forget(pid);
        mStatistics.mStarted++;
    }

    // Set limits on a process.  This does nothing if the process cgroup files are not ready.
    void setLimits(int pid, int uid, int64_t memHigh, int64_t swapMax) {
        if (!mMonitor) return;
        if (!mMonitorSwap) {
            swapMax = LIMIT_IS_IGNORED;
        }
        std::lock_guard _l(mLock);
        if (auto p = watchLocked(pid, uid); p.has_value()) {
            (*p)->setLimits(memHigh, swapMax);
        }
    }

    // Forget about a monitored process, if it exists.
    void forget(pid_t pid) {
        if (!mMonitor) return;
        std::lock_guard _l(mLock);
        auto i = mTargets.find(pid);
        if (i != mTargets.end()) {
            forgetLocked(i);
        }
    }

    // Return the statistics as a string.
    std::string getStatistics() const {
        std::lock_guard _l(mLock);
        return mStatistics.toString();
    }

private:
    std::unordered_map<pid_t, Process>::iterator forgetLocked(
            std::unordered_map<pid_t, Process>::iterator p) {
        p->second.unwatch(mInotifyFd, mWdMap);
        p = mTargets.erase(p);
        mStatistics.mProcesses = mTargets.size();
        return p;
    }

    // Ensure the process is being watched.  Return a pointer to the process.  If this method
    // returns a process pointer, it is guaranteed that the process is being watched.
    std::optional<Process*> watchLocked(int pid, int uid) {
        auto i = mTargets.find(pid);
        if (i == mTargets.end()) {
            Process p(pid, uid);
            if (!p.isReady()) {
                // If isReady() fails then the cgroup files are not present, which is either
                // because the process is actually not alive or the cgroup files have not been
                // created.  Either way, skip the current call.
                return std::nullopt;
            }
            if (auto [j, inserted] = mTargets.emplace(pid, std::move(p)); !inserted) {
                // This is a startling failure.
                return std::nullopt;
            } else {
                j->second.watch(mInotifyFd, mWdMap, mStatistics);
                i = j;
                mStatistics.mProcesses = mTargets.size();
                mStatistics.mProcessesHwm.store(
                        std::max(mStatistics.mProcessesHwm, mStatistics.mProcesses));
            }
        }
        return &i->second;
    }

    static void* startMonitoring(void* arg) {
        return reinterpret_cast<Monitor*>(arg)->run();
    }

    // This class captures information from the poll functions.
    struct PollStatus {
        bool running = true;
        bool anyRed = false;
    };

    // The main monitoring loop.
    void* run() {
        ALOGI("begin monitoring");

        // Attach the thread to the VM.  It is known that the thread is not currently attached.
        JNIEnv* env;
        if (mVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            ALOGE("failed to attach monitor thread");
            return nullptr;
        }

        static const int event_size = 32;
        struct epoll_event events[event_size];
        memset(events, 0, sizeof(events));

        // Flags learned from the poll.  Routines can signal that the poll should exit or that a
        // red-zone process is in the process list.
        PollStatus status;

        // The thread polls for two conditions: processes that have exited and processes that
        // are in the red-zone and may have breached anon+swap.  The PID poll is relatively slow
        // and always runs.  The RED poll is faster but only runs if there is a red-zone
        // process.  The initial poll, however, is fast (1s).  The timeout will be adjusted to
        // its correct value after the first event is handled.
        int timeout = TEST_POLL_PERIOD_MS;

        while (true) {
            int ready = epoll_wait(mEpollFd, events, event_size, timeout);
            if (ready < 0) {
                if (errno != EINTR) {
                    ALOGE("epoll_wait fails: %s", strerror(errno));
                    break;
                }
            } else if (ready == 0) {
                handle_timeout(status);
            } else {
                handle_epoll(events, ready, status);
            }
            if (!status.running) {
                break;
            }

            if (mTestMode) {
                // If running in test mode, the timeout occurs every second.
                timeout = TEST_POLL_PERIOD_MS;
            } else if (status.anyRed) {
                timeout = RED_POLL_PERIOD_MS;
                mStatistics.mRedPoll++;
            } else {
                timeout = PID_POLL_PERIOD_MS;
            }
        }

        mVm->DetachCurrentThread();
        ALOGI("end monitoring");
        return nullptr;
    }

    // Handle a poll timeout.  This scrubs non-existent processes from the target list.
    void handle_timeout(PollStatus& status) {
        std::vector<Process> red;
        int count = 0;
        status.anyRed = false;
        {
            const std::lock_guard _l(mLock);
            for (auto i = mTargets.begin(); i != mTargets.end();) {
                Process& p = i->second;
                if (!p.isAlive(mSystem)) {
                    ALOGI_IF(DEBUG, "scrubbing %s", p.toString().c_str());
                    i = forgetLocked(i);
                    count++;
                    continue;
                }
                if (p.isRed()) {
                    status.anyRed = true;
                    switch (p.testAnonSwap()) {
                        case AnonSwapState::kHot:
                            red.push_back(p);
                            p.setExceeded(MonitoredLimit::kAnonSwap, mLimitMode);
                            break;
                        case AnonSwapState::kOkay:
                            break;
                        case AnonSwapState::kTriggered:
                            break;
                        case AnonSwapState::kCold:
                            p.watch(mInotifyFd, mWdMap, mStatistics);
                            break;
                    }
                    mStatistics.mRedProcess++;
                }
                ++i;
            }
        }
        for (size_t i = 0; i < red.size(); i++) {
            mCallback(red[i], MonitoredLimit::kAnonSwap);
        }

        ALOGI_IF(DEBUG && count > 0, "scrubbed %d processes", count);
    }

    // Handle a single epoll event.  Return true if the enclosing loop should continue and false
    // if it should exit.
    void handle_epoll(struct epoll_event const* events, int size, PollStatus& status) {
        for (int i = 0; i < size; i++) {
            uint64_t datum = events[i].data.u64;
            switch (datum) {
                case 0:
                    handle_event(status);
                    break;
                case 1:
                    handle_inotify(status);
                    break;
                default:
                    ALOGE("unexpected poll datum: %" PRIu64, datum);
                    break;
            }
        }
    }

    // A thread-safe FIFO queue of commands.
    class CmdQueue {
        mutable std::mutex mLock;
        std::queue<Cmd> mCmd;

    public:
        void push(Cmd cmd) {
            std::lock_guard _l(mLock);
            mCmd.push(cmd);
        }
        Cmd pop() {
            std::lock_guard _l(mLock);
            if (mCmd.empty()) return Cmd::Error;
            Cmd cmd = mCmd.front();
            mCmd.pop();
            return cmd;
        }
    };
    CmdQueue mCmd;

    // Push the command on the queue and notify the poller that a command is ready.  The
    // operations must proceed in that order, to ensure that the poller does not try to read
    // the queue before a command has been placed in it.
    bool sendCommand(Cmd cmd) {
        mCmd.push(cmd);
        uint64_t data = 1;
        return ::write(mEventFd, &data, sizeof(data)) == sizeof(data);
    }

    // Handle an event that is sent to the loop from the upper layers.  The function returns
    // false if the thread should terminate.
    void handle_event(PollStatus& status) {
        uint64_t data;
        if (read(mEventFd, &data, sizeof(data)) == 8) {
            while (data-- > 0) {
                Cmd cmd = mCmd.pop();
                switch (cmd) {
                    case Cmd::Stop:
                        status.running = false;
                        break;
                    default:
                        ALOGE("read(event) unknown cmd %d", cmd);
                        break;
                }
            }
        } else {
            ALOGE("read(event) failed: %s", strerror(errno));
        }
    }

    void handle_inotify(PollStatus& status) {
        // A buffer big enough to hold at least one complete inotify event.
        char data[sizeof(inotify_event) + NAME_MAX + 1];

        ssize_t len = read(mInotifyFd, data, sizeof(data));
        if (len < sizeof(inotify_event)) {
            ALOGE("read(inotify) failed: %s", strerror(errno));
            return;
        }

        // The size of data and the fact that read() returned enough bytes for an event means
        // the following cast is valid.  There may be multiple events in the buffer because of
        // the variable name[] field, which is not used in this code.
        for (size_t pos = 0; pos <= (len - sizeof(inotify_event));) {
            const inotify_event* event = reinterpret_cast<const inotify_event*>(&data[pos]);
            if ((event->mask & IN_MODIFY) != 0) {
                handle_modify(event->wd, status);
            }
            pos += sizeof(inotify_event) + event->len;
        }
    }

    // Handle a single inotify "modify" event.
    void handle_modify(int wd, PollStatus& status) {
        MonitoredLimit type = MonitoredLimit::kUnknown;
        std::optional<Process> found;
        bool anyRed = false;
        {
            // A block that is guarded by the lock.
            std::lock_guard _l(mLock);
            pid_t pid = lookup(wd);
            // inotify events can arrive when a process is deleted and its cgroup files go away.
            // In that case, the process will not be in the process list, so ignore it.  The
            // other reason a pid is not found is if an event arrives between the time it fired
            // and it was unwatched: the first occurrence will remove it from the pid-map.  The
            // second occurrence can be ignored.
            auto i = mTargets.find(pid);
            if (i == mTargets.end()) return;

            Process& p = i->second;
            if (!p.isAlive(mSystem)) {
                // The process has exited.  It's unclear why the watch fired but ignore
                // the event and stop watching the process.  The process record will be
                // scrubbed in the poller.
                p.unwatch(mInotifyFd, mWdMap);
                return;
            }
            type = p.getLimitType(wd);
            if (p.getEventCount(type) != p.getEventBaseline(type)) {
                // Collect the state of the process before any further modifications.
                found = p;

                // Stop watching the specified limit, but keep watching any other enabled limits.
                p.unwatch(mInotifyFd, mWdMap, type);
                p.setExceeded(type, mLimitMode);
                anyRed = p.isRed();
                p.resetLimits();
            } else {
                mStatistics.mFalseEvents++;
            }
        }

        // If a callback is necessary, ensure it is issued outside the lock.
        if (found) {
            mCallback(*found, type);
            mStatistics.mEvents++;
        }
        if (anyRed) {
            status.anyRed = true;
        }
    }

    // True if this is system_server, which is a privileged process.  If this is not
    // system_server then it is most likely a test process.
    const bool mSystem;

    // True if monitoring is enabled.
    const bool mMonitor;

    // True if swap monitoring is enabled.
    const bool mMonitorSwap;

    // True if limits continue to be applied to a process after a limit is breached.
    const bool mLimitMode;

    // True if running in test mode.  This primarily affects the poller, which is busier (less
    // efficient) in test mode.
    const bool mTestMode;
};

// Convert a long from the java layer into a Monitor*.
Monitor* getMonitor(jlong service) {
    return reinterpret_cast<Monitor*>(service);
}

// Create a new Monitor object and returns its address.
jlong initLimiter(JNIEnv* env, jclass, jobject jlimiter, jboolean monitor, jboolean monitorSwap,
                  jboolean limitMode, jboolean testMode) {
    const bool system = (getuid() == AID_SYSTEM);
    std::unique_ptr<Monitor> m(
            new Monitor(env, jlimiter, system, monitor, monitorSwap, limitMode, testMode));
    if (env->ExceptionCheck()) {
        return 0;
    }
    return reinterpret_cast<jlong>(m.release());
}

// Close and release a limiter.
void closeLimiter(JNIEnv* env, jclass, jlong service) {
    Monitor* m = getMonitor(service);
    delete m;
}

// A process has started.  This call just ensures that the process list does not contain a
// stale references to the pid.
void startProcess(JNIEnv* env, jclass, jlong service, jint pid, jint /* uid */) {
    Monitor* m = getMonitor(service);
    m->start(pid);
}

// A process is being configured with memory and swap limits.  See writeLimit() for special
// handling of non-positive limits.
void configureLimit(JNIEnv*, jclass, jlong service, jint pid, jint uid, jlong mem, jlong swap) {
    Monitor* m = getMonitor(service);
    m->setLimits(pid, uid, mem, swap);
}

// Return the statistics for the memory limiter.  If the limiter is invalid, null is returned.
jstring getStatistics(JNIEnv* env, jclass, jlong service) {
    Monitor* m = getMonitor(service);
    std::string stats = m->getStatistics();
    return env->NewStringUTF(stats.c_str());
}

// Parse a cgroup file into its components.  The two parameters are the name of the file and the
// string to be parsed.  The function returns an array of longs.  The order of the longs matches
// the order in which elements are found in the cgroup file.  Null is returned if parsing fails.
//
// This is not the most intuitive interface but it exists solely for testing internal code.
jlongArray testParseCgroup(JNIEnv* env, jclass, jstring jfile, jstring jdata) {
    ScopedUtfChars file(env, jfile);
    ScopedUtfChars data(env, jdata);
    std::vector<int64_t> fields;

    if (strcmp(file.c_str(), "memory.stat") == 0) {
        MemoryStat stat;
        if (!stat.scan(data.c_str())) {
            return nullptr;
        }
        fields.push_back(stat.anon);
        fields.push_back(stat.shmem);
    } else if (strcmp(file.c_str(), "memory.events") == 0) {
        MemoryEvents events;
        if (!events.scan(data.c_str())) {
            return nullptr;
        }
        fields.push_back(events.low);
        fields.push_back(events.high);
        fields.push_back(events.max);
        fields.push_back(events.oom);
        fields.push_back(events.oom_kill);
        fields.push_back(events.oom_group_kill);
    } else {
        return nullptr;
    }

    jlongArray result = env->NewLongArray(fields.size());
    for (size_t i = 0; i < fields.size(); i++) {
        env->SetLongArrayRegion(result, i, 1, &fields[i]);
    }
    return result;
}

const JNINativeMethod sMethods[] = {
        {"initLimiter", "(Lcom/android/server/am/MemoryLimiter$Controller;ZZZZ)J",
         (void*)initLimiter},
        {"closeLimiter", "(J)V", (void*)closeLimiter},
        {"onProcessStarted", "(JII)V", (void*)startProcess},
        {"configureLimit", "(JIIJJ)V", (void*)configureLimit},
        {"getStatistics", "(J)Ljava/lang/String;", (void*)getStatistics},
        {"testParseCgroup", "(Ljava/lang/String;Ljava/lang/String;)[J", (void*)testParseCgroup},
};

} // namespace

// Register the JNI methods and perform some common initialization.  This returns int but no one
// looks at the return value.  The method is expected to crash the process if anything goes
// wrong.
int register_android_server_am_MemoryLimiter(JNIEnv* env) {
    static const char* class_name = "com/android/server/am/MemoryLimiter";
    return jniRegisterNativeMethods(env, class_name, sMethods, NELEM(sMethods));
}

} // namespace android
