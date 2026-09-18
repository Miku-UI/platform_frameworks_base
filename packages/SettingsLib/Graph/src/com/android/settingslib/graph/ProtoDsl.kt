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

package com.android.settingslib.graph

import com.android.settingslib.graph.proto.BundleProto
import com.android.settingslib.graph.proto.BundleProto.BundleValue
import com.android.settingslib.graph.proto.BundleProtoOrBuilder
import com.android.settingslib.graph.proto.IntentProto
import com.android.settingslib.graph.proto.KeyParametersProto
import com.android.settingslib.graph.proto.ParameterizedPreferenceScreenProto
import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.graph.proto.PreferenceGroupProto
import com.android.settingslib.graph.proto.PreferenceGroupProtoOrBuilder
import com.android.settingslib.graph.proto.PreferenceOrGroupProto
import com.android.settingslib.graph.proto.PreferenceOrGroupProtoOrBuilder
import com.android.settingslib.graph.proto.PreferenceProto
import com.android.settingslib.graph.proto.PreferenceProto.ActionTarget
import com.android.settingslib.graph.proto.PreferenceProto.ActionTargetOrBuilder
import com.android.settingslib.graph.proto.PreferenceProtoOrBuilder
import com.android.settingslib.graph.proto.PreferenceScreenProto
import com.android.settingslib.graph.proto.PreferenceScreenProtoOrBuilder
import com.android.settingslib.graph.proto.PreferenceValueDescriptorProto
import com.android.settingslib.graph.proto.PreferenceValueProto
import com.android.settingslib.graph.proto.PreferenceErrorProto
import com.android.settingslib.graph.proto.PossibleValueProto
import com.android.settingslib.graph.proto.RangeValueProto
import com.android.settingslib.graph.proto.SetWarningProto
import com.android.settingslib.graph.proto.TextProto
import com.android.settingslib.metadata.KeyParameters

/** Returns root or null. */
val PreferenceScreenProtoOrBuilder.rootOrNull
    get() = if (hasRoot()) root else null

/** Kotlin DSL-style builder for [PreferenceScreenProto]. */
@JvmSynthetic
inline fun preferenceScreenProto(
    init: PreferenceScreenProto.Builder.() -> Unit
): PreferenceScreenProto = PreferenceScreenProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [PreferenceScreenProto]. */
inline fun parameterizedPreferenceScreenProto(
    init: ParameterizedPreferenceScreenProto.Builder.() -> Unit
): ParameterizedPreferenceScreenProto =
    ParameterizedPreferenceScreenProto.newBuilder().also(init).build()

/** Returns preference or null. */
val PreferenceOrGroupProtoOrBuilder.preferenceOrNull
    get() = if (hasPreference()) preference else null

/** Returns group or null. */
val PreferenceOrGroupProtoOrBuilder.groupOrNull
    get() = if (hasGroup()) group else null

/** Kotlin DSL-style builder for [PreferenceOrGroupProto]. */
@JvmSynthetic
inline fun preferenceOrGroupProto(
    init: PreferenceOrGroupProto.Builder.() -> Unit
): PreferenceOrGroupProto = PreferenceOrGroupProto.newBuilder().also(init).build()

/** Returns preference or null. */
val PreferenceGroupProtoOrBuilder.preferenceOrNull
    get() = if (hasPreference()) preference else null

/** Kotlin DSL-style builder for [PreferenceGroupProto]. */
@JvmSynthetic
inline fun preferenceGroupProto(
    init: PreferenceGroupProto.Builder.() -> Unit
): PreferenceGroupProto = PreferenceGroupProto.newBuilder().also(init).build()

/** Returns title or null. */
val PreferenceProtoOrBuilder.titleOrNull
    get() = if (hasTitle()) title else null

/** Returns summary or null. */
val PreferenceProtoOrBuilder.summaryOrNull
    get() = if (hasSummary()) summary else null

/** Returns actionTarget or null. */
val PreferenceProtoOrBuilder.actionTargetOrNull
    get() = if (hasActionTarget()) actionTarget else null

/** Returns key or null. */
val ActionTargetOrBuilder.keyOrNull
    get() = if (hasKey()) key else null

/** Returns args or null. */
val ActionTargetOrBuilder.argsOrNull
    get() = if (hasArgs()) args else null

/** Returns keyParameters or null. */
val ActionTargetOrBuilder.keyParametersOrNull
    get() = if (hasKeyParameters()) keyParameters else null

/** Kotlin DSL-style builder for [PreferenceProto]. */
@JvmSynthetic
inline fun preferenceProto(init: PreferenceProto.Builder.() -> Unit): PreferenceProto =
    PreferenceProto.newBuilder().also(init).build()

