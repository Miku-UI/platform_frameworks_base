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

#include <vector>

#include "androidfw/ConfigDescription.h"
#include "androidfw/ResourceTypes.h"

#include "utils/Log.h"
#include "utils/String8.h"
#include "utils/Vector.h"

#include "TestHelpers.h"
#include "gtest/gtest.h"

using ::android::ConfigDescription;

namespace android {

static ResTable_config selectBest(const ResTable_config& target,
                                  const std::vector<ResTable_config>& configs) {
  ResTable_config bestConfig;
  memset(&bestConfig, 0, sizeof(bestConfig));
  for (const auto& thisConfig : configs) {
    if (!thisConfig.match(target)) {
      continue;
    }

    if (thisConfig.isBetterThan(bestConfig, &target)) {
      bestConfig = thisConfig;
    }
  }
  return bestConfig;
}

static ResTable_config buildDensityConfig(int density) {
  ResTable_config config;
  memset(&config, 0, sizeof(config));
  config.density = uint16_t(density);
  config.sdkVersion = 4;
  return config;
}

TEST(ConfigTest, shouldSelectBestDensity) {
  ResTable_config deviceConfig;
  memset(&deviceConfig, 0, sizeof(deviceConfig));
  deviceConfig.density = ResTable_config::DENSITY_XHIGH;
  deviceConfig.sdkVersion = 21;

  std::vector<ResTable_config> configs;

  ResTable_config expectedBest =
      buildDensityConfig(ResTable_config::DENSITY_HIGH);
  configs.push_back(expectedBest);
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));

  expectedBest = buildDensityConfig(ResTable_config::DENSITY_XXHIGH);
  configs.push_back(expectedBest);
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));

  expectedBest = buildDensityConfig(int(ResTable_config::DENSITY_XXHIGH) - 20);
  configs.push_back(expectedBest);
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));

  configs.push_back(buildDensityConfig(int(ResTable_config::DENSITY_HIGH) + 20));
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));

  configs.push_back(buildDensityConfig(int(ResTable_config::DENSITY_XHIGH) - 1));
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));

  expectedBest = buildDensityConfig(ResTable_config::DENSITY_XHIGH);
  configs.push_back(expectedBest);
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));

  expectedBest = buildDensityConfig(ResTable_config::DENSITY_ANY);
  expectedBest.sdkVersion = 21;
  configs.push_back(expectedBest);
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));
}

TEST(ConfigTest, shouldSelectBestDensityWhenNoneSpecified) {
  ResTable_config deviceConfig;
  memset(&deviceConfig, 0, sizeof(deviceConfig));
  deviceConfig.sdkVersion = 21;

  std::vector<ResTable_config> configs;
  configs.push_back(buildDensityConfig(ResTable_config::DENSITY_HIGH));

  ResTable_config expectedBest =
      buildDensityConfig(ResTable_config::DENSITY_MEDIUM);
  configs.push_back(expectedBest);
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));

  expectedBest = buildDensityConfig(ResTable_config::DENSITY_ANY);
  configs.push_back(expectedBest);
  ASSERT_EQ(expectedBest, selectBest(deviceConfig, configs));
}

TEST(ConfigTest, shouldMatchRoundQualifier) {
  ResTable_config deviceConfig;
  memset(&deviceConfig, 0, sizeof(deviceConfig));

  ResTable_config roundConfig;
  memset(&roundConfig, 0, sizeof(roundConfig));
  roundConfig.screenLayout2 = ResTable_config::SCREENROUND_YES;

  EXPECT_FALSE(roundConfig.match(deviceConfig));

  deviceConfig.screenLayout2 = ResTable_config::SCREENROUND_YES;

  EXPECT_TRUE(roundConfig.match(deviceConfig));

  deviceConfig.screenLayout2 = ResTable_config::SCREENROUND_NO;

  EXPECT_FALSE(roundConfig.match(deviceConfig));

  ResTable_config notRoundConfig;
  memset(&notRoundConfig, 0, sizeof(notRoundConfig));
  notRoundConfig.screenLayout2 = ResTable_config::SCREENROUND_NO;

  EXPECT_TRUE(notRoundConfig.match(deviceConfig));
}

