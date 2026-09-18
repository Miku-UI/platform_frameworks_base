/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.usb.hal.port;

import android.hardware.usb.DisplayPortAltModeInfo;
import android.hardware.usb.PowerProfileInfo;
import android.hardware.usb.PowerProfileMatchInfo;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Used for storing the raw data from the HAL.
 * Values of the member variables mocked directly in case of emulation.
 */
public final class RawPortInfo implements Parcelable {
    public final String portId;
    public final int supportedModes;
    public final int supportedContaminantProtectionModes;
    public int currentMode;
    public boolean canChangeMode;
    public int currentPowerRole;
    public boolean canChangePowerRole;
    public int currentDataRole;
    public boolean canChangeDataRole;
    public boolean supportsEnableContaminantPresenceProtection;
    public int contaminantProtectionStatus;
    public boolean supportsEnableContaminantPresenceDetection;
    public int contaminantDetectionStatus;
    public int usbDataStatus;
    public boolean powerTransferLimited;
    public int powerBrickConnectionStatus;
    public final boolean supportsComplianceWarnings;
    public int[] complianceWarnings;
    public int plugState;
    public int supportedAltModes;
    public DisplayPortAltModeInfo displayPortAltModeInfo;
    public boolean supportsPartnerBc12Type;
    public int partnerBc12Type;
    public boolean supportsPowerProfiles;
    public ArrayList<PowerProfileInfo> portSinkPowerProfiles;
    public ArrayList<PowerProfileInfo> portSourcePowerProfiles;
    public ArrayList<PowerProfileInfo> partnerSinkPowerProfiles;
    public ArrayList<PowerProfileInfo> partnerSourcePowerProfiles;
    public ArrayList<PowerProfileMatchInfo> portSinkPowerProfileMatches;
    public ArrayList<PowerProfileMatchInfo> portSourcePowerProfileMatches;

    private RawPortInfo(Builder builder) {
        this.portId = builder.mPortId;
        this.supportedModes = builder.mSupportedModes;
        this.supportedContaminantProtectionModes = builder.mSupportedContaminantProtectionModes;
        this.currentMode = builder.mCurrentMode;
        this.canChangeMode = builder.mCanChangeMode;
        this.currentPowerRole = builder.mCurrentPowerRole;
        this.canChangePowerRole = builder.mCanChangePowerRole;
        this.currentDataRole = builder.mCurrentDataRole;
        this.canChangeDataRole = builder.mCanChangeDataRole;
        this.supportsEnableContaminantPresenceProtection =
                builder.mSupportsEnableContaminantPresenceProtection;
        this.contaminantProtectionStatus = builder.mContaminantProtectionStatus;
        this.supportsEnableContaminantPresenceDetection =
                builder.mSupportsEnableContaminantPresenceDetection;
        this.contaminantDetectionStatus = builder.mContaminantDetectionStatus;
        this.usbDataStatus = builder.mUsbDataStatus;
        this.powerTransferLimited = builder.mPowerTransferLimited;
        this.powerBrickConnectionStatus = builder.mPowerBrickConnectionStatus;
        this.supportsComplianceWarnings = builder.mSupportsComplianceWarnings;
        this.complianceWarnings = builder.mComplianceWarnings;
        this.plugState = builder.mPlugState;
        this.supportedAltModes = builder.mSupportedAltModes;
        this.displayPortAltModeInfo = builder.mDisplayPortAltModeInfo;
        this.supportsPartnerBc12Type = builder.mSupportsPartnerBc12Type;
        this.partnerBc12Type = builder.mPartnerBc12Type;
        this.supportsPowerProfiles = builder.mSupportsPowerProfiles;
        this.portSinkPowerProfiles = builder.mPortSinkPowerProfiles;
        this.portSourcePowerProfiles = builder.mPortSourcePowerProfiles;
        this.partnerSinkPowerProfiles = builder.mPartnerSinkPowerProfiles;
        this.partnerSourcePowerProfiles = builder.mPartnerSourcePowerProfiles;
        this.portSinkPowerProfileMatches = builder.mPortSinkPowerProfileMatches;
        this.portSourcePowerProfileMatches = builder.mPortSourcePowerProfileMatches;
    }

