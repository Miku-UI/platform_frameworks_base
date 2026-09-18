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

package com.android.settingslib.graph

import com.android.settingslib.graph.proto.BundleProto
import com.android.settingslib.graph.proto.IntentProto
import com.android.settingslib.graph.proto.KeyParametersProto
import com.android.settingslib.graph.proto.KeyParametersSchemaProto
import com.android.settingslib.graph.proto.ParameterDefinitionProto
import com.android.settingslib.graph.proto.ParameterizedPreferenceScreenProto
import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.graph.proto.PreferenceGroupProto
import com.android.settingslib.graph.proto.PreferenceOrGroupProto
import com.android.settingslib.graph.proto.PreferenceProto
import com.android.settingslib.graph.proto.PreferenceScreenProto
import com.android.settingslib.graph.proto.PreferenceValueDescriptorProto
import com.android.settingslib.graph.proto.TextProto

/**
 * Utility to shrink and expand [PreferenceGraphProto] using In-Place Skeleton Parameterization.
 *
 * It deduplicates sibling screens by identifying those with identical internal structures.
 *
 * --- SHRINKING ---
 * 1. The first sibling in a group is the ANCHOR. It stores a structural template where concrete
 *    routing values are replaced by ${key} placeholders.
 * 2. Subsequent identical siblings are SKELETONS. Their 'screen' field is cleared to save space,
 *    as their structure can be derived from the Anchor.
 *
 * --- EXPANDING ---
 * 1. The pass maintains the 'activeTemplate' from the most recent Anchor.
 * 2. Every sibling (Anchor or Skeleton) is materialized by re-injecting its unique parameters
 *    into the active template, perfectly restoring the original LOSSLESS state.
 */
object PreferenceGraphCompressor {

    /** Performs hierarchy-wide deduplication. Returns the original if no changes were made. */
    fun shrink(proto: PreferenceGraphProto): PreferenceGraphProto {
        val shrunkenScreens = proto.screensMap.mapValues { (_, screen) -> shrinkScreen(screen) }
        return if (shrunkenScreens == proto.screensMap) proto else proto.toBuilder().putAllScreens(shrunkenScreens).build()
    }

    /** Re-materializes all skeletons. Returns the original if no expansion was needed. */
    fun expand(proto: PreferenceGraphProto): PreferenceGraphProto {
        val expandedScreens = proto.screensMap.mapValues { (_, screen) -> expandScreen(screen) }
        return if (expandedScreens == proto.screensMap) proto else proto.toBuilder().putAllScreens(expandedScreens).build()
    }

    private fun shrinkScreen(screen: PreferenceScreenProto): PreferenceScreenProto {
        val builder = screen.toBuilder().apply { if (hasRoot()) setRoot(shrinkGroup(root)) }
        val siblings = screen.parameterizedScreensList
        if (siblings.isEmpty()) return builder.build()

        val shrunkenSiblings = mutableListOf<ParameterizedPreferenceScreenProto>()
        var index = 0
        while (index < siblings.size) {
            val anchor = siblings[index]
            val shrunkenAnchorSubTree = if (anchor.hasScreen()) shrinkScreen(anchor.screen) else null
            val anchorTemplate = if (shrunkenAnchorSubTree != null) {
                mapStringsInScreen(
                    shrunkenAnchorSubTree,
                    anchor.keyParameters.valuesMap,
                    ::replaceWithPlaceholder)
            } else null

            // Add the Anchor: It carries the structural template for its group.
            shrunkenSiblings.add(anchor.toBuilder().apply { if (anchorTemplate != null) setScreen(anchorTemplate) }.build())

            var lookAheadIndex = index + 1
            while (lookAheadIndex < siblings.size) {
                val candidate = siblings[lookAheadIndex]
                val shrunkenCandidateSubTree = if (candidate.hasScreen()) shrinkScreen(candidate.screen) else null
                val candidateTemplate = if (shrunkenCandidateSubTree != null) {
                    mapStringsInScreen(
                            shrunkenCandidateSubTree,
                        candidate.keyParameters.valuesMap,
                        ::replaceWithPlaceholder)
                } else null

                if (candidateTemplate == anchorTemplate) {
                    // SKELETON: Identical structure found. Clear the screen field to deduplicate.
                    shrunkenSiblings.add(candidate.toBuilder().clearScreen().build())
                    lookAheadIndex++
                } else break
            }
            index = lookAheadIndex
        }
        return builder.clearParameterizedScreens().addAllParameterizedScreens(shrunkenSiblings).build()
    }