TEST(ConfigTest, RoundQualifierShouldHaveStableSortOrder) {
  ResTable_config defaultConfig;
  memset(&defaultConfig, 0, sizeof(defaultConfig));

  ResTable_config longConfig = defaultConfig;
  longConfig.screenLayout = ResTable_config::SCREENLONG_YES;

  ResTable_config longRoundConfig = longConfig;
  longRoundConfig.screenLayout2 = ResTable_config::SCREENROUND_YES;

  ResTable_config longRoundPortConfig = longConfig;
  longRoundPortConfig.orientation = ResTable_config::ORIENTATION_PORT;

  EXPECT_TRUE(longConfig.compare(longRoundConfig) < 0);
  EXPECT_TRUE(longConfig.compareLogical(longRoundConfig) < 0);
  EXPECT_TRUE(longRoundConfig.compare(longConfig) > 0);
  EXPECT_TRUE(longRoundConfig.compareLogical(longConfig) > 0);

  EXPECT_TRUE(longRoundConfig.compare(longRoundPortConfig) < 0);
  EXPECT_TRUE(longRoundConfig.compareLogical(longRoundPortConfig) < 0);
  EXPECT_TRUE(longRoundPortConfig.compare(longRoundConfig) > 0);
  EXPECT_TRUE(longRoundPortConfig.compareLogical(longRoundConfig) > 0);
}

TEST(ConfigTest, ScreenShapeHasCorrectDiff) {
  ResTable_config defaultConfig;
  memset(&defaultConfig, 0, sizeof(defaultConfig));

  ResTable_config roundConfig = defaultConfig;
  roundConfig.screenLayout2 = ResTable_config::SCREENROUND_YES;

  EXPECT_EQ(defaultConfig.diff(roundConfig),
            ResTable_config::CONFIG_SCREEN_ROUND);
}

TEST(ConfigTest, RoundIsMoreSpecific) {
  ResTable_config deviceConfig;
  memset(&deviceConfig, 0, sizeof(deviceConfig));
  deviceConfig.screenLayout2 = ResTable_config::SCREENROUND_YES;
  deviceConfig.screenLayout = ResTable_config::SCREENLONG_YES;

  ResTable_config targetConfigA;
  memset(&targetConfigA, 0, sizeof(targetConfigA));

  ResTable_config targetConfigB = targetConfigA;
  targetConfigB.screenLayout = ResTable_config::SCREENLONG_YES;

  ResTable_config targetConfigC = targetConfigB;
  targetConfigC.screenLayout2 = ResTable_config::SCREENROUND_YES;

  EXPECT_TRUE(targetConfigB.isBetterThan(targetConfigA, &deviceConfig));
  EXPECT_TRUE(targetConfigC.isBetterThan(targetConfigB, &deviceConfig));
}

TEST(ConfigTest, ScreenIsWideGamut) {
  ResTable_config defaultConfig;
  memset(&defaultConfig, 0, sizeof(defaultConfig));

  ResTable_config wideGamutConfig = defaultConfig;
  wideGamutConfig.colorMode = ResTable_config::WIDE_COLOR_GAMUT_YES;

  EXPECT_EQ(defaultConfig.diff(wideGamutConfig), ResTable_config::CONFIG_COLOR_MODE);
}

TEST(ConfigTest, ScreenIsHdr) {
  ResTable_config defaultConfig;
  memset(&defaultConfig, 0, sizeof(defaultConfig));

  ResTable_config hdrConfig = defaultConfig;
  hdrConfig.colorMode = ResTable_config::HDR_YES;

  EXPECT_EQ(defaultConfig.diff(hdrConfig), ResTable_config::CONFIG_COLOR_MODE);
}

