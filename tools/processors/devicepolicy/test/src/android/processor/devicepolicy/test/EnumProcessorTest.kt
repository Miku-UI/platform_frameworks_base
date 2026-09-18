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

package android.processor.devicepolicy.test

import org.junit.Test

class EnumProcessorTest {

    private val compiler = PolicyIdentifierCompiler()

    companion object {
        const val ALLOWED_DPC_TYPES_SNIPPET = PolicyIdentifierCompiler.ALLOWED_DPC_TYPES_SNIPPET
    }

    @Test
    fun test_zeroValue_failsToCompile() {
        compiler.compileExpectError(
            """
                // The intdef used for the policy values.
                /** First entry */
                public static final int ENUM_ENTRY_FIRST = 0;
                /** Second entry */
                public static final int ENUM_ENTRY_LAST = 1;
                /** Intdef for an enum. */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"ENUM_ENTRY_"},
                        value = {ENUM_ENTRY_FIRST, ENUM_ENTRY_LAST})
                public @interface EnumIntDef {}

                /**
                * Enum values must not be 0.
                */
                @EnumPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        defaultValue = ENUM_ENTRY_LAST,
                        intDef = EnumIntDef.class,
                        resolutionMechanism = @EnumResolutionMechanism(custom = true)
                )
                public static final PolicyIdentifier<Integer> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
                """,
            expectedError =
                "The Protobuf enum value '0' is reserved for the default *_UNSPECIFIED case " +
                    "(https://google.aip.dev/126) and should not be used for any other enum " +
                    "value. Found in: ENUM_ENTRY_FIRST",
        )
    }

    @Test
    fun test_allowedZeroValue_compiles() {
        compiler.compileExpectSuccess(
            """
                // The intdef used for the policy values.
                /** First entry */
                public static final int AUTO_TIME_USER_CHOICE = 0;
                /** Second entry */
                public static final int AUTO_TIME_DISABLED = 1;
                /** Intdef for an enum. */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"AUTO_TIME_"},
                        value = {AUTO_TIME_USER_CHOICE, AUTO_TIME_DISABLED})
                public @interface AutoTimeValue {}

                /**
                * Enum values can be 0 if allowed.
                */
                @EnumPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        defaultValue = AUTO_TIME_DISABLED,
                        intDef = AutoTimeValue.class,
                        resolutionMechanism = @EnumResolutionMechanism(custom = true)
                )
                public static final PolicyIdentifier<Integer> AUTO_TIME =
                    new PolicyIdentifier<>("AUTO_TIME");
            """
        )
    }

    @Test
    fun test_missingResolutionMechanism_failsToCompile() {
        compiler.compileExpectError(
            """
                // The intdef used for the policy values.
                /** First entry */
                public static final int ENUM_ENTRY_1 = 1;
                /** Second entry */
                public static final int ENUM_ENTRY_2 = 2;
                /** Intdef for an enum. */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"ENUM_ENTRY_"},
                        value = {ENUM_ENTRY_1, ENUM_ENTRY_2})
                public @interface EnumIntDef {}

                /**
                * ResolutionMechanism can not be empty.
                */
                @EnumPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        defaultValue = ENUM_ENTRY_2,
                        intDef = EnumIntDef.class,
                        resolutionMechanism = @EnumResolutionMechanism()
                )
                public static final PolicyIdentifier<Integer> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
            """,
            expectedError = "In @EnumResolutionMechanism, a resolution mechanism must be set.",
        )
    }

