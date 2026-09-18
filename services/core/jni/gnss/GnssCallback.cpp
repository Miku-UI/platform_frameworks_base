/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "GnssCallbackJni"

#include "GnssCallback.h"

#include <hardware_legacy/power.h>

#define WAKE_LOCK_NAME "GPS"

namespace android::gnss {

using android::hardware::gnss::V1_0::GnssLocationFlags;
using binder::Status;
using hardware::hidl_vec;
using hardware::Return;
using hardware::Void;

using ElapsedRealtime = android::hardware::gnss::ElapsedRealtime;
using GnssLocationAidl = android::hardware::gnss::GnssLocation;
using GnssSignalType = android::hardware::gnss::GnssSignalType;
using GnssLocation_V1_0 = android::hardware::gnss::V1_0::GnssLocation;
using GnssLocation_V2_0 = android::hardware::gnss::V2_0::GnssLocation;
using IGnssCallbackAidl = android::hardware::gnss::IGnssCallback;
using IGnssCallback_V1_0 = android::hardware::gnss::V1_0::IGnssCallback;
using IGnssCallback_V2_0 = android::hardware::gnss::V2_0::IGnssCallback;
using IGnssCallback_V2_1 = android::hardware::gnss::V2_1::IGnssCallback;

jmethodID method_reportGnssServiceDied;

namespace {

jclass class_arrayList;
jclass class_gnssSignalType;
jclass class_string;

jmethodID method_arrayListAdd;
jmethodID method_arrayListCtor;
jmethodID method_gnssSignalTypeCreate;
jmethodID method_reportLocation;
jmethodID method_reportStatus;
jmethodID method_reportSvStatus;
jmethodID method_reportNmea;
jmethodID method_setTopHalCapabilities;
jmethodID method_setSignalTypeCapabilities;
jmethodID method_setGnssYearOfHardware;
jmethodID method_setGnssHardwareModelName;
jmethodID method_requestLocation;
jmethodID method_requestUtcTime;

// Returns true if location has lat/long information.
inline bool hasLatLong(const GnssLocationAidl& location) {
    return (location.gnssLocationFlags & hardware::gnss::GnssLocation::HAS_LAT_LONG) != 0;
}

// Returns true if location has lat/long information.
inline bool hasLatLong(const GnssLocation_V1_0& location) {
    return (static_cast<uint32_t>(location.gnssLocationFlags) & GnssLocationFlags::HAS_LAT_LONG) !=
            0;
}

// Returns true if location has lat/long information.
inline bool hasLatLong(const GnssLocation_V2_0& location) {
    return hasLatLong(location.v1_0);
}

inline jboolean boolToJbool(bool value) {
    return value ? JNI_TRUE : JNI_FALSE;
}

// SFINAE helpers to detect if a type has specific members
template <typename, typename = void>
struct has_signalType : std::false_type {};
template <typename T>
struct has_signalType<T, std::void_t<decltype(T::signalType)>> : std::true_type {};

template <typename, typename = void>
struct has_elapsedRealtime : std::false_type {};
template <typename T>
struct has_elapsedRealtime<T, std::void_t<decltype(T::elapsedRealtime)>> : std::true_type {};

// These flags must be kept in sync with their counterparts in
// frameworks/base/location/java/android/location/GnssStatus.java
namespace SvidFlags {
constexpr uint32_t HAS_BASEBAND_CN0 = (1 << 4);
constexpr uint32_t HAS_CODE_TYPE = (1 << 5);
constexpr uint32_t HAS_ELAPSED_REALTIME_NANOS = (1 << 6);
constexpr uint32_t HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS = (1 << 7);
} // namespace SvidFlags

} // anonymous namespace

bool isSvStatusRegistered = false;
bool isNmeaRegistered = false;

void Gnss_class_init_once(JNIEnv* env, jclass& clazz) {
    method_reportLocation =
            env->GetMethodID(clazz, "reportLocation", "(ZLandroid/location/Location;)V");
    method_reportStatus = env->GetMethodID(clazz, "reportStatus", "(I)V");
    method_reportSvStatus = env->GetMethodID(clazz, "reportSvStatus",
                                             "(I[I[I[I[F[F[F[F[F[Ljava/lang/String;[J[D)V");
    method_reportNmea = env->GetMethodID(clazz, "reportNmea", "(J)V");

    method_setTopHalCapabilities = env->GetMethodID(clazz, "setTopHalCapabilities", "(IZ)V");
    method_setSignalTypeCapabilities =
            env->GetMethodID(clazz, "setSignalTypeCapabilities", "(Ljava/util/List;)V");
    method_setGnssYearOfHardware = env->GetMethodID(clazz, "setGnssYearOfHardware", "(I)V");
    method_setGnssHardwareModelName =
            env->GetMethodID(clazz, "setGnssHardwareModelName", "(Ljava/lang/String;)V");

    method_requestLocation = env->GetMethodID(clazz, "requestLocation", "(ZZ)V");
    method_requestUtcTime = env->GetMethodID(clazz, "requestUtcTime", "()V");
    method_reportGnssServiceDied = env->GetMethodID(clazz, "reportGnssServiceDied", "()V");

    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    class_arrayList = (jclass)env->NewGlobalRef(arrayListClass);
    method_arrayListCtor = env->GetMethodID(class_arrayList, "<init>", "()V");
    method_arrayListAdd = env->GetMethodID(class_arrayList, "add", "(Ljava/lang/Object;)Z");

    jclass gnssSignalTypeClass = env->FindClass("android/location/GnssSignalType");
    class_gnssSignalType = (jclass)env->NewGlobalRef(gnssSignalTypeClass);
    method_gnssSignalTypeCreate =
            env->GetStaticMethodID(class_gnssSignalType, "create",
                                   "(IDLjava/lang/String;)Landroid/location/GnssSignalType;");

    jclass stringClass = env->FindClass("java/lang/String");
    class_string = (jclass)env->NewGlobalRef(stringClass);
}

Status GnssCallbackAidl::gnssSetCapabilitiesCb(const int capabilities) {
    ALOGD("%s: %du\n", __func__, capabilities);
    bool isAdrCapabilityKnown = (interfaceVersion >= 3) ? true : false;
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_setTopHalCapabilities, capabilities,
                        isAdrCapabilityKnown);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Status::ok();
}

