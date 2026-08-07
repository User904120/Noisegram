package org.cleargram.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.WhiteListStorageRecord;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.BlackListStorageRecord;
import org.cleargram.spi.BlackListStorageState;
import org.cleargram.spi.DuplicateVideoStoragePort;
import org.cleargram.spi.DuplicateVideoStorageClassification;
import org.cleargram.spi.DuplicateVideoStorageSettings;

public final class NoiseBootstrapPersistentCompositionTest {

    private static BootstrapConfiguration persistentConfiguration(
            WhiteListStoragePort whiteListStoragePort,
            BlackListStoragePort blackListStoragePort,
            DuplicateVideoStoragePort duplicateVideoStoragePort
    ) {
        return BootstrapConfiguration.withRuleStorage(
                whiteListStoragePort, blackListStoragePort, duplicateVideoStoragePort);
    }

    @Before
    public void setUp() throws Exception {
        resetBootstrap();
    }

    @After
    public void tearDown() throws Exception {
        resetBootstrap();
    }

    @Test
    public void defaultConfigurationUsesNoStoragePort() {
        BootstrapConfiguration configuration = new BootstrapConfiguration();
        assertEquals(null, configuration.getWhiteListStoragePort());
        assertEquals(null, configuration.getBlackListStoragePort());
        assertEquals(null, configuration.getDuplicateVideoStoragePort());
    }

    @Test(expected = NullPointerException.class)
    public void persistentConfigurationRequiresStoragePort() {
        persistentConfiguration(null, new FakeBlackListStorage(), new FakeDuplicateVideoStorage());
    }

    @Test(expected = NullPointerException.class)
    public void persistentConfigurationRequiresBlackListStoragePort() {
        persistentConfiguration(new FakeStorage(), null, new FakeDuplicateVideoStorage());
    }

    @Test(expected = NullPointerException.class)
    public void persistentConfigurationRequiresDuplicateVideoStoragePort() {
        persistentConfiguration(new FakeStorage(), new FakeBlackListStorage(), null);
    }

    @Test
    public void legacyWhiteListOnlyPersistentConfigurationIsAbsent() {
        for (java.lang.reflect.Method method : BootstrapConfiguration.class.getDeclaredMethods()) {
            assertFalse("Legacy white-list-only factory must not remain",
                    "withWhiteListStorage".equals(method.getName()));
        }
    }

    @Test
    public void persistentConfigurationRetainsStoragePortForComposition() {
        FakeStorage storage = new FakeStorage();
        FakeBlackListStorage blackListStorage = new FakeBlackListStorage();
        FakeDuplicateVideoStorage duplicateVideoStorage = new FakeDuplicateVideoStorage();
        BootstrapConfiguration configuration = persistentConfiguration(
                storage, blackListStorage, duplicateVideoStorage);

        assertSame(storage, configuration.getWhiteListStoragePort());
        assertSame(blackListStorage, configuration.getBlackListStoragePort());
        assertSame(duplicateVideoStorage, configuration.getDuplicateVideoStoragePort());
    }

