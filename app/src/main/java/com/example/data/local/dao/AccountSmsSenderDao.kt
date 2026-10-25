package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AccountSmsSenderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountSmsSenderDao {

    @Query("SELECT * FROM account_sms_senders WHERE accountId = :accountId")
    fun getSendersForAccount(accountId: Long): Flow<List<AccountSmsSenderEntity>>

    @Query("SELECT * FROM account_sms_senders WHERE accountId = :accountId")
    suspend fun getSendersForAccountList(accountId: Long): List<AccountSmsSenderEntity>

    @Query("SELECT * FROM account_sms_senders")
    fun getAllSenders(): Flow<List<AccountSmsSenderEntity>>

    @Query("SELECT * FROM account_sms_senders")
    suspend fun getAllSendersList(): List<AccountSmsSenderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sender: AccountSmsSenderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(senders: List<AccountSmsSenderEntity>)

    @Delete
    suspend fun delete(sender: AccountSmsSenderEntity)

    @Query("DELETE FROM account_sms_senders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM account_sms_senders WHERE accountId = :accountId AND address = :address")
    suspend fun deleteByAccountIdAndAddress(accountId: Long, address: String)

    @Query("DELETE FROM account_sms_senders WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)
}
