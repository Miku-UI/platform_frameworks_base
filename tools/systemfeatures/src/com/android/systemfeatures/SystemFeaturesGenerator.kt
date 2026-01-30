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

package com.android.systemfeatures

import com.google.common.base.CaseFormat
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import java.io.File
import javax.lang.model.element.Modifier
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/*
 * Simple Java code generator that takes as input a list of defined features and generates an
 * accessory class based on the provided versions.
 *
 * <p>Example:
 *
 * <pre>
 *   <cmd> com.foo.RoSystemFeatures --readonly=true \
 *           --feature=WATCH:0 --feature=AUTOMOTIVE: --feature=VULKAN:9348 --feature=PC:UNAVAILABLE
 *           --feature-apis=WATCH,PC,LEANBACK
 * </pre>
 *
 * This generates a class that has the following signature:
 *
 * <pre>
 * package com.foo;
 * public final class RoSystemFeatures {
 *     @AssumeTrueForR8
 *     public static boolean hasFeatureWatch(Context context);
 *     @AssumeFalseForR8
 *     public static boolean hasFeaturePc(Context context);
 *     @AssumeTrueForR8
 *     public static boolean hasFeatureVulkan(Context context);
 *     public static boolean hasFeatureAutomotive(Context context);
 *     public static boolean hasFeatureLeanback(Context context);
 *     public static Boolean maybeHasFeature(String feature, int version);
 *     public static ArrayMap<String, FeatureInfo> getReadOnlySystemEnabledFeatures();
 * }
 * </pre>
 *
 * <p> If `--metadata-only=true` is set, the resulting class would simply be:
 * <pre>
 * package com.foo;
 * public final class RoSystemFeatures {
 *     public static String getMethodNameForFeatureName(String featureName);
  * }
 * </pre>
 */
object SystemFeaturesGenerator {
    private const val FEATURE_ARG = "--feature="
    private const val FEATURE_XML_FILES_ARG = "--feature-xml-files="
    private const val UNAVAILABLE_FEATURE_XML_FILES_ARG = "--unavailable-feature-xml-files="
    private const val FEATURE_APIS_ARG = "--feature-apis="
    private const val READONLY_ARG = "--readonly="
    private const val METADATA_ONLY_ARG = "--metadata-only="
    private val PACKAGEMANAGER_CLASS = ClassName.get("android.content.pm", "PackageManager")
    private val CONTEXT_CLASS = ClassName.get("android.content", "Context")
    private val FEATUREINFO_CLASS = ClassName.get("android.content.pm", "FeatureInfo")
    private val ARRAYMAP_CLASS = ClassName.get("android.util", "ArrayMap")
    private val ASSUME_TRUE_CLASS =
        ClassName.get("com.android.aconfig.annotations", "AssumeTrueForR8")
    private val ASSUME_FALSE_CLASS =
        ClassName.get("com.android.aconfig.annotations", "AssumeFalseForR8")

    private fun usage() {
        println("Usage: SystemFeaturesGenerator <outputClassName> [options]")
        println(" Options:")
        println("  --readonly=true|false    Whether to encode features as build-time constants")
        println("  --feature=\$NAME:\$VER     A feature+version pair, where \$VER can be:")
        println("                             * blank/empty == undefined (variable API)")
        println("                             * valid int   == enabled   (constant API)")
        println("                             * UNAVAILABLE == disabled  (constant API)")
        println("                           This will always generate associated query APIs,")
        println("                           adding to or replacing those from `--feature-apis=`.")
        println("  --feature-apis=\$NAME_1,\$NAME_2")
        println("                           A comma-separated set of features for which to always")
        println("                           generate named query APIs. If a feature in this set is")
        println("                           not explicitly defined via `--feature=`, then a simple")
        println("                           runtime passthrough API will be generated, regardless")
        println("                           of the `--readonly` flag. This allows decoupling the")
        println("                           API surface from variations in device feature sets.")
        println("  --feature-xml-files=\$XML_FILE_1,\$XML_FILE_2")
        println("                           A comma-separated list of XML permission feature files")
        println("                           to parse and add to the generated query APIs. The file")
        println("                           format matches that used by SystemConfig parsing. This")
        println("                           parses <feature /> and <unavailable-feature /> defs.")
        println("  --unavailable-feature-xml-files=\$XML_FILE_1,\$XML_FILE_2")
        println("                           A comma-separated list of XML permission feature files")
        println("                           to parse and add to the generated query APIs. The file")
        println("                           format matches that used by SystemConfig parsing. This")
        println("                           parses *only* <unavailable-feature /> defs.")
        println("  --metadata-only=true|false Whether to simply output metadata about the")
        println("                             generated API surface.")
    }

