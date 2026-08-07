package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.BlackListRuleSnapshot;
import org.cleargram.api.BootstrapConfiguration;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseBootstrap;
import org.telegram.messenger.DispatchQueue;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class TelegramBlackListManagementGatewayTest {

    private DispatchQueue queue;

    @Before
    public void setUp() throws Exception {
        resetBootstrap();
        NoiseBootstrap.initialize(new BootstrapConfiguration());
        queue = new DispatchQueue("TelegramBlackListManagementGatewayTest");
    }

    @After
    public void tearDown() throws Exception {
        queue.recycle();
        resetBootstrap();
    }

    @Test
    public void globalActionOperationsRunAsynchronouslyAndReturnOnUiThread() throws Exception {
        TelegramBlackListManagementGateway gateway = new TelegramBlackListManagementGateway(NoiseBootstrap.getCore(), queue);

        Probe<NoiseAction> initial = new Probe<>();
        gateway.getAction(initial);
        assertEquals(NoiseAction.COLLAPSE, initial.awaitSuccess());
        assertTrue(initial.callbackOnUiThread.get());

        Probe<Void> set = new Probe<>();
        gateway.setAction(NoiseAction.HIDE, set);
        set.awaitSuccess();
        assertTrue(set.callbackOnUiThread.get());

        Probe<BlackListRuleSnapshot> add = new Probe<>();
        gateway.addRule("rule", add);
        BlackListRuleSnapshot rule = add.awaitSuccess();
        assertEquals(NoiseAction.HIDE, rule.getAction());
        assertTrue(rule.isEnabled());

        Probe<Void> invalid = new Probe<>();
        gateway.setAction(NoiseAction.ALLOW, invalid);
        invalid.awaitFailure();
        assertTrue(invalid.callbackOnUiThread.get());
    }

    private static void resetBootstrap() throws Exception {
        Field initialized = NoiseBootstrap.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        initialized.setBoolean(null, false);
        Field core = NoiseBootstrap.class.getDeclaredField("core");
        core.setAccessible(true);
        core.set(null, null);
    }

    private static final class Probe<T> implements TelegramBlackListManagementGateway.Callback<T> {
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<RuntimeException> error = new AtomicReference<>();
        final AtomicReference<Boolean> callbackOnUiThread = new AtomicReference<>(false);

        @Override public void onSuccess(T result) {
            this.result.set(result);
            callbackOnUiThread.set(Looper.myLooper() == Looper.getMainLooper());
            terminal.countDown();
        }

        @Override public void onFailure(RuntimeException error) {
            this.error.set(error);
            callbackOnUiThread.set(Looper.myLooper() == Looper.getMainLooper());
            terminal.countDown();
        }

        T awaitSuccess() throws Exception {
            assertTrue("Timed out", terminal.await(15, TimeUnit.SECONDS));
            if (error.get() != null) throw error.get();
            return result.get();
        }

        void awaitFailure() throws Exception {
            assertTrue("Timed out", terminal.await(15, TimeUnit.SECONDS));
            assertTrue(error.get() != null);
        }
    }
}
