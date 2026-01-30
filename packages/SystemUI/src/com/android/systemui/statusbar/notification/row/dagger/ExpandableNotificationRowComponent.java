/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.row.dagger;

import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.PipelineEntry;
import com.android.systemui.statusbar.notification.row.ActivatableNotificationView;
import com.android.systemui.statusbar.notification.row.BigPictureIconManager;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRowController;

import dagger.BindsInstance;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;


/**
 * Dagger Component for a {@link ExpandableNotificationRow}.
 */
@Subcomponent(modules = {
        ActivatableNotificationViewModule.class,
        ExpandableNotificationRowComponent.ExpandableNotificationRowModule.class,
        RemoteInputViewModule.class
})
@NotificationRowScope
public interface ExpandableNotificationRowComponent {
    /**
     * Builder for {@link NotificationRowComponent}.
     */
    @Subcomponent.Builder
    interface Builder {
        // TODO: NotificationEntry contains a reference to ExpandableNotificationRow, so it
        // should be possible to pull one from the other, but they aren't connected at the time
        // this component is constructed.
        @BindsInstance
        Builder expandableNotificationRow(ExpandableNotificationRow view);
        @BindsInstance
        Builder pipelineEntry(PipelineEntry pipelineEntry);
        @BindsInstance
        Builder onExpandClickListener(ExpandableNotificationRow.OnExpandClickListener presenter);
        ExpandableNotificationRowComponent build();
    }

    /**
     * Creates a ExpandableNotificationRowController.
     */
    @NotificationRowScope
    ExpandableNotificationRowController getExpandableNotificationRowController();

    /**
     * Creates a BigPictureIconManager.
     */
    @NotificationRowScope
    BigPictureIconManager getBigPictureIconManager();

    /**
     * Dagger Module that extracts interesting properties from an ExpandableNotificationRow.
     */
    @Module
    class ExpandableNotificationRowModule {
        /** ExpandableNotificationRow is provided as an instance of ActivatableNotificationView. */
        @Provides
        ActivatableNotificationView bindExpandableView(ExpandableNotificationRow view) {
            return view;
        }

        @Provides
        NotificationEntry provideNotificationEntry(PipelineEntry pipelineEntry) {
            return (NotificationEntry) pipelineEntry;
        }
    }
}
