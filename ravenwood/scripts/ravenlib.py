#
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Library for Ravenwood python scripts."""

import csv
import pathlib
import re
from typing import Callable

Path = pathlib.Path


class SourceFile:
  """A Java or Kotlin source file."""

  path: Path
  lines: list[str]
  modified: bool = False

  def __init__(self, path: Path):
    self.path = path
    with open(path, "r") as f:
      self.lines = f.readlines()

  def get_package(self) -> str:
    """Returns the package name of the source file."""
    for line in self.lines:
      if line.startswith("package "):
        return line.split(" ", 1)[1].strip()
    return ""

  def get_class_idx(self, class_name: str) -> int:
    """Returns the index of the class in the source file."""
    simple_class_name = class_name.split(".")[-1]
    for idx, line in enumerate(self.lines):
      if f"class {simple_class_name}" in line:
        return idx
    return -1

  def list_classes(self) -> list[(str, int)]:
    """Finds the classes and their line numbers in the source file."""
    if self.path.name.endswith(".java"):
      pattern = re.compile(
          r"^(?:(?:public|protected|private|abstract|static|final)\s+)*"
          r"class\s+"
          r"(\w+)"
      )
    elif self.path.name.endswith(".kt"):
      pattern = re.compile(
          r"^(?:(?:public|protected|private|internal|data|open|abstract|sealed)\s+)*"
          r"class\s+"
          r"(\w+)"
      )
    else:
      return []

    results = []
    package = self.get_package()
    for idx, line in enumerate(self.lines):
      class_name = pattern.findall(line)
      if class_name:
        results.append((f"{package}.{class_name[0]}", idx))

    return results

  def remove_import(self, class_name: str):
    """Removes an import statement from the source file."""
    for idx, line in enumerate(self.lines):
      if line.startswith(f"import {class_name}"):
        self.lines.pop(idx)
        self.modified = True
        return

  def list_annotations(self, class_idx: int) -> list[(str, int)]:
    """Finds the annotations and their line numbers in the source file."""
    result = []
    curr_idx = class_idx - 1
    while True:
      line = self.lines[curr_idx].strip()
      if line.startswith("@"):
        annotation_class = line[1:].split("(", 1)[0]
        result.append((annotation_class, curr_idx))
        curr_idx -= 1
      else:
        break
    return result

  def remove_annotation(self, class_name: str, annotation: str):
    """Removes an annotation (ignoring the package name) from the source file."""
    class_idx = self.get_class_idx(class_name)
    for annot, idx in self.list_annotations(class_idx):
      if annot.split(".")[-1] == annotation.split(".")[-1]:
        self.lines.pop(idx)
        self.modified = True
        break

  def add_annotation(self, class_name: str, annotation: str):
    """Adds an annotation to the source file, if it doesn't have it already."""
    class_idx = self.get_class_idx(class_name)
    for annot, _ in self.list_annotations(class_idx):
      if annot.split(".")[-1] == annotation.split(".")[-1]:
        # The annotation is already present.
        return
    self.lines.insert(class_idx, f"@{annotation}\n")
    self.modified = True

  def write(self):
    """Writes the source file to disk."""
    if not self.modified:
      return
    with open(self.path, "w") as f:
      f.writelines(self.lines)

  def print(self):
    """Prints the source file."""
    for line in self.lines:
      print(line, end="")


def load_source_files(src_root: str) -> list[SourceFile]:
  """Loads all the source files from the given root directory."""
  files = []
  for java in Path(src_root).glob("**/*.java"):
    files.append(SourceFile(java))
  for kt in Path(src_root).glob("**/*.kt"):
    files.append(SourceFile(kt))
  return files


def load_source_map(src_root: str) -> dict[str, SourceFile]:
  """Loads all the source files from the given root directory, and returns a map from class name to source file."""
  files = load_source_files(src_root)
  result = {}
  for src in files:
    for clazz, _ in src.list_classes():
      result[clazz] = src
  return result


def _find_tests(
    csv_file: str, select_func: Callable[[[str]], bool]
) -> list[str]:
  """Finds all test classes from a test result CSV file."""
  test = []
  with open(csv_file) as f:
    reader = csv.DictReader(f)
    for row in reader:
      if row["Type"] == "c" and select_func(row):
        test.append(row["Class"])
  return test


def find_passed_tests(csv_file: str) -> list[str]:
  """Finds all test classes that passed from a test result CSV file."""
  return _find_tests(
      csv_file, lambda row: int(row["Failed"]) == 0 and int(row["Skipped"]) == 0
  )


def find_failed_tests(csv_file: str) -> list[str]:
  """Finds all test classes with at least one failure from a test result CSV file."""
  return _find_tests(csv_file, lambda row: int(row["Failed"]) > 0)