    private RawPortInfo(Parcel in) {
        this.portId = in.readString();
        this.supportedModes = in.readInt();
        this.supportedContaminantProtectionModes = in.readInt();
        this.currentMode = in.readInt();
        this.canChangeMode = in.readByte() != 0;
        this.currentPowerRole = in.readInt();
        this.canChangePowerRole = in.readByte() != 0;
        this.currentDataRole = in.readInt();
        this.canChangeDataRole = in.readByte() != 0;
        this.supportsEnableContaminantPresenceProtection = in.readByte() != 0;
        this.contaminantProtectionStatus = in.readInt();
        this.supportsEnableContaminantPresenceDetection = in.readByte() != 0;
        this.contaminantDetectionStatus = in.readInt();
        this.usbDataStatus = in.readInt();
        this.powerTransferLimited = in.readByte() != 0;
        this.powerBrickConnectionStatus = in.readInt();
        this.supportsComplianceWarnings = in.readByte() != 0;
        this.complianceWarnings = in.createIntArray();
        this.plugState = in.readInt();
        this.supportedAltModes = in.readInt();
        if ((this.supportedAltModes & UsbPort.FLAG_ALT_MODE_TYPE_DISPLAYPORT) != 0) {
            this.displayPortAltModeInfo = DisplayPortAltModeInfo.CREATOR.createFromParcel(in);
        } else {
            this.displayPortAltModeInfo = null;
        }
        this.supportsPartnerBc12Type = in.readByte() != 0;
        this.partnerBc12Type = in.readInt();
        this.supportsPowerProfiles = in.readByte() != 0;
        ArrayList<PowerProfileInfo> portSinkPowerProfiles = new ArrayList<>();
        ArrayList<PowerProfileInfo> portSourcePowerProfiles = new ArrayList<>();
        ArrayList<PowerProfileInfo> partnerSinkPowerProfiles = new ArrayList<>();
        ArrayList<PowerProfileInfo> partnerSourcePowerProfiles = new ArrayList<>();
        in.readParcelableList(portSinkPowerProfiles, ClassLoader.getSystemClassLoader(),
                PowerProfileInfo.class);
        in.readParcelableList(portSourcePowerProfiles, ClassLoader.getSystemClassLoader(),
                PowerProfileInfo.class);
        in.readParcelableList(partnerSinkPowerProfiles, ClassLoader.getSystemClassLoader(),
                PowerProfileInfo.class);
        in.readParcelableList(partnerSourcePowerProfiles, ClassLoader.getSystemClassLoader(),
                PowerProfileInfo.class);
        this.portSinkPowerProfiles = portSinkPowerProfiles;
        this.portSourcePowerProfiles = portSourcePowerProfiles;
        this.partnerSinkPowerProfiles = partnerSinkPowerProfiles;
        this.partnerSourcePowerProfiles = partnerSourcePowerProfiles;
        ArrayList<PowerProfileMatchInfo> portSinkPowerProfileMatches = new ArrayList<>();
        ArrayList<PowerProfileMatchInfo> portSourcePowerProfileMatches = new ArrayList<>();
        in.readParcelableList(portSinkPowerProfileMatches, ClassLoader.getSystemClassLoader(),
                PowerProfileMatchInfo.class);
        in.readParcelableList(portSourcePowerProfileMatches,
                ClassLoader.getSystemClassLoader(), PowerProfileMatchInfo.class);
        this.portSinkPowerProfileMatches = portSinkPowerProfileMatches;
        this.portSourcePowerProfileMatches = portSourcePowerProfileMatches;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(portId);
        dest.writeInt(supportedModes);
        dest.writeInt(supportedContaminantProtectionModes);
        dest.writeInt(currentMode);
        dest.writeByte((byte) (canChangeMode ? 1 : 0));
        dest.writeInt(currentPowerRole);
        dest.writeByte((byte) (canChangePowerRole ? 1 : 0));
        dest.writeInt(currentDataRole);
        dest.writeByte((byte) (canChangeDataRole ? 1 : 0));
        dest.writeBoolean(supportsEnableContaminantPresenceProtection);
        dest.writeInt(contaminantProtectionStatus);
        dest.writeBoolean(supportsEnableContaminantPresenceDetection);
        dest.writeInt(contaminantDetectionStatus);
        dest.writeInt(usbDataStatus);
        dest.writeBoolean(powerTransferLimited);
        dest.writeInt(powerBrickConnectionStatus);
        dest.writeBoolean(supportsComplianceWarnings);
        dest.writeIntArray(complianceWarnings);
        dest.writeInt(plugState);
        dest.writeInt(supportedAltModes);
        if ((supportedAltModes & UsbPort.FLAG_ALT_MODE_TYPE_DISPLAYPORT) != 0) {
            displayPortAltModeInfo.writeToParcel(dest, 0);
        }
        dest.writeBoolean(supportsPartnerBc12Type);
        dest.writeInt(partnerBc12Type);
        dest.writeBoolean(supportsPowerProfiles);
        dest.writeParcelableList(portSinkPowerProfiles, 0);
        dest.writeParcelableList(portSourcePowerProfiles, 0);
        dest.writeParcelableList(partnerSinkPowerProfiles, 0);
        dest.writeParcelableList(partnerSourcePowerProfiles, 0);
        dest.writeParcelableList(portSinkPowerProfileMatches, 0);
        dest.writeParcelableList(portSourcePowerProfileMatches, 0);
    }

