/*
 * Copyright (C) 2007 The Android Open Source Project
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

#define LOG_NDEBUG 0

#define LOG_TAG "BootAnimation"
#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include <cstring>
#include <filesystem>
#include <vector>

#include <stdint.h>
#include <inttypes.h>
#include <sys/inotify.h>
#include <sys/poll.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <math.h>
#include <fcntl.h>
#include <utils/misc.h>
#include <utils/Trace.h>
#include <signal.h>
#include <time.h>

#include <cutils/properties.h>

#include <android/bitmap.h>
#include <android/imagedecoder.h>
#include <androidfw/AssetManager.h>
#include <androidfw/ZipFileRO.h>
#include <utils/FileMap.h>
#include <binder/IPCThreadState.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <android-base/properties.h>

#include <ui/DisplayMode.h>
#include <ui/PixelFormat.h>
#include <ui/Rect.h>
#include <ui/Region.h>

#include <gui/ISurfaceComposer.h>
#include <gui/DisplayEventReceiver.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/eglext.h>

#include "BootAnimation.h"
#include "BootAnimationShaders.h"

#include <com_android_graphics_bootanimation_flags.h>

#define ANIM_PATH_MAX 255
#define STR(x)   #x
#define STRTO(x) STR(x)

namespace android {

using ui::DisplayMode;

static const char OEM_BOOTANIMATION_FILE[] = "/oem/media/bootanimation.zip";
static const char OEM_BOOTANIMATION_MBA_FILE[] = "/oem/media/bootanimation.mba";
static const char PRODUCT_BOOTANIMATION_DIR[] = "/product/media/";
static const char PRODUCT_BOOTANIMATION_DARK_FILE[] = "bootanimation-dark.zip";
static const char PRODUCT_BOOTANIMATION_FILE[] = "bootanimation.zip";
static const char PRODUCT_BOOTANIMATION_DARK_MBA_FILE[] = "bootanimation-dark.mba";
static const char PRODUCT_BOOTANIMATION_MBA_FILE[] = "bootanimation.mba";
static const char SYSTEM_BOOTANIMATION_FILE[] = "/system/media/bootanimation.zip";
static const char SYSTEM_BOOTANIMATION_MBA_FILE[] = "/system/media/bootanimation.mba";
static const char APEX_BOOTANIMATION_FILE[] = "/apex/com.android.bootanimation/etc/bootanimation.zip";
static const char APEX_BOOTANIMATION_MBA_FILE[] =
        "/apex/com.android.bootanimation/etc/bootanimation.mba";
static const char OEM_SHUTDOWNANIMATION_FILE[] = "/oem/media/shutdownanimation.zip";
static const char PRODUCT_SHUTDOWNANIMATION_FILE[] = "/product/media/shutdownanimation.zip";
static const char SYSTEM_SHUTDOWNANIMATION_FILE[] = "/system/media/shutdownanimation.zip";

static constexpr const char* PRODUCT_USERSPACE_REBOOT_ANIMATION_FILE = "/product/media/userspace-reboot.zip";
static constexpr const char* OEM_USERSPACE_REBOOT_ANIMATION_FILE = "/oem/media/userspace-reboot.zip";
static constexpr const char* SYSTEM_USERSPACE_REBOOT_ANIMATION_FILE = "/system/media/userspace-reboot.zip";

static const char BOOTANIM_DATA_DIR_PATH[] = "/data/misc/bootanim";
static const char BOOTANIM_TIME_DIR_NAME[] = "time";
static const char BOOTANIM_TIME_DIR_PATH[] = "/data/misc/bootanim/time";
static const char CLOCK_FONT_ASSET[] = "images/clock_font.png";
static const char CLOCK_FONT_ZIP_NAME[] = "clock_font.png";
static const char PROGRESS_FONT_ASSET[] = "images/progress_font.png";
static const char PROGRESS_FONT_ZIP_NAME[] = "progress_font.png";
static const char LAST_TIME_CHANGED_FILE_NAME[] = "last_time_change";
static const char LAST_TIME_CHANGED_FILE_PATH[] = "/data/misc/bootanim/time/last_time_change";
static const char ACCURATE_TIME_FLAG_FILE_NAME[] = "time_is_accurate";
static const char ACCURATE_TIME_FLAG_FILE_PATH[] = "/data/misc/bootanim/time/time_is_accurate";
static const char TIME_FORMAT_12_HOUR_FLAG_FILE_PATH[] = "/data/misc/bootanim/time/time_format_12_hour";
// Java timestamp format. Don't show the clock if the date is before 2000-01-01 00:00:00.
static const long long ACCURATE_TIME_EPOCH = 946684800000;
static constexpr char FONT_BEGIN_CHAR = ' ';
static constexpr char FONT_END_CHAR = '~' + 1;
static constexpr size_t FONT_NUM_CHARS = FONT_END_CHAR - FONT_BEGIN_CHAR + 1;
static constexpr size_t FONT_NUM_COLS = 16;
static constexpr size_t FONT_NUM_ROWS = FONT_NUM_CHARS / FONT_NUM_COLS;
static const int TEXT_CENTER_VALUE = INT_MAX;
static const int TEXT_MISSING_VALUE = INT_MIN;
static const char EXIT_PROP_NAME[] = "service.bootanim.exit";
static const char PROGRESS_PROP_NAME[] = "service.bootanim.progress";
static const char CLOCK_ENABLED_PROP_NAME[] = "persist.sys.bootanim.clock.enabled";
static const char DECODE_RGB565_PROP_NAME[] = "persist.sys.bootanim.decode.rgb565";
static const int ANIM_ENTRY_NAME_MAX = ANIM_PATH_MAX + 1;
static const int MAX_CHECK_EXIT_INTERVAL_US = 50000;
static constexpr size_t TEXT_POS_LEN_MAX = 16;
static const int DYNAMIC_COLOR_COUNT = 4;
static const char U_TEXTURE[] = "uTexture";
static const char U_FADE[] = "uFade";
static const char U_CROP_AREA[] = "uCropArea";
static const char U_START_COLOR_PREFIX[] = "uStartColor";
static const char U_END_COLOR_PREFIX[] = "uEndColor";
static const char U_COLOR_PROGRESS[] = "uColorProgress";
static const char A_UV[] = "aUv";
static const char A_POSITION[] = "aPosition";

static GLfloat quadPositions[] = {
    -0.5f, -0.5f,
    +0.5f, -0.5f,
    +0.5f, +0.5f,
    +0.5f, +0.5f,
    -0.5f, +0.5f,
    -0.5f, -0.5f
};
static GLfloat quadUVs[] = {
    0.0f, 1.0f,
    1.0f, 1.0f,
    1.0f, 0.0f,
    1.0f, 0.0f,
    0.0f, 0.0f,
    0.0f, 1.0f
};

// ---------------------------------------------------------------------------

BootAnimation::BootAnimation(sp<Callbacks> callbacks)
        : Thread(false), mLooper(new Looper(false)), mClockEnabled(true), mTimeIsAccurate(false),
        mTimeFormat12Hour(false), mTimeCheckThread(nullptr), mCallbacks(callbacks) {
    ATRACE_CALL();
    mSession = sp<SurfaceComposerClient>::make();

    std::string powerCtl = android::base::GetProperty("sys.powerctl", "");
    if (powerCtl.empty()) {
        mShuttingDown = false;
    } else {
        mShuttingDown = true;
    }
    ALOGD("%sAnimationStartTiming start time: %" PRId64 "ms", mShuttingDown ? "Shutdown" : "Boot",
            elapsedRealtime());
}

BootAnimation::~BootAnimation() {
    ATRACE_CALL();
    if (mAnimation != nullptr) {
        releaseAnimation(mAnimation);
        mAnimation = nullptr;
    }
    ALOGD("%sAnimationStopTiming start time: %" PRId64 "ms", mShuttingDown ? "Shutdown" : "Boot",
            elapsedRealtime());
}

void BootAnimation::onFirstRef() {
    ATRACE_CALL();
    status_t err = mSession->linkToComposerDeath(this);
    SLOGE_IF(err, "linkToComposerDeath failed (%s) ", strerror(-err));
    if (err == NO_ERROR) {
        // Load the animation content -- this can be slow (eg 200ms)
        // called before waitForSurfaceFlinger() in main() to avoid wait
        ALOGD("%sAnimationPreloadTiming start time: %" PRId64 "ms",
                mShuttingDown ? "Shutdown" : "Boot", elapsedRealtime());
        preloadAnimation();
        ALOGD("%sAnimationPreloadTiming stop time: %" PRId64 "ms",
                mShuttingDown ? "Shutdown" : "Boot", elapsedRealtime());
    }
}

sp<SurfaceComposerClient> BootAnimation::session() const {
    return mSession;
}

void BootAnimation::binderDied(const wp<IBinder>&) {
    ATRACE_CALL();
    // woah, surfaceflinger died!
    SLOGD("SurfaceFlinger died, exiting...");

    // calling requestExit() is not enough here because the Surface code
    // might be blocked on a condition variable that will never be updated.
    kill( getpid(), SIGKILL );
    requestExit();
}

static void* decodeImage(const void* encodedData, size_t dataLength, AndroidBitmapInfo* outInfo,
    bool premultiplyAlpha, bool decodeOpaque565, int targetWidth = 0, int targetHeight = 0) {
    ATRACE_CALL();
    AImageDecoder* decoder = nullptr;
    AImageDecoder_createFromBuffer(encodedData, dataLength, &decoder);
    if (!decoder) {
        return nullptr;
    }

    const AImageDecoderHeaderInfo* info = AImageDecoder_getHeaderInfo(decoder);
    outInfo->width = AImageDecoderHeaderInfo_getWidth(info);
    outInfo->height = AImageDecoderHeaderInfo_getHeight(info);
    outInfo->format = AImageDecoderHeaderInfo_getAndroidBitmapFormat(info);
    if (decodeOpaque565
        && AImageDecoderHeaderInfo_getAlphaFlags(info) == ANDROID_BITMAP_FLAGS_ALPHA_OPAQUE) {
        AImageDecoder_setAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGB_565);
        outInfo->format = ANDROID_BITMAP_FORMAT_RGB_565;
    }
    // setTargetSize is incompatible with unpremultiplied output.
    if (targetWidth > 0 && targetHeight > 0) {
        if (AImageDecoder_setTargetSize(decoder, targetWidth, targetHeight) ==
                ANDROID_IMAGE_DECODER_SUCCESS) {
            outInfo->width = targetWidth;
            outInfo->height = targetHeight;
        }
    }
    if (!premultiplyAlpha) {
        AImageDecoder_setUnpremultipliedRequired(decoder, true);
    }
    outInfo->stride = AImageDecoder_getMinimumStride(decoder);
    outInfo->flags = 0;

    const size_t size = outInfo->stride * outInfo->height;
    void* pixels = malloc(size);
    int result = AImageDecoder_decodeImage(decoder, pixels, outInfo->stride, size);
    AImageDecoder_delete(decoder);

    if (result != ANDROID_IMAGE_DECODER_SUCCESS) {
        free(pixels);
        return nullptr;
    }
    return pixels;
}

status_t BootAnimation::initTexture(Texture* texture, AssetManager& assets,
        const char* name, bool premultiplyAlpha) {
    ATRACE_CALL();
    Asset* asset = assets.open(name, Asset::ACCESS_BUFFER);
    if (asset == nullptr)
        return NO_INIT;

    AndroidBitmapInfo bitmapInfo;
    void* pixels = decodeImage(asset->getBuffer(false), asset->getLength(), &bitmapInfo,
        premultiplyAlpha, mDecodeOpaqueImageTo565);
    auto pixelDeleter = std::unique_ptr<void, decltype(free)*>{ pixels, free };

    asset->close();
    delete asset;

    if (!pixels) {
        return NO_INIT;
    }

    const int w = bitmapInfo.width;
    const int h = bitmapInfo.height;

    texture->w = w;
    texture->h = h;

    glGenTextures(1, &texture->name);
    glBindTexture(GL_TEXTURE_2D, texture->name);

    switch (bitmapInfo.format) {
        case ANDROID_BITMAP_FORMAT_A_8:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, w, h, 0, GL_ALPHA,
                    GL_UNSIGNED_BYTE, pixels);
            break;
        case ANDROID_BITMAP_FORMAT_RGBA_4444:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                    GL_UNSIGNED_SHORT_4_4_4_4, pixels);
            break;
        case ANDROID_BITMAP_FORMAT_RGBA_8888:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, pixels);
            break;
        case ANDROID_BITMAP_FORMAT_RGB_565:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB,
                    GL_UNSIGNED_SHORT_5_6_5, pixels);
            break;
        default:
            break;
    }

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    return NO_ERROR;
}

status_t BootAnimation::initTexture(FileMap* map, int* width, int* height,
    bool premultiplyAlpha) {
    ATRACE_CALL();
    AndroidBitmapInfo bitmapInfo;
    void* pixels = decodeImage(map->getDataPtr(), map->getDataLength(), &bitmapInfo,
        premultiplyAlpha, mDecodeOpaqueImageTo565);
    auto pixelDeleter = std::unique_ptr<void, decltype(free)*>{ pixels, free };

    // FileMap memory is never released until application exit.
    // Release it now as the texture is already loaded and the memory used for
    // the packed resource can be released.
    delete map;

    if (!pixels) {
        return NO_INIT;
    }

    const int w = bitmapInfo.width;
    const int h = bitmapInfo.height;

    int tw = 1 << (31 - __builtin_clz(w));
    int th = 1 << (31 - __builtin_clz(h));
    if (tw < w) tw <<= 1;
    if (th < h) th <<= 1;

    switch (bitmapInfo.format) {
        case ANDROID_BITMAP_FORMAT_RGBA_8888:
            if (!mUseNpotTextures && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, nullptr);
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, pixels);
            }
            break;

        case ANDROID_BITMAP_FORMAT_RGB_565:
            if (!mUseNpotTextures && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, tw, th, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, nullptr);
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, 0, w, h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, pixels);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, pixels);
            }
            break;
        default:
            break;
    }

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    *width = w;
    *height = h;

    return NO_ERROR;
}

class BootAnimation::DisplayEventCallback : public LooperCallback {
    BootAnimation* mBootAnimation;

public:
    DisplayEventCallback(BootAnimation* bootAnimation) {
        ATRACE_CALL();
        mBootAnimation = bootAnimation;
    }

    int handleEvent(int /* fd */, int events, void* /* data */) {
        ATRACE_CALL();
        if (events & (Looper::EVENT_ERROR | Looper::EVENT_HANGUP)) {
            ALOGE("Display event receiver pipe was closed or an error occurred. events=0x%x",
                    events);
            return 0; // remove the callback
        }

        if (!(events & Looper::EVENT_INPUT)) {
            ALOGW("Received spurious callback for unhandled poll event.  events=0x%x", events);
            return 1; // keep the callback
        }

        constexpr int kBufferSize = 100;
        DisplayEventReceiver::Event buffer[kBufferSize];
        ssize_t numEvents;
        do {
            numEvents = mBootAnimation->mDisplayEventReceiver->getEvents(buffer, kBufferSize);
            for (size_t i = 0; i < static_cast<size_t>(numEvents); i++) {
                const auto& event = buffer[i];
                if (event.header.type == DisplayEventType::DISPLAY_EVENT_HOTPLUG) {
                    SLOGV("Hotplug received");

                    if (!event.hotplug.connected) {
                        // ignore hotplug disconnect
                        continue;
                    }
                    auto token = SurfaceComposerClient::getPhysicalDisplayToken(
                        event.header.displayId);

                    auto& firstDisplay = mBootAnimation->mDisplays.front();
                    if (token != firstDisplay.displayToken) {
                        // ignore hotplug of a secondary display
                        continue;
                    }

                    DisplayMode displayMode;
                    const status_t error = SurfaceComposerClient::getActiveDisplayMode(
                                               firstDisplay.displayToken, &displayMode);
                    if (error != NO_ERROR) {
                        SLOGE("Can't get active display mode.");
                    }
                    mBootAnimation->resizeSurface(displayMode.resolution.getWidth(),
                                                  displayMode.resolution.getHeight(),
                                                  firstDisplay);
                }
            }
        } while (numEvents > 0);

        return 1;  // keep the callback
    }
};

