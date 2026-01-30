# Running AppMemoryTestCases

This guide provides instructions on how to run `AppMemoryTestCases` on a local device and in the Forrest/ABTD environment.

## Running on a Local Device

Follow these steps to run the tests on a physical device connected to your cloudtop.

1. **Install the superproject** (see go/repo-init) on your cloudtop.

2. **Set up and flash** your test device.

3. **Link the device** to your cloudtop using [pontis](http://go/pontis).

4. Source the build environment and select the target:

```
source build/envsetup.sh
lunch panther-trunk_staging-userdebug
```

5. Navigate to the test directory:

```
cd frameworks/base/tests/AppMemoryTest/
```

6. Execute the test suite:

```
atest AppMemoryTestCases
```

7. **Find the test artifacts** in the output directory specified in the logs. For example:

/tmp/atest_result_yourusernamehere/20250903_180921_clbph7a3/log/invocation_946579573326099876/inv_13336902944655844617/AppMemoryTestCases


## Running on Forrest/ABTD

Follow these steps to execute the tests using the Forrest/ABTD web interface.

1. Click **Create a run**.

2. Click **Run tests**.

3. Select **LGKB** as the test runner.

4. Select the branch **git_main**.

5. Input `AppMemoryTestCases` as the **atest module**.

6. Check the **Advanced** settings checkbox.

7. Set the product to a physical device, e.g., **raven**.

8. Click **Run** to start the test.
