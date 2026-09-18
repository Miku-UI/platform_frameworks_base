/*
 * Copyright 2025 The Android Open Source Project
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

package android.media.projection;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.util.Size;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaProjectionAppContentTest {

    @Test
    public void testConstructorAndGetters() {
        // Create a mock Bitmap
        Bitmap mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        // Create a mock Icon
        Icon mockIcon = Icon.createWithResource("android", android.R.drawable.ic_delete);

        // Create a MediaProjectionAppContent object
        MediaProjectionAppContent content = new MediaProjectionAppContent.Builder(123)
                .setThumbnail(mockBitmap)
                .setTitle("Test Title")
                .setIcon(mockIcon)
                .build();

        // Verify the values using getters
        assertThat(content.getTitle()).isEqualTo("Test Title");
        assertThat(content.getId()).isEqualTo(123);
        assertThat(content.getIcon()).isEqualTo(mockIcon);
        // Compare bitmap configurations and dimensions
        assertThat(content.getThumbnail().getConfig()).isEqualTo(mockBitmap.getConfig());
        assertThat(content.getThumbnail().getWidth()).isEqualTo(mockBitmap.getWidth());
        assertThat(content.getThumbnail().getHeight()).isEqualTo(mockBitmap.getHeight());
    }

    @Test
    public void testParcelable() {
        // Create a mock Bitmap
        Bitmap mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        // Create a mock Icon
        Icon mockIcon = Icon.createWithResource("android", android.R.drawable.ic_delete);

        // Create a MediaProjectionAppContent object
        MediaProjectionAppContent content = new MediaProjectionAppContent.Builder(123)
                .setThumbnail(mockBitmap)
                .setTitle("Test Title")
                .setIcon(mockIcon)
                .build();

        // Parcel and unparcel the object
        Parcel parcel = Parcel.obtain();
        content.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaProjectionAppContent unparceledContent =
                MediaProjectionAppContent.CREATOR.createFromParcel(parcel);

        // Verify the values of the unparceled object
        assertThat(unparceledContent.getTitle()).isEqualTo("Test Title");
        assertThat(unparceledContent.getId()).isEqualTo(123);
        // Icons are not equal after being parceled, so we compare their string representations
        assertThat(unparceledContent.getIcon().toString()).isEqualTo(mockIcon.toString());
        // Compare bitmap configurations and dimensions
        assertThat(unparceledContent.getThumbnail().getConfig()).isEqualTo(
                mockBitmap.getConfig());
        assertThat(unparceledContent.getThumbnail().getWidth()).isEqualTo(mockBitmap.getWidth());
        assertThat(unparceledContent.getThumbnail().getHeight()).isEqualTo(
                mockBitmap.getHeight());

        parcel.recycle();
    }

    @Test
    public void testCreatorNewArray() {
        // Create a new array using the CREATOR
        MediaProjectionAppContent[] contentArray = MediaProjectionAppContent.CREATOR.newArray(5);

        // Verify that the array is not null and has the correct size
        assertThat(contentArray).isNotNull();
        assertThat(contentArray).hasLength(5);
    }

    @Test
    public void testBuilder_withOnlyRequiredFields() {
        // Create a MediaProjectionAppContent object with only the mandatory ID
        MediaProjectionAppContent content = new MediaProjectionAppContent.Builder(456).build();

        // Verify the ID is set correctly
        assertThat(content.getId()).isEqualTo(456);

        // Verify that optional fields are set to their default values
        assertThat(content.getTitle().toString()).isEmpty();
        assertThat(content.getThumbnail()).isNull();
        assertThat(content.getIcon()).isNull();
    }

    /**
     * Verifies that optimizeResources correctly scales down the thumbnail and icon when the
     * target sizes are smaller than the original image dimensions.
     */
    @Test
    public void optimizeResources_scalesDownImages() {
        // Create a large bitmap for thumbnail and icon
        Bitmap largeThumbnail = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Bitmap largeIconBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Icon largeIcon = Icon.createWithBitmap(largeIconBitmap);

        MediaProjectionAppContent content = new MediaProjectionAppContent.Builder(1)
                .setThumbnail(largeThumbnail)
                .setIcon(largeIcon)
                .build();

        // Define smaller target sizes
        Size targetThumbnailSize = new Size(100, 100);
        Size targetIconSize = new Size(50, 50);

        // Optimize resources
        content.optimizeResources(targetThumbnailSize, targetIconSize);

        // Verify thumbnail is scaled down
        Bitmap thumbnail = content.getThumbnail();
        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.getWidth()).isEqualTo(100);
        assertThat(thumbnail.getHeight()).isEqualTo(100);

        // Verify icon is not null after scaling. We cannot inspect its size directly,
        // but we can confirm it wasn't nulled out.
        assertThat(content.getIcon()).isNotNull();
    }

    /**
     * Verifies that optimizeResources does not scale up images if the target sizes are larger
     * than the original image dimensions.
     */
    @Test
    public void optimizeResources_doesNotScaleUpImages() {
        // Create a small bitmap for thumbnail and icon
        Bitmap smallThumbnail = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Bitmap smallIconBitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888);
        Icon smallIcon = Icon.createWithBitmap(smallIconBitmap);

        MediaProjectionAppContent content = new MediaProjectionAppContent.Builder(1)
                .setThumbnail(smallThumbnail)
                .setIcon(smallIcon)
                .build();

        // Define larger target sizes
        Size targetThumbnailSize = new Size(100, 100);
        Size targetIconSize = new Size(50, 50);

        // Optimize resources
        content.optimizeResources(targetThumbnailSize, targetIconSize);

        // Verify thumbnail is NOT scaled up
        Bitmap thumbnail = content.getThumbnail();
        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.getWidth()).isEqualTo(50);
        assertThat(thumbnail.getHeight()).isEqualTo(50);

        // Verify icon is not null and was not scaled up
        assertThat(content.getIcon()).isNotNull();
    }

    /**
     * Verifies that optimizeResources nulls out the thumbnail and icon when provided with
     * zero or negative dimensions for the target sizes.
     */
    @Test
    public void optimizeResources_nullsOutImagesForInvalidSize() {
        // Create a bitmap for thumbnail and icon
        Bitmap thumbnailBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Bitmap iconBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Icon icon = Icon.createWithBitmap(iconBitmap);

        MediaProjectionAppContent content = new MediaProjectionAppContent.Builder(1)
                .setThumbnail(thumbnailBitmap)
                .setIcon(icon)
                .build();

        // Define zero and negative sizes
        Size zeroThumbnailSize = new Size(0, 0);
        Size negativeIconSize = new Size(-1, -1);

        // Optimize resources
        content.optimizeResources(zeroThumbnailSize, negativeIconSize);

        // Verify thumbnail and icon are nulled out
        assertThat(content.getThumbnail()).isNull();
        assertThat(content.getIcon()).isNull();
    }

    /**
     * Verifies that optimizeResources handles cases where the thumbnail and/or icon are
     * already null, ensuring no exceptions are thrown.
     */
    @Test
    public void optimizeResources_handlesNullContent() {
        // Create content without thumbnail or icon
        MediaProjectionAppContent content = new MediaProjectionAppContent.Builder(1).build();

        // Define valid sizes
        Size targetThumbnailSize = new Size(100, 100);
        Size targetIconSize = new Size(50, 50);

        // Optimize resources
        content.optimizeResources(targetThumbnailSize, targetIconSize);

        // Verify thumbnail and icon are still null
        assertThat(content.getThumbnail()).isNull();
        assertThat(content.getIcon()).isNull();
    }
}
