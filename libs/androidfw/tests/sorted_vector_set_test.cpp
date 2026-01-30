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

#include "androidfw/sorted_vector_set.h"

#include <gtest/gtest.h>

#include <algorithm>
#include <memory>
#include <set>
#include <utility>
#include <vector>

namespace android {

namespace {

template <class SortedVectorContainer>
class SortedVectorTestBase : public ::testing::Test {
 public:
  void TearDown() override {
    EXPECT_TRUE(std::ranges::is_sorted(s, s.key_comp()));
  }

 protected:
  SortedVectorContainer s;
};

class SortedVectorSetIntTest : public SortedVectorTestBase<sorted_vector_set<int>> {};

class SortedVectorSetIntGreaterTest
    : public SortedVectorTestBase<sorted_vector_set<int, std::greater<int>>> {};

struct MyStruct {
  int value;
  std::string name;

  bool operator==(const MyStruct& other) const {
    return value == other.value && name == other.name;
  }
};

struct MyStructComparator {
  bool operator()(const MyStruct& a, const MyStruct& b) const {
    return a.value < b.value;  // Compare based on value
  }
};

class SortedVectorSetMyStructTest
    : public SortedVectorTestBase<sorted_vector_set<MyStruct, MyStructComparator>> {};

class SortedVectorSetStringTest : public SortedVectorTestBase<sorted_vector_set<std::string>> {};
}  // namespace

TEST_F(SortedVectorSetIntTest, DefaultConstructor) {
  EXPECT_TRUE(this->s.empty());
  EXPECT_EQ(this->s.size(), 0);
}

TEST_F(SortedVectorSetIntTest, SizeConstructor) {
  sorted_vector_set<int> s2(10);  // Reserve space for 10 elements.
  EXPECT_TRUE(s2.empty());
  EXPECT_EQ(s2.size(), 0);
  EXPECT_GE(s2.capacity(), 10);
}

TEST_F(SortedVectorSetIntTest, InsertAndFind) {
  auto result1 = this->s.insert(5);
  EXPECT_TRUE(result1.second);
  EXPECT_EQ(*result1.first, 5);
  EXPECT_EQ(this->s.size(), 1);

  auto result2 = this->s.insert(5);
  EXPECT_FALSE(result2.second);
  EXPECT_EQ(*result2.first, 5);
  EXPECT_EQ(this->s.size(), 1);

  EXPECT_TRUE(this->s.contains(5));
  EXPECT_FALSE(this->s.contains(10));

  auto it = this->s.find(5);
  EXPECT_EQ(*it, 5);

  auto it2 = this->s.find(10);
  EXPECT_EQ(it2, this->s.end());

  auto it3 = this->s.find(1);
  EXPECT_EQ(it3, this->s.end());
}

TEST_F(SortedVectorSetIntTest, InsertMultipleAndFind) {
  this->s.insert(30);
  this->s.insert(10);
  this->s.insert(20);
  this->s.insert(50);
  this->s.insert(40);
  EXPECT_EQ(this->s.size(), 5);
  auto it = this->s.begin();
  EXPECT_EQ(*it++, 10);
  EXPECT_EQ(*it++, 20);
  EXPECT_EQ(*it++, 30);
  EXPECT_EQ(*it++, 40);
  EXPECT_EQ(*it, 50);

  EXPECT_EQ(this->s.find(5), this->s.end());
  EXPECT_EQ(this->s.find(15), this->s.end());
  EXPECT_EQ(this->s.find(25), this->s.end());
  EXPECT_EQ(this->s.find(35), this->s.end());
  EXPECT_EQ(this->s.find(45), this->s.end());
  EXPECT_EQ(this->s.find(55), this->s.end());
}

TEST_F(SortedVectorSetIntTest, Emplace) {
  auto result1 = this->s.emplace(5);
  EXPECT_TRUE(result1.second);
  EXPECT_EQ(*result1.first, 5);
  EXPECT_EQ(this->s.size(), 1);

  auto result2 = this->s.emplace(5);
  EXPECT_FALSE(result2.second);
  EXPECT_EQ(*result2.first, 5);
  EXPECT_EQ(this->s.size(), 1);
}

TEST_F(SortedVectorSetIntTest, EmplaceHint) {
  this->s.insert(1);
  this->s.insert(3);
  this->s.insert(6);

  auto hint = this->s.find(3);
  auto result1 = this->s.emplace_hint(hint, 4);
  EXPECT_TRUE(result1.second);
  EXPECT_EQ(*result1.first, 4);
  EXPECT_EQ(this->s.size(), 4);

  auto result2 = this->s.emplace_hint(this->s.begin(), 2);
  EXPECT_TRUE(result2.second);
  EXPECT_EQ(*result2.first, 2);
  EXPECT_EQ(this->s.size(), 5);

  auto result3 = this->s.emplace_hint(this->s.end(), 10);
  EXPECT_TRUE(result3.second);
  EXPECT_EQ(*result3.first, 10);
  EXPECT_EQ(this->s.size(), 6);

  auto result4 = this->s.emplace_hint(this->s.find(3), 3);
  EXPECT_FALSE(result4.second);
  EXPECT_EQ(*result4.first, 3);
  EXPECT_EQ(this->s.size(), 6);

  auto result5 = this->s.emplace_hint(this->s.find(10), 5);
  EXPECT_TRUE(result5.second);
  EXPECT_EQ(*result5.first, 5);
  EXPECT_EQ(this->s.size(), 7);
}

TEST_F(SortedVectorSetIntTest, EmplaceHintBeginning) {
  this->s.insert(2);
  auto hint = this->s.begin();
  auto result = this->s.emplace_hint(hint, 0);
  EXPECT_TRUE(result.second);
  EXPECT_EQ(*result.first, 0);
  EXPECT_EQ(this->s.size(), 2);

  result = this->s.emplace_hint(this->s.end(), 1);
  EXPECT_TRUE(result.second);
  EXPECT_EQ(*result.first, 1);
  EXPECT_EQ(this->s.size(), 3);
}

TEST_F(SortedVectorSetIntTest, EmplaceHintEnd) {
  this->s.insert(1);
  auto hint = this->s.end();
  auto result = this->s.emplace_hint(hint, 2);
  EXPECT_TRUE(result.second);
  EXPECT_EQ(*result.first, 2);
  EXPECT_EQ(this->s.size(), 2);
}

TEST_F(SortedVectorSetIntTest, EmplaceHintExisting) {
  this->s.insert(1);
  this->s.insert(2);
  auto hint = this->s.find(1);
  auto result = this->s.emplace_hint(hint, 1);
  EXPECT_FALSE(result.second);
  EXPECT_EQ(*result.first, 1);
  EXPECT_EQ(this->s.size(), 2);
}

TEST_F(SortedVectorSetIntTest, Count) {
  this->s.insert(5);
  EXPECT_EQ(this->s.count(5), 1);
  EXPECT_EQ(this->s.count(10), 0);
}

TEST_F(SortedVectorSetIntGreaterTest, CustomComparator) {
  this->s.insert(5);
  this->s.insert(10);
  this->s.insert(1);
  auto it = this->s.begin();
  EXPECT_EQ(*it++, 10);
  EXPECT_EQ(*it++, 5);
  EXPECT_EQ(*it, 1);
}

TEST_F(SortedVectorSetMyStructTest, InsertWithCustomComparator) {
  MyStruct a(1, "one");
  MyStruct b(2, "two");
  MyStruct c(3, "three");

  auto result1 = this->s.insert(a);
  EXPECT_TRUE(result1.second);
  EXPECT_EQ(*result1.first, a);
  EXPECT_EQ(this->s.size(), 1);

  auto result2 = this->s.insert(b);
  EXPECT_TRUE(result2.second);
  EXPECT_EQ(*result2.first, b);
  EXPECT_EQ(this->s.size(), 2);

  auto result3 = this->s.insert(a);  // Duplicate
  EXPECT_FALSE(result3.second);
  EXPECT_EQ(*result3.first, a);
  EXPECT_EQ(this->s.size(), 2);

  EXPECT_TRUE(this->s.contains(a));
  EXPECT_TRUE(this->s.contains(b));
  EXPECT_FALSE(this->s.contains(c));
}

TEST_F(SortedVectorSetMyStructTest, FindWithCustomComparator) {
  MyStruct a(1, "one");
  MyStruct b(2, "two");
  this->s.insert(a);
  this->s.insert(b);

  auto it1 = this->s.find(a);
  EXPECT_EQ(*it1, a);

  auto it2 = this->s.find(b);
  EXPECT_EQ(*it2, b);

  MyStruct c(3, "three");
  auto it3 = this->s.find(c);
  EXPECT_EQ(it3, this->s.end());
}

TEST_F(SortedVectorSetMyStructTest, LowerBoundWithCustomComparator) {
  MyStruct a(1, "one");
  MyStruct b(2, "two");
  MyStruct c(3, "three");
  this->s.insert(a);
  this->s.insert(c);

  auto it1 = this->s.lower_bound(b);
  EXPECT_EQ(*it1, c);  // lower_bound of 2 is 3

  auto it2 = this->s.lower_bound(a);
  EXPECT_EQ(*it2, a);
}

TEST_F(SortedVectorSetMyStructTest, EmplaceWithCustomComparator) {
  auto result1 = this->s.emplace({1, "one"});
  EXPECT_TRUE(result1.second);
  EXPECT_EQ(result1.first->value, 1);
  EXPECT_EQ(result1.first->name, "one");
  EXPECT_EQ(this->s.size(), 1);

  auto result2 = this->s.emplace({1, "another"});  // Duplicate value
  EXPECT_FALSE(result2.second);
  EXPECT_EQ(result2.first->value, 1);
  EXPECT_EQ(result2.first->name, "one");  // Should not change
  EXPECT_EQ(this->s.size(), 1);
}

TEST_F(SortedVectorSetMyStructTest, EmplaceHintWithCustomComparator) {
  this->s.emplace({1, "one"});
  this->s.emplace({3, "three"});
  auto hint = this->s.find(MyStruct(3, "three"));

  auto result1 = this->s.emplace_hint(hint, {2, "two"});
  EXPECT_TRUE(result1.second);
  EXPECT_EQ(result1.first->value, 2);
  EXPECT_EQ(result1.first->name, "two");
  EXPECT_EQ(this->s.size(), 3);

  auto result2 = this->s.emplace_hint(this->s.begin(), {0, "zero"});
  EXPECT_TRUE(result2.second);
  EXPECT_EQ(result2.first->value, 0);
  EXPECT_EQ(result2.first->name, "zero");
  EXPECT_EQ(this->s.size(), 4);
}

TEST_F(SortedVectorSetIntTest, ConstIterators) {
  this->s.insert(1);
  this->s.insert(2);
  this->s.insert(3);

  const sorted_vector_set<int>& const_s = this->s;  // Treat as const
  sorted_vector_set<int>::const_iterator cit = const_s.begin();
  EXPECT_EQ(*cit, 1);

  sorted_vector_set<int>::const_reverse_iterator crit = const_s.rbegin();
  EXPECT_EQ(*crit, 3);

  auto cbegin_it = const_s.cbegin();
  auto cend_it = const_s.cend();
  EXPECT_EQ(*cbegin_it, 1);
  EXPECT_NE(cbegin_it, cend_it);
  EXPECT_EQ(std::distance(cbegin_it, cend_it), 3);

  auto crbegin_it = const_s.crbegin();
  auto crend_it = const_s.crend();
  EXPECT_EQ(*crbegin_it, 3);
  EXPECT_NE(crbegin_it, crend_it);
  EXPECT_EQ(std::distance(crbegin_it, crend_it), 3);
}

TEST_F(SortedVectorSetIntTest, RangeBasedForLoop) {
  this->s.insert(3);
  this->s.insert(1);
  this->s.insert(2);
  int expected = 1;
  for (int value : this->s) {
    EXPECT_EQ(value, expected);
    expected++;
  }
}

TEST_F(SortedVectorSetIntTest, CopyConstructor) {
  this->s.insert(1);
  this->s.insert(2);
  sorted_vector_set<int> s2(this->s);  // Copy constructor
  EXPECT_EQ(s2.size(), 2);
  auto it = s2.begin();
  EXPECT_EQ(*it++, 1);
  EXPECT_EQ(*it, 2);

  // Ensure the copy is independent
  s2.insert(3);
  EXPECT_EQ(this->s.size(), 2);
  EXPECT_EQ(s2.size(), 3);
}

TEST_F(SortedVectorSetIntTest, CopyAssignmentOperator) {
  this->s.insert(1);
  this->s.insert(2);
  sorted_vector_set<int> s2;
  s2 = this->s;  // Copy assignment
  EXPECT_EQ(s2.size(), 2);
  auto it = s2.begin();
  EXPECT_EQ(*it++, 1);
  EXPECT_EQ(*it, 2);

  // Ensure the copy is independent
  s2.insert(3);
  EXPECT_EQ(this->s.size(), 2);
  EXPECT_EQ(s2.size(), 3);
}

TEST_F(SortedVectorSetIntTest, MoveConstructor) {
  this->s.insert(1);
  this->s.insert(2);
  size_t s_capacity = this->s.capacity();
  sorted_vector_set<int> s2(std::move(this->s));  // Move constructor
  EXPECT_EQ(s2.size(), 2);
  auto it = s2.begin();
  EXPECT_EQ(*it++, 1);
  EXPECT_EQ(*it, 2);
  EXPECT_EQ(s2.capacity(), s_capacity);  // Capacity should be moved

  // this->s is now in a valid but unspecified state, but often empty
  //  EXPECT_EQ(this->s.size(), 0);  // Not guaranteed to be 0.
  //  EXPECT_TRUE(this->s.empty());
}

TEST_F(SortedVectorSetIntTest, MoveAssignmentOperator) {
  this->s.insert(1);
  this->s.insert(2);
  size_t s_capacity = this->s.capacity();
  sorted_vector_set<int> s2;
  s2 = std::move(this->s);  // Move assignment
  EXPECT_EQ(s2.size(), 2);
  auto it = s2.begin();
  EXPECT_EQ(*it++, 1);
  EXPECT_EQ(*it, 2);
  EXPECT_EQ(s2.capacity(), s_capacity);

  // this->s is now in a valid but unspecified state.
}

TEST_F(SortedVectorSetIntTest, VectorAccessorAndIndexing) {
  this->s.insert(10);
  this->s.insert(20);
  this->s.insert(30);

  const std::vector<int>& underlyingVector = this->s.vector();
  auto it = underlyingVector.begin();
  EXPECT_EQ(*it++, 10);
  EXPECT_EQ(*it++, 20);
  EXPECT_EQ(*it, 30);

  EXPECT_EQ(this->s.vector().front(), 10);
  EXPECT_EQ(this->s.vector().back(), 30);
}

TEST_F(SortedVectorSetStringTest, StringMoveSemantics) {
  std::string str1 = "hello";
  std::string str2 = "world";

  this->s.insert(std::move(str1));
  EXPECT_EQ(this->s.size(), 1);
  EXPECT_EQ(*this->s.begin(), "hello");
  EXPECT_NE(*this->s.begin(), str1);  // String should be moved from.

  this->s.emplace(std::move(str2));
  EXPECT_EQ(this->s.size(), 2);
  auto it = this->s.begin();
  it++;
  EXPECT_EQ(*it, "world");
  EXPECT_NE(*it, str2);
}

TEST_F(SortedVectorSetStringTest, HeterogeneousComparison) {
  this->s.insert("apple");
  this->s.insert("banana");
  this->s.insert("cherry");

  EXPECT_TRUE(this->s.contains(std::string_view("apple")));
  EXPECT_TRUE(this->s.contains("banana"));
  EXPECT_FALSE(this->s.contains(std::string_view("grape")));
  EXPECT_FALSE(this->s.contains("grapefruit"));

  EXPECT_EQ(*this->s.find(std::string_view("apple")), "apple");
  EXPECT_EQ(*this->s.find("banana"), "banana");
  EXPECT_EQ(this->s.find(std::string_view("grape")), this->s.end());
  EXPECT_EQ(this->s.find("grapefruit"), this->s.end());

  EXPECT_EQ(*this->s.lower_bound(std::string_view("banana")), "banana");
  EXPECT_EQ(*this->s.lower_bound("banana"), "banana");
  EXPECT_EQ(this->s.lower_bound(std::string_view("grape")), this->s.end());
  EXPECT_EQ(this->s.lower_bound("grape"), this->s.end());

  EXPECT_EQ(this->s.erase(std::string_view("banana")), 1);
  EXPECT_EQ(this->s.size(), 2);
  EXPECT_EQ(this->s.erase("orange"), 0);
  EXPECT_EQ(this->s.size(), 2);
}

TEST_F(SortedVectorSetIntTest, Erase) {
  this->s.insert(1);
  this->s.insert(2);
  this->s.insert(3);
  this->s.insert(4);

  EXPECT_EQ(this->s.erase(0), 0);
  EXPECT_EQ(this->s.size(), 4);

  EXPECT_EQ(this->s.erase(1), 1);
  EXPECT_EQ(this->s.size(), 3);
  EXPECT_FALSE(this->s.contains(1));
  EXPECT_EQ(this->s.erase(1), 0);
  EXPECT_EQ(this->s.size(), 3);
  EXPECT_FALSE(this->s.contains(1));

  EXPECT_TRUE(std::ranges::is_sorted(this->s));
  EXPECT_EQ(*this->s.erase(this->s.begin() + 1), 4);
  EXPECT_EQ(this->s.size(), 2);
  EXPECT_TRUE(std::ranges::is_sorted(this->s));
}

}  // namespace android
