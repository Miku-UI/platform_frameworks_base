package com.android.server.locksettings;

import static org.mockito.Mockito.mock;

import android.hardware.weaver.IWeaver;
import android.hardware.weaver.WeaverConfig;
import android.hardware.weaver.WeaverReadResponse;
import android.hardware.weaver.WeaverReadStatus;
import android.os.IBinder;
import android.os.RemoteException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;

public class MockWeaverService implements IWeaver {

    private static final int MAX_SLOTS = 8;
    private static final int KEY_LENGTH = 256 / 8;
    private static final int VALUE_LENGTH = 256 / 8;

    private static class WeaverSlot {
        public byte[] key;
        public byte[] value;
        public int failureCounter;
    }

    private final WeaverSlot[] mSlots = new WeaverSlot[MAX_SLOTS];
    private WeaverReadResponse mInjectedReadResponse;

    public MockWeaverService() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            mSlots[i] = new WeaverSlot();
        }
    }

    @Override
    public WeaverConfig getConfig() throws RemoteException {
        WeaverConfig config = new WeaverConfig();
        config.keySize = KEY_LENGTH;
        config.valueSize = VALUE_LENGTH;
        config.slots = MAX_SLOTS;
        return config;
    }

    @Override
    public void write(int slotId, byte[] key, byte[] value) throws RemoteException {
        if (slotId < 0 || slotId >= MAX_SLOTS) {
            throw new RuntimeException("Invalid slot id");
        }
        WeaverSlot slot = mSlots[slotId];
        slot.key = key.clone();
        slot.value = value.clone();
        slot.failureCounter = 0;
    }

    @Override
    public WeaverReadResponse read(int slotId, byte[] key) throws RemoteException {
        if (slotId < 0 || slotId >= MAX_SLOTS) {
            throw new RuntimeException("Invalid slot id");
        }
        WeaverReadResponse response = mInjectedReadResponse;
        if (response != null) {
            mInjectedReadResponse = null;
            return response;
        }
        WeaverSlot slot = mSlots[slotId];
        response = new WeaverReadResponse();
        if (Arrays.equals(key, slot.key)) {
            response.value = slot.value.clone();
            response.status = WeaverReadStatus.OK;
            slot.failureCounter = 0;
        } else {
            response.status = WeaverReadStatus.INCORRECT_KEY;
            slot.failureCounter++;
        }
        return response;
    }

    @Override
    public String getInterfaceHash() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInterfaceVersion() {
        return 2;
    }

    @Override
    public IBinder asBinder() {
        return mock(IBinder.class);
    }

    public int getSumOfFailureCounters() {
        return Arrays.stream(mSlots).mapToInt(slot -> slot.failureCounter).sum();
    }

    /** Returns an adapter object that implements the old (HIDL) Weaver interface. */
    public android.hardware.weaver.V1_0.IWeaver.Stub asHidl() {
        return new MockWeaverServiceHidlAdapter();
    }

    /** Injects a response to be returned by the next {@link #read(int, byte[])}. */
    public void injectReadResponse(int status, Duration timeout) {
        WeaverReadResponse response = new WeaverReadResponse();
        response.status = status;
        response.timeout = timeout.toMillis();
        mInjectedReadResponse = response;
    }

    private class MockWeaverServiceHidlAdapter extends android.hardware.weaver.V1_0.IWeaver.Stub {

        @Override
        public void getConfig(getConfigCallback cb) throws RemoteException {
            WeaverConfig aidlConfig = MockWeaverService.this.getConfig();
            android.hardware.weaver.V1_0.WeaverConfig hidlConfig =
                    new android.hardware.weaver.V1_0.WeaverConfig();
            hidlConfig.keySize = aidlConfig.keySize;
            hidlConfig.valueSize = aidlConfig.valueSize;
            hidlConfig.slots = aidlConfig.slots;
            cb.onValues(android.hardware.weaver.V1_0.WeaverStatus.OK, hidlConfig);
        }

        @SuppressWarnings("NonApiType")
        @Override
        public int write(int slotId, ArrayList<Byte> key, ArrayList<Byte> value)
                throws RemoteException {
            MockWeaverService.this.write(slotId, fromByteArrayList(key), fromByteArrayList(value));
            return android.hardware.weaver.V1_0.WeaverStatus.OK;
        }

        @SuppressWarnings("NonApiType")
        @Override
        public void read(int slotId, ArrayList<Byte> key, readCallback cb) throws RemoteException {
            WeaverReadResponse aidlResponse =
                    MockWeaverService.this.read(slotId, fromByteArrayList(key));
            android.hardware.weaver.V1_0.WeaverReadResponse hidlResponse =
                    new android.hardware.weaver.V1_0.WeaverReadResponse();
            int hidlStatus = switch (aidlResponse.status) {
                case WeaverReadStatus.OK -> android.hardware.weaver.V1_0.WeaverStatus.OK;
                case WeaverReadStatus.INCORRECT_KEY ->
                        android.hardware.weaver.V1_0.WeaverReadStatus.INCORRECT_KEY;
                case WeaverReadStatus.THROTTLE ->
                        android.hardware.weaver.V1_0.WeaverReadStatus.THROTTLE;
                default -> android.hardware.weaver.V1_0.WeaverStatus.FAILED;
            };
            if (aidlResponse.value != null) {
                hidlResponse.value = toByteArrayList(aidlResponse.value);
            }
            cb.onValues(hidlStatus, hidlResponse);
        }

        @SuppressWarnings("NonApiType")
        private static ArrayList<Byte> toByteArrayList(byte[] data) {
            ArrayList<Byte> result = new ArrayList<Byte>(data.length);
            for (int i = 0; i < data.length; i++) {
                result.add(data[i]);
            }
            return result;
        }

        @SuppressWarnings("NonApiType")
        private static byte[] fromByteArrayList(ArrayList<Byte> data) {
            byte[] result = new byte[data.size()];
            for (int i = 0; i < data.size(); i++) {
                result[i] = data.get(i);
            }
            return result;
        }
    }
}
