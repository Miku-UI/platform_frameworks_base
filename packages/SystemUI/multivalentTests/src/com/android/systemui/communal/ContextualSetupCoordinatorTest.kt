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

package com.android.systemui.communal

import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_UPRIGHT_CHARGING_DREAMS_SETUP
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.communal.domain.definition.SetupTarget
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.activityStarter
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ContextualSetupCoordinatorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val activityStarter = kosmos.activityStarter
    private val repository = kosmos.contextualSetupRepository.fake
    private val definition = kosmos.contextualSetupDefinitionFactory("test_def").fake

    private val Kosmos.interactor by
        Kosmos.Fixture { contextualSetupInteractorFactory(setOf(definition)) }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            ContextualSetupCoordinator(
                context = context,
                activityStarter = activityStarter,
                contextualSetupInteractor = interactor,
                bgScope = testScope.backgroundScope,
            )
        }

    @Test
    @EnableFlags(FLAG_UPRIGHT_CHARGING_DREAMS_SETUP)
    fun start_launchesActivityTarget() =
        kosmos.runTest {
            val componentName = ComponentName("com.test", "TestActivity")
            val target = SetupTarget.Activity(componentName)
            definition.target = target

            underTest.start()
            definition.setIsReady(true)
            runCurrent()

            val intentCaptor = argumentCaptor<Intent>()
            verify(activityStarter)
                .startActivity(intentCaptor.capture(), eq(false), eq(null), eq(true))
            val intent = intentCaptor.firstValue
            assertThat(intent.component).isEqualTo(componentName)
            assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
            assertThat(intent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).isNotEqualTo(0)
        }

    @Test
    @DisableFlags(FLAG_UPRIGHT_CHARGING_DREAMS_SETUP)
    fun start_flagDisabled_doesNotLaunch() =
        kosmos.runTest {
            val componentName = ComponentName("com.test", "TestActivity")
            val target = SetupTarget.Activity(componentName)
            definition.target = target

            underTest.start()
            definition.setIsReady(true)
            runCurrent()

            verify(activityStarter, never())
                .startActivity(
                    any<Intent>(),
                    any<Boolean>(),
                    any<ActivityTransitionAnimator.Controller>(),
                    any<Boolean>(),
                )
        }
}
