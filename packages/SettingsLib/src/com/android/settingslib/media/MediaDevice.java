/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.settingslib.media;

import static android.media.MediaRoute2Info.TYPE_AUX_LINE;
import static android.media.MediaRoute2Info.TYPE_BLE_HEADSET;
import static android.media.MediaRoute2Info.TYPE_BLUETOOTH_A2DP;
import static android.media.MediaRoute2Info.TYPE_BUILTIN_SPEAKER;
import static android.media.MediaRoute2Info.TYPE_DOCK;
import static android.media.MediaRoute2Info.TYPE_GROUP;
import static android.media.MediaRoute2Info.TYPE_HDMI;
import static android.media.MediaRoute2Info.TYPE_HDMI_ARC;
import static android.media.MediaRoute2Info.TYPE_HDMI_EARC;
import static android.media.MediaRoute2Info.TYPE_HEARING_AID;
import static android.media.MediaRoute2Info.TYPE_LINE_ANALOG;
import static android.media.MediaRoute2Info.TYPE_LINE_DIGITAL;
import static android.media.MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_TV;
import static android.media.MediaRoute2Info.TYPE_UNKNOWN;
import static android.media.MediaRoute2Info.TYPE_USB_ACCESSORY;
import static android.media.MediaRoute2Info.TYPE_USB_DEVICE;
import static android.media.MediaRoute2Info.TYPE_USB_HEADSET;
import static android.media.MediaRoute2Info.TYPE_WIRED_HEADPHONES;
import static android.media.MediaRoute2Info.TYPE_WIRED_HEADSET;
import static android.media.RouteListingPreference.Item.FLAG_ONGOING_SESSION;
import static android.media.RouteListingPreference.Item.FLAG_ONGOING_SESSION_MANAGED;
import static android.media.RouteListingPreference.Item.FLAG_SUGGESTED;
import static android.media.RouteListingPreference.Item.SUBTEXT_AD_ROUTING_DISALLOWED;
import static android.media.RouteListingPreference.Item.SUBTEXT_CUSTOM;
import static android.media.RouteListingPreference.Item.SUBTEXT_DEVICE_LOW_POWER;
import static android.media.RouteListingPreference.Item.SUBTEXT_DOWNLOADED_CONTENT_ROUTING_DISALLOWED;
import static android.media.RouteListingPreference.Item.SUBTEXT_ERROR_UNKNOWN;
import static android.media.RouteListingPreference.Item.SUBTEXT_NONE;
import static android.media.RouteListingPreference.Item.SUBTEXT_SUBSCRIPTION_REQUIRED;
import static android.media.RouteListingPreference.Item.SUBTEXT_TRACK_UNSUPPORTED;
import static android.media.RouteListingPreference.Item.SUBTEXT_UNAUTHORIZED;

import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_SELECTED;
import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_TRANSFER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.NearbyDevice;
import android.media.RouteListingPreference;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.DoNotInline;
import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.media.flags.Flags;
import com.android.settingslib.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * MediaDevice represents a media device(such like Bluetooth device, cast device and phone device).
 */
