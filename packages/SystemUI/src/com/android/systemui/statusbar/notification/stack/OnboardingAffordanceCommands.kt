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

package com.android.systemui.statusbar.notification.stack

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.commandline.CommandRegistry
import com.android.systemui.statusbar.commandline.ParseableCommand
import com.android.systemui.statusbar.notification.stack.domain.interactor.BundleOnboardingInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.SummarizationOnboardingInteractor
import dagger.Binds
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.io.PrintWriter
import javax.inject.Inject

private const val CMD_RESTORE_ONBOARDING = "restore_onboarding"

/**
 * Restores previously-dismissed notification onboarding affordances.
 *
 * `adb shell cmd statusbar restore_onboarding --target (bundles|summaries)`
 */
@SysUISingleton
class OnboardingAffordanceCommands
@Inject
constructor(
    private val commandRegistry: CommandRegistry,
    private val bundleOnboardingInteractor: BundleOnboardingInteractor,
    private val summarizationOnboardingInteractor: SummarizationOnboardingInteractor,
) : CoreStartable {

    override fun start() {
        commandRegistry.registerCommand(CMD_RESTORE_ONBOARDING) { Command() }
    }

    private enum class Target {
        Bundle,
        Summarization,
    }

    private inner class Command :
        ParseableCommand(
            name = CMD_RESTORE_ONBOARDING,
            description = "Restores a dismissed notification onboarding affordance.",
        ) {

        private val target: Target by
            param(
                    shortName = "t",
                    longName = "target",
                    description =
                        """
                            Which onboarding affordance to restore. One of "bundles" or "summaries".
                        """
                            .trimIndent(),
                    valueParser = { arg ->
                        when (arg) {
                            "bundles",
                            "b" -> Result.success(Target.Bundle)
                            "summaries",
                            "s" -> Result.success(Target.Summarization)
                            else -> Result.failure(IllegalArgumentException("unknown target: $arg"))
                        }
                    },
                )
                .required()

        override fun execute(pw: PrintWriter) {
            when (target) {
                Target.Bundle -> bundleOnboardingInteractor.resurrectOnboarding()
                Target.Summarization -> summarizationOnboardingInteractor.resurrectOnboarding()
            }
        }
    }

    @dagger.Module
    interface Module {
        @Binds
        @IntoMap
        @ClassKey(OnboardingAffordanceCommands::class)
        fun bindStartable(impl: OnboardingAffordanceCommands): CoreStartable
    }
}
