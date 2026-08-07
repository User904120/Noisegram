package org.cleargram.storage.telegram;

import android.os.Handler;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.cleargram.api.NoiseAction;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class CleargramDatabase {

    private static final int DATABASE_VERSION = 4;
    private static final String WHITE_LIST_TABLE = "white_list_rules";
    private static final String BLACK_LIST_TABLE = "black_list_rules";
    private static final String SETTINGS_TABLE = "settings";
    private static final String BLACK_LIST_ACTION_KEY = "black_list_action";
    private static final String DEFAULT_BLACK_LIST_SEED_VERSION_KEY = "default_black_list_seed_version";
    private static final String DEFAULT_BLACK_LIST_SEED_VERSION = "1";
    private static final String DEFAULT_BLACK_LIST_PENDING_SUFFIX = ".default-black-list-v1.pending";
    private static final String[] DEFAULT_BLACK_LIST_PATTERNS = {
            "#реклама",
            "рекламодатель",
            "erid:",
            "ставки",
            "каппер"
    };
    static final String DUPLICATE_VIDEO_ENABLED_KEY = "duplicate_video_enabled";
    private static final String DUPLICATE_VIDEO_MATCH_MODE_KEY = "duplicate_video_match_mode";
    private static final String FRESH_INSTALL_ENABLED_VALUE = "1";
    static final String FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY = "fresh_filter_defaults_seed_version";
    private static final String FRESH_FILTER_DEFAULTS_SEED_VERSION = "1";
    static final String FRESH_FILTER_DEFAULTS_PENDING_SUFFIX = ".fresh-filter-defaults-v1.pending";
    private static final String DUPLICATE_VIDEO_HISTORY_TABLE = "duplicate_video_history";
    private static final String CREATE_WHITE_LIST_TABLE_SQL = "CREATE TABLE white_list_rules ("
            + "canonical_pattern TEXT NOT NULL COLLATE BINARY PRIMARY KEY CHECK (length(canonical_pattern) BETWEEN 1 AND 255),"
            + "enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),"
            + "order_key INTEGER NOT NULL UNIQUE CHECK (order_key > 0)"
            + ")";
    private static final String CREATE_BLACK_LIST_TABLE_SQL = "CREATE TABLE black_list_rules ("
            + "canonical_pattern TEXT NOT NULL COLLATE BINARY PRIMARY KEY CHECK (length(canonical_pattern) BETWEEN 1 AND 255),"
            + "action INTEGER NOT NULL CHECK (action IN (1, 2)),"
            + "enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),"
            + "order_key INTEGER NOT NULL UNIQUE CHECK (order_key > 0)"
            + ")";
    private static final String CREATE_SETTINGS_TABLE_SQL = "CREATE TABLE settings ("
            + "key TEXT NOT NULL PRIMARY KEY,"
            + "value TEXT NOT NULL"
            + ")";
    private static final String CREATE_DUPLICATE_VIDEO_HISTORY_TABLE_SQL = "CREATE TABLE duplicate_video_history ("
            + "id INTEGER PRIMARY KEY,"
            + "match_mode INTEGER NOT NULL CHECK (match_mode IN (1, 2)),"
            + "key_version INTEGER NOT NULL CHECK (key_version > 0),"
            + "match_key BLOB NOT NULL,"
            + "first_seen INTEGER NOT NULL CHECK (first_seen >= 0),"
            + "last_seen INTEGER NOT NULL CHECK (last_seen >= first_seen),"
            + "UNIQUE (match_mode, key_version, match_key)"
            + ")";

    private enum State {
        NEW,
        OPEN,
        IN_TRANSACTION,
        FAILED,
        CLOSED
    }

    private interface TransactionOperation {
        void run() throws SQLiteException;
    }

    static final class WhiteListRow {
        private final String canonicalPattern;
        private final boolean enabled;

        private WhiteListRow(String canonicalPattern, boolean enabled) {
            this.canonicalPattern = canonicalPattern;
            this.enabled = enabled;
        }

        String getCanonicalPattern() {
            return canonicalPattern;
        }

        boolean isEnabled() {
            return enabled;
        }
    }

    static final class BlackListRow {
        private final String canonicalPattern;
        private final NoiseAction action;
        private final boolean enabled;

        private BlackListRow(String canonicalPattern, NoiseAction action, boolean enabled) {
            this.canonicalPattern = canonicalPattern;
            this.action = action;
            this.enabled = enabled;
        }

        String getCanonicalPattern() { return canonicalPattern; }
        NoiseAction getAction() { return action; }
        boolean isEnabled() { return enabled; }
    }

    static final class DuplicateVideoSettingsRow {
        private final int enabled;
        private final int matchMode;

        private DuplicateVideoSettingsRow(int enabled, int matchMode) {
            this.enabled = enabled;
            this.matchMode = matchMode;
        }

        int getEnabled() { return enabled; }
        int getMatchMode() { return matchMode; }
    }

    enum DuplicateVideoRowClassification { FIRST_SEEN, DUPLICATE }

    private final File databaseFile;
    private final File defaultBlackListPendingFile;
    private final File freshFilterDefaultsPendingFile;
    private final DispatchQueue storageQueue;
    private SQLiteDatabase database;
    private State state = State.NEW;

    CleargramDatabase(File databaseFile, DispatchQueue storageQueue) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile");
        defaultBlackListPendingFile = new File(databaseFile.getAbsolutePath()
                + DEFAULT_BLACK_LIST_PENDING_SUFFIX);
        freshFilterDefaultsPendingFile = new File(databaseFile.getAbsolutePath()
                + FRESH_FILTER_DEFAULTS_PENDING_SUFFIX);
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
    }

    void open() throws SQLiteException {
        assertOwningQueue();
        if (state != State.NEW) {
            throw new IllegalStateException("Cleargram database can only be opened from NEW state");
        }

        boolean freshFilterDefaultsInitializationPending = beginFreshFilterDefaultsInitialization();
        boolean defaultBlackListInitializationPending = beginDefaultBlackListInitialization();
        try {
            database = new SQLiteDatabase(databaseFile.getAbsolutePath());
            state = State.OPEN;
            int version = readUserVersion();
            if (version == 0) {
                migrateToVersionOne();
                migrateToVersionTwo();
                migrateToVersionThree();
                migrateToVersionFour();
            } else if (version == 1) {
                migrateToVersionTwo();
                migrateToVersionThree();
                migrateToVersionFour();
            } else if (version == 2) {
                migrateToVersionThree();
                migrateToVersionFour();
            } else if (version == 3) {
                migrateToVersionFour();
            } else if (version != DATABASE_VERSION) {
                throw new SQLiteException("Unsupported Cleargram database version: " + version);
            }
            if (freshFilterDefaultsInitializationPending) {
                initializeFreshInstallFilterDefaults();
                deleteFreshFilterDefaultsPendingFile();
            }
            validateSchemaReadiness();
            if (defaultBlackListInitializationPending) {
                initializeDefaultBlackList();
                deleteDefaultBlackListPendingFile();
            }
        } catch (SQLiteException e) {
            failOpen(e);
            throw e;
        } catch (RuntimeException e) {
            failOpen(e);
            throw e;
        }
    }

    void close() {
        assertOwningQueue();
        if (state == State.CLOSED) {
            return;
        }
        if (state == State.NEW) {
            throw new IllegalStateException("Cleargram database cannot be closed from NEW state");
        }
        if (state == State.IN_TRANSACTION) {
            throw new IllegalStateException("Cleargram database cannot be closed during a transaction");
        }

        closeHandle();
        if (state == State.OPEN) {
            state = State.CLOSED;
        }
    }

    List<WhiteListRow> loadWhiteListRows() throws SQLiteException {
        assertOpenOperation();
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT canonical_pattern, enabled FROM white_list_rules ORDER BY order_key ASC");
            List<WhiteListRow> rows = new ArrayList<>();
            while (cursor.next()) {
                if (cursor.isNull(0)) {
                    throw new SQLiteException("Null canonical pattern in White List storage");
                }
                if (cursor.isNull(1) || cursor.getTypeOf(1) != SQLiteCursor.FIELD_TYPE_INT) {
                    throw new SQLiteException("Invalid White List enabled storage representation");
                }
                int enabled = cursor.intValue(1);
                if (enabled != 0 && enabled != 1) {
                    throw new SQLiteException("Invalid White List enabled value: " + enabled);
                }
                rows.add(new WhiteListRow(cursor.stringValue(0), enabled == 1));
            }
            return Collections.unmodifiableList(rows);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    List<BlackListRow> loadBlackListRows() throws SQLiteException {
        assertOpenOperation();
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT canonical_pattern, action, enabled FROM black_list_rules ORDER BY order_key ASC");
            List<BlackListRow> rows = new ArrayList<>();
            while (cursor.next()) {
                if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(2)
                        || cursor.getTypeOf(1) != SQLiteCursor.FIELD_TYPE_INT
                        || cursor.getTypeOf(2) != SQLiteCursor.FIELD_TYPE_INT) {
                    throw new SQLiteException("Invalid Black List storage representation");
                }
                int action = cursor.intValue(1);
                int enabled = cursor.intValue(2);
                if (enabled != 0 && enabled != 1) {
                    throw new SQLiteException("Invalid Black List enabled value: " + enabled);
                }
                rows.add(new BlackListRow(cursor.stringValue(0), actionFromStorage(action), enabled == 1));
            }
            return Collections.unmodifiableList(rows);
        } finally {
            if (cursor != null) { cursor.dispose(); }
        }
    }

    void insertWhiteListRow(String canonicalPattern, boolean enabled) throws SQLiteException {
        assertOpenOperation();
        requirePattern(canonicalPattern);
        runInTransaction(() -> insertWhiteListRowInTransaction(canonicalPattern, enabled));
    }

    void insertBlackListRow(String canonicalPattern, NoiseAction action, boolean enabled) throws SQLiteException {
        assertOpenOperation();
        requirePattern(canonicalPattern);
        requireBlackListAction(action);
        runInTransaction(() -> insertBlackListRowInTransaction(canonicalPattern, action, enabled));
    }

    boolean deleteWhiteListRow(String canonicalPattern) throws SQLiteException {
        assertOpenOperation();
        requirePattern(canonicalPattern);
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("DELETE FROM white_list_rules WHERE canonical_pattern = ?");
            statement.bindString(1, canonicalPattern);
            statement.stepThis();
            return readSingleRowChangeResult();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    boolean setWhiteListRowEnabled(String canonicalPattern, boolean enabled) throws SQLiteException {
        assertOpenOperation();
        requirePattern(canonicalPattern);
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("UPDATE white_list_rules SET enabled = ? WHERE canonical_pattern = ?");
            statement.bindInteger(1, enabled ? 1 : 0);
            statement.bindString(2, canonicalPattern);
            statement.stepThis();
            return readSingleRowChangeResult();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    boolean deleteBlackListRow(String canonicalPattern) throws SQLiteException {
        assertOpenOperation();
        requirePattern(canonicalPattern);
        return deleteRow(BLACK_LIST_TABLE, canonicalPattern);
    }

    boolean setBlackListRowEnabled(String canonicalPattern, boolean enabled) throws SQLiteException {
        assertOpenOperation();
        requirePattern(canonicalPattern);
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("UPDATE black_list_rules SET enabled = ? WHERE canonical_pattern = ?");
            statement.bindInteger(1, enabled ? 1 : 0);
            statement.bindString(2, canonicalPattern);
            statement.stepThis();
            return readSingleRowChangeResult();
        } finally {
            if (statement != null) { statement.dispose(); }
        }
    }

    NoiseAction loadBlackListAction() throws SQLiteException {
        assertOpenOperation();
        return loadBlackListActionInternal();
    }

    private NoiseAction loadBlackListActionInternal() throws SQLiteException {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT value FROM settings WHERE key = '" + BLACK_LIST_ACTION_KEY + "'");
            if (!cursor.next() || cursor.isNull(0)) {
                throw new IllegalStateException("Missing Black List action setting");
            }
            String value = cursor.stringValue(0);
            if (cursor.next()) {
                throw new IllegalStateException("Duplicate Black List action setting");
            }
            if ("1".equals(value)) return NoiseAction.HIDE;
            if ("2".equals(value)) return NoiseAction.COLLAPSE;
            throw new IllegalStateException("Invalid Black List action setting");
        } finally {
            if (cursor != null) cursor.dispose();
        }
    }

    void updateBlackListAction(NoiseAction action) throws SQLiteException {
        assertOpenOperation();
        requireBlackListAction(action);
        runInTransaction(() -> {
            SQLitePreparedStatement setting = null;
            SQLitePreparedStatement rules = null;
            try {
                setting = database.executeFast("UPDATE settings SET value = ? WHERE key = '" + BLACK_LIST_ACTION_KEY + "'");
                setting.bindString(1, action == NoiseAction.HIDE ? "1" : "2");
                setting.stepThis();
                if (!readSingleRowChangeResult()) throw new IllegalStateException("Missing Black List action setting");
                rules = database.executeFast("UPDATE black_list_rules SET action = ?");
                rules.bindInteger(1, actionToStorage(action));
                rules.stepThis();
            } finally {
                if (setting != null) setting.dispose();
                if (rules != null) rules.dispose();
            }
        });
    }

    DuplicateVideoSettingsRow loadDuplicateVideoSettings() throws SQLiteException {
        assertOpenOperation();
        return new DuplicateVideoSettingsRow(
                parseRequiredSetting(DUPLICATE_VIDEO_ENABLED_KEY),
                parseRequiredSetting(DUPLICATE_VIDEO_MATCH_MODE_KEY));
    }

    void updateDuplicateVideoEnabled(boolean enabled) throws SQLiteException {
        assertOpenOperation();
        updateRequiredSetting(DUPLICATE_VIDEO_ENABLED_KEY, enabled ? "1" : "0");
    }

    void updateDuplicateVideoMatchMode(int matchMode) throws SQLiteException {
        assertOpenOperation();
        if (matchMode != 1 && matchMode != 2) {
            throw new IllegalArgumentException("matchMode");
        }
        updateRequiredSetting(DUPLICATE_VIDEO_MATCH_MODE_KEY, Integer.toString(matchMode));
    }

    List<DuplicateVideoRowClassification> classifyDuplicateVideoRows(
            int matchMode,
            int keyVersion,
            List<byte[]> matchKeys,
            long batchTimestamp
    ) throws SQLiteException {
        assertOpenOperation();
        requireDuplicateVideoArguments(matchMode, keyVersion, matchKeys, batchTimestamp);
        List<DuplicateVideoRowClassification> result = new ArrayList<>(matchKeys.size());
        runInTransaction(() -> {
            for (byte[] matchKey : matchKeys) {
                if (findDuplicateVideoFirstSeen(matchMode, keyVersion, matchKey) == null) {
                    insertDuplicateVideoRow(matchMode, keyVersion, matchKey, batchTimestamp);
                    result.add(DuplicateVideoRowClassification.FIRST_SEEN);
                } else {
                    updateDuplicateVideoLastSeen(matchMode, keyVersion, matchKey, batchTimestamp);
                    result.add(DuplicateVideoRowClassification.DUPLICATE);
                }
            }
        });
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    void clearDuplicateVideoHistory() throws SQLiteException {
        assertOpenOperation();
        runInTransaction(() -> executeSql("DELETE FROM " + DUPLICATE_VIDEO_HISTORY_TABLE));
    }

    private int parseRequiredSetting(String key) throws SQLiteException {
        String value = loadSettingValue(key);
        if (value == null) {
            throw new IllegalStateException("Missing Duplicate Video setting: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid Duplicate Video setting: " + key, exception);
        }
    }

    private void updateRequiredSetting(String key, String value) throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("UPDATE settings SET value = ? WHERE key = ?");
            statement.bindString(1, value);
            statement.bindString(2, key);
            statement.stepThis();
            if (!readSingleRowChangeResult()) {
                throw new IllegalStateException("Missing Duplicate Video setting: " + key);
            }
        } finally {
            if (statement != null) { statement.dispose(); }
        }
    }

    private Long findDuplicateVideoFirstSeen(int matchMode, int keyVersion, byte[] matchKey)
            throws SQLiteException {
        SQLitePreparedStatement statement = null;
        SQLiteCursor cursor = null;
        try {
            statement = database.executeFast("SELECT first_seen FROM duplicate_video_history WHERE match_mode = ? AND key_version = ? AND match_key = ?");
            statement.bindInteger(1, matchMode);
            statement.bindInteger(2, keyVersion);
            statement.bindByteBuffer(3, ByteBuffer.wrap(matchKey));
            cursor = new SQLiteCursor(statement);
            if (!cursor.next()) { return null; }
            long firstSeen = cursor.longValue(0);
            if (cursor.next()) { throw new SQLiteException("Duplicate Duplicate Video history rows"); }
            return firstSeen;
        } finally {
            if (cursor != null) { cursor.dispose(); }
            else if (statement != null) { statement.dispose(); }
        }
    }

    private void insertDuplicateVideoRow(int matchMode, int keyVersion, byte[] matchKey, long timestamp)
            throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT INTO duplicate_video_history (match_mode, key_version, match_key, first_seen, last_seen) VALUES (?, ?, ?, ?, ?)");
            statement.bindInteger(1, matchMode);
            statement.bindInteger(2, keyVersion);
            statement.bindByteBuffer(3, ByteBuffer.wrap(matchKey));
            statement.bindLong(4, timestamp);
            statement.bindLong(5, timestamp);
            statement.stepThis();
        } finally {
            if (statement != null) { statement.dispose(); }
        }
    }

    private void updateDuplicateVideoLastSeen(int matchMode, int keyVersion, byte[] matchKey, long timestamp)
            throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("UPDATE duplicate_video_history SET last_seen = ? WHERE match_mode = ? AND key_version = ? AND match_key = ?");
            statement.bindLong(1, timestamp);
            statement.bindInteger(2, matchMode);
            statement.bindInteger(3, keyVersion);
            statement.bindByteBuffer(4, ByteBuffer.wrap(matchKey));
            statement.stepThis();
            if (!readSingleRowChangeResult()) { throw new IllegalStateException("Missing Duplicate Video history row"); }
        } finally {
            if (statement != null) { statement.dispose(); }
        }
    }

    private void requireDuplicateVideoArguments(
            int matchMode, int keyVersion, List<byte[]> matchKeys, long batchTimestamp
    ) {
        if ((matchMode != 1 && matchMode != 2) || keyVersion <= 0 || matchKeys == null
                || matchKeys.isEmpty() || batchTimestamp < 0) {
            throw new IllegalArgumentException("invalid Duplicate Video classification arguments");
        }
        for (byte[] matchKey : matchKeys) {
            if (matchKey == null || matchKey.length != 32) {
                throw new IllegalArgumentException("invalid Duplicate Video match key");
            }
        }
    }

    String loadSettingValue(String key) throws SQLiteException {
        assertOpenOperation();
        Objects.requireNonNull(key, "key");
        return loadSettingValueInternal(key);
    }

    private String loadSettingValueInternal(String key) throws SQLiteException {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT value FROM settings WHERE key = '"
                    + escapeSqlLiteral(key) + "'");
            if (!cursor.next()) {
                return null;
            }
            if (cursor.isNull(0)) {
                throw new SQLiteException("Null Cleargram setting value");
            }
            String value = cursor.stringValue(0);
            if (cursor.next()) {
                throw new SQLiteException("Duplicate Cleargram setting key");
            }
            return value;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    void upsertSettingValue(String key, String value) throws SQLiteException {
        assertOpenOperation();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        upsertSettingValueInternal(key, value);
    }

    private void upsertSettingValueInternal(String key, String value) throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)");
            statement.bindString(1, key);
            statement.bindString(2, value);
            statement.stepThis();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    private void initializeFreshInstallFilterDefaults() throws SQLiteException {
        runInTransaction(() -> {
            String seedVersion = loadSettingValueInternal(FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY);
            if (FRESH_FILTER_DEFAULTS_SEED_VERSION.equals(seedVersion)) {
                return;
            }
            if (seedVersion != null) {
                throw new IllegalStateException("Invalid fresh filter defaults seed version");
            }
            upsertSettingValueInternal(TelegramHideReactionsStorage.HIDE_REACTIONS_KEY,
                    FRESH_INSTALL_ENABLED_VALUE);
            upsertSettingValueInternal(DUPLICATE_VIDEO_ENABLED_KEY, FRESH_INSTALL_ENABLED_VALUE);
            upsertSettingValueInternal(
                    TelegramHideChannelEndAdvertisementStorage.HIDE_CHANNEL_END_ADVERTISEMENT_KEY,
                    FRESH_INSTALL_ENABLED_VALUE);
            upsertSettingValueInternal(TelegramHideFullscreenVideoAdvertisementStorage.SETTING_KEY,
                    FRESH_INSTALL_ENABLED_VALUE);
            upsertSettingValueInternal(TelegramHideChannelPinnedMessageHeaderStorage.SETTING_KEY,
                    FRESH_INSTALL_ENABLED_VALUE);
            upsertSettingValueInternal(TelegramMessageCtaButtonSettingsStorage.ENABLED_SETTING_KEY,
                    FRESH_INSTALL_ENABLED_VALUE);
            upsertSettingValueInternal(FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY,
                    FRESH_FILTER_DEFAULTS_SEED_VERSION);
        });
    }

    private void insertWhiteListRowInTransaction(String canonicalPattern, boolean enabled) throws SQLiteException {
        long nextOrderKey = readNextOrderKey();
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT INTO white_list_rules (canonical_pattern, enabled, order_key) VALUES (?, ?, ?)");
            statement.bindString(1, canonicalPattern);
            statement.bindInteger(2, enabled ? 1 : 0);
            statement.bindLong(3, nextOrderKey);
            statement.stepThis();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    private void insertBlackListRowInTransaction(
            String canonicalPattern,
            NoiseAction action,
            boolean enabled
    ) throws SQLiteException {
        long nextOrderKey = readNextOrderKey(BLACK_LIST_TABLE);
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT INTO black_list_rules (canonical_pattern, action, enabled, order_key) VALUES (?, ?, ?, ?)");
            statement.bindString(1, canonicalPattern);
            statement.bindInteger(2, actionToStorage(action));
            statement.bindInteger(3, enabled ? 1 : 0);
            statement.bindLong(4, nextOrderKey);
            statement.stepThis();
        } finally {
            if (statement != null) { statement.dispose(); }
        }
    }

    private void initializeDefaultBlackList() throws SQLiteException {
        runInTransaction(() -> {
            String seedVersion = loadSettingValueInternal(DEFAULT_BLACK_LIST_SEED_VERSION_KEY);
            if (DEFAULT_BLACK_LIST_SEED_VERSION.equals(seedVersion)) {
                return;
            }
            if (seedVersion != null) {
                throw new IllegalStateException("Invalid default Black List seed version");
            }
            if (hasBlackListRows()) {
                throw new IllegalStateException("Pending default Black List database already contains rules");
            }
            NoiseAction action = loadBlackListActionInternal();
            for (int index = 0; index < DEFAULT_BLACK_LIST_PATTERNS.length; index++) {
                insertDefaultBlackListRow(DEFAULT_BLACK_LIST_PATTERNS[index], action, index + 1);
            }
            insertDefaultBlackListSeedVersion();
        });
    }

    private boolean hasBlackListRows() throws SQLiteException {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT count(*) FROM " + BLACK_LIST_TABLE);
            if (!cursor.next() || cursor.isNull(0)) {
                throw new SQLiteException("Missing Black List row count");
            }
            return cursor.longValue(0) != 0;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private void insertDefaultBlackListRow(String canonicalPattern, NoiseAction action, int orderKey)
            throws SQLiteException {
        requirePattern(canonicalPattern);
        requireBlackListAction(action);
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT INTO black_list_rules (canonical_pattern, action, enabled, order_key) VALUES (?, ?, 1, ?)");
            statement.bindString(1, canonicalPattern);
            statement.bindInteger(2, actionToStorage(action));
            statement.bindInteger(3, orderKey);
            statement.stepThis();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    private void insertDefaultBlackListSeedVersion() throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("INSERT INTO settings (key, value) VALUES (?, ?)");
            statement.bindString(1, DEFAULT_BLACK_LIST_SEED_VERSION_KEY);
            statement.bindString(2, DEFAULT_BLACK_LIST_SEED_VERSION);
            statement.stepThis();
        } finally {
            if (statement != null) {
                statement.dispose();
            }
        }
    }

    private boolean beginDefaultBlackListInitialization() throws SQLiteException {
        if (defaultBlackListPendingFile.exists()) {
            return true;
        }
        if (databaseFile.exists()) {
            return false;
        }
        try {
            if (defaultBlackListPendingFile.createNewFile() || defaultBlackListPendingFile.exists()) {
                return true;
            }
        } catch (IOException exception) {
            throw new SQLiteException("Unable to create default Black List pending marker: "
                    + exception.getMessage());
        }
        throw new SQLiteException("Unable to create default Black List pending marker");
    }

    private boolean beginFreshFilterDefaultsInitialization() throws SQLiteException {
        boolean pendingMarkerExists = freshFilterDefaultsPendingFile.exists();
        if (!shouldCreateFreshFilterDefaultsPendingMarker(pendingMarkerExists, databaseFile.exists())) {
            return pendingMarkerExists;
        }
        try {
            if (freshFilterDefaultsPendingFile.createNewFile() || freshFilterDefaultsPendingFile.exists()) {
                return true;
            }
        } catch (IOException exception) {
            throw new SQLiteException("Unable to create fresh filter defaults pending marker: "
                    + exception.getMessage());
        }
        throw new SQLiteException("Unable to create fresh filter defaults pending marker");
    }

    private void deleteDefaultBlackListPendingFile() {
        if (defaultBlackListPendingFile.exists() && !defaultBlackListPendingFile.delete()) {
            FileLog.e(new IOException("Unable to delete default Black List pending marker: "
                    + defaultBlackListPendingFile.getAbsolutePath()));
        }
    }

    private void deleteFreshFilterDefaultsPendingFile() {
        if (freshFilterDefaultsPendingFile.exists() && !freshFilterDefaultsPendingFile.delete()) {
            FileLog.e(new IOException("Unable to delete fresh filter defaults pending marker: "
                    + freshFilterDefaultsPendingFile.getAbsolutePath()));
        }
    }

    private long readNextOrderKey() throws SQLiteException {
        return readNextOrderKey(WHITE_LIST_TABLE);
    }

    private long readNextOrderKey(String tableName) throws SQLiteException {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT MAX(order_key) FROM " + tableName);
            if (!cursor.next() || cursor.isNull(0)) {
                return 1;
            }
            long maximum = cursor.longValue(0);
            if (maximum <= 0 || maximum == Long.MAX_VALUE) {
                throw new SQLiteException("Invalid White List order key maximum");
            }
            return maximum + 1;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private boolean deleteRow(String tableName, String canonicalPattern) throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try {
            statement = database.executeFast("DELETE FROM " + tableName + " WHERE canonical_pattern = ?");
            statement.bindString(1, canonicalPattern);
            statement.stepThis();
            return readSingleRowChangeResult();
        } finally {
            if (statement != null) { statement.dispose(); }
        }
    }

    private boolean readSingleRowChangeResult() throws SQLiteException {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT changes()");
            if (!cursor.next()) {
                throw new SQLiteException("Missing SQLite change result");
            }
            int changes = cursor.intValue(0);
            if (changes == 0) {
                return false;
            }
            if (changes == 1) {
                return true;
            }
            throw new SQLiteException("Unexpected SQLite change result: " + changes);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private void requirePattern(String canonicalPattern) {
        if (canonicalPattern == null) {
            throw new IllegalArgumentException("canonicalPattern");
        }
    }

    private void assertOpenOperation() {
        assertOwningQueue();
        if (state != State.OPEN) {
            throw new IllegalStateException("White List operation requires OPEN state");
        }
    }

    private void migrateToVersionOne() throws SQLiteException {
        runInTransaction(() -> {
            executeSql(CREATE_WHITE_LIST_TABLE_SQL);
            validateWhiteListStructuralSchema();
            executeSql("PRAGMA user_version = 1");
        });
    }

    private void migrateToVersionTwo() throws SQLiteException {
        runInTransaction(() -> {
            executeSql(CREATE_BLACK_LIST_TABLE_SQL);
            validateBlackListStructuralSchema();
            executeSql("PRAGMA user_version = 2");
        });
    }

    private void migrateToVersionThree() throws SQLiteException {
        runInTransaction(() -> {
            executeSql(CREATE_SETTINGS_TABLE_SQL);
            executeSql("DELETE FROM white_list_rules");
            executeSql("DELETE FROM black_list_rules");
            executeSql("INSERT INTO settings (key, value) VALUES ('" + BLACK_LIST_ACTION_KEY + "', '2')");
            executeSql("PRAGMA user_version = 3");
        });
    }

    private void migrateToVersionFour() throws SQLiteException {
        runInTransaction(() -> {
            executeSql(CREATE_DUPLICATE_VIDEO_HISTORY_TABLE_SQL);
            executeSql("INSERT INTO settings (key, value) VALUES ('" + DUPLICATE_VIDEO_ENABLED_KEY + "', '0')");
            executeSql("INSERT INTO settings (key, value) VALUES ('" + DUPLICATE_VIDEO_MATCH_MODE_KEY + "', '1')");
            executeSql("PRAGMA user_version = 4");
        });
    }

    private void runInTransaction(TransactionOperation operation) throws SQLiteException {
        assertOwningQueue();
        if (state != State.OPEN) {
            throw new IllegalStateException("Cleargram transaction requires OPEN state");
        }

        executeSql("BEGIN");
        state = State.IN_TRANSACTION;
        try {
            operation.run();
            executeSql("COMMIT");
            state = State.OPEN;
        } catch (SQLiteException e) {
            rollbackAfterFailure(e);
            throw e;
        } catch (RuntimeException e) {
            rollbackAfterFailure(e);
            throw e;
        }
    }

    private void rollbackAfterFailure(Throwable primaryFailure) {
        try {
            executeSql("ROLLBACK");
            state = State.OPEN;
        } catch (SQLiteException | RuntimeException rollbackFailure) {
            primaryFailure.addSuppressed(rollbackFailure);
            state = State.FAILED;
        }
    }

    private void validateSchemaReadiness() throws SQLiteException {
        if (readUserVersion() != DATABASE_VERSION) {
            throw new SQLiteException("Cleargram schema version is not ready");
        }
        validateWhiteListStructuralSchema();
        validateBlackListStructuralSchema();
        if (!database.tableExists(SETTINGS_TABLE)) {
            throw new SQLiteException("Missing Cleargram table: " + SETTINGS_TABLE);
        }
        if (!database.tableExists(DUPLICATE_VIDEO_HISTORY_TABLE)) {
            throw new SQLiteException("Missing Cleargram table: " + DUPLICATE_VIDEO_HISTORY_TABLE);
        }
    }

    private void validateWhiteListStructuralSchema() throws SQLiteException {
        if (!database.tableExists(WHITE_LIST_TABLE)) {
            throw new SQLiteException("Missing Cleargram table: " + WHITE_LIST_TABLE);
        }
        validateWhiteListColumns();
        validateOrderKeyUniqueConstraint(WHITE_LIST_TABLE);
    }

    private void validateBlackListStructuralSchema() throws SQLiteException {
        if (!database.tableExists(BLACK_LIST_TABLE)) {
            throw new SQLiteException("Missing Cleargram table: " + BLACK_LIST_TABLE);
        }
        validateBlackListColumns();
        validateOrderKeyUniqueConstraint(BLACK_LIST_TABLE);
    }

    private void validateWhiteListColumns() throws SQLiteException {
        SQLiteCursor cursor = null;
        int expectedIndex = 0;
        try {
            cursor = database.queryFinalized("PRAGMA table_info(" + WHITE_LIST_TABLE + ")");
            while (cursor.next()) {
                if (expectedIndex >= 3) {
                    throw new SQLiteException("Unexpected Cleargram schema column");
                }
                validateColumn(cursor, expectedIndex);
                expectedIndex++;
            }
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        if (expectedIndex != 3) {
            throw new SQLiteException("Incomplete Cleargram schema columns");
        }
    }

    private void validateBlackListColumns() throws SQLiteException {
        SQLiteCursor cursor = null;
        int expectedIndex = 0;
        try {
            cursor = database.queryFinalized("PRAGMA table_info(" + BLACK_LIST_TABLE + ")");
            String[] names = {"canonical_pattern", "action", "enabled", "order_key"};
            String[] types = {"TEXT", "INTEGER", "INTEGER", "INTEGER"};
            while (cursor.next()) {
                if (expectedIndex >= names.length
                        || cursor.intValue(0) != expectedIndex
                        || !names[expectedIndex].equals(cursor.stringValue(1))
                        || !types[expectedIndex].equals(cursor.stringValue(2))
                        || cursor.intValue(3) != 1
                        || !cursor.isNull(4)
                        || cursor.intValue(5) != (expectedIndex == 0 ? 1 : 0)) {
                    throw new SQLiteException("Black List schema column contract mismatch");
                }
                expectedIndex++;
            }
        } finally {
            if (cursor != null) { cursor.dispose(); }
        }
        if (expectedIndex != 4) { throw new SQLiteException("Incomplete Black List schema columns"); }
    }

    private void validateColumn(SQLiteCursor cursor, int expectedIndex) throws SQLiteException {
        String[] names = {"canonical_pattern", "enabled", "order_key"};
        String[] types = {"TEXT", "INTEGER", "INTEGER"};
        int expectedPrimaryKey = expectedIndex == 0 ? 1 : 0;
        if (cursor.intValue(0) != expectedIndex
                || !names[expectedIndex].equals(cursor.stringValue(1))
                || !types[expectedIndex].equals(cursor.stringValue(2))
                || cursor.intValue(3) != 1
                || !cursor.isNull(4)
                || cursor.intValue(5) != expectedPrimaryKey) {
            throw new SQLiteException("Cleargram schema column contract mismatch");
        }
    }

    private void validateOrderKeyUniqueConstraint(String tableName) throws SQLiteException {
        SQLiteCursor cursor = null;
        boolean found = false;
        try {
            cursor = database.queryFinalized("PRAGMA index_list(" + tableName + ")");
            while (cursor.next()) {
                if (cursor.intValue(2) == 1 && isOrderKeyOnlyIndex(cursor.stringValue(1))) {
                    found = true;
                    break;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        if (!found) {
            throw new SQLiteException("Missing unique order_key constraint");
        }
    }

    private NoiseAction actionFromStorage(int action) throws SQLiteException {
        if (action == 1) { return NoiseAction.HIDE; }
        if (action == 2) { return NoiseAction.COLLAPSE; }
        throw new SQLiteException("Invalid Black List action: " + action);
    }

    private int actionToStorage(NoiseAction action) {
        requireBlackListAction(action);
        return action == NoiseAction.HIDE ? 1 : 2;
    }

    private void requireBlackListAction(NoiseAction action) {
        if (action != NoiseAction.HIDE && action != NoiseAction.COLLAPSE) {
            throw new IllegalArgumentException("Black List action must be HIDE or COLLAPSE");
        }
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private boolean isOrderKeyOnlyIndex(String indexName) throws SQLiteException {
        SQLiteCursor cursor = null;
        int count = 0;
        try {
            cursor = database.queryFinalized("PRAGMA index_info(\"" + indexName.replace("\"", "\"\"") + "\")");
            while (cursor.next()) {
                if (count != 0 || !"order_key".equals(cursor.stringValue(2))) {
                    return false;
                }
                count++;
            }
            return count == 1;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private int readUserVersion() throws SQLiteException {
        Integer version = database.executeInt("PRAGMA user_version");
        if (version == null) {
            throw new SQLiteException("Unable to read Cleargram schema version");
        }
        return version;
    }

    static boolean shouldCreateFreshFilterDefaultsPendingMarker(
            boolean pendingMarkerExists,
            boolean databaseExists
    ) {
        return !pendingMarkerExists && !databaseExists;
    }

    private void executeSql(String sql) throws SQLiteException {
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

    private void failOpen(Throwable ignored) {
        state = State.FAILED;
        closeHandle();
    }

    private void closeHandle() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    private void assertOwningQueue() {
        Handler handler = storageQueue.getHandler();
        if (handler == null || Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("Cleargram database operation must run on the owning Cleargram storage queue");
        }
    }
}
