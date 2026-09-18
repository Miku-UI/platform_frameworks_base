/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.platform.test.ravenwood;

import static com.android.ravenwood.common.RavenwoodInternalUtils.RAVENWOOD_VERBOSE_LOGGING;

import android.platform.test.annotations.internal.InnerRunner;
import android.util.Log;

import androidx.test.internal.runner.junit3.AndroidJUnit3Builder;
import androidx.test.internal.runner.junit4.AndroidJUnit4Builder;

import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;

import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.InvalidOrderingException;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Orderable;
import org.junit.runner.manipulation.Orderer;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.TestClass;

import java.util.Arrays;
import java.util.List;

abstract class RavenwoodAwareTestRunnerBase extends Runner implements Filterable, Orderable {
    public static final String TAG = RavenwoodInternalUtils.TAG;

    private final List<RunnerBuilder> mRunnerBuilders = Arrays.asList(
            new RavenwoodAnnotatedBuilder(),
            junit3Builder(),
            new AndroidJUnit4Builder(0));

    static class RavenwoodAnnotatedBuilder extends RunnerBuilder {
        @Override
        public Runner runnerForClass(Class<?> testClass) throws Exception {
            final InnerRunner innerRunnerAnnotation = testClass.getAnnotation(InnerRunner.class);
            if (innerRunnerAnnotation != null) {
                var clazz = innerRunnerAnnotation.value();
                if (RAVENWOOD_VERBOSE_LOGGING) {
                    Log.v(TAG, "Initializing the inner runner: " + clazz);
                }
                try {
                    try {
                        return clazz.getConstructor(Class.class).newInstance(testClass);
                    } catch (NoSuchMethodException ignored) {
                        return clazz.getConstructor(Class.class, RunnerBuilder.class)
                                .newInstance(testClass, new AllDefaultPossibilitiesBuilder());
                    }
                } catch (Exception e) {
                    throw logAndFail("Failed to instantiate " + clazz, e);
                }
            }
            return null;
        }
    }

    RunnerBuilder junit3Builder() {
        return new AndroidJUnit3Builder(0);
    }

    static Error logAndFail(String message, Throwable exception) {
        Log.e(TAG, message, exception);
        return new AssertionError(message, exception);
    }

    final Runner instantiateRealRunner(TestClass testClass) {
        try {
            for (RunnerBuilder each : mRunnerBuilders) {
                Runner runner = each.runnerForClass(testClass.getJavaClass());
                if (runner != null) {
                    return runner;
                }
            }
            return null;
        } catch (Throwable e) {
            SneakyThrow.sneakyThrow(e);
            throw new RuntimeException();
        }
    }

    abstract Runner getRealRunner();

    @Override
    public final Description getDescription() {
        return getRealRunner().getDescription();
    }

    @Override
    public final void filter(Filter filter) throws NoTestsRemainException {
        if (getRealRunner() instanceof Filterable r) {
            r.filter(filter);
        }
    }

    @Override
    public final void order(Orderer orderer) throws InvalidOrderingException {
        if (getRealRunner() instanceof Orderable r) {
            r.order(orderer);
        }
    }

    @Override
    public final void sort(Sorter sorter) {
        if (getRealRunner() instanceof Sortable r) {
            r.sort(sorter);
        }
    }
}
