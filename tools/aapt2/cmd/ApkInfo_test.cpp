/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "ApkInfo.h"

#include "ApkInfo.pb.h"
#include "LoadedApk.h"
#include "android-base/unique_fd.h"
#include "io/StringStream.h"
#include "test/Test.h"

using testing::Eq;
using testing::Ne;

namespace aapt {

using ApkInfoTest = CommandTestFixture;

void AssertProducedAndExpectedInfo(const std::string& produced_path,
                                   const std::string& expected_path) {
  android::base::unique_fd fd(open(produced_path.c_str(), O_RDONLY));
  ASSERT_NE(fd.get(), -1);

  pb::ApkInfo produced_apk_info;
  produced_apk_info.ParseFromFileDescriptor(fd.get());

  std::string expected;
  ::android::base::ReadFileToString(expected_path, &expected);

  std::string produced;

  {
    // Use a CodedOutputStream with SetSerializationDeterministic(true) to
    // produce a deterministic proto message that can be compared against the
    // golden output.
    // Use an inner scope so that the output streams are flushed when
    // leaving the scope.
    google::protobuf::io::StringOutputStream string_output_stream(&produced);
    google::protobuf::io::CodedOutputStream coded_output(&string_output_stream);
    coded_output.SetSerializationDeterministic(true);

    ASSERT_TRUE(produced_apk_info.SerializeToCodedStream(&coded_output));
  }

  EXPECT_EQ(produced, expected);
}

static android::NoOpDiagnostics noop_diag;

// To regenerate the golden data for these tests, run:
// $ cd $ANDROID_BUILD_TOP
// $ m aapt2
// $ out/soong/.intermediates/frameworks/base/tools/aapt2/aapt2/linux_glibc_x86_64/unversioned/aapt2 apkinfo frameworks/base/tools/aapt2/integration-tests/DumpTest/components.apk  -o frameworks/base/tools/aapt2/integration-tests/DumpTest/components_expected_proto.binpb
// $ out/soong/.intermediates/frameworks/base/tools/aapt2/aapt2/linux_glibc_x86_64/unversioned/aapt2 apkinfo --include-resource-table --include-xml AndroidManifest.xml --include-xml res/oy.xml frameworks/base/tools/aapt2/integration-tests/DumpTest/components.apk  -o frameworks/base/tools/aapt2/integration-tests/DumpTest/components_full_proto.binpb

TEST_F(ApkInfoTest, ApkInfoWithBadging) {
  auto apk_path = file::BuildPath(
      {android::base::GetExecutableDirectory(), "integration-tests", "DumpTest", "components.apk"});
  auto out_info_path = GetTestPath("apk_info.pb");

  ApkInfoCommand command(&noop_diag);
  command.Execute({"-o", out_info_path, apk_path}, &std::cerr);

  auto expected_path =
      file::BuildPath({android::base::GetExecutableDirectory(), "integration-tests", "DumpTest",
                       "components_expected_proto.binpb"});
  AssertProducedAndExpectedInfo(out_info_path, expected_path);
}

TEST_F(ApkInfoTest, FullApkInfo) {
  auto apk_path = file::BuildPath(
      {android::base::GetExecutableDirectory(), "integration-tests", "DumpTest", "components.apk"});
  auto out_info_path = GetTestPath("apk_info.pb");

  ApkInfoCommand command(&noop_diag);
  command.Execute({"-o", out_info_path, "--include-resource-table", "--include-xml",
                   "AndroidManifest.xml", "--include-xml", "res/oy.xml", apk_path},
                  &std::cerr);

  auto expected_path =
      file::BuildPath({android::base::GetExecutableDirectory(), "integration-tests", "DumpTest",
                       "components_full_proto.binpb"});
  AssertProducedAndExpectedInfo(out_info_path, expected_path);
}

}  // namespace aapt