    public static final Parcelable.Creator<RawPortInfo> CREATOR =
            new Parcelable.Creator<RawPortInfo>() {
        @Override
        public RawPortInfo createFromParcel(Parcel in) {
            return new RawPortInfo(in);
        }

        @Override
        public RawPortInfo[] newArray(int size) {
            return new RawPortInfo[size];
        }
    };

    public static class Builder {
        private final String mPortId;
        private int mSupportedModes;
        private int mSupportedContaminantProtectionModes;
        private int mCurrentMode;
        private boolean mCanChangeMode;
        private int mCurrentPowerRole;
        private boolean mCanChangePowerRole;
        private int mCurrentDataRole;
        private boolean mCanChangeDataRole;
        private boolean mSupportsEnableContaminantPresenceProtection;
        private int mContaminantProtectionStatus;
        private boolean mSupportsEnableContaminantPresenceDetection;
        private int mContaminantDetectionStatus;
        private int mUsbDataStatus;
        private boolean mPowerTransferLimited;
        private int mPowerBrickConnectionStatus;
        private boolean mSupportsComplianceWarnings;
        private int[] mComplianceWarnings;
        private int mPlugState;
        private int mSupportedAltModes;
        private DisplayPortAltModeInfo mDisplayPortAltModeInfo;
        private boolean mSupportsPartnerBc12Type;
        private int mPartnerBc12Type;
        private boolean mSupportsPowerProfiles;
        private ArrayList<PowerProfileInfo> mPortSinkPowerProfiles;
        private ArrayList<PowerProfileInfo> mPortSourcePowerProfiles;
        private ArrayList<PowerProfileInfo> mPartnerSinkPowerProfiles;
        private ArrayList<PowerProfileInfo> mPartnerSourcePowerProfiles;
        private ArrayList<PowerProfileMatchInfo> mPortSinkPowerProfileMatches;
        private ArrayList<PowerProfileMatchInfo> mPortSourcePowerProfileMatches;