EGLConfig BootAnimation::getEglConfig(const EGLDisplay& display) {
    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE,   8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE,  8,
        EGL_DEPTH_SIZE, 0,
        EGL_NONE
    };
    EGLint numConfigs;
    EGLConfig config;
    eglChooseConfig(display, attribs, &config, 1, &numConfigs);
    return config;
}

ui::Size BootAnimation::limitSurfaceSize(int width, int height) const {
    ui::Size limited(width, height);
    bool wasLimited = false;
    const float aspectRatio = float(width) / float(height);
    if (mMaxWidth != 0 && width > mMaxWidth) {
        limited.height = mMaxWidth / aspectRatio;
        limited.width = mMaxWidth;
        wasLimited = true;
    }
    if (mMaxHeight != 0 && limited.height > mMaxHeight) {
        limited.height = mMaxHeight;
        limited.width = mMaxHeight * aspectRatio;
        wasLimited = true;
    }
    SLOGV_IF(wasLimited, "Surface size has been limited to [%dx%d] from [%dx%d]",
             limited.width, limited.height, width, height);
    return limited;
}

status_t BootAnimation::readyToRun() {
    ATRACE_CALL();
    mAssets.addDefaultAssets();
    return initDisplaysAndSurfaces();
}

status_t BootAnimation::initDisplaysAndSurfaces() {
    std::vector<PhysicalDisplayId> displayIds = SurfaceComposerClient::getPhysicalDisplayIds();
    if (displayIds.empty()) {
        SLOGE("Failed to get ID for any displays");
        return NAME_NOT_FOUND;
    }

    // If Multi-Display isn't explicitly enabled, ignore all displays after the first one
    if (!com::android::graphics::bootanimation::flags::multidisplay()) {
        displayIds.erase(displayIds.begin() + 1, displayIds.end());
    }

    for (const auto id : displayIds) {
        if (const auto token = SurfaceComposerClient::getPhysicalDisplayToken(id)) {
            mDisplays.push_back({.displayToken = token});
        } else {
            SLOGE("Failed to get display token for a display");
            SLOGE("Failed to get display token for display %" PRIu64, id.value);
            return NAME_NOT_FOUND;
        }
    }

    // Initialize EGL
    mEgl = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(mEgl, nullptr, nullptr);
    EGLConfig config = getEglConfig(mEgl);
    EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    mEglContext = eglCreateContext(mEgl, config, nullptr, contextAttributes);

    mMaxWidth = android::base::GetIntProperty("ro.surface_flinger.max_graphics_width", 0);
    mMaxHeight = android::base::GetIntProperty("ro.surface_flinger.max_graphics_height", 0);

    for (size_t displayIdx = 0; displayIdx < mDisplays.size(); displayIdx++) {
        auto& display = mDisplays[displayIdx];
        DisplayMode displayMode;
        const status_t error =
                SurfaceComposerClient::getActiveDisplayMode(display.displayToken, &displayMode);
        if (error != NO_ERROR) {
            return error;
        }
        ui::Size resolution = displayMode.resolution;
        // Clamp each surface to max size
        resolution = limitSurfaceSize(resolution.width, resolution.height);
        // Create the native surface
        display.surfaceControl =
                session()->createSurface(String8("BootAnimation"), resolution.width,
                                         resolution.height, PIXEL_FORMAT_RGB_565,
                                         ISurfaceComposerClient::eOpaque);
        // Attach surface to layerstack, and associate layerstack with physical display
        configureDisplayAndLayerStack(display, ui::LayerStack::fromValue(displayIdx));
        display.surface = display.surfaceControl->getSurface();
        display.eglSurface = eglCreateWindowSurface(mEgl, config, display.surface.get(), nullptr);

        EGLint w, h;
        eglQuerySurface(mEgl, display.eglSurface, EGL_WIDTH, &w);
        eglQuerySurface(mEgl, display.eglSurface, EGL_HEIGHT, &h);
        if (eglMakeCurrent(mEgl, display.eglSurface, display.eglSurface,
                           mEglContext) == EGL_FALSE) {
            return NO_INIT;
        }
        display.initWidth = display.width = w;
        display.initHeight = display.height = h;
        mTargetInset = -1;

        // Rotate the boot animation according to the value specified in the sysprop
        // ro.bootanim.set_orientation_<display_id>. Four values are supported: ORIENTATION_0,
        // ORIENTATION_90, ORIENTATION_180 and ORIENTATION_270.
        // If the value isn't specified or is ORIENTATION_0, nothing will be changed.
        // This is needed to support boot animation in orientations different from the natural
        // device orientation. For example, on tablets that may want to keep natural orientation
        // portrait for applications compatibility and to have the boot animation in landscape.
        rotateAwayFromNaturalOrientationIfNeeded(display);

        projectSceneToWindow(display);
    } // end iteration over all display tokens

    // Register a display event receiver
    mDisplayEventReceiver = std::make_unique<DisplayEventReceiver>();
    status_t status = mDisplayEventReceiver->initCheck();
    SLOGE_IF(status != NO_ERROR, "Initialization of DisplayEventReceiver failed with status: %d",
             status);
    mLooper->addFd(mDisplayEventReceiver->getFd(), 0, Looper::EVENT_INPUT,
                   new DisplayEventCallback(this), nullptr);

    return NO_ERROR;
}

void BootAnimation::configureDisplayAndLayerStack(const Display& display,
                                                  ui::LayerStack layerStack) {
    SurfaceComposerClient::Transaction t;
    t.setDisplayLayerStack(display.displayToken, layerStack);
    t.setLayerStack(display.surfaceControl, layerStack)
     .setLayer(display.surfaceControl, 0x40000000)
     .apply();
}

void BootAnimation::rotateAwayFromNaturalOrientationIfNeeded(Display& display) {
    ATRACE_CALL();
    const auto orientation = parseOrientationProperty();

    if (orientation == ui::ROTATION_0) {
        // Do nothing if the sysprop isn't set or is set to ROTATION_0.
        return;
    }

    if (orientation == ui::ROTATION_90 || orientation == ui::ROTATION_270) {
        std::swap(display.width, display.height);
        std::swap(display.initWidth, display.initHeight);
        display.surfaceControl->updateDefaultBufferSize(display.width, display.height);
    }

    Rect displayRect(0, 0, display.width, display.height);
    Rect layerStackRect(0, 0, display.width, display.height);

    SurfaceComposerClient::Transaction t;
    t.setDisplayProjection(display.displayToken, orientation, layerStackRect, displayRect);
    t.apply();
}

ui::Rotation BootAnimation::parseOrientationProperty() {
    ATRACE_CALL();
    const auto displayIds = SurfaceComposerClient::getPhysicalDisplayIds();
    if (displayIds.size() == 0) {
        return ui::ROTATION_0;
    }
    const auto displayId = displayIds[0];
    const auto syspropName = [displayId] {
        std::stringstream ss;
        ss << "ro.bootanim.set_orientation_" << displayId.value;
        return ss.str();
    }();
    auto syspropValue = android::base::GetProperty(syspropName, "");
    if (syspropValue == "") {
        syspropValue = android::base::GetProperty("ro.bootanim.set_orientation_logical_0", "");
    }

    if (syspropValue == "ORIENTATION_90") {
        return ui::ROTATION_90;
    } else if (syspropValue == "ORIENTATION_180") {
        return ui::ROTATION_180;
    } else if (syspropValue == "ORIENTATION_270") {
        return ui::ROTATION_270;
    }
    return ui::ROTATION_0;
}

void BootAnimation::projectSceneToWindow(const Display& display) {
    ATRACE_CALL();
    glViewport(0, 0, display.width, display.height);
    glScissor(0, 0, display.width, display.height);
}