namespace {

jobject translateSingleSignalType(JNIEnv* env, const GnssSignalType& signalType) {
    jstring jstringCodeType = env->NewStringUTF(signalType.codeType.c_str());
    jobject signalTypeObject =
            env->CallStaticObjectMethod(class_gnssSignalType, method_gnssSignalTypeCreate,
                                        signalType.constellation, signalType.carrierFrequencyHz,
                                        jstringCodeType);
    env->DeleteLocalRef(jstringCodeType);
    return signalTypeObject;
}

} // anonymous namespace

Status GnssCallbackAidl::gnssSetSignalTypeCapabilitiesCb(
        const std::vector<GnssSignalType>& signalTypes) {
    ALOGD("%s: %d signal types", __func__, (int)signalTypes.size());
    JNIEnv* env = getJniEnv();
    jobject arrayList = env->NewObject(class_arrayList, method_arrayListCtor);
    for (auto& signalType : signalTypes) {
        jobject signalTypeObject = translateSingleSignalType(env, signalType);
        env->CallBooleanMethod(arrayList, method_arrayListAdd, signalTypeObject);
        // Delete Local Refs
        env->DeleteLocalRef(signalTypeObject);
    }
    env->CallVoidMethod(mCallbacksObj, method_setSignalTypeCapabilities, arrayList);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    env->DeleteLocalRef(arrayList);
    return Status::ok();
}

Status GnssCallbackAidl::gnssStatusCb(const GnssStatusValue status) {
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_reportStatus, status);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Status::ok();
}

Status GnssCallbackAidl::gnssSvStatusCb(const std::vector<GnssSvInfo>& svInfoList) {
    GnssCallbackHidl::gnssSvStatusCbImpl<std::vector<GnssSvInfo>,
                                         GnssSvInfo>(svInfoList, this->interfaceVersion);
    return Status::ok();
}

Status GnssCallbackAidl::gnssLocationCb(const hardware::gnss::GnssLocation& location) {
    GnssCallbackHidl::gnssLocationCbImpl<hardware::gnss::GnssLocation>(location);
    return Status::ok();
}

Status GnssCallbackAidl::gnssNmeaCb(const int64_t timestamp, const std::string& nmea) {
    // In AIDL v1, if no listener is registered, do not report nmea to the framework.
    if (interfaceVersion <= 1) {
        if (!isNmeaRegistered) {
            return Status::ok();
        }
    }
    JNIEnv* env = getJniEnv();
    /*
     * The Java code will call back to read these values.
     * We do this to avoid creating unnecessary String objects.
     */
    GnssCallbackHidl::sNmeaString = nmea.c_str();
    GnssCallbackHidl::sNmeaStringLength = nmea.size();

    env->CallVoidMethod(mCallbacksObj, method_reportNmea, timestamp);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Status::ok();
}

