/*
 * Copyright 2024 The Android Open Source Project
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

#define LOG_TAG "DisplayTopology-JNI"

#include <android_hardware_display_DisplayTopology.h>
#include <nativehelper/ScopedLocalRef.h>
#include <utils/Errors.h>

#include "jni_wrappers.h"

namespace android {

// ----------------------------------------------------------------------------

static struct {
    jclass clazz;
    jfieldID primaryDisplayId;
    jfieldID displayNodes;
} gDisplayTopologyGraphClassInfo;

static struct {
    jclass clazz;
    jfieldID displayId;
    jfieldID density;
    jfieldID boundsInGlobalDp;
    jfieldID adjacentEdges;
} gDisplayTopologyGraphNodeClassInfo;

static struct {
    jclass clazz;
    jfieldID displayNode;
    jfieldID position;
    jfieldID offsetDp;
} gDisplayTopologyGraphAdjacentDisplayClassInfo;

static struct {
    jclass clazz;
    jfieldID left;
    jfieldID top;
    jfieldID right;
    jfieldID bottom;
} gDisplayTopologyGraphDisplayBoundsClassInfo;

// ----------------------------------------------------------------------------

status_t android_hardware_display_DisplayTopologyDisplayBounds_toNative(JNIEnv* env,
                                                                        jobject displayBoundsObj,
                                                                        FloatRect* displayBounds) {
    displayBounds->left =
            env->GetFloatField(displayBoundsObj, gDisplayTopologyGraphDisplayBoundsClassInfo.left);
    displayBounds->top =
            env->GetFloatField(displayBoundsObj, gDisplayTopologyGraphDisplayBoundsClassInfo.top);
    displayBounds->right =
            env->GetFloatField(displayBoundsObj, gDisplayTopologyGraphDisplayBoundsClassInfo.right);
    displayBounds->bottom = env->GetFloatField(displayBoundsObj,
                                               gDisplayTopologyGraphDisplayBoundsClassInfo.bottom);
    return OK;
}

status_t android_hardware_display_DisplayTopologyAdjacentDisplay_toNative(
        JNIEnv* env, jobject adjacentDisplayObj, DisplayTopologyAdjacentDisplay* adjacentDisplay) {
    ScopedLocalRef<jobject>
            displayNodeObj(env,
                           env->GetObjectField(adjacentDisplayObj,
                                               gDisplayTopologyGraphAdjacentDisplayClassInfo
                                                       .displayNode));
    adjacentDisplay->displayId = ui::LogicalDisplayId{
            env->GetIntField(displayNodeObj.get(), gDisplayTopologyGraphNodeClassInfo.displayId)};
    adjacentDisplay->position = static_cast<DisplayTopologyPosition>(
            env->GetIntField(adjacentDisplayObj,
                             gDisplayTopologyGraphAdjacentDisplayClassInfo.position));
    adjacentDisplay->offsetDp =
            env->GetFloatField(adjacentDisplayObj,
                               gDisplayTopologyGraphAdjacentDisplayClassInfo.offsetDp);
    return OK;
}

status_t android_hardware_display_DisplayTopologyGraphNode_toNative(
        JNIEnv* env, jobject nodeObj,
        std::unordered_map<ui::LogicalDisplayId, DisplayTopologyGraph::Properties>& topologyGraph) {
    ui::LogicalDisplayId displayId = ui::LogicalDisplayId{
            env->GetIntField(nodeObj, gDisplayTopologyGraphNodeClassInfo.displayId)};

    topologyGraph[displayId].density =
            env->GetIntField(nodeObj, gDisplayTopologyGraphNodeClassInfo.density);

    ScopedLocalRef<jobject> displayBounds(env,
                                          env->GetObjectField(nodeObj,
                                                              gDisplayTopologyGraphNodeClassInfo
                                                                      .boundsInGlobalDp));
    android_hardware_display_DisplayTopologyDisplayBounds_toNative(env, displayBounds.get(),
                                                                   &topologyGraph[displayId]
                                                                            .boundsInGlobalDp);

    jobjectArray adjacentEdgesArray = static_cast<jobjectArray>(
            env->GetObjectField(nodeObj, gDisplayTopologyGraphNodeClassInfo.adjacentEdges));

    if (adjacentEdgesArray) {
        jsize length = env->GetArrayLength(adjacentEdgesArray);
        for (jsize i = 0; i < length; i++) {
            ScopedLocalRef<jobject>
                    adjacentDisplayObj(env, env->GetObjectArrayElement(adjacentEdgesArray, i));
            if (NULL == adjacentDisplayObj.get()) {
                break; // found null element indicating end of used portion of the array
            }

            DisplayTopologyAdjacentDisplay adjacentDisplay;
            android_hardware_display_DisplayTopologyAdjacentDisplay_toNative(env,
                                                                             adjacentDisplayObj
                                                                                     .get(),
                                                                             &adjacentDisplay);
            topologyGraph[displayId].adjacentDisplays.push_back(adjacentDisplay);
        }
    }
    return OK;
}

base::Result<const DisplayTopologyGraph> android_hardware_display_DisplayTopologyGraph_toNative(
        JNIEnv* env, jobject topologyObj) {
    std::unordered_map<ui::LogicalDisplayId, DisplayTopologyGraph::Properties> topologyGraph;
    ui::LogicalDisplayId primaryDisplayId = ui::LogicalDisplayId{
            env->GetIntField(topologyObj, gDisplayTopologyGraphClassInfo.primaryDisplayId)};

    jobjectArray nodesArray = static_cast<jobjectArray>(
            env->GetObjectField(topologyObj, gDisplayTopologyGraphClassInfo.displayNodes));

    if (nodesArray) {
        jsize length = env->GetArrayLength(nodesArray);
        for (jsize i = 0; i < length; i++) {
            ScopedLocalRef<jobject> nodeObj(env, env->GetObjectArrayElement(nodesArray, i));
            if (NULL == nodeObj.get()) {
                break; // found null element indicating end of used portion of the array
            }

            android_hardware_display_DisplayTopologyGraphNode_toNative(env, nodeObj.get(),
                                                                       /*byRef*/ topologyGraph);
        }
    }
    return DisplayTopologyGraph::create(primaryDisplayId, std::move(topologyGraph));
}

