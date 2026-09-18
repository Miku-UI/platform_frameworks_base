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

#include <android/native_service.h>
#include <native_service_private.h>

void ANativeService_setOnBindCallback(ANativeService* service,
                                      const ANativeService_onBindCallback callback) {
    service->callbacks.onBind = callback;
}

void ANativeService_setOnUnbindCallback(ANativeService* service,
                                        const ANativeService_onUnbindCallback callback) {
    service->callbacks.onUnbind = callback;
}

void ANativeService_setOnRebindCallback(ANativeService* service,
                                        const ANativeService_onRebindCallback callback) {
    service->callbacks.onRebind = callback;
}

void ANativeService_setOnDestroyCallback(ANativeService* service,
                                         const ANativeService_onDestroyCallback callback) {
    service->callbacks.onDestroy = callback;
}

void ANativeService_setOnTrimMemoryCallback(ANativeService* service,
                                            const ANativeService_onTrimMemoryCallback callback) {
    service->callbacks.onTrimMemory = callback;
}
