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

package com.android.systemui.biometrics.ui.view

import android.text.TextDirectionHeuristics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.compose.SelectedUserAwareInputConnection
import com.android.systemui.common.ui.compose.SelectedUserAwareLocalContext
import com.android.systemui.res.R
import kotlinx.coroutines.launch

@Composable
fun CredentialPasswordView(
    onVerify: suspend (CharSequence) -> ByteArray?,
    onSuccess: (ByteArray) -> Unit,
    isVisible: Boolean,
    error: String = "",
    userId: Int,
) {
    val state = remember { TextFieldState() }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        } else {
            state.clearText()
        }
    }

    val onSubmit = {
        if (state.text.isNotEmpty()) {
            scope.launch {
                val attestation = onVerify(state.text)
                if (attestation != null) {
                    onSuccess(attestation)
                } else {
                    state.clearText()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PromptErrorText(error = error)

        val color = MaterialTheme.colorScheme.onSurfaceVariant
        SelectedUserAwareInputConnection(selectedUserId = userId) {
            SelectedUserAwareLocalContext(selectedUserId = userId) {
                OutlinedSecureTextField(
                    state = state,
                    textStyle =
                        LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            // Ideally, TextDirection.Content would be used here but doesn't work
                            // properly with the bullets in the password field. Check first
                            // character direction manually
                            textDirection =
                                if (
                                    TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(
                                        state.text,
                                        0,
                                        state.text.length,
                                    )
                                )
                                    TextDirection.Rtl
                                else TextDirection.Ltr,
                        ),
                    modifier =
                        Modifier.width(
                                dimensionResource(id = R.dimen.keyguard_password_field_width)
                            )
                            .testTag("lockPassword")
                            .focusRequester(focusRequester),
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    isError = error.isNotEmpty(),
                    onKeyboardAction = { onSubmit() },
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = color,
                            unfocusedBorderColor = color,
                        ),
                )
            }
        }
    }
}
