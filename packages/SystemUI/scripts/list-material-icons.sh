#!/bin/bash

# list-m3-icons.sh
# Lists all M3 icons available in the prebuilt AndroidX sources JAR.
# Output format: type/IconName.kt (e.g., filled/Foo.kt)

# Set strict mode: exit on error, error on unset variables, error if pipeline fails.
set -euo pipefail

# --- Configuration ---
# The base path structure inside the sources JAR that we want to strip from the output
readonly INTERNAL_JAR_PREFIX="commonMain/androidx/compose/material/icons/"
# The pattern to find the sources JAR within ANDROID_BUILD_TOP
readonly JAR_RELATIVE_PATTERN="prebuilts/sdk/current/androidx/m2repository/androidx/compose/material/material-icons-extended-android/*/material-icons-extended-android-*-sources.jar"

# --- Main Script ---

# 1. Validate Environment
if [[ -z "${ANDROID_BUILD_TOP:-}" ]]; then
    echo "Error: ANDROID_BUILD_TOP environment variable is not set." >&2
    echo "Please run 'source build/envsetup.sh' and 'lunch' first." >&2
    exit 1
fi

# 2. Locate the Source JAR
JAR_PATTERN="${ANDROID_BUILD_TOP}/${JAR_RELATIVE_PATTERN}"

# Use nullglob so the array is empty if nothing matches, instead of containing the pattern itself.
shopt -s nullglob
JARS=($JAR_PATTERN)
shopt -u nullglob # Turn it off immediately after use

if [[ ${#JARS[@]} -eq 0 ]]; then
    echo "Error: Could not find the material-icons-extended sources JAR." >&2
    echo "Looked for pattern: ${JAR_PATTERN}" >&2
    exit 1
fi

SOURCE_JAR="${JARS[0]}"

if [[ ${#JARS[@]} -gt 1 ]]; then
    # If multiple versions somehow exist, use the first match found.
    # Redirect warning to stderr so stdout only contains the file list.
    echo "Warning: Found multiple matching JARs. Using: ${SOURCE_JAR}" >&2
fi

# 3. List and Format Icons

# We use a pipeline to process the JAR contents:
# 1. unzip -Z1: Lists the contents of the JAR, one file per line (ZipInfo format).
# 2. grep: Filters the list using a specific regex to match the expected structure (Prefix/Type/File.kt).
#    We assume icons are exactly one level deep (the style directory).
#    - ^${INTERNAL_JAR_PREFIX}: Starts exactly with the prefix.
#    - [^/]*/: Followed by a single directory level (e.g., "filled/").
#    - [^/]*\.kt$: Followed by a filename ending in .kt (ensuring no further subdirectories).
# 3. sed: Removes the prefix from the start of the line.
# 4. sort: Ensures a consistent, alphabetical output.

unzip -Z1 "${SOURCE_JAR}" | \
    grep "^${INTERNAL_JAR_PREFIX}[^/]*/[^/]*\.kt$" | \
    sed "s|^${INTERNAL_JAR_PREFIX}||" | \
    sort
