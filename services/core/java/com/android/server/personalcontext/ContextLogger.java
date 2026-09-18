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

package com.android.server.personalcontext;

import android.os.UserHandle;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.PublishedContextInsight;

import com.android.server.personalcontext.component.Component;
import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/** @hide */
public class ContextLogger implements
        RefinerWorkflow.EventListener,
        RendererWorkflow.EventListener,
        AccessController.EventListener {
    private static final int MAX_RECENT_TIMELINES_TO_KEEP = 3;
    private static final int MAX_RECENT_ACCESSES_TO_KEEP = 25;

    private final Executor mExecutor;
    private final Queue<Timeline> mRecentRefinerTimelines = new ConcurrentLinkedQueue<>();
    private final Map<Long, Timeline> mActiveRefinerTimelines = new HashMap<>();
    private final Queue<Timeline> mRecentRendererTimelines = new ConcurrentLinkedQueue<>();
    private final Map<Long, Timeline> mActiveRendererTimelines = new HashMap<>();
    private final Queue<Timeline> mRecentAccessTimelines = new ConcurrentLinkedQueue<>();

    public ContextLogger(Executor executor) {
        mExecutor = executor;
    }

    /** Called when a workflow is started. */
    @Override
    public void onRefinerWorkflowStarted(long flowId, Collection<PublishedContextHint> hints) {
        mExecutor.execute(() -> {
            final Timeline flowTimeline = new Timeline();

            flowTimeline.addStringDetail(
                    "Hint workflow %s started with %s hints", flowId, hints.size());

            flowTimeline.addHintListDetail(hints);

            mActiveRefinerTimelines.put(flowId, flowTimeline);
        });
    }

    /** Called when a set of hints is sent to a refiner. */
    @Override
    public void onHintsSentToRefiner(
            long flowId, Collection<PublishedContextHint> hints, Refiner refiner) {
        mExecutor.execute(() -> {
            Timeline flowTimeline = mActiveRefinerTimelines.get(flowId);
            if (flowTimeline == null) return;

            flowTimeline.addStringDetail(
                    "Hint workflow %s sent %s hints to refiner/understander", flowId, hints.size());

            flowTimeline.addComponentDetail(refiner);
            flowTimeline.addHintListDetail(hints);
        });
    }

    /** Called when a set of hints is received from a refiner. */
    @Override
    public void onHintsReceivedFromRefiner(
            long flowId, Collection<PublishedContextHint> hints, Refiner refiner) {
        mExecutor.execute(() -> {
            Timeline flowTimeline = mActiveRefinerTimelines.get(flowId);
            if (flowTimeline == null) return;

            flowTimeline.addStringDetail(
                    "Hint workflow %s received %s hints from refiner/understander",
                    flowId,
                    hints.size());

            flowTimeline.addComponentDetail(refiner);
            flowTimeline.addHintListDetail(hints);
        });
    }

    /** Called when a workflow stops. */
    @Override
    public void onRefinerWorkflowFinished(long flowId) {
        mExecutor.execute(() -> {
            Timeline flowTimeline = mActiveRefinerTimelines.get(flowId);
            if (flowTimeline == null) return;

            flowTimeline.addStringDetail("Hint workflow %s finished", flowId);

            flushRefinerWorkflowTimeline(flowId, flowTimeline);
        });
    }

    /** Called when a workflow has an unexpected error. */
    @Override
    public void onRefinerWorkflowError(long flowId, Throwable t) {
        mExecutor.execute(() -> {
            Timeline flowTimeline = mActiveRefinerTimelines.get(flowId);
            if (flowTimeline == null) return;

            flowTimeline.addStringDetail("Hint workflow %s failed", flowId);
            flowTimeline.addThrowableDetail(t);

            flushRefinerWorkflowTimeline(flowId, flowTimeline);
        });
    }

    private void flushRefinerWorkflowTimeline(long flowId, Timeline timeline) {
        mActiveRefinerTimelines.remove(flowId);
        mRecentRefinerTimelines.add(timeline);
        while (mRecentRefinerTimelines.size() > MAX_RECENT_TIMELINES_TO_KEEP) {
            mRecentRefinerTimelines.remove();
        }
    }

    /** Called when a workflow is started. */
    @Override
    public void onRendererWorkflowStarted(long flowId, PublishedContextInsight insight) {
        mExecutor.execute(() -> {
            final Timeline flowTimeline = new Timeline();

            flowTimeline.addStringDetail("Insight workflow %s started", flowId);
            flowTimeline.addInsightDetail(insight);

            mActiveRendererTimelines.put(flowId, flowTimeline);
        });
    }

    /** Called when an insight is sent to a renderer. */
    @Override
    public void onInsightSentToRenderer(
            long flowId, PublishedContextInsight insight, Renderer renderer) {
        mExecutor.execute(() -> {
            Timeline flowTimeline = mActiveRendererTimelines.get(flowId);
            if (flowTimeline == null) return;

            flowTimeline.addStringDetail("Insight workflow %s sent insight to renderer", flowId);
            flowTimeline.addComponentDetail(renderer);
        });
    }

    /** Called when a workflow stops. */
    @Override
    public void onRendererWorkflowFinished(long flowId) {
        mExecutor.execute(() -> {
            Timeline flowTimeline = mActiveRendererTimelines.get(flowId);
            if (flowTimeline == null) return;

            flowTimeline.addStringDetail("Insight workflow %s finished", flowId);

            flushRendererWorkflowTimeline(flowId, flowTimeline);
        });
    }

    /** Called when a workflow has an unexpected error. */
    @Override
    public void onRendererWorkflowError(long flowId, Throwable t) {
        mExecutor.execute(() -> {
            Timeline flowTimeline = mActiveRendererTimelines.get(flowId);
            if (flowTimeline == null) return;

            flowTimeline.addStringDetail("Insight workflow %s failed", flowId);
            flowTimeline.addThrowableDetail(t);

            flushRendererWorkflowTimeline(flowId, flowTimeline);
        });
    }

    @Override
    public void onAccessChecked(String packageName, UserHandle user, String description,
            int result) {
        mExecutor.execute(() -> {
            final Timeline accessTimeline = new Timeline();

            accessTimeline.addStringDetail("%s (Package: %s, User: %s) - %s",
                    description,
                    packageName,
                    user.getIdentifier(),
                    result == AccessController.RESULT_ALLOWED ? "allowed" :
                            result == AccessController.RESULT_DENIED ? "denied" : "bypassed");

            mRecentAccessTimelines.add(accessTimeline);
            while (mRecentAccessTimelines.size() > MAX_RECENT_ACCESSES_TO_KEEP) {
                mRecentAccessTimelines.remove();
            }
        });
    }

    private void flushRendererWorkflowTimeline(long flowId, Timeline timeline) {
        mActiveRendererTimelines.remove(flowId);
        mRecentRendererTimelines.add(timeline);
        if (mRecentRendererTimelines.size() > MAX_RECENT_TIMELINES_TO_KEEP) {
            mRecentRendererTimelines.remove();
        }
    }

    /** Writes recent activity in a log format to the PrintWriter. */
    public void dump(PrintWriter fout, Runnable onComplete) {
        mExecutor.execute(() -> {
            fout.write("Recent Hint Workflows\n");
            fout.write("=====================\n");
            dumpTimelines(fout, List.of(mRecentRefinerTimelines, mActiveRefinerTimelines.values()));

            fout.write("Recent Insight Workflows\n");
            fout.write("========================\n");
            dumpTimelines(fout,
                    List.of(mRecentRendererTimelines, mActiveRendererTimelines.values()));

            fout.write("Recent Access Checks\n");
            fout.write("====================\n");
            for (Timeline timeline : mRecentAccessTimelines) {
                timeline.dump(fout);
            }
            fout.write("\n");

            onComplete.run();
        });
    }

    private void dumpTimelines(PrintWriter fout, Collection<Collection<Timeline>> timelineLists) {
        boolean first = true;
        for (Collection<Timeline> timelines : timelineLists) {
            for (Timeline timeline : timelines) {
                if (first) {
                    first = false;
                } else {
                    fout.write("---------------------\n");
                }
                timeline.dump(fout);
            }
        }
        fout.write("\n");
    }

    private static final class Timeline {
        private final List<String> mDetails = new ArrayList<>();

        public void addStringDetail(String pattern, Object... args) {
            mDetails.add(String.format(pattern, args));
        }

        public void addHintListDetail(Collection<PublishedContextHint> hints) {
            for (PublishedContextHint hint : hints) {
                addStringDetail(
                        "  Hint: %s - %s",
                        hint.getContextHint().getClass().getSimpleName(),
                        hint.getContextHint().getHintId());
            }
        }

        public void addComponentDetail(Component component) {
            addStringDetail("  Component: %s", component);
        }

        public void addThrowableDetail(Throwable t) {
            addStringDetail("  Exception: %s", t.getMessage());
        }

        public void addInsightDetail(PublishedContextInsight insight) {
            addStringDetail(
                    "  Insight: %s - %s (%s source hints)",
                    insight.getClass().getSimpleName(),
                    insight.getInsight().getInsightId(),
                    insight.getInsight().getOriginHints().size());

            addHintListDetail(insight.getInsight().getOriginHints());
        }

        public void dump(PrintWriter fout) {
            for (String detail : mDetails) {
                fout.write(detail + "\n");
            }
        }
    }
}
