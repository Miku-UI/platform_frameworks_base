/*
 * Copyright (C) 2025 The Android Open Source Project
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

#include <errno.h>
#include <jni.h>
#include <linux/fs.h>
#include <nativehelper/JNIPlatformHelp.h>
#include <string.h>
#include <sys/ioctl.h>

#include <string>

extern "C" JNIEXPORT void JNICALL Java_com_android_fsverity_Helper_disableCompression(
        JNIEnv* env, [[maybe_unused]] jobject clazz, jobject fdObj) {
    int fd = jniGetFDFromFileDescriptor(env, fdObj);

    unsigned int flags = 0;
    if (ioctl(fd, FS_IOC_GETFLAGS, &flags) != 0) {
        std::string msg = std::string("FS_IOC_GETFLAGS failed: ") + strerror(errno);
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), msg.c_str());
        return;
    }
    flags &= ~FS_COMPR_FL;
    flags |= FS_NOCOMP_FL;
    if (ioctl(fd, FS_IOC_SETFLAGS, &flags) != 0 &&
        // EOPNOTSUPP is expected if the filesystem doesn't have the compression feature flag.
        errno != EOPNOTSUPP) {
        std::string msg = std::string("FS_IOC_SETFLAGS failed: ") + strerror(errno);
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), msg.c_str());
    }
}
