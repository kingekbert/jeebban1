package com.example.data.repository

import com.example.data.local.dao.AccountSmsSenderDao
import com.example.data.local.entity.AccountSmsSenderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AccountSmsSenderRepository(
    private val dao: AccountSmsSenderDao
) {

    fun getSendersForAccount(accountId: Long): Flow<List<AccountSmsSenderEntity>> {
        return dao.getSendersForAccount(accountId)
    }

    suspend fun getSendersForAccountList(accountId: Long): List<AccountSmsSenderEntity> =
        withContext(Dispatchers.IO) {
            dao.getSendersForAccountList(accountId)
        }

    fun getAllSenders(): Flow<List<AccountSmsSenderEntity>> {
        return dao.getAllSenders()
    }

    suspend fun getAllSendersList(): List<AccountSmsSenderEntity> =
        withContext(Dispatchers.IO) {
            dao.getAllSendersList()
        }

    suspend fun setSendersForAccount(accountId: Long, addresses: List<String>) =
        withContext(Dispatchers.IO) {
            dao.deleteAllForAccount(accountId)
            val distinctList = addresses
                .map { normalizeSenderAddress(it) }
                .filter { it.isNotBlank() }
                .distinct()
            val entities = distinctList.map {
                AccountSmsSenderEntity(accountId = accountId, address = it)
            }
            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
            }
        }

    suspend fun addSender(accountId: Long, address: String): Long =
        withContext(Dispatchers.IO) {
            val normalized = normalizeSenderAddress(address)
            if (normalized.isBlank()) return@withContext 0L
            dao.insert(AccountSmsSenderEntity(accountId = accountId, address = normalized))
        }

    suspend fun removeSender(accountId: Long, address: String) =
        withContext(Dispatchers.IO) {
            val normalized = normalizeSenderAddress(address)
            dao.deleteByAccountIdAndAddress(accountId, normalized)
        }

    companion object {
        fun normalizeSenderAddress(address: String): String {
            return address.trim()
        }

        fun matchesSender(smsSender: String, registeredSender: String): Boolean {
            val s1 = normalizeSenderAddress(smsSender)
            val s2 = normalizeSenderAddress(registeredSender)
            if (s1.equals(s2, ignoreCase = true)) return true

            // Handle phone number prefixes like +98 vs 0098 vs 09 vs 98
            val digits1 = s1.filter { it.isDigit() }
            val digits2 = s2.filter { it.isDigit() }
            if (digits1.isNotEmpty() && digits2.isNotEmpty()) {
                val norm1 = digits1.removePrefix("0098").removePrefix("98").trimStart('0')
                val norm2 = digits2.removePrefix("0098").removePrefix("98").trimStart('0')
                if (norm1.isNotEmpty() && norm1 == norm2) return true
            }
            return false
        }
    }
}
