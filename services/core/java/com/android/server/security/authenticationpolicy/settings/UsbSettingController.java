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

package com.android.server.security.authenticationpolicy.settings;

import static android.hardware.usb.InternalUsbDataSignalDisableReason.USB_DISABLE_REASON_LOCKDOWN_MODE;

import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Setting controller for whether USB is enabled. */
class UsbSettingController implements SettingController<Map<String, Boolean>> {
    private static final String TAG = "UsbSettingController";

    @Nullable
    private IUsbManagerInternal mUsbManagerInternal;
    @Nullable
    private UsbManager mUsbManager;
    private boolean mSkipSecurityFeaturesForTest = false;

    @Override
    public void storeOriginalValue(@NonNull SettingState<Map<String, Boolean>> state, int userId)
            throws Exception {
        if (mUsbManagerInternal == null) {
            Slog.w(TAG, "IUsbManagerInternal is null, cannot retrieve USB port states");
            return;
        }
        Map<String, Boolean> usbPortsEnabledStatus = getUsbPortsEnabledStatus();
        state.setOriginalValue(usbPortsEnabledStatus);
    }

    @Override
    public void applySecureLockDeviceValue(@NonNull SettingState<Map<String, Boolean>> state,
            int userId) throws Exception {
        if (mSkipSecurityFeaturesForTest) {
            Slog.d(TAG, "Skipping USB data signal disable for test.");
            return;
        } else if (mUsbManagerInternal == null) {
            Slog.w(TAG, "IUsbManagerInternal is null, cannot apply secure lock device value "
                    + "for USB state.");
            return;
        }
        mUsbManagerInternal.enableUsbDataSignal(false, USB_DISABLE_REASON_LOCKDOWN_MODE);
    }

    @Override
    public void restoreFromOriginalValue(@NonNull SettingState<Map<String, Boolean>> state,
            int userId) throws Exception {
        if (mUsbManager == null) {
            Slog.w(TAG, "UsbManager is null, cannot restore USB setting.");
            return;
        } else if (state.getOriginalValue() == null) {
            Slog.w(TAG, "Original value for USB setting is null, cannot restore USB state");
            return;
        }

        List<UsbPort> ports = mUsbManager.getPorts();
        Map<String, Boolean> originalPortStates = state.getOriginalValue();

        if (originalPortStates != null) {
            for (UsbPort port : ports) {
                boolean shouldEnablePort = originalPortStates.getOrDefault(port.getId(), false);
                if (shouldEnablePort) {
                    int result = port.enableUsbData(true);
                    if (result == UsbPort.ENABLE_USB_DATA_SUCCESS) {
                        Slog.i(TAG, "Re-enabled USB data signal on port " + port);
                    } else {
                        Slog.w(TAG, "Failed to re-enable USB data signal on port " + port);
                    }
                } else {
                    Slog.i(TAG, "USB data signal on port " + port.getId() + " was already "
                            + "disabled prior to secure lock device, leave unchanged.");
                }
            }
        }
    }

    @Override
    public void serializeOriginalValue(@NonNull String settingKey,
            @NonNull Map<String, Boolean> originalValue, @NonNull TypedXmlSerializer serializer)
            throws IOException {
        for (Map.Entry<String, Boolean> mapEntry : originalValue.entrySet()) {
            String portId = mapEntry.getKey();
            boolean isEnabled = mapEntry.getValue();
            serializer.startTag(null, "port");
            serializer.attribute(null, "id", portId);
            serializer.attributeBoolean(null, "enabled", isEnabled);
            serializer.endTag(null, "port");
        }
    }

    @Override
    public Map<String, Boolean> deserializeOriginalValue(@NonNull TypedXmlPullParser parser,
            @NonNull String settingKey) throws IOException, XmlPullParserException {
        Map<String, Boolean> usbPortEnabledStates = new HashMap<>();
        int portMapInnerDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, portMapInnerDepth)) {
            if ("port".equals(parser.getName())) {
                String portId = parser.getAttributeValue(null, "id");
                boolean enabled = parser.getAttributeBoolean(null, "enabled", false);
                if (portId != null) {
                    usbPortEnabledStates.put(portId, enabled);
                }
            } else {
                XmlUtils.skipCurrentTag(parser);
            }
        }
        return usbPortEnabledStates;
    }

    private Map<String, Boolean> getUsbPortsEnabledStatus() {
        Map<String, Boolean> usbPortsEnabledStatus = new HashMap<>();
        if (mUsbManager == null) {
            Slog.w(TAG, "UsbManager is null, cannot retrieve USB ports enabled status");
            return usbPortsEnabledStatus;
        }

        List<UsbPort> ports = mUsbManager.getPorts();
        for (UsbPort port : ports) {
            UsbPortStatus portStatus = port.getStatus();
            if (portStatus != null) {
                int usbDataStatus = portStatus.getUsbDataStatus();
                boolean isPortEnabled =
                        (usbDataStatus & UsbPortStatus.DATA_STATUS_DISABLED_FORCE) == 0;
                usbPortsEnabledStatus.put(port.getId(), isPortEnabled);
            }
        }
        return usbPortsEnabledStatus;
    }

    /**
     * Sets the IUsbManagerInternal for this controller.
     * @param usbManagerInternal The IUsbManagerInternal to set.
     */
    void setUsbManagerInternal(@Nullable IUsbManagerInternal usbManagerInternal) {
        mUsbManagerInternal = usbManagerInternal;
    }

    /**
     * Sets the UsbManager for this controller.
     * @param usbManager The UsbManager to set.
     */
    void setUsbManager(@Nullable UsbManager usbManager) {
        mUsbManager = usbManager;
    }

    /**
     * Sets whether to skip security features for test.
     *
     * @param skipSecurityFeaturesForTest Whether to skip security features for test.
     */
    void setSkipSecurityFeaturesForTest(boolean skipSecurityFeaturesForTest) {
        mSkipSecurityFeaturesForTest = skipSecurityFeaturesForTest;
    }
}
