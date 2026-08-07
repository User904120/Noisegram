package org.cleargram.storage.telegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.NoiseAction;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.util.Arrays;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class CleargramDatabaseTest {

    private static final String DEFAULT_BLACK_LIST_SEED_VERSION_KEY = "default_black_list_seed_version";
    private static final String DEFAULT_BLACK_LIST_PENDING_SUFFIX = ".default-black-list-v1.pending";
    private static final String FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY = "fresh_filter_defaults_seed_version";
    private static final String FRESH_FILTER_DEFAULTS_PENDING_SUFFIX = ".fresh-filter-defaults-v1.pending";
    private static final String[] FRESH_FILTER_DEFAULTS_KEYS = {
            "hide_reactions",
            "duplicate_video_enabled",
            "hide_channel_end_advertisement",
            "hide_fullscreen_video_advertisement",
            "hide_channel_pinned_message_header",
            "message_cta_button_enabled"
    };
    private static final String[] DEFAULT_BLACK_LIST_PATTERNS = {
            "#реклама", "рекламодатель", "erid:", "ставки", "каппер"
    };

    @Test
    public void testFreshDatabaseCreatesVersionFourSchemaAndDefaultBlackList() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            assertDefaultBlackListRows(fixture.loadBlackRows());
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(4, raw.executeInt("PRAGMA user_version").intValue());
                assertTrue(raw.tableExists("white_list_rules"));
                assertTrue(raw.tableExists("black_list_rules"));
                assertTrue(raw.tableExists("settings"));
                assertTrue(raw.tableExists("duplicate_video_history"));
                assertEquals(0, raw.executeInt("SELECT count(*) FROM white_list_rules").intValue());
                assertEquals(5, raw.executeInt("SELECT count(*) FROM black_list_rules").intValue());
                assertEquals("2", querySingleString(raw, "SELECT value FROM settings WHERE key = 'black_list_action'"));
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = '" + DEFAULT_BLACK_LIST_SEED_VERSION_KEY + "'"));
                assertFreshFilterDefaults(raw);
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = '"
                        + FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY + "'"));
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_match_mode'"));
                assertEquals(0, raw.executeInt("SELECT count(*) FROM duplicate_video_history").intValue());
                assertSqlFails(raw, "INSERT INTO black_list_rules VALUES('bad_action', 3, 1, 1)");
                assertSqlFails(raw, "INSERT INTO black_list_rules VALUES('bad_enabled', 1, 2, 1)");
                assertSqlFails(raw, "INSERT INTO black_list_rules VALUES('bad_order', 1, 1, 0)");
            });
            assertFalse(pendingFile(fixture).exists());
            assertFalse(freshFilterDefaultsPendingFile(fixture).exists());
        } finally { fixture.close(); }
    }

    @Test
    public void testPendingVersionFourDatabaseRecoversFreshFilterDefaults() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(CleargramDatabaseTest::createVersionFourSchema);
            assertTrue(freshFilterDefaultsPendingFile(fixture).createNewFile());
            fixture.open();
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(4, raw.executeInt("PRAGMA user_version").intValue());
                assertFreshFilterDefaults(raw);
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = '"
                        + FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY + "'"));
                assertEquals("2", querySingleString(raw, "SELECT value FROM settings WHERE key = 'black_list_action'"));
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_match_mode'"));
            });
            assertFalse(freshFilterDefaultsPendingFile(fixture).exists());
        } finally { fixture.close(); }
    }

    @Test
    public void testFreshFilterDefaultsSeedRollsBackAsOneTransaction() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                createVersionFourSchema(raw);
                execute(raw, "CREATE TRIGGER reject_fresh_filter_defaults_seed BEFORE INSERT ON settings WHEN NEW.key = '"
                        + FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY + "' BEGIN SELECT RAISE(ABORT, 'seed failure'); END");
            });
            assertTrue(freshFilterDefaultsPendingFile(fixture).createNewFile());
            fixture.assertOpenFails(SQLiteException.class);
            fixture.assertRaw(raw -> {
                assertEquals("0", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_enabled'"));
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key IN ('hide_reactions', 'hide_channel_end_advertisement', 'hide_fullscreen_video_advertisement', 'hide_channel_pinned_message_header', 'message_cta_button_enabled', '"
                        + FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY + "')").intValue());
            });
            assertTrue(freshFilterDefaultsPendingFile(fixture).exists());
        } finally { fixture.close(); }
    }

    @Test
    public void testCommittedFreshFilterDefaultsDoNotOverwriteUserValueWhenMarkerRemains() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.close());
            first.assertRaw(raw -> execute(raw, "UPDATE settings SET value = '0' WHERE key = 'hide_reactions'"));
            assertTrue(freshFilterDefaultsPendingFile(first).createNewFile());
            first.recycleQueue();

            second = first.reopen();
            Fixture reopened = second;
            reopened.open();
            reopened.run(() -> reopened.database.close());
            reopened.assertRaw(raw -> {
                assertEquals("0", querySingleString(raw, "SELECT value FROM settings WHERE key = 'hide_reactions'"));
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = '"
                        + FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY + "'"));
            });
            assertFalse(freshFilterDefaultsPendingFile(reopened).exists());
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testDefaultBlackListDoesNotDuplicateAfterReopen() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.close());
            first.recycleQueue();

            second = first.reopen();
            second.open();
            assertDefaultBlackListRows(second.loadBlackRows());
            assertFalse(pendingFile(second).exists());
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testExistingEmptyVersionFourDatabaseIsNotSeeded() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> createVersionFourSchema(raw));
            fixture.open();
            assertTrue(fixture.loadBlackRows().isEmpty());
            assertNull(fixture.loadSetting(DEFAULT_BLACK_LIST_SEED_VERSION_KEY));
            assertNull(fixture.loadSetting(FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY));
            assertEquals("0", fixture.loadSetting("duplicate_video_enabled"));
            for (String key : FRESH_FILTER_DEFAULTS_KEYS) {
                if (!"duplicate_video_enabled".equals(key)) {
                    assertNull(fixture.loadSetting(key));
                }
            }
            assertFalse(pendingFile(fixture).exists());
            assertFalse(freshFilterDefaultsPendingFile(fixture).exists());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testDeletedDefaultBlackListRulesAreNotRestoredAfterReopen() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> {
                for (String pattern : DEFAULT_BLACK_LIST_PATTERNS) {
                    assertTrue(first.database.deleteBlackListRow(pattern));
                }
                first.database.close();
            });
            first.recycleQueue();

            second = first.reopen();
            second.open();
            assertTrue(second.loadBlackRows().isEmpty());
            assertEquals("1", second.loadSetting(DEFAULT_BLACK_LIST_SEED_VERSION_KEY));
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testPendingVersionFourDatabaseCompletesDefaultBlackListSeed() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> createVersionFourSchema(raw));
            assertTrue(pendingFile(fixture).createNewFile());
            fixture.open();
            assertDefaultBlackListRows(fixture.loadBlackRows());
            assertEquals("1", fixture.loadSetting(DEFAULT_BLACK_LIST_SEED_VERSION_KEY));
            assertFalse(pendingFile(fixture).exists());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testPendingMarkerAfterCommittedSeedDoesNotDuplicateRules() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.close());
            assertTrue(pendingFile(first).createNewFile());
            first.recycleQueue();

            second = first.reopen();
            second.open();
            assertDefaultBlackListRows(second.loadBlackRows());
            assertEquals("1", second.loadSetting(DEFAULT_BLACK_LIST_SEED_VERSION_KEY));
            assertFalse(pendingFile(second).exists());
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testPendingDatabaseWithRulesWithoutSeedMarkerFailsWithoutMutation() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                createVersionFourSchema(raw);
                execute(raw, "INSERT INTO black_list_rules VALUES('existing', 2, 1, 1)");
            });
            assertTrue(pendingFile(fixture).createNewFile());
            fixture.assertOpenFails(IllegalStateException.class);
            fixture.assertRaw(raw -> {
                assertEquals(1, raw.executeInt("SELECT count(*) FROM black_list_rules WHERE canonical_pattern = 'existing'").intValue());
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key = '" + DEFAULT_BLACK_LIST_SEED_VERSION_KEY + "'").intValue());
            });
            assertTrue(pendingFile(fixture).exists());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testDefaultBlackListSeedTransactionRollsBackOnInsertFailure() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                createVersionFourSchema(raw);
                execute(raw, "CREATE TRIGGER reject_default_black_list_seed BEFORE INSERT ON black_list_rules WHEN NEW.canonical_pattern = 'erid:' BEGIN SELECT RAISE(ABORT, 'seed failure'); END");
            });
            assertTrue(pendingFile(fixture).createNewFile());
            fixture.assertOpenFails(SQLiteException.class);
            fixture.assertRaw(raw -> {
                assertEquals(0, raw.executeInt("SELECT count(*) FROM black_list_rules").intValue());
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key = '" + DEFAULT_BLACK_LIST_SEED_VERSION_KEY + "'").intValue());
            });
            assertTrue(pendingFile(fixture).exists());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testVersionOneMigrationClearsLegacyRulesAndCreatesDefaultAction() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                execute(raw, "CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL COLLATE BINARY PRIMARY KEY CHECK (length(canonical_pattern) BETWEEN 1 AND 255), enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)), order_key INTEGER NOT NULL UNIQUE CHECK (order_key > 0))");
                execute(raw, "INSERT INTO white_list_rules VALUES('white', 1, 1)");
                execute(raw, "PRAGMA user_version = 1");
            });
            fixture.open();
            fixture.run(() -> {
                fixture.database.insertBlackListRow("first", NoiseAction.HIDE, true);
                fixture.database.insertBlackListRow("second", NoiseAction.COLLAPSE, false);
                assertTrue(fixture.database.deleteBlackListRow("first"));
                fixture.database.insertBlackListRow("first", NoiseAction.HIDE, true);
                List<CleargramDatabase.BlackListRow> rows = fixture.database.loadBlackListRows();
                assertEquals("second", rows.get(0).getCanonicalPattern());
                assertEquals(NoiseAction.COLLAPSE, rows.get(0).getAction());
                assertEquals("first", rows.get(1).getCanonicalPattern());
                assertTrue(fixture.database.setBlackListRowEnabled("second", true));
            });
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(0, raw.executeInt("SELECT count(*) FROM white_list_rules").intValue());
                assertEquals(2, raw.executeInt("SELECT order_key FROM black_list_rules WHERE canonical_pattern = 'second'").intValue());
                assertEquals(3, raw.executeInt("SELECT order_key FROM black_list_rules WHERE canonical_pattern = 'first'").intValue());
                assertEquals("2", querySingleString(raw, "SELECT value FROM settings WHERE key = 'black_list_action'"));
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testVersionTwoMigrationClearsBothLegacyRuleTables() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                createVersionTwoSchema(raw);
                execute(raw, "INSERT INTO white_list_rules VALUES('white', 1, 1)");
                execute(raw, "INSERT INTO black_list_rules VALUES('hide', 1, 1, 1)");
                execute(raw, "INSERT INTO black_list_rules VALUES('collapse', 2, 0, 2)");
                execute(raw, "PRAGMA user_version = 2");
            });

            fixture.open();
            assertEquals(NoiseAction.COLLAPSE, fixture.loadAction());
            assertTrue(fixture.loadRows().isEmpty());
            assertTrue(fixture.loadBlackRows().isEmpty());
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(4, raw.executeInt("PRAGMA user_version").intValue());
                assertEquals("2", querySingleString(raw, "SELECT value FROM settings WHERE key = 'black_list_action'"));
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testBlackListActionPersistsAcrossReopen() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.updateBlackListAction(NoiseAction.HIDE));
            first.run(() -> first.database.close());
            first.recycleQueue();

            second = first.reopen();
            second.open();
            Fixture reopened = second;
            assertEquals(NoiseAction.HIDE, reopened.loadAction());
            reopened.run(() -> reopened.database.updateBlackListAction(NoiseAction.COLLAPSE));
            assertEquals(NoiseAction.COLLAPSE, reopened.loadAction());
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testUpdateBlackListActionUpdatesRowsWithoutChangingOrderOrEnabledState() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> fixture.database.insertBlackListRow("first", NoiseAction.HIDE, true));
            fixture.run(() -> fixture.database.insertBlackListRow("second", NoiseAction.HIDE, false));
            fixture.run(() -> fixture.database.updateBlackListAction(NoiseAction.COLLAPSE));

            List<CleargramDatabase.BlackListRow> rows = fixture.loadBlackRows();
            assertEquals(7, rows.size());
            assertEquals(NoiseAction.COLLAPSE, rows.get(0).getAction());
            assertEquals("first", rows.get(5).getCanonicalPattern());
            assertTrue(rows.get(5).isEnabled());
            assertEquals(NoiseAction.COLLAPSE, rows.get(5).getAction());
            assertEquals("second", rows.get(6).getCanonicalPattern());
            assertFalse(rows.get(6).isEnabled());
            assertEquals(NoiseAction.COLLAPSE, rows.get(6).getAction());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testInvalidBlackListActionIsRejectedBeforeTransaction() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            assertQueueThrows(fixture, IllegalArgumentException.class, () -> fixture.database.updateBlackListAction(null));
            assertQueueThrows(fixture, IllegalArgumentException.class, () -> fixture.database.updateBlackListAction(NoiseAction.ALLOW));
            assertEquals(NoiseAction.COLLAPSE, fixture.loadAction());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testVersionThreeMigrationPreservesExistingDataAndAddsDuplicateVideoState() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                createVersionTwoSchema(raw);
                execute(raw, "CREATE TABLE settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
                execute(raw, "INSERT INTO white_list_rules VALUES('white', 1, 1)");
                execute(raw, "INSERT INTO black_list_rules VALUES('black', 1, 1, 1)");
                execute(raw, "INSERT INTO settings VALUES('black_list_action', '1')");
                execute(raw, "INSERT INTO settings VALUES('hide_reactions', '1')");
                execute(raw, "INSERT INTO settings VALUES('unrelated', 'kept')");
                execute(raw, "PRAGMA user_version = 3");
            });
            fixture.open();
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(4, raw.executeInt("PRAGMA user_version").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM white_list_rules WHERE canonical_pattern = 'white'").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM black_list_rules WHERE canonical_pattern = 'black'").intValue());
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = 'black_list_action'"));
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = 'hide_reactions'"));
                assertEquals("kept", querySingleString(raw, "SELECT value FROM settings WHERE key = 'unrelated'"));
                assertEquals("0", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_enabled'"));
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_match_mode'"));
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key = '"
                        + FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY + "'").intValue());
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key IN ('hide_channel_end_advertisement', 'hide_fullscreen_video_advertisement', 'hide_channel_pinned_message_header', 'message_cta_button_enabled')").intValue());
                assertEquals(0, raw.executeInt("SELECT count(*) FROM duplicate_video_history").intValue());
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testVersionThreeToFourMigrationFailureRollsBackOnlyVersionFourWork() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                createVersionTwoSchema(raw);
                execute(raw, "CREATE TABLE settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
                execute(raw, "INSERT INTO white_list_rules VALUES('white', 1, 1)");
                execute(raw, "INSERT INTO black_list_rules VALUES('black', 2, 0, 1)");
                execute(raw, "INSERT INTO settings VALUES('black_list_action', '2')");
                execute(raw, "INSERT INTO settings VALUES('hide_reactions', '1')");
                execute(raw, "INSERT INTO settings VALUES('unrelated', 'kept')");
                execute(raw, "CREATE TRIGGER fail_v4_setting BEFORE INSERT ON settings WHEN NEW.key = 'duplicate_video_match_mode' BEGIN SELECT RAISE(ABORT, 'test migration failure'); END");
                execute(raw, "PRAGMA user_version = 3");
            });
            fixture.assertOpenFails(SQLiteException.class);
            fixture.assertRaw(raw -> {
                assertEquals(3, raw.executeInt("PRAGMA user_version").intValue());
                assertFalse(raw.tableExists("duplicate_video_history"));
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key IN ('duplicate_video_enabled','duplicate_video_match_mode')").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM white_list_rules WHERE canonical_pattern = 'white'").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM black_list_rules WHERE canonical_pattern = 'black'").intValue());
                assertEquals("2", querySingleString(raw, "SELECT value FROM settings WHERE key = 'black_list_action'"));
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = 'hide_reactions'"));
                assertEquals("kept", querySingleString(raw, "SELECT value FROM settings WHERE key = 'unrelated'"));
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testDuplicateVideoSettingsRemainRawAndUpdatesAreIsolated() throws Exception {
        Fixture first = new Fixture();
        Fixture reopened = null;
        try {
            first.open();
            first.run(() -> {
                first.database.upsertSettingValue("duplicate_video_enabled", "7");
                first.database.upsertSettingValue("duplicate_video_match_mode", "9");
                first.database.upsertSettingValue("unrelated", "kept");
                first.database.close();
            });
            first.recycleQueue();
            reopened = first.reopen();
            reopened.open();
            Fixture current = reopened;
            current.run(() -> {
                CleargramDatabase.DuplicateVideoSettingsRow raw = current.database.loadDuplicateVideoSettings();
                assertEquals(7, raw.getEnabled());
                assertEquals(9, raw.getMatchMode());
                current.database.updateDuplicateVideoEnabled(true);
                assertEquals(1, current.database.loadDuplicateVideoSettings().getEnabled());
                assertEquals(9, current.database.loadDuplicateVideoSettings().getMatchMode());
                current.database.updateDuplicateVideoMatchMode(2);
                assertEquals(1, current.database.loadDuplicateVideoSettings().getEnabled());
                assertEquals(2, current.database.loadDuplicateVideoSettings().getMatchMode());
                current.database.close();
            });
            current.assertRaw(raw -> assertEquals("kept", querySingleString(raw, "SELECT value FROM settings WHERE key = 'unrelated'")));
        } finally {
            if (reopened != null) reopened.close();
            first.close();
        }
    }

    @Test
    public void testMissingAndNonIntegerDuplicateVideoSettingsFailWithoutDefaulting() throws Exception {
        assertDuplicateVideoSettingsLoadFails("DELETE FROM settings WHERE key = 'duplicate_video_enabled'");
        assertDuplicateVideoSettingsLoadFails("DELETE FROM settings WHERE key = 'duplicate_video_match_mode'");
        assertDuplicateVideoSettingsLoadFails("UPDATE settings SET value = 'not-an-int' WHERE key = 'duplicate_video_enabled'");
        assertDuplicateVideoSettingsLoadFails("UPDATE settings SET value = 'not-an-int' WHERE key = 'duplicate_video_match_mode'");
    }

    private void assertDuplicateVideoSettingsLoadFails(String rawMutation) throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.close());
            first.assertRaw(raw -> execute(raw, rawMutation));
            first.recycleQueue();
            second = first.reopen();
            second.open();
            Fixture current = second;
            assertQueueThrows(current, IllegalStateException.class, () -> current.database.loadDuplicateVideoSettings());
            current.run(() -> current.database.close());
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testUpdatingMissingDuplicateVideoSettingsFailsWithoutRecreatingThem() throws Exception {
        assertMissingDuplicateVideoUpdate("duplicate_video_enabled", true);
        assertMissingDuplicateVideoUpdate("duplicate_video_match_mode", false);
    }

    private void assertMissingDuplicateVideoUpdate(String missingKey, boolean enabledUpdate) throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> { first.database.upsertSettingValue("unrelated", "kept"); first.database.close(); });
            first.assertRaw(raw -> execute(raw, "DELETE FROM settings WHERE key = '" + missingKey + "'"));
            first.recycleQueue();
            second = first.reopen();
            second.open();
            Fixture current = second;
            if (enabledUpdate) {
                assertQueueThrows(current, IllegalStateException.class, () -> current.database.updateDuplicateVideoEnabled(true));
            } else {
                assertQueueThrows(current, IllegalStateException.class, () -> current.database.updateDuplicateVideoMatchMode(2));
            }
            current.run(() -> current.database.close());
            current.assertRaw(raw -> {
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key = '" + missingKey + "'").intValue());
                assertEquals("kept", querySingleString(raw, "SELECT value FROM settings WHERE key = 'unrelated'"));
                String otherKey = enabledUpdate ? "duplicate_video_match_mode" : "duplicate_video_enabled";
                assertEquals(enabledUpdate ? "1" : "0", querySingleString(raw,
                        "SELECT value FROM settings WHERE key = '" + otherKey + "'"));
            });
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testDuplicateVideoModeVersionSpacesAndBatchRollbackAreIndependent() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            byte[] key = duplicateKey((byte) 5);
            byte[] staged = duplicateKey((byte) 6);
            first.open();
            first.run(() -> {
                assertEquals(Arrays.asList(CleargramDatabase.DuplicateVideoRowClassification.FIRST_SEEN),
                        first.database.classifyDuplicateVideoRows(1, 1, Arrays.asList(key), 10L));
                assertEquals(Arrays.asList(CleargramDatabase.DuplicateVideoRowClassification.FIRST_SEEN),
                        first.database.classifyDuplicateVideoRows(2, 1, Arrays.asList(key), 11L));
                assertEquals(Arrays.asList(CleargramDatabase.DuplicateVideoRowClassification.FIRST_SEEN),
                        first.database.classifyDuplicateVideoRows(1, 2, Arrays.asList(key), 12L));
                first.database.close();
            });
            first.assertRaw(raw -> execute(raw, "CREATE TRIGGER fail_staged_duplicate BEFORE INSERT ON duplicate_video_history WHEN NEW.match_key = X'0606060606060606060606060606060606060606060606060606060606060606' BEGIN SELECT RAISE(ABORT, 'batch failure'); END"));
            first.recycleQueue();
            second = first.reopen();
            second.open();
            Fixture current = second;
            assertQueueThrows(current, SQLiteException.class, () -> current.database.classifyDuplicateVideoRows(1, 1, Arrays.asList(key, staged), 20L));
            current.run(() -> current.database.close());
            current.assertRaw(raw -> {
                assertEquals(3, raw.executeInt("SELECT count(*) FROM duplicate_video_history").intValue());
                assertEquals(10, raw.executeInt("SELECT last_seen FROM duplicate_video_history WHERE match_mode = 1 AND key_version = 1").intValue());
                assertEquals(0, raw.executeInt("SELECT count(*) FROM duplicate_video_history WHERE match_key = X'0606060606060606060606060606060606060606060606060606060606060606'").intValue());
            });
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testDuplicateVideoClassificationIsOrderedBinaryAndClearPreservesSettings() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> {
                fixture.database.insertWhiteListRow("white", true);
                fixture.database.insertBlackListRow("black", NoiseAction.HIDE, true);
                fixture.database.upsertSettingValue("unrelated", "kept");
                fixture.database.updateDuplicateVideoMatchMode(2);
            });
            byte[] first = duplicateKey((byte) 1);
            byte[] sameContent = Arrays.copyOf(first, first.length);
            byte[] second = duplicateKey((byte) 2);
            AtomicReference<List<CleargramDatabase.DuplicateVideoRowClassification>> result = new AtomicReference<>();
            fixture.run(() -> result.set(fixture.database.classifyDuplicateVideoRows(1, 1,
                    Arrays.asList(first, sameContent, second), 100L)));
            assertEquals(Arrays.asList(CleargramDatabase.DuplicateVideoRowClassification.FIRST_SEEN,
                    CleargramDatabase.DuplicateVideoRowClassification.DUPLICATE,
                    CleargramDatabase.DuplicateVideoRowClassification.FIRST_SEEN), result.get());
            fixture.run(() -> result.set(fixture.database.classifyDuplicateVideoRows(1, 1,
                    Arrays.asList(Arrays.copyOf(first, first.length)), 200L)));
            assertEquals(CleargramDatabase.DuplicateVideoRowClassification.DUPLICATE, result.get().get(0));
            fixture.run(() -> fixture.database.updateDuplicateVideoEnabled(true));
            fixture.run(() -> fixture.database.clearDuplicateVideoHistory());
            fixture.run(() -> fixture.database.clearDuplicateVideoHistory());
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(0, raw.executeInt("SELECT count(*) FROM duplicate_video_history").intValue());
                assertEquals("1", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_enabled'"));
                assertEquals("2", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_match_mode'"));
                assertEquals(1, raw.executeInt("SELECT count(*) FROM white_list_rules WHERE canonical_pattern = 'white'").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM black_list_rules WHERE canonical_pattern = 'black'").intValue());
                assertEquals("kept", querySingleString(raw, "SELECT value FROM settings WHERE key = 'unrelated'"));
                assertEquals(4, raw.executeInt("PRAGMA user_version").intValue());
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testDuplicateVideoHistorySchemaHasExpectedColumnsAndConstraints() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                SQLiteCursor cursor = raw.queryFinalized("PRAGMA table_info(duplicate_video_history)");
                try {
                    assertColumn(cursor, 0, "id", "INTEGER", 1);
                    assertColumn(cursor, 1, "match_mode", "INTEGER", 0);
                    assertColumn(cursor, 2, "key_version", "INTEGER", 0);
                    assertColumn(cursor, 3, "match_key", "BLOB", 0);
                    assertColumn(cursor, 4, "first_seen", "INTEGER", 0);
                    assertColumn(cursor, 5, "last_seen", "INTEGER", 0);
                    assertFalse(cursor.next());
                } finally { cursor.dispose(); }
                String key = "X'0000000000000000000000000000000000000000000000000000000000000000'";
                execute(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (1,1," + key + ",0,0)");
                assertSqlFails(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (1,1," + key + ",0,0)");
                execute(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (2,1," + key + ",0,0)");
                execute(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (1,2," + key + ",0,0)");
                assertSqlFails(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (3,1," + key + ",0,0)");
                assertSqlFails(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (1,0," + key + ",0,0)");
                assertSqlFails(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (1,1," + key + ",-1,0)");
                assertSqlFails(raw, "INSERT INTO duplicate_video_history (match_mode,key_version,match_key,first_seen,last_seen) VALUES (1,1," + key + ",2,1)");
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testDuplicateVideoSuccessfulTimestampTransitionsArePerBatch() throws Exception {
        Fixture fixture = new Fixture();
        try {
            byte[] first = duplicateKey((byte) 21);
            byte[] second = duplicateKey((byte) 22);
            fixture.open();
            fixture.run(() -> {
                fixture.database.classifyDuplicateVideoRows(1, 1, Arrays.asList(first, second), 1000L);
                fixture.database.classifyDuplicateVideoRows(1, 1, Arrays.asList(first), 2000L);
                fixture.database.close();
            });
            fixture.assertRaw(raw -> {
                assertEquals(1000, raw.executeInt("SELECT first_seen FROM duplicate_video_history WHERE match_key = X'1515151515151515151515151515151515151515151515151515151515151515'").intValue());
                assertEquals(2000, raw.executeInt("SELECT last_seen FROM duplicate_video_history WHERE match_key = X'1515151515151515151515151515151515151515151515151515151515151515'").intValue());
                assertEquals(1000, raw.executeInt("SELECT first_seen FROM duplicate_video_history WHERE match_key = X'1616161616161616161616161616161616161616161616161616161616161616'").intValue());
                assertEquals(1000, raw.executeInt("SELECT last_seen FROM duplicate_video_history WHERE match_key = X'1616161616161616161616161616161616161616161616161616161616161616'").intValue());
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testClearDuplicateVideoHistoryPreservesStateForBothEnabledValues() throws Exception {
        assertClearDuplicateHistoryPreservesState(false);
        assertClearDuplicateHistoryPreservesState(true);
    }

    private void assertClearDuplicateHistoryPreservesState(boolean enabled) throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> {
                fixture.database.updateDuplicateVideoEnabled(enabled);
                fixture.database.updateDuplicateVideoMatchMode(2);
                fixture.database.insertWhiteListRow("white-" + enabled, true);
                fixture.database.insertBlackListRow("black-" + enabled, NoiseAction.HIDE, true);
                fixture.database.upsertSettingValue("unrelated", "kept");
                fixture.database.classifyDuplicateVideoRows(1, 1, Arrays.asList(duplicateKey((byte) (enabled ? 31 : 30))), 100L);
                fixture.database.clearDuplicateVideoHistory();
                fixture.database.clearDuplicateVideoHistory();
                fixture.database.close();
            });
            fixture.assertRaw(raw -> {
                assertEquals(0, raw.executeInt("SELECT count(*) FROM duplicate_video_history").intValue());
                assertEquals(enabled ? "1" : "0", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_enabled'"));
                assertEquals("2", querySingleString(raw, "SELECT value FROM settings WHERE key = 'duplicate_video_match_mode'"));
                assertEquals("kept", querySingleString(raw, "SELECT value FROM settings WHERE key = 'unrelated'"));
                assertEquals(1, raw.executeInt("SELECT count(*) FROM white_list_rules WHERE canonical_pattern = 'white-" + enabled + "'").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM black_list_rules WHERE canonical_pattern = 'black-" + enabled + "'").intValue());
                assertEquals(4, raw.executeInt("PRAGMA user_version").intValue());
            });
        } finally { fixture.close(); }
    }

    private static byte[] duplicateKey(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }

    @Test
    public void testMissingOrInvalidActionIsNotRepairedAndDoesNotChangeRules() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.insertBlackListRow("rule", NoiseAction.HIDE, true));
            first.run(() -> first.database.updateBlackListAction(NoiseAction.HIDE));
            first.run(() -> first.database.close());
            first.assertRaw(raw -> execute(raw, "DELETE FROM settings WHERE key = 'black_list_action'"));
            first.recycleQueue();

            second = first.reopen();
            second.open();
            Fixture reopened = second;
            assertQueueThrows(reopened, IllegalStateException.class, () -> reopened.database.loadBlackListAction());
            assertQueueThrows(reopened, IllegalStateException.class, () -> reopened.database.updateBlackListAction(NoiseAction.COLLAPSE));
            reopened.run(() -> reopened.database.close());
            reopened.assertRaw(raw -> {
                assertEquals(0, raw.executeInt("SELECT count(*) FROM settings WHERE key = 'black_list_action'").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM black_list_rules WHERE canonical_pattern = 'rule' AND action = 1").intValue());
            });
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testInvalidStoredActionIsRejectedWithoutRepair() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.close());
            first.assertRaw(raw -> execute(raw, "UPDATE settings SET value = 'invalid' WHERE key = 'black_list_action'"));
            first.recycleQueue();

            second = first.reopen();
            second.open();
            Fixture reopened = second;
            assertQueueThrows(reopened, IllegalStateException.class, () -> reopened.database.loadBlackListAction());
            reopened.run(() -> reopened.database.close());
            reopened.assertRaw(raw -> assertEquals("invalid", querySingleString(raw, "SELECT value FROM settings WHERE key = 'black_list_action'")));
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testActionUpdateRollsBackWhenRuleUpdateFails() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.insertBlackListRow("rule", NoiseAction.HIDE, true));
            first.run(() -> first.database.updateBlackListAction(NoiseAction.HIDE));
            first.run(() -> first.database.close());
            first.assertRaw(raw -> execute(raw, "CREATE TRIGGER reject_black_list_action BEFORE UPDATE OF action ON black_list_rules BEGIN SELECT RAISE(ABORT, 'reject action'); END"));
            first.recycleQueue();

            second = first.reopen();
            second.open();
            Fixture reopened = second;
            assertQueueThrows(reopened, SQLiteException.class, () -> reopened.database.updateBlackListAction(NoiseAction.COLLAPSE));
            assertEquals(NoiseAction.HIDE, reopened.loadAction());
            assertEquals(NoiseAction.HIDE, findBlackListRow(reopened.loadBlackRows(), "rule").getAction());
        } finally {
            if (second != null) second.close();
            first.close();
        }
    }

    @Test
    public void testEmptyWhiteListLoad() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            List<CleargramDatabase.WhiteListRow> first = fixture.loadRows();
            List<CleargramDatabase.WhiteListRow> second = fixture.loadRows();
            assertNotNull(first);
            assertTrue(first.isEmpty());
            assertTrue(second.isEmpty());
            assertFalse(first == second);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testLoadPreservesInsertionOrderAndEnabledMapping() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.insert("first", true);
            fixture.insert("second", false);
            fixture.insert("third", true);
            List<CleargramDatabase.WhiteListRow> rows = fixture.loadRows();
            assertRows(rows, "first", "second", "third");
            assertTrue(rows.get(0).isEnabled());
            assertFalse(rows.get(1).isEnabled());
            assertTrue(rows.get(2).isEnabled());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testDeleteExistingAndMissingRows() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open(); fixture.insert("A", true); fixture.insert("B", true); fixture.insert("C", true);
            assertTrue(fixture.delete("B"));
            assertFalse(fixture.delete("B"));
            assertRows(fixture.loadRows(), "A", "C");
        } finally { fixture.close(); }
    }

    @Test
    public void testEnabledUpdateExistingAndMissingRows() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open(); fixture.insert("A", true); fixture.insert("B", true);
            assertTrue(fixture.update("A", false));
            assertFalse(fixture.update("missing", true));
            assertRows(fixture.loadRows(), "A", "B");
            assertFalse(fixture.loadRows().get(0).isEnabled());
        } finally { fixture.close(); }
    }

    @Test
    public void testDeleteDoesNotCompactAndReinsertMovesToEnd() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open(); fixture.insert("A", true); fixture.insert("B", true); fixture.insert("C", true);
            assertTrue(fixture.delete("B")); fixture.insert("B", true);
            assertRows(fixture.loadRows(), "A", "C", "B");
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(1, raw.executeInt("SELECT order_key FROM white_list_rules WHERE canonical_pattern = 'A'").intValue());
                assertEquals(3, raw.executeInt("SELECT order_key FROM white_list_rules WHERE canonical_pattern = 'C'").intValue());
                assertEquals(4, raw.executeInt("SELECT order_key FROM white_list_rules WHERE canonical_pattern = 'B'").intValue());
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testDuplicateInsertRollsBackWithoutMutation() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open(); fixture.insert("A", true);
            assertQueueThrows(fixture, SQLiteException.class, () -> fixture.database.insertWhiteListRow("A", true));
            fixture.insert("B", true);
            assertRows(fixture.loadRows(), "A", "B");
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                assertEquals(2, raw.executeInt("SELECT count(*) FROM white_list_rules").intValue());
                assertEquals(1, raw.executeInt("SELECT order_key FROM white_list_rules WHERE canonical_pattern = 'A'").intValue());
                assertEquals(2, raw.executeInt("SELECT order_key FROM white_list_rules WHERE canonical_pattern = 'B'").intValue());
            });
        } finally { fixture.close(); }
    }

    @Test
    public void testOrderKeyOverflowFailsWithoutMutation() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                execute(raw, "CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, order_key INTEGER NOT NULL UNIQUE)");
                execute(raw, "PRAGMA user_version = 1");
                execute(raw, "INSERT INTO white_list_rules VALUES('maximum', 1, " + Long.MAX_VALUE + ")");
            });
            fixture.open();
            assertQueueThrows(fixture, SQLiteException.class, () -> fixture.database.insertWhiteListRow("new", true));
            assertRows(fixture.loadRows(), "maximum");
        } finally { fixture.close(); }
    }

    @Test
    public void testLoadRejectsInvalidEnabledWithoutPartialResult() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                execute(raw, "CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, order_key INTEGER NOT NULL UNIQUE)");
                execute(raw, "PRAGMA user_version = 1");
                execute(raw, "INSERT INTO white_list_rules VALUES('valid', 1, 1)");
                execute(raw, "INSERT INTO white_list_rules VALUES('invalid', 2, 2)");
            });
            fixture.open();
            assertQueueThrows(fixture, SQLiteException.class, () -> fixture.database.loadWhiteListRows());
            assertTrue(fixture.update("valid", false));
        } finally { fixture.close(); }
    }

    @Test
    public void testLoadRejectsNonIntegerEnabledRepresentation() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                execute(raw, "CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, order_key INTEGER NOT NULL UNIQUE)");
                execute(raw, "PRAGMA user_version = 1");
                execute(raw, "INSERT INTO white_list_rules VALUES('valid', 1, 1)");
                insertBoundRow(raw, "invalid", "invalid", 2);
            });
            fixture.open();
            assertQueueThrows(fixture, SQLiteException.class, () -> fixture.database.loadWhiteListRows());
            assertTrue(fixture.update("valid", false));
        } finally { fixture.close(); }
    }

    @Test
    public void testWhiteListOperationsFailOnWrongThread() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            assertDirectThrows(IllegalStateException.class, () -> fixture.database.loadWhiteListRows());
            assertDirectThrows(IllegalStateException.class, () -> fixture.database.insertWhiteListRow("A", true));
            assertDirectThrows(IllegalStateException.class, () -> fixture.database.deleteWhiteListRow("A"));
            assertDirectThrows(IllegalStateException.class, () -> fixture.database.setWhiteListRowEnabled("A", false));
        } finally { fixture.close(); }
    }

    @Test
    public void testWhiteListOperationsFailBeforeOpen() throws Exception {
        Fixture fixture = new Fixture();
        try {
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.loadWhiteListRows());
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.insertWhiteListRow("A", true));
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.deleteWhiteListRow("A"));
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.setWhiteListRowEnabled("A", false));
            assertFalse(fixture.databaseFile.exists());
        } finally { fixture.close(); }
    }

    @Test
    public void testWhiteListOperationsFailAfterClose() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> fixture.database.close());
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.loadWhiteListRows());
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.insertWhiteListRow("A", true));
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.deleteWhiteListRow("A"));
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.setWhiteListRowEnabled("A", false));
        } finally { fixture.close(); }
    }

    @Test
    public void testNullPatternArgumentsAreRejected() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            assertQueueThrows(fixture, IllegalArgumentException.class, () -> fixture.database.insertWhiteListRow(null, true));
            assertQueueThrows(fixture, IllegalArgumentException.class, () -> fixture.database.deleteWhiteListRow(null));
            assertQueueThrows(fixture, IllegalArgumentException.class, () -> fixture.database.setWhiteListRowEnabled(null, false));
            assertTrue(fixture.loadRows().isEmpty());
        } finally { fixture.close(); }
    }

    @Test
    public void testSchemaMetadataMatchesContract() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(this::assertSchemaMetadata);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testSchemaConstraintsAreEnforced() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> fixture.database.close());
            fixture.assertRaw(raw -> {
                execute(raw, "INSERT INTO white_list_rules VALUES('A', 1, 1)");
                execute(raw, "INSERT INTO white_list_rules VALUES('a', 1, 2)");
                assertSqlFails(raw, "INSERT INTO white_list_rules VALUES('', 1, 3)");
                assertBoundInsertFails(raw, repeat('x', 256), 1, 4);
                assertSqlFails(raw, "INSERT INTO white_list_rules VALUES('B', -1, 3)");
                assertSqlFails(raw, "INSERT INTO white_list_rules VALUES('B', 2, 3)");
                assertSqlFails(raw, "INSERT INTO white_list_rules VALUES('B', 1, 0)");
                assertSqlFails(raw, "INSERT INTO white_list_rules VALUES('B', 1, -1)");
                assertSqlFails(raw, "INSERT INTO white_list_rules VALUES('A', 1, 3)");
                assertSqlFails(raw, "INSERT INTO white_list_rules VALUES('B', 1, 2)");
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testCurrentVersionReopenPreservesData() throws Exception {
        Fixture first = new Fixture();
        Fixture second = null;
        try {
            first.open();
            first.run(() -> first.database.close());
            first.assertRaw(raw -> execute(raw, "INSERT INTO white_list_rules VALUES('persisted', 1, 1)"));
            first.recycleQueue();
            second = first.reopen();
            Fixture reopened = second;
            reopened.open();
            reopened.run(() -> reopened.database.close());
            reopened.assertRaw(raw -> assertEquals(1, raw.executeInt("SELECT count(*) FROM white_list_rules WHERE canonical_pattern = 'persisted'").intValue()));
        } finally {
            if (second != null) {
                second.close();
            }
            first.close();
        }
    }

    @Test
    public void testFutureVersionIsRejectedWithoutMutation() throws Exception {
        assertRejectedSchema("PRAGMA user_version = 5", "future_marker");
    }

    @Test
    public void testNegativeUserVersionBehavior() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> execute(raw, "PRAGMA user_version = -1"));
            final int readBack[] = new int[1];
            fixture.assertRaw(raw -> readBack[0] = raw.executeInt("PRAGMA user_version").intValue());
            if (readBack[0] == -1) {
                fixture.assertOpenFails(SQLiteException.class);
                fixture.assertRaw(raw -> assertEquals(-1, raw.executeInt("PRAGMA user_version").intValue()));
                assertTrue(fixture.databaseFile.exists());
            } else if (readBack[0] == 0) {
                fixture.open();
                fixture.run(() -> fixture.database.close());
                fixture.assertRaw(raw -> assertEquals(4, raw.executeInt("PRAGMA user_version").intValue()));
            } else {
                fail("Unexpected SQLite user_version read-back: " + readBack[0]);
            }
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testExtraColumnSchemaIsRejected() throws Exception {
        assertMalformedSchema("CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, order_key INTEGER NOT NULL UNIQUE, extra TEXT NOT NULL)", raw -> {
            assertEquals(4, raw.executeInt("SELECT count(*) FROM pragma_table_info('white_list_rules')").intValue());
            assertEquals(1, raw.executeInt("SELECT count(*) FROM pragma_table_info('white_list_rules') WHERE name = 'extra'").intValue());
        });
    }

    @Test
    public void testMissingOrderKeyUniqueConstraintIsRejected() throws Exception {
        assertMalformedSchema("CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, order_key INTEGER NOT NULL)", raw -> {
            assertEquals(3, raw.executeInt("SELECT count(*) FROM pragma_table_info('white_list_rules')").intValue());
            assertFalse(hasUniqueOrderKeyIndex(raw));
        });
    }

    @Test
    public void testWrongPrimaryKeySchemaIsRejected() throws Exception {
        assertMalformedSchema("CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL, enabled INTEGER NOT NULL, order_key INTEGER NOT NULL UNIQUE, PRIMARY KEY(order_key))", raw -> {
            assertEquals(0, raw.executeInt("SELECT pk FROM pragma_table_info('white_list_rules') WHERE name = 'canonical_pattern'").intValue());
            assertEquals(1, raw.executeInt("SELECT pk FROM pragma_table_info('white_list_rules') WHERE name = 'order_key'").intValue());
        });
    }

    @Test
    public void testMigrationFailureRollsBack() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                execute(raw, "CREATE TABLE white_list_rules (broken INTEGER)");
                execute(raw, "CREATE TABLE marker (value TEXT)");
                execute(raw, "INSERT INTO marker VALUES('migration_marker')");
            });
            fixture.assertOpenFails(SQLiteException.class);
            fixture.assertRaw(raw -> {
                assertTrue(fixture.databaseFile.exists());
                assertEquals(0, raw.executeInt("PRAGMA user_version").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM pragma_table_info('white_list_rules')").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM pragma_table_info('white_list_rules') WHERE name = 'broken'").intValue());
                assertEquals(0, raw.executeInt("SELECT count(*) FROM pragma_table_info('white_list_rules') WHERE name IN ('canonical_pattern', 'enabled', 'order_key')").intValue());
                assertEquals(1, raw.executeInt("SELECT count(*) FROM marker WHERE value = 'migration_marker'").intValue());
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testWrongThreadOpenFailsWithoutCreatingDatabase() throws Exception {
        Fixture fixture = new Fixture();
        try {
            assertDirectThrows(IllegalStateException.class, () -> fixture.database.open());
            assertFalse(fixture.databaseFile.exists());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testWrongThreadCloseFails() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            assertDirectThrows(IllegalStateException.class, () -> fixture.database.close());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testCloseIsIdempotentAndReopenIsForbidden() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            fixture.run(() -> fixture.database.close());
            fixture.run(() -> fixture.database.close());
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.open());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testCloseFromNewFails() throws Exception {
        Fixture fixture = new Fixture();
        try {
            assertQueueThrows(fixture, IllegalStateException.class, () -> fixture.database.close());
            assertFalse(fixture.databaseFile.exists());
        } finally {
            fixture.close();
        }
    }

    private void assertRejectedSchema(String versionSql, String marker) throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                execute(raw, "CREATE TABLE marker (value TEXT)");
                execute(raw, "INSERT INTO marker VALUES('" + marker + "')");
                execute(raw, versionSql);
            });
            fixture.assertOpenFails(SQLiteException.class);
            fixture.assertRaw(raw -> {
                assertTrue(fixture.databaseFile.exists());
                assertEquals(4, raw.executeInt("PRAGMA user_version").intValue());
                assertTrue(raw.tableExists("marker"));
                assertEquals(1, raw.executeInt("SELECT count(*) FROM marker WHERE value = '" + marker + "'").intValue());
                assertFalse(raw.tableExists("white_list_rules"));
            });
        } finally {
            fixture.close();
        }
    }

    private void assertMalformedSchema(String createTableSql, RawOperation verification) throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.assertRaw(raw -> {
                execute(raw, createTableSql);
                execute(raw, "PRAGMA user_version = 1");
                execute(raw, "CREATE TABLE marker (value TEXT)");
                execute(raw, "INSERT INTO marker VALUES('schema_marker')");
            });
            fixture.assertOpenFails(SQLiteException.class);
            fixture.assertRaw(raw -> {
                assertTrue(fixture.databaseFile.exists());
                assertEquals(1, raw.executeInt("PRAGMA user_version").intValue());
                assertTrue(raw.tableExists("white_list_rules"));
                assertEquals(1, raw.executeInt("SELECT count(*) FROM marker WHERE value = 'schema_marker'").intValue());
                verification.run(raw);
            });
        } finally {
            fixture.close();
        }
    }

    private static void execute(SQLiteDatabase database, String sql) throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast(sql);
            statement.stepThis();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    private static void createVersionTwoSchema(SQLiteDatabase database) throws SQLiteException {
        execute(database, "CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL COLLATE BINARY PRIMARY KEY CHECK (length(canonical_pattern) BETWEEN 1 AND 255), enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)), order_key INTEGER NOT NULL UNIQUE CHECK (order_key > 0))");
        execute(database, "CREATE TABLE black_list_rules (canonical_pattern TEXT NOT NULL COLLATE BINARY PRIMARY KEY CHECK (length(canonical_pattern) BETWEEN 1 AND 255), action INTEGER NOT NULL CHECK (action IN (1, 2)), enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)), order_key INTEGER NOT NULL UNIQUE CHECK (order_key > 0))");
    }

    private static void createVersionFourSchema(SQLiteDatabase database) throws SQLiteException {
        createVersionTwoSchema(database);
        execute(database, "CREATE TABLE settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
        execute(database, "CREATE TABLE duplicate_video_history (id INTEGER PRIMARY KEY, match_mode INTEGER NOT NULL CHECK (match_mode IN (1, 2)), key_version INTEGER NOT NULL CHECK (key_version > 0), match_key BLOB NOT NULL, first_seen INTEGER NOT NULL CHECK (first_seen >= 0), last_seen INTEGER NOT NULL CHECK (last_seen >= first_seen), UNIQUE (match_mode, key_version, match_key))");
        execute(database, "INSERT INTO settings VALUES('black_list_action', '2')");
        execute(database, "INSERT INTO settings VALUES('duplicate_video_enabled', '0')");
        execute(database, "INSERT INTO settings VALUES('duplicate_video_match_mode', '1')");
        execute(database, "PRAGMA user_version = 4");
    }

    private static String querySingleString(SQLiteDatabase database, String sql) throws SQLiteException {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(sql);
            assertTrue("Expected one row for " + sql, cursor.next());
            String value = cursor.stringValue(0);
            assertFalse("Expected a single row for " + sql, cursor.next());
            return value;
        } finally {
            if (cursor != null) cursor.dispose();
        }
    }

    private void assertSchemaMetadata(SQLiteDatabase database) throws Exception {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("PRAGMA table_info(white_list_rules)");
            assertColumn(cursor, 0, "canonical_pattern", "TEXT", 1);
            assertColumn(cursor, 1, "enabled", "INTEGER", 0);
            assertColumn(cursor, 2, "order_key", "INTEGER", 0);
            assertFalse(cursor.next());
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }

        String orderKeyIndex = null;
        try {
            cursor = database.queryFinalized("PRAGMA index_list(white_list_rules)");
            while (cursor.next()) {
                if (cursor.intValue(2) == 1 && isSingleColumnIndex(database, cursor.stringValue(1), "order_key")) {
                    orderKeyIndex = cursor.stringValue(1);
                    break;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        assertNotNull("Missing unique order_key index", orderKeyIndex);
    }

    private static void assertColumn(SQLiteCursor cursor, int cid, String name, String type, int primaryKey) throws Exception {
        assertTrue("Missing column " + name, cursor.next());
        assertEquals(cid, cursor.intValue(0));
        assertEquals(name, cursor.stringValue(1));
        assertEquals(type, cursor.stringValue(2));
        assertEquals(1, cursor.intValue(3));
        assertTrue(cursor.isNull(4));
        assertEquals(primaryKey, cursor.intValue(5));
    }

    private static boolean isSingleColumnIndex(SQLiteDatabase database, String indexName, String column) throws Exception {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("PRAGMA index_info(\"" + indexName.replace("\"", "\"\"") + "\")");
            return cursor.next() && column.equals(cursor.stringValue(2)) && !cursor.next();
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private static boolean hasUniqueOrderKeyIndex(SQLiteDatabase database) throws Exception {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("PRAGMA index_list(white_list_rules)");
            while (cursor.next()) {
                if (cursor.intValue(2) == 1 && isSingleColumnIndex(database, cursor.stringValue(1), "order_key")) {
                    return true;
                }
            }
            return false;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private static void assertBoundInsertFails(SQLiteDatabase database, String pattern, int enabled, int orderKey) throws Exception {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT INTO white_list_rules VALUES(?, ?, ?)");
            statement.bindString(1, pattern);
            statement.bindInteger(2, enabled);
            statement.bindInteger(3, orderKey);
            statement.stepThis();
            fail("Expected SQLiteException for bound insert");
        } catch (SQLiteException expected) {
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    private static void insertBoundRow(SQLiteDatabase database, String pattern, String enabled, int orderKey) throws Exception {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT INTO white_list_rules VALUES(?, ?, ?)");
            statement.bindString(1, pattern);
            statement.bindString(2, enabled);
            statement.bindInteger(3, orderKey);
            statement.stepThis();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static void assertSqlFails(SQLiteDatabase database, String sql) throws Exception {
        try {
            execute(database, sql);
            fail("Expected SQLiteException for " + sql);
        } catch (SQLiteException expected) {
        }
    }

    private static void assertDirectThrows(Class<? extends Throwable> expected, ThrowingOperation operation) throws Exception {
        try {
            operation.run();
            fail("Expected " + expected.getSimpleName());
        } catch (Throwable actual) {
            assertTrue("Expected " + expected.getSimpleName() + " but was " + actual, expected.isInstance(actual));
        }
    }

    private static void assertQueueThrows(Fixture fixture, Class<? extends Throwable> expected, ThrowingOperation operation) throws Exception {
        try {
            fixture.run(operation);
            fail("Expected " + expected.getSimpleName());
        } catch (Throwable actual) {
            assertTrue("Expected " + expected.getSimpleName() + " but was " + actual, expected.isInstance(actual));
        }
    }

    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private interface RawOperation {
        void run(SQLiteDatabase database) throws Exception;
    }

    private static void assertRows(List<CleargramDatabase.WhiteListRow> rows, String... patterns) {
        assertEquals(patterns.length, rows.size());
        for (int index = 0; index < patterns.length; index++) {
            assertEquals(patterns[index], rows.get(index).getCanonicalPattern());
        }
    }

    private static void assertDefaultBlackListRows(List<CleargramDatabase.BlackListRow> rows) {
        assertEquals(DEFAULT_BLACK_LIST_PATTERNS.length, rows.size());
        for (int index = 0; index < DEFAULT_BLACK_LIST_PATTERNS.length; index++) {
            CleargramDatabase.BlackListRow row = rows.get(index);
            assertEquals(DEFAULT_BLACK_LIST_PATTERNS[index], row.getCanonicalPattern());
            assertEquals(NoiseAction.COLLAPSE, row.getAction());
            assertTrue(row.isEnabled());
        }
    }

    private static CleargramDatabase.BlackListRow findBlackListRow(
            List<CleargramDatabase.BlackListRow> rows,
            String canonicalPattern
    ) {
        for (CleargramDatabase.BlackListRow row : rows) {
            if (canonicalPattern.equals(row.getCanonicalPattern())) {
                return row;
            }
        }
        fail("Missing Black List row: " + canonicalPattern);
        throw new AssertionError();
    }

    private static File pendingFile(Fixture fixture) {
        return new File(fixture.databaseFile.getAbsolutePath() + DEFAULT_BLACK_LIST_PENDING_SUFFIX);
    }

    private static File freshFilterDefaultsPendingFile(Fixture fixture) {
        return new File(fixture.databaseFile.getAbsolutePath() + FRESH_FILTER_DEFAULTS_PENDING_SUFFIX);
    }

    private static void assertFreshFilterDefaults(SQLiteDatabase database) throws Exception {
        for (String key : FRESH_FILTER_DEFAULTS_KEYS) {
            assertEquals("1", querySingleString(database,
                    "SELECT value FROM settings WHERE key = '" + key + "'"));
        }
    }

    private static final class Fixture {
        private static final long TIMEOUT_SECONDS = 15;
        final File directory;
        final File databaseFile;
        final DispatchQueue queue;
        final CleargramDatabase database;
        private boolean recycled;
        private boolean openAttempted;

        Fixture() {
            directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "cleargram-db-test-" + UUID.randomUUID());
            assertTrue("Cannot create test directory", directory.mkdirs());
            databaseFile = new File(directory, "noisegram.db");
            queue = new DispatchQueue("CleargramDatabaseTest-" + UUID.randomUUID());
            database = new CleargramDatabase(databaseFile, queue);
        }

        private Fixture(File directory, File databaseFile) {
            this.directory = directory;
            this.databaseFile = databaseFile;
            queue = new DispatchQueue("CleargramDatabaseTest-" + UUID.randomUUID());
            database = new CleargramDatabase(databaseFile, queue);
        }

        Fixture reopen() {
            return new Fixture(directory, databaseFile);
        }

        void open() throws Exception {
            openAttempted = true;
            run(() -> database.open());
        }

        List<CleargramDatabase.WhiteListRow> loadRows() throws Exception {
            AtomicReference<List<CleargramDatabase.WhiteListRow>> result = new AtomicReference<>();
            run(() -> result.set(database.loadWhiteListRows()));
            return result.get();
        }

        List<CleargramDatabase.BlackListRow> loadBlackRows() throws Exception {
            AtomicReference<List<CleargramDatabase.BlackListRow>> result = new AtomicReference<>();
            run(() -> result.set(database.loadBlackListRows()));
            return result.get();
        }

        NoiseAction loadAction() throws Exception {
            AtomicReference<NoiseAction> result = new AtomicReference<>();
            run(() -> result.set(database.loadBlackListAction()));
            return result.get();
        }

        String loadSetting(String key) throws Exception {
            AtomicReference<String> result = new AtomicReference<>();
            run(() -> result.set(database.loadSettingValue(key)));
            return result.get();
        }

        void insert(String pattern, boolean enabled) throws Exception {
            run(() -> database.insertWhiteListRow(pattern, enabled));
        }

        boolean delete(String pattern) throws Exception {
            AtomicReference<Boolean> result = new AtomicReference<>();
            run(() -> result.set(database.deleteWhiteListRow(pattern)));
            return result.get();
        }

        boolean update(String pattern, boolean enabled) throws Exception {
            AtomicReference<Boolean> result = new AtomicReference<>();
            run(() -> result.set(database.setWhiteListRowEnabled(pattern, enabled)));
            return result.get();
        }

        void assertOpenFails(Class<? extends Throwable> expected) throws Exception {
            openAttempted = true;
            assertQueueThrows(this, expected, () -> database.open());
        }

        void run(ThrowingOperation operation) throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            queue.postRunnable(() -> {
                try {
                    operation.run();
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    latch.countDown();
                }
            });
            assertTrue("Timed out waiting for storage queue", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Throwable throwable = failure.get();
            if (throwable != null) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                }
                if (throwable instanceof Error) {
                    throw (Error) throwable;
                }
                throw new AssertionError(throwable);
            }
        }

        void assertRaw(RawOperation operation) throws Exception {
            SQLiteDatabase raw = new SQLiteDatabase(databaseFile.getAbsolutePath());
            try {
                operation.run(raw);
            } finally {
                raw.close();
            }
        }

        void recycleQueue() throws Exception {
            if (!recycled) {
                run(() -> { });
                recycled = true;
                queue.recycle();
            }
        }

        void close() throws Exception {
            if (!recycled) {
                try {
                    if (openAttempted) {
                        run(() -> database.close());
                    }
                } finally {
                    recycleQueue();
                }
            }
            delete(databaseFile);
            delete(new File(databaseFile.getAbsolutePath() + DEFAULT_BLACK_LIST_PENDING_SUFFIX));
            delete(new File(databaseFile.getAbsolutePath() + FRESH_FILTER_DEFAULTS_PENDING_SUFFIX));
            delete(new File(databaseFile.getAbsolutePath() + "-journal"));
            delete(new File(databaseFile.getAbsolutePath() + "-wal"));
            delete(new File(databaseFile.getAbsolutePath() + "-shm"));
            delete(directory);
        }

        private static void delete(File file) {
            if (file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }
}
