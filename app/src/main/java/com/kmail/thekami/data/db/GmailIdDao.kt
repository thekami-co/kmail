package com.kmail.thekami.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GmailIdDao {
    @Query("SELECT * FROM gmail_ids WHERE accountId = :accountId AND gmailId IN (:gmailIds)")
    suspend fun byGmailIds(accountId: String, gmailIds: List<String>): List<GmailIdEntity>

    @Query("SELECT * FROM gmail_ids WHERE accountId = :accountId AND uid IN (:uids)")
    suspend fun byUids(accountId: String, uids: List<Long>): List<GmailIdEntity>

    /** Returns -1 when the (account, uid) or (account, gmailId) pair already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: GmailIdEntity): Long
}
