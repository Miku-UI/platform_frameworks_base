/*
 * Copyright (C) 2025 The Android Open Source Project
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
package android.hardware.usb;

/** @hide */
@RustDerive(Clone=true, Eq=true, PartialEq=true)
parcelable UsbAuthDeviceInfo {
    String syspath;
    int busNumber;
    int deviceNumber;
    int vendorId;
    int productId;
    byte deviceClass;
    String serialNumber;
    String manufacturer;
    String productName;
    int bcdDevice;
    byte bDeviceClass;
    byte bDeviceSubClass;
    byte bDeviceProtocol;
    byte bInterfaceClass;
    byte bInterfaceSubClass;
    byte bInterfaceProtocol;
    byte bInterfaceNumber;
}
