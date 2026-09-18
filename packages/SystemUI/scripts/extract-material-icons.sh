#!/bin/bash

# extract-material-icons.sh
# Extracts specified Material icons from the prebuilt AndroidX sources JAR,
# preserving the relative directory structure (e.g., filled/, outlined/).

# Set strict mode: exit on error, error on unset variables, error if pipeline fails.
set -euo pipefail

# --- Configuration ---
# The base path structure inside the sources JAR that precedes the icon paths
readonly INTERNAL_JAR_PREFIX="commonMain/androidx/compose/material/icons/"
# The pattern to find the sources JAR within ANDROID_BUILD_TOP
readonly JAR_RELATIVE_PATTERN="prebuilts/sdk/current/androidx/m2repository/androidx/compose/material/material-icons-extended-android/*/material-icons-extended-android-*-sources.jar"

# --- Functions ---

usage() {
    echo "Usage: $0 ICON_FILE_1.kt [ICON_FILE_2.kt ...] OUTPUT_DIRECTORY"
    echo ""
    echo "Example (preserves the directory structure):"
    echo "  $0 filled/Foo.kt outlined/Bar.kt path/to/output/directory/"
    echo ""
    echo "Requires the ANDROID_BUILD_TOP environment variable to be set."
}

# --- Main Script ---

# 1. Validate Environment and Arguments
if [[ $# -lt 2 ]]; then
    echo "Error: Missing arguments. At least one icon file (.kt) and an output directory are required." >&2
    usage
    exit 1
fi

if [[ -z "${ANDROID_BUILD_TOP:-}" ]]; then
    echo "Error: ANDROID_BUILD_TOP environment variable is not set." >&2
    echo "Please run 'source build/envsetup.sh' and 'lunch' first." >&2
    exit 1
fi

# 2. Parse Arguments
# The last argument is the output directory
OUTPUT_DIR="${@: -1}"
# All arguments except the last one are the icons (e.g., filled/Foo.kt)
# Array slicing: Start at index 1, take ($# - 1) elements
ICONS=("${@:1:$#-1}")

# 3. Locate the Source JAR
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
    echo "Warning: Found multiple matching JARs. Using the first one: ${SOURCE_JAR}" >&2
fi

echo "Using JAR: ${SOURCE_JAR}"

# 4. Extract Icons
echo "Starting extraction..."
FAILED_COUNT=0

# Iterate over icons individually.
# To strip the prefix (commonMain/...) but keep the suffix (filled/Foo.kt),
# we manually create the directory structure and use `unzip -p` (pipe to stdout).
for icon_path in "${ICONS[@]}"; do
    # icon_path is like filled/Foo.kt

    # Construct the full path inside the JAR (e.g., commonMain/.../filled/Foo.kt)
    internal_file_path="${INTERNAL_JAR_PREFIX}${icon_path}"

    # Determine the final output path (e.g., output_dir/filled/Foo.kt)
    output_file_path="${OUTPUT_DIR}${icon_path}"

    # Determine the subdirectory needed (e.g., output_dir/filled)
    output_subdir=$(dirname "${output_file_path}")

    # Ensure the subdirectory exists
    if ! mkdir -p "${output_subdir}"; then
         echo "  [ERROR] Could not create directory ${output_subdir}" >&2
         ((FAILED_COUNT++))
         continue
    fi

    # Extract the file: pipe content from JAR to the desired output file.
    # `unzip -p` extracts the raw content to stdout, ignoring paths.
    # We redirect stderr to /dev/null to keep the output clean if unzip fails,
    # relying on the exit code for success/failure detection.
    if unzip -p "${SOURCE_JAR}" "${internal_file_path}" > "${output_file_path}" 2>/dev/null; then
        echo "  [OK] Extracted ${icon_path} -> ${output_file_path}"
    else
        # unzip -p returns non-zero if the file is not found in the archive
        echo "  [ERROR] Failed to extract ${icon_path} (Not found in JAR)" >&2
        # Clean up the empty file created by the shell redirection (>) if unzip failed
        rm -f "${output_file_path}"
        ((FAILED_COUNT++))
    fi
done

# 5. Final Status
if [[ ${FAILED_COUNT} -gt 0 ]]; then
    echo "Done with errors. ${FAILED_COUNT} file(s) failed to extract." >&2
    exit 1
else
    echo "Done. Successfully extracted ${#ICONS[@]} file(s)."
fi