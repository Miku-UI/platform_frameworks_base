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
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.ScopedArtifacts
import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

private val log = Logging.getLogger("Ravenwood")

/**
 * Boolean indicating whether to run host tests on Ravenwood or not.
 * To enable Ravenwood:
 * - Add `enableRavenwood=true` to gradle.properties. (e.g. $HOME/.gradle/gradle.properties)
 * - Or, ./gradlew :app:testDebugUnitTest --info -PenableRavenwood=false
 */
val enableRavenwood = project.findProperty("enableRavenwood")?.toString()?.toBoolean() ?: false
log.lifecycle("Ravenwood: enableRavenwood=$enableRavenwood")

/**
 * If true, use Robolectric instead of Ravenwood. [enableRobolectric] and [enableRavenwood] must
 * be mutually exclusive.
 */
val enableRobolectric = !enableRavenwood

// Add this to be compatible with go/sonata.
private fun Project.androidBuildTop() = project.rootDir.resolve("../../../../..")

// Relative to $ANDROID_BUILD_TOP
private val _androidHostOut = "out/host/linux-x86"
private val _ravenwoodRuntimePath = "$_androidHostOut/testcases/ravenwood-runtime"
private val _ravenwoodRuntimeClasspathFile =
    "$_ravenwoodRuntimePath/ravenwood-data/ravenwood-classpath.txt"
private val _ravenwoodUtilsPath = "$_androidHostOut/testcases/ravenwood-utils"
private val _ravenizerJar = "$_androidHostOut/framework/ravenizer.jar"

private fun Project.ravenwoodRuntimePath() = this.androidBuildTop().resolve(_ravenwoodRuntimePath)
private fun Project.ravenwoodRuntimeClasspathFile() =
    this.androidBuildTop().resolve(_ravenwoodRuntimeClasspathFile)
private fun Project.ravenwoodUtilsPath() = this.androidBuildTop().resolve(_ravenwoodUtilsPath)
private fun Project.ravenizerJar() = this.androidBuildTop().resolve(_ravenizerJar)

private fun Project.ravenwoodPropFile() = this.rootProject.rootDir.resolve("ravenwood.properties")

/**
 * Represents jar files in ravenwood-runtime.
 */
private data class ClassPathJars(
    /** Jar files that need to be added before the test jar file. */
    val preJars: List<String>,

    /** Jar files that need to be added after the test jar file. */
    val postJars: List<String>,
)

/**
 * Return the ravenwood-runtime jars to be added to the classpath.
 *
 * The input file looks like this: http://ac/frameworks/base/ravenwood/texts/ravenwood-classpath.txt
 * Parse it and return two list -- ones before "{TEST_JARS}" and ones after.
 */
private fun Project.ravenwoodRuntimeJars(): ClassPathJars {
    val commentMatcher = Regex("#.*")
    val jars = Files.lines(ravenwoodRuntimeClasspathFile().toPath())
        .map<String> { line -> line.trim().replace(commentMatcher, "")}
        .filter { line -> !line.isEmpty() }
        .toList()

    val testJarsIndex = jars.indexOf("{TEST_JARS}")

    if (testJarsIndex < 0) {
        throw RuntimeException("Failed to read ravenwood-classpath.txt: {TEST_JARS} not found")
    }
    val pre = jars.subList(0, testJarsIndex - 1)
    val post = jars.subList(testJarsIndex + 1, jars.size)
    return ClassPathJars(pre, post)
}

/**
 * Jars in ravenwood-utils to be (optionally) used when compiling tests. They contain RavenwoodRule,
 * various ravenwood annotations, etc.
 */
// TODO: Do not hardcode this. However, unlike ravenwood-runtime, we don't have
// a source of truth for this at the moment.
// If we add "android_ravenwood_libgroup" to the java deps file, we could get it fom there.
private fun Project.ravenwoodUtilsJars() = listOf(
    "ravenwood-junit.jar",
    "ravenwood-framework.jar",
    "mockito-ravenwood-prebuilt.jar",
)

private fun Project.ravenwoodJvmArgs() = listOf(
    "--add-modules=jdk.compiler",
    "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
)

private fun Project.ravenwoodJavaProps() = mapOf(
    "android.ravenwood.version" to "1",
    "android.ravenwood.runtime_path" to "${ravenwoodRuntimePath()}/",
    "android.ravenwood.prop_file" to ravenwoodPropFile().toString(),
    "java.library.path" to "${ravenwoodRuntimePath()}/lib64/",
    // "android.ravenwood.artifacts_path" to ..., // Optional parameter, no need to set.
)

private fun Project.ravenwoodEnvVars() = mapOf(
    "LANG" to "C",
    "LC_ALL" to "C",
    "RAVENWOOD_RUNTIME_PATH" to "${ravenwoodRuntimePath()}",

    // TODO: Need to add the test's self JNI path, if a test uses any.
    "LD_LIBRARY_PATH" to "${ravenwoodRuntimePath()}/lib64/",
)

private fun ravenizerOptionalArgs() = listOf(
    "--info",
    "--strip-mockito",
)

/**
 * Task to run Ravenizer.
 */