TEST(ConfigTest, GrammaticalGender) {
  ResTable_config defaultConfig = {};
  ResTable_config masculine = {};
  masculine.grammaticalInflection = ResTable_config::GRAMMATICAL_GENDER_MASCULINE;

  EXPECT_EQ(defaultConfig.diff(masculine), ResTable_config::CONFIG_GRAMMATICAL_GENDER);

  ResTable_config feminine = {};
  feminine.grammaticalInflection = ResTable_config::GRAMMATICAL_GENDER_FEMININE;

  EXPECT_EQ(defaultConfig.diff(feminine), ResTable_config::CONFIG_GRAMMATICAL_GENDER);
  EXPECT_EQ(masculine.diff(feminine), ResTable_config::CONFIG_GRAMMATICAL_GENDER);
}

static ResTable_config Cfg(StringPiece str) {
  ConfigDescription config = {};
  // We're assuming str will successfully parse, to simplify writing tests
  ConfigDescription::Parse(str, &config);
  return config;
}

TEST(ConfigTest, SdkAndMinorVersion_Match) {
  // Left is resource version, right is platform version
  EXPECT_TRUE(Cfg("").match(Cfg("v41")));
  EXPECT_TRUE(Cfg("").match(Cfg("v41.1")));

  EXPECT_TRUE(Cfg("v41").match(Cfg("v41")));
  EXPECT_TRUE(Cfg("v41").match(Cfg("v41.1")));
  EXPECT_TRUE(Cfg("v41").match(Cfg("v41.2")));
  EXPECT_TRUE(Cfg("v41").match(Cfg("v42")));
  EXPECT_TRUE(Cfg("v41").match(Cfg("v42.1")));

  EXPECT_FALSE(Cfg("v41.1").match(Cfg("v41")));
  EXPECT_TRUE(Cfg("v41.1").match(Cfg("v41.1")));
  EXPECT_TRUE(Cfg("v41.1").match(Cfg("v41.2")));
  EXPECT_TRUE(Cfg("v41.1").match(Cfg("v42")));
  EXPECT_TRUE(Cfg("v41.1").match(Cfg("v42.1")));

  EXPECT_FALSE(Cfg("v41.2").match(Cfg("v41")));
  EXPECT_FALSE(Cfg("v41.2").match(Cfg("v41.1")));
  EXPECT_TRUE(Cfg("v41.2").match(Cfg("v41.2")));
  EXPECT_TRUE(Cfg("v41.2").match(Cfg("v42")));
  EXPECT_TRUE(Cfg("v41.2").match(Cfg("v42.1")));

  EXPECT_FALSE(Cfg("v42").match(Cfg("v41")));
  EXPECT_FALSE(Cfg("v42").match(Cfg("v41.1")));
  EXPECT_FALSE(Cfg("v42").match(Cfg("v41.2")));
  EXPECT_TRUE(Cfg("v42").match(Cfg("v42")));
  EXPECT_TRUE(Cfg("v42").match(Cfg("v42.1")));

  EXPECT_FALSE(Cfg("v42.1").match(Cfg("v41")));
  EXPECT_FALSE(Cfg("v42.1").match(Cfg("v41.1")));
  EXPECT_FALSE(Cfg("v42.1").match(Cfg("v41.2")));
  EXPECT_FALSE(Cfg("v42.1").match(Cfg("v42")));
  EXPECT_TRUE(Cfg("v42.1").match(Cfg("v42.1")));

  // ConfigDescription::Parse doesn't allow "v0.3" so we have to create it manually to test
  ResTable_config config = {};
  config.sdkVersion = 0;
  config.minorVersion = 3;
  EXPECT_FALSE(config.match(Cfg("v41")));
}

