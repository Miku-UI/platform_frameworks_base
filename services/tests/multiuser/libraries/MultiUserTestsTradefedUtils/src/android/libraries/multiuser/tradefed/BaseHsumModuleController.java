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
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.suite.module.BaseModuleController;

/**
 * Base class for controllers used to run tests only on {@code HSUM} (Headless System User Mode)
 * devices.
 *
 * <p>It checks that the device is {@code HSUM}, but it's up to the subclass to add additional
 * checks.
 */
public abstract class BaseHsumModuleController extends BaseModuleController {

    @Override
    public final RunStrategy shouldRun(IInvocationContext context)
            throws DeviceNotAvailableException {
        if (context.getDevices().isEmpty()) {
            CLog.w("No device found in invocation context. Skipping module.");
            return RunStrategy.SKIP_MODULE_TESTCASES;
        }
        ITestDevice device = context.getDevices().get(0);

        if (!device.isHeadlessSystemUserMode()) {
            CLog.i("Skipping module as device %s is not Headless System User Mode",
                    device.getSerialNumber());
            return RunStrategy.SKIP_MODULE_TESTCASES;
        }

        return shouldRun(device);
    }

    /**
     * "Real" {@code shouldRun()} method that should be implemented by the subclasses.
     *
     * <p>When called, the {@code HSUM} check is already done, so the device is {@code HSUM}.
     */
    protected abstract RunStrategy shouldRun(ITestDevice device) throws DeviceNotAvailableException;
}
