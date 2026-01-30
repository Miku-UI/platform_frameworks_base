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

package android.service.security.talisman;

import android.annotation.SystemApi;
import android.app.Service;
import java.util.List;

/**
 * A service that can fetch a set of talismans from a remote source and make them available to the
 * system.
 *
 * <p>To implement a talisman service, you must extend this class and implement the {@link
 * #onReportTalismanNeeds(TalismanNeeds)} method. The system will call this method to communicate
 * its current need for talismans. Your service is responsible for satisfying these needs by
 * fetching talismans and providing them back to the system using {@link #addTalismans(List)} and
 * {@link #setTrustConfiguration(List, List)}.
 *
 * <h3>Lifecycle</h3>
 *
 * <p>The system manages the lifecycle of this service, binding to it whenever possible. When the
 * system's needs change (e.g., as talismans are used or expire), it will call {@link
 * #onReportTalismanNeeds(TalismanNeeds)} with the updated requirements.
 *
 * <h3>Talisman Needs</h3>
 *
 * <p>The {@link TalismanNeeds} object provided to {@link #onReportTalismanNeeds(TalismanNeeds)}
 * specifies:
 *
 * <ul>
 *   <li>The number of "verified device" talismans required.
 *   <li>The number of identity-bound talisman sets required.
 *   <li>THe time of the last trust configuration update.
 * </ul>
 *
 * <p>The needs also indicate how many talismans are needed urgently. Urgent needs should be
 * fulfilled as soon as possible, while non-urgent needs should be fulfilled when conditions are
 * favorable (e.g., when the device is charging and on an unmetered network).
 *
 * <h3>Manifest Declaration</h3>
 *
 * <p>You must declare your service in the application's manifest file. The service must be
 * protected by the {@code android.permission.BIND_TALISMAN_SERVICE} permission and include an
 * intent filter for the {@link #SERVICE_INTERFACE} action. For example:
 *
 * <pre>{@code
 * <service
 *        android:name=".MyTalismanService"
 *        android:label="@string/talisman_service_name"
 *        android:exported="true"
 *        android:permission="android.permission.BIND_TALISMAN_SERVICE">
 *    <intent-filter>
 *        <action android:name="android.service.security.talisman.TalismanService" />
 *    </intent-filter>
 * </service>
 * }</pre>
 *
 * @hide
 */
public abstract class TalismanService extends Service {
    // TODO(b/418280383): Make this @SystemApi

    /** The Intent action that a TalismanService must respond to. */
    public static final String SERVICE_INTERFACE =
            "android.service.security.talisman.TalismanService";

    /**
     * Called by the system to update the current need for talismans.
     *
     * <p>Implementers of this service are responsible to satisfy the needs of the system and
     * provide the required talismans and trust configuration via the other methods in this class.
     * This method is always called with a single snapshot of the current needs, so any needs
     * reported previously should be ignored.
     *
     * <p>This method is called at most once as a result of a call to {@link
     * #addTalismans(List<byte[]>)}.
     */
    public void onReportTalismanNeeds(TalismanNeeds needs) {}

    /**
     * Adds new talismans to the system's cache.
     *
     * <p>Talismans are encoded as CBOR Web Tokens (CWTs) per RFC 8392 and the Talisman protocol.
     * This method is intended to be backwards compatible as new SDK versions and Protocol versions
     * are released, so it will support old Talisman versions as the protocol evolves.
     *
     * @param encodedTalismans The encoded talismans to add.
     */
    public final void addTalismans(List<byte[]> encodedTalismans) {}

    /**
     * Sets the trust configuration.
     *
     * <p>The trust configuration determines how this device verifies talismans coming from other
     * devices. Talismans are signed by an intermediate certificate which itself is signed by a root
     * trust anchor.
     *
     * <p>For valid peer talismans:
     *
     * <ul>
     *   <li>The talisman's issuer (`iss`) must be in the intermediate certificates passed to this
     *       method in {@code intermediateCertificates}, as specified by the certificate's subject
     *       (`sub`).
     *   <li>The intermediate certificates passed to this method must be signed by a trust anchor in
     *       the intersection of the set of pre-configured trust anchors and those passed to this
     *       method in {@code allowedRoots}.
     * </ul>
     *
     * Pre-configured trust anchors are set in TODO(b/427273055): Document when system trust config
     * exists.
     *
     * @param allowedRoots The list of trust anchors.
     * @param intermediateCertificates The list of intermediate certificates.
     */
    public final void setTrustConfiguration(
            List<byte[]> allowedRoots, List<byte[]> intermediateCertificates) {}
}
