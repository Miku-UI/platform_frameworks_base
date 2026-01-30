# [Android Framework Lint Checker](./framework)

Checks written here are going to be executed for modules that opt in to those (e.g. any
`services.XXX` module) and results will be automatically reported on CLs on gerrit.

## How to add new framework lint checks

1. Write your detector with its issues and put it into
   `framework/checks/src/main/java/com/google/android/lint`.
2. Add your detector's issues into `AndroidFrameworkIssueRegistry.kt`'s `issues`
   field.
3. Write unit tests for your detector in one file and put it into
   `framework/checks/test/java/com/google/android/lint`.
4. Done! Your lint checks should be applied in lint report builds for modules that include
   `AndroidFrameworkLintChecker`.

## How to run lint against your module

1. Add the following `lint` attribute to the module definition, e.g. `services.autofill`:
```
java_library_static {
    name: "services.autofill",
    ...
    lint: {
        extra_check_modules: ["AndroidFrameworkLintChecker"],
    },
}
```
2. Run the following command to verify that the report is being correctly built:
```
m services-autofill-lint
```
   Replace `services-autofil` with your module's name. After you run the command, the
   lint report can be found in the soong intermediates path corresponding to your
   module , i.e.
   `out/soong/.intermediates/frameworks/base/services/autofill/services.autofill/android_common/lint/lint-report.html`.

3. Now lint issues should appear on gerrit!

**Notes:**

- Lint report will not be produced if you just build the module, i.e. `m services.autofill` will not
  build the lint report.
- If you want to build lint reports for more than 1 module and they include a common module in their
  `defaults` field, e.g. `platform_service_defaults`, you can add the `lint` property to that common
  module instead of adding it in every module.
- If you want to run a single lint type, use the `ANDROID_LINT_CHECK`
  environment variable with the id of the lint. For example:
  `ANDROID_LINT_CHECK=UnusedTokenOfOriginalCallingIdentity m out/[...]/lint-report.html`

  There are more notes and instructions available in the `//platform/tools/lint_checks` repo.
