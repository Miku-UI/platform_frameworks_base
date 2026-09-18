# ProtoLog Lint Checker

This document explains how to run the custom Android Lint checks for ProtoLog usage and how to add
these checks to other modules in your `Android.bp` files.

These checks help ensure correct and efficient use of the ProtoLog library.

## Available Checks

The following Issue IDs are available:

*   `ProtoLogInvalidFormatSpecifier`: Ensures only supported format specifiers (%b, %d, %f, %s) are
                                      used.
*   `ProtoLogNonConstantFormat`: Ensures the format string is a compile-time constant.
*   `ProtoLogArgCount`: Checks for the correct number of arguments for the format string.
*   `ProtoLogArgType`: Checks that argument types match the format specifiers.
*   `ProtoLogConstantArgument`: Warns against using constants as arguments (they should be in the
                                format string).
*   `ProtoLogNoContext`: Encourages format strings to have textual context beyond just specifiers.

## How to Run the Checks

The lint checks run as part of the normal build process for any module that has them configured. To
trigger the lint checks for a specific module, you can build the module's lint target:

```bash
m <YourModuleName>-lint
```

For example, to run the checks on the `WindowManager-Shell` module defined in the example:
```
m WindowManager-Shell-lint
```

Lint results (errors and warnings) will be output to the console. Detailed reports are generated in
the out/ directory.

## How to Add These Checks to Your Module

To add the ProtoLog lint checks to your android_library or java_library in its Android.bp file, add
a lint section:

1. Include the Checker Module: Add `ProtoLogLintChecker` to the `extra_check_modules` list.
2. Specify Checks: List the desired ProtoLog Issue IDs under `error_checks` or `warning_checks`.

```
android_library {
    name: "MyAwesomeModule",
    // ... other properties like srcs, static_libs, etc.

    lint: {
        extra_check_modules: [
            "ProtoLogLintChecker",
        ],
        error_checks: [
            // (optional) Promote specific ProtoLog checks to errors
            "ProtoLogNoContext",
        ],
        warning_checks: [
            // (optional) Promote/demote specific ProtoLog checks to warnings
            "ProtoLogConstantArgument",
        ],
    },
}
```
