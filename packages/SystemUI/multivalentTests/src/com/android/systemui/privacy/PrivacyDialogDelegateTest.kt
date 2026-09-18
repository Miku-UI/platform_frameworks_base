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

package com.android.systemui.privacy

import android.content.Intent
import android.testing.TestableLooper
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.systemUIDialogDotFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class PrivacyDialogDelegateTest : SysuiTestCase() {

    companion object {
        private const val TEST_PACKAGE_NAME = "test_pkg"
        private const val TEST_USER_ID = 0
        private const val TEST_PERM_GROUP = "test_perm_group"
    }

    private val kosmos = testKosmos()

    @Mock
    private lateinit var starter: (String, Int, CharSequence?, Intent?) -> Unit
    private lateinit var delegate: PrivacyDialogDelegate
    private lateinit var dialog: SystemUIDialog

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @After
    fun teardown() {
        if (this::dialog.isInitialized) {
            dialog.dismiss()
        }
    }

    @Test
    fun testStarterCalledWithCorrectParams() {
        val list = listOf(
                PrivacyDialogDelegate.PrivacyElement(
                        PrivacyType.TYPE_MICROPHONE,
                        TEST_PACKAGE_NAME,
                        TEST_USER_ID,
                        "App",
                        null,
                        null,
                        null,
                        0L,
                        false,
                        false,
                        false,
                        TEST_PERM_GROUP,
                        null
                )
        )
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        dialog.requireViewById<View>(R.id.privacy_item).callOnClick()
        verify(starter).invoke(TEST_PACKAGE_NAME, TEST_USER_ID, null, null)
    }

    @Test
    fun testDismissListenerCalledOnDismiss() {
        delegate =
            PrivacyDialogDelegate(context, emptyList(), starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        val dismissListener = mock(PrivacyDialogDelegate.OnDialogDismissed::class.java)
        delegate.addOnDismissListener(dismissListener)
        dialog.show()

        verify(dismissListener, never()).onDialogDismissed()
        dialog.dismiss()
        verify(dismissListener).onDialogDismissed()
    }

    @Test
    fun testDismissListenerCalledImmediatelyIfDialogAlreadyDismissed() {
        delegate =
            PrivacyDialogDelegate(context, emptyList(), starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        val dismissListener = mock(PrivacyDialogDelegate.OnDialogDismissed::class.java)
        dialog.show()
        dialog.dismiss()

        delegate.addOnDismissListener(dismissListener)
        verify(dismissListener).onDialogDismissed()
    }

    @Test
    fun testCorrectNumElements() {
        val list = listOf(
                PrivacyDialogDelegate.PrivacyElement(
                        PrivacyType.TYPE_CAMERA,
                        TEST_PACKAGE_NAME,
                        TEST_USER_ID,
                        "App",
                        null,
                        null,
                        null,
                        0L,
                        true,
                        false,
                        false,
                        TEST_PERM_GROUP,
                        null
                ),
                PrivacyDialogDelegate.PrivacyElement(
                        PrivacyType.TYPE_MICROPHONE,
                        TEST_PACKAGE_NAME,
                        TEST_USER_ID,
                        "App",
                        null,
                        null,
                        null,
                        0L,
                        false,
                        false,
                        false,
                        TEST_PERM_GROUP,
                        null
                )
        )
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<ViewGroup>(R.id.root).childCount).isEqualTo(2)
    }

    @Test
    fun testUsingText() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_CAMERA,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                null,
                null,
                0L,
                true,
                false,
                false,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<TextView>(R.id.text).text).isEqualTo(
                context.getString(
                        R.string.ongoing_privacy_dialog_using_op,
                        element.applicationName,
                        element.type.getName(context)
                )
        )
    }

    @Test
    fun testRecentText() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                null,
                null,
                0L,
                false,
                false,
                false,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<TextView>(R.id.text).text).isEqualTo(
                context.getString(
                        R.string.ongoing_privacy_dialog_recent_op,
                        element.applicationName,
                        element.type.getName(context)
                )
        )
    }

    @Test
    fun testEnterprise() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                null,
                null,
                0L,
                false,
                true,
                false,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<TextView>(R.id.text).text.toString()).contains(
                context.getString(R.string.ongoing_privacy_dialog_enterprise)
        )
    }

    @Test
    fun testPhoneCall() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                null,
                null,
                0L,
                false,
                false,
                true,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<TextView>(R.id.text).text.toString()).contains(
                context.getString(R.string.ongoing_privacy_dialog_phonecall)
        )
    }

    @Test
    fun testPhoneCallNotClickable() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                null,
                null,
                0L,
                false,
                false,
                true,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<View>(R.id.privacy_item).isClickable).isFalse()
        assertThat(dialog.requireViewById<View>(R.id.chevron).visibility).isEqualTo(View.GONE)
    }

    @Test
    fun testProxyLabel() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                null,
                "proxyLabel",
                0L,
                false,
                false,
                true,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<TextView>(R.id.text).text.toString()).contains(
                context.getString(
                        R.string.ongoing_privacy_dialog_attribution_text,
                        element.proxyLabel
                )
        )
    }

    @Test
    fun testSubattribution() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                "For subattribution",
                null,
                0L,
                true,
                false,
                false,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<TextView>(R.id.text).text.toString()).contains(
                context.getString(
                        R.string.ongoing_privacy_dialog_attribution_label,
                        element.attributionLabel
                )
        )
    }

    @Test
    fun testSubattributionAndProxyLabel() {
        val element = PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                "For subattribution",
                "proxy label",
                0L,
                true,
                false,
                false,
                TEST_PERM_GROUP,
                null
        )

        val list = listOf(element)
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()
        assertThat(dialog.requireViewById<TextView>(R.id.text).text.toString()).contains(
                context.getString(
                        R.string.ongoing_privacy_dialog_attribution_proxy_label,
                        element.attributionLabel, element.proxyLabel
                )
        )
    }

    @Test
    fun testDialogHasTitle() {
        // Dialog must have a non-empty title for a11y purposes.

        val list = listOf(
            PrivacyDialogDelegate.PrivacyElement(
                PrivacyType.TYPE_MICROPHONE,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                "App",
                null,
                null,
                null,
                0L,
                false,
                false,
                false,
                TEST_PERM_GROUP,
                null
            )
        )
        delegate = PrivacyDialogDelegate(context, list, starter, kosmos.systemUIDialogDotFactory)
        dialog = delegate.createDialog()
        dialog.show()

        assertThat(TextUtils.isEmpty(dialog.window?.attributes?.title)).isFalse()
    }
}
