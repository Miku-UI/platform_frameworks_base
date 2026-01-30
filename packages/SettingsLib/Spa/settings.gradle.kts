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

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    rulesMode.set(RulesMode.FAIL_ON_PROJECT_RULES)

    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("android")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.PhilJay")
            }
        }
    }
}
rootProject.name = "SpaLib"
include(":Spa")
project(":Spa").projectDir = file("spa")
include(":Spa:gallery")
project(":Spa:gallery").projectDir = file("gallery")
include(":Spa:screenshot")
project(":Spa:screenshot").projectDir = file("screenshot")
include(":Spa:testutils")
project(":Spa:testutils").projectDir = file("testutils")
include(":SettingsLib:Color")
project(":SettingsLib:Color").projectDir = file("../Color")
