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

package com.android.server.input.data;

import android.hardware.input.AppLaunchData;
import android.hardware.input.InputGestureData;
import android.util.Slog;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** {@link PersistedData} implementation for InputGestureData */
final class InputGesturePersistedData extends PersistedData<InputGestureData> {

    private static final String TAG = "InputGesturePersistedData";
    private static final String TAG_ROOT = "root";
    private static final String TAG_INPUT_GESTURE_LIST = "input_gesture_list";
    private static final String TAG_INPUT_GESTURE = "input_gesture";
    private static final String TAG_KEY_TRIGGER = "key_trigger";
    private static final String TAG_TOUCHPAD_TRIGGER = "touchpad_trigger";
    private static final String TAG_APP_LAUNCH_DATA = "app_launch_data";

    private static final String ATTR_KEY_TRIGGER_KEYCODE = "keycode";
    private static final String ATTR_KEY_TRIGGER_MODIFIER_STATE = "modifiers";
    private static final String ATTR_KEY_GESTURE_TYPE = "key_gesture_type";
    private static final String ATTR_TOUCHPAD_TRIGGER_GESTURE_TYPE = "touchpad_gesture_type";
    private static final String ATTR_APP_LAUNCH_DATA_CATEGORY = "category";
    private static final String ATTR_APP_LAUNCH_DATA_ROLE = "role";
    private static final String ATTR_APP_LAUNCH_DATA_PACKAGE_NAME = "package_name";
    private static final String ATTR_APP_LAUNCH_DATA_CLASS_NAME = "class_name";

    InputGesturePersistedData() {
        super("input_gestures");
    }

    /**
     * Parses the given input and returns the list of {@link InputGestureData} objects.
     * This parsing happens on a best effort basis. If invalid data exists in the given payload
     * it will be skipped. An example of this would be a keycode that does not exist in the
     * present version of Android.  If the payload is malformed, instead this will throw an
     * exception and require the caller to handel this appropriately for its situation.
     *
     * @param parser XML parser for the input payload of XML data
     * @return list of {@link InputGestureData} objects pulled from the payload
     * @throws XmlPullParserException If there is an issue parsing the XML.
     * @throws IOException            If there is an issue reading from the stream.
     */
    @Override
    public List<InputGestureData> readListFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<InputGestureData> inputGestureDataList = new ArrayList<>();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            final String tag = parser.getName();
            if (TAG_ROOT.equals(tag)) {
                continue;
            }