void BootAnimation::resizeSurface(int newWidth, int newHeight, Display& display) {
    ATRACE_CALL();
    // We assume this function is called on the animation thread.
    if (newWidth == display.width && newHeight == display.height) {
        return;
    }

    eglMakeCurrent(mEgl, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(mEgl, display.eglSurface);

    const auto limitedSize = limitSurfaceSize(newWidth, newHeight);
    display.width = limitedSize.width;
    display.height = limitedSize.height;

    display.surfaceControl->updateDefaultBufferSize(display.width, display.height);
    EGLConfig config = getEglConfig(mEgl);
    EGLSurface eglSurface = eglCreateWindowSurface(mEgl, config, display.surface.get(), nullptr);
    if (eglMakeCurrent(mEgl, eglSurface, eglSurface, mEglContext) == EGL_FALSE) {
        SLOGE("Can't make the new eglSurface current. Error %d", eglGetError());
        return;
    }

    projectSceneToWindow(display);

    display.eglSurface = eglSurface;
}

static bool hasMbaManifest(const char* path) {
    ZipFileRO* zip = ZipFileRO::open(path);
    if (zip == nullptr) {
        return false;
    }
    ZipEntryRO entry = zip->findEntryByName("mba.txt");
    const bool found = entry != nullptr;
    if (entry) {
        zip->releaseEntry(entry);
    }
    delete zip;
    return found;
}

bool BootAnimation::preloadAnimation() {
    ATRACE_CALL();
    findBootAnimationFile();
    if (mZipFileName.empty()) {
        return false;
    }
    if (hasMbaManifest(mZipFileName.c_str())) {
        mMbaFile = true;
        return true;
    }
    mAnimation = loadAnimation(mZipFileName);
    return (mAnimation != nullptr);
}

bool BootAnimation::findBootAnimationFileInternal(const std::vector<std::string> &files) {
    ATRACE_CALL();
    for (const std::string& f : files) {
        if (access(f.c_str(), R_OK) == 0) {
            mZipFileName = f.c_str();
            return true;
        }
    }
    return false;
}

void BootAnimation::findBootAnimationFile() {
    ATRACE_CALL();
    // Pixel ABL sets ro.boot.theme=1; UiModeManager stores applied night as persist.sys.theme=2.
    const bool playDarkAnim =
            android::base::GetIntProperty("ro.boot.theme", 0) == 1 ||
            android::base::GetIntProperty("persist.sys.theme", 0) == 2;
    const std::string productOverride =
            android::base::GetProperty("ro.product.bootanim.file", "");
    std::vector<std::string> bootFiles = {
        APEX_BOOTANIMATION_MBA_FILE,
        APEX_BOOTANIMATION_FILE,
    };
    if (!productOverride.empty()) {
        bootFiles.push_back(PRODUCT_BOOTANIMATION_DIR + productOverride);
    } else {
        bootFiles.push_back(std::string(PRODUCT_BOOTANIMATION_DIR) +
                (playDarkAnim ? PRODUCT_BOOTANIMATION_DARK_MBA_FILE
                              : PRODUCT_BOOTANIMATION_MBA_FILE));
        bootFiles.push_back(std::string(PRODUCT_BOOTANIMATION_DIR) +
                (playDarkAnim ? PRODUCT_BOOTANIMATION_DARK_FILE : PRODUCT_BOOTANIMATION_FILE));
    }
    bootFiles.push_back(OEM_BOOTANIMATION_MBA_FILE);
    bootFiles.push_back(OEM_BOOTANIMATION_FILE);
    bootFiles.push_back(SYSTEM_BOOTANIMATION_MBA_FILE);
    bootFiles.push_back(SYSTEM_BOOTANIMATION_FILE);
    static const std::vector<std::string> shutdownFiles = {
        PRODUCT_SHUTDOWNANIMATION_FILE, OEM_SHUTDOWNANIMATION_FILE, SYSTEM_SHUTDOWNANIMATION_FILE, ""
    };
    static const std::vector<std::string> userspaceRebootFiles = {
        PRODUCT_USERSPACE_REBOOT_ANIMATION_FILE, OEM_USERSPACE_REBOOT_ANIMATION_FILE,
        SYSTEM_USERSPACE_REBOOT_ANIMATION_FILE,
    };

    if (android::base::GetBoolProperty("sys.init.userspace_reboot.in_progress", false)) {
        findBootAnimationFileInternal(userspaceRebootFiles);
    } else if (mShuttingDown) {
        findBootAnimationFileInternal(shutdownFiles);
    } else {
        findBootAnimationFileInternal(bootFiles);
    }
}

GLuint compileShader(GLenum shaderType, const GLchar *source) {
    ATRACE_CALL();
    GLuint shader = glCreateShader(shaderType);
    glShaderSource(shader, 1, &source, 0);
    glCompileShader(shader);
    GLint isCompiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &isCompiled);
    if (isCompiled == GL_FALSE) {
        SLOGE("Compile shader failed. Shader type: %d", shaderType);
        GLint maxLength = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &maxLength);
        std::vector<GLchar> errorLog(maxLength);
        glGetShaderInfoLog(shader, maxLength, &maxLength, &errorLog[0]);
        SLOGE("Shader compilation error: %s", &errorLog[0]);
        return 0;
    }
    return shader;
}

GLuint linkShader(GLuint vertexShader, GLuint fragmentShader) {
    ATRACE_CALL();
    GLuint program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);
    GLint isLinked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, (int *)&isLinked);
    if (isLinked == GL_FALSE) {
        SLOGE("Linking shader failed. Shader handles: vert %d, frag %d",
            vertexShader, fragmentShader);
        return 0;
    }
    return program;
}

void BootAnimation::initShaders() {
    ATRACE_CALL();
    bool dynamicColoringEnabled = mAnimation != nullptr && mAnimation->dynamicColoringEnabled;
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, (const GLchar *)VERTEX_SHADER_SOURCE);
    GLuint imageFragmentShader =
        compileShader(GL_FRAGMENT_SHADER, dynamicColoringEnabled
            ? (const GLchar *)IMAGE_FRAG_DYNAMIC_COLORING_SHADER_SOURCE
            : (const GLchar *)IMAGE_FRAG_SHADER_SOURCE);
    GLuint textFragmentShader =
        compileShader(GL_FRAGMENT_SHADER, (const GLchar *)TEXT_FRAG_SHADER_SOURCE);

    // Initialize image shader.
    mImageShader = linkShader(vertexShader, imageFragmentShader);
    GLint positionLocation = glGetAttribLocation(mImageShader, A_POSITION);
    GLint uvLocation = glGetAttribLocation(mImageShader, A_UV);
    mImageTextureLocation = glGetUniformLocation(mImageShader, U_TEXTURE);
    mImageFadeLocation = glGetUniformLocation(mImageShader, U_FADE);
    glEnableVertexAttribArray(positionLocation);
    glVertexAttribPointer(positionLocation, 2,  GL_FLOAT, GL_FALSE, 0, quadPositions);
    glVertexAttribPointer(uvLocation, 2, GL_FLOAT, GL_FALSE, 0, quadUVs);
    glEnableVertexAttribArray(uvLocation);

    // Initialize text shader.
    mTextShader = linkShader(vertexShader, textFragmentShader);
    positionLocation = glGetAttribLocation(mTextShader, A_POSITION);
    uvLocation = glGetAttribLocation(mTextShader, A_UV);
    mTextTextureLocation = glGetUniformLocation(mTextShader, U_TEXTURE);
    mTextCropAreaLocation = glGetUniformLocation(mTextShader, U_CROP_AREA);
    glEnableVertexAttribArray(positionLocation);
    glVertexAttribPointer(positionLocation, 2,  GL_FLOAT, GL_FALSE, 0, quadPositions);
    glVertexAttribPointer(uvLocation, 2, GL_FLOAT, GL_FALSE, 0, quadUVs);
    glEnableVertexAttribArray(uvLocation);
}