        public Builder(String portId) {
            this.mPortId = portId;
            mSupportedModes = UsbPortStatus.MODE_NONE;
            mSupportedContaminantProtectionModes = UsbPortStatus.CONTAMINANT_PROTECTION_NONE;
            mCurrentMode = UsbPortStatus.MODE_NONE;
            mCanChangeMode = false;
            mCurrentPowerRole = UsbPortStatus.POWER_ROLE_NONE;
            mCanChangePowerRole = false;
            mCurrentDataRole = UsbPortStatus.DATA_ROLE_NONE;
            mCanChangeDataRole = false;
            mSupportsEnableContaminantPresenceProtection = false;
            mContaminantProtectionStatus = UsbPortStatus.CONTAMINANT_PROTECTION_NONE;
            mSupportsEnableContaminantPresenceDetection = false;
            mContaminantDetectionStatus = UsbPortStatus.CONTAMINANT_DETECTION_NOT_SUPPORTED;
            mUsbDataStatus = UsbPortStatus.DATA_STATUS_UNKNOWN;
            mPowerTransferLimited = false;
            mPowerBrickConnectionStatus = UsbPortStatus.POWER_BRICK_STATUS_UNKNOWN;
            mSupportsComplianceWarnings = false;
            mComplianceWarnings = new int[] {};
            mPlugState = UsbPortStatus.PLUG_STATE_UNKNOWN;
            mSupportedAltModes = 0;
            mDisplayPortAltModeInfo = null;
            mSupportsPartnerBc12Type = false;
            mPartnerBc12Type = UsbPortStatus.BC12_TYPE_UNKNOWN;
            mSupportsPowerProfiles = false;
            mPortSinkPowerProfiles = new ArrayList<>();
            mPortSourcePowerProfiles = new ArrayList<>();
            mPartnerSinkPowerProfiles = new ArrayList<>();
            mPartnerSourcePowerProfiles = new ArrayList<>();
            mPortSinkPowerProfileMatches = new ArrayList<>();
            mPortSourcePowerProfileMatches = new ArrayList<>();
        }

        /**
         * Sets the supported modes of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportedModes(int modes) {
            mSupportedModes = modes;
            return this;
        }

        /**
         * Sets the supported contaminant protection modes of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportedContaminantProtectionModes(int modes) {
            mSupportedContaminantProtectionModes = modes;
            return this;
        }

        /**
         * Sets the current mode of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setCurrentMode(int val) {
            mCurrentMode = val;
            return this;
        }

        /**
         * Sets the mode change capability of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setCanChangeMode(boolean val) {
            mCanChangeMode = val;
            return this;
        }

        /**
         * Sets the current power role of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setCurrentPowerRole(int val) {
            mCurrentPowerRole = val;
            return this;
        }

        /**
         * Sets the power role change capability of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setCanChangePowerRole(boolean val) {
            mCanChangePowerRole = val;
            return this;
        }

        /**
         * Sets the current data role of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setCurrentDataRole(int val) {
            mCurrentDataRole = val;
            return this;
        }

        /**
         * Sets the data role change capability of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setCanChangeDataRole(boolean val) {
            mCanChangeDataRole = val;
            return this;
        }

        /**
         * Sets the enable contaminant presence protection capability of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportsEnableContaminantPresenceProtection(boolean val) {
            mSupportsEnableContaminantPresenceProtection = val;
            return this;
        }

        /**
         * Sets the contaminant protection status of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setContaminantProtectionStatus(int val) {
            mContaminantProtectionStatus = val;
            return this;
        }

        /**
         * Sets the enable contaminant presence detection capability of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportsEnableContaminantPresenceDetection(boolean val) {
            mSupportsEnableContaminantPresenceDetection = val;
            return this;
        }

        /**
         * Sets the contaminant detection status of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setContaminantDetectionStatus(int val) {
            mContaminantDetectionStatus = val;
            return this;
        }

        /**
         * Sets the usb data status of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setUsbDataStatus(int val) {
            mUsbDataStatus = val;
            return this;
        }

        /**
         * Sets the power transfer limited status of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setPowerTransferLimited(boolean val) {
            mPowerTransferLimited = val;
            return this;
        }

        /**
         * Sets the power brick connection status of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setPowerBrickConnectionStatus(int val) {
            mPowerBrickConnectionStatus = val;
            return this;
        }

        /**
         * Sets the compliance warning capabilities of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportsComplianceWarnings(boolean val) {
            mSupportsComplianceWarnings = val;
            return this;
        }

        /**
         * Sets the current compliance warnings of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setComplianceWarnings(int[] val) {
            mComplianceWarnings = val;
            return this;
        }

        /**
         * Sets the plug state of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setPlugState(int val) {
            mPlugState = val;
            return this;
        }

        /**
         * Sets the supported alt modes of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportedAltModes(int val) {
            mSupportedAltModes = val;
            return this;
        }

        /**
         * Sets the DisplayPort Alt Mode info of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setDisplayPortAltModeInfo(DisplayPortAltModeInfo val) {
            mDisplayPortAltModeInfo = val;
            return this;
        }

        /**
         * Sets the partner BC 1.2 type reporting capability of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportsPartnerBc12Type(boolean val) {
            mSupportsPartnerBc12Type = val;
            return this;
        }

        /**
         * Sets the current partner BC 1.2 type of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setPartnerBc12Type(int val) {
            mPartnerBc12Type = val;
            return this;
        }

        /**
         * Sets the power profile reporting capability of {@link RawPortInfo}
         *
         * @return instance of {@link Builder}
         */
        public Builder setSupportsPowerProfiles(boolean val) {
            mSupportsPowerProfiles = val;
            return this;
        }

