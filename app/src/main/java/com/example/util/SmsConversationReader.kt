package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

data class SmsConversationInfo(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val messageCount: Int = 1
)

object SmsConversationReader {

    private const val MAX_ROWS_TO_SCAN = 4000
    private const val MAX_CONVERSATIONS = 250

    /**
     * Reads SMS conversations from the device safely.
     * Guards against missing permissions, NULL columns, malformed rows, CursorWindow errors,
     * and very large SMS inboxes so conversation loading is fast and never crashes.
     */
    fun getConversations(context: Context): List<SmsConversationInfo> {
        val appContext = context.applicationContext ?: context
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return emptyList()
        }

        val conversations = LinkedHashMap<String, SmsConversationInfo>()
        val uris = listOf(
            Uri.parse("content://sms/inbox"),
            Uri.parse("content://sms")
        )

        for (uri in uris) {
            val loaded = queryUriSafely(appContext, uri, conversations)
            if (loaded && conversations.isNotEmpty()) {
                break
            }
        }

        return conversations.values.sortedByDescending { it.date }
    }

    private fun queryUriSafely(
        context: Context,
        uri: Uri,
        conversations: LinkedHashMap<String, SmsConversationInfo>
    ): Boolean {
        val projection = arrayOf("_id", "thread_id", "address", "body", "date")
        return try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            ) ?: return false

            cursor.use { c ->
                val threadIdIndex = c.getColumnIndex("thread_id")
                val addressIndex = c.getColumnIndex("address")
                val bodyIndex = c.getColumnIndex("body")
                val dateIndex = c.getColumnIndex("date")
                if (addressIndex == -1) return false

                var scannedRows = 0
                while (scannedRows < MAX_ROWS_TO_SCAN && safeMoveToNext(c)) {
                    scannedRows++
                    try {
                        val address = if (!c.isNull(addressIndex)) {
                            c.getString(addressIndex)?.trim().orEmpty()
                        } else ""
                        if (address.isBlank()) continue

                        val key = address.lowercase()
                        val existing = conversations[key]
                        if (existing == null) {
                            if (conversations.size >= MAX_CONVERSATIONS) continue

                            val threadId = if (threadIdIndex != -1 && !c.isNull(threadIdIndex)) {
                                runCatching { c.getLong(threadIdIndex) }.getOrDefault(0L)
                            } else 0L

                            val body = if (bodyIndex != -1 && !c.isNull(bodyIndex)) {
                                runCatching { c.getString(bodyIndex)?.trim().orEmpty() }.getOrDefault("")
                            } else ""

                            val date = if (dateIndex != -1 && !c.isNull(dateIndex)) {
                                runCatching { c.getLong(dateIndex) }.getOrDefault(0L)
                            } else 0L

                            conversations[key] = SmsConversationInfo(
                                threadId = threadId,
                                address = address,
                                snippet = body.take(120),
                                date = date,
                                messageCount = 1
                            )
                        } else {
                            conversations[key] = existing.copy(
                                messageCount = existing.messageCount + 1
                            )
                        }
                    } catch (_: Throwable) {
                        // Skip individual malformed/corrupted SMS rows safely
                    }
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun safeMoveToNext(cursor: android.database.Cursor): Boolean {
        return try {
            cursor.moveToNext()
        } catch (_: Throwable) {
            false
        }
    }
}