bool BootAnimation::threadLoop() {
    ATRACE_CALL();
    bool result;
    initShaders();

    // We have no bootanimation file, so we use the stock android logo
    // animation.
    if (mZipFileName.empty()) {
        ALOGD("No animation file");
        result = android(mDisplays.front());
    } else if (mMbaFile) {
        result = movieMba();
    } else {
        result = movie();
    }

    mCallbacks->shutdown();
    eglMakeCurrent(mEgl, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(mEgl, mEglContext);
    for (auto& display : mDisplays) {
        eglDestroySurface(mEgl, display.eglSurface);
        display.surface.clear();
        display.surfaceControl.clear();
    }
    eglTerminate(mEgl);
    eglReleaseThread();
    IPCThreadState::self()->stopProcess();
    return result;
}

bool BootAnimation::android(const Display& display) {
    ATRACE_CALL();
    glActiveTexture(GL_TEXTURE0);

    SLOGD("%sAnimationShownTiming start time: %" PRId64 "ms", mShuttingDown ? "Shutdown" : "Boot",
            elapsedRealtime());
    initTexture(&mAndroid[0], mAssets, "images/android-logo-mask.png");
    initTexture(&mAndroid[1], mAssets, "images/android-logo-shine.png");

    mCallbacks->init({});

    // clear screen
    glDisable(GL_DITHER);
    glDisable(GL_SCISSOR_TEST);
    glUseProgram(mImageShader);

    glClearColor(0,0,0,1);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(mEgl, display.eglSurface);

    // Blend state
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const nsecs_t startTime = systemTime();
    do {
        processDisplayEvents();
        const GLint xc = (display.width  - mAndroid[0].w) / 2;
        const GLint yc = (display.height - mAndroid[0].h) / 2;
        const Rect updateRect(xc, yc, xc + mAndroid[0].w, yc + mAndroid[0].h);
        glScissor(updateRect.left, display.height - updateRect.bottom, updateRect.width(),
                  updateRect.height());

        nsecs_t now = systemTime();
        double time = now - startTime;
        float t = 4.0f * float(time / us2ns(16667)) / mAndroid[1].w;
        GLint offset = (1 - (t - floorf(t))) * mAndroid[1].w;
        GLint x = xc - offset;

        glDisable(GL_SCISSOR_TEST);
        glClear(GL_COLOR_BUFFER_BIT);

        glEnable(GL_SCISSOR_TEST);
        glDisable(GL_BLEND);
        glBindTexture(GL_TEXTURE_2D, mAndroid[1].name);
        drawTexturedQuad(x,                 yc, mAndroid[1].w, mAndroid[1].h, display);
        drawTexturedQuad(x + mAndroid[1].w, yc, mAndroid[1].w, mAndroid[1].h, display);

        glEnable(GL_BLEND);
        glBindTexture(GL_TEXTURE_2D, mAndroid[0].name);
        drawTexturedQuad(xc, yc, mAndroid[0].w, mAndroid[0].h, display);

        EGLBoolean res = eglSwapBuffers(mEgl, display.eglSurface);
        if (res == EGL_FALSE)
            break;

        // 12fps: don't animate too fast to preserve CPU
        const nsecs_t sleepTime = 83333 - ns2us(systemTime() - now);
        if (sleepTime > 0)
            usleep(sleepTime);

        checkExit();
    } while (!exitPending());

    glDeleteTextures(1, &mAndroid[0].name);
    glDeleteTextures(1, &mAndroid[1].name);
    return false;
}

void BootAnimation::checkExit() {
    ATRACE_CALL();
    // Allow SurfaceFlinger to gracefully request shutdown
    char value[PROPERTY_VALUE_MAX];
    property_get(EXIT_PROP_NAME, value, "0");
    int exitnow = atoi(value);
    if (exitnow) {
        requestExit();
    }
}

bool BootAnimation::validClock(const Animation::Part& part) {
    ATRACE_CALL();
    return part.clockPosX != TEXT_MISSING_VALUE && part.clockPosY != TEXT_MISSING_VALUE;
}

bool parseTextCoord(const char* str, int* dest) {
    ATRACE_CALL();
    if (strcmp("c", str) == 0) {
        *dest = TEXT_CENTER_VALUE;
        return true;
    }

    char* end;
    int val = (int) strtol(str, &end, 0);
    if (end == str || *end != '\0' || val == INT_MAX || val == INT_MIN) {
        return false;
    }
    *dest = val;
    return true;
}

// Parse two position coordinates. If only string is non-empty, treat it as the y value.
void parsePosition(const char* str1, const char* str2, int* x, int* y) {
    ATRACE_CALL();
    bool success = false;
    if (strlen(str1) == 0) {  // No values were specified
        // success = false
    } else if (strlen(str2) == 0) {  // we have only one value
        if (parseTextCoord(str1, y)) {
            *x = TEXT_CENTER_VALUE;
            success = true;
        }
    } else {
        if (parseTextCoord(str1, x) && parseTextCoord(str2, y)) {
            success = true;
        }
    }

    if (!success) {
        *x = TEXT_MISSING_VALUE;
        *y = TEXT_MISSING_VALUE;
    }
}

// Parse a color represented as an HTML-style 'RRGGBB' string: each pair of
// characters in str is a hex number in [0, 255], which are converted to
// floating point values in the range [0.0, 1.0] and placed in the
// corresponding elements of color.
//
// If the input string isn't valid, parseColor returns false and color is
// left unchanged.
static bool parseColor(const char str[7], float color[3]) {
    ATRACE_CALL();
    float tmpColor[3];
    for (int i = 0; i < 3; i++) {
        int val = 0;
        for (int j = 0; j < 2; j++) {
            val *= 16;
            char c = str[2*i + j];
            if      (c >= '0' && c <= '9') val += c - '0';
            else if (c >= 'A' && c <= 'F') val += (c - 'A') + 10;
            else if (c >= 'a' && c <= 'f') val += (c - 'a') + 10;
            else                           return false;
        }
        tmpColor[i] = static_cast<float>(val) / 255.0f;
    }
    memcpy(color, tmpColor, sizeof(tmpColor));
    return true;
}

// Parse a color represented as a signed decimal int string.
// E.g. "-2757722" (whose hex 2's complement is 0xFFD5EBA6).
// If the input color string is empty, set color with values in defaultColor.
static void parseColorDecimalString(const std::string& colorString,
    float color[3], float defaultColor[3]) {
    ATRACE_CALL();
    if (colorString == "") {
        memcpy(color, defaultColor, sizeof(float) * 3);
        return;
    }
    int colorInt = atoi(colorString.c_str());
    color[0] = ((float)((colorInt >> 16) & 0xFF)) / 0xFF; // r
    color[1] = ((float)((colorInt >> 8) & 0xFF)) / 0xFF; // g
    color[2] = ((float)(colorInt & 0xFF)) / 0xFF; // b
}

static bool readFile(ZipFileRO* zip, const char* name, String8& outString) {
    ATRACE_CALL();
    ZipEntryRO entry = zip->findEntryByName(name);
    SLOGE_IF(!entry, "couldn't find %s", name);
    if (!entry) {
        return false;
    }

    FileMap* entryMap = zip->createEntryFileMap(entry);
    zip->releaseEntry(entry);
    SLOGE_IF(!entryMap, "entryMap is null");
    if (!entryMap) {
        return false;
    }

    outString = String8((char const*)entryMap->getDataPtr(), entryMap->getDataLength());
    delete entryMap;
    return true;
}

// The font image should be a 96x2 array of character images.  The
// columns are the printable ASCII characters 0x20 - 0x7f.  The
// top row is regular text; the bottom row is bold.
status_t BootAnimation::initFont(Font* font, const char* fallback) {
    ATRACE_CALL();
    status_t status = NO_ERROR;

    if (font->map != nullptr) {
        glGenTextures(1, &font->texture.name);
        glBindTexture(GL_TEXTURE_2D, font->texture.name);

        status = initTexture(font->map, &font->texture.w, &font->texture.h);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    } else if (fallback != nullptr) {
        status = initTexture(&font->texture, mAssets, fallback);
    } else {
        return NO_INIT;
    }

    if (status == NO_ERROR) {
        font->char_width = font->texture.w / FONT_NUM_COLS;
        font->char_height = font->texture.h / FONT_NUM_ROWS / 2;  // There are bold and regular rows
    }

    return status;
}

void BootAnimation::drawText(const char* str, const Font& font, bool bold,
                             int* x, int* y, const Display& display) {
    ATRACE_CALL();
    glEnable(GL_BLEND);  // Allow us to draw on top of the animation
    glBindTexture(GL_TEXTURE_2D, font.texture.name);
    glUseProgram(mTextShader);
    glUniform1i(mTextTextureLocation, 0);

    const int len = strlen(str);
    const int strWidth = font.char_width * len;

    if (*x == TEXT_CENTER_VALUE) {
        *x = (display.width - strWidth) / 2;
    } else if (*x < 0) {
        *x = display.width + *x - strWidth;
    }
    if (*y == TEXT_CENTER_VALUE) {
        *y = (display.height - font.char_height) / 2;
    } else if (*y < 0) {
        *y = display.height + *y - font.char_height;
    }

    for (int i = 0; i < len; i++) {
        char c = str[i];

        if (c < FONT_BEGIN_CHAR || c > FONT_END_CHAR) {
            c = '?';
        }

        // Crop the texture to only the pixels in the current glyph
        const int charPos = (c - FONT_BEGIN_CHAR);  // Position in the list of valid characters
        const int row = charPos / FONT_NUM_COLS;
        const int col = charPos % FONT_NUM_COLS;
        // Bold fonts are expected in the second half of each row.
        float v0 = (row + (bold ? 0.5f : 0.0f)) / FONT_NUM_ROWS;
        float u0 = ((float)col) / FONT_NUM_COLS;
        float v1 = v0 + 1.0f / FONT_NUM_ROWS / 2;
        float u1 = u0 + 1.0f / FONT_NUM_COLS;
        glUniform4f(mTextCropAreaLocation, u0, v0, u1, v1);
        drawTexturedQuad(*x, *y, font.char_width, font.char_height, display);

        *x += font.char_width;
    }

    glDisable(GL_BLEND);  // Return to the animation's default behaviour
    glBindTexture(GL_TEXTURE_2D, 0);
}

// We render 12 or 24 hour time.
void BootAnimation::drawClock(const Font& font, const int xPos, const int yPos,
                              const Display& display) {
    ATRACE_CALL();
    static constexpr char TIME_FORMAT_12[] = "%l:%M";
    static constexpr char TIME_FORMAT_24[] = "%H:%M";
    static constexpr int TIME_LENGTH = 6;

    time_t rawtime;
    time(&rawtime);
    struct tm* timeInfo = localtime(&rawtime);

    char timeBuff[TIME_LENGTH];
    const char* timeFormat = mTimeFormat12Hour ? TIME_FORMAT_12 : TIME_FORMAT_24;
    size_t length = strftime(timeBuff, TIME_LENGTH, timeFormat, timeInfo);

    if (length != TIME_LENGTH - 1) {
        SLOGE("Couldn't format time; abandoning boot animation clock");
        mClockEnabled = false;
        return;
    }

    char* out = timeBuff[0] == ' ' ? &timeBuff[1] : &timeBuff[0];
    int x = xPos;
    int y = yPos;
    drawText(out, font, false, &x, &y, display);
}

void BootAnimation::drawProgress(int percent, const Font& font, const int xPos, const int yPos,
                                 const Display& display) {
    ATRACE_CALL();
    static constexpr int PERCENT_LENGTH = 5;

    char percentBuff[PERCENT_LENGTH];
    // ';' has the ascii code just after ':', and the font resource contains '%'
    // for that ascii code.
    sprintf(percentBuff, "%d;", percent);
    int x = xPos;
    int y = yPos;
    drawText(percentBuff, font, false, &x, &y, display);
}

bool BootAnimation::parseAnimationDesc(Animation& animation)  {
    ATRACE_CALL();
    String8 desString;

    if (!readFile(animation.zip, "desc.txt", desString)) {
        return false;
    }
    char const* s = desString.c_str();
    std::string dynamicColoringPartName = "";
    bool postDynamicColoring = false;

    // Parse the description file
    for (;;) {
        const char* endl = strstr(s, "\n");
        if (endl == nullptr) break;
        String8 line(s, endl - s);
        const char* l = line.c_str();
        int fps = 0;
        int width = 0;
        int height = 0;
        int count = 0;
        int pause = 0;
        int progress = 0;
        int framesToFadeCount = 0;
        int colorTransitionStart = 0;
        int colorTransitionEnd = 0;
        char path[ANIM_ENTRY_NAME_MAX];
        char color[7] = "000000"; // default to black if unspecified
        char clockPos1[TEXT_POS_LEN_MAX + 1] = "";
        char clockPos2[TEXT_POS_LEN_MAX + 1] = "";
        char dynamicColoringPartNameBuffer[ANIM_ENTRY_NAME_MAX];
        char pathType;
        // start colors default to black if unspecified
        char start_color_0[7] = "000000";
        char start_color_1[7] = "000000";
        char start_color_2[7] = "000000";
        char start_color_3[7] = "000000";

        int nextReadPos;

        if (strlen(l) == 0) {
            s = ++endl;
            continue;
        }

        int topLineNumbers = sscanf(l, "%d %d %d %d", &width, &height, &fps, &progress);
        if (topLineNumbers == 3 || topLineNumbers == 4) {
            // SLOGD("> w=%d, h=%d, fps=%d, progress=%d", width, height, fps, progress);
            animation.width = width;
            animation.height = height;
            animation.fps = fps;
            if (topLineNumbers == 4) {
              animation.progressEnabled = (progress != 0);
            } else {
              animation.progressEnabled = false;
            }
        } else if (sscanf(l, "dynamic_colors %" STRTO(ANIM_PATH_MAX) "s #%6s #%6s #%6s #%6s %d %d",
            dynamicColoringPartNameBuffer,
            start_color_0, start_color_1, start_color_2, start_color_3,
            &colorTransitionStart, &colorTransitionEnd)) {
            animation.dynamicColoringEnabled = true;
            parseColor(start_color_0, animation.startColors[0]);
            parseColor(start_color_1, animation.startColors[1]);
            parseColor(start_color_2, animation.startColors[2]);
            parseColor(start_color_3, animation.startColors[3]);
            animation.colorTransitionStart = colorTransitionStart;
            animation.colorTransitionEnd = colorTransitionEnd;
            dynamicColoringPartName = std::string(dynamicColoringPartNameBuffer);
        } else if (sscanf(l, "%c %d %d %" STRTO(ANIM_PATH_MAX) "s%n",
                          &pathType, &count, &pause, path, &nextReadPos) >= 4) {
            if (pathType == 'f') {
                sscanf(l + nextReadPos, " %d #%6s %16s %16s", &framesToFadeCount, color, clockPos1,
                       clockPos2);
            } else {
                sscanf(l + nextReadPos, " #%6s %16s %16s", color, clockPos1, clockPos2);
            }
            // SLOGD("> type=%c, count=%d, pause=%d, path=%s, framesToFadeCount=%d, color=%s, "
            //       "clockPos1=%s, clockPos2=%s",
            //       pathType, count, pause, path, framesToFadeCount, color, clockPos1, clockPos2);
            Animation::Part part;
            if (path == dynamicColoringPartName) {
                // Part is specified to use dynamic coloring.
                part.useDynamicColoring = true;
                part.postDynamicColoring = false;
                postDynamicColoring = true;
            } else {
                // Part does not use dynamic coloring.
                part.useDynamicColoring = false;
                part.postDynamicColoring =  postDynamicColoring;
            }
            part.playUntilComplete = pathType == 'c';
            part.framesToFadeCount = framesToFadeCount;
            part.count = count;
            part.pause = pause;
            part.path = path;
            part.audioData = nullptr;
            part.animation = nullptr;
            if (!parseColor(color, part.backgroundColor)) {
                SLOGE("> invalid color '#%s'", color);
                part.backgroundColor[0] = 0.0f;
                part.backgroundColor[1] = 0.0f;
                part.backgroundColor[2] = 0.0f;
            }
            parsePosition(clockPos1, clockPos2, &part.clockPosX, &part.clockPosY);
            animation.parts.add(part);
        }
        else if (strcmp(l, "$SYSTEM") == 0) {
            // SLOGD("> SYSTEM");
            Animation::Part part;
            part.playUntilComplete = false;
            part.framesToFadeCount = 0;
            part.count = 1;
            part.pause = 0;
            part.audioData = nullptr;
            part.animation = loadAnimation(String8(SYSTEM_BOOTANIMATION_FILE));
            if (part.animation != nullptr)
                animation.parts.add(part);
        }
        s = ++endl;
    }

    return true;
}

bool BootAnimation::preloadZip(Animation& animation) {
    ATRACE_CALL();
    const size_t numParts = animation.parts.size();
    void *cookie = nullptr;
    ZipFileRO* zip = animation.zip;
    if (!zip->startIteration(&cookie)) {
        return false;
    }

    ZipEntryRO entry;
    char name[ANIM_ENTRY_NAME_MAX];
    while ((entry = zip->nextEntry(cookie)) != nullptr) {
        const int foundEntryName = zip->getEntryFileName(entry, name, ANIM_ENTRY_NAME_MAX);
        if (foundEntryName > ANIM_ENTRY_NAME_MAX || foundEntryName == -1) {
            SLOGE("Error fetching entry file name");
            continue;
        }

        const std::filesystem::path entryName(name);
        const std::filesystem::path path(entryName.parent_path());
        const std::filesystem::path leaf(entryName.filename());
        if (!leaf.empty()) {
            if (entryName == CLOCK_FONT_ZIP_NAME) {
                FileMap* map = zip->createEntryFileMap(entry);
                if (map) {
                    animation.clockFont.map = map;
                }
                continue;
            }

            if (entryName == PROGRESS_FONT_ZIP_NAME) {
                FileMap* map = zip->createEntryFileMap(entry);
                if (map) {
                    animation.progressFont.map = map;
                }
                continue;
            }

            for (size_t partIdx = 0; partIdx < numParts; partIdx++) {
                if (path.string() == animation.parts[partIdx].path.c_str()) {
                    uint16_t method;
                    // supports only stored png files
                    if (zip->getEntryInfo(entry, &method, nullptr, nullptr, nullptr, nullptr,
                            nullptr, nullptr)) {
                        if (method == ZipFileRO::kCompressStored) {
                            FileMap* map = zip->createEntryFileMap(entry);
                            if (map) {
                                Animation::Part& part(animation.parts.editItemAt(partIdx));
                                if (leaf == "audio.wav") {
                                    // a part may have at most one audio file
                                    part.audioData = (uint8_t *)map->getDataPtr();
                                    part.audioLength = map->getDataLength();
                                } else if (leaf == "trim.txt") {
                                    part.trimData = String8((char const*)map->getDataPtr(),
                                                        map->getDataLength());
                                } else {
                                    Animation::Frame frame;
                                    frame.name = leaf.c_str();
                                    frame.map = map;
                                    frame.trimWidth = animation.width;
                                    frame.trimHeight = animation.height;
                                    frame.trimX = 0;
                                    frame.trimY = 0;
                                    part.frames.add(frame);
                                }
                            }
                        } else {
                            SLOGE("bootanimation.zip is compressed; must be only stored");
                        }
                    }
                }
            }
        }
    }

    // If there is trimData present, override the positioning defaults.
    for (Animation::Part& part : animation.parts) {
        const char* trimDataStr = part.trimData.c_str();
        const size_t numFramesInPart = part.frames.size();
        for (size_t frameIdxInPart = 0; frameIdxInPart < numFramesInPart; frameIdxInPart++) {
            const char* endl = strstr(trimDataStr, "\n");
            // No more trimData for this part.
            if (endl == nullptr) {
                break;
            }
            String8 line(trimDataStr, endl - trimDataStr);
            const char* lineStr = line.c_str();
            trimDataStr = ++endl;
            int width = 0, height = 0, x = 0, y = 0;
            if (sscanf(lineStr, "%dx%d+%d+%d", &width, &height, &x, &y) == 4) {
                Animation::Frame& frame(part.frames.editItemAt(frameIdxInPart));
                frame.trimWidth = width;
                frame.trimHeight = height;
                frame.trimX = x;
                frame.trimY = y;
            } else {
                SLOGE("Error parsing trim.txt, line: %s", lineStr);
                break;
            }
        }
    }

    zip->endIteration(cookie);

    return true;
}

void BootAnimation::detectNpotTextures() {
    ATRACE_CALL();
    mUseNpotTextures = false;
    String8 gl_extensions;
    const char* exts = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    if (!exts) {
        glGetError();
    } else {
        gl_extensions = exts;
        if ((gl_extensions.find("GL_ARB_texture_non_power_of_two") != -1) ||
            (gl_extensions.find("GL_OES_texture_npot") != -1)) {
            mUseNpotTextures = true;
        }
    }
}

bool BootAnimation::movie() {
    ATRACE_CALL();
    if (mAnimation == nullptr) {
        mAnimation = loadAnimation(mZipFileName);
    }

    if (mAnimation == nullptr)
        return false;

    // mCallbacks->init() may get called recursively,
    // this loop is needed to get the same results
    for (const Animation::Part& part : mAnimation->parts) {
        if (part.animation != nullptr) {
            mCallbacks->init(part.animation->parts);
        }
    }
    mCallbacks->init(mAnimation->parts);

    bool anyPartHasClock = false;
    for (size_t i=0; i < mAnimation->parts.size(); i++) {
        if(validClock(mAnimation->parts[i])) {
            anyPartHasClock = true;
            break;
        }
    }
    if (!anyPartHasClock) {
        mClockEnabled = false;
    } else if (!android::base::GetBoolProperty(CLOCK_ENABLED_PROP_NAME, false)) {
        mClockEnabled = false;
    }

    detectNpotTextures();

    mDecodeOpaqueImageTo565 = android::base::GetBoolProperty(DECODE_RGB565_PROP_NAME, false);

    // Blend required to draw time on top of animation frames.
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_DITHER);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_BLEND);

    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    bool clockFontInitialized = false;
    if (mClockEnabled) {
        clockFontInitialized =
            (initFont(&mAnimation->clockFont, CLOCK_FONT_ASSET) == NO_ERROR);
        mClockEnabled = clockFontInitialized;
    }

    initFont(&mAnimation->progressFont, PROGRESS_FONT_ASSET);

    if (mClockEnabled && !updateIsTimeAccurate()) {
        mTimeCheckThread = new TimeCheckThread(this);
        mTimeCheckThread->run("BootAnimation::TimeCheckThread", PRIORITY_NORMAL);
    }

    if (mAnimation->dynamicColoringEnabled) {
        initDynamicColors();
    }

    playAnimation(*mAnimation);

    if (mTimeCheckThread != nullptr) {
        mTimeCheckThread->requestExit();
        mTimeCheckThread = nullptr;
    }

    if (clockFontInitialized) {
        glDeleteTextures(1, &mAnimation->clockFont.texture.name);
    }

    releaseAnimation(mAnimation);
    mAnimation = nullptr;

    return false;
}

