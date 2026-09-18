# AppMemoryTest (smoldroid)

AppMemoryTest is a test designed to produce low-noise, highly-actionable metrics
for Android cold app startups. It starts a barebones Android app, then collects
and reports only reproducible and easily-attributable metrics, such as the size
and number of items on the app heap or total number of binder transactions
emitted by the app process.

This guide provides instructions on how to run AppMemoryTest on a local device
and in ABTD, as well as how to analyze output artifacts.

## Running smoldroid on a local device

1. Connect a device over ADB (a local physical device, a virtual `acloud`
   device, etc.)
2. From the root of your Android checkout, run `source build/envsetup.sh` and
   `lunch <target>`, e.g. `lunch cheetah-trunk_staging-userdebug`.
3. Run `atest AppMemoryTestCases`, which will output a few metrics in plain text
   as well as a path to the directory containing atest output artifacts. In the
   example below, `appmemorytest_app_heap_count` and
   `appmemorytest_app_heap_size_bytes` are the metric results.
   `/tmp/atest_result/20251222_190845_6fbp6fde` will contain a Perfetto trace
   and a .hprof

```
$ atest AppMemoryTestCases

Atest results and logs directory: /tmp/atest_result/20251222_190845_6fbp6fde

...

arm64-v8a AppMemoryTestCases
----------------------------
arm64-v8a AppMemoryTestCases (1 Test)
[1/1] android.app.memory.tests.AppMemoryTest#testApp: PASSED (12.138s)
        appmemorytest_app_heap_count: 14198
        appmemorytest_app_heap_size_bytes: 563401

...
```

Googlers can find more detailed steps at
[go/smoldroid](http://go/smoldroid).

## Understanding smoldroid metrics

The plain metrics returned after the `atest` invocation (`_heap_count` and
`_heap_size_bytes`) refer to the number and size of the objects contained on the
barebones test app's Java heap after a cold startup. This is directly computed
from the .hprof file collected during the test, which can be further inspected
with AHAT. The heap object count and size is expected to be fairly stable across
test runs, as allocations made during an app startup shouldn't vary much.

The trace file collected by smoldroid can be analyzed like any other device-side
[performance trace](https://developer.android.com/topic/performance/tracing),
meaning that it can be opened in the [Perfetto UI](https://ui.perfetto.dev/) and
supports
[PerfettoSQL](https://perfetto.dev/docs/analysis/perfetto-sql-getting-started)
queries. The `metrics/` folder contains a couple of quick sample queries--for
example, the SQL in `appmemorytest_binder_transactions.textproto` can be used
to view the full set of binder transactions initiated by the test app during
startup.

```sql
INCLUDE PERFETTO MODULE android.binder;
INCLUDE PERFETTO MODULE android.startup.startups;
WITH
  testhelper_startup AS (
    SELECT
      ts,
      (ts + dur) AS ts_end
    FROM
      android_startups
    WHERE
      package = 'android.app.memory.testhelper'
    LIMIT 1
  )
SELECT
  abt.aidl_name,
  COALESCE(abt.aidl_name, 'NULL') AS aidl_name_including_nulls
FROM
  android_binder_txns AS abt
  JOIN testhelper_startup
    ON (
      abt.client_ts >= testhelper_startup.ts
      AND abt.client_ts < testhelper_startup.ts_end
    )
WHERE
  abt.client_process = 'android.app.memory.testhelper'
```

The query above returns this output:

| aidl_name | aidl_name_including_nulls |
| --- | --- |
| null | NULL |
| null | NULL |
| AIDL::java::IActivityManager::attachApplication::server | AIDL::java::IActivityManager::attachApplication::server |
| null | NULL |
| AIDL::java::IDisplayManager::getPreferredWideGamutColorSpaceId::server | AIDL::java::IDisplayManager::getPreferredWideGamutColorSpaceId::server |
| AIDL::java::IDisplayManager::getOverlaySupport::server | AIDL::java::IDisplayManager::getOverlaySupport::server |
| AIDL::java::IDisplayManager::getDisplayInfo::server | AIDL::java::IDisplayManager::getDisplayInfo::server |
| AIDL::java::INetworkManagementService::setUidCleartextNetworkPolicy::server | AIDL::java::INetworkManagementService::setUidCleartextNetworkPolicy::server |
| ... | ... |