TEST(ConfigTest, SdkAndMinorVersion_IsBetterThan) {
  ResTable_config requested = Cfg("v45");
  EXPECT_FALSE(Cfg("v40").isBetterThan(Cfg("v40"), &requested));
  EXPECT_TRUE(Cfg("v41").isBetterThan(Cfg("v40"), &requested));
  EXPECT_TRUE(Cfg("v41.1").isBetterThan(Cfg("v41"), &requested));
  EXPECT_TRUE(Cfg("v41.2").isBetterThan(Cfg("v41.1"), &requested));
  EXPECT_TRUE(Cfg("v42").isBetterThan(Cfg("v41.2"), &requested));
  EXPECT_TRUE(Cfg("v43.1").isBetterThan(Cfg("v42"), &requested));

  requested = Cfg("v45.9");
  EXPECT_FALSE(Cfg("v40").isBetterThan(Cfg("v40"), &requested));
  EXPECT_TRUE(Cfg("v41").isBetterThan(Cfg("v40"), &requested));
  EXPECT_TRUE(Cfg("v41.1").isBetterThan(Cfg("v41"), &requested));
  EXPECT_TRUE(Cfg("v41.2").isBetterThan(Cfg("v41.1"), &requested));
  EXPECT_TRUE(Cfg("v42").isBetterThan(Cfg("v41.2"), &requested));
  EXPECT_TRUE(Cfg("v43.1").isBetterThan(Cfg("v42"), &requested));

  // isBetterThan defaults to isMoreSpecificThan if requested is null
  EXPECT_FALSE(Cfg("v40").isBetterThan(Cfg("v40"), nullptr));
  EXPECT_FALSE(Cfg("v41").isBetterThan(Cfg("v40"), nullptr));
  EXPECT_TRUE(Cfg("v41.1").isBetterThan(Cfg("v41"), nullptr));
  EXPECT_FALSE(Cfg("v41.2").isBetterThan(Cfg("v41.1"), nullptr));
  EXPECT_FALSE(Cfg("v42").isBetterThan(Cfg("v41.2"), nullptr));
  EXPECT_TRUE(Cfg("v43.1").isBetterThan(Cfg("v42"), nullptr));
}

TEST(ConfigTest, SdkAndMinorVersion_SelectBest) {
  ResTable_config requested = Cfg("v45");
  EXPECT_STREQ("v42", selectBest(requested, {Cfg("v40"), Cfg("v42"), Cfg("v41")}).toString());
  EXPECT_STREQ("v42.3",
               selectBest(requested, {Cfg("v40.5"), Cfg("v42.3"), Cfg("v41.2")}).toString());
  EXPECT_STREQ("v42.5",
               selectBest(requested, {Cfg("v42.5"), Cfg("v42.3"), Cfg("v41.2")}).toString());
  EXPECT_STREQ("v42.5",
               selectBest(requested, {Cfg("v42.3"), Cfg("v41.2"), Cfg("v42.5")}).toString());
  EXPECT_STREQ("v42.5",
               selectBest(requested, {Cfg("v42"), Cfg("v42.5"), Cfg("v41.2")}).toString());
  EXPECT_STREQ("v44", selectBest(requested, {Cfg("v42.5"), Cfg("v42.3"), Cfg("v44")}).toString());

  requested = Cfg("v45.9");
  EXPECT_STREQ("v42", selectBest(requested, {Cfg("v40"), Cfg("v42"), Cfg("v41")}).toString());
  EXPECT_STREQ("v42.3",
               selectBest(requested, {Cfg("v40.5"), Cfg("v42.3"), Cfg("v41.2")}).toString());
  EXPECT_STREQ("v42.5",
               selectBest(requested, {Cfg("v42.5"), Cfg("v42.3"), Cfg("v41.2")}).toString());
  EXPECT_STREQ("v42.5",
               selectBest(requested, {Cfg("v42.3"), Cfg("v41.2"), Cfg("v42.5")}).toString());
  EXPECT_STREQ("v42.5",
               selectBest(requested, {Cfg("v42"), Cfg("v42.5"), Cfg("v41.2")}).toString());
  EXPECT_STREQ("v44",
               selectBest(requested, {Cfg("v42.5"), Cfg("v42.3"), Cfg("v44")}).toString());
  EXPECT_STREQ("v45.6",
               selectBest(requested, {Cfg("v45.3"), Cfg("45"), Cfg("v45.6")}).toString());
}

}  // namespace android.
