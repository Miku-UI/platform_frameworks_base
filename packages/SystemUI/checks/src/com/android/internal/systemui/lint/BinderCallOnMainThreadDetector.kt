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

package com.android.internal.systemui.lint

import com.android.internal.systemui.lint.BindServiceOnMainThreadDetector.Companion.containingMethodOrClassHasWorkerThreadAnnotation
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingDeclaration

/**
 * A linter that warns when binder calls are being made not on a background thread.
 *
 * Please add additional binder calls to the list in [binderCalls]! This will help protect future
 * engineers from adding a binder call on the main thread without realizing it.
 */
class BinderCallOnMainThreadDetector : Detector(), SourceCodeScanner {
    // TODO: b/469073407 - Add more binder calls.
    /**
     * A list of known binder calls that should be invoked on the background.
     *
     * The format is "${fully-qualified-class-name}#${method-name}". The method name will be used as
     * a regex. Some examples:
     * - If there's a single method in the class that's a binder call, use the method name directly:
     *   `getBroadcast` e.g.
     * - If *all* method calls in a class are binder calls, use `.*` as the method name.
     * - If any registrations or unregistrations are binder calls, use `(register|unregister).*` as
     *   the method name.
     */
    private val binderCalls =
        listOf(
            // go/keep-sorted start
            "android.app.PendingIntent#getBroadcast",
            "android.app.PendingIntent#queryIntentComponents",
            "android.content.pm.PackageManager#getApplicationInfo",
            "android.media.projection.MediaProjectionManager#stopActiveProjection",
            "android.media.session.MediaController#registerCallback",
            "android.media.session.MediaController#unregisterCallback",
            "android.telephony.SubscriptionManager#.*",
            "com.android.internal.statusbar.IStatusBarService#disableForUser",
            // go/keep-sorted end
        )

    // A list of constructors that should be considered binder calls.
    private val constructorBinderCalls =
        listOf(
            // go/keep-sorted start
            "android.media.session.MediaController"
            // go/keep-sorted end
        )

    private data class BinderCall(
        /** Package location of binder call, like "android.app". */
        val packageName: String,
        /** Class containing binder call, like "PendingIntent". */
        val className: String,
        /** Binder call method name, like "getBroadcast". */
        val methodName: String,
    )

    private val parsedBinderCalls: List<BinderCall> =
        binderCalls.map { binderCallString ->
            val classAndMethod = binderCallString.split("#")
            check(classAndMethod.size == 2)
            val fullyQualifiedClass = classAndMethod[0]
            val methodName = classAndMethod[1]

            val indexOfPackageClassSplit = fullyQualifiedClass.indexOfLast { it == '.' }
            val packageName = fullyQualifiedClass.substring(0, indexOfPackageClassSplit)
            val className =
                fullyQualifiedClass.substring(
                    indexOfPackageClassSplit + 1,
                    fullyQualifiedClass.length,
                )

            BinderCall(methodName = methodName, className = className, packageName = packageName)
        }