        /**
         * Sets the port power profiles of {@link RawPortInfo}
         *
         * @return Instance of {@link Builder}
         */
        public Builder setPortPowerProfiles(PowerProfileInfo[] sinkPowerProfiles,
                                            PowerProfileInfo[] sourcePowerProfiles) {
            List<PowerProfileInfo> sinkProfilesList = Arrays.asList(sinkPowerProfiles);
            List<PowerProfileInfo> sourceProfilesList = Arrays.asList(sourcePowerProfiles);
            mPortSinkPowerProfiles.addAll(new ArrayList<>(sinkProfilesList));
            mPortSourcePowerProfiles.addAll(new ArrayList<>(sourceProfilesList));
            return this;
        }

        /**
         * Sets the partner power profiles of {@link RawPortInfo}
         *
         * @return Instance of {@link Builder}
         */
        public Builder setPartnerPowerProfiles(PowerProfileInfo[] sinkPowerProfiles,
                                               PowerProfileInfo[] sourcePowerProfiles) {
            List<PowerProfileInfo> sinkProfilesList = Arrays.asList(sinkPowerProfiles);
            List<PowerProfileInfo> sourceProfilesList = Arrays.asList(sourcePowerProfiles);
            mPartnerSinkPowerProfiles.addAll(new ArrayList<>(sinkProfilesList));
            mPartnerSourcePowerProfiles.addAll(new ArrayList<>(sourceProfilesList));
            return this;
        }

        /**
         * Sets the power profile match results of {@link RawPortInfo}
         *
         * @return Instance of {@link Builder}
         */
        public Builder setPowerProfileMatchInfo(PowerProfileMatchInfo[] portSinkMatches,
                PowerProfileMatchInfo[] portSourceMatches) {
            List<PowerProfileMatchInfo> portSinkMatchList = Arrays.asList(portSinkMatches);
            List<PowerProfileMatchInfo> portSourceMatchList = Arrays.asList(portSourceMatches);
            mPortSinkPowerProfileMatches.addAll(new ArrayList<>(portSinkMatchList));
            mPortSourcePowerProfileMatches.addAll(new ArrayList<>(portSourceMatchList));
            return this;
        }

        /**
         * Use the Builder info to create a new {@link RawPortInfo} instance
         *
         * @return instance of {@link RawPortInfo}
         */
        public RawPortInfo build() {
            return new RawPortInfo(this);
        }
    }
}