    /** Main entrypoint for build-time system feature codegen. */
    @JvmStatic
    fun main(args: Array<String>) {
        generate(args, System.out)
    }

    /**
     * Simple API entrypoint for build-time system feature codegen.
     *
     * Note: Typically this would be implemented in terms of a proper Builder-type input argument,
     * but it's primarily used for testing as opposed to direct production usage.
     */
    @JvmStatic
    fun generate(args: Array<String>, output: Appendable) {
        if (args.size < 1) {
            usage()
            return
        }

        var readonly = false
        var metadataOnly = false
        var outputClassName: ClassName? = null
        val featureArgs = mutableListOf<FeatureInfo>()
        // We could just as easily hardcode this list, as the static API surface should change
        // somewhat infrequently, but this decouples the codegen from the framework completely.
        val featureApiArgs = mutableSetOf<String>()
        for (arg in args) {
            when {
                arg.startsWith(READONLY_ARG) ->
                    readonly = arg.substring(READONLY_ARG.length).toBoolean()
                arg.startsWith(METADATA_ONLY_ARG) ->
                    metadataOnly = arg.substring(METADATA_ONLY_ARG.length).toBoolean()
                arg.startsWith(FEATURE_ARG) -> {
                    featureArgs.add(parseFeatureArg(arg))
                }
                arg.startsWith(FEATURE_APIS_ARG) -> {
                    featureApiArgs.addAll(
                        arg.substring(FEATURE_APIS_ARG.length).split(",").map {
                            parseFeatureName(it)
                        }
                    )
                }
                arg.startsWith(FEATURE_XML_FILES_ARG) -> {
                    featureArgs.addAll(
                        parseFeatureXmlFiles(arg.substring(FEATURE_XML_FILES_ARG.length).split(","))
                    )
                }
                arg.startsWith(UNAVAILABLE_FEATURE_XML_FILES_ARG) -> {
                    featureArgs.addAll(
                        parseFeatureXmlFiles(
                            arg.substring(UNAVAILABLE_FEATURE_XML_FILES_ARG.length).split(","),
                            parseOnlyUnavailableFeatures = true,
                        )
                    )
                }
                else -> outputClassName = ClassName.bestGuess(arg)
            }
        }

        // First load in all of the feature APIs we want to generate. Explicit feature definitions
        // will then override this set with the appropriate readonly and version value. Note that we
        // use a sorted map to ensure stable codegen outputs given identical inputs.
        val features = sortedMapOf<String, FeatureInfo>()
        featureApiArgs.associateByTo(
            features,
            { it },
            { FeatureInfo(it, version = null, readonly = false) },
        )

        // Multiple defs for the same feature may be present when aggregating permission files.
        // To preserve SystemConfig semantics, use the following ordering for insertion priority:
        //     * readonly (build-time overrides runtime)
        //     * unavailable (version == null, overrides available)
        //     * version (higher overrides lower)
        featureArgs
            .sortedWith(
                compareBy<FeatureInfo> { it.readonly }
                    .thenBy { it.version == null }
                    .thenBy { it.version }
            )
            .associateByTo(
                features,
                { it.name },
                { FeatureInfo(it.name, it.version, it.readonly && readonly) },
            )

        outputClassName
            ?: run {
                System.err.println("Output class name must be provided.")
                usage()
                return
            }

        val classBuilder =
            TypeSpec.classBuilder(outputClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("@hide")

        if (metadataOnly) {
            addMetadataMethodToClass(classBuilder, features.values)
        } else {
            addFeatureMethodsToClass(classBuilder, features.values)
            addMaybeFeatureMethodToClass(classBuilder, features.values)
            addGetFeaturesMethodToClass(classBuilder, features.values)
        }

        // TODO(b/203143243): Add validation of build vs runtime values to ensure consistency.
        JavaFile.builder(outputClassName.packageName(), classBuilder.build())
            .indent("    ")
            .skipJavaLangImports(true)
            .addFileComment("This file is auto-generated. DO NOT MODIFY.\n")
            .addFileComment("Args: ${args.joinToString(" \\\n           ")}")
            .build()
            .writeTo(output)
    }

    /*
     * Parses a feature argument of the form "--feature=$NAME:$VER", where "$VER" is optional.
     *   * "--feature=WATCH:0" -> Feature enabled w/ version 0 (default version when enabled)
     *   * "--feature=WATCH:7" -> Feature enabled w/ version 7
     *   * "--feature=WATCH:"  -> Feature status undefined, runtime API generated
     *   * "--feature=WATCH:UNAVAILABLE"  -> Feature disabled
     */
    private fun parseFeatureArg(arg: String): FeatureInfo {
        val featureArgs = arg.substring(FEATURE_ARG.length).split(":")
        val name = parseFeatureName(featureArgs[0])
        return when (featureArgs.getOrNull(1)) {
            null, "" -> FeatureInfo(name, null, readonly = false)
            "UNAVAILABLE" -> FeatureInfo(name, null, readonly = true)
            else -> {
                val featureVersion =
                    featureArgs[1].toIntOrNull()
                        ?: throw IllegalArgumentException(
                            "Invalid feature version input for $name: ${featureArgs[1]}"
                        )
                FeatureInfo(name, featureVersion, readonly = true)
            }
        }
    }

    private fun parseFeatureName(name: String): String =
        when {
            name.startsWith("android") -> {
                parseFeatureNameFromValue(name)
                    ?: throw IllegalArgumentException(
                        "Unrecognized Android system feature name: $name"
                    )
            }
            name.startsWith("FEATURE_") -> name
            else -> "FEATURE_$name"
        }

    private fun parseFeatureNameFromValue(name: String): String? =
        SystemFeaturesLookup.getDeclaredFeatureVarNameFromValue(name)


    /**
     * Parses a list of feature permission XML file paths into a list of FeatureInfo definitions.
     */
    private fun parseFeatureXmlFiles(
        filePaths: Collection<String>,
        parseOnlyUnavailableFeatures: Boolean = false,
    ): Collection<FeatureInfo> =
        filePaths.flatMap {
            try {
                parseFeatureXmlFile(File(it), parseOnlyUnavailableFeatures)
            } catch (e: Exception) {
                throw IllegalArgumentException("Error parsing feature XML file: $it", e)
            }
        }

    /**
     * Parses a feature permission XML file into a (possibly empty) list of FeatureInfo definitions.
     */
    private fun parseFeatureXmlFile(
        file: File,
        parseOnlyUnavailableFeatures: Boolean = false,
    ): Collection<FeatureInfo> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        doc.documentElement.normalize()

        val xPath = XPathFactory.newInstance().newXPath()
        val rootElement =
            xPath.evaluate("/permissions", doc, XPathConstants.NODE) as? Element
                ?: xPath.evaluate("/config", doc, XPathConstants.NODE) as? Element
        if (rootElement == null) {
            System.err.println("Warning: No <permissions>/<config> elements found in ${file.path}")
            return emptyList()
        }

        return rootElement.childNodes.let { nodeList ->
            (0 until nodeList.length)
                .asSequence()
                .map { nodeList.item(it) }
                .filter { it.nodeType == Node.ELEMENT_NODE }
                .map { it as Element }
                .mapNotNull { element ->
                    when {
                        element.tagName == "unavailable-feature" ->
                            parseUnavailableFeatureElement(element)
                        !parseOnlyUnavailableFeatures && element.tagName == "feature" ->
                            parseFeatureElement(element)
                        else -> null
                    }
                }
                .toList()
        }
    }