/** Returns intent or null. */
val ActionTarget.intentOrNull
    get() = if (hasIntent()) intent else null

/** Kotlin DSL-style builder for [ActionTarget]. */
@JvmSynthetic
inline fun actionTargetProto(init: ActionTarget.Builder.() -> Unit): ActionTarget =
    ActionTarget.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [PreferenceValueProto]. */
@JvmSynthetic
inline fun preferenceValueProto(
    init: PreferenceValueProto.Builder.() -> Unit
): PreferenceValueProto = PreferenceValueProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [PreferenceErrorProto]. */
@JvmSynthetic
inline fun preferenceErrorProto(
    init: PreferenceErrorProto.Builder.() -> Unit
): PreferenceErrorProto = PreferenceErrorProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [PossibleValueProto]. */
@JvmSynthetic
inline fun possibleValueProto(init: PossibleValueProto.Builder.() -> Unit): PossibleValueProto =
    PossibleValueProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [PreferenceValueDescriptorProto]. */
@JvmSynthetic
inline fun preferenceValueDescriptorProto(
    init: PreferenceValueDescriptorProto.Builder.() -> Unit
): PreferenceValueDescriptorProto = PreferenceValueDescriptorProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [RangeValueProto]. */
@JvmSynthetic
inline fun rangeValueProto(init: RangeValueProto.Builder.() -> Unit): RangeValueProto =
    RangeValueProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [TextProto]. */
@JvmSynthetic
inline fun textProto(init: TextProto.Builder.() -> Unit): TextProto =
    TextProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [IntentProto]. */
@JvmSynthetic
inline fun intentProto(init: IntentProto.Builder.() -> Unit): IntentProto =
    IntentProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [BundleProto]. */
@JvmSynthetic
inline fun bundleProto(init: BundleProto.Builder.() -> Unit): BundleProto =
    BundleProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [BundleValue]. */
@JvmSynthetic
inline fun bundleValueProto(init: BundleValue.Builder.() -> Unit): BundleValue =
    BundleValue.newBuilder().also(init).build()

fun PreferenceGraphProto.Builder.mergeForLazyMode(
    screen: PreferenceScreenProto,
    screenKey: String,
    args: BundleProtoOrBuilder?,
) {
    val oldScreen = getScreensOrDefault(screenKey, null)
    if (oldScreen == null) {
        putScreens(screenKey, screen)
        return
    }
    val screenBuilder = oldScreen.toBuilder()
    if (args == null) {
        screenBuilder.mergeFrom(screen)
    } else {
        screenBuilder.addAllParameterizedScreens(screen.parameterizedScreensList)
    }
    putScreens(screenKey, screenBuilder.build())
}

/**
 * Merges a [PreferenceScreenProto] into this graph builder, with behavior tailored for lazy
 * loading scenarios.
 *
 * If no screen exists for the given [screenKey], the new [screen] is added to the graph.
 *
 * If a screen already exists, the merge strategy depends on [keyParameters]:
 * - If [keyParameters] is `null`, a full merge of the [screen] proto is performed, updating all
 *   fields of the existing screen.
 * - If [keyParameters] is not `null`, only the `parameterized_screens` list from the new [screen]
 *   is added to the existing screen. This is used to add data for a specific set of parameters
 *   without re-fetching the base screen information.
 *
 * @param screen The new [PreferenceScreenProto] data to merge.
 * @param screenKey The key identifying the screen within the preference graph.
 * @param keyParameters A map of parameters that dictates the merge strategy. If null, a full
 *     merge is performed.
 */
fun PreferenceGraphProto.Builder.mergeForLazyMode(
    screen: PreferenceScreenProto,
    screenKey: String,
    keyParameters: KeyParameters?,
) {
    val oldScreen = getScreensOrDefault(screenKey, null)
    if (oldScreen == null) {
        putScreens(screenKey, screen)
        return
    }
    val screenBuilder = oldScreen.toBuilder()
    if (keyParameters == null) {
        screenBuilder.mergeFrom(screen)
    } else {
        screenBuilder.addAllParameterizedScreens(screen.parameterizedScreensList)
    }
    putScreens(screenKey, screenBuilder.build())
}

/** Kotlin DSL-style builder for [com.android.settingslib.graph.proto.KeyParametersProto]. */
@JvmSynthetic
inline fun keyParametersProto(init: KeyParametersProto.Builder.() -> Unit): KeyParametersProto =
    KeyParametersProto.newBuilder().also(init).build()

/** Kotlin DSL-style builder for [com.android.settingslib.graph.proto.SetWarningProto]. */
@JvmSynthetic
inline fun setWarningProto(init: SetWarningProto.Builder.() -> Unit): SetWarningProto =
    SetWarningProto.newBuilder().also(init).build()
