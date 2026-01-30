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

#include "optimize/VersionCollapser.h"

#include <algorithm>
#include <vector>

#include "ResourceTable.h"
#include "trace/TraceBuffer.h"

using android::ConfigDescription;

namespace aapt {

template <typename Iterator, typename Pred>
class FilterIterator {
 public:
  FilterIterator(Iterator begin, Iterator end, Pred pred = Pred())
      : current_(begin), end_(end), pred_(pred) {
    Advance();
  }

  bool HasNext() { return current_ != end_; }

  Iterator NextIter() {
    Iterator iter = current_;
    ++current_;
    Advance();
    return iter;
  }

  typename Iterator::reference Next() { return *NextIter(); }

 private:
  void Advance() {
    for (; current_ != end_; ++current_) {
      if (pred_(*current_)) {
        return;
      }
    }
  }

  Iterator current_, end_;
  Pred pred_;
};

template <typename Iterator, typename Pred>
FilterIterator<Iterator, Pred> make_filter_iterator(Iterator begin,
                                                    Iterator end = Iterator(),
                                                    Pred pred = Pred()) {
  return FilterIterator<Iterator, Pred>(begin, end, pred);
}

/**
 * Every Configuration with an SDK version specified that is less than minSdk will be removed. The
 * exception is when there is no exact matching resource for the minSdk. The next smallest one will
 * be kept.
 */
static void CollapseVersions(IAaptContext* context, int min_sdk, ResourceEntry* entry) {
  // First look for all sdks less than minSdk.
  // Note that we iterate in reverse order, meaning we will first encounter entries with the
  // highest SDK level and work our way down.
  for (auto iter = entry->values.rbegin(); iter != entry->values.rend();
       ++iter) {
    // Check if the item was already marked for removal.
    if (!(*iter)) {
      continue;
    }

    const ConfigDescription& config = (*iter)->config;
    if (config.sdkVersion != 0 && (config.sdkVersion < min_sdk ||
                                   (config.sdkVersion == min_sdk && config.minorVersion == 0))) {
      // This is the first configuration we've found with a smaller or equal SDK level to the
      // minimum. We MUST keep this one, but remove all others we find, which will be smaller and
      // therefore get overridden by this one.

      ConfigDescription config_without_sdk = config.CopyWithoutSdkVersion();
      auto pred = [&](const std::unique_ptr<ResourceConfigValue>& val) -> bool {
        // Check that the value hasn't already been marked for removal.
        if (!val) {
          return false;
        }

        // Only return Configs that differ in SDK version.
        config_without_sdk.version = val->config.version;
        return config_without_sdk == val->config &&
               (val->config.sdkVersion < min_sdk ||
                (val->config.sdkVersion == min_sdk && val->config.minorVersion == 0));
      };

      // Remove the rest that match; all of them will be overridden by this one.
      auto filter_iter =
          make_filter_iterator(iter + 1, entry->values.rend(), pred);
      while (filter_iter.HasNext()) {
        auto& next = filter_iter.Next();
        if (context->IsVerbose()) {
          context->GetDiagnostics()->Note(android::DiagMessage()
                                          << "removing configuration " << next->config.to_string()
                                          << " for entry: " << entry->name
                                          << ", because its SDK version is smaller than minSdk "
                                          << min_sdk);
        }
        next = {};
      }
    }
  }

  // Now erase the nullptr values.
  entry->values.erase(
      std::remove_if(entry->values.begin(), entry->values.end(),
                     [](const std::unique_ptr<ResourceConfigValue>& val)
                         -> bool { return val == nullptr; }),
      entry->values.end());

  // Strip the version qualifiers for every resource with version <= minSdk. This will ensure that
  // the resource entries are all packed together in the same ResTable_type struct and take up less
  // space in the resources.arsc table.
  bool modified = false;
  for (std::unique_ptr<ResourceConfigValue>& config_value : entry->values) {
    const auto& config = config_value->config;
    if (config.sdkVersion != 0 && (config.sdkVersion < min_sdk ||
                                   (config.sdkVersion == min_sdk && config.minorVersion == 0))) {
      // Override the resource with a Configuration without an SDK.
      std::unique_ptr<ResourceConfigValue> new_value =
          util::make_unique<ResourceConfigValue>(
              config_value->config.CopyWithoutSdkVersion(),
              config_value->product);
      if (context->IsVerbose()) {
        context->GetDiagnostics()->Note(android::DiagMessage()
                                        << "overriding resource: " << entry->name
                                        << ", removing SDK version from configuration "
                                        << config_value->config.to_string());
      }
      new_value->value = std::move(config_value->value);
      config_value = std::move(new_value);

      modified = true;
    }
  }

  if (modified) {
    // We've modified the keys (ConfigDescription) by changing the sdkVersion to 0. We MUST re-sort
    // to ensure ordering guarantees hold.
    std::sort(entry->values.begin(), entry->values.end(),
              [](const std::unique_ptr<ResourceConfigValue>& a,
                 const std::unique_ptr<ResourceConfigValue>& b) -> bool {
                return a->config.compare(b->config) < 0;
              });
  }
}

bool VersionCollapser::Consume(IAaptContext* context, ResourceTable* table) {
  TRACE_NAME("VersionCollapser::Consume");
  const int min_sdk = context->GetMinSdkVersion();
  if (context->IsVerbose()) {
    context->GetDiagnostics()->Note(android::DiagMessage()
                                    << "Running VersionCollapser with minSdk = " << min_sdk);
  }
  for (auto& package : table->packages) {
    for (auto& type : package->types) {
      for (auto& entry : type->entries) {
        CollapseVersions(context, min_sdk, entry.get());
      }
    }
  }
  return true;
}

}  // namespace aapt