    private fun expandScreen(screen: PreferenceScreenProto): PreferenceScreenProto {
        val builder = screen.toBuilder().apply { if (hasRoot()) setRoot(expandGroup(root)) }
        val siblings = screen.parameterizedScreensList
        if (siblings.isEmpty()) return builder.build()

        var activeTemplate: PreferenceScreenProto? = null
        val expandedSiblings = siblings.map { sibling ->
            // If the sibling has a screen, it's a new Anchor. Update the active template.
            if (sibling.hasScreen()) activeTemplate = sibling.screen

            val template = activeTemplate ?: error("Dangling Skeleton: No preceding Anchor found.")

            // Materialize the template using this sibling's unique parameters.
            val materialized =
                mapStringsInScreen(template,
                    sibling.keyParameters.valuesMap,
                    ::restoreFromPlaceholder)

            // Ensure the materialized sub-tree is also expanded.
            sibling.toBuilder().setScreen(expandScreen(materialized)).build()
        }
        return builder.clearParameterizedScreens().addAllParameterizedScreens(expandedSiblings).build()
    }

    private fun shrinkGroup(group: PreferenceGroupProto): PreferenceGroupProto = group.toBuilder()
        .clearPreferences().addAllPreferences(group.preferencesList.map { child ->
            if (child.hasGroup()) PreferenceOrGroupProto.newBuilder().setGroup(shrinkGroup(child.group)).build() else child
        }).build()

    private fun expandGroup(group: PreferenceGroupProto): PreferenceGroupProto = group.toBuilder()
        .clearPreferences().addAllPreferences(group.preferencesList.map { child ->
            if (child.hasGroup()) PreferenceOrGroupProto.newBuilder().setGroup(expandGroup(child.group)).build() else child
        }).build()

    // --- String Mapping Engine: The recursive "Search & Replace" heart of the compressor ---

    private fun mapStringsInScreen(
        screen: PreferenceScreenProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): PreferenceScreenProto = screen.toBuilder().apply {
        if (hasRoot()) setRoot(mapStringsInGroup(root, parameters, stringMapper))
        if (hasIntent()) setIntent(mapStringsInIntent(intent, parameters, stringMapper))
        if (hasParametersSchema()) setParametersSchema(mapStringsInSchema(parametersSchema, parameters, stringMapper))
    }.build()

    private fun mapStringsInGroup(
        group: PreferenceGroupProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): PreferenceGroupProto = group.toBuilder().apply {
        if (hasPreference()) setPreference(mapStringsInPreference(preference, parameters, stringMapper))
        val mappedChildren = preferencesList.map { child ->
            val childBuilder = child.toBuilder()
            if (child.hasGroup()) childBuilder.setGroup(mapStringsInGroup(child.group, parameters, stringMapper))
            else if (child.hasPreference()) childBuilder.setPreference(mapStringsInPreference(child.preference, parameters, stringMapper))
            childBuilder.build()
        }
        clearPreferences().addAllPreferences(mappedChildren)
    }.build()

    private fun mapStringsInPreference(
        preference: PreferenceProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): PreferenceProto = preference.toBuilder().apply {
        if (hasKey()) setKey(stringMapper(key, parameters))
        if (hasTitle()) setTitle(mapStringsInText(title, parameters, stringMapper))
        if (hasSummary()) setSummary(mapStringsInText(summary, parameters, stringMapper))
        if (hasExtras()) setExtras(mapStringsInBundle(extras, parameters, stringMapper))
        if (hasLaunchIntent()) setLaunchIntent(mapStringsInIntent(launchIntent, parameters, stringMapper))
        if (hasActionTarget()) setActionTarget(actionTarget.toBuilder().apply {
            if (hasKey()) setKey(stringMapper(key, parameters))
            if (hasKeyParameters()) setKeyParameters(mapKeyParameterStrings(keyParameters, parameters, stringMapper))
            if (hasIntent()) setIntent(mapStringsInIntent(intent, parameters, stringMapper))
        }.build())
        if (hasParametersSchema()) setParametersSchema(mapStringsInSchema(parametersSchema, parameters, stringMapper))
        if (hasKeyParameters()) setKeyParameters(mapKeyParameterStrings(keyParameters, parameters, stringMapper))
        if (hasValueDescriptor()) setValueDescriptor(mapStringsInDescriptor(valueDescriptor, parameters, stringMapper))

        if (hasReadPermissions()) setReadPermissions(mapStringsInPermissions(readPermissions, parameters, stringMapper))
        if (hasWritePermissions()) setWritePermissions(mapStringsInPermissions(writePermissions, parameters, stringMapper))

        val mappedTags = tagsList.map { stringMapper(it, parameters) }
        clearTags().addAllTags(mappedTags)

        val mappedGetPre = getPreconditionsList.map { stringMapper(it, parameters) }
        clearGetPreconditions().addAllGetPreconditions(mappedGetPre)

        val mappedSetPre = setPreconditionsList.map { stringMapper(it, parameters) }
        clearSetPreconditions().addAllSetPreconditions(mappedSetPre)
    }.build()

