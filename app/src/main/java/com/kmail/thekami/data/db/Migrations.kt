package com.kmail.thekami.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the account provider type and the Gmail id mapping table. Existing accounts stay IMAP. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN providerType TEXT NOT NULL DEFAULT 'IMAP'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gmail_ids` (`accountId` TEXT NOT NULL, `uid` INTEGER NOT NULL, " +
                "`gmailId` TEXT NOT NULL, PRIMARY KEY(`accountId`, `uid`), " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_gmail_ids_accountId_gmailId` ON `gmail_ids` (`accountId`, `gmailId`)",
        )
    }
}
