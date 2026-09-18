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

package com.google.errorprone.bugpatterns.android;

import static com.google.errorprone.BugPattern.LinkType.NONE;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.hasAnnotation;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.android.FieldMatchers.staticField;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol.VarSymbol;

import java.util.List;

/**
 * Checker to prevent passing special UserHandle values to methods that are annotated with
 * {@code @CannotBeSpecialUser}.
 */
@AutoService(BugChecker.class)
@BugPattern(
    summary = "Do not pass special UserHandle values to methods that do not accept them.",
    linkType = NONE,
    severity = ERROR)
public class CannotBeSpecialUserChecker extends BugChecker implements MethodInvocationTreeMatcher {

    private static final String CANNOT_BE_SPECIAL_USER_ANNOTATION =
            "android.annotation.SpecialUsers.CannotBeSpecialUser";

    private static final Matcher<ExpressionTree> IS_FORBIDDEN_USER_HANDLE =
            anyOf(
                    staticField("android.os.UserHandle", "CURRENT"),
                    staticField("android.os.UserHandle", "USER_CURRENT"),
                    staticField("android.os.UserHandle", "ALL"),
                    staticField("android.os.UserHandle", "USER_ALL"),
                    staticField("android.os.UserHandle", "NULL"),
                    staticField("android.os.UserHandle", "USER_NULL"),
                    staticField("android.os.UserHandle", "CURRENT_OR_SELF"),
                    staticField("android.os.UserHandle", "USER_CURRENT_OR_SELF"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        List<? extends ExpressionTree> arguments = tree.getArguments();
        if (arguments.isEmpty()) {
            return Description.NO_MATCH;
        }

        List<VarSymbol> formals = ASTHelpers.getSymbol(tree).params();
        for (int i = 0; i < formals.size(); i++) {
            VarSymbol formal = formals.get(i);
            if (ASTHelpers.hasAnnotation(formal, CANNOT_BE_SPECIAL_USER_ANNOTATION, state)) {
                ExpressionTree argument = arguments.get(i);
                if (IS_FORBIDDEN_USER_HANDLE.matches(argument, state)) {
                    return buildDescription(argument)
                            .setMessage(
                                    "This method does not accept special UserHandle values. Please"
                                        + " provide an explicit user instead. E.g., instead of"
                                        + " using USER_CURRENT, consider using"
                                        + " ActivityManager.getCurrentUser().")
                            .build();
                }
            }
        }
        return Description.NO_MATCH;
    }
}
