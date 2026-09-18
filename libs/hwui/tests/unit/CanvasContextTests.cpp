/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <gtest/gtest.h>

#include "AnimationContext.h"
#include "ColorArea.h"
#include "FeatureFlags.h"
#include "IContextFactory.h"
#include "renderthread/CanvasContext.h"
#include "renderthread/VulkanManager.h"
#include "tests/common/TestUtils.h"
#include "utils/Color.h"

using namespace android;
using namespace android::uirenderer;
using namespace android::uirenderer::renderthread;

// Helper to define colors for clarity
const uirenderer::Lab COLOR_LIGHT_1 = {100, 10, 10};
const uirenderer::Lab COLOR_LIGHT_2 = {100, 20, 20};
const uirenderer::Lab COLOR_DARK_1 = {0, 10, 10};
const uirenderer::Lab COLOR_DARK_2 = {0, 20, 20};

class ContextFactory : public IContextFactory {
public:
    virtual AnimationContext* createAnimationContext(renderthread::TimeLord& clock) override {
        return new AnimationContext(clock);
    }
};

RENDERTHREAD_TEST(CanvasContext, create) {
    auto rootNode = TestUtils::createNode(0, 0, 200, 400, nullptr);
    ContextFactory contextFactory;
    std::unique_ptr<CanvasContext> canvasContext(
            CanvasContext::create(renderThread, false, rootNode.get(), &contextFactory, 0, 0));

    ASSERT_FALSE(canvasContext->hasOutputTarget());

    canvasContext->destroy();
}

RENDERTHREAD_TEST(CanvasContext, buildLayerDoesntLeak) {
    auto node = TestUtils::createNode(0, 0, 200, 400, [](RenderProperties& props, Canvas& canvas) {
        canvas.drawColor(0xFFFF0000, SkBlendMode::kSrc);
    });
    ASSERT_TRUE(node->isValid());
    EXPECT_EQ(LayerType::None, node->stagingProperties().effectiveLayerType());
    node->mutateStagingProperties().mutateLayerProperties().setType(LayerType::RenderLayer);

    auto& cacheManager = renderThread.cacheManager();
    EXPECT_TRUE(cacheManager.areAllContextsStopped());
    ContextFactory contextFactory;
    std::unique_ptr<CanvasContext> canvasContext(
            CanvasContext::create(renderThread, false, node.get(), &contextFactory, 0, 0));
    canvasContext->buildLayer(node.get());
    EXPECT_TRUE(node->hasLayer());
    if (Properties::getRenderPipelineType() == RenderPipelineType::SkiaVulkan) {
        auto instance = VulkanManager::peekInstance();
        if (instance) {
            EXPECT_TRUE(instance->hasVkContext());
        } else {
            ADD_FAILURE() << "VulkanManager wasn't initialized to buildLayer?";
        }
    }
    renderThread.destroyRenderingContext();
    EXPECT_FALSE(node->hasLayer()) << "Node still has a layer after rendering context destroyed";

    if (Properties::getRenderPipelineType() == RenderPipelineType::SkiaVulkan) {
        auto instance = VulkanManager::peekInstance();
        if (instance) {
            ADD_FAILURE() << "VulkanManager still exists";
            EXPECT_FALSE(instance->hasVkContext());
        }
    }
}

RENDERTHREAD_TEST(CanvasContext, forceInvertColorArea_detectsLightTheme) {
    if (!view_accessibility_flags::force_invert_color()) {
        GTEST_SKIP() << "Test only applies when force_invert_colorarea_detector flag is enabled";
    }
    Properties::setIsForceInvertEnabled(true);
    auto buttonNode =
            TestUtils::createNode(0, 0, 50, 100, [](RenderProperties& props, Canvas& canvas) {
                Paint paint;
                paint.setStyle(SkPaint::Style::kFill_Style);
                paint.setColor(0xFFEE11CC);

                canvas.drawRoundRect(0, 0, 50, 100, 5, 5, paint);
            });
    auto panelNode = TestUtils::createNode(0, 0, 100, 200,
                                           [buttonNode](RenderProperties& props, Canvas& canvas) {
                                               Paint paint;
                                               paint.setStyle(SkPaint::Style::kFill_Style);
                                               paint.setColor(0xEE111111);

                                               canvas.drawRect(0, 0, 100, 200, paint);
                                               canvas.drawRenderNode(buttonNode.get());
                                           });

    auto node = TestUtils::createNode(0, 0, 200, 400,
                                      [panelNode](RenderProperties& props, Canvas& canvas) {
                                          canvas.drawColor(0xFFEEEEE1, SkBlendMode::kSrc);

                                          canvas.drawRenderNode(panelNode.get());
                                      });
    node->mutateStagingProperties().mutateLayerProperties().setType(LayerType::RenderLayer);

    auto& cacheManager = renderThread.cacheManager();
    ContextFactory contextFactory;
    std::unique_ptr<CanvasContext> canvasContext(
            CanvasContext::create(renderThread, false, node.get(), &contextFactory, 0, 0));
    canvasContext->setForceDark(android::uirenderer::ForceDarkType::FORCE_INVERT_COLOR_DARK);

    EXPECT_EQ(canvasContext->getColorArea().getPolarity(), Unknown);

    canvasContext->prepareAndDraw(node.get());

    EXPECT_EQ(canvasContext->getColorArea().getPolarity(), Light);

    Properties::setIsForceInvertEnabled(false);
    renderThread.destroyRenderingContext();
}

