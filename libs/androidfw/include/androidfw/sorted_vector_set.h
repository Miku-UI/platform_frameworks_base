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

#pragma once

#include <algorithm>
#include <concepts>
#include <utility>
#include <vector>

namespace android {

// The implementation of std::set that holds all items in a sorted vector - basically std::flat_set
// with a little fewer generic features in favor of code complexity.
// Given that libc++ decided to not implement it for now, this class can be used until it's
// available in the STL.
//
// This is the most efficient way of storing a rarely modified collection of items - all the
// lookups happen faster because of the cache locality, there's much less per-iterm memory overhead,
// and in order iteration is the fastest it can be on modern hardware.
//
// The tradeoff is in the insertion and erasure complexity - this container has to move the whole
// trail of elements when modifying one in the middle, turning it into a linear operation instead
// of a logarithmic one.
//
// Also, beware of the iterator and pointer stability - the underlying vector can resize at any
// insertion, so insertions invalidate all iterators and pointers, and removals invalidate
// everything past the removed element.
//
// TODO(b/406585404): replace with std::flat_set<> when available.

// A way to ensure that the templated lookup functions are only called with the compatible types.
template <class T, class U, class Cmp>
concept Comparable = requires(const T t, const U u, Cmp c) {
  { c(t, u) } -> std::convertible_to<bool>;
  { c(u, t) } -> std::convertible_to<bool>;
};

template <class T, class Cmp = std::less<>>
class sorted_vector_set : std::vector<T> {
  using base = std::vector<T>;

 public:
  // Set doesn't let you modify its elements via iterators.
  using iterator = typename base::const_iterator;
  using reverse_iterator = typename base::const_reverse_iterator;
  using const_iterator = iterator;
  using const_reverse_iterator = reverse_iterator;
  using value_type = typename base::value_type;
  using reference = const value_type&;
  using size_type = typename base::size_type;
  using key_compare = Cmp;
  using value_compare = Cmp;

  sorted_vector_set() = default;

  explicit sorted_vector_set(Cmp cmp) : mCmp(std::move(cmp)) {
  }

  sorted_vector_set(size_t reserve_size) {
    base::reserve(reserve_size);
  }

  // Const functions mostly can be passed through.
  using base::capacity;
  using base::cbegin;
  using base::cend;
  using base::crbegin;
  using base::crend;
  using base::empty;
  using base::size;

  // These are totally safe as well.
  using base::clear;
  using base::erase;
  using base::pop_back;
  using base::reserve;
  using base::shrink_to_fit;

  iterator begin() const {
    return cbegin();
  }
  iterator end() const {
    return cend();
  }
  reverse_iterator rbegin() const {
    return crbegin();
  }
  reverse_iterator rend() const {
    return crend();
  }

  key_compare key_comp() const {
    return mCmp;
  }
  value_compare value_comp() const {
    return mCmp;
  }

  // Add the vector interface accessor as well.
  const base& vector() const {
    return *this;
  }

  // Now, set interface.
  template <class Key = T>
    requires Comparable<Key, T, Cmp>
  bool contains(const Key& k) const {
    return find(k) != base::cend();
  }

  template <class Key = T>
    requires Comparable<Key, T, Cmp>
  const_iterator find(const Key& k) const {
    auto it = lower_bound(k);
    if (it == base::cend()) return it;
    if (mCmp(k, *it)) return base::end();
    return it;
  }

  template <class Key = T>
    requires Comparable<Key, T, Cmp>
  const_iterator lower_bound(const Key& k) const {
    return std::lower_bound(base::begin(), base::end(), k, mCmp);
  }

  std::pair<iterator, bool> insert(const T& t) {
    return insert(T(t));
  }

  std::pair<iterator, bool> insert(T&& t) {
    auto it = lower_bound(t);
    if (it != base::cend() && !mCmp(t, *it)) {
      return {it, false};
    }
    return {base::insert(it, std::move(t)), true};
  }

  template <class Key = T>
    requires Comparable<Key, T, Cmp>
  std::pair<iterator, bool> emplace(Key&& k) {
    auto it = lower_bound(k);
    if (it != base::cend() && !mCmp(k, *it)) {
      return {it, false};
    }
    return {base::emplace(it, std::forward<Key>(k)), true};
  }

  template <class Key = T>
    requires Comparable<Key, T, Cmp>
  std::pair<iterator, bool> emplace_hint(iterator hint, Key&& k) {
    // Check if the hint is in the correct position.
    if ((hint != base::cend() && mCmp(*hint, k)) ||
        (hint != base::cbegin() && mCmp(k, *(std::prev(hint))))) {
      // No, discard it.
      return emplace(std::forward<Key>(k));
    }
    return emplace_impl(hint, std::forward<Key>(k));
  }

  template <class Key = T>
    requires Comparable<Key, T, Cmp>
  size_t count(const Key& k) const {
    return contains(k);
  }

  template <class Key = T>
    requires Comparable<Key, T, Cmp>
  size_t erase(const Key& k) {
    const auto it = find(k);
    if (it == cend()) {
      return 0;
    }
    base::erase(it);
    return 1;
  }

 private:
  template <class Key>
  std::pair<iterator, bool> emplace_impl(iterator pos, Key&& k) {
    if (pos != base::cend() && !mCmp(k, *pos)) {
      return {pos, false};
    }
    return {base::emplace(pos, std::forward<Key>(k)), true};
  }

 private:
  [[no_unique_address]] Cmp mCmp;
};

}  // namespace android