// ----------------------------------------------------------------------------

int register_android_hardware_display_DisplayTopology(JNIEnv* env) {
    jclass graphClazz = FindClassOrDie(env, "android/hardware/display/DisplayTopologyGraph");
    gDisplayTopologyGraphClassInfo.clazz = MakeGlobalRefOrDie(env, graphClazz);

    gDisplayTopologyGraphClassInfo.primaryDisplayId =
            GetFieldIDOrDie(env, gDisplayTopologyGraphClassInfo.clazz, "mPrimaryDisplayId", "I");
    gDisplayTopologyGraphClassInfo.displayNodes =
            GetFieldIDOrDie(env, gDisplayTopologyGraphClassInfo.clazz, "mDisplayNodes",
                            "[Landroid/hardware/display/DisplayTopologyGraph$DisplayNode;");

    jclass displayNodeClazz =
            FindClassOrDie(env, "android/hardware/display/DisplayTopologyGraph$DisplayNode");
    gDisplayTopologyGraphNodeClassInfo.clazz = MakeGlobalRefOrDie(env, displayNodeClazz);
    gDisplayTopologyGraphNodeClassInfo.displayId =
            GetFieldIDOrDie(env, gDisplayTopologyGraphNodeClassInfo.clazz, "mDisplayId", "I");
    gDisplayTopologyGraphNodeClassInfo.density =
            GetFieldIDOrDie(env, gDisplayTopologyGraphNodeClassInfo.clazz, "mDensity", "I");
    gDisplayTopologyGraphNodeClassInfo.boundsInGlobalDp =
            GetFieldIDOrDie(env, gDisplayTopologyGraphNodeClassInfo.clazz, "mBoundsInGlobalDp",
                            "Landroid/graphics/RectF;");
    gDisplayTopologyGraphNodeClassInfo.adjacentEdges =
            GetFieldIDOrDie(env, gDisplayTopologyGraphNodeClassInfo.clazz, "mAdjacentEdges",
                            "[Landroid/hardware/display/DisplayTopologyGraph$AdjacentEdge;");

    jclass adjacentDisplayClazz =
            FindClassOrDie(env, "android/hardware/display/DisplayTopologyGraph$AdjacentEdge");
    gDisplayTopologyGraphAdjacentDisplayClassInfo.clazz =
            MakeGlobalRefOrDie(env, adjacentDisplayClazz);
    gDisplayTopologyGraphAdjacentDisplayClassInfo.displayNode =
            GetFieldIDOrDie(env, gDisplayTopologyGraphAdjacentDisplayClassInfo.clazz,
                            "mDisplayNode",
                            "Landroid/hardware/display/DisplayTopologyGraph$DisplayNode;");
    gDisplayTopologyGraphAdjacentDisplayClassInfo.position =
            GetFieldIDOrDie(env, gDisplayTopologyGraphAdjacentDisplayClassInfo.clazz, "mPosition",
                            "I");
    gDisplayTopologyGraphAdjacentDisplayClassInfo.offsetDp =
            GetFieldIDOrDie(env, gDisplayTopologyGraphAdjacentDisplayClassInfo.clazz, "mOffsetDp",
                            "F");

    jclass displayBoundsClazz = FindClassOrDie(env, "android/graphics/RectF");
    gDisplayTopologyGraphDisplayBoundsClassInfo.clazz = MakeGlobalRefOrDie(env, displayBoundsClazz);
    gDisplayTopologyGraphDisplayBoundsClassInfo.left =
            GetFieldIDOrDie(env, gDisplayTopologyGraphDisplayBoundsClassInfo.clazz, "left", "F");
    gDisplayTopologyGraphDisplayBoundsClassInfo.top =
            GetFieldIDOrDie(env, gDisplayTopologyGraphDisplayBoundsClassInfo.clazz, "top", "F");
    gDisplayTopologyGraphDisplayBoundsClassInfo.right =
            GetFieldIDOrDie(env, gDisplayTopologyGraphDisplayBoundsClassInfo.clazz, "right", "F");
    gDisplayTopologyGraphDisplayBoundsClassInfo.bottom =
            GetFieldIDOrDie(env, gDisplayTopologyGraphDisplayBoundsClassInfo.clazz, "bottom", "F");
    return 0;
}

} // namespace android
