/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "java/ManifestClassGenerator.h"

#include "androidfw/Source.h"
#include "java/ClassDefinition.h"
#include "java/JavaClassGenerator.h"
#include "text/Unicode.h"
#include "xml/XmlDom.h"
#include "cmd/Util.h"
#include "util/Util.h"

using ::aapt::text::IsJavaIdentifier;

namespace aapt {

static std::optional<std::string> ExtractJavaIdentifier(android::IDiagnostics* diag,
                                                        const android::Source& source,
                                                        const std::string& value) {
  std::string result = value;
  size_t pos = value.rfind('.');
  if (pos != std::string::npos) {
    result = result.substr(pos + 1);
  }

  // Normalize only the java identifier, leave the original value unchanged.
  if (result.find('-') != std::string::npos) {
    result = JavaClassGenerator::TransformToFieldName(result);
  }

  if (result.empty()) {
    diag->Error(android::DiagMessage(source) << "empty symbol");
    return {};
  }

  if (!IsJavaIdentifier(result)) {
    diag->Error(android::DiagMessage(source) << "invalid Java identifier '" << result << "'");
    return {};
  }
  return result;
}

static bool WriteSymbol(const android::Source& source, android::IDiagnostics* diag,
                        xml::Element* el, ClassDefinition* class_def,
                        const FeatureFlagValues& feature_flag_values) {
  xml::Attribute* name_attr = el->FindAttribute(xml::kSchemaAndroid, "name");
  if (!name_attr) {
    diag->Error(android::DiagMessage(source) << "<" << el->name << "> must define 'android:name'");
    return false;
  }

  std::optional<std::string> identifier =
      ExtractJavaIdentifier(diag, source.WithLine(el->line_number), name_attr->value);
  if (!identifier) {
    return false;
  }

  std::unique_ptr<StringMember> string_member =
      util::make_unique<StringMember>(identifier.value(), name_attr->value);

  bool member_enabled = true;
  xml::Attribute* feature_flag_attr = el->FindAttribute(xml::kSchemaAndroid, xml::kAttrFeatureFlag);
  if (feature_flag_attr) {
    auto flag = ParseFlag(feature_flag_attr->value);
    if (flag) {
      const auto& flag_name = flag->name;
      bool negated = flag->negated;
      if (auto it = feature_flag_values.find(flag_name); it != feature_flag_values.end()) {
        // Member is disabled if flag==true && attr=="!flag" (negated)
        // OR flag==false && attr=="flag"
        if (it->second.enabled.has_value() && it->second.read_only &&
            *it->second.enabled == negated) {
          if (diag->IsVerbose()) {
            diag->Note(android::DiagMessage(source.WithLine(el->line_number))
                      << "not adding comment for '" << identifier.value()
                      << "' guarded by feature flag '" << (negated ? "!" : "") << flag_name
                      << "' with value " << (*it->second.enabled ? "true" : "false"));
          }
          member_enabled = false;
        }
      }
    }
  }
  string_member->GetCommentBuilder()->AppendComment(el->comment);

  // We ALWAYS want members generated in the Manifest Java file so that code referencing them (e.g.,
  // within a feature flag check) will still compile, regardless of the feature flag value. However,
  // if there are multiple members with the same name, we want to prioritize the members (i.e.,
  // permission or permission group) that are enabled based on the feature flag value. (See value
  // passed to `can_overwrite` below.)
  //
  // This mostly pertains to the comment on the member. For example, you can have the following
  // permissions in the manifest:
  //
  //    <!-- If `some_flag` is true, pick this comment for `PERM` member -->
  //    <permission android:name="android.permission.PERM" android:featureFlag="some_flag" />
  //    <!-- If `some_flag` is false, pick this comment for `PERM` member -->
  //    <permission android:name="android.permission.PERM" android:featureFlag="!some_flag" />

  if (class_def->AddMember(std::move(string_member),
                           /*can_overwrite=*/member_enabled) ==
      ClassDefinition::Result::kOverridden) {
    diag->Warn(android::DiagMessage(source.WithLine(el->line_number))
               << "duplicate definitions of '" << identifier.value() << "', overriding previous");
  }
  return true;
}

std::unique_ptr<ClassDefinition> GenerateManifestClass(
    android::IDiagnostics* diag, xml::XmlResource* res,
    const FeatureFlagValues& feature_flag_values) {
  xml::Element* el = xml::FindRootElement(res->root.get());
  if (!el) {
    diag->Error(android::DiagMessage(res->file.source) << "no root tag defined");
    return {};
  }

  if (el->name != "manifest" && !el->namespace_uri.empty()) {
    diag->Error(android::DiagMessage(res->file.source) << "no <manifest> root tag defined");
    return {};
  }

  std::unique_ptr<ClassDefinition> permission_class =
      util::make_unique<ClassDefinition>("permission", ClassQualifier::kStatic, false);
  std::unique_ptr<ClassDefinition> permission_group_class =
      util::make_unique<ClassDefinition>("permission_group", ClassQualifier::kStatic, false);

  bool error = false;
  std::vector<xml::Element*> children = el->GetChildElements();
  for (xml::Element* child_el : children) {
    if (child_el->namespace_uri.empty()) {
      if (child_el->name == "permission") {
        error |= !WriteSymbol(res->file.source, diag, child_el, permission_class.get(),
                              feature_flag_values);
      } else if (child_el->name == "permission-group") {
        error |= !WriteSymbol(res->file.source, diag, child_el, permission_group_class.get(),
                              feature_flag_values);
      }
    }
  }

  if (error) {
    return {};
  }

  std::unique_ptr<ClassDefinition> manifest_class =
      util::make_unique<ClassDefinition>("Manifest", ClassQualifier::kNone, false);
  manifest_class->AddMember(std::move(permission_class));
  manifest_class->AddMember(std::move(permission_group_class));
  return manifest_class;
}

}  // namespace aapt