namespace {

struct MbaTex {
    GLint w = 0;
    GLint h = 0;
    GLuint name = 0;
    float uScale = 1.f;
    float vScale = 1.f;
};

struct MbaScene {
    int version = 1;
    int width = 1080;
    int height = 1920;
    int fps = 30;
    float bg[3] = {0.f, 0.f, 0.f};
    bool darkMode = false;
    bool nativeMode = false;
    int rows = 7;
    int gapX = 64;
    int gapY = 20;
    float speed = 140.f;
    bool fgOn = false;
    float fgX = 0.f;
    float fgY = 0.f;
    float fgScale = 1.f;
    int fgOrigW = 0;
    int fgOrigH = 0;
    float outroDuration = 3.2f;
    float outroX = 0.f;
    float outroY = 0.f;
    float outroColor[3] = {1.f, 1.f, 1.f};
    float marqueeFade0 = 0.f;
    float marqueeFade1 = 1.5f;
    float logoFade0 = 0.5f;
    float logoFade1 = 1.8f;
    MbaTex tile;
    MbaTex tileInk;
    MbaTex wordmark;
    MbaTex wordmarkInk;
    MbaTex foreground;
};

struct MbaCover {
    float scale;
    float animX;
    float animY;
};

static float mbaClampf(float n, float a, float b) {
    return fmaxf(a, fminf(b, n));
}

static float mbaEase(float t) {
    t = mbaClampf(t, 0.f, 1.f);
    return t * t * (3.f - 2.f * t);
}

static float mbaFadeOut(float u, float start, float end) {
    if (end <= start) {
        return u >= start ? 0.f : 1.f;
    }
    return 1.f - mbaEase((u - start) / (end - start));
}

static float mbaFadeIn(float u, float start, float end) {
    if (end <= start) {
        return u >= start ? 1.f : 0.f;
    }
    return mbaEase((u - start) / (end - start));
}

static bool mbaNear(float a, float b) {
    return fabsf(a - b) < (1.5f / 255.f);
}

static void mbaDeleteTex(MbaTex* tex) {
    if (tex->name != 0) {
        glDeleteTextures(1, &tex->name);
        tex->name = 0;
    }
}

static void mbaReleaseScene(MbaScene* scene) {
    mbaDeleteTex(&scene->tile);
    mbaDeleteTex(&scene->tileInk);
    mbaDeleteTex(&scene->wordmark);
    mbaDeleteTex(&scene->wordmarkInk);
    mbaDeleteTex(&scene->foreground);
}

static FileMap* mbaMapStored(ZipFileRO* zip, const char* name) {
    ZipEntryRO entry = zip->findEntryByName(name);
    if (!entry) {
        return nullptr;
    }
    uint16_t method = 0;
    if (!zip->getEntryInfo(entry, &method, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr) ||
            method != ZipFileRO::kCompressStored) {
        SLOGE("mba: %s must be stored", name);
        zip->releaseEntry(entry);
        return nullptr;
    }
    FileMap* map = zip->createEntryFileMap(entry);
    zip->releaseEntry(entry);
    return map;
}

static void mbaTintRgbKeepAlpha(uint8_t* pixels, int w, int h, int stride,
                                uint8_t r, uint8_t g, uint8_t b) {
    for (int y = 0; y < h; ++y) {
        uint8_t* row = pixels + y * stride;
        for (int x = 0; x < w; ++x) {
            uint8_t* p = row + x * 4;
            p[0] = r;
            p[1] = g;
            p[2] = b;
        }
    }
}

static void mbaUnpremultiply(uint8_t* pixels, int w, int h, int stride) {
    for (int y = 0; y < h; ++y) {
        uint8_t* row = pixels + y * stride;
        for (int x = 0; x < w; ++x) {
            uint8_t* p = row + x * 4;
            const int a = p[3];
            if (a == 0 || a == 255) {
                continue;
            }
            p[0] = static_cast<uint8_t>((p[0] * 255 + a / 2) / a);
            p[1] = static_cast<uint8_t>((p[1] * 255 + a / 2) / a);
            p[2] = static_cast<uint8_t>((p[2] * 255 + a / 2) / a);
        }
    }
}

static status_t mbaUpload(MbaTex* texture, bool useNpot, const AndroidBitmapInfo& info,
                          const void* pixels) {
    const int w = info.width;
    const int h = info.height;
    int tw = 1 << (31 - __builtin_clz(w > 0 ? w : 1));
    int th = 1 << (31 - __builtin_clz(h > 0 ? h : 1));
    if (tw < w) tw <<= 1;
    if (th < h) th <<= 1;

    int bpp = 0;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        bpp = 4;
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        bpp = 2;
    }
    std::vector<uint8_t> packed;
    const void* data = pixels;
    if (bpp > 0 && info.stride != w * bpp) {
        packed.resize(static_cast<size_t>(w * bpp * h));
        const uint8_t* src = static_cast<const uint8_t*>(pixels);
        uint8_t* dst = packed.data();
        const int row = w * bpp;
        for (int y = 0; y < h; ++y) {
            memcpy(dst + y * row, src + y * info.stride, row);
        }
        data = dst;
    }

    glGenTextures(1, &texture->name);
    glBindTexture(GL_TEXTURE_2D, texture->name);

    switch (info.format) {
        case ANDROID_BITMAP_FORMAT_RGBA_8888:
            if (!useNpot && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, nullptr);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA,
                        GL_UNSIGNED_BYTE, data);
                texture->uScale = static_cast<float>(w) / static_cast<float>(tw);
                texture->vScale = static_cast<float>(h) / static_cast<float>(th);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, data);
            }
            break;
        case ANDROID_BITMAP_FORMAT_RGB_565:
            if (!useNpot && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, tw, th, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, nullptr);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, data);
                texture->uScale = static_cast<float>(w) / static_cast<float>(tw);
                texture->vScale = static_cast<float>(h) / static_cast<float>(th);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, data);
            }
            break;
        default:
            SLOGE("mba: unsupported bitmap format %d", info.format);
            glDeleteTextures(1, &texture->name);
            texture->name = 0;
            return BAD_VALUE;
    }

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    texture->w = w;
    texture->h = h;
    if (glGetError() != GL_NO_ERROR) {
        SLOGE("mba: glTexImage failed for %dx%d", w, h);
        mbaDeleteTex(texture);
        return UNKNOWN_ERROR;
    }
    return NO_ERROR;
}

