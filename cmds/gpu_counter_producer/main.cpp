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

#define LOG_TAG "gpu_counters"

#include <dlfcn.h>
#include <fcntl.h>
#include <log/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <filesystem>
#include <string>

#include <android-base/properties.h>

using android::base::GetProperty;

#define _LOG(level, msg, ...)                                 \
    do {                                                      \
        fprintf(stderr, #level ": " msg "\n", ##__VA_ARGS__); \
        ALOG##level(msg, ##__VA_ARGS__);                      \
    } while (false)

#define LOG_ERR(msg, ...) _LOG(E, msg, ##__VA_ARGS__)
#define LOG_WARN(msg, ...) _LOG(W, msg, ##__VA_ARGS__)
#define LOG_INFO(msg, ...) _LOG(I, msg, ##__VA_ARGS__)

typedef void (*FN_PTR)(void);

#define LIBRARY_PATH_PROPERTY "graphics.gpu.profiler.counter_producer_lib"
#define DEFAULT_LIBRARY_PATH "libgpudataproducer.so"

const char* kPidFileName = "/data/local/tmp/gpu_counter_producer.pid";

#if defined(__LP64__)
#define VENDOR_LIB_DIR "/vendor/lib64"
#else
#define VENDOR_LIB_DIR "/vendor/lib"
#endif

static FN_PTR loadLibrary(const char* lib) {
    char* error;

    LOG_INFO("Trying %s", lib);
    void* handle = dlopen(lib, RTLD_GLOBAL);
    if ((error = dlerror()) != nullptr || handle == nullptr) {
        LOG_WARN("Error loading lib: %s", error);
        return nullptr;
    }

    FN_PTR startFunc = (FN_PTR)dlsym(handle, "start");
    if ((error = dlerror()) != nullptr) {
        LOG_ERR("Error looking for start symbol: %s", error);
        dlclose(handle);
        return nullptr;
    }
    return startFunc;
}

static void killExistingProcess() {
    int fd = open(kPidFileName, O_RDONLY);
    if (fd == -1) {
        return;
    }
    char pidString[10];
    if (read(fd, pidString, 10) > 0) {
        int pid = -1;
        sscanf(pidString, "%d", &pid);
        if (pid > 0) {
            kill(pid, SIGINT);
        }
    }
    close(fd);
}

static bool writeToPidFile() {
    killExistingProcess();
    int fd = open(kPidFileName, O_CREAT | O_WRONLY | O_TRUNC,
                  S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH);
    if (fd == -1) {
        return false;
    }
    pid_t pid = getpid();
    char pidString[10];
    sprintf(pidString, "%d", pid);
    write(fd, pidString, strlen(pidString));
    close(fd);
    return true;
}

static void clearPidFile() {
    unlink(kPidFileName);
}

std::string getLdLibraryPath(const std::string& libPath) {
    namespace fs = std::filesystem;
    if (fs::exists(libPath)) {
        if (fs::is_symlink(libPath)) {
            return fs::canonical(libPath).parent_path().string() + ":" VENDOR_LIB_DIR;
        } else {
            return fs::path(libPath).parent_path().string() + ":" VENDOR_LIB_DIR;
        }
    }

    fs::path vendorPath = fs::path(VENDOR_LIB_DIR) / libPath;
    if (fs::is_symlink(vendorPath)) {
        return fs::canonical(vendorPath).parent_path().string() + ":" VENDOR_LIB_DIR;
    }
    return VENDOR_LIB_DIR;
}

static void usage(const char* pname) {
    fprintf(stderr,
            "Starts the GPU hardware counter profiling Perfetto data producer.\n\n"
            "usage: %s [-hf]\n"
            "   -f: run in the foreground.\n"
            "   -h: this message.\n",
            pname);
}

// Program to load the GPU Perfetto producer .so and call start().
int main(int argc, char** argv) {
    const char* pname = argv[0];
    bool foreground = false;
    int c;
    while ((c = getopt(argc, argv, "fh")) != -1) {
        switch (c) {
            case 'f':
                foreground = true;
                break;
            case '?':
            case ':':
            case 'h':
                usage(pname);
                return 1;
        }
    }

    if (optind < argc) {
        usage(pname);
        return 1;
    }

    if (!foreground) {
        daemon(0, 0);
    }

    std::string libPath = GetProperty(LIBRARY_PATH_PROPERTY, DEFAULT_LIBRARY_PATH);
    if (getenv("LD_LIBRARY_PATH") == nullptr) {
        LOG_INFO("Library name: %s", libPath.c_str());
        std::string ldPath = getLdLibraryPath(libPath);
        setenv("LD_LIBRARY_PATH", ldPath.c_str(), 0 /*override*/);
        LOG_INFO("execv with: LD_LIBRARY_PATH=%s", getenv("LD_LIBRARY_PATH"));
        execvpe(pname, argv, environ);
    }

    if (!writeToPidFile()) {
        LOG_ERR("Could not open %s", kPidFileName);
        return 1;
    }

    dlerror(); // Clear any possibly ignored previous error.
    FN_PTR startFunc = loadLibrary(libPath.c_str());

    if (startFunc == nullptr) {
        LOG_ERR("Did not find the producer library");
        LOG_ERR("LD_LIBRARY_PATH=%s", getenv("LD_LIBRARY_PATH"));
        clearPidFile();
        return 1;
    }

    LOG_INFO("Calling start at %p", startFunc);
    (*startFunc)();
    LOG_WARN("Producer has exited.");

    clearPidFile();
    return 0;
}
