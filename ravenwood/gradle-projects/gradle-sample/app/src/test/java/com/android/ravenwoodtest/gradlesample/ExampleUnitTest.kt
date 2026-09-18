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
package com.android.ravenwoodtest.gradlesample


import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.internal.management.ManagementFactory

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    @android.platform.test.annotations.DisabledOnRavenwood
    fun doNotRunOnRavenwood() {
        assertNotEquals("1", System.getProperty("android.ravenwood.version"))
    }

    @Test
    fun testDumpEnvironment() {
        // Print command line args
        val runtimeMxBean = ManagementFactory.getRuntimeMXBean()

        // Retrieve the list of JVM arguments passed to the 'java' command
        val jvmArgs: List<String> = runtimeMxBean.inputArguments

        println("JVM Arguments passed to the 'java' command:")
        if (jvmArgs.isEmpty()) {
            println("  (None)")
        } else {
            jvmArgs.forEach { arg ->
                println("  $arg")
            }
        }

        println("JVM Props:")
        System.getProperties().forEach { key, value ->
            println("  $key=$value")
        }

        println("Environment:")
        val filter = Regex("^.*(RAVEN|ROBO|GRADLE|JAVA|PATH)")
        System.getenv()
            .filter { it.key.matches(filter) }
            .forEach { key, value ->
            println("  $key=$value")
        }
    }

    @Test
    fun testIsUserAMonkey() {
        android.app.ActivityManager.isUserAMonkey()
    }

    @Test
    fun resourceTest() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val res = context.resources
        assertEquals("Ravenwood Gradle Sample", res.getString(R.string.app_name))
    }
}