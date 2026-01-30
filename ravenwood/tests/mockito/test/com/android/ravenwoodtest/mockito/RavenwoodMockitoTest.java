/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ravenwoodtest.mockito;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.platform.test.ravenwood.RavenwoodRule;

import org.junit.Rule;
import org.junit.Test;

public class RavenwoodMockitoTest {
    @Rule public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Test
    public void testMockJdkClass() {
        Process object = mock(Process.class);

        when(object.exitValue()).thenReturn(42);

        assertThat(object.exitValue()).isEqualTo(42);
    }

    @Test
    public void testMockAndroidClass1() {
        Intent object = mock(Intent.class);

        when(object.getAction()).thenReturn("ACTION_RAVENWOOD");

        assertThat(object.getAction()).isEqualTo("ACTION_RAVENWOOD");
    }

    @Test
    public void testMockAndroidClass2() {
        Context object = mock(Context.class);

        when(object.getPackageName()).thenReturn("android");

        assertThat(object.getPackageName()).isEqualTo("android");
    }

    @Test
    public void testMockFinalClass() {
        var object = mock(Parcel.class);

        when(object.readInt()).thenReturn(123);

        assertThat(object.readInt()).isEqualTo(123);
    }

    public static final class MyFinalClass {
        public int getValue() {
            return 1;
        }
    }

    public static final class MyParcelable implements Parcelable {
        private final int mValue;

        public MyParcelable(int value) {
            mValue = value;
        }

        private MyParcelable(Parcel src) {
            mValue = src.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mValue);
        }

        public int getValue() {
            return mValue;
        }

        public static final Parcelable.Creator<MyParcelable> CREATOR =
                new Parcelable.Creator<>() {
                    public MyParcelable createFromParcel(Parcel in) {
                        return new MyParcelable(in);
                    }

                    public MyParcelable[] newArray(int size) {
                        return new MyParcelable[size];
                    }
                };
    }

    @Test
    public void testMockLocalFinalClass() {
        var p1 = mock(MyFinalClass.class);

        when(p1.getValue()).thenReturn(42);
        assertThat(p1.getValue()).isEqualTo(42);
    }

    @Test
    public void testMockLocalFinalParcelableClass() {
        // First, test the non-mock version, just in case.
        var p1 = new MyParcelable(1);
        assertThat(p1.getValue()).isEqualTo(1);

        var p2 = mock(MyParcelable.class);

        when(p2.getValue()).thenReturn(42);
        assertThat(p2.getValue()).isEqualTo(42);
    }
}
