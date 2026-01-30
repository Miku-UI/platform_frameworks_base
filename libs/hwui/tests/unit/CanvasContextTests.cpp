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
#include "FeatureFlags.h"
#include "IContextFactory.h"
#include "renderthread/CanvasContext.h"
#include "renderthread/VulkanManager.h"
#include "tests/common/TestUtils.h"

using namespace android;
using namespace android::uirenderer;
using namespace android::uirenderer::renderthread;

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