static bool mbaLoadPng(ZipFileRO* zip, const char* name, bool useNpot, MbaTex* tex, MbaTex* ink,
                       int maxEdge, bool premultiplyThenUnpremultiply, int* origW, int* origH) {
    FileMap* map = mbaMapStored(zip, name);
    if (!map) {
        SLOGE("mba: missing %s", name);
        return false;
    }

    int headerW = 0;
    int headerH = 0;
    AImageDecoder* headerDec = nullptr;
    AImageDecoder_createFromBuffer(map->getDataPtr(), map->getDataLength(), &headerDec);
    if (headerDec) {
        const AImageDecoderHeaderInfo* info = AImageDecoder_getHeaderInfo(headerDec);
        headerW = AImageDecoderHeaderInfo_getWidth(info);
        headerH = AImageDecoderHeaderInfo_getHeight(info);
        AImageDecoder_delete(headerDec);
    }
    if (origW) *origW = headerW;
    if (origH) *origH = headerH;

    int targetW = 0;
    int targetH = 0;
    if (maxEdge > 0 && headerW > 0 && headerH > 0) {
        const int longEdge = headerW > headerH ? headerW : headerH;
        if (longEdge > maxEdge) {
            const float s = static_cast<float>(maxEdge) / static_cast<float>(longEdge);
            targetW = static_cast<int>(headerW * s + 0.5f);
            targetH = static_cast<int>(headerH * s + 0.5f);
            if (targetW < 1) targetW = 1;
            if (targetH < 1) targetH = 1;
        }
    }

    AndroidBitmapInfo info;
    // Scaled decode cannot request unpremultiplied output; undo premultiply on CPU.
    void* pixels = decodeImage(map->getDataPtr(), map->getDataLength(), &info,
            premultiplyThenUnpremultiply, false /* rgb565 */, targetW, targetH);
    delete map;
    if (!pixels) {
        SLOGE("mba: failed to decode %s", name);
        return false;
    }
    if (targetW > 0 && info.width > maxEdge) {
        SLOGE("mba: %s too large to upload (%dx%d)", name, info.width, info.height);
        free(pixels);
        return false;
    }
    if (origW && *origW <= 0) *origW = info.width;
    if (origH && *origH <= 0) *origH = info.height;
    if (premultiplyThenUnpremultiply && info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        mbaUnpremultiply(static_cast<uint8_t*>(pixels), info.width, info.height, info.stride);
    }
    if (mbaUpload(tex, useNpot, info, pixels) != NO_ERROR) {
        free(pixels);
        return false;
    }
    if (ink && info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        mbaTintRgbKeepAlpha(static_cast<uint8_t*>(pixels), info.width, info.height, info.stride,
                            0, 0, 0);
        if (mbaUpload(ink, useNpot, info, pixels) != NO_ERROR) {
            free(pixels);
            return false;
        }
    }
    free(pixels);
    return true;
}

static bool mbaParse(const char* text, MbaScene* scene) {
    const char* s = text;
    for (;;) {
        const char* endl = strstr(s, "\n");
        const char* lineEnd = endl ? endl : s + strlen(s);
        if (lineEnd == s && !endl) {
            break;
        }
        while (lineEnd > s && (lineEnd[-1] == '\r' || lineEnd[-1] == ' ')) {
            --lineEnd;
        }
        String8 line(s, lineEnd - s);
        const char* l = line.c_str();
        if (endl) {
            s = endl + 1;
        } else {
            s = lineEnd;
        }
        if (l[0] == '\0' || l[0] == '#') {
            if (!endl) break;
            continue;
        }
        char tag[16] = {};
        if (sscanf(l, "%15s", tag) != 1) {
            if (!endl) break;
            continue;
        }
        if (!strcmp(tag, "mba")) {
            sscanf(l, "mba %d", &scene->version);
        } else if (!strcmp(tag, "size")) {
            sscanf(l, "size %d %d %d", &scene->width, &scene->height, &scene->fps);
        } else if (!strcmp(tag, "bg")) {
            char hex[8] = {};
            sscanf(l, "bg %7s", hex);
            const char* p = hex[0] == '#' ? hex + 1 : hex;
            char rgb[7] = "000000";
            if (strlen(p) >= 6) {
                memcpy(rgb, p, 6);
            }
            parseColor(rgb, scene->bg);
        } else if (!strcmp(tag, "flags")) {
            int dark = 0;
            int native = 0;
            sscanf(l, "flags %d %d", &dark, &native);
            scene->darkMode = dark == 1;
            scene->nativeMode = native == 1;
        } else if (!strcmp(tag, "loop")) {
            sscanf(l, "loop %d %d %d %f",
                    &scene->rows, &scene->gapX, &scene->gapY, &scene->speed);
        } else if (!strcmp(tag, "fg")) {
            int on = 0;
            if (sscanf(l, "fg %d %f %f %f", &on, &scene->fgX, &scene->fgY, &scene->fgScale) >= 1) {
                scene->fgOn = on == 1;
            }
        } else if (!strcmp(tag, "outro")) {
            char hex[8] = {};
            sscanf(l, "outro %f %f %f %7s %f %f %f %f",
                    &scene->outroDuration, &scene->outroX, &scene->outroY, hex,
                    &scene->marqueeFade0, &scene->marqueeFade1,
                    &scene->logoFade0, &scene->logoFade1);
            const char* p = hex[0] == '#' ? hex + 1 : hex;
            char rgb[7] = "000000";
            if (strlen(p) >= 6) {
                memcpy(rgb, p, 6);
            }
            parseColor(rgb, scene->outroColor);
        }
        if (!endl) {
            break;
        }
    }
    if (scene->fps <= 0) scene->fps = 30;
    if (scene->rows < 1) scene->rows = 1;
    if (scene->rows > 24) scene->rows = 24;
    if (scene->gapX < 0) scene->gapX = 0;
    if (scene->gapY < 0) scene->gapY = 0;
    scene->speed = mbaClampf(scene->speed, 10.f, 600.f);
    scene->width = static_cast<int>(mbaClampf(scene->width, 160, 3840));
    scene->height = static_cast<int>(mbaClampf(scene->height, 160, 3840));
    if (scene->outroDuration <= 0.f) scene->outroDuration = 3.2f;
    if (scene->fgScale <= 0.f) scene->fgScale = 1.f;
    return scene->width > 0 && scene->height > 0;
}

}  // namespace

bool BootAnimation::movieMba() {
    ATRACE_CALL();
    ZipFileRO* zip = ZipFileRO::open(mZipFileName.c_str());
    if (zip == nullptr) {
        SLOGE("Failed to open mba \"%s\": %s", mZipFileName.c_str(), strerror(errno));
        return false;
    }

    String8 manifest;
    if (!readFile(zip, "mba.txt", manifest)) {
        delete zip;
        return false;
    }

    MbaScene scene;
    if (!mbaParse(manifest.c_str(), &scene)) {
        SLOGE("mba: invalid mba.txt");
        delete zip;
        return false;
    }

    detectNpotTextures();
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_DITHER);
    glDisable(GL_SCISSOR_TEST);
    glEnable(GL_BLEND);
    glUseProgram(mImageShader);
    glUniform1i(mImageTextureLocation, 0);

    GLint maxTex = 2048;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTex);
    if (maxTex < 64) {
        maxTex = 2048;
    }
    // Keep the decoded foreground under a fixed edge so 5k art fits GLES2.
    const int fgEdge = maxTex < 2048 ? maxTex : 2048;

    if (!mbaLoadPng(zip, "tile.png", mUseNpotTextures, &scene.tile, &scene.tileInk,
                    0, false, nullptr, nullptr) ||
            !mbaLoadPng(zip, "wordmark.png", mUseNpotTextures, &scene.wordmark, &scene.wordmarkInk,
                    0, false, nullptr, nullptr)) {
        mbaReleaseScene(&scene);
        delete zip;
        return false;
    }
    if (scene.fgOn) {
        if (!mbaLoadPng(zip, "foreground.png", mUseNpotTextures, &scene.foreground, nullptr,
                        fgEdge, true, &scene.fgOrigW, &scene.fgOrigH)) {
            SLOGW("mba: foreground.png skipped");
            scene.fgOn = false;
        }
    }
    delete zip;

    mCallbacks->init({});

    const MbaTex& marquee = scene.darkMode && scene.tileInk.name ? scene.tileInk : scene.tile;
    const MbaTex* logo = &scene.wordmark;
    if (mbaNear(scene.outroColor[0], 0.f) && mbaNear(scene.outroColor[1], 0.f) &&
            mbaNear(scene.outroColor[2], 0.f) && scene.wordmarkInk.name) {
        logo = &scene.wordmarkInk;
    }

    const float period = fmaxf(1.f, static_cast<float>(marquee.w + scene.gapX));
    int loopFrames = static_cast<int>(lroundf(
            (period / fmaxf(1.f, scene.speed)) * static_cast<float>(scene.fps)));
    if (loopFrames < 2) loopFrames = 2;
    const float loopT = static_cast<float>(loopFrames) / static_cast<float>(scene.fps);
    const nsecs_t frameDuration = s2ns(1) / scene.fps;

    SLOGD("%sAnimationShownTiming start time: %" PRId64 "ms (mba v%d %dx%d %dfps)",
            mShuttingDown ? "Shutdown" : "Boot", elapsedRealtime(),
            scene.version, scene.width, scene.height, scene.fps);

    auto drawSprite = [&](const MbaTex& tex, float x, float y, float w, float h, float opacity,
                          const Display& display, const MbaCover& cover) {
        if (tex.name == 0 || opacity <= 0.001f || w <= 0.f || h <= 0.f) {
            return;
        }
        const float dw = w * cover.scale;
        const float dh = h * cover.scale;
        const float dx = cover.animX + x * cover.scale;
        const float dyTop = cover.animY + y * cover.scale;
        const float dy = display.height - (dyTop + dh);
        GLfloat saved[12];
        memcpy(saved, quadUVs, sizeof(quadUVs));
        const float us = tex.uScale;
        const float vs = tex.vScale;
        quadUVs[0] = 0.f;  quadUVs[1] = vs;
        quadUVs[2] = us;   quadUVs[3] = vs;
        quadUVs[4] = us;   quadUVs[5] = 0.f;
        quadUVs[6] = us;   quadUVs[7] = 0.f;
        quadUVs[8] = 0.f;  quadUVs[9] = 0.f;
        quadUVs[10] = 0.f; quadUVs[11] = vs;
        glBindTexture(GL_TEXTURE_2D, tex.name);
        glUniform1f(mImageFadeLocation, 1.f - opacity);
        drawTexturedQuad(dx, dy, dw, dh, display);
        memcpy(quadUVs, saved, sizeof(quadUVs));
    };

    auto drawScene = [&](float offset, float marqueeA, float logoA) {
        const float pitch = static_cast<float>(marquee.h + scene.gapY);
        const float blockH = scene.rows * marquee.h +
                fmaxf(0.f, static_cast<float>(scene.rows - 1)) * scene.gapY;
        const float startY = roundf((scene.height - blockH) / 2.f);
        for (const auto& display : mDisplays) {
            eglMakeCurrent(mEgl, display.eglSurface, display.eglSurface, mEglContext);
            MbaCover cover;
            cover.scale = fmaxf(static_cast<float>(display.width) / scene.width,
                                static_cast<float>(display.height) / scene.height);
            cover.animX = (display.width - scene.width * cover.scale) / 2.f;
            cover.animY = (display.height - scene.height * cover.scale) / 2.f;
            projectSceneToWindow(display);
            handleViewport(frameDuration, display);
            glClearColor(scene.bg[0], scene.bg[1], scene.bg[2], 1.f);
            glClear(GL_COLOR_BUFFER_BIT);

            if (marqueeA > 0.001f) {
                float s = fmodf(offset, period);
                if (s < 0.f) s += period;
                for (int i = 0; i < scene.rows; ++i) {
                    const float y = startY + i * pitch;
                    if (y + marquee.h < 0.f || y > scene.height) {
                        continue;
                    }
                    const bool goingLeft = (i % 2) == 0;
                    const float start = goingLeft ? -s : s - period;
                    for (float x = start; x < scene.width; x += period) {
                        drawSprite(marquee, x, y, marquee.w, marquee.h, marqueeA, display, cover);
                    }
                }
                if (scene.fgOn && scene.foreground.name) {
                    const float dw = scene.fgOrigW * scene.fgScale;
                    const float dh = scene.fgOrigH * scene.fgScale;
                    drawSprite(scene.foreground, scene.fgX - dw / 2.f, scene.fgY - dh / 2.f,
                               dw, dh, marqueeA, display, cover);
                }
            }
            if (logoA > 0.001f && logo->name) {
                const float maxW = scene.width * 0.82f;
                const float ls = fminf(1.f, maxW / fmaxf(1.f, static_cast<float>(logo->w)));
                const float dw = logo->w * ls;
                const float dh = logo->h * ls;
                drawSprite(*logo, scene.outroX - dw / 2.f, scene.outroY - dh / 2.f,
                           dw, dh, logoA, display, cover);
            }
            eglSwapBuffers(mEgl, display.eglSurface);
        }
    };

    enum Phase { kLoop, kOutro };
    Phase phase = kLoop;
    float outroStartOffset = 0.f;
    const nsecs_t loopStart = systemTime();
    nsecs_t outroAnchor = loopStart;

    for (;;) {
        processDisplayEvents();
        checkExit();
        const nsecs_t now = systemTime();
        float marqueeA = 1.f;
        float logoA = 0.f;
        float offset = 0.f;

        if (phase == kLoop) {
            const float elapsed = static_cast<float>(now - loopStart) / 1e9f;
            float localT = fmodf(elapsed, loopT);
            if (localT < 0.f) localT += loopT;
            offset = (localT / loopT) * period;
            if (exitPending()) {
                outroStartOffset = scene.nativeMode ? offset : 0.f;
                phase = kOutro;
                outroAnchor = now;
            }
        }
        if (phase == kOutro) {
            const float u = static_cast<float>(now - outroAnchor) / 1e9f;
            if (u >= scene.outroDuration) {
                break;
            }
            marqueeA = mbaFadeOut(u, scene.marqueeFade0, scene.marqueeFade1);
            logoA = mbaFadeIn(u, scene.logoFade0, scene.logoFade1);
            if (scene.nativeMode) {
                float o = fmodf(outroStartOffset + (u / loopT) * period, period);
                if (o < 0.f) o += period;
                offset = o;
            } else {
                offset = fmodf((u / loopT) * period, period);
                if (offset < 0.f) offset += period;
            }
        }

        drawScene(offset, marqueeA, logoA);

        const nsecs_t after = systemTime();
        const nsecs_t delay = frameDuration - (after - now);
        if (delay > 0) {
            struct timespec spec;
            spec.tv_sec = (after + delay) / 1000000000;
            spec.tv_nsec = (after + delay) % 1000000000;
            int err;
            do {
                err = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &spec, nullptr);
            } while (err == EINTR);
        }
    }

    mbaReleaseScene(&scene);
    ALOGD("%sAnimationShownTiming End time: %" PRId64 "ms", mShuttingDown ? "Shutdown" : "Boot",
            elapsedRealtime());
    return false;
}

