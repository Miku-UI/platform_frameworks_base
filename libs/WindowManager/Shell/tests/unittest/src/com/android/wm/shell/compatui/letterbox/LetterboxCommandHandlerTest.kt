/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.graphics.Color
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import java.io.PrintWriter
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [LetterboxCommandHandler].
 *
 * Build/Install/Run: atest WMShellUnitTests:LetterboxCommandHandlerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxCommandHandlerTest : ShellTestCase() {

    @Test
    fun `backgroundColor command with params sets background color`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("backgroundColor", "red"))
            r.verifySetLetterboxBackgroundColor(Color.valueOf(Color.RED))
        }
    }

    @Test
    fun `backgroundColor command without params prints current color`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("backgroundColor"))
            r.verifyGetLetterboxBackgroundColor()
        }
    }

    @Test
    fun `backgroundColorReset command resets background color`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("backgroundColorReset"))
            r.verifyResetLetterboxBackgroundColor()
        }
    }

    @Test
    fun `backgroundColorResource command sets background color resource`() {
        runTestScenario { r ->
            val resourceName = "white"
            val colorId = 1234
            r.setupColorResource(resourceName, colorId)

            r.onShellCommand(arrayOf("backgroundColorResource", resourceName))

            r.verifySetLetterboxBackgroundColorResourceId(colorId)
        }
    }

    @Test
    fun `cornerRadius command with params sets corner radius`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("cornerRadius", "10"))
            r.verifySetLetterboxActivityCornersRadius(10)
        }
    }

    @Test
    fun `cornerRadius command without params prints current radius`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("cornerRadius"))
            r.verifyGetLetterboxActivityCornersRadius()
        }
    }

    @Test
    fun `cornerRadiusReset command resets corner radius`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("cornerRadiusReset"))
            r.verifyResetLetterboxActivityCornersRadius()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `backgroundType command with params sets background type`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("backgroundType", "1"))
            r.verifySetLetterboxBackgroundType(1)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `backgroundType command without params prints current type`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("backgroundType"))
            r.verifyGetLetterboxBackgroundType()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `backgroundTypeReset command resets background type`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("backgroundTypeReset"))
            r.verifyResetLetterboxBackgroundType()
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `backgroundType command does nothing when flag disabled`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("backgroundType", "1"))
            r.onShellCommand(arrayOf("backgroundType"))
            r.onShellCommand(arrayOf("backgroundTypeReset"))
            r.verifyNoInteraction()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperBlurRadius command with params sets blur radius`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperBlurRadius", "20"))
            r.verifySetLetterboxBackgroundWallpaperBlurRadiusPx(20)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperBlurRadius command without params prints current blur radius`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperBlurRadius"))
            r.verifyGetLetterboxBackgroundWallpaperBlurRadiusPx()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperBlurRadiusReset command resets blur radius`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperBlurRadiusReset"))
            r.verifyResetLetterboxBackgroundWallpaperBlurRadiusPx()
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperBlurRadius command does nothing when flag disabled`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperBlurRadius", "20"))
            r.onShellCommand(arrayOf("wallpaperBlurRadius"))
            r.onShellCommand(arrayOf("wallpaperBlurRadiusReset"))
            r.verifyNoInteraction()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperDarkScrimAlpha command with params sets dark scrim alpha`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperDarkScrimAlpha", "0.6"))
            r.verifySetLetterboxBackgroundWallpaperDarkScrimAlpha(0.6f)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperDarkScrimAlpha command without params prints current alpha`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperDarkScrimAlpha"))
            r.verifyGetLetterboxBackgroundWallpaperDarkScrimAlpha()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperDarkScrimAlphaReset command resets alpha`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperDarkScrimAlphaReset"))
            r.verifyResetLetterboxBackgroundWallpaperDarkScrimAlpha()
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_ENABLE_BLUR_WALLPAPER_IN_SHELL)
    fun `wallpaperDarkScrimAlpha command does nothing when flag disabled`() {
        runTestScenario { r ->
            r.onShellCommand(arrayOf("wallpaperDarkScrimAlpha", "0.6"))
            r.onShellCommand(arrayOf("wallpaperDarkScrimAlpha"))
            r.onShellCommand(arrayOf("wallpaperDarkScrimAlphaReset"))
            r.verifyNoInteraction()
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<LetterboxCommandHandlerRobotTest>) {
        val robot = LetterboxCommandHandlerRobotTest()
        consumer.accept(robot)
    }

    inner class LetterboxCommandHandlerRobotTest {

        private val letterboxConfiguration =
            mock<LetterboxConfiguration> {
                on { getLetterboxBackgroundColor() } doReturn Color.valueOf(Color.WHITE)
            }
        private val shellInit = mock<ShellInit>()
        private val shellCommandHandler = mock<ShellCommandHandler>()
        private val pw = mock<PrintWriter>()
        private val commandHandler: LetterboxCommandHandler

        init {
            spyOn(mContext.resources)
            commandHandler =
                LetterboxCommandHandler(
                    mContext,
                    shellInit,
                    shellCommandHandler,
                    letterboxConfiguration,
                )
        }

        fun onShellCommand(args: Array<String>) {
            commandHandler.onShellCommand(args, pw)
        }

        fun verifySetLetterboxBackgroundColor(color: Color) {
            verify(letterboxConfiguration).setLetterboxBackgroundColor(eq(color))
        }

        fun verifyGetLetterboxBackgroundColor() {
            verify(letterboxConfiguration).getLetterboxBackgroundColor()
        }

        fun verifyResetLetterboxBackgroundColor() {
            verify(letterboxConfiguration).resetLetterboxBackgroundColor()
        }

        fun setupColorResource(name: String, id: Int) {
            doReturn(id).`when`(mContext.resources).getIdentifier(eq(name), eq("color"), any())
        }

        fun verifySetLetterboxBackgroundColorResourceId(colorId: Int) {
            verify(letterboxConfiguration).setLetterboxBackgroundColorResourceId(eq(colorId))
        }

        fun verifySetLetterboxActivityCornersRadius(radius: Int) {
            verify(letterboxConfiguration).setLetterboxActivityCornersRadius(eq(radius))
        }

        fun verifyGetLetterboxActivityCornersRadius() {
            verify(letterboxConfiguration).getLetterboxActivityCornersRadius()
        }

        fun verifyResetLetterboxActivityCornersRadius() {
            verify(letterboxConfiguration).resetLetterboxActivityCornersRadius()
        }

        fun verifySetLetterboxBackgroundType(type: Int) {
            verify(letterboxConfiguration).setLetterboxBackgroundType(eq(type))
        }

        fun verifyGetLetterboxBackgroundType() {
            verify(letterboxConfiguration).getLetterboxBackgroundType()
        }

        fun verifyResetLetterboxBackgroundType() {
            verify(letterboxConfiguration).resetLetterboxBackgroundType()
        }

        fun verifySetLetterboxBackgroundWallpaperBlurRadiusPx(radius: Int) {
            verify(letterboxConfiguration).setLetterboxBackgroundWallpaperBlurRadiusPx(eq(radius))
        }

        fun verifyGetLetterboxBackgroundWallpaperBlurRadiusPx() {
            verify(letterboxConfiguration).getLetterboxBackgroundWallpaperBlurRadiusPx()
        }

        fun verifyResetLetterboxBackgroundWallpaperBlurRadiusPx() {
            verify(letterboxConfiguration).resetLetterboxBackgroundWallpaperBlurRadiusPx()
        }

        fun verifySetLetterboxBackgroundWallpaperDarkScrimAlpha(alpha: Float) {
            verify(letterboxConfiguration).setLetterboxBackgroundWallpaperDarkScrimAlpha(eq(alpha))
        }

        fun verifyGetLetterboxBackgroundWallpaperDarkScrimAlpha() {
            verify(letterboxConfiguration).getLetterboxBackgroundWallpaperDarkScrimAlpha()
        }

        fun verifyResetLetterboxBackgroundWallpaperDarkScrimAlpha() {
            verify(letterboxConfiguration).resetLetterboxBackgroundWallpaperDarkScrimAlpha()
        }

        fun verifyNoInteraction() {
            org.mockito.kotlin.verifyNoInteractions(letterboxConfiguration)
        }
    }
}
