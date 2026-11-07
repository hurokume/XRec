package io.github.hurokume.xrec.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

internal object DatabaseFileMigration {
    const val DATABASE_NAME = "xrec.db"

    // This identifier must remain readable to import databases from earlier releases/backups.
    private const val LEGACY_DATABASE_NAME = "timerecorder.db"

    @Synchronized
    fun prepare(context: Context): String {
        val destination = context.getDatabasePath(DATABASE_NAME)
        val source = context.getDatabasePath(LEGACY_DATABASE_NAME)
        if (destination.exists() || !source.exists()) return DATABASE_NAME

        // Before Room opens any connection, recover journals and checkpoint all WAL records.
        // Never let Android's default corruption handler delete the only copy of user data.
        SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE) {
            throw SQLiteException("Cannot migrate the existing XRec database: corruption detected")
        }.use { database ->
            database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { result ->
                check(result.moveToFirst() && result.getInt(0) == 0) {
                    "Cannot migrate the existing XRec database while it is busy"
                }
            }
            database.rawQuery("PRAGMA journal_mode=DELETE", null).use { result ->
                check(result.moveToFirst() && result.getString(0).equals("delete", true)) {
                    "Cannot consolidate the existing XRec database journals"
                }
            }
        }

        // Same-directory rename is atomic: an interrupted launch leaves either the original
        // or the complete destination. A failure must not fall through to creating an empty DB.
        check(source.renameTo(destination)) { "Cannot rename the existing XRec database" }
        return DATABASE_NAME
    }
}