bool BootAnimation::shouldStopPlayingPart(const Animation::Part& part,
                                          const int fadedFramesCount,
                                          const int lastDisplayedProgress) {
    ATRACE_CALL();
    // stop playing only if it is time to exit and it's a partial part which has been faded out
    return exitPending() && !part.playUntilComplete && fadedFramesCount >= part.framesToFadeCount &&
        (lastDisplayedProgress == 0 || lastDisplayedProgress == 100);
}

// Linear mapping from range <a1, a2> to range <b1, b2>
float mapLinear(float x, float a1, float a2, float b1, float b2) {
    return b1 + ( x - a1 ) * ( b2 - b1 ) / ( a2 - a1 );
}

void BootAnimation::drawTexturedQuad(float xStart, float yStart,
                                     float width, float height,
                                     const Display& display) {
    ATRACE_CALL();
    // Map coordinates from screen space to world space.
    float x0 = mapLinear(xStart, 0, display.width, -1, 1);
    float y0 = mapLinear(yStart, 0, display.height, -1, 1);
    float x1 = mapLinear(xStart + width, 0, display.width, -1, 1);
    float y1 = mapLinear(yStart + height, 0, display.height, -1, 1);
    // Update quad vertex positions.
    quadPositions[0] = x0;
    quadPositions[1] = y0;
    quadPositions[2] = x1;
    quadPositions[3] = y0;
    quadPositions[4] = x1;
    quadPositions[5] = y1;
    quadPositions[6] = x1;
    quadPositions[7] = y1;
    quadPositions[8] = x0;
    quadPositions[9] = y1;
    quadPositions[10] = x0;
    quadPositions[11] = y0;
    glDrawArrays(GL_TRIANGLES, 0,
        sizeof(quadPositions) / sizeof(quadPositions[0]) / 2);
}

void BootAnimation::initDynamicColors() {
    ATRACE_CALL();
    for (int i = 0; i < DYNAMIC_COLOR_COUNT; i++) {
        const auto syspropName = "persist.bootanim.color" + std::to_string(i + 1);
        const auto syspropValue = android::base::GetProperty(syspropName, "");
        if (syspropValue != "") {
            SLOGI("Loaded dynamic color: %s -> %s", syspropName.c_str(), syspropValue.c_str());
            mDynamicColorsApplied = true;
        }
        parseColorDecimalString(syspropValue,
            mAnimation->endColors[i], mAnimation->startColors[i]);
    }
    glUseProgram(mImageShader);
    SLOGI("Dynamically coloring boot animation. Sysprops loaded? %i", mDynamicColorsApplied);
    for (int i = 0; i < DYNAMIC_COLOR_COUNT; i++) {
        float *startColor = mAnimation->startColors[i];
        float *endColor = mAnimation->endColors[i];
        glUniform3f(glGetUniformLocation(mImageShader,
            (U_START_COLOR_PREFIX + std::to_string(i)).c_str()),
            startColor[0], startColor[1], startColor[2]);
        glUniform3f(glGetUniformLocation(mImageShader,
            (U_END_COLOR_PREFIX + std::to_string(i)).c_str()),
            endColor[0], endColor[1], endColor[2]);
    }
    mImageColorProgressLocation = glGetUniformLocation(mImageShader, U_COLOR_PROGRESS);
}

bool BootAnimation::playAnimation(const Animation& animation) {
    ATRACE_CALL();
    const size_t numParts = animation.parts.size();
    nsecs_t frameDuration = s2ns(1) / animation.fps;

    SLOGD("%sAnimationShownTiming start time: %" PRId64 "ms", mShuttingDown ? "Shutdown" : "Boot",
            elapsedRealtime());

    int fadedFramesCount = 0;
    int lastDisplayedProgress = 0;
    int colorTransitionStart = animation.colorTransitionStart;
    int colorTransitionEnd = animation.colorTransitionEnd;
    for (size_t partIdx = 0; partIdx < numParts; partIdx++) {
        const Animation::Part& part(animation.parts[partIdx]);
        const size_t numFramesInPart = part.frames.size();
        glBindTexture(GL_TEXTURE_2D, 0);

        // Handle animation package
        if (part.animation != nullptr) {
            playAnimation(*part.animation);
            if (exitPending())
                break;
            continue; //to next part
        }

        // process the part not only while the count allows but also if already fading
        for (int frameIdx = 0;
             !part.count || frameIdx < part.count || fadedFramesCount > 0;
             frameIdx++) {
            if (shouldStopPlayingPart(part, fadedFramesCount, lastDisplayedProgress)) break;

            // It's possible that the sysprops were not loaded yet at this boot phase.
            // If that's the case, then we should keep trying until they are available.
            if (animation.dynamicColoringEnabled && !mDynamicColorsApplied
                && (part.useDynamicColoring || part.postDynamicColoring)) {
                SLOGD("Trying to load dynamic color sysprops.");
                initDynamicColors();
                if (mDynamicColorsApplied) {
                    // Sysprops were loaded. Next step is to adjust the animation if we loaded
                    // the colors after the animation should have started.
                    const int transitionLength = colorTransitionEnd - colorTransitionStart;
                    if (part.postDynamicColoring) {
                        colorTransitionStart = 0;
                        colorTransitionEnd = fmin(transitionLength, numFramesInPart - 1);
                    }
                }
            }

            mCallbacks->playPart(partIdx, part, frameIdx);

            glClearColor(
                    part.backgroundColor[0],
                    part.backgroundColor[1],
                    part.backgroundColor[2],
                    1.0f);

            ALOGD("Playing files = %s/%s, Requested repeat = %d, playUntilComplete = %s",
                    animation.fileName.c_str(), part.path.c_str(), part.count,
                    part.playUntilComplete ? "true" : "false");

            // For the last animation, if we have progress indicator from
            // the system, display it.
            const bool displayProgress = animation.progressEnabled && (partIdx == (numParts - 1)) &&
                    android::base::GetIntProperty(PROGRESS_PROP_NAME, 0) != 0;

            for (size_t frameIdxInPart = 0; frameIdxInPart < numFramesInPart; frameIdxInPart++) {
                if (shouldStopPlayingPart(part, fadedFramesCount, lastDisplayedProgress)) break;

                // Color progress is
                // - the animation progress, normalized from
                //   [colorTransitionStart,colorTransitionEnd] to [0, 1] for the dynamic coloring
                //   part.
                // - 0 for parts that come before,
                // - 1 for parts that come after.
                float colorProgress = part.useDynamicColoring
                    ? fmin(fmax(
                        (static_cast<float>(frameIdxInPart) - colorTransitionStart) /
                            fmax(colorTransitionEnd - colorTransitionStart, 1.0f), 0.0f), 1.0f)
                    : (part.postDynamicColoring ? 1 : 0);
                processDisplayEvents();

                const Animation::Frame& frame(part.frames[frameIdxInPart]);
                nsecs_t lastFrame = systemTime();

                if (frameIdx > 0) {
                    glBindTexture(GL_TEXTURE_2D, frame.tid);
                } else {
                    if (part.count != 1) {
                        glGenTextures(1, &frame.tid);
                        glBindTexture(GL_TEXTURE_2D, frame.tid);
                    }
                    int w, h;
                    // Set decoding option to alpha unpremultiplied so that the R, G, B channels
                    // of transparent pixels are preserved.
                    initTexture(frame.map, &w, &h, false /* don't premultiply alpha */);
                }

                float fade = 0;
                // if the part hasn't been stopped yet then continue fading if necessary
                if (exitPending() && part.hasFadingPhase()) {
                    fade = static_cast<float>(++fadedFramesCount) / part.framesToFadeCount;
                    if (fadedFramesCount >= part.framesToFadeCount) {
                        fadedFramesCount = MAX_FADED_FRAMES_COUNT; // no more fading
                    }
                }

                // Draw the current frame's texture on every physical display that is enabled.
                for (const auto& display : mDisplays) {
                    eglMakeCurrent(mEgl, display.eglSurface, display.eglSurface, mEglContext);

                    // Cover the display (center-crop). Viewport clips overflow.
                    const float scale = (animation.width > 0 && animation.height > 0)
                            ? fmaxf(static_cast<float>(display.width) / animation.width,
                                    static_cast<float>(display.height) / animation.height)
                            : 1.0f;
                    const float animationX =
                            (display.width - animation.width * scale) / 2.0f;
                    const float animationY =
                            (display.height - animation.height * scale) / 2.0f;

                    const float trimWidth = frame.trimWidth * scale;
                    const float trimHeight = frame.trimHeight * scale;
                    const float trimX = frame.trimX * scale;
                    const float trimY = frame.trimY * scale;
                    const float xc = animationX + trimX;
                    const float yc = animationY + trimY;
                    projectSceneToWindow(display);
                    handleViewport(frameDuration, display);
                    glClear(GL_COLOR_BUFFER_BIT);
                    // specify the y center as ceiling((height - frame.trimHeight) / 2)
                    // which is equivalent to height - (yc + frame.trimHeight)
                    const float frameDrawY = display.height - (yc + trimHeight);

                    glUseProgram(mImageShader);
                    glUniform1i(mImageTextureLocation, 0);
                    glUniform1f(mImageFadeLocation, fade);
                    if (animation.dynamicColoringEnabled) {
                        glUniform1f(mImageColorProgressLocation, colorProgress);
                    }
                    glEnable(GL_BLEND);
                    drawTexturedQuad(xc, frameDrawY, trimWidth, trimHeight, display);
                    glDisable(GL_BLEND);

                    if (mClockEnabled && mTimeIsAccurate && validClock(part)) {
                        drawClock(animation.clockFont, part.clockPosX, part.clockPosY, display);
                    }

                    if (displayProgress) {
                        int newProgress = android::base::GetIntProperty(PROGRESS_PROP_NAME, 0);
                        // In case the new progress jumped suddenly, still show an
                        // increment of 1.
                        if (lastDisplayedProgress != 100) {
                          // Artificially sleep 1/10th a second to slow down the animation.
                          usleep(100000);
                          if (lastDisplayedProgress < newProgress) {
                            lastDisplayedProgress++;
                          }
                        }
                        // Put the progress percentage at 1/3 of the scaled animation height.
                        int posY = static_cast<int>(
                                animationY + animation.height * scale / 3.0f);
                        if (posY < 0) {
                            posY = 0;
                        }
                        int posX = TEXT_CENTER_VALUE;
                        drawProgress(lastDisplayedProgress,
                            animation.progressFont, posX, posY, display);
                    }

                    eglSwapBuffers(mEgl, display.eglSurface);
                }


                nsecs_t now = systemTime();
                nsecs_t delay = frameDuration - (now - lastFrame);
                //SLOGD("%lld, %lld", ns2ms(now - lastFrame), ns2ms(delay));
                lastFrame = now;

                if (delay > 0) {
                    struct timespec spec;
                    spec.tv_sec  = (now + delay) / 1000000000;
                    spec.tv_nsec = (now + delay) % 1000000000;
                    int err;
                    do {
                        err = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &spec, nullptr);
                    } while (err == EINTR);
                }

                checkExit();
            }

            int pauseDuration = part.pause * ns2us(frameDuration);
            while(pauseDuration > 0 && !exitPending()){
                if (pauseDuration > MAX_CHECK_EXIT_INTERVAL_US) {
                    usleep(MAX_CHECK_EXIT_INTERVAL_US);
                    pauseDuration -= MAX_CHECK_EXIT_INTERVAL_US;
                } else {
                    usleep(pauseDuration);
                    break;
                }
                checkExit();
            }

            if (exitPending() && !part.count && mCurrentInset >= mTargetInset &&
                !part.hasFadingPhase()) {
                if (lastDisplayedProgress != 0 && lastDisplayedProgress != 100) {
                    android::base::SetProperty(PROGRESS_PROP_NAME, "100");
                    continue;
                }
                break; // exit the infinite non-fading part when it has been played at least once
            }
        }
    }

    // Free textures created for looping parts now that the animation is done.
    for (const Animation::Part& part : animation.parts) {
        if (part.count != 1) {
            for (const auto& frame : part.frames) {
                glDeleteTextures(1, &frame.tid);
            }
        }
    }

    ALOGD("%sAnimationShownTiming End time: %" PRId64 "ms", mShuttingDown ? "Shutdown" : "Boot",
            elapsedRealtime());

    return true;
}

