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
package com.android.app.concurrent.benchmark.util

private const val MAIN_THREAD_NAME = "BenchmarkRunner"

const val VLOG_TAG = "Benchmark"

// This name should match the one used in the Perfetto SQL
const val BG_THREAD_NAME_PREFIX = "Bg:"

const val BARRIER_TIMEOUT_MILLIS = 1000L

// These variables make it easier to copy-paste the SQL query below into the Perfetto UI for
// development:
private const val ts = "${'$'}ts"
private const val dur = "${'$'}dur"
private const val thread_name_prefix = "${'$'}thread_name_prefix"
private const val metric_name = "${'$'}metric_name"

// For each measurement (of which there are 50 by default), compute the average runtime of the
// main thread ("BenchmarkRunner") and the background thread ("BgThread"). Then, return the minimum,
// median (p50), and standard deviation of those averages.
const val PERFETTO_SQL_QUERY_FORMAT_STR =
    """
-- For debugging, replace selected value with 'BenchmarkRunner';
CREATE OR REPLACE PERFETTO FUNCTION main_thread_name()
RETURNS STRING AS
SELECT '$MAIN_THREAD_NAME';

-- For debugging, replace selected value with 'Bg:[percent character]';
CREATE OR REPLACE PERFETTO FUNCTION bg_thread_name()
RETURNS STRING AS
SELECT '%s';

INCLUDE PERFETTO MODULE linux.cpu.utilization.thread;

INCLUDE PERFETTO MODULE slices.with_context;

-- Returns the first counter value between ts (inclusive) and dur (exclusive)
-- from the 'metric: name' track, which is used by androidx to log metadata
-- about the current measurement, such as the number of iterations recorded,
-- or average nanoseconds per iteration for the measurement.
CREATE OR REPLACE PERFETTO FUNCTION metric_value(
    -- Start of the interval
    ts TIMESTAMP,
    -- Duration of the interval
    dur DURATION,
    -- Name of metric to retrieve
    metric_name STRING
)
RETURNS LONG AS
SELECT
  c.value
FROM counters AS c
LEFT JOIN counter_track AS t
  ON c.track_id = t.id
WHERE
  t.name = 'metric: ' || $metric_name AND $ts <= ts AND ts < (
    $ts + $dur
  )
ORDER BY
  ts ASC
LIMIT 1;

CREATE OR REPLACE PERFETTO TABLE benchmark_time_measurements AS
WITH
  benchmark_time AS (
    SELECT
      stack_id,
      depth
    FROM slice
    WHERE
      name = 'Benchmark Time'
  )
SELECT
  name AS measurement_name,
  ts,
  dur,
  metric_value(ts, dur, 'iterations') AS iteration_count,
  metric_value(ts, dur, 'timeNs') AS time_nanos
FROM benchmark_time
LEFT JOIN descendant_slice_by_stack(benchmark_time.stack_id) AS descendant
  ON descendant.depth = benchmark_time.depth + 1
WHERE
  name REGEXP '^measurement [0-9]*$'
  AND NOT iteration_count IS NULL AND time_nanos IS NOT NULL;

CREATE OR REPLACE PERFETTO TABLE benchmark_allocation_measurements AS
WITH
  benchmark_time AS (
    SELECT
      stack_id,
      depth
    FROM slice
    WHERE
      name = 'Benchmark Allocations'
  )
SELECT
  name AS measurement_name,
  ts,
  dur,
  metric_value(ts, dur, 'allocationCount') AS allocation_count
FROM benchmark_time
LEFT JOIN descendant_slice_by_stack(benchmark_time.stack_id) AS descendant
  ON descendant.depth = benchmark_time.depth + 1
WHERE
  name REGEXP '^measurement [0-9]*$' AND allocation_count IS NOT NULL;

CREATE OR REPLACE PERFETTO TABLE summary_metrics AS
SELECT
  *
FROM (
  SELECT
    min(time_nanos) AS time_nanos_min,
    percentile(time_nanos, 50) AS time_nanos_median,
    sqrt(
      sum(
        (
          time_nanos - (
            SELECT
              avg(time_nanos)
            FROM benchmark_time_measurements
          )
        ) * (
          time_nanos - (
            SELECT
              avg(time_nanos)
            FROM benchmark_time_measurements
          )
        )
      ) / (
        count(time_nanos) - 1
      )
    ) AS time_nanos_stddev,
    min(iteration_count) AS iteration_count_min,
    percentile(iteration_count, 50) AS iteration_count_median,
    sqrt(
      sum(
        (
          iteration_count - (
            SELECT
              avg(iteration_count)
            FROM benchmark_time_measurements
          )
        ) * (
          iteration_count - (
            SELECT
              avg(iteration_count)
            FROM benchmark_time_measurements
          )
        )
      ) / (
        count(iteration_count) - 1
      )
    ) AS iteration_count_stddev
  FROM benchmark_time_measurements
)
CROSS JOIN (
  SELECT
    min(allocation_count) AS allocation_count_min,
    percentile(allocation_count, 50) AS allocation_count_median,
    sqrt(
      sum(
        (
          allocation_count - (
            SELECT
              avg(allocation_count)
            FROM benchmark_allocation_measurements
          )
        ) * (
          allocation_count - (
            SELECT
              avg(allocation_count)
            FROM benchmark_allocation_measurements
          )
        )
      ) / (
        count(allocation_count) - 1
      )
    ) AS allocation_count_stddev
  FROM benchmark_allocation_measurements
);

CREATE OR REPLACE PERFETTO TABLE cpu_cycles_per_measurement AS
SELECT
  m.measurement_name,
  c.utid,
  thread.name AS thread_name,
  c.runtime / m.iteration_count AS thread_runtime_per_iteration_avg,
  c.millicycles / m.iteration_count AS thread_millicycles_per_iteration_avg
FROM benchmark_time_measurements AS m, cpu_cycles_per_thread_in_interval(m.ts, m.dur) AS c
JOIN thread
  USING (utid)
JOIN process
  USING (upid)
WHERE
  process.name = 'com.android.app.concurrent.benchmark'
  AND (
    thread_name LIKE main_thread_name() OR thread_name LIKE bg_thread_name()
  );

CREATE OR REPLACE PERFETTO FUNCTION matching_thread_with_largest_runtime(
  thread_name_prefix STRING
) RETURNS LONG AS
SELECT
  utid
FROM cpu_cycles_per_measurement
WHERE
  thread_name LIKE $thread_name_prefix
GROUP BY
  utid
ORDER BY
  sum(thread_runtime_per_iteration_avg) DESC
LIMIT 1;

CREATE OR REPLACE PERFETTO FUNCTION cpu_cycles_per_measurement_for_thread(
  thread_name_prefix STRING
)
RETURNS TABLE(
  measurement_name STRING,
  utid LONG,
  thread_name STRING,
  thread_runtime_per_iteration_avg LONG,
  thread_millicycles_per_iteration_avg LONG
) AS
SELECT
  measurement_name,
  utid,
  thread_name,
  thread_runtime_per_iteration_avg,
  thread_millicycles_per_iteration_avg
FROM cpu_cycles_per_measurement
WHERE
  utid = matching_thread_with_largest_runtime($thread_name_prefix);

CREATE OR REPLACE PERFETTO TABLE thread_cpu_cycles_per_measurement AS
SELECT
  m.measurement_name,
  m.dur,
  m.iteration_count,
  m.time_nanos,
  main_thread.thread_millicycles_per_iteration_avg AS main_thread_millicycles_per_iteration,
  main_thread.thread_runtime_per_iteration_avg AS main_thread_runtime_per_iteration,
  main_thread.thread_name AS main_thread_name,
  main_thread.utid AS main_thread_utid,
  bg_thread.thread_name AS bg_thread_name,
  bg_thread.utid AS bg_thread_thread_utid,
  bg_thread.thread_millicycles_per_iteration_avg AS bg_thread_millicycles_per_iteration,
  bg_thread.thread_runtime_per_iteration_avg AS bg_thread_runtime_per_iteration
FROM benchmark_time_measurements AS m
LEFT JOIN cpu_cycles_per_measurement_for_thread(main_thread_name()) AS main_thread
  USING (measurement_name)
LEFT JOIN cpu_cycles_per_measurement_for_thread(bg_thread_name()) AS bg_thread
  USING (measurement_name);

SELECT
  *
FROM (
  SELECT
    summary_metrics.time_nanos_min,
    summary_metrics.time_nanos_median,
    summary_metrics.time_nanos_stddev,
    measurement_name AS time_nanos_min_measurement_name,
    main_thread_runtime_per_iteration AS time_nanos_min_main_thread_runtime_per_iteration,
    bg_thread_runtime_per_iteration AS time_nanos_min_bg_thread_runtime_per_iteration,
    summary_metrics.iteration_count_min,
    summary_metrics.iteration_count_median,
    summary_metrics.iteration_count_stddev,
    summary_metrics.allocation_count_min,
    summary_metrics.allocation_count_median,
    summary_metrics.allocation_count_stddev,
    main_thread_name,
    main_thread_utid,
    bg_thread_name,
    bg_thread_thread_utid
  FROM thread_cpu_cycles_per_measurement
  JOIN summary_metrics
    ON (
      time_nanos = time_nanos_min
    )
  ORDER BY
    time_nanos ASC
  LIMIT 1
)
WHERE
  NOT EXISTS(
    SELECT
      1
    FROM stats
    WHERE
      severity IN ('data_loss', 'error') AND value > 0
  )
UNION ALL
-- Generate error values for traces with errors or data loss:
SELECT
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()',
  '=NA()'
WHERE
  EXISTS(
    SELECT
      1
    FROM stats
    WHERE
      severity IN ('data_loss', 'error') AND value > 0
  );
"""
