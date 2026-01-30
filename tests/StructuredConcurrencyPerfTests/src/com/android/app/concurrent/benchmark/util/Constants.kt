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

const val DEBUG = false

const val VERBOSE_DEBUG = false

const val PERFETTO_CONFIG =
    """
# RSS events
buffers {
  size_kb: 262144
  fill_policy: RING_BUFFER
}
# procfs polling and linux system info from sysfs
buffers {
  size_kb: 8192
  fill_policy: RING_BUFFER
}
data_sources {
  config {
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {
      throttle_rss_stat: true
      # These parameters affect only the kernel trace buffer size and how
      # frequently it gets moved into the userspace buffer defined above.
      buffer_size_kb: 65536
      drain_period_ms: 500

      # Store certain high-volume "sched" ftrace events in a denser format
      # (falling back to the default format if not supported by the tracer).
      compact_sched {
        enabled: true
      }

      # Enables symbol name resolution against /proc/kallsyms
      symbolize_ksyms: true
      # Parse kallsyms before acknowledging that the ftrace data source has been started. In
      # combination with "perfetto --background-wait" as the consumer, it lets us defer the
      # test we're tracing until after the cpu has quieted down from the cpu-bound kallsyms parsing.
      initialize_ksyms_synchronously_for_testing: true
      # Avoid re-parsing kallsyms on every test run, as it takes 200-500ms per run. See b/239951079
      ksyms_mem_policy: KSYMS_RETAIN

      # We need to do process tracking to ensure kernel ftrace events targeted at short-lived
      # threads are associated correctly
      ftrace_events: "task/task_newtask"
      ftrace_events: "task/task_rename"

      ftrace_events: "sched/sched_process_exit"
      ftrace_events: "sched/sched_process_free"
      ftrace_events: "mm_event/mm_event_record"
      ftrace_events: "kmem/rss_stat"
      ftrace_events: "kmem/ion_heap_shrink"
      ftrace_events: "kmem/ion_heap_grow"
      ftrace_events: "ion/ion_stat"
      ftrace_events: "oom/oom_score_adj_update"
      ftrace_events: "disk"
      ftrace_events: "ufs/ufshcd_clk_gating"
      ftrace_events: "lowmemorykiller/lowmemory_kill"
      ftrace_events: "sched/sched_blocked_reason"
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "sched/sched_wakeup_new"
      ftrace_events: "sched/sched_waking"

      atrace_categories: "am"
      atrace_categories: "aidl"
      atrace_categories: "audio"
      atrace_categories: "binder_driver"
      atrace_categories: "camera"
      atrace_categories: "dalvik"
      atrace_categories: "freq"
      atrace_categories: "gfx"
      atrace_categories: "hal"
      atrace_categories: "idle"
      atrace_categories: "input"
      atrace_categories: "memreclaim"
      atrace_categories: "power"
      atrace_categories: "res"
      atrace_categories: "sched"
      atrace_categories: "sync"
      atrace_categories: "view"
      atrace_categories: "wm"
    }
  }
}
data_sources {
  config {
    name: "linux.process_stats"
    target_buffer: 1
    process_stats_config {
      scan_all_processes_on_start: true
      proc_stats_poll_ms: 10000
    }
  }
}
data_sources {
  config {
    name: "android.packages_list"
    target_buffer: 1
  }
}
data_sources {
  config {
    name: "linux.sys_stats"
    target_buffer: 1
    sys_stats_config {
      meminfo_period_ms: 1000
      meminfo_counters: MEMINFO_MEM_TOTAL
      meminfo_counters: MEMINFO_MEM_FREE
      meminfo_counters: MEMINFO_MEM_AVAILABLE
      meminfo_counters: MEMINFO_BUFFERS
      meminfo_counters: MEMINFO_CACHED
      meminfo_counters: MEMINFO_SWAP_CACHED
      meminfo_counters: MEMINFO_ACTIVE
      meminfo_counters: MEMINFO_INACTIVE
      meminfo_counters: MEMINFO_ACTIVE_ANON
      meminfo_counters: MEMINFO_INACTIVE_ANON
      meminfo_counters: MEMINFO_ACTIVE_FILE
      meminfo_counters: MEMINFO_INACTIVE_FILE
      meminfo_counters: MEMINFO_UNEVICTABLE
      meminfo_counters: MEMINFO_SWAP_TOTAL
      meminfo_counters: MEMINFO_SWAP_FREE
      meminfo_counters: MEMINFO_DIRTY
      meminfo_counters: MEMINFO_WRITEBACK
      meminfo_counters: MEMINFO_ANON_PAGES
      meminfo_counters: MEMINFO_MAPPED
      meminfo_counters: MEMINFO_SHMEM
    }
  }
}
data_sources {
  config {
    name: "android.power"
    android_power_config {
      battery_poll_ms: 250
      battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
      battery_counters: BATTERY_COUNTER_CHARGE
      battery_counters: BATTERY_COUNTER_CURRENT
      collect_power_rails: true
    }
  }
}
data_sources {
  config {
    name: "android.gpu.memory"
  }
}
data_sources {
  config {
    name: "android.surfaceflinger.frame"
  }
}
data_sources {
  config {
    name: "android.surfaceflinger.frametimeline"
  }
}
data_sources {
  config {
    name: "track_event"
    track_event_config {
      disabled_categories: "*"
      enabled_categories: "rendering"
    }
  }
}
enable_extra_guardrails: false
statsd_metadata {
}
write_into_file: true
file_write_period_ms: 2500
flush_period_ms: 5000
data_source_stop_timeout_ms: 2500
statsd_logging: STATSD_LOGGING_DISABLED
"""
