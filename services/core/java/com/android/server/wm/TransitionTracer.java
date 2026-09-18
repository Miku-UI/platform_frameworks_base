package com.android.server.wm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.window.TransitionInfo;

import java.io.PrintWriter;

interface TransitionTracer {
    void logSentTransition(Transition transition, TransitionInfo info);
    void logFinishedTransition(Transition transition);
    void logAbortedTransition(Transition transition);
    void logRemovingStartingWindow(@NonNull StartingData startingData);

    void startTrace(@Nullable PrintWriter pw);
    void stopTrace(@Nullable PrintWriter pw);
    boolean isTracing();
    void saveForBugreport(@Nullable PrintWriter pw);
}