RENDERTHREAD_TEST(CanvasContext, forceInvertColorArea_detectsDarkTheme) {
    if (!view_accessibility_flags::force_invert_color()) {
        GTEST_SKIP() << "Test only applies when force_invert_colorarea_detector flag is enabled";
    }
    Properties::setIsForceInvertEnabled(true);
    auto buttonNode =
            TestUtils::createNode(0, 0, 50, 100, [](RenderProperties& props, Canvas& canvas) {
                Paint paint;
                paint.setStyle(SkPaint::Style::kFill_Style);
                paint.setColor(0xFFFF5566);

                canvas.drawRoundRect(0, 0, 50, 100, 5, 5, paint);
            });
    auto panelNode = TestUtils::createNode(0, 0, 100, 200,
                                           [buttonNode](RenderProperties& props, Canvas& canvas) {
                                               Paint paint;
                                               paint.setStyle(SkPaint::Style::kFill_Style);
                                               paint.setColor(0xFFCCCCCC);

                                               canvas.drawRect(0, 0, 100, 200, paint);
                                               canvas.drawRenderNode(buttonNode.get());
                                           });

    auto node = TestUtils::createNode(0, 0, 200, 400,
                                      [panelNode](RenderProperties& props, Canvas& canvas) {
                                          canvas.drawColor(0xFF030102, SkBlendMode::kSrc);

                                          canvas.drawRenderNode(panelNode.get());
                                      });
    node->mutateStagingProperties().mutateLayerProperties().setType(LayerType::RenderLayer);

    auto& cacheManager = renderThread.cacheManager();
    ContextFactory contextFactory;
    std::unique_ptr<CanvasContext> canvasContext(
            CanvasContext::create(renderThread, false, node.get(), &contextFactory, 0, 0));
    canvasContext->setForceDark(android::uirenderer::ForceDarkType::FORCE_INVERT_COLOR_DARK);

    EXPECT_EQ(canvasContext->getColorArea().getPolarity(), Unknown);

    canvasContext->prepareAndDraw(node.get());

    EXPECT_EQ(canvasContext->getColorArea().getPolarity(), Dark);

    Properties::setIsForceInvertEnabled(false);
    renderThread.destroyRenderingContext();
}

TEST(CanvasContext, computeGradient_allLight_detectsLight) {
    // Average L = 100
    SkAndroidFrameworkUtils::LinearGradientInfo info;
    SkColor4f colors[] = {LabToSRGB(COLOR_LIGHT_1, /* alpha= */ 1),
                          LabToSRGB(COLOR_LIGHT_2, /* alpha= */ 1)};
    float offsets[] = {0.0f, 1.0f};

    info.fColorCount = 2;
    info.fColors = colors;
    info.fColorOffsets = offsets;

    EXPECT_EQ(ColorArea::GradientLightness::Light, ColorArea::computeGradient(info));
}

TEST(CanvasContext, computeGradient_allDark_detectsDark) {
    // Average L = 0
    SkAndroidFrameworkUtils::LinearGradientInfo info;
    SkColor4f colors[] = {LabToSRGB(COLOR_DARK_1, /* alpha= */ 1),
                          LabToSRGB(COLOR_DARK_2, /* alpha= */ 1)};
    float offsets[] = {0.0f, 1.0f};

    info.fColorCount = 2;
    info.fColors = colors;
    info.fColorOffsets = offsets;

    EXPECT_EQ(ColorArea::GradientLightness::Dark, ColorArea::computeGradient(info));
}

TEST(CanvasContext, computeGradient_respectsOffsets_DominantlyDark) {
    // Gradient is Light -> Dark, but Light is only the first 10%.
    // 0.0 to 0.1: Light to Dark (Avg L=50, Area=0.1) -> Contribution: 5
    // 0.1 to 1.0: Dark to Dark (Avg L=0, Area=0.9)  -> Contribution: 0
    // Total Avg L: 5. This should be Dark.
    SkAndroidFrameworkUtils::LinearGradientInfo info;
    SkColor4f colors[] = {LabToSRGB(COLOR_LIGHT_1, /* alpha= */ 1),
                          LabToSRGB(COLOR_DARK_1, /* alpha= */ 1),
                          LabToSRGB(COLOR_DARK_2, /* alpha= */ 1)};
    float offsets[] = {0.0f, 0.1f, 1.0f};

    info.fColorCount = 3;
    info.fColors = colors;
    info.fColorOffsets = offsets;

    EXPECT_EQ(ColorArea::GradientLightness::Dark, ColorArea::computeGradient(info));
}

TEST(CanvasContext, computeGradient_respectsOffsets_DominantlyLight) {
    // Gradient is Dark -> Light, but Light is 90% of the area.
    // 0.0 to 0.1: Dark to Light (Avg L=50, Area=0.1) -> Contribution: 5
    // 0.1 to 1.0: Light to Light (Avg L=100, Area=0.9) -> Contribution: 90
    // Total Avg L: 95. This should be Light.
    SkAndroidFrameworkUtils::LinearGradientInfo info;
    SkColor4f colors[] = {LabToSRGB(COLOR_DARK_1, /* alpha= */ 1),
                          LabToSRGB(COLOR_LIGHT_1, /* alpha= */ 1),
                          LabToSRGB(COLOR_LIGHT_2, /* alpha= */ 1)};
    float offsets[] = {0.0f, 0.1f, 1.0f};

    info.fColorCount = 3;
    info.fColors = colors;
    info.fColorOffsets = offsets;

    EXPECT_EQ(ColorArea::GradientLightness::Light, ColorArea::computeGradient(info));
}