    @Test
    public void defaultBootstrapPreservesInMemoryBehavior() {
        NoiseBootstrap.initialize(new BootstrapConfiguration());

        NoiseCore core = NoiseBootstrap.getCore();
        assertTrue(NoiseBootstrap.isInitialized());
        assertNotNull(core);
        assertEquals(0, core.getWhiteListRules().size());
        core.addWhiteListRule("important", true);
        assertEquals(1, core.getWhiteListRules().size());
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("important")).getAction());
    }

    @Test
    public void defaultInMemoryCoreEvaluationIsUnchanged() {
        NoiseBootstrap.initialize(new BootstrapConfiguration());

        assertEquals(NoiseAction.ALLOW,
                NoiseBootstrap.getCore().evaluate(new NoiseMessage("message")).getAction());
    }

    @Test
    public void persistentBootstrapStartsLoadExactlyOnceWithoutWaiting() {
        FakeStorage storage = new FakeStorage();

        NoiseBootstrap.initialize(persistentConfiguration(storage, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));

        NoiseCore core = NoiseBootstrap.getCore();
        assertTrue(NoiseBootstrap.isInitialized());
        assertEquals(1, storage.loadCallCount);
        assertNotNull(storage.callback);
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("important")).getAction());
        expectIllegalState(core::getWhiteListRules);
    }

    @Test
    public void persistentCompositionStartsAllThreeLoadsBeforeAnyTerminalCallback() {
        FakeStorage whiteListStorage = new FakeStorage();
        FakeBlackListStorage blackListStorage = new FakeBlackListStorage();
        FakeDuplicateVideoStorage duplicateVideoStorage = new FakeDuplicateVideoStorage();

        NoiseBootstrap.initialize(persistentConfiguration(
                whiteListStorage, blackListStorage, duplicateVideoStorage));

        assertTrue(NoiseBootstrap.isInitialized());
        assertNotNull(NoiseBootstrap.getCore());
        assertEquals(1, whiteListStorage.loadCallCount);
        assertEquals(1, blackListStorage.loadCallCount);
        assertEquals(1, duplicateVideoStorage.loadCallCount);
        assertNotNull(whiteListStorage.callback);
        assertNotNull(blackListStorage.callback);
        assertNotNull(duplicateVideoStorage.callback);

        whiteListStorage.completeLoad(java.util.Collections.<WhiteListStorageRecord>emptyList());
        blackListStorage.completeLoad(new BlackListStorageState(
                NoiseAction.COLLAPSE, java.util.Collections.<BlackListStorageRecord>emptyList()));
        duplicateVideoStorage.completeLoad(new DuplicateVideoStorageSettings(0, 1));
        assertEquals(DuplicateVideoRuntimeStatus.DISABLED,
                NoiseBootstrap.getCore().getDuplicateVideoState().getStatus());
    }

    @Test
    public void persistentCoreFailsOpenWhileLoading() {
        FakeStorage storage = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(storage, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));

        assertNotNull(storage.callback);
        assertEquals(NoiseAction.ALLOW,
                NoiseBootstrap.getCore().evaluate(new NoiseMessage("pending")).getAction());
        expectIllegalState(NoiseBootstrap.getCore()::getWhiteListRules);
    }

    @Test
    public void persistentBootstrapBecomesReadyAfterSuccessfulLoad() {
        FakeStorage storage = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(storage, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));

        storage.completeLoad(Arrays.asList(
                new WhiteListStorageRecord("enabled", true),
                new WhiteListStorageRecord("disabled", false)));

        NoiseCore core = NoiseBootstrap.getCore();
        assertEquals(1, storage.loadCallCount);
        assertEquals(2, core.getWhiteListRules().size());
        assertEquals("enabled", core.getWhiteListRules().get(0).getCanonicalPattern());
        assertTrue(core.getWhiteListRules().get(0).isEnabled());
        assertEquals("disabled", core.getWhiteListRules().get(1).getCanonicalPattern());
        assertFalse(core.getWhiteListRules().get(1).isEnabled());
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("enabled")).getAction());
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("disabled")).getAction());
    }

    @Test
    public void persistentCoreEvaluatesNormallyAfterReady() {
        FakeStorage storage = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(storage, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));
        NoiseCore core = NoiseBootstrap.getCore();

        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("enabled")).getAction());
        storage.completeLoad(Arrays.asList(new WhiteListStorageRecord("enabled", true)));

        assertEquals(1, core.getWhiteListRules().size());
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("enabled")).getAction());
    }

    @Test
    public void persistentBootstrapFailureKeepsCorePublishedAndFailsOpen() {
        FakeStorage storage = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(storage, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));
        NoiseCore core = NoiseBootstrap.getCore();

        storage.failLoad(new RuntimeException("load failure"));

        assertSame(core, NoiseBootstrap.getCore());
        assertTrue(NoiseBootstrap.isInitialized());
        assertEquals(1, storage.loadCallCount);
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("important")).getAction());
        expectIllegalState(core::getWhiteListRules);
    }

    @Test
    public void persistentCoreFailsOpenAfterLoadFailure() {
        FakeStorage storage = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(storage, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));
        storage.failLoad(new RuntimeException("load failure"));

        assertEquals(NoiseAction.ALLOW,
                NoiseBootstrap.getCore().evaluate(new NoiseMessage("failed")).getAction());
        expectIllegalState(NoiseBootstrap.getCore()::getWhiteListRules);
    }

    @Test
    public void invalidPersistentLoadDoesNotPublishPartialSnapshot() {
        FakeStorage storage = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(storage, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));
        NoiseCore core = NoiseBootstrap.getCore();

        storage.completeLoad(Arrays.asList(
                new WhiteListStorageRecord("valid", true),
                new WhiteListStorageRecord("valid", false)));

        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("valid")).getAction());
        expectIllegalState(core::getWhiteListRules);
    }

    @Test
    public void repeatedInitializeReturnsExistingCoreAndDoesNotReload() {
        FakeStorage first = new FakeStorage();
        FakeStorage second = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(first, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));
        NoiseCore firstCore = NoiseBootstrap.getCore();

        NoiseBootstrap.initialize(persistentConfiguration(second, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));

        assertSame(firstCore, NoiseBootstrap.getCore());
        assertEquals(1, first.loadCallCount);
        assertEquals(0, second.loadCallCount);
    }

    @Test
    public void repeatedInitializeDuringLoadingDoesNotStartSecondLoad() {
        FakeStorage first = new FakeStorage();
        FakeStorage second = new FakeStorage();
        NoiseBootstrap.initialize(persistentConfiguration(first, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));
        NoiseCore core = NoiseBootstrap.getCore();

        NoiseBootstrap.initialize(persistentConfiguration(second, new FakeBlackListStorage(), new FakeDuplicateVideoStorage()));

        assertSame(core, NoiseBootstrap.getCore());
        assertEquals(1, first.loadCallCount);
        assertEquals(0, second.loadCallCount);
    }

    @Test
    public void persistentCompositionIsFirstCallWinsAcrossAllThreePorts() {
        FakeStorage firstWhite = new FakeStorage();
        FakeBlackListStorage firstBlack = new FakeBlackListStorage();
        FakeDuplicateVideoStorage firstDuplicate = new FakeDuplicateVideoStorage();
        FakeStorage secondWhite = new FakeStorage();
        FakeBlackListStorage secondBlack = new FakeBlackListStorage();
        FakeDuplicateVideoStorage secondDuplicate = new FakeDuplicateVideoStorage();

        NoiseBootstrap.initialize(persistentConfiguration(firstWhite, firstBlack, firstDuplicate));
        NoiseCore firstCore = NoiseBootstrap.getCore();
        NoiseBootstrap.initialize(persistentConfiguration(secondWhite, secondBlack, secondDuplicate));

        assertSame(firstCore, NoiseBootstrap.getCore());
        assertEquals(1, firstWhite.loadCallCount);
        assertEquals(1, firstBlack.loadCallCount);
        assertEquals(1, firstDuplicate.loadCallCount);
        assertEquals(0, secondWhite.loadCallCount);
        assertEquals(0, secondBlack.loadCallCount);
        assertEquals(0, secondDuplicate.loadCallCount);
    }

    @Test
    public void partialPersistentConfigurationsFailBeforeStartingAnyLoad() throws Exception {
        for (int mask = 1; mask < 7; mask++) {
            FakeStorage white = new FakeStorage();
            FakeBlackListStorage black = new FakeBlackListStorage();
            FakeDuplicateVideoStorage duplicate = new FakeDuplicateVideoStorage();
            BootstrapConfiguration configuration = partialConfiguration(mask, white, black, duplicate);

            try {
                NoiseBootstrap.initialize(configuration);
                fail("Expected IllegalArgumentException for partial configuration " + mask);
            } catch (IllegalArgumentException expected) {
                // Every mixed configuration is rejected before storage construction.
            }
            assertFalse(NoiseBootstrap.isInitialized());
            assertEquals(0, white.loadCallCount);
            assertEquals(0, black.loadCallCount);
            assertEquals(0, duplicate.loadCallCount);
        }
    }

    @Test
    public void nullBootstrapConfigurationDoesNotInitialize() {
        try {
            NoiseBootstrap.initialize(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected fail-fast validation.
        }
        assertFalse(NoiseBootstrap.isInitialized());
        expectIllegalState(NoiseBootstrap::getCore);

        NoiseBootstrap.initialize(new BootstrapConfiguration());
        assertTrue(NoiseBootstrap.isInitialized());
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected strict readiness behavior.
        }
    }

    private static void resetBootstrap() throws Exception {
        Field initialized = NoiseBootstrap.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        initialized.setBoolean(null, false);
        Field core = NoiseBootstrap.class.getDeclaredField("core");
        core.setAccessible(true);
        core.set(null, null);
    }

    private static BootstrapConfiguration partialConfiguration(
            int mask,
            WhiteListStoragePort whiteListStoragePort,
            BlackListStoragePort blackListStoragePort,
            DuplicateVideoStoragePort duplicateVideoStoragePort
    ) throws Exception {
        Constructor<BootstrapConfiguration> constructor = BootstrapConfiguration.class.getDeclaredConstructor(
                WhiteListStoragePort.class, BlackListStoragePort.class, DuplicateVideoStoragePort.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                (mask & 1) != 0 ? whiteListStoragePort : null,
                (mask & 2) != 0 ? blackListStoragePort : null,
                (mask & 4) != 0 ? duplicateVideoStoragePort : null);
    }

    private static final class FakeStorage implements WhiteListStoragePort {

        private int loadCallCount;
        private LoadCallback callback;

        @Override
        public void loadRules(LoadCallback callback) {
            loadCallCount++;
            this.callback = callback;
        }

        private void completeLoad(List<WhiteListStorageRecord> records) {
            callback.onLoaded(records);
        }

        private void failLoad(Throwable error) {
            callback.onFailed(error);
        }

        @Override
        public void insertRule(WhiteListStorageRecord record) {
        }

        @Override
        public boolean deleteRule(String canonicalPattern) {
            return true;
        }

        @Override
        public boolean updateRuleEnabled(String canonicalPattern, boolean enabled) {
            return true;
        }
    }

    private static final class FakeBlackListStorage implements BlackListStoragePort {

        private int loadCallCount;
        private LoadCallback callback;
        private int insertCallCount;
        private int deleteCallCount;
        private int updateEnabledCallCount;
        private int updateActionCallCount;

        @Override public void loadState(LoadCallback callback) {
            loadCallCount++;
            this.callback = callback;
        }
        @Override public void insertRule(BlackListStorageRecord record) { insertCallCount++; }
        @Override public boolean deleteRule(String canonicalPattern) { deleteCallCount++; return true; }
        @Override public boolean updateRuleEnabled(String canonicalPattern, boolean enabled) {
            updateEnabledCallCount++;
            return true;
        }
        @Override public void updateAction(NoiseAction action) { updateActionCallCount++; }
        private void completeLoad(BlackListStorageState state) { callback.onLoaded(state); }
        private void failLoad(Throwable error) { callback.onFailed(error); }
    }

    private static final class FakeDuplicateVideoStorage implements DuplicateVideoStoragePort {

        private int loadCallCount;
        private LoadCallback callback;
        private int setEnabledCallCount;
        private int setMatchModeCallCount;
        private int classifyCallCount;
        private int clearHistoryCallCount;

        @Override public void load(LoadCallback callback) {
            loadCallCount++;
            this.callback = callback;
        }
        @Override public void setEnabled(boolean enabled) { setEnabledCallCount++; }
        @Override public void setMatchMode(int mode) { setMatchModeCallCount++; }
        @Override public List<DuplicateVideoStorageClassification> classifyOrdered(
                int mode, int version, List<byte[]> keys) {
            classifyCallCount++;
            return java.util.Collections.nCopies(
                    keys.size(), DuplicateVideoStorageClassification.FIRST_SEEN);
        }
        @Override public void clearHistory() { clearHistoryCallCount++; }
        private void completeLoad(DuplicateVideoStorageSettings settings) { callback.onLoaded(settings); }
        private void failLoad(Throwable error) { callback.onFailed(error); }
    }
}
