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

package com.android.mediaroutertest;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderInfo;
import android.os.Parcel;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.media.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaRoute2ProviderInfoTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_afterSetUniqueId_routeHasUniqueIdAndPackageName() {
        MediaRoute2ProviderInfo.Builder builder = createBuilder();
        MediaRoute2Info formerRoute = createTestRoute("id0", "name0");
        MediaRoute2Info latterRoute = createTestRoute("id1", "name1");

        builder.addRoute(formerRoute);
        builder.setUniqueId("fake.package.name", "fake.package.name/fakeRouteProvider");
        builder.addRoute(latterRoute);
        MediaRoute2ProviderInfo info = builder.build();

        MediaRoute2Info resultFormerRoute = info.getRoute("id0");
        MediaRoute2Info resultLatterRoute = info.getRoute("id1");
        assertThat(resultFormerRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultFormerRoute.getProviderPackageName()).isEqualTo("fake.package.name");
        assertThat(resultLatterRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultLatterRoute.getProviderPackageName()).isEqualTo("fake.package.name");
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_afterSetSystemRouteProvider_routeHasIsSystem() {
        MediaRoute2ProviderInfo.Builder builder = createBuilder();
        MediaRoute2Info formerRoute = createTestRoute("id0", "name0");
        MediaRoute2Info latterRoute = createTestRoute("id1", "name1");

        builder.addRoute(formerRoute);
        builder.setSystemRouteProvider(true);
        builder.addRoute(latterRoute);
        MediaRoute2ProviderInfo info = builder.build();

        MediaRoute2Info resultFormerRoute = info.getRoute("id0");
        MediaRoute2Info resultLatterRoute = info.getRoute("id1");
        assertThat(resultFormerRoute.isSystemRoute()).isTrue();
        assertThat(resultLatterRoute.isSystemRoute()).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_afterBuilderFromInfoFlagEnabled_inheritsAndAppliesCachedValues() {
        MediaRoute2ProviderInfo originalInfo =
                createProviderInfoWithRoute(
                        "fake.package.name",
                        "fake.package.name/fakeRouteProvider",
                        true,
                        "id0",
                        "name0");
        MediaRoute2ProviderInfo.Builder builder = new MediaRoute2ProviderInfo.Builder(originalInfo);
        MediaRoute2Info newRoute = createTestRoute("id1", "name1");

        builder.addRoute(newRoute);

        MediaRoute2ProviderInfo newInfo = builder.build();
        MediaRoute2Info resultNewRoute = newInfo.getRoute("id1");
        assertThat(resultNewRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultNewRoute.getProviderPackageName()).isEqualTo("fake.package.name");
        assertThat(resultNewRoute.isSystemRoute()).isTrue();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_afterBuilderFromInfoFlagDisabled_doesNotApplyNonUniqueIdCachedValues() {
        MediaRoute2ProviderInfo originalInfo =
                createProviderInfoWithRoute(
                        "fake.package.name",
                        "fake.package.name/fakeRouteProvider",
                        true,
                        "id0",
                        "name0");
        MediaRoute2ProviderInfo.Builder builder = new MediaRoute2ProviderInfo.Builder(originalInfo);
        MediaRoute2Info newRoute = createTestRoute("id1", "name1");

        builder.addRoute(newRoute);

        MediaRoute2ProviderInfo newInfo = builder.build();
        MediaRoute2Info resultNewRoute = newInfo.getRoute("id1");
        assertThat(resultNewRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultNewRoute.getProviderPackageName()).isNull();
        assertThat(resultNewRoute.isSystemRoute()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_withExistingPropertiesFlagEnabled_isOverwritten() {
        MediaRoute2ProviderInfo.Builder builder =
                createBuilder()
                        .setUniqueId("fake.package.name", "fake.package.name/fakeRouteProvider")
                        .setSystemRouteProvider(true);
        MediaRoute2Info routeWithProps =
                new MediaRoute2Info.Builder("id", "name")
                        .setProviderPackageName("newFake.package.name")
                        .setSystemRoute(false)
                        .addFeature("feature")
                        .build();

        builder.addRoute(routeWithProps);

        MediaRoute2ProviderInfo info = builder.build();
        MediaRoute2Info resultRoute = info.getRoute("id");
        assertThat(resultRoute.getProviderId()).isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultRoute.getProviderPackageName()).isEqualTo("fake.package.name");
        assertThat(resultRoute.isSystemRoute()).isTrue();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_withExistingPropertiesFlagDisabled_isNotOverwritten() {
        MediaRoute2ProviderInfo.Builder builder =
                createBuilder()
                        .setUniqueId("fake.package.name", "fake.package.name/fakeRouteProvider")
                        .setSystemRouteProvider(true);
        MediaRoute2Info routeWithProps =
                new MediaRoute2Info.Builder("id", "name")
                        .setProviderPackageName("newFake.package.name")
                        .setSystemRoute(false)
                        .addFeature("feature")
                        .build();

        builder.addRoute(routeWithProps);

        MediaRoute2ProviderInfo info = builder.build();
        MediaRoute2Info resultRoute = info.getRoute("id");
        assertThat(resultRoute.getProviderId()).isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultRoute.getProviderPackageName()).isEqualTo("newFake.package.name");
        assertThat(resultRoute.isSystemRoute()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_afterParcelRoundTripFlagEnabled_appliesCachedValues() {
        MediaRoute2ProviderInfo originalInfo =
                createProviderInfoWithRoute(
                        "fake.package.name",
                        "fake.package.name/fakeRouteProvider",
                        true,
                        "id0",
                        "name0");
        Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaRoute2ProviderInfo restoredInfo =
                MediaRoute2ProviderInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        MediaRoute2ProviderInfo.Builder builder = new MediaRoute2ProviderInfo.Builder(restoredInfo);
        MediaRoute2Info newRoute = createTestRoute("id1", "name1");

        builder.addRoute(newRoute);

        MediaRoute2ProviderInfo newInfo = builder.build();
        MediaRoute2Info resultNewRoute = newInfo.getRoute("id1");
        assertThat(resultNewRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultNewRoute.getProviderPackageName()).isEqualTo("");
        assertThat(resultNewRoute.isSystemRoute()).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_afterParcelRoundTripFlagDisabled_doesNotApplyCachedValues() {
        MediaRoute2ProviderInfo originalInfo =
                createProviderInfoWithRoute(
                        "fake.package.name",
                        "fake.package.name/fakeRouteProvider",
                        true,
                        "id0",
                        "name0");
        Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaRoute2ProviderInfo restoredInfo =
                MediaRoute2ProviderInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        MediaRoute2ProviderInfo.Builder builder = new MediaRoute2ProviderInfo.Builder(restoredInfo);
        MediaRoute2Info newRoute = createTestRoute("id1", "name1");

        builder.addRoute(newRoute);

        MediaRoute2ProviderInfo newInfo = builder.build();
        MediaRoute2Info resultNewRoute = newInfo.getRoute("id1");
        assertThat(resultNewRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultNewRoute.getProviderPackageName()).isNull();
        assertThat(resultNewRoute.isSystemRoute()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void addRoute_afterParcelRoundTripWithNullIsSystemFlagEnabled_appliesCachedValues() {
        MediaRoute2ProviderInfo originalInfo =
                createProviderInfoWithRoute(
                        "fake.package.name",
                        "fake.package.name/fakeRouteProvider",
                        null,
                        "id0",
                        "name0");
        Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaRoute2ProviderInfo restoredInfo =
                MediaRoute2ProviderInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        MediaRoute2ProviderInfo.Builder builder = new MediaRoute2ProviderInfo.Builder(restoredInfo);
        MediaRoute2Info newRoute = createTestRoute("id1", "name1");

        builder.addRoute(newRoute);

        MediaRoute2ProviderInfo newInfo = builder.build();
        MediaRoute2Info resultNewRoute = newInfo.getRoute("id1");
        assertThat(resultNewRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultNewRoute.getProviderPackageName()).isEqualTo("");
        assertThat(resultNewRoute.isSystemRoute()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_PROVIDER_INFO_BUILDER_CACHING)
    public void
        addRoute_afterParcelRoundTripWithNullProviderPackageNameFlagEnabled_appliesCachedValues() {
        MediaRoute2ProviderInfo originalInfo =
                createProviderInfoWithRoute(
                        null, "fake.package.name/fakeRouteProvider", true, "id0", "name0");
        Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaRoute2ProviderInfo restoredInfo =
                MediaRoute2ProviderInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        MediaRoute2ProviderInfo.Builder builder = new MediaRoute2ProviderInfo.Builder(restoredInfo);
        MediaRoute2Info newRoute = createTestRoute("id1", "name1");

        builder.addRoute(newRoute);

        MediaRoute2ProviderInfo newInfo = builder.build();
        MediaRoute2Info resultNewRoute = newInfo.getRoute("id1");
        assertThat(resultNewRoute.getProviderId())
                .isEqualTo("fake.package.name/fakeRouteProvider");
        assertThat(resultNewRoute.getProviderPackageName()).isEqualTo("");
        assertThat(resultNewRoute.isSystemRoute()).isFalse();
    }

    private MediaRoute2ProviderInfo createProviderInfoWithRoute(
            @NonNull String pkg,
            @NonNull String uId,
            Boolean isSystemRoute,
            @NonNull String routeId,
            @NonNull String routeName) {
        if (isSystemRoute == null) {
            return createBuilder()
                    .setUniqueId(pkg, uId)
                    .addRoute(createTestRoute(routeId, routeName))
                    .build();
        } else {
            return createBuilder()
                    .setUniqueId(pkg, uId)
                    .setSystemRouteProvider(isSystemRoute)
                    .addRoute(createTestRoute(routeId, routeName))
                    .build();
        }
    }

    private MediaRoute2ProviderInfo.Builder createBuilder() {
        return new MediaRoute2ProviderInfo.Builder();
    }

    private MediaRoute2Info createTestRoute(@NonNull String id, @NonNull String name) {
        return new MediaRoute2Info.Builder(id, name).addFeature("feature").build();
    }
}