    override fun getApplicableConstructorTypes() = constructorBinderCalls

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // #visitConstructor should only be invoked if the constructor is in our binder call list,
        // so we can assume it's a binder call and immediately visit it.
        visitBinderCall(context, node)
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                visitMethodInternal(context, node, method)
            }
        }
    }

    private fun visitMethodInternal(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val visitedMethodName = method.name
        val visitedPackageName = context.evaluator.getPackage(method)?.qualifiedName
        val visitedClassName = (method.parent as? PsiClass)?.name ?: return

        val isBinderCall: Boolean =
            parsedBinderCalls.any { binderCall ->
                visitedPackageName == binderCall.packageName &&
                    visitedClassName == binderCall.className &&
                    visitedMethodName.matches(Regex(binderCall.methodName))
            }
        if (!isBinderCall) {
            return
        }

        visitBinderCall(context, node)
    }

    /**
     * Visits a call that is definitely in our list of binder calls to check if it's invoked on the
     * background or not.
     */
    private fun visitBinderCall(context: JavaContext, node: UCallExpression) {
        if (node.containingMethodOrClassHasWorkerThreadAnnotation(context)) {
            // This binder call is inside a method or class marked `@WorkerThread`, and that
            // annotation enforces that the method will only be invoked on the background thread.
            return
        }

        val containingBlock = findContainingBlock(node)
        if (containingBlock != null) {
            if (
                containingBlock.isLaunchedOnBackground() ||
                    containingBlock.isStartedWithBackgroundParameter()
            ) {
                // This binder call is correctly inside a block that explicitly runs in the
                // background.
                return
            }
        }

        if (isFlowOnBackground(node)) {
            // This binder call is correctly in a flow on the background.
            return
        }

        context.report(ISSUE, context.getNameLocation(node), message = "Binder call on main thread")
    }

    /**
     * Given a specific method call, find the block that contains it. For example:
     * ```
     * withContext(backgroundDispatcher) {
     *   manager.binderCall()
     * }
     * ```
     *
     * Calling this method with the `binderCall` node will return the call expression for
     * `withContext`.
     */
    private fun findContainingBlock(node: UCallExpression): UCallExpression? {
        var parent = node.uastParent
        while (parent != null && parent !is UCallExpression) {
            parent = parent.uastParent
        }
        return parent as UCallExpression?
    }

    /**
     * Returns true if the given [node] is flowed on the background via `.stateIn(backgroundScope)`
     * or `.flowOn(backgroundDispatcher)`.
     */
    private fun isFlowOnBackground(node: UCallExpression): Boolean {
        var containingDeclaration = node.getContainingDeclaration()
        while (containingDeclaration != null) {
            if (
                containingDeclaration.text.contains(
                    Regex("(stateIn|flowOn)\\s*\\(\\s*(bg|background)")
                )
            ) {
                return true
            }
            containingDeclaration = containingDeclaration.getContainingDeclaration()
        }
        return false
    }

    /**
     * Returns true if the call expression is received by a background worker. Matches expressions
     * like `bgScope.launch {}`, `backgroundExecutor.execute {}`, and `uiBgExecutor.execute {}`.
     */
    private fun UCallExpression.isLaunchedOnBackground(): Boolean {
        // For both member-defined functions like `Executor.execute` AND extension functions like
        // `CoroutineScope.launch`, the `Executor`/`CoroutineScope` is the receiver.
        val receiverIdentifier: String? =
            when (val receiver = this.receiver) {
                is USimpleNameReferenceExpression -> receiver.identifier
                is UParenthesizedExpression -> receiver.expression.asRenderString()
                else -> null
            }
        return receiverIdentifier?.isBackgroundIdentifier() == true
    }

    /**
     * Returns true if the call expression matches expressions that start a job in the background.
     * Matches expressions like `withContext(backgroundContext) {}` or `scope.launch(bgDispatcher)
     * {}`.
     */
    private fun UCallExpression.isStartedWithBackgroundParameter(): Boolean {
        if (this.methodName != null && this.methodName != "withContext") {
            return false
        }
        // For calls like `scope.launch(bgDispatcher)`, the method name is `null` so we can't
        // compare on method name and so we just check the first argument.
        // TODO: b/469073407 - Determine whether the method name for `launch` isn't getting
        // populated only in the linter test, or in production code as well.

        if (this.valueArguments.isEmpty()) {
            return false
        }

        val parameter = this.valueArguments[0]
        return parameter is USimpleNameReferenceExpression &&
            parameter.identifier.isBackgroundIdentifier()
    }

    /** Returns true if this string identifies a background worker of any sort. */
    private fun String.isBackgroundIdentifier(): Boolean {
        return this.matches(
            Regex(".*(bg|background|Bg|Background)(Context|Dispatcher|Executor|Scope)")
        )
    }

    companion object {
        @JvmStatic
        val ISSUE =
            Issue.create(
                id = "BinderCallOnMainThread",
                briefDescription = "Binder call on main thread",
                explanation =
                    """
                    Blocking IPC/binder calls must be done on a background thread to avoid heavy
                    computation on the main thread, because heavy computation on the main thread
                    can cause jank and frame drops. Switch work off the main thread using
                    `withContext(backgroundDispatcher)`, `flowOn(backgroundDispatcher)`, or
                    `stateIn(backgroundScope)` instead.
                    Note: If already using one of these methods, you may just need to update your
                    context, dispatcher, or scope variable name to include `background`.
                """,
                moreInfo =
                    "http://go/sysui-perf-dev-guide#moving-long-operations-to-background-thread",
                category = Category.PERFORMANCE,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        BinderCallOnMainThreadDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
