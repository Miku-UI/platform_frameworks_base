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
package android.view.contentcapture;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MyCustomViewWithA11yProvider extends View {
    private MyAccessibilityNodeProvider mProvider;

    public MyCustomViewWithA11yProvider(Context context) {
        super(context);
        init();
    }

    public MyCustomViewWithA11yProvider(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MyCustomViewWithA11yProvider(Context context,
            @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return mProvider;
    }
    private void init() {
        mProvider = new MyAccessibilityNodeProvider(this);
    }

    private static class MyAccessibilityNodeProvider extends AccessibilityNodeProvider {
        private static final int VIRTUAL_CHILD_ID_1 = 1;
        private static final int VIRTUAL_CHILD_ID_2 = 2;
        private static final int VIRTUAL_CHILD_ID_3 = 3;
        private final View mHostView;
        private final String mPackageName;
        MyAccessibilityNodeProvider(View hostView) {
            mHostView = hostView;
            mPackageName = hostView.getContext().getPackageName();
        }

        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            final AccessibilityNodeInfo info;
            if (virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) {
                info = AccessibilityNodeInfo.obtain(mHostView);
                mHostView.onInitializeAccessibilityNodeInfo(info);
                info.setPackageName(mPackageName);
                info.addChild(mHostView, VIRTUAL_CHILD_ID_1);
                info.addChild(mHostView, VIRTUAL_CHILD_ID_2);
                info.addChild(mHostView, VIRTUAL_CHILD_ID_3);
                return info;
            }
            info = createChildrenAccessibilityNodeInfo(virtualViewId, 3);
            return info;
        }

        private AccessibilityNodeInfo createChildrenAccessibilityNodeInfo(
                int virtualViewId, int maxChildCount) {
            if (virtualViewId > maxChildCount) {
                return null;
            }
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(mHostView, virtualViewId);
            info.setPackageName(mPackageName);
            info.setClassName("VirtualChild" + virtualViewId + "Class");
            info.setText("Text" + virtualViewId);
            info.setContentDescription("CD" + virtualViewId);
            info.setClickable(true);
            info.setParent(mHostView);
            info.setVisibleToUser(true);
            info.setBoundsInScreen(new Rect(10 * (virtualViewId - 1), 0, 10, 10));
            return info;
        }
        @Override
        public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(
                String text, int virtualViewId) {
            return new ArrayList<>();
        }
        @Override
        public AccessibilityNodeInfo findFocus(int focus) {
            return null;
        }
        @Override
        public void addExtraDataToAccessibilityNodeInfo(
                int virtualViewId, @NonNull AccessibilityNodeInfo info,
                @NonNull String extraDataKey, @Nullable Bundle arguments) {}
    }
}