public abstract class MediaDevice implements Comparable<MediaDevice> {
    private static final String TAG = "MediaDevice";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MediaDeviceType.TYPE_UNKNOWN,
            MediaDeviceType.TYPE_PHONE_DEVICE,
            MediaDeviceType.TYPE_USB_C_AUDIO_DEVICE,
            MediaDeviceType.TYPE_3POINT5_MM_AUDIO_DEVICE,
            MediaDeviceType.TYPE_FAST_PAIR_BLUETOOTH_DEVICE,
            MediaDeviceType.TYPE_BLUETOOTH_DEVICE,
            MediaDeviceType.TYPE_CAST_DEVICE,
            MediaDeviceType.TYPE_CAST_GROUP_DEVICE,
            MediaDeviceType.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER})
    public @interface MediaDeviceType {
        int TYPE_UNKNOWN = 0;
        int TYPE_PHONE_DEVICE = 1;
        int TYPE_USB_C_AUDIO_DEVICE = 2;
        int TYPE_3POINT5_MM_AUDIO_DEVICE = 3;
        int TYPE_FAST_PAIR_BLUETOOTH_DEVICE = 4;
        int TYPE_BLUETOOTH_DEVICE = 5;
        int TYPE_CAST_DEVICE = 6;
        int TYPE_CAST_GROUP_DEVICE = 7;
        int TYPE_REMOTE_AUDIO_VIDEO_RECEIVER = 8;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SelectionBehavior.SELECTION_BEHAVIOR_NONE,
            SelectionBehavior.SELECTION_BEHAVIOR_TRANSFER,
            SelectionBehavior.SELECTION_BEHAVIOR_GO_TO_APP
    })
    public @interface SelectionBehavior {
        int SELECTION_BEHAVIOR_NONE = 0;
        int SELECTION_BEHAVIOR_TRANSFER = 1;
        int SELECTION_BEHAVIOR_GO_TO_APP = 2;
    }

    public static final int SUGGESTION_PROVIDER_UNSPECIFIED = 0;
    public static final int SUGGESTION_PROVIDER_RLP = 1;
    public static final int SUGGESTION_PROVIDER_DEVICE_SUGGESTION_APP = 2;
    public static final int SUGGESTION_PROVIDER_DEVICE_SUGGESTION_OTHER = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                SUGGESTION_PROVIDER_UNSPECIFIED,
                SUGGESTION_PROVIDER_RLP,
                SUGGESTION_PROVIDER_DEVICE_SUGGESTION_APP,
                SUGGESTION_PROVIDER_DEVICE_SUGGESTION_OTHER
            })
    public @interface SuggestionProvider {}

    @VisibleForTesting
    int mType;

    private int mConnectedRecord;
    private int mState;
    @NearbyDevice.RangeZone
    private int mRangeZone = NearbyDevice.RANGE_UNKNOWN;

    protected final Context mContext;
    @Nullable protected final MediaRoute2Info mRouteInfo;
    @Nullable private final DynamicRouteAttributes mDynamicRouteAttributes;
    protected final RouteListingPreference.Item mRlpItem;
    private boolean mIsSuggested;
    private boolean mIsSuggestedByApp;

    MediaDevice(
            @NonNull Context context,
            @Nullable MediaRoute2Info routeInfo,
            @Nullable DynamicRouteAttributes dynamicRouteAttributes,
            @Nullable RouteListingPreference.Item rlpItem) {
        mContext = context;
        mRouteInfo = routeInfo;
        mRlpItem = rlpItem;
        mDynamicRouteAttributes = dynamicRouteAttributes;
        setType(routeInfo);
        if (Flags.enableSuggestedDeviceApi()) {
            mState = LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED;
        }
    }

    // MediaRoute2Info.getType was made public on API 34, but exists since API 30.
    @SuppressWarnings("NewApi")
    private void setType(MediaRoute2Info routeInfo) {
        if (routeInfo == null) {
            mType = MediaDeviceType.TYPE_BLUETOOTH_DEVICE;
            return;
        }
        switch (routeInfo.getType()) {
            case TYPE_GROUP:
                mType = MediaDeviceType.TYPE_CAST_GROUP_DEVICE;
                break;
            case TYPE_BUILTIN_SPEAKER:
                mType = MediaDeviceType.TYPE_PHONE_DEVICE;
                break;
            case TYPE_WIRED_HEADSET:
            case TYPE_WIRED_HEADPHONES:
            case TYPE_LINE_DIGITAL:
            case TYPE_LINE_ANALOG:
            case TYPE_AUX_LINE:
                mType = MediaDeviceType.TYPE_3POINT5_MM_AUDIO_DEVICE;
                break;
            case TYPE_USB_DEVICE:
            case TYPE_USB_HEADSET:
            case TYPE_USB_ACCESSORY:
            case TYPE_DOCK:
            case TYPE_HDMI:
            case TYPE_HDMI_ARC:
            case TYPE_HDMI_EARC:
                mType = MediaDeviceType.TYPE_USB_C_AUDIO_DEVICE;
                break;
            case TYPE_HEARING_AID:
            case TYPE_BLUETOOTH_A2DP:
            case TYPE_BLE_HEADSET:
                mType = MediaDeviceType.TYPE_BLUETOOTH_DEVICE;
                break;
            case TYPE_REMOTE_AUDIO_VIDEO_RECEIVER:
                mType = MediaDeviceType.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER;
                break;
            case TYPE_UNKNOWN:
            case TYPE_REMOTE_TV:
            case TYPE_REMOTE_SPEAKER:
            default:
                mType = MediaDeviceType.TYPE_CAST_DEVICE;
                break;
        }
    }

    void initDeviceRecord() {
        ConnectionRecordManager.getInstance().fetchLastSelectedDevice(mContext);
        mConnectedRecord = ConnectionRecordManager.getInstance().fetchConnectionRecord(mContext,
                getId());
    }

    public @NearbyDevice.RangeZone int getRangeZone() {
        return mRangeZone;
    }

    public void setRangeZone(@NearbyDevice.RangeZone int rangeZone) {
        mRangeZone = rangeZone;
    }

    /** Returns a route associated with this device. */
    @Nullable
    public MediaRoute2Info getRouteInfo() {
        return mRouteInfo;
    }

    /**
     * Get name from MediaDevice.
     *
     * @return name of MediaDevice.
     */
    public abstract String getName();

    /**
     * Get summary from MediaDevice.
     *
     * @return summary of MediaDevice.
     */
    public abstract String getSummary();

    /**
     * Get summary from MediaDevice for TV with low batter states in a different color if
     * applicable.
     *
     * @param lowBatteryColorRes Color resource for the part of the CharSequence that describes a
     *                           low battery state.
     */
    public CharSequence getSummaryForTv(int lowBatteryColorRes) {
        return getSummary();
    }

    /**
     * Get icon of MediaDevice.
     *
     * @return drawable of icon.
     */
    public abstract Drawable getIcon();

    /**
     * Get icon of MediaDevice without background.
     *
     * @return drawable of icon
     */
    public abstract Drawable getIconWithoutBackground();

    /**
     * Get unique ID that represent MediaDevice
     *
     * @return unique id of MediaDevice
     */
    public abstract String getId();

    /** Returns {@code true} if the device has a non-null {@link RouteListingPreference.Item}. */
    public boolean hasRouteListingPreferenceItem() {
        return mRlpItem != null;
    }

    /**
     * Get selection behavior of device
     *
     * @return selection behavior of device
     */
    @SelectionBehavior
    public int getSelectionBehavior() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mRlpItem != null
                ? mRlpItem.getSelectionBehavior() : SELECTION_BEHAVIOR_TRANSFER;
    }

    /**
     * Checks if device is has subtext
     *
     * @return true if device has subtext
     */
    public boolean hasSubtext() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && mRlpItem != null
                && mRlpItem.getSubText() != SUBTEXT_NONE;
    }

    /**
     * Get subtext of device
     *
     * @return subtext of device
     */
    @RouteListingPreference.Item.SubText
    public int getSubtext() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mRlpItem != null
                ? mRlpItem.getSubText() : SUBTEXT_NONE;
    }

    /**
     * Returns subtext string for current route.
     *
     * @return subtext string for this route
     */
    public String getSubtextString() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mRlpItem != null
                ? Api34Impl.composeSubtext(mRlpItem, mContext) : null;
    }

    /**
     * Checks if device has ongoing shared session, which allow user to join
     *
     * @return true if device has ongoing session
     */
    public boolean hasOngoingSession() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && Api34Impl.hasOngoingSession(mRlpItem);
    }

    /**
     * Checks if device is the host for ongoing shared session, which allow user to adjust volume
     *
     * @return true if device is the host for ongoing shared session
     */
    public boolean isHostForOngoingSession() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && Api34Impl.isHostForOngoingSession(mRlpItem);
    }

    /**
     * Checks if device is suggested device from application. A device can be suggested through
     * either {@link RouteListingPreference} or through {@link MediaRouter2#setDeviceSuggestions}.
     *
     * <p>Prioritization and conflict resolution between the two APIs is as follows: - Suggestions
     * from both RLP and the new API will be visible in OSw - Only suggestions from the new API will
     * be visible in both OSw and new UI surfaces such as UMO - If suggestions are provided from
     * local and proxy routers, priority will be given to the local router
     *
     * @return true if device is suggested device
     */
    public boolean isSuggestedDevice() {
        return mIsSuggested || isSuggestedByRouteListingPreferences();
    }

    /**
     * Checks if the device is suggested from the application's RouteListingPreferences
     *
     * @return true if the device is suggested
     */
    public boolean isSuggestedByRouteListingPreferences() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && Api34Impl.isSuggestedDevice(mRlpItem);
    }

    /**
     * Returns the provider influencing the route ordering in the Output Switcher. See {@link
     * SuggestionProvider}
     *
     * @return the provider influencing the route ordering.
     */
    public @SuggestionProvider int getSuggestionProvider() {
        if (!isSuggestedDevice()) {
            return SUGGESTION_PROVIDER_UNSPECIFIED;
        }
        if (isSuggestedByRouteListingPreferences()) {
            return SUGGESTION_PROVIDER_RLP;
        }
        return mIsSuggestedByApp
                ? SUGGESTION_PROVIDER_DEVICE_SUGGESTION_APP
                : SUGGESTION_PROVIDER_DEVICE_SUGGESTION_OTHER;
    }

    void setConnectedRecord() {
        mConnectedRecord++;
        ConnectionRecordManager.getInstance().setConnectionRecord(mContext, getId(),
                mConnectedRecord);
    }

    /**
     * According the MediaDevice type to check whether we are connected to this MediaDevice.
     *
     * @return Whether it is connected.
     */
    public abstract boolean isConnected();

    /**
     * Get max volume from MediaDevice.
     *
     * @return max volume.
     */
    public int getMaxVolume() {
        if (mRouteInfo == null) {
            Log.w(TAG, "Unable to get max volume. RouteInfo is empty");
            return 0;
        }
        return mRouteInfo.getVolumeMax();
    }

    /**
     * Get current volume from MediaDevice.
     *
     * @return current volume.
     */
    public int getCurrentVolume() {
        if (mRouteInfo == null) {
            Log.w(TAG, "Unable to get current volume. RouteInfo is empty");
            return 0;
        }
        return mRouteInfo.getVolume();
    }

    /**
     * Get application package name.
     *
     * @return package name.
     */
    public String getClientPackageName() {
        if (mRouteInfo == null) {
            Log.w(TAG, "Unable to get client package name. RouteInfo is empty");
            return null;
        }
        return mRouteInfo.getClientPackageName();
    }

    /**
     * Check if the device is Bluetooth LE Audio device.
     *
     * @return true if the RouteInfo equals TYPE_BLE_HEADSET.
     */
    // MediaRoute2Info.getType was made public on API 34, but exists since API 30.
    @SuppressWarnings("NewApi")
    public boolean isBLEDevice() {
        return mRouteInfo.getType() == TYPE_BLE_HEADSET;
    }

    public boolean isInputDevice() {
        return false;
    }

    /**
     * Get application label from MediaDevice.
     *
     * @return application label.
     */
    public int getDeviceType() {
        return mType;
    }

    /**
     * Get the {@link MediaRoute2Info.Type} of the device.
     */
    public int getRouteType() {
        if (mRouteInfo == null) {
            return TYPE_UNKNOWN;
        }
        return mRouteInfo.getType();
    }

    /**
     * Checks if route's volume is fixed, if true, we should disable volume control for the device.
     *
     * @return route for this device is fixed.
     */
    @SuppressLint("NewApi")
    public boolean isVolumeFixed() {
        if (mRouteInfo == null) {
            Log.w(TAG, "RouteInfo is empty, regarded as volume fixed.");
            return true;
        }
        return mRouteInfo.getVolumeHandling() == MediaRoute2Info.PLAYBACK_VOLUME_FIXED;
    }

    /** Set current device's state */
    public void setState(@LocalMediaManager.MediaDeviceState int state) {
        mState = state;
    }

    /**
     * Get current device's state
     *
     * @return state of device
     */
    public @LocalMediaManager.MediaDeviceState int getState() {
        return mState;
    }

    /** Sets whether the current device is suggested. */
    public void setIsSuggested(boolean suggested, boolean suggestedByApp) {
        mIsSuggested = suggested;
        mIsSuggestedByApp = suggestedByApp;
    }

    /**
     * Rules:
     * 1. If there is one of the connected devices identified as a carkit or fast pair device,
     * the fast pair device will be always on the first of the device list and carkit will be
     * second. Rule 2 and Rule 3 can’t overrule this rule.
     * 2. For devices without any usage data yet
     * WiFi device group sorted by alphabetical order + BT device group sorted by alphabetical
     * order + phone speaker
     * 3. For devices with usage record.
     * The most recent used one + device group with usage info sorted by how many times the
     * device has been used.
     * 4. The order is followed below rule:
     *    1. Phone
     *    2. USB-C audio device
     *    3. 3.5 mm audio device
     *    4. Bluetooth device
     *    5. Cast device
     *    6. Cast group device
     *
     * So the device list will look like 5 slots ranked as below.
     * Rule 4 + Rule 1 + the most recently used device + Rule 3 + Rule 2
     * Any slot could be empty. And available device will belong to one of the slots.
     *
     * @return a negative integer, zero, or a positive integer
     * as this object is less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(MediaDevice another) {
        if (another == null) {
            return -1;
        }
        // Check Bluetooth device is have same connection state
        if (isConnected() ^ another.isConnected()) {
            if (isConnected()) {
                return -1;
            } else {
                return 1;
            }
        }

        if (getState() == STATE_SELECTED) {
            return -1;
        } else if (another.getState() == STATE_SELECTED) {
            return 1;
        }

        if (isSuggestedDevice()) {
            return -1;
        } else if (another.isSuggestedDevice()) {
            return 1;
        }

        if (mType == another.mType) {
            // Check device is muting expected device
            if (isMutingExpectedDevice()) {
                return -1;
            } else if (another.isMutingExpectedDevice()) {
                return 1;
            }

            // Check fast pair device
            if (isFastPairDevice()) {
                return -1;
            } else if (another.isFastPairDevice()) {
                return 1;
            }

            // Check carkit
            if (isCarKitDevice()) {
                return -1;
            } else if (another.isCarKitDevice()) {
                return 1;
            }

            // Both devices have same connection status and type, compare the range zone
            if (NearbyDevice.compareRangeZones(getRangeZone(), another.getRangeZone()) != 0) {
                return NearbyDevice.compareRangeZones(getRangeZone(), another.getRangeZone());
            }

            // Set last used device at the first item
            final String lastSelectedDevice = ConnectionRecordManager.getInstance()
                    .getLastSelectedDevice();
            if (TextUtils.equals(lastSelectedDevice, getId())) {
                return -1;
            } else if (TextUtils.equals(lastSelectedDevice, another.getId())) {
                return 1;
            }
            // Sort by how many times the device has been used if there is usage record
            if ((mConnectedRecord != another.mConnectedRecord)
                    && (another.mConnectedRecord > 0 || mConnectedRecord > 0)) {
                return (another.mConnectedRecord - mConnectedRecord);
            }

            // Both devices have never been used
            // To devices with the same type, sort by alphabetical order
            final String s1 = getName();
            final String s2 = another.getName();
            return s1.compareToIgnoreCase(s2);
        } else {
            // Both devices have never been used, the priority is:
            // 1. Phone
            // 2. USB-C audio device
            // 3. 3.5 mm audio device
            // 4. Bluetooth device
            // 5. Cast device
            // 6. Cast group device
            return mType < another.mType ? -1 : 1;
        }
    }

    /**
     * Gets the supported features of the route.
     */
    public List<String> getFeatures() {
        if (mRouteInfo == null) {
            Log.w(TAG, "Unable to get features. RouteInfo is empty");
            return new ArrayList<>();
        }
        return mRouteInfo.getFeatures();
    }

    /**
     * Check if it is CarKit device
     * @return true if it is CarKit device
     */
    protected boolean isCarKitDevice() {
        return false;
    }

    /**
     * Check if it is FastPair device
     * @return {@code true} if it is FastPair device, otherwise return {@code false}
     */
    protected boolean isFastPairDevice() {
        return false;
    }

    /**
     * Check if it is muting expected device
     * @return {@code true} if it is muting expected device, otherwise return {@code false}
     */
    public boolean isMutingExpectedDevice() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MediaDevice)) {
            return false;
        }
        final MediaDevice otherDevice = (MediaDevice) obj;
        return otherDevice.getId().equals(getId());
    }

    /** Whether a device supports moving media playback to itself. */
    public boolean isTransferable() {
        if (mDynamicRouteAttributes == null) {
            return false;
        }
        return mDynamicRouteAttributes.getTransferable();
    }

    /** Whether a device has active playback. */
    public boolean isSelected() {
        if (mDynamicRouteAttributes == null) {
            return false;
        }
        return mDynamicRouteAttributes.getSelected();
    }

    /** Whether a device can be added to playback session. */
    public boolean isSelectable() {
        if (mDynamicRouteAttributes == null) {
            return false;
        }
        return mDynamicRouteAttributes.getSelectable();
    }

    /** Whether a device can be removed from a playback session. */
    public boolean isDeselectable() {
        if (mDynamicRouteAttributes == null) {
            return false;
        }
        return mDynamicRouteAttributes.getDeselectable();
    }

    @NonNull
    @Override
    public String toString() {
        return "MediaDevice (" + getClass().getSimpleName() + "): {"
                + " name=" + getName()
                + ", type=" + getDeviceTypeString(mType)
                + ", id=" + getId()
                + ", volume=" + getVolumeString()
                + ", attributes=" + getDynamicAttributesString()
                + (isSuggestedDevice() ? ",  suggestedBy=" + getSuggestedSourceString() : "")
                + ", rangeZone=" + NearbyDevice.rangeZoneToString(getRangeZone())
                + ", routeFeatures=" + getRouteFeaturesString()
                + " }";
    }

    @NonNull
    private String getVolumeString() {
        return "[" + getCurrentVolume() + "/" + getMaxVolume() + (isVolumeFixed() ? "/fixed" : "")
                + "]";
    }

    @NonNull
    private String getSuggestedSourceString() {
        List<String> attributes = new ArrayList<>();
        if (mIsSuggested) attributes.add("IDS");
        if (Api34Impl.isSuggestedDevice(mRlpItem)) attributes.add("RLP");
        return "[" + TextUtils.join(", ", attributes) + "]";
    }

    @NonNull
    private String getDynamicAttributesString() {
        List<String> attributes = new ArrayList<>();
        if (isSelected()) attributes.add("selected");
        if (isTransferable()) attributes.add("transferable");
        if (isSelectable()) attributes.add("selectable");
        if (isDeselectable()) attributes.add("deselectable");
        if (hasRouteListingPreferenceItem()) attributes.add("hasRLP");
        if (hasOngoingSession()) attributes.add("ongoingSession");
        if (isHostForOngoingSession()) attributes.add("ongoingSessionHost");
        if (isMutingExpectedDevice()) attributes.add("mutingExpectedDevice");
        return "[" + TextUtils.join(", ", attributes) + "]";
    }

    /** Returns string value for MediaDeviceType. */
    @NonNull
    public static String getDeviceTypeString(@MediaDeviceType int type) {
        return switch (type) {
            case MediaDeviceType.TYPE_UNKNOWN -> "UNKNOWN";
            case MediaDeviceType.TYPE_PHONE_DEVICE -> "PHONE_DEVICE";
            case MediaDeviceType.TYPE_USB_C_AUDIO_DEVICE -> "USB_C_AUDIO_DEVICE";
            case MediaDeviceType.TYPE_3POINT5_MM_AUDIO_DEVICE -> "3POINT5_MM_AUDIO_DEVICE";
            case MediaDeviceType.TYPE_FAST_PAIR_BLUETOOTH_DEVICE -> "FAST_PAIR_BLUETOOTH_DEVICE";
            case MediaDeviceType.TYPE_BLUETOOTH_DEVICE -> "BLUETOOTH_DEVICE";
            case MediaDeviceType.TYPE_CAST_DEVICE -> "CAST_DEVICE";
            case MediaDeviceType.TYPE_CAST_GROUP_DEVICE -> "CAST_GROUP_DEVICE";
            case MediaDeviceType.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER -> "REMOTE_AUDIO_VIDEO_RECEIVER";
            default -> TextUtils.formatSimple("UNSUPPORTED(%d)", type);
        };
    }

    private String getRouteFeaturesString() {
        return String.join(/* delimiter= */ "|", getFeatures());
    }

    @RequiresApi(34)
    private static class Api34Impl {
        @DoNotInline
        static boolean isHostForOngoingSession(RouteListingPreference.Item rlpItem) {
            int flags = rlpItem != null ? rlpItem.getFlags() : 0;
            return (flags & FLAG_ONGOING_SESSION) != 0
                    && (flags & FLAG_ONGOING_SESSION_MANAGED) != 0;
        }

        @DoNotInline
        static boolean isSuggestedDevice(RouteListingPreference.Item rlpItem) {
            return rlpItem != null && (rlpItem.getFlags() & FLAG_SUGGESTED) != 0;
        }

        @DoNotInline
        static boolean hasOngoingSession(RouteListingPreference.Item rlpItem) {
            return rlpItem != null && (rlpItem.getFlags() & FLAG_ONGOING_SESSION) != 0;
        }

        @DoNotInline
        static String composeSubtext(RouteListingPreference.Item rlpItem, Context context) {
            switch (rlpItem.getSubText()) {
                case SUBTEXT_ERROR_UNKNOWN:
                    return context.getString(R.string.media_output_status_unknown_error);
                case SUBTEXT_SUBSCRIPTION_REQUIRED:
                    return context.getString(R.string.media_output_status_require_premium);
                case SUBTEXT_DOWNLOADED_CONTENT_ROUTING_DISALLOWED:
                    return context.getString(R.string.media_output_status_not_support_downloads);
                case SUBTEXT_AD_ROUTING_DISALLOWED:
                    return context.getString(R.string.media_output_status_try_after_ad);
                case SUBTEXT_DEVICE_LOW_POWER:
                    return context.getString(R.string.media_output_status_device_in_low_power_mode);
                case SUBTEXT_UNAUTHORIZED:
                    return context.getString(R.string.media_output_status_unauthorized);
                case SUBTEXT_TRACK_UNSUPPORTED:
                    return context.getString(R.string.media_output_status_track_unsupported);
                case SUBTEXT_CUSTOM:
                    return (String) rlpItem.getCustomSubtextMessage();
            }
            return "";
        }
    }
}
