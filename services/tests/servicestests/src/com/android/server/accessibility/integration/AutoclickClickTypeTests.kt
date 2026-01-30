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
package com.android.server.accessibility.integration

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
import android.os.Bundle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.view.Display.DEFAULT_DISPLAY
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.android.server.accessibility.Flags
import kotlin.test.assertEquals
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.desktop.DesktopMouseTestRule

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
class AutoclickClickTypeTests {
    @Rule(order = 0)
    @JvmField
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Rule(order = 1)
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<TestClickActivity> =
        ActivityScenarioRule(TestClickActivity::class.java)

    @Rule(order = 2)
    @JvmField
    val autoclickEnabledSettingRule: SettingsStateChangerRule =
        SettingsStateChangerRule(
            InstrumentationRegistry.getInstrumentation().context,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_ENABLED,
            "1"
        )

    @Rule(order = 3)
    @JvmField
    val desktopMouseTestRule = DesktopMouseTestRule()
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var uiDevice: UiDevice
    private lateinit var testClickButton: Button

    @Before
    fun setup() {
        Configurator.getInstance().setUiAutomationFlags(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiDevice = UiDevice.getInstance(instrumentation)

        activityScenarioRule.scenario.onActivity { activity ->
            testClickButton = activity.findViewById(TEST_BUTTON_ID)
        }

        initiateAutoclickPanel(
            InstrumentationRegistry.getInstrumentation().context, uiDevice, desktopMouseTestRule
        )
    }

    private fun getViewCenter(view: View): Pair<Int, Int> {
        val xOnScreen = view.locationOnScreen[0]
        val yOnScreen = view.locationOnScreen[1]
        val centerX = xOnScreen + (view.width / 2)
        val centerY = yOnScreen + (view.height / 2)
        return Pair(centerX, centerY)
    }

    // Move the mouse to the center of the view
    private fun moveMouseToView(view: View) {
        val (centerX, centerY) = getViewCenter(view)
        desktopMouseTestRule.move(DEFAULT_DISPLAY, centerX, centerY)
    }

    // Move the mouse a given distance away from the center of the view.
    private fun moveMouseAwayFromView(view: View, deltaX: Int, deltaY: Int) {
        val (centerX, centerY) = getViewCenter(view)
        desktopMouseTestRule.move(DEFAULT_DISPLAY, centerX + deltaX, centerY + deltaY)
    }

    private fun moveMouseToScrollButton(resourceId: String) {
        val scrollButton = findObject(
            uiDevice, By.res(resourceId)
        )
        desktopMouseTestRule.move(
            DEFAULT_DISPLAY,
            scrollButton.visibleCenter.x,
            scrollButton.visibleCenter.y
        )
    }

    @Test
    fun performLeftClick_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, LEFT_CLICK_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == LEFT_CLICK_TEXT
        }
    }

    @Test
    fun performDoubleClick_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, DOUBLE_CLICK_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == DOUBLE_CLICK_TEXT
        }
    }

    @Test
    fun performRightClick_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, RIGHT_CLICK_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == RIGHT_CLICK_TEXT
        }
    }

    @Test
    fun performLongPress_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, LONG_PRESS_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == LONG_PRESS_TEXT
        }
    }

    @Test
    fun performDrag_buttonReflectsClickType() {
        val (testClickButtonInitialX, testClickButtonInitialY) = getViewCenter(testClickButton)

        changeClickType(uiDevice, desktopMouseTestRule, DRAG_CLICK_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        // Wait until the button detects a long press, this confirms the initial drag click has
        // completed.
        waitAndAssert {
            testClickButton.text == LONG_PRESS_TEXT
        }

        val dragDistanceX = 50
        val dragDistanceY = 100
        moveMouseAwayFromView(testClickButton, dragDistanceX, dragDistanceY)

        // Wait for the click type to switch back to left click which signals the drag is done.
        findObject(uiDevice, By.res(LEFT_CLICK_BUTTON_LAYOUT_ID))

        // Use a small tolerance when verifying the new button location to account for the autoclick
        // default slop.
        val (testClickButtonCurrentX, testClickButtonCurrentY) = getViewCenter(testClickButton)
        assertEquals(
            testClickButtonCurrentX.toDouble(),
            (testClickButtonInitialX + dragDistanceX).toDouble(),
            20.0
        )
        assertEquals(
            testClickButtonCurrentY.toDouble(),
            (testClickButtonInitialY + dragDistanceY).toDouble(),
            20.0
        )
    }

    @Test
    fun performScroll_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, SCROLL_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        // Scroll to each button in this order to prevent the exit button from being inadvertently
        // hovered.
        moveMouseToScrollButton(SCROLL_RIGHT_BUTTON_LAYOUT_ID)
        waitAndAssert {
            testClickButton.text == SCROLL_RIGHT_TEXT
        }

        moveMouseToScrollButton(SCROLL_UP_BUTTON_LAYOUT_ID)
        waitAndAssert {
            testClickButton.text == SCROLL_UP_TEXT
        }

        moveMouseToScrollButton(SCROLL_LEFT_BUTTON_LAYOUT_ID)
        waitAndAssert {
            testClickButton.text == SCROLL_LEFT_TEXT
        }

        moveMouseToScrollButton(SCROLL_DOWN_BUTTON_LAYOUT_ID)
        waitAndAssert {
            testClickButton.text == SCROLL_DOWN_TEXT
        }

        // Close the scroll panel.
        moveMouseToScrollButton(SCROLL_EXIT_BUTTON_LAYOUT_ID)
        waitAndAssert {
            uiDevice.findObject(By.res(SCROLL_EXIT_BUTTON_LAYOUT_ID)) == null
        }
    }

    // Test activity responsible for receiving clicks and updating its UI depending on the click
    // type.
    class TestClickActivity : Activity() {
        private lateinit var gestureDetector: GestureDetector
        private var initialX = 0f
        private var initialY = 0f

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val contentLayout = LinearLayout(this)
            contentLayout.setOrientation(LinearLayout.VERTICAL)

            val testButton = Button(this)
            testButton.id = TEST_BUTTON_ID

            gestureDetector =
                GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        testButton.text = DOUBLE_CLICK_TEXT
                        return true
                    }
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        testButton.text = LEFT_CLICK_TEXT
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        testButton.text = LONG_PRESS_TEXT
                    }
                })

            testButton.setOnTouchListener { view, event ->
                // Move the button when drag detected.
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Store offset from touch point to buttons's top-left corner.
                        initialX = event.rawX - view.x
                        initialY = event.rawY - view.y
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Set buttons's new position directly based on mouse's raw position and initial offset.
                        view.x = event.rawX - initialX
                        view.y = event.rawY - initialY
                    }
                }

                gestureDetector.onTouchEvent(event)
            }

            // Right click and scroll listener.
            val genericMotionListener = View.OnGenericMotionListener { _, motionEvent ->
                if (motionEvent.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    if (motionEvent.action == MotionEvent.ACTION_SCROLL) {
                        val vScroll = motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL, 0)
                        val hScroll = motionEvent.getAxisValue(MotionEvent.AXIS_HSCROLL, 0)

                        if (vScroll > 0) {
                            testButton.text = SCROLL_UP_TEXT
                        } else if (vScroll < 0) {
                            testButton.text = SCROLL_DOWN_TEXT
                        } else if (hScroll > 0) {
                            testButton.text = SCROLL_LEFT_TEXT
                        } else if (hScroll < 0) {
                            testButton.text = SCROLL_RIGHT_TEXT
                        }
                        true
                    } else if (motionEvent.action == MotionEvent.ACTION_BUTTON_PRESS && motionEvent.actionButton == MotionEvent.BUTTON_SECONDARY) {
                        testButton.text = RIGHT_CLICK_TEXT
                        true
                    }
                }
                false
            }
            testButton.setOnGenericMotionListener(genericMotionListener)

            contentLayout.addView(testButton)
            setContentView(contentLayout)
        }
    }

    private companion object {
        private val TEST_BUTTON_ID = View.generateViewId()

        // Button text.
        private val LEFT_CLICK_TEXT = "Left Clicked!"
        private val DOUBLE_CLICK_TEXT = "Double Clicked!"
        private val RIGHT_CLICK_TEXT = "Right Clicked!"
        private val LONG_PRESS_TEXT = "Long Press Clicked!"
        private val SCROLL_UP_TEXT = "Scrolled Up!"
        private val SCROLL_DOWN_TEXT = "Scrolled Down!"
        private val SCROLL_LEFT_TEXT = "Scrolled Left!"
        private val SCROLL_RIGHT_TEXT = "Scrolled Right!"

        @BeforeClass
        @JvmStatic
        fun setupBeforeClass() {
            // Disables showing an SDK version dialog to prevent it from interfering with the test.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("setprop debug.wm.disable_deprecated_target_sdk_dialog 1")
                .close()
        }

        @AfterClass
        @JvmStatic
        fun teardownAfterClass() {
            // Wait for the Autoclick panel to be closed.
            waitAndAssert {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .findObject(By.res(AUTOCLICK_PANEL_ID)) == null
            }

            // Re-enable SDK version dialog.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("setprop debug.wm.disable_deprecated_target_sdk_dialog 0")
                .close()
        }
    }
}