Status GnssCallbackAidl::gnssAcquireWakelockCb() {
    acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
    return Status::ok();
}

Status GnssCallbackAidl::gnssReleaseWakelockCb() {
    release_wake_lock(WAKE_LOCK_NAME);
    return Status::ok();
}

Status GnssCallbackAidl::gnssSetSystemInfoCb(const GnssSystemInfo& info) {
    ALOGD("%s: yearOfHw=%d, name=%s\n", __func__, info.yearOfHw, info.name.c_str());
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_setGnssYearOfHardware, info.yearOfHw);
    jstring jstringName = env->NewStringUTF(info.name.c_str());
    env->CallVoidMethod(mCallbacksObj, method_setGnssHardwareModelName, jstringName);
    if (jstringName) {
        env->DeleteLocalRef(jstringName);
    }
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Status::ok();
}

Status GnssCallbackAidl::gnssRequestTimeCb() {
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_requestUtcTime);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Status::ok();
}

Status GnssCallbackAidl::gnssRequestLocationCb(const bool independentFromGnss,
                                               const bool isUserEmergency) {
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_requestLocation, boolToJbool(independentFromGnss),
                        boolToJbool(isUserEmergency));
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Status::ok();
}

// Implementation of IGnssCallbackHidl

Return<void> GnssCallbackHidl::gnssNameCb(const android::hardware::hidl_string& name) {
    ALOGD("%s: name=%s\n", __func__, name.c_str());

    JNIEnv* env = getJniEnv();
    jstring jstringName = env->NewStringUTF(name.c_str());
    env->CallVoidMethod(mCallbacksObj, method_setGnssHardwareModelName, jstringName);
    if (jstringName) {
        env->DeleteLocalRef(jstringName);
    }
    checkAndClearExceptionFromCallback(env, __FUNCTION__);

    return Void();
}

const char* GnssCallbackHidl::sNmeaString = nullptr;
size_t GnssCallbackHidl::sNmeaStringLength = 0;

template <class T>
Return<void> GnssCallbackHidl::gnssLocationCbImpl(const T& location) {
    JNIEnv* env = getJniEnv();

    jobject jLocation = translateGnssLocation(env, location);

    env->CallVoidMethod(mCallbacksObj, method_reportLocation, boolToJbool(hasLatLong(location)),
                        jLocation);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    env->DeleteLocalRef(jLocation);
    return Void();
}

Return<void> GnssCallbackHidl::gnssLocationCb(const GnssLocation_V1_0& location) {
    return gnssLocationCbImpl<GnssLocation_V1_0>(location);
}

Return<void> GnssCallbackHidl::gnssLocationCb_2_0(const GnssLocation_V2_0& location) {
    return gnssLocationCbImpl<GnssLocation_V2_0>(location);
}

Return<void> GnssCallbackHidl::gnssStatusCb(const IGnssCallback_V2_0::GnssStatusValue status) {
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_reportStatus, status);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Void();
}

template <>
uint32_t GnssCallbackHidl::getHasBasebandCn0DbHzFlag(
        const hidl_vec<IGnssCallback_V2_1::GnssSvInfo>& svStatus) {
    return SvidFlags::HAS_BASEBAND_CN0;
}

template <>
uint32_t GnssCallbackHidl::getHasBasebandCn0DbHzFlag(
        const std::vector<IGnssCallbackAidl::GnssSvInfo>& svStatus) {
    return SvidFlags::HAS_BASEBAND_CN0;
}

template <>
double GnssCallbackHidl::getBasebandCn0DbHz(
        const std::vector<IGnssCallbackAidl::GnssSvInfo>& svInfoList, size_t i) {
    return svInfoList[i].basebandCN0DbHz;
}

template <>
double GnssCallbackHidl::getBasebandCn0DbHz(
        const hidl_vec<IGnssCallback_V2_1::GnssSvInfo>& svInfoList, size_t i) {
    return svInfoList[i].basebandCN0DbHz;
}

template <>
uint32_t GnssCallbackHidl::getGnssSvInfoListSize(const IGnssCallback_V1_0::GnssSvStatus& svStatus) {
    return svStatus.numSvs;
}

template <>
uint32_t GnssCallbackHidl::getConstellationType(const IGnssCallback_V1_0::GnssSvStatus& svStatus,
                                                size_t i) {
    return static_cast<uint32_t>(svStatus.gnssSvList.data()[i].constellation);
}

template <>
uint32_t GnssCallbackHidl::getConstellationType(
        const hidl_vec<IGnssCallback_V2_1::GnssSvInfo>& svInfoList, size_t i) {
    return static_cast<uint32_t>(svInfoList[i].v2_0.constellation);
}

