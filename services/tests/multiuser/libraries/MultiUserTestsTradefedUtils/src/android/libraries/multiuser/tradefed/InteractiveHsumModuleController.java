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
package android.libraries.multiuser.tradefed;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Controller that only executes tests when the device is {@code HSUM} (Headless System User Mode)
 * and the system user can be switched to.
 */
public final class InteractiveHsumModuleController extends BaseHsumModuleController {

    @Override
    protected RunStrategy shouldRun(ITestDevice device) throws DeviceNotAvailableException {
        String cmd = "cmd overlay lookup android android:bool/config_canSwitchToHeadlessSystemUser";
        String result = device.executeShellCommand(cmd).trim();
        CLog.d("shouldRun(serial=%s): Shell command '%s' returned '%s'", device.getSerialNumber(),
                cmd, result);

        if (!result.equals("true")) {
            CLog.i("Skipping module as device %s is not Interactive Headless System User Mode",
                    device.getSerialNumber());
            return RunStrategy.SKIP_MODULE_TESTCASES;
        }

        return RunStrategy.RUN;
    }
}