            if (TAG_INPUT_GESTURE_LIST.equals(tag)) {
                inputGestureDataList.addAll(readInputGestureListFromXml(parser));
            }
        }
        return inputGestureDataList;
    }

    /**
     * Serializes the given list of {@link InputGestureData} objects to XML in the provided output
     * stream.
     *
     * @param serializer           XML serializer to output the data
     * @param inputGestureDataList the list of {@link InputGestureData} objects to serialize.
     */
    @Override
    public void writeListToXml(TypedXmlSerializer serializer,
            List<InputGestureData> inputGestureDataList) throws IOException {
        serializer.startDocument(null, true);
        serializer.startTag(null, TAG_ROOT);
        writeInputGestureListToXml(serializer, inputGestureDataList);
        serializer.endTag(null, TAG_ROOT);
        serializer.endDocument();
    }

    private InputGestureData readInputGestureFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException, IllegalArgumentException {
        InputGestureData.Builder builder = new InputGestureData.Builder();
        builder.setKeyGestureType(parser.getAttributeInt(null, ATTR_KEY_GESTURE_TYPE));
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            // If the parser has left the initial scope when it was called, break out.
            if (outerDepth > parser.getDepth()) {
                throw new RuntimeException(
                        "Parser has left the initial scope of the tag that was being parsed on "
                                + "line number: "
                                + parser.getLineNumber());
            }

            // If the parser has reached the closing tag for the Input Gesture, break out.
            if (type == XmlPullParser.END_TAG && parser.getName().equals(TAG_INPUT_GESTURE)) {
                break;
            }

            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            final String tag = parser.getName();
            if (TAG_KEY_TRIGGER.equals(tag)) {
                builder.setTrigger(InputGestureData.createKeyTrigger(
                        parser.getAttributeInt(null, ATTR_KEY_TRIGGER_KEYCODE),
                        parser.getAttributeInt(null, ATTR_KEY_TRIGGER_MODIFIER_STATE)));
            } else if (TAG_TOUCHPAD_TRIGGER.equals(tag)) {
                builder.setTrigger(InputGestureData.createTouchpadTrigger(
                        parser.getAttributeInt(null, ATTR_TOUCHPAD_TRIGGER_GESTURE_TYPE)));
            } else if (TAG_APP_LAUNCH_DATA.equals(tag)) {
                final String roleValue = parser.getAttributeValue(null, ATTR_APP_LAUNCH_DATA_ROLE);
                final String categoryValue = parser.getAttributeValue(null,
                        ATTR_APP_LAUNCH_DATA_CATEGORY);
                final String classNameValue = parser.getAttributeValue(null,
                        ATTR_APP_LAUNCH_DATA_CLASS_NAME);
                final String packageNameValue = parser.getAttributeValue(null,
                        ATTR_APP_LAUNCH_DATA_PACKAGE_NAME);
                final AppLaunchData appLaunchData = AppLaunchData.createLaunchData(categoryValue,
                        roleValue, packageNameValue, classNameValue);
                if (appLaunchData != null) {
                    builder.setAppLaunchData(appLaunchData);
                }
            }
        }
        return builder.build();
    }

    private List<InputGestureData> readInputGestureListFromXml(TypedXmlPullParser parser) throws
            XmlPullParserException, IOException {
        List<InputGestureData> inputGestureDataList = new ArrayList<>();
        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            // If the parser has left the initial scope when it was called, break out.
            if (outerDepth > parser.getDepth()) {
                throw new RuntimeException(
                        "Parser has left the initial scope of the tag that was being parsed on "
                                + "line number: "
                                + parser.getLineNumber());
            }

            // If the parser has reached the closing tag for the Input Gesture List, break out.
            if (type == XmlPullParser.END_TAG && parser.getName().equals(TAG_INPUT_GESTURE_LIST)) {
                break;
            }

            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            final String tag = parser.getName();
            if (TAG_INPUT_GESTURE.equals(tag)) {
                try {
                    inputGestureDataList.add(readInputGestureFromXml(parser));
                } catch (IllegalArgumentException exception) {
                    Slog.w(TAG, "Invalid parameters for input gesture data: ", exception);
                    continue;
                }
            }
        }
        return inputGestureDataList;
    }

    private void writeInputGestureToXml(TypedXmlSerializer serializer,
            InputGestureData inputGestureData) throws IOException {
        serializer.startTag(null, TAG_INPUT_GESTURE);
        serializer.attributeInt(null, ATTR_KEY_GESTURE_TYPE,
                inputGestureData.getAction().keyGestureType());

        final InputGestureData.Trigger trigger = inputGestureData.getTrigger();
        if (trigger instanceof InputGestureData.KeyTrigger keyTrigger) {
            serializer.startTag(null, TAG_KEY_TRIGGER);
            serializer.attributeInt(null, ATTR_KEY_TRIGGER_KEYCODE, keyTrigger.getKeycode());
            serializer.attributeInt(null, ATTR_KEY_TRIGGER_MODIFIER_STATE,
                    keyTrigger.getModifierState());
            serializer.endTag(null, TAG_KEY_TRIGGER);
        } else if (trigger instanceof InputGestureData.TouchpadTrigger touchpadTrigger) {
            serializer.startTag(null, TAG_TOUCHPAD_TRIGGER);
            serializer.attributeInt(null, ATTR_TOUCHPAD_TRIGGER_GESTURE_TYPE,
                    touchpadTrigger.getTouchpadGestureType());
            serializer.endTag(null, TAG_TOUCHPAD_TRIGGER);
        }

        if (inputGestureData.getAction().appLaunchData() != null) {
            serializer.startTag(null, TAG_APP_LAUNCH_DATA);
            final AppLaunchData appLaunchData = inputGestureData.getAction().appLaunchData();
            if (appLaunchData instanceof AppLaunchData.RoleData roleData) {
                serializer.attribute(null, ATTR_APP_LAUNCH_DATA_ROLE, roleData.getRole());
            } else if (appLaunchData
                    instanceof AppLaunchData.CategoryData categoryData) {
                serializer.attribute(null, ATTR_APP_LAUNCH_DATA_CATEGORY,
                        categoryData.getCategory());
            } else if (appLaunchData instanceof AppLaunchData.ComponentData componentData) {
                serializer.attribute(null, ATTR_APP_LAUNCH_DATA_PACKAGE_NAME,
                        componentData.getPackageName());
                serializer.attribute(null, ATTR_APP_LAUNCH_DATA_CLASS_NAME,
                        componentData.getClassName());
            }
            serializer.endTag(null, TAG_APP_LAUNCH_DATA);
        }

        serializer.endTag(null, TAG_INPUT_GESTURE);
    }

    private void writeInputGestureListToXml(TypedXmlSerializer serializer,
            List<InputGestureData> inputGestureDataList) throws IOException {
        serializer.startTag(null, TAG_INPUT_GESTURE_LIST);
        for (final InputGestureData inputGestureData : inputGestureDataList) {
            writeInputGestureToXml(serializer, inputGestureData);
        }
        serializer.endTag(null, TAG_INPUT_GESTURE_LIST);
    }
}