template <class T_list, class T_sv_info>
Return<void> GnssCallbackHidl::gnssSvStatusCbImpl(const T_list& svStatus, int interfaceVersion) {
    // In HIDL or AIDL, if no listener is registered, do not report svInfoList to the framework.
    if (!isSvStatusRegistered) {
        return Void();
    }

    JNIEnv* env = getJniEnv();

    uint32_t listSize = getGnssSvInfoListSize(svStatus);
    jintArray svidArray = env->NewIntArray(listSize);
    jintArray constellationTypeArray = env->NewIntArray(listSize);
    jintArray svFlagArray = env->NewIntArray(listSize);
    jfloatArray cn0Array = env->NewFloatArray(listSize);
    jfloatArray elevArray = env->NewFloatArray(listSize);
    jfloatArray azimArray = env->NewFloatArray(listSize);
    jfloatArray carrierFreqArray = env->NewFloatArray(listSize);
    jfloatArray basebandCn0Array = env->NewFloatArray(listSize);
    jobjectArray codeTypeArray = env->NewObjectArray(listSize, class_string, nullptr);
    jlongArray elapsedRealtimeArray = env->NewLongArray(listSize);
    jdoubleArray elapsedRealtimeUncertaintyArray = env->NewDoubleArray(listSize);

    jint* svids = env->GetIntArrayElements(svidArray, 0);
    jint* constellationTypes = env->GetIntArrayElements(constellationTypeArray, 0);
    jint* svFlags = env->GetIntArrayElements(svFlagArray, 0);
    jfloat* cn0s = env->GetFloatArrayElements(cn0Array, 0);
    jfloat* elev = env->GetFloatArrayElements(elevArray, 0);
    jfloat* azim = env->GetFloatArrayElements(azimArray, 0);
    jfloat* carrierFreq = env->GetFloatArrayElements(carrierFreqArray, 0);
    jfloat* basebandCn0s = env->GetFloatArrayElements(basebandCn0Array, 0);
    jlong* elapsedRealtimes = env->GetLongArrayElements(elapsedRealtimeArray, 0);
    jdouble* elapsedRealtimeUncertainties =
            env->GetDoubleArrayElements(elapsedRealtimeUncertaintyArray, 0);

    /*
     * Read GNSS SV info.
     */
    for (size_t i = 0; i < listSize; ++i) {
        const T_sv_info& info = getGnssSvInfoOfIndex(svStatus, i);
        svids[i] = info.svid;
        constellationTypes[i] = getConstellationType(svStatus, i);
        svFlags[i] = info.svFlag;
        cn0s[i] = info.cN0Dbhz;
        elev[i] = info.elevationDegrees;
        azim[i] = info.azimuthDegrees;
        carrierFreq[i] = info.carrierFrequencyHz;
        svFlags[i] |= getHasBasebandCn0DbHzFlag(svStatus);
        basebandCn0s[i] = getBasebandCn0DbHz(svStatus, i);

        // codeType and elapsedRealtime fields are added in 26Q2 (interfaceVersion=7).
        if (interfaceVersion >= 7) {
            if constexpr (has_signalType<T_sv_info>::value) {
                if (info.signalType) {
                    const GnssSignalType& signalType = info.signalType.value();
                    constellationTypes[i] = static_cast<uint32_t>(signalType.constellation);
                    carrierFreq[i] = static_cast<jfloat>(signalType.carrierFrequencyHz);
                    svFlags[i] |= SvidFlags::HAS_CODE_TYPE;
                    jstring codeType = env->NewStringUTF(signalType.codeType.c_str());
                    env->SetObjectArrayElement(codeTypeArray, i, codeType);
                    env->DeleteLocalRef(codeType);
                }
            }
            if constexpr (has_elapsedRealtime<T_sv_info>::value) {
                if (info.elapsedRealtime) {
                    const auto& elapsedRealtime = info.elapsedRealtime.value();
                    if (elapsedRealtime.flags & ElapsedRealtime::HAS_TIMESTAMP_NS) {
                        svFlags[i] |= SvidFlags::HAS_ELAPSED_REALTIME_NANOS;
                        elapsedRealtimes[i] = elapsedRealtime.timestampNs;
                    }
                    if (elapsedRealtime.flags & ElapsedRealtime::HAS_TIME_UNCERTAINTY_NS) {
                        svFlags[i] |= SvidFlags::HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS;
                        elapsedRealtimeUncertainties[i] = elapsedRealtime.timeUncertaintyNs;
                    }
                }
            }
        }
    }

    env->ReleaseIntArrayElements(svidArray, svids, 0);
    env->ReleaseIntArrayElements(constellationTypeArray, constellationTypes, 0);
    env->ReleaseIntArrayElements(svFlagArray, svFlags, 0);
    env->ReleaseFloatArrayElements(cn0Array, cn0s, 0);
    env->ReleaseFloatArrayElements(elevArray, elev, 0);
    env->ReleaseFloatArrayElements(azimArray, azim, 0);
    env->ReleaseFloatArrayElements(carrierFreqArray, carrierFreq, 0);
    env->ReleaseFloatArrayElements(basebandCn0Array, basebandCn0s, 0);
    env->ReleaseLongArrayElements(elapsedRealtimeArray, elapsedRealtimes, 0);
    env->ReleaseDoubleArrayElements(elapsedRealtimeUncertaintyArray, elapsedRealtimeUncertainties,
                                    0);

    env->CallVoidMethod(mCallbacksObj, method_reportSvStatus, static_cast<jint>(listSize),
                        svFlagArray, svidArray, constellationTypeArray, cn0Array, elevArray,
                        azimArray, carrierFreqArray, basebandCn0Array, codeTypeArray,
                        elapsedRealtimeArray, elapsedRealtimeUncertaintyArray);

    env->DeleteLocalRef(svidArray);
    env->DeleteLocalRef(constellationTypeArray);
    env->DeleteLocalRef(svFlagArray);
    env->DeleteLocalRef(cn0Array);
    env->DeleteLocalRef(elevArray);
    env->DeleteLocalRef(azimArray);
    env->DeleteLocalRef(carrierFreqArray);
    env->DeleteLocalRef(basebandCn0Array);
    env->DeleteLocalRef(codeTypeArray);
    env->DeleteLocalRef(elapsedRealtimeArray);
    env->DeleteLocalRef(elapsedRealtimeUncertaintyArray);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Void();
}