    private fun parseFeatureElement(element: Element): FeatureInfo? {
        val name = parseFeatureNameFromValue(element.getAttribute("name")) ?: return null
        return if (element.getAttribute("notLowRam") == "true") {
            // If a feature is marked as being disabled on low-ram devices (notLowRam==true), we
            // we cannot finalize the exported feature version or its availability, as we don't
            // (yet) know whether the target product is low-ram.
            FeatureInfo(name, version = null, readonly = false)
        } else {
            val version = element.getAttribute("version")
            FeatureInfo(name, version.toIntOrNull() ?: 0, readonly = true)
        }
    }

    private fun parseUnavailableFeatureElement(element: Element): FeatureInfo? {
        val name = parseFeatureNameFromValue(element.getAttribute("name")) ?: return null
        return FeatureInfo(name, version = null, readonly = true)
    }

    /*
     * Adds per-feature query methods to the class with the form:
     * {@code public static boolean hasFeatureX(Context context)},
     * returning the fallback value from PackageManager if not readonly.
     */
    private fun addFeatureMethodsToClass(
        builder: TypeSpec.Builder,
        features: Collection<FeatureInfo>,
    ) {
        for (feature in features) {
            val methodBuilder =
                MethodSpec.methodBuilder(feature.methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addJavadoc("Check for ${feature.name}.\n\n@hide")
                    .returns(Boolean::class.java)
                    .addParameter(CONTEXT_CLASS, "context")

            if (feature.readonly) {
                val featureEnabled = compareValues(feature.version, 0) >= 0
                methodBuilder.addAnnotation(
                    if (featureEnabled) ASSUME_TRUE_CLASS else ASSUME_FALSE_CLASS
                )
                methodBuilder.addStatement("return $featureEnabled")
            } else {
                methodBuilder.addStatement(
                    "return hasFeatureFallback(context, \$T.\$N)",
                    PACKAGEMANAGER_CLASS,
                    feature.name
                )
            }
            builder.addMethod(methodBuilder.build())
        }

        // This is a trivial method, even if unused based on readonly-codegen, it does little harm
        // to always include it.
        builder.addMethod(
            MethodSpec.methodBuilder("hasFeatureFallback")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(Boolean::class.java)
                .addParameter(CONTEXT_CLASS, "context")
                .addParameter(String::class.java, "featureName")
                .addStatement("return context.getPackageManager().hasSystemFeature(featureName)")
                .build()
        )
    }

    /*
     * Adds a generic query method to the class with the form: {@code public static boolean
     * maybeHasFeature(String featureName, int version)}, returning null if the feature version is
     * undefined or not (compile-time) readonly.
     *
     * This method is useful for internal usage within the framework, e.g., from the implementation
     * of {@link android.content.pm.PackageManager#hasSystemFeature(Context)}, when we may only
     * want a valid result if it's defined as readonly, and we want a custom fallback otherwise
     * (e.g., to the existing runtime binder query).
     */
    private fun addMaybeFeatureMethodToClass(
        builder: TypeSpec.Builder,
        features: Collection<FeatureInfo>,
    ) {
        val methodBuilder =
            MethodSpec.methodBuilder("maybeHasFeature")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(ClassName.get("android.annotation", "Nullable"))
                .addJavadoc("@hide")
                .returns(Boolean::class.javaObjectType) // Use object type for nullability
                .addParameter(String::class.java, "featureName")
                .addParameter(Int::class.java, "version")

        var hasSwitchBlock = false
        for (feature in features) {
            // We only return non-null results for queries against readonly-defined features.
            if (!feature.readonly) {
                continue
            }
            if (!hasSwitchBlock) {
                // As an optimization, only create the switch block if needed. Even an empty
                // switch-on-string block can induce a hash, which we can avoid if readonly
                // support is completely disabled.
                hasSwitchBlock = true
                methodBuilder.beginControlFlow("switch (featureName)")
            }
            methodBuilder.addCode("case \$T.\$N: ", PACKAGEMANAGER_CLASS, feature.name)
            if (feature.version != null) {
                methodBuilder.addStatement("return \$L >= version", feature.version)
            } else {
                methodBuilder.addStatement("return false")
            }
        }
        if (hasSwitchBlock) {
            methodBuilder.addCode("default: ")
            methodBuilder.addStatement("break")
            methodBuilder.endControlFlow()
        }
        methodBuilder.addStatement("return null")
        builder.addMethod(methodBuilder.build())
    }

    /*
     * Adds a method to get all compile-time enabled features.
     *
     * This method is useful for internal usage within the framework to augment
     * any system features that are parsed from the various partitions.
     */
    private fun addGetFeaturesMethodToClass(
        builder: TypeSpec.Builder,
        features: Collection<FeatureInfo>,
    ) {
        val methodBuilder =
                MethodSpec.methodBuilder("getReadOnlySystemEnabledFeatures")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(ClassName.get("android.annotation", "NonNull"))
                .addJavadoc("Gets features marked as available at compile-time, keyed by name." +
                        "\n\n@hide")
                .returns(ParameterizedTypeName.get(
                        ARRAYMAP_CLASS,
                        ClassName.get(String::class.java),
                        FEATUREINFO_CLASS))

        val availableFeatures = features.filter { it.readonly && it.version != null }
        methodBuilder.addStatement("\$T<String, FeatureInfo> features = new \$T<>(\$L)",
                ARRAYMAP_CLASS, ARRAYMAP_CLASS, availableFeatures.size)
        if (!availableFeatures.isEmpty()) {
            methodBuilder.addStatement("FeatureInfo fi = new FeatureInfo()")
        }
        for (feature in availableFeatures) {
            methodBuilder.addStatement("fi.name = \$T.\$N", PACKAGEMANAGER_CLASS, feature.name)
            methodBuilder.addStatement("fi.version = \$L", feature.version)
            methodBuilder.addStatement("features.put(fi.name, new FeatureInfo(fi))")
        }
        methodBuilder.addStatement("return features")
        builder.addMethod(methodBuilder.build())
    }

    /*
     * Adds a metadata helper method that maps FEATURE_FOO names to their generated hasFeatureFoo()
     * API counterpart, if defined.
     */
    private fun addMetadataMethodToClass(
        builder: TypeSpec.Builder,
        features: Collection<FeatureInfo>,
    ) {
        val methodBuilder =
            MethodSpec.methodBuilder("getMethodNameForFeatureName")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("@return \"hasFeatureFoo\" if FEATURE_FOO is in the API, else null")
                .returns(String::class.java)
                .addParameter(String::class.java, "featureVarName")

        methodBuilder.beginControlFlow("switch (featureVarName)")
        for (feature in features) {
            methodBuilder.addStatement("case \$S: return \$S", feature.name, feature.methodName)
        }
        methodBuilder.addStatement("default: return null").endControlFlow()

        builder.addMethod(methodBuilder.build())
    }

    private data class FeatureInfo(val name: String, val version: Int?, val readonly: Boolean) {
        // Turn "FEATURE_FOO" into "hasFeatureFoo".
        val methodName get() = "has" + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name)
    }
}
