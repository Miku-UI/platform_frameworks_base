/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.media.projection.MediaProjectionAppContent;
import android.media.projection.IAppContentProjectionSession;
import android.os.RemoteCallback;

/**
* @hide
*/
oneway interface IAppContentProjectionCallback {
     /**
        * Called when the picker UI has been shown to the user.
        *
        * @param newContentConsumer Consumer to update the list of displayed content
        *                           if needed.
        * @param thumbnailWidth The requested width of the app content thumbnail
        * @param thumbnailHeight The requested height of the app content thumbnail
        */
       @EnforcePermission(allOf = {"MANAGE_MEDIA_PROJECTION"})
       void onContentRequest(in RemoteCallback newContentConsumer, int thumbnailWidth, int thumbnailHeight);

       /**
        * Called when the user picked a content to be shared within the requesting app.
        * This can be called multiple time if the user picked a different content to
        * be shared
        *
        * @return true if the request has been fulfilled, false otherwise.
        */
       @EnforcePermission(allOf = {"MANAGE_MEDIA_PROJECTION"})
       void onLoopbackProjectionStarted(in IAppContentProjectionSession session, int contentId);

      /**
       * Called when the sharing session has been ended by the user or the system. The shared
       * resources can be discarded.
       */
       @EnforcePermission(allOf = {"MANAGE_MEDIA_PROJECTION"})
       void onSessionStopped();

      /**
       * Called when the user didn't pick some app content to be shared. This can happen if the
       * projection request was canceled, of the user picked another source (e.g. display, whole app).
       * <p>
       * Any resources created for sharing app content, such as thumbnail, can be discarded at this
       * point.
       */
       @EnforcePermission(allOf = {"MANAGE_MEDIA_PROJECTION"})
       void onContentRequestCanceled();
}
