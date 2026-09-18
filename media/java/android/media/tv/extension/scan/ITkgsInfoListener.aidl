/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.scan;

/**
 * @hide
 */
oneway interface ITkgsInfoListener {
    /**
    * Notify listeners when service list are detected  in tkgs service list search.
    *
    * @param serviceList an array of String of service list.
    */
    void onServiceList(in String[] serviceList);
    /**
    * Notify listeners when tkgs version detected in tkgs service list search.
    *
    * @param tableVersion the value of TKGS table version.
    */
    void onTableVersionUpdate(int tableVersion);
    /**
    * Notify listeners when tkgs user message detected in tkgs channel installation.
    *
    * @param strMessage the value of TKGS user message.
    */
    void onUserMessage(String strMessage);
}
