/*
 * Copyright 2017 The Android Open Source Project
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

package android.conscrypt;

import static org.conscrypt.TestUtils.newTextMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import android.conscrypt.ServerEndpoint.MessageProcessor;
import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;
import androidx.test.filters.LargeTest;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.conscrypt.ChannelType;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

/**
 * Benchmark for comparing performance of server socket implementations.
 */
@RunWith(JUnitParamsRunner.class)
@LargeTest
public final class ServerSocketPerfTest {

    private static class RetryRule implements TestRule {
        private final int retryCount;

        RetryRule(int retryCount) {
            this.retryCount = retryCount;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Throwable caughtThrowable = null;
                    for (int i = 0; i < retryCount; i++) {
                        try {
                            base.evaluate();
                            return;
                        } catch (Throwable t) {
                            caughtThrowable = t;
                        }
                    }
                    if (caughtThrowable != null) {
                        throw caughtThrowable;
                    }
                }
            };
        }
    }

    @Rule
    public PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    @Rule
    public final TestRule retryRule = new RetryRule(3);

    /**
     * Provider for the benchmark configuration
     */
    private class Config {
        EndpointFactory a_clientFactory;
        EndpointFactory b_serverFactory;
        int c_messageSize;
        String d_cipher;
        ChannelType e_channelType;
        Config(EndpointFactory clientFactory,
            EndpointFactory serverFactory,
            int messageSize,
            String cipher,
            ChannelType channelType) {
            a_clientFactory = clientFactory;
            b_serverFactory = serverFactory;
            c_messageSize = messageSize;
            d_cipher = cipher;
            e_channelType = channelType;
        }
        public EndpointFactory clientFactory() {
            return a_clientFactory;
        }

        public EndpointFactory serverFactory() {
            return b_serverFactory;
        }

        public int messageSize() {
            return c_messageSize;
        }

        public String cipher() {
            return d_cipher;
        }

        public ChannelType channelType() {
            return e_channelType;
        }
    }

    public Collection<Object[]> getParams() {
        final List<Object[]> params = new ArrayList<>();
        for (EndpointFactory endpointFactory : EndpointFactory.values()) {
            for (ChannelType channelType : ChannelType.values()) {
                for (int messageSize : ConscryptParams.messageSizes) {
                    for (String cipher : ConscryptParams.ciphers) {
                        params.add(new Object[] {new Config(endpointFactory, endpointFactory,
                                messageSize, cipher, channelType)});
                    }
                }
            }
        }
        return params;
    }

    private SocketPair socketPair = new SocketPair();
    private ExecutorService executor;
    private Future<?> receivingFuture;
    private volatile boolean stopping;
    private static final AtomicLong bytesCounter = new AtomicLong();
    private AtomicBoolean recording = new AtomicBoolean();

    private static class SocketPair implements AutoCloseable {
        public ClientEndpoint client;
        public ServerEndpoint server;

        SocketPair() {
            client = null;
            server = null;
        }

        @Override
        public void close() {
            if (client != null) {
                client.stop();
            }
            if (server != null) {
                server.stop();
            }
        }
    }

    private void setup(final Config config) throws Exception {
        recording.set(false);
        stopping = false;

        byte[] message = newTextMessage(config.messageSize());

        socketPair.server = config.serverFactory().newServer(
                config.messageSize(), new String[] {"TLSv1.3", "TLSv1.2"}, ciphers(config));
        socketPair.server.init();
        socketPair.server.setMessageProcessor(new MessageProcessor() {
            @Override
            public void processMessage(byte[] inMessage, int numBytes, OutputStream os) {
                try {
                    while (!stopping) {
                        os.write(inMessage, 0, numBytes);
                    }
                } catch (SSLException e) {
                    String msg = e.getMessage();
                    if (msg == null || !msg.contains("Connection reset by peer")) {
                        throw new RuntimeException(e);
                    }
                } catch (IOException e) {
                    if (!stopping) {
                        throw new RuntimeException(e);
                    }
                } finally {
                    try {
                        os.flush();
                    } catch (IOException e) {
                    }
                }
            }
        });

        Future<?> connectedFuture = socketPair.server.start();

        socketPair.client =
                config.clientFactory().newClient(ChannelType.CHANNEL, socketPair.server.port(),
                        new String[] {"TLSv1.3", "TLSv1.2"}, ciphers(config));
        socketPair.client.start();

        // Wait for the initial connection to complete.
        connectedFuture.get(5, TimeUnit.SECONDS);

        // Start the server-side streaming by sending a message to the server.
        socketPair.client.sendMessage(message);
        socketPair.client.flush();

        executor = Executors.newSingleThreadExecutor();
        receivingFuture = executor.submit(new Runnable() {
            @Override
            public void run() {
                Thread thread = Thread.currentThread();
                byte[] buffer = new byte[config.messageSize()];
                while (!stopping && !thread.isInterrupted()) {
                    try {
                        int numBytes = socketPair.client.readMessage(buffer);
                        if (numBytes < 0) {
                            return;
                        }
                        assertEquals(config.messageSize(), numBytes);

                        if (recording.get()) {
                            bytesCounter.addAndGet(numBytes);
                        }
                    } catch (Exception e) {
                        if (!stopping) {
                            fail("Client read failed: " + e.getMessage());
                        }
                        return;
                    }
                }
            }
        });
    }

    void close() throws Exception {
        stopping = true;

        if (executor != null) {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        if (receivingFuture != null) {
            try {
                receivingFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Ignore exceptions here, as the task may have been interrupted.
            }
        }

        if (socketPair != null) {
            socketPair.close();
        }
    }

    @Test
    @Parameters(method = "getParams")
    public void throughput(Config config) throws Exception {
        try {
            setup(config);
            BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
            while (state.keepRunning()) {
                recording.set(true);
                while (bytesCounter.get() < config.messageSize()) {
                    Thread.yield();
                }
                bytesCounter.set(0);
                recording.set(false);
            }
        } finally {
            close();
        }
    }

    @After
    public void tearDown() throws Exception {
        close();
    }

    private String[] ciphers(Config config) {
        return new String[] {config.cipher()};
    }
}