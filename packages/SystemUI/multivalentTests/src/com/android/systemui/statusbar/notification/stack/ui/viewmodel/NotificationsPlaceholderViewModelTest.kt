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

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.notifications.ui.YSpace
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class NotificationsPlaceholderViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val vmFactory = kosmos.notificationsPlaceholderViewModelFactory
    private val goneViewModel = vmFactory.create(Scenes.Gone)
    private val lockscreenViewModel = vmFactory.create(Scenes.Lockscreen)
    private val quickSettingsViewModel = vmFactory.create(Scenes.QuickSettings)
    private val shadeViewModel = vmFactory.create(Scenes.Shade)

    private val underTest by lazy { kosmos.notificationsPlaceholderViewModel }

    @Test
    fun onScrimBoundsChanged() =
        kosmos.testScope.runTest {
            val bounds = ShadeScrimBounds(left = 5f, top = 15f, right = 25f, bottom = 35f)
            underTest.onScrimBoundsChanged(bounds)
            val scrimBounds by
                collectLastValue(
                    kosmos.notificationStackAppearanceInteractor.notificationShadeScrimBounds
                )
            assertThat(scrimBounds).isEqualTo(bounds)
        }

    @Test
    fun onStackScrollTopChanged_lockscreenAndShade() =
        kosmos.runTest {
            var scrollTop: Float? = Float.NaN
            val disposable =
                kosmos.notificationScrollViewModel.stackScrollTop.observe { value ->
                    scrollTop = value
                }

            // Given: Initial value is zero.
            assertThat(scrollTop).isEqualTo(0f)

            // When: LS sends updates
            lockscreenViewModel.setStackScrollTop(100f)
            // Then: Observers are notified
            assertThat(scrollTop).isEqualTo(100f)

            // When: Shade sends an update
            shadeViewModel.setStackScrollTop(80f)
            // Then: Observers get the value from Shade
            assertThat(scrollTop).isEqualTo(80f)

            // When: Shade is removed
            shadeViewModel.resetStackScrollTop()
            // Then: Observers get the LS values again
            assertThat(scrollTop).isEqualTo(100f)

            // When: LS is removed
            lockscreenViewModel.resetStackScrollTop()
            // Then: Observers get the default value again
            assertThat(scrollTop).isEqualTo(0f)

            disposable.dispose()
        }

    @Test
    fun onStackBoundsChanged_quickSettingsAndShade() =
        kosmos.runTest {
            var stackBounds: YSpace? = YSpace(Float.NaN, Float.NaN)
            val disposable =
                kosmos.notificationScrollViewModel.stackBounds.observe { value ->
                    stackBounds = value
                }

            // Given: Initial bounds are zero.
            assertThat(stackBounds).isEqualTo(YSpace.Zero)

            // When: Shade sends an update
            shadeViewModel.setStackBounds(YSpace(0f, 100f))
            // Then: Observers are notified
            assertThat(stackBounds).isEqualTo(YSpace(0f, 100f))

            // When: Shade sends more updates
            shadeViewModel.setStackBounds(YSpace(50f, 150f))
            // Then: Observers are notified
            assertThat(stackBounds).isEqualTo(YSpace(50f, 150f))

            // When: QS sends updates
            quickSettingsViewModel.setStackBounds(YSpace(100f, 200f))
            // Then: Observers still see the last value from Shade
            assertThat(stackBounds).isEqualTo(YSpace(50f, 150f))

            // When: Shade is removed
            shadeViewModel.resetStackBounds()
            // Then: Observers get the QS values
            assertThat(stackBounds).isEqualTo(YSpace(100f, 200f))

            disposable.dispose()
        }

    @Test
    fun onHunBoundsChanged_goneToShade() =
        kosmos.runTest {
            var hunBounds: YSpace? = YSpace(Float.NaN, Float.NaN)
            val disposable =
                kosmos.notificationScrollViewModel.headsUpBounds.observe { value ->
                    hunBounds = value
                }

            // Given: Initial bounds are zero.
            assertThat(hunBounds).isEqualTo(YSpace.Zero)

            // When: HUN is idle over Gone
            goneViewModel.setHeadsUpBounds(YSpace(50f, 100f))
            // Then: Observers are notified
            assertThat(hunBounds).isEqualTo(YSpace(50f, 100f))

            // When: Shade sends an update
            shadeViewModel.setHeadsUpBounds(YSpace(100f, 150f))
            // Then: Observers still have the value from Gone
            assertThat(hunBounds).isEqualTo(YSpace(50f, 100f))

            // When: Gone sends more updates
            goneViewModel.setHeadsUpBounds(YSpace(75f, 125f))
            // Then: Observers get notified
            assertThat(hunBounds).isEqualTo(YSpace(75f, 125f))

            // When: The Gone scene is gone
            goneViewModel.resetHeadsUpBounds()
            // Then: Observers get the Shade values
            assertThat(hunBounds).isEqualTo(YSpace(100f, 150f))

            disposable.dispose()
        }

    @Test
    fun onStackAlphaChanged_shadeFadesOut() =
        kosmos.runTest {
            var stackAlpha: Float = Float.NaN
            val disposable =
                kosmos.notificationScrollViewModel.stackPlaceholderAlpha.observe { value ->
                    stackAlpha = value
                }

            // Given: Initial alpha is 1f.
            assertThat(stackAlpha).isEqualTo(1f)

            // When: Shade sends an update
            shadeViewModel.setStackAlpha(1f)
            // Then: Observers are notified
            assertThat(stackAlpha).isEqualTo(1f)

            // When: Shade starts to fade out
            shadeViewModel.setStackAlpha(.8f)
            // Then: Observers are notified
            assertThat(stackAlpha).isEqualTo(.8f)

            // When: Shade continues to fade out
            shadeViewModel.setStackAlpha(.4f)
            // Then: Observers are notified
            assertThat(stackAlpha).isEqualTo(.4f)

            // When: Shade is removed
            shadeViewModel.resetStackAlpha()
            // Then: Observers get 1f again
            assertThat(stackAlpha).isEqualTo(1f)

            disposable.dispose()
        }
}