void BootAnimation::processDisplayEvents() {
    ATRACE_CALL();
    // This will poll mDisplayEventReceiver and if there are new events it'll call
    // displayEventCallback synchronously.
    mLooper->pollOnce(0);
}

void BootAnimation::handleViewport(nsecs_t timestep, const Display& display) {
    ATRACE_CALL();
    if (mShuttingDown || !display.surfaceControl || mTargetInset == 0) {
        return;
    }
    if (mTargetInset < 0) {
        // Poll the amount for the top display inset. This will return -1 until persistent properties
        // have been loaded.
        mTargetInset =
                android::base::GetIntProperty("persist.sys.displayinset.top", -1 /* default */,
                                              -1 /* min */, display.height / 2 /* max */);
    }
    if (mTargetInset <= 0) {
        return;
    }

    if (mCurrentInset < mTargetInset) {
        // After the device boots, the inset will effectively be cropped away. We animate this here.
        float fraction = static_cast<float>(mCurrentInset) / mTargetInset;
        int interpolatedInset = (cosf((fraction + 1) * M_PI) / 2.0f + 0.5f) * mTargetInset;

        SurfaceComposerClient::Transaction()
                .setCrop(display.surfaceControl,
                         Rect(0, interpolatedInset, display.width, display.height))
                .apply();
    } else {
        // At the end of the animation, we switch to the viewport that DisplayManager will apply
        // later. This changes the coordinate system, and means we must move the surface up by
        // the inset amount.
        Rect layerStackRect(0, 0,
                display.width,
                display.height - mTargetInset);
        Rect displayRect(0, mTargetInset,
                display.width,
                display.height);
        SurfaceComposerClient::Transaction t;
        t.setPosition(display.surfaceControl, 0, -mTargetInset)
                .setCrop(display.surfaceControl,
                         Rect(0, mTargetInset,
                              display.width,
                              display.height));
        t.setDisplayProjection(display.displayToken,
                ui::ROTATION_0, layerStackRect, displayRect);
        t.apply();

        mTargetInset = mCurrentInset = 0;
    }

    int delta = timestep * mTargetInset / ms2ns(200);
    mCurrentInset += delta;
}

void BootAnimation::releaseAnimation(Animation* animation) const {
    ATRACE_CALL();
    for (Vector<Animation::Part>::iterator it = animation->parts.begin(),
         e = animation->parts.end(); it != e; ++it) {
        if (it->animation)
            releaseAnimation(it->animation);
    }
    if (animation->zip)
        delete animation->zip;
    delete animation;
}

BootAnimation::Animation* BootAnimation::loadAnimation(const String8& fn) {
    ATRACE_CALL();
    if (mLoadedFiles.indexOf(fn) >= 0) {
        SLOGE("File \"%s\" is already loaded. Cyclic ref is not allowed",
            fn.c_str());
        return nullptr;
    }
    ZipFileRO *zip = ZipFileRO::open(fn.c_str());
    if (zip == nullptr) {
        SLOGE("Failed to open animation zip \"%s\": %s",
            fn.c_str(), strerror(errno));
        return nullptr;
    }

    ALOGD("%s is loaded successfully", fn.c_str());

    Animation *animation =  new Animation;
    animation->fileName = fn;
    animation->zip = zip;
    animation->clockFont.map = nullptr;
    mLoadedFiles.add(animation->fileName);

    parseAnimationDesc(*animation);
    if (!preloadZip(*animation)) {
        releaseAnimation(animation);
        return nullptr;
    }

    mLoadedFiles.remove(fn);
    return animation;
}

bool BootAnimation::updateIsTimeAccurate() {
    ATRACE_CALL();
    static constexpr long long MAX_TIME_IN_PAST =   60000LL * 60LL * 24LL * 30LL;  // 30 days
    static constexpr long long MAX_TIME_IN_FUTURE = 60000LL * 90LL;  // 90 minutes

    if (mTimeIsAccurate) {
        return true;
    }
    if (mShuttingDown) return true;
    struct stat statResult;

    if(stat(TIME_FORMAT_12_HOUR_FLAG_FILE_PATH, &statResult) == 0) {
        mTimeFormat12Hour = true;
    }

    if(stat(ACCURATE_TIME_FLAG_FILE_PATH, &statResult) == 0) {
        mTimeIsAccurate = true;
        return true;
    }

    FILE* file = fopen(LAST_TIME_CHANGED_FILE_PATH, "r");
    if (file != nullptr) {
      long long lastChangedTime = 0;
      fscanf(file, "%lld", &lastChangedTime);
      fclose(file);
      if (lastChangedTime > 0) {
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        // Match the Java timestamp format
        long long rtcNow = (now.tv_sec * 1000LL) + (now.tv_nsec / 1000000LL);
        if (ACCURATE_TIME_EPOCH < rtcNow
            && lastChangedTime > (rtcNow - MAX_TIME_IN_PAST)
            && lastChangedTime < (rtcNow + MAX_TIME_IN_FUTURE)) {
            mTimeIsAccurate = true;
        }
      }
    }

    return mTimeIsAccurate;
}

BootAnimation::TimeCheckThread::TimeCheckThread(BootAnimation* bootAnimation) : Thread(false),
    mInotifyFd(-1), mBootAnimWd(-1), mTimeWd(-1), mBootAnimation(bootAnimation) {}

BootAnimation::TimeCheckThread::~TimeCheckThread() {
    ATRACE_CALL();
    // mInotifyFd may be -1 but that's ok since we're not at risk of attempting to close a valid FD.
    close(mInotifyFd);
}

bool BootAnimation::TimeCheckThread::threadLoop() {
    ATRACE_CALL();
    bool shouldLoop = doThreadLoop() && !mBootAnimation->mTimeIsAccurate
        && mBootAnimation->mClockEnabled;
    if (!shouldLoop) {
        close(mInotifyFd);
        mInotifyFd = -1;
    }
    return shouldLoop;
}

bool BootAnimation::TimeCheckThread::doThreadLoop() {
    ATRACE_CALL();
    static constexpr int BUFF_LEN (10 * (sizeof(struct inotify_event) + NAME_MAX + 1));

    // Poll instead of doing a blocking read so the Thread can exit if requested.
    struct pollfd pfd = { mInotifyFd, POLLIN, 0 };
    ssize_t pollResult = poll(&pfd, 1, 1000);

    if (pollResult == 0) {
        return true;
    } else if (pollResult < 0) {
        SLOGE("Could not poll inotify events");
        return false;
    }

    char buff[BUFF_LEN] __attribute__ ((aligned(__alignof__(struct inotify_event))));;
    ssize_t length = read(mInotifyFd, buff, BUFF_LEN);
    if (length == 0) {
        return true;
    } else if (length < 0) {
        SLOGE("Could not read inotify events");
        return false;
    }

    const struct inotify_event *event;
    for (char* ptr = buff; ptr < buff + length; ptr += sizeof(struct inotify_event) + event->len) {
        event = (const struct inotify_event *) ptr;
        if (event->wd == mBootAnimWd && strcmp(BOOTANIM_TIME_DIR_NAME, event->name) == 0) {
            addTimeDirWatch();
        } else if (event->wd == mTimeWd && (strcmp(LAST_TIME_CHANGED_FILE_NAME, event->name) == 0
                || strcmp(ACCURATE_TIME_FLAG_FILE_NAME, event->name) == 0)) {
            return !mBootAnimation->updateIsTimeAccurate();
        }
    }

    return true;
}

void BootAnimation::TimeCheckThread::addTimeDirWatch() {
        ATRACE_CALL();
        mTimeWd = inotify_add_watch(mInotifyFd, BOOTANIM_TIME_DIR_PATH,
                IN_CLOSE_WRITE | IN_MOVED_TO | IN_ATTRIB);
        if (mTimeWd > 0) {
            // No need to watch for the time directory to be created if it already exists
            inotify_rm_watch(mInotifyFd, mBootAnimWd);
            mBootAnimWd = -1;
        }
}

status_t BootAnimation::TimeCheckThread::readyToRun() {
    ATRACE_CALL();
    mInotifyFd = inotify_init();
    if (mInotifyFd < 0) {
        SLOGE("Could not initialize inotify fd");
        return NO_INIT;
    }

    mBootAnimWd = inotify_add_watch(mInotifyFd, BOOTANIM_DATA_DIR_PATH, IN_CREATE | IN_ATTRIB);
    if (mBootAnimWd < 0) {
        close(mInotifyFd);
        mInotifyFd = -1;
        SLOGE("Could not add watch for %s: %s", BOOTANIM_DATA_DIR_PATH, strerror(errno));
        return NO_INIT;
    }

    addTimeDirWatch();

    if (mBootAnimation->updateIsTimeAccurate()) {
        close(mInotifyFd);
        mInotifyFd = -1;
        return ALREADY_EXISTS;
    }

    return NO_ERROR;
}

// ---------------------------------------------------------------------------

} // namespace android
