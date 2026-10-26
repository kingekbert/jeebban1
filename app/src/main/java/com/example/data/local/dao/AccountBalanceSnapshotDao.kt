package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AccountBalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountBalanceSnapshotDao {

    @Query("SELECT * FROM account_balance_snapshots ORDER BY smsDate DESC")
    fun getAll(): Flow<List<AccountBalanceSnapshotEntity>>

    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId ORDER BY smsDate DESC")
    fun getByAccountId(accountId: Long): Flow<List<AccountBalanceSnapshotEntity>>

    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId ORDER BY smsDate DESC")
    suspend fun getListByAccountId(accountId: Long): List<AccountBalanceSnapshotEntity>

    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId ORDER BY smsDate DESC LIMIT 1")
    suspend fun getLatestSnapshot(accountId: Long): AccountBalanceSnapshotEntity?

    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId ORDER BY smsDate DESC LIMIT 1")
    fun getLatestSnapshotFlow(accountId: Long): Flow<AccountBalanceSnapshotEntity?>

    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId AND smsHash = :smsHash LIMIT 1")
    suspend fun getByHash(accountId: Long, smsHash: String): AccountBalanceSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: AccountBalanceSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<AccountBalanceSnapshotEntity>)

    @Delete
    suspend fun delete(snapshot: AccountBalanceSnapshotEntity)

    @Query("DELETE FROM account_balance_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM account_balance_snapshots WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)

    @Query("DELETE FROM account_balance_snapshots")
    suspend fun deleteAll()
}
