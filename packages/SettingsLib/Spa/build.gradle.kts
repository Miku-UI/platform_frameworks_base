/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.android) apply false
}

val androidTop: String = file("../../../../..").canonicalPath

allprojects {
    extra["androidTop"] = androidTop
    extra["jetpackComposeVersion"] = "1.9.0-beta01"
}

allprojects {
    val projectBuildPath = path.replace(':', File.separatorChar)
    layout.buildDirectory = file("$androidTop/out/gradle-spa/$projectBuildPath/build")
}

subprojects {
    plugins.withType<BasePlugin> {
        the(CommonExtension::class).apply { compileSdk = 36 }

        configure<JavaPluginExtension> {
            toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get())) }
        }
    }

    plugins.withType<AppPlugin> {
        configure<ApplicationExtension> { defaultConfig { targetSdk = 36 } }
    }

    plugins.withType<LibraryPlugin> {
        configure<LibraryExtension> { testOptions { targetSdk = 36 } }
    }
}
