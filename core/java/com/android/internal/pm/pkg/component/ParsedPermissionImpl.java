/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.pm.pkg.component;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.pkg.parsing.ParsingUtils;
import com.android.internal.util.DataClass;
import com.android.internal.util.Parcelling;
import com.android.internal.util.Parcelling.BuiltIn.ForInternedString;
import com.android.internal.util.Parcelling.BuiltIn.ForStringSet;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @hide
 */
@DataClass(genGetters = true, genSetters = true, genBuilder = false, genParcelable = false)
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ParsedPermissionImpl extends ParsedComponentImpl implements ParsedPermission,
        Parcelable {

    private static final ForStringSet sForStringSet =
            Parcelling.Cache.getOrCreate(ForStringSet.class);

    @Nullable
    private String backgroundPermission;
    @Nullable
    @DataClass.ParcelWith(ForInternedString.class)
    private String group;
    private int requestRes;
    private int protectionLevel;
    private int requiresPurposeTargetSdkVersion;
    private boolean tree;
    private boolean purposeRequired;
    @Nullable
    private ParsedPermissionGroup parsedPermissionGroup;
    @Nullable
    private Set<String> knownCerts;
    @Nullable
    private List<ParsedValidPurpose> validPurposes;

    @VisibleForTesting
    public ParsedPermissionImpl() {
    }

    public ParsedPermissionGroup getParsedPermissionGroup() {
        return parsedPermissionGroup;
    }

    public ParsedPermissionImpl setGroup(String group) {
        this.group = TextUtils.safeIntern(group);
        return this;
    }

    protected void setKnownCert(String knownCert) {
        // Convert the provided digest to upper case for consistent Set membership
        // checks when verifying the signing certificate digests of requesting apps.
        this.knownCerts = Set.of(knownCert.toUpperCase(Locale.US));
    }

    protected void setKnownCerts(String[] knownCerts) {
        this.knownCerts = new ArraySet<>();
        for (String knownCert : knownCerts) {
            this.knownCerts.add(knownCert.toUpperCase(Locale.US));
        }
    }

    @NonNull
    @Override
    public Set<String> getKnownCerts() {
        return knownCerts == null ? Collections.emptySet() : knownCerts;
    }

    @NonNull
    @Override
    public List<ParsedValidPurpose> getValidPurposes() {
        return validPurposes == null ? Collections.emptyList() : validPurposes;
    }

    public String toString() {
        return "Permission{"
                + Integer.toHexString(System.identityHashCode(this))
                + " " + getName() + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(this.backgroundPermission);
        dest.writeString(this.group);
        dest.writeInt(this.requestRes);
        dest.writeInt(this.protectionLevel);
        dest.writeInt(this.requiresPurposeTargetSdkVersion);
        dest.writeBoolean(this.tree);
        dest.writeBoolean(this.purposeRequired);
        dest.writeParcelable((ParsedPermissionGroupImpl) this.parsedPermissionGroup, flags);
        sForStringSet.parcel(knownCerts, dest, flags);
        ParsingUtils.writeParcelableList(dest, this.validPurposes);
    }

    protected ParsedPermissionImpl(Parcel in) {
        super(in);
        this.backgroundPermission = in.readString();
        this.group = TextUtils.safeIntern(in.readString());
        this.requestRes = in.readInt();
        this.protectionLevel = in.readInt();
        this.requiresPurposeTargetSdkVersion = in.readInt();
        this.tree = in.readBoolean();
        this.purposeRequired = in.readBoolean();
        this.parsedPermissionGroup = in.readParcelable(
                ParsedPermissionGroupImpl.class.getClassLoader(), ParsedPermissionGroupImpl.class);
        this.knownCerts = sForStringSet.unparcel(in);
        this.validPurposes =
                ParsingUtils.createTypedInterfaceList(in, ParsedValidPurposeImpl.CREATOR);
    }

    @NonNull
    public static final Parcelable.Creator<ParsedPermissionImpl> CREATOR =
            new Parcelable.Creator<ParsedPermissionImpl>() {
                @Override
                public ParsedPermissionImpl createFromParcel(Parcel source) {
                    return new ParsedPermissionImpl(source);
                }

                @Override
                public ParsedPermissionImpl[] newArray(int size) {
                    return new ParsedPermissionImpl[size];
                }
            };



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/core/java/com/android/internal/pm/pkg/component/ParsedPermissionImpl.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    public ParsedPermissionImpl(
            @Nullable String backgroundPermission,
            @Nullable String group,
            int requestRes,
            int protectionLevel,
            int requiresPurposeTargetSdkVersion,
            boolean tree,
            boolean purposeRequired,
            @Nullable ParsedPermissionGroup parsedPermissionGroup,
            @Nullable Set<String> knownCerts,
            @Nullable List<ParsedValidPurpose> validPurposes) {
        this.backgroundPermission = backgroundPermission;
        this.group = group;
        this.requestRes = requestRes;
        this.protectionLevel = protectionLevel;
        this.requiresPurposeTargetSdkVersion = requiresPurposeTargetSdkVersion;
        this.tree = tree;
        this.purposeRequired = purposeRequired;
        this.parsedPermissionGroup = parsedPermissionGroup;
        this.knownCerts = knownCerts;
        this.validPurposes = validPurposes;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public @Nullable String getBackgroundPermission() {
        return backgroundPermission;
    }

    @DataClass.Generated.Member
    public @Nullable String getGroup() {
        return group;
    }

    @DataClass.Generated.Member
    public int getRequestRes() {
        return requestRes;
    }

    @DataClass.Generated.Member
    public int getProtectionLevel() {
        return protectionLevel;
    }

    @DataClass.Generated.Member
    public int getRequiresPurposeTargetSdkVersion() {
        return requiresPurposeTargetSdkVersion;
    }

    @DataClass.Generated.Member
    public boolean isTree() {
        return tree;
    }

    @DataClass.Generated.Member
    public boolean isPurposeRequired() {
        return purposeRequired;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setBackgroundPermission(@NonNull String value) {
        backgroundPermission = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setRequestRes( int value) {
        requestRes = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setProtectionLevel( int value) {
        protectionLevel = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setRequiresPurposeTargetSdkVersion( int value) {
        requiresPurposeTargetSdkVersion = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setTree( boolean value) {
        tree = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setPurposeRequired( boolean value) {
        purposeRequired = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setParsedPermissionGroup(@NonNull ParsedPermissionGroup value) {
        parsedPermissionGroup = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setKnownCerts(@NonNull Set<String> value) {
        knownCerts = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionImpl setValidPurposes(@NonNull List<ParsedValidPurpose> value) {
        validPurposes = value;
        return this;
    }

    @DataClass.Generated(
            time = 1750112502414L,
            codegenVersion = "1.0.23",
            sourceFile = "frameworks/base/core/java/com/android/internal/pm/pkg/component/ParsedPermissionImpl.java",
            inputSignatures = "private static final  com.android.internal.util.Parcelling.BuiltIn.ForStringSet sForStringSet\nprivate @android.annotation.Nullable java.lang.String backgroundPermission\nprivate @android.annotation.Nullable @com.android.internal.util.DataClass.ParcelWith(com.android.internal.util.Parcelling.BuiltIn.ForInternedString.class) java.lang.String group\nprivate  int requestRes\nprivate  int protectionLevel\nprivate  int requiresPurposeTargetSdkVersion\nprivate  boolean tree\nprivate  boolean purposeRequired\nprivate @android.annotation.Nullable com.android.internal.pm.pkg.component.ParsedPermissionGroup parsedPermissionGroup\nprivate @android.annotation.Nullable java.util.Set<java.lang.String> knownCerts\nprivate @android.annotation.Nullable java.util.List<com.android.internal.pm.pkg.component.ParsedValidPurpose> validPurposes\npublic static final @android.annotation.NonNull android.os.Parcelable.Creator<com.android.internal.pm.pkg.component.ParsedPermissionImpl> CREATOR\npublic  com.android.internal.pm.pkg.component.ParsedPermissionGroup getParsedPermissionGroup()\npublic  com.android.internal.pm.pkg.component.ParsedPermissionImpl setGroup(java.lang.String)\nprotected  void setKnownCert(java.lang.String)\nprotected  void setKnownCerts(java.lang.String[])\npublic @android.annotation.NonNull @java.lang.Override java.util.Set<java.lang.String> getKnownCerts()\npublic @android.annotation.NonNull @java.lang.Override java.util.List<com.android.internal.pm.pkg.component.ParsedValidPurpose> getValidPurposes()\npublic  java.lang.String toString()\npublic @java.lang.Override int describeContents()\npublic @java.lang.Override void writeToParcel(android.os.Parcel,int)\nclass ParsedPermissionImpl extends com.android.internal.pm.pkg.component.ParsedComponentImpl implements [com.android.internal.pm.pkg.component.ParsedPermission, android.os.Parcelable]\n@com.android.internal.util.DataClass(genGetters=true, genSetters=true, genBuilder=false, genParcelable=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
