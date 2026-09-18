#include <android/dlext.h>
#include <android/looper.h>
#include <android/native_service.h>
#include <bionic/dlext_namespaces.h>
#include <cutils/ashmem.h>
#include <link.h>
#include <native_service_private.h>
#include <time.h>

/* Note: Do not use bindgen for dlfcn.h, use it with only the dlext extensions.
         dlfcn.h is known to generate incorrect definition on LP32 due to
         https://github.com/rust-lang/rust-bindgen/issues/2472. */