    @Test
    fun test_twoResolutionMechanisms_failsToCompile() {
        compiler.compileExpectError(
            """
                // The intdef used for the policy values.
                /** First entry */
                public static final int ENUM_ENTRY_1 = 1;
                /** Second entry */
                public static final int ENUM_ENTRY_2 = 2;
                /** Intdef for an enum. */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"ENUM_ENTRY_"},
                        value = {ENUM_ENTRY_1, ENUM_ENTRY_2})
                public @interface EnumIntDef {}

                /**
                * ResolutionMechanism can not be empty.
                */
                @EnumPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        defaultValue = ENUM_ENTRY_2,
                        intDef = EnumIntDef.class,
                        resolutionMechanism = @EnumResolutionMechanism(
                        custom=true,
                            mostRestrictive={
                                ENUM_ENTRY_1,
                                ENUM_ENTRY_2,
                            }
                        )
                )
                public static final PolicyIdentifier<Integer> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
            """,
            expectedError =
                "In @EnumResolutionMechanism, a single resolution mechanism can be selected.",
        )
    }

    @Test
    fun test_mostRestrictiveResolutionMechanism_duplicates_failsToCompile() {
        compiler.compileExpectError(
            """
                // The intdef used for the policy values.
                /** First entry */
                public static final int ENUM_ENTRY_FIRST = 1;
                /** Second entry */
                public static final int ENUM_ENTRY_LAST = 2;
                /** Intdef for an enum. */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"ENUM_ENTRY_"},
                        value = {ENUM_ENTRY_FIRST, ENUM_ENTRY_LAST})
                public @interface EnumIntDef {}

                /**
                * MostRestrictive ResolutionMechanism can not contain duplicates.
                */
                @EnumPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        defaultValue = ENUM_ENTRY_FIRST,
                        intDef = EnumIntDef.class,
                        resolutionMechanism = @EnumResolutionMechanism(mostRestrictive={
                            ENUM_ENTRY_FIRST,
                            ENUM_ENTRY_FIRST,
                            ENUM_ENTRY_FIRST,
                            ENUM_ENTRY_LAST,
                        })
                )
                public static final PolicyIdentifier<Integer> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
            """,
            expectedError = "mostRestrictive contains duplicate values: ENUM_ENTRY_FIRST",
        )
    }

    @Test
    fun test_mostRestrictiveResolutionMechanism_unexpectedValues_failsToCompile() {
        compiler.compileExpectError(
            """
                // The intdef used for the policy values.
                /** First entry */
                public static final int ENUM_ENTRY_FIRST = 1;
                /** Second entry */
                public static final int ENUM_ENTRY_LAST = 2;
                /** Intdef for an enum. */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"ENUM_ENTRY_"},
                        value = {ENUM_ENTRY_FIRST, ENUM_ENTRY_LAST})
                public @interface EnumIntDef {}

                /**
                * MostRestrictive ResolutionMechanism must contain all values.
                */
                @EnumPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        defaultValue = ENUM_ENTRY_FIRST,
                        intDef = EnumIntDef.class,
                        resolutionMechanism = @EnumResolutionMechanism(mostRestrictive={
                            ENUM_ENTRY_FIRST,
                            5,
                            ENUM_ENTRY_LAST,
                            9,
                        })
                )
                public static final PolicyIdentifier<Integer> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
            """,
            expectedError = "mostRestrictive contains unexpected values: 5,9",
        )
    }

    @Test
    fun test_mostRestrictiveResolutionMechanism_missingValues_failsToCompile() {
        compiler.compileExpectError(
            """
                // The intdef used for the policy values.
                /** First entry */
                public static final int ENUM_ENTRY_FIRST = 1;
                /** Second entry */
                public static final int ENUM_ENTRY_LAST = 2;
                /** Intdef for an enum. */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"ENUM_ENTRY_"},
                        value = {ENUM_ENTRY_FIRST, ENUM_ENTRY_LAST})
                public @interface EnumIntDef {}

                /**
                * MostRestrictive ResolutionMechanism must contain all values.
                */
                @EnumPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        defaultValue = ENUM_ENTRY_FIRST,
                        intDef = EnumIntDef.class,
                        resolutionMechanism = @EnumResolutionMechanism(mostRestrictive={
                            ENUM_ENTRY_FIRST,
                        })
                )
                public static final PolicyIdentifier<Integer> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
            """,
            expectedError = "mostRestrictive must also contain: ENUM_ENTRY_LAST",
        )
    }
}