    private fun mapStringsInIntent(
        intent: IntentProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): IntentProto = intent.toBuilder().apply {
        if (hasAction()) setAction(stringMapper(action, parameters))
        if (hasData()) setData(stringMapper(data, parameters))
        if (hasPkg()) setPkg(stringMapper(pkg, parameters))
        if (hasComponent()) setComponent(stringMapper(component, parameters))
        if (hasExtras()) setExtras(mapStringsInBundle(extras, parameters, stringMapper))
        if (hasMimeType()) setMimeType(stringMapper(mimeType, parameters))
    }.build()

    private fun mapStringsInPermissions(
        permissions: com.android.settingslib.graph.proto.PermissionsProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): com.android.settingslib.graph.proto.PermissionsProto = permissions.toBuilder().apply {
        val mappedAllOf = allOfList.map { mapStringsInPermission(it, parameters, stringMapper) }
        clearAllOf().addAllAllOf(mappedAllOf)
        val mappedAnyOf = anyOfList.map { mapStringsInPermission(it, parameters, stringMapper) }
        clearAnyOf().addAllAnyOf(mappedAnyOf)
    }.build()

    private fun mapStringsInPermission(
        permission: com.android.settingslib.graph.proto.PermissionProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): com.android.settingslib.graph.proto.PermissionProto = permission.toBuilder().apply {
        if (hasPermission()) setPermission(stringMapper(this.permission, parameters))
        if (hasPermissions()) setPermissions(mapStringsInPermissions(permissions, parameters, stringMapper))
    }.build()

    private fun mapStringsInBundle(
        bundle: BundleProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): BundleProto = bundle.toBuilder().apply {
        for ((key, value) in bundle.valuesMap) {
            val valueBuilder = value.toBuilder()
            if (value.hasStringValue()) {
                valueBuilder.setStringValue(stringMapper(value.stringValue, parameters))
            } else if (value.hasBundleValue()) {
                valueBuilder.setBundleValue(mapStringsInBundle(value.bundleValue, parameters, stringMapper))
            }
            putValues(stringMapper(key, parameters), valueBuilder.build())
        }
    }.build()

    private fun mapStringsInText(
        text: TextProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): TextProto = if (text.hasString()) {
        text.toBuilder().setString(stringMapper(text.string, parameters)).build()
    } else text

    private fun mapStringsInSchema(
        schema: KeyParametersSchemaProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): KeyParametersSchemaProto {
        val builder = schema.toBuilder()
        for ((name, definition) in schema.parametersMap) {
            builder.putParameters(name, mapStringsInDefinition(definition, parameters, stringMapper))
        }
        return builder.build()
    }

    private fun mapStringsInDefinition(
        definition: ParameterDefinitionProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): ParameterDefinitionProto = definition.toBuilder().apply {
        if (hasPurpose()) setPurpose(stringMapper(purpose, parameters))
        if (hasValueDescriptor()) setValueDescriptor(mapStringsInDescriptor(valueDescriptor, parameters, stringMapper))
    }.build()

    private fun mapStringsInDescriptor(
        descriptor: PreferenceValueDescriptorProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): PreferenceValueDescriptorProto = descriptor.toBuilder().apply {
        if (hasDescription()) setDescription(stringMapper(description, parameters))
        if (hasValueDescriptorKey()) setValueDescriptorKey(stringMapper(valueDescriptorKey, parameters))
        val mappedPossibleValues = possibleValuesList.map {
            it.toBuilder().apply {
                if (hasDescription()) setDescription(stringMapper(description, parameters))
                if (hasValue() && value.hasStringValue()) {
                    setValue(value.toBuilder().setStringValue(stringMapper(value.stringValue, parameters)).build())
                }
            }.build()
        }
        clearPossibleValues().addAllPossibleValues(mappedPossibleValues)
        if (hasParametersSchema()) setParametersSchema(mapStringsInSchema(parametersSchema, parameters, stringMapper))
        if (hasParameters()) setParameters(mapKeyParameterStrings(this.parameters, parameters, stringMapper))
    }.build()

    private fun mapKeyParameterStrings(
        keyParameters: KeyParametersProto,
        parameters: Map<String, String>,
        stringMapper: (String, Map<String, String>) -> String
    ): KeyParametersProto = keyParameters.toBuilder().apply {
        for ((key, value) in keyParameters.valuesMap) putValues(key, stringMapper(value, parameters))
    }.build()

    private fun replaceWithPlaceholder(text: String, parameters: Map<String, String>): String {
        var result = text
        // PRECEDENCE: Longer values are replaced first to prevent partial overlaps.
        parameters.entries.sortedByDescending { it.value.length }.forEach { (key, value) ->
            if (value.isNotEmpty()) result = result.replace(value, "\${$key}")
        }
        return result
    }

    private fun restoreFromPlaceholder(text: String, parameters: Map<String, String>): String {
        var result = text
        parameters.forEach { (key, value) -> result = result.replace("\${$key}", value) }
        return result
    }
}
