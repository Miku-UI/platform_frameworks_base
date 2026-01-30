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

package com.android.server.appfunctions;

import android.annotation.NonNull;
import android.content.pm.Signature;
import android.content.pm.SignedPackage;
import android.util.Slog;

import java.util.ArrayList;
import java.util.List;

public class SignedPackageParser {
    private static final String SIGNED_PACKAGE_SEPARATOR = ";";
    private static final String CERTIFICATE_SEPARATOR = ":";
    private static final String TAG = SignedPackageParser.class.getSimpleName();

    /**
     * Parse a {@link SignedPackage} from a string input.
     *
     * @param input the package name, optionally followed by a colon and a signing certificate
     *     digest
     * @return the parsed {@link SignedPackage}, or {@code null} if the input is invalid
     */
    private static SignedPackage parse(@NonNull String input) {
        String packageName;
        byte[] certificate;
        int certificateSeparatorIndex = input.indexOf(CERTIFICATE_SEPARATOR);
        if (certificateSeparatorIndex != -1) {
            packageName = input.substring(0, certificateSeparatorIndex);
            String certificateString = input.substring(certificateSeparatorIndex + 1);
            try {
                certificate = new Signature(certificateString).toByteArray();
            } catch (IllegalArgumentException e) {
                Slog.w(TAG, "Cannot parse the signed package input of: " + input, e);
                return null;
            }
        } else {
            packageName = input;
            certificate = null;
        }
        return new SignedPackage(packageName, certificate);
    }

    /**
     * Parse a list of {@link SignedPackage}s from a string input.
     *
     * @param input the package names, each optionally followed by a colon and a signing certificate
     *     digest
     * @return the parsed list of valid {@link SignedPackage}s
     */
    @NonNull
    public static List<SignedPackage> parseList(@NonNull String input) throws Exception {
        List<SignedPackage> signedPackages = new ArrayList<>();
        if (input.isEmpty()) {
            return signedPackages;
        }

        for (String signedPackageInput : input.split(SIGNED_PACKAGE_SEPARATOR)) {
            SignedPackage signedPackage = parse(signedPackageInput);
            if (signedPackage == null) {
                throw new IllegalArgumentException(
                        "Cannot parse the signed package input of: " + signedPackageInput);
            } else {
                signedPackages.add(signedPackage);
            }
        }
        return signedPackages;
    }

    @NonNull
    public static String serializePackagesOnly(@NonNull List<String> packages) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < packages.size(); i++) {
            result.append(packages.get(i));
            if (i < (packages.size() - 1)) {
                result.append(SIGNED_PACKAGE_SEPARATOR);
            }
        }
        return result.toString();
    }
}
