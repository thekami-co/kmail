package com.kmail.thekami.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Maps a Gmail message id (64-bit hex string) to the 32-bit UID the rest of the app uses.
 * Removed automatically when its account is deleted.
 */
@Entity(
    tableName = "gmail_ids",
    primaryKeys = ["accountId", "uid"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId", "gmailId"], unique = true)],
)
class GmailIdEntity(
    val accountId: String,
    val uid: Long,
    val gmailId: String,
)