internal abstract class RavenizeClassesTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    companion object {
        private val log = Logging.getLogger("Ravenwood:Ravenizer")
    }

    /** This contains all the dependency jars */
    @get:InputFiles
    abstract val inputJars: ListProperty<RegularFile>

    /** The directories that contain test classes */
    @get:InputFiles
    abstract val inputDirectories: ListProperty<Directory>

    /** Ravenized output jar */
    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    /** Path to ravenizer.jar */
    // Tasks can't refer to top-level vals because that'd make it non-static. So we need to
    // pass them as properties.
    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    /** Optional arguments to Ravenizer */
    @get:Input
    abstract val ravenizerArgs: ListProperty<String>

    @TaskAction
    fun execute() {
        val outJarFile = outputJar.get().asFile
        outJarFile.parentFile.mkdirs()

        if (log.isInfoEnabled) {
            log.info(
                "Ravenwood: Ravenizer:\n  out={}\n  in-jars=\n{}\n  in-dirs=\n{}\n",
                outputJar.get().asFile,
                inputJars.get().map { "    $it" }.joinToString("\n"),
                inputDirectories.get().map { "    $it" }.joinToString("\n"),
            )
        }

        // To avoid the arguments from getting too long, pass input file paths from an @ file.
        val atFile = File(temporaryDir, "ravenizer-input.txt")
        BufferedWriter(FileWriter(atFile)).use { file ->
            inputJars.get().forEach {
                file.write("--in-jar\n")
                file.write(it.asFile.absolutePath)
                file.write("\n")
            }
            inputDirectories.get().forEach {
                file.write("--in-dir\n")
                file.write(it.asFile.absolutePath)
                file.write("\n")
            }
        }

        // Use injected execOperations instead of the project object
        execOperations.javaexec {
            classpath = toolClasspath
            mainClass.set("com.android.platform.test.ravenwood.ravenizer.RavenizerMain")

            val argsList = mutableListOf<String>()
            argsList.add("--out-jar")
            argsList.add(outJarFile.absolutePath)

            argsList.add("@$atFile")

            argsList.addAll(ravenizerArgs.get())
            args(argsList)
        }
    }
}

android {
    namespace = "com.android.ravenwoodtest.gradlesample"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.android.ravenwoodtest.gradlesample"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        // Let host tests use resources.
        unitTests {
            isIncludeAndroidResources = true
        }

        // Needed for Ravenwood tests.
        if (enableRavenwood) {
            unitTests.all { test ->
                configureRavenwoodTest(test)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    if (enableRavenwood) {
        configureRavenwood()
    }
    if (enableRobolectric) {
        testImplementation("org.robolectric:robolectric:4.16.1")

        // Build the test with ravenwood-utils.jars.
        // We need to do this for Ravenwood too, but we do this in configureRavenwood(),
        // to avoid ravenwood specific logic in dependencies, so we can easily copy it
        // to sonata.
        testCompileOnly(files(ravenwoodUtilsJars().map {"${ravenwoodUtilsPath()}/$it"}))
    }
}

/**
 * Configure [test] to be run with the JVM arguments, etc, as required by Ravenwood.
 */
internal fun Project.configureRavenwoodTest(test: Test) {
    log.info("Ravenwood: configureRavenwoodTest(Test={${test.name}})")

    test.jvmArgs(ravenwoodJvmArgs())
    ravenwoodEnvVars().forEach { test.environment(it.key, it.value) }
    ravenwoodJavaProps().forEach { test.systemProperty(it.key, it.value) }

    // If it's the test task we're interested in, update its classpath.
    if (test.name == "testDebugUnitTest") {
        val ravenizerTask = project.tasks.named<RavenizeClassesTask>("ravenizerDebugClasses")
        test.dependsOn(ravenizerTask)

        // Output jar file
        val ravenizedJar = project.files(ravenizerTask.flatMap { it.outputJar })

        val ravenwoodRuntimeJars = ravenwoodRuntimeJars()

        val ravenizedClassPath =
            files(ravenwoodRuntimeJars.preJars.map { "${ravenwoodRuntimePath()}/$it" }) +
            ravenizedJar +
            files(ravenwoodRuntimeJars.postJars.map { "${ravenwoodRuntimePath()}/$it" })

        if (log.isInfoEnabled) {
            log.info(
                "Ravenwood: Updating classpath for {}: ravenized=\n{}",
                test.name, ravenizedClassPath.files.map { "    $it" }.joinToString("\n")
            )
        }

        // Update the class path.
        test.classpath = ravenizedClassPath
    }
}

/**
 * Prepare host tests for Ravenwood.
 */
internal fun Project.configureRavenwood() {
    // Compile ravenwood tests with ravenwood-utils jars.
    dependencies {
        "testCompileOnly"(
            files(ravenwoodUtilsJars().map {"${ravenwoodUtilsPath()}/$it"})
        )
    }

    // Register Ravenizer task on all host tests.
    val androidComponents = extensions.findByType(AndroidComponentsExtension::class.java)
    androidComponents?.onVariants { variant ->
        val unitTestComponent = (variant as? HasHostTests)?.hostTests[HostTestBuilder.UNIT_TEST_TYPE]
            ?: return@onVariants

        val taskName = "ravenizer${variant.name.replaceFirstChar { it.uppercase() }}Classes"

        val ravenizerTaskProvider = project.tasks.register<RavenizeClassesTask>(taskName) {
            toolClasspath.from(project.file(ravenizerJar()))
            ravenizerArgs.set(ravenizerOptionalArgs())
        }

        unitTestComponent.artifacts
            .forScope(ScopedArtifacts.Scope.ALL)
            .use(ravenizerTaskProvider)
            .toTransform(
                com.android.build.api.artifact.ScopedArtifact.CLASSES,
                RavenizeClassesTask::inputJars,
                RavenizeClassesTask::inputDirectories,
                RavenizeClassesTask::outputJar
            )
    }
}
