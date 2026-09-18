/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.appfunctions.allowlist

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SignedPackage
import android.content.pm.SigningInfo
import android.os.Bundle
import android.os.allowlist.AllowlistManager
import android.os.allowlist.AllowlistRequest
import android.os.allowlist.AllowlistResponse
import android.os.allowlist.SignedPackageMultiMap
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.PackageUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.appfunctions.ServiceConfig
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.function.Consumer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER)
class SystemAppFunctionAllowlistReaderTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var packageManager: PackageManager
    private lateinit var allowlistManager: AllowlistManager
    private lateinit var serviceConfig: ServiceConfig
    private lateinit var allowlistReader: SystemAppFunctionAllowlistReader

    private val directExecutor = MoreExecutors.directExecutor()

    @Before
    fun setUp() {
        packageManager = mock(PackageManager::class.java)
        allowlistManager = mock(AllowlistManager::class.java)
        serviceConfig = mock(ServiceConfig::class.java)

        `when`(serviceConfig.appFunctionAllowlistCacheSize).thenReturn(TEST_CACHE_SIZE)

        allowlistReader =
            SystemAppFunctionAllowlistReader(
                packageManager,
                allowlistManager,
                serviceConfig,
                directExecutor,
                directExecutor,
            )
    }

    @Test
    fun isAllowlisted_samePackage_returnsTrue() {
        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, AGENT_PACKAGE, 0).get()

        assertThat(result).isTrue()
    }

    @Test
    fun isAllowlisted_packageNotFound_returnsFalse() {
        `when`(packageManager.getPackageInfoAsUser(eq(AGENT_PACKAGE), anyInt(), eq(0)))
            .thenThrow(PackageManager.NameNotFoundException())

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_noSigningInfo_returnsFalse() {
        val packageInfo =
            PackageInfo().apply {
                packageName = AGENT_PACKAGE
                signingInfo = null
            }
        `when`(packageManager.getPackageInfoAsUser(eq(AGENT_PACKAGE), anyInt(), eq(0)))
            .thenReturn(packageInfo)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_noCertificates_returnsFalse() {
        val packageInfo =
            PackageInfo().apply {
                packageName = AGENT_PACKAGE
                signingInfo = SigningInfo()
            }
        `when`(packageManager.getPackageInfoAsUser(eq(AGENT_PACKAGE), anyInt(), eq(0)))
            .thenReturn(packageInfo)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_nullMap_returnFalse() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, null)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_emptyMap_returnFalse() {
        val allowlistMap = emptyMap<SignedPackage, List<SignedPackage>>()
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_agentMissingFromMap_returnsFalse() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        val otherAgentSignedPackage = buildSignedPackage(OTHER_AGENT_PACKAGE, SIGNATURE)
        val otherSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        val allowlistMap = mapOf(otherAgentSignedPackage to listOf(otherSignedPackage))
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_agentWrongSignature_returnsFalse() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, WRONG_SIGNATURE)
        val otherSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        val allowlistMap = mapOf(agentSignedPackage to listOf(otherSignedPackage))
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_allowlistContainsTarget_returnsTrue() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, SIGNATURE)
        val targetSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        val allowlistMap = mapOf(agentSignedPackage to listOf(targetSignedPackage))
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isTrue()
    }

    @Test
    fun isAllowlisted_allowlistDoesNotContainTarget_returnsFalse() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, SIGNATURE)
        val otherSignedPackage = buildSignedPackage(OTHER_TARGET_PACKAGE)
        val allowlistMap = mapOf(agentSignedPackage to listOf(otherSignedPackage))
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_allowlistContainsWildcard_returnsTrue() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, SIGNATURE)
        val wildcardSignedPackage = buildSignedPackage("*")
        val allowlistMap = mapOf(agentSignedPackage to listOf(wildcardSignedPackage))
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isTrue()
    }

    @Test
    fun isAllowlisted_providerError_returnsTrue() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_ERROR_PROVIDER, null)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isTrue()
    }

    @Test
    fun isAllowlisted_networkError_returnsTrue() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_ERROR_NETWORK, null)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isTrue()
    }

    @Test
    fun isAllowlisted_unknownError_returnsFalse() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_ERROR_UNKNOWN, null)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun isAllowlisted_multipleCertificates_usesLastCertificateShouldReturnTrue() {
        val packageInfo = PackageInfo().apply { this.packageName = AGENT_PACKAGE }
        val mockSigningInfo = mock(SigningInfo::class.java)
        `when`(mockSigningInfo.signingCertificateHistory)
            .thenReturn(arrayOf(WRONG_SIGNATURE, SIGNATURE))
        packageInfo.signingInfo = mockSigningInfo
        `when`(packageManager.getPackageInfoAsUser(eq(AGENT_PACKAGE), anyInt(), eq(0)))
            .thenReturn(packageInfo)

        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, SIGNATURE)
        val targetSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        val allowlistMap = mapOf(agentSignedPackage to listOf(targetSignedPackage))
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isTrue()
    }

    @Test
    fun isAllowlisted_multipleCertificates_useFirstCertificateShouldReturnFalse() {
        val packageInfo = PackageInfo().apply { this.packageName = AGENT_PACKAGE }
        val mockSigningInfo = mock(SigningInfo::class.java)
        `when`(mockSigningInfo.signingCertificateHistory)
            .thenReturn(arrayOf(WRONG_SIGNATURE, SIGNATURE))
        packageInfo.signingInfo = mockSigningInfo
        `when`(packageManager.getPackageInfoAsUser(eq(AGENT_PACKAGE), anyInt(), eq(0)))
            .thenReturn(packageInfo)

        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, WRONG_SIGNATURE)
        val targetSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        val allowlistMap = mapOf(agentSignedPackage to listOf(targetSignedPackage))
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun onAllowlistChanged_updatesMultipleAgents() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        setupPackageInfo(OTHER_AGENT_PACKAGE, SIGNATURE, 0)
        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, SIGNATURE)
        val otherAgentSignedPackage = buildSignedPackage(OTHER_AGENT_PACKAGE, SIGNATURE)
        val targetSignedPackage = buildSignedPackage(TARGET_PACKAGE)

        setupAllowlistResponse(
            AllowlistManager.RESPONSE_STATUS_SUCCESS,
            mapOf(
                agentSignedPackage to listOf(targetSignedPackage),
                otherAgentSignedPackage to listOf(targetSignedPackage),
            ),
        )
        allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()
        allowlistReader.isAllowlisted(OTHER_AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        @Suppress("UNCHECKED_CAST")
        val listenerCaptor =
            ArgumentCaptor.forClass(Consumer::class.java)
                as ArgumentCaptor<Consumer<AllowlistRequest>>
        verify(allowlistManager)
            .addOnAllowlistChangedListener(any(), any(), listenerCaptor.capture())

        // Update the allowlist, remove target from AGENT_PACKAGE and update OTHER_AGENT_PACKAGE to
        // contain OTHER_TARGET_PACKAGE.
        val otherTargetSignedPackage = buildSignedPackage(OTHER_TARGET_PACKAGE)
        setupAllowlistResponse(
            AllowlistManager.RESPONSE_STATUS_SUCCESS,
            mapOf(
                agentSignedPackage to emptyList(),
                otherAgentSignedPackage to listOf(otherTargetSignedPackage),
            ),
        )

        listenerCaptor.value.accept(mock(AllowlistRequest::class.java))

        assertThat(allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()).isFalse()
        assertThat(allowlistReader.isAllowlisted(OTHER_AGENT_PACKAGE, TARGET_PACKAGE, 0).get())
            .isFalse()
        assertThat(
                allowlistReader.isAllowlisted(OTHER_AGENT_PACKAGE, OTHER_TARGET_PACKAGE, 0).get()
            )
            .isTrue()
    }

    @Test
    fun onAllowlistChanged_removesDeletedAgentFromCache() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, SIGNATURE)
        val targetSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        setupAllowlistResponse(
            AllowlistManager.RESPONSE_STATUS_SUCCESS,
            mapOf(agentSignedPackage to listOf(targetSignedPackage)),
        )
        allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()
        // Notify the listener that the original agent has been removed
        // from the allowlist.
        @Suppress("UNCHECKED_CAST")
        val listenerCaptor =
            ArgumentCaptor.forClass(Consumer::class.java)
                as ArgumentCaptor<Consumer<AllowlistRequest>>
        verify(allowlistManager)
            .addOnAllowlistChangedListener(any(), any(), listenerCaptor.capture())
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, emptyMap())

        listenerCaptor.value.accept(mock(AllowlistRequest::class.java))
        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isFalse()
    }

    @Test
    fun onAllowlistChanged_queryFails_keepsOldCache() {
        setupPackageInfo(AGENT_PACKAGE, SIGNATURE, 0)
        val agentSignedPackage = buildSignedPackage(AGENT_PACKAGE, SIGNATURE)
        val targetSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        setupAllowlistResponse(
            AllowlistManager.RESPONSE_STATUS_SUCCESS,
            mapOf(agentSignedPackage to listOf(targetSignedPackage)),
        )
        allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()
        // Notify the listener that the allowlist has changed but the query
        // fails with a network error.
        @Suppress("UNCHECKED_CAST")
        val listenerCaptor =
            ArgumentCaptor.forClass(Consumer::class.java)
                as ArgumentCaptor<Consumer<AllowlistRequest>>
        verify(allowlistManager)
            .addOnAllowlistChangedListener(any(), any(), listenerCaptor.capture())
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_ERROR_NETWORK, null)

        listenerCaptor.value.accept(mock(AllowlistRequest::class.java))
        val result = allowlistReader.isAllowlisted(AGENT_PACKAGE, TARGET_PACKAGE, 0).get()

        assertThat(result).isTrue()
    }

    @Test
    fun isAllowlisted_enforcesCacheCapacity_returnsCorrectResult() {
        val agents = (1..50).map { "com.example.agent\$it" }
        for (agent in agents) {
            setupPackageInfo(agent, SIGNATURE, 0)
        }
        val targetSignedPackage = buildSignedPackage(TARGET_PACKAGE)
        val allowlistMap = agents.associate {
            buildSignedPackage(it, SIGNATURE) to listOf(targetSignedPackage)
        }
        setupAllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, allowlistMap)

        for (i in 1..50) {
            for (agent in agents) {
                assertThat(allowlistReader.isAllowlisted(agent, TARGET_PACKAGE, 0).get()).isTrue()
                assertThat(allowlistReader.isAllowlisted(agent, OTHER_TARGET_PACKAGE, 0).get())
                    .isFalse()
                assertThat(
                        allowlistReader.isAllowlisted(OTHER_AGENT_PACKAGE, TARGET_PACKAGE, 0).get()
                    )
                    .isFalse()
            }
        }
        // No matter how many times isAllowlisted is called, the allowlist
        // listener should only be added once.
        verify(allowlistManager, times(1)).addOnAllowlistChangedListener(any(), any(), any())
    }

    private fun setupPackageInfo(packageName: String, signature: Signature, userId: Int) {
        val packageInfo = PackageInfo().apply { this.packageName = packageName }
        val mockSigningInfo = mock(SigningInfo::class.java)
        `when`(mockSigningInfo.signingCertificateHistory).thenReturn(arrayOf(signature))
        packageInfo.signingInfo = mockSigningInfo

        `when`(packageManager.getPackageInfoAsUser(eq(packageName), anyInt(), eq(userId)))
            .thenReturn(packageInfo)
    }

    private fun buildSignedPackage(
        packageName: String,
        signature: Signature? = null,
    ): SignedPackage {
        val digest = signature?.let { PackageUtils.computeSha256DigestBytes(it.toByteArray()) }
        return SignedPackage(packageName, digest)
    }

    private fun setupAllowlistResponse(
        status: Int,
        allowlistMap: Map<SignedPackage, List<SignedPackage>>?,
    ) {
        doAnswer { invocation ->
                val consumer = invocation.arguments[2] as Consumer<AllowlistResponse>
                val data = Bundle()
                if (allowlistMap != null) {
                    val multiMap = SignedPackageMultiMap(allowlistMap)
                    data.putParcelable(
                        AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                        multiMap,
                    )
                }
                consumer.accept(AllowlistResponse(status, data))
            }
            .`when`(allowlistManager)
            .queryAllowlist(any(), any(), any())
    }

    companion object {
        private const val TEST_CACHE_SIZE = 2
        private const val AGENT_PACKAGE = "com.example.agent"
        private const val TARGET_PACKAGE = "com.example.target"
        private const val OTHER_AGENT_PACKAGE = "com.example.agent.other"
        private const val OTHER_TARGET_PACKAGE = "com.example.other"
        private val SIGNATURE = Signature("1234".toByteArray())
        private val WRONG_SIGNATURE = Signature("2345".toByteArray())
    }
}