Return<void> GnssCallbackHidl::gnssNmeaCb(int64_t timestamp,
                                          const ::android::hardware::hidl_string& nmea) {
    // In HIDL, if no listener is registered, do not report nmea to the framework.
    if (!isNmeaRegistered) {
        return Void();
    }
    JNIEnv* env = getJniEnv();
    /*
     * The Java code will call back to read these values.
     * We do this to avoid creating unnecessary String objects.
     */
    sNmeaString = nmea.c_str();
    sNmeaStringLength = nmea.size();

    env->CallVoidMethod(mCallbacksObj, method_reportNmea, timestamp);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Void();
}

Return<void> GnssCallbackHidl::gnssSetCapabilitesCb(uint32_t capabilities) {
    ALOGD("%s: %du\n", __func__, capabilities);

    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_setTopHalCapabilities, capabilities,
                        /* isAdrCapabilityKnown= */ false);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Void();
}

Return<void> GnssCallbackHidl::gnssSetCapabilitiesCb_2_0(uint32_t capabilities) {
    return GnssCallbackHidl::gnssSetCapabilitesCb(capabilities);
}

Return<void> GnssCallbackHidl::gnssSetCapabilitiesCb_2_1(uint32_t capabilities) {
    return GnssCallbackHidl::gnssSetCapabilitesCb(capabilities);
}

Return<void> GnssCallbackHidl::gnssAcquireWakelockCb() {
    acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
    return Void();
}

Return<void> GnssCallbackHidl::gnssReleaseWakelockCb() {
    release_wake_lock(WAKE_LOCK_NAME);
    return Void();
}

Return<void> GnssCallbackHidl::gnssRequestTimeCb() {
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_requestUtcTime);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Void();
}

Return<void> GnssCallbackHidl::gnssRequestLocationCb(const bool independentFromGnss) {
    return GnssCallbackHidl::gnssRequestLocationCb_2_0(independentFromGnss, /* isUserEmergency= */
                                                       false);
}

Return<void> GnssCallbackHidl::gnssRequestLocationCb_2_0(const bool independentFromGnss,
                                                         const bool isUserEmergency) {
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_requestLocation, boolToJbool(independentFromGnss),
                        boolToJbool(isUserEmergency));
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Void();
}

Return<void> GnssCallbackHidl::gnssSetSystemInfoCb(const IGnssCallback_V2_0::GnssSystemInfo& info) {
    ALOGD("%s: yearOfHw=%d\n", __func__, info.yearOfHw);

    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_setGnssYearOfHardware, info.yearOfHw);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Void();
}

} // namespace android::gnss