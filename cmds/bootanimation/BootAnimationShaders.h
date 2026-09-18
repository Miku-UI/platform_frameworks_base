/*
 * Copyright (C) 2026 The Android Open Source Project
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

#ifndef ANDROID_BOOTANIMATION_SHADERS_H
#define ANDROID_BOOTANIMATION_SHADERS_H

static const char VERTEX_SHADER_SOURCE[] = R"(
    precision mediump float;
    attribute vec4 aPosition;
    attribute highp vec2 aUv;
    varying highp vec2 vUv;
    void main() {
        gl_Position = aPosition;
        vUv = aUv;
    })";
static const char IMAGE_FRAG_DYNAMIC_COLORING_SHADER_SOURCE[] = R"(
    precision mediump float;
    const float cWhiteMaskThreshold = 0.05;
    uniform sampler2D uTexture;
    uniform float uFade;
    uniform float uColorProgress;
    uniform vec3 uStartColor0;
    uniform vec3 uStartColor1;
    uniform vec3 uStartColor2;
    uniform vec3 uStartColor3;
    uniform vec3 uEndColor0;
    uniform vec3 uEndColor1;
    uniform vec3 uEndColor2;
    uniform vec3 uEndColor3;
    varying highp vec2 vUv;
    void main() {
        vec4 mask = texture2D(uTexture, vUv);
        float r = mask.r;
        float g = mask.g;
        float b = mask.b;
        float a = mask.a;
        // If all channels have values, render pixel as a shade of white.
        float useWhiteMask = step(cWhiteMaskThreshold, r)
            * step(cWhiteMaskThreshold, g)
            * step(cWhiteMaskThreshold, b)
            * step(cWhiteMaskThreshold, a);
        vec3 color = r * mix(uStartColor0, uEndColor0, uColorProgress)
                + g * mix(uStartColor1, uEndColor1, uColorProgress)
                + b * mix(uStartColor2, uEndColor2, uColorProgress)
                + a * mix(uStartColor3, uEndColor3, uColorProgress);
        color = mix(color, vec3((r + g + b + a) * 0.25), useWhiteMask);
        gl_FragColor = vec4(color.x, color.y, color.z, (1.0 - uFade));
    })";
static const char IMAGE_FRAG_SHADER_SOURCE[] = R"(
    precision mediump float;
    uniform sampler2D uTexture;
    uniform float uFade;
    varying highp vec2 vUv;
    void main() {
        vec4 color = texture2D(uTexture, vUv);
        gl_FragColor = vec4(color.x, color.y, color.z, (1.0 - uFade)) * color.a;
    })";
static const char TEXT_FRAG_SHADER_SOURCE[] = R"(
    precision mediump float;
    uniform sampler2D uTexture;
    uniform vec4 uCropArea;
    varying highp vec2 vUv;
    void main() {
        vec2 uv = vec2(mix(uCropArea.x, uCropArea.z, vUv.x),
                       mix(uCropArea.y, uCropArea.w, vUv.y));
        gl_FragColor = texture2D(uTexture, uv);
    })";

#endif // ANDROID_BOOTANIMATION_SHADERS_H
