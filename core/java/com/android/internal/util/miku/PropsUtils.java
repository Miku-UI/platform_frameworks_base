/*
 * Copyright (C) 2020 The Pixel Experience Project
 *
 * Copyright (C) 2021-2022 Miku UI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.util.miku;

import android.os.Build;
import android.util.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PropsUtils {

    private static final String TAG = PropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static volatile boolean sIsGms = false;
    public static final String PACKAGE_GMS = "com.google.android.gms";

    private static final Map<String, Object> propsToChange;
    private static final Map<String, Object> propsToChangeMeizu;
    private static final Map<String, ArrayList<String>> propsToKeep;
    private static final String[] extraPackagesToChange = {
        "com.android.vending",
        "com.breel.wallpapers20"
    };
    private static final String[] meizuPropToChange = {
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
	    "com.kugou.android",
	    "cmccwm.mobilemusic",
	    "cn.kuwo.player",
	    "com.meizu.media.music"
    };

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put("com.google.android.settings.intelligence", new ArrayList<String>(Arrays.asList("FINGERPRINT")));
        propsToChange = new HashMap<>();
        propsToChange.put("BRAND", "google");
        propsToChange.put("MANUFACTURER", "Google");
        propsToChange.put("DEVICE", "raven");
        propsToChange.put("PRODUCT", "raven");
        propsToChange.put("MODEL", "Pixel 6 Pro");
        propsToChange.put("FINGERPRINT", "google/redfin/redfin:12/SP2A.220305.012/8177914:user/release-keys");
	propsToChangeMeizu = new HashMap<>();
	propsToChangeMeizu.put("BRAND", "meizu");
	propsToChangeMeizu.put("MANUFACTURER", "meizu");
	propsToChangeMeizu.put("DEVICE", "meizu18");
	propsToChangeMeizu.put("PRODUCT", "meizu_18_CN");
	propsToChangeMeizu.put("MODEL", "MEIZU 18");
	propsToChangeMeizu.put("FINGERPRINT", "meizu/meizu_18_CN/meizu18:11/RKQ1.201105.002/1607588916:user/release-keys");
    }

    public static void setProps(String packageName) {
        if (packageName == null){
            return;
        }
        if (packageName.equals(PACKAGE_GMS)) {
            sIsGms = true;
            setPropValue("TYPE", "userdebug");
        }
        if (packageName.startsWith("com.google.") || Arrays.asList(extraPackagesToChange).contains(packageName)){
            if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
            for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                if (propsToKeep.containsKey(packageName) && propsToKeep.get(packageName).contains(key)){
                    if (DEBUG) Log.d(TAG, "Not defining " + key + " prop for: " + packageName);
                    continue;
                }
                if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + packageName);
                setPropValue(key, value);
            }
        }
	// Set Props for StatusBar Lyric
	if(Arrays.asList(meizuPropToChange).contains(packageName)){
	    if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
	    for (Map.Entry<String, Object> prop : propsToChangeMeizu.entrySet()) {
		String key = prop.getKey();
		Object value = prop.getValue();
		if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + packageName);
		setPropValue(key, value);
	    }
	}
        // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")){
            setPropValue("FINGERPRINT", Build.DATE);
        }
    }

    private static void setPropValue(String key, Object value){
        try {
            if (DEBUG) Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet
        if (sIsGms && isCallerSafetyNet()) {
            throw new UnsupportedOperationException();
        }
    }
}
