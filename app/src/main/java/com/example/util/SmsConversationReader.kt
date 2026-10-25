package com.example.util

import android.content.Context
import android.net.Uri

data class SmsConversationInfo(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val messageCount: Int = 1
)

object SmsConversationReader {

    /**
     * Reads SMS conversations from the device.
     * Uses `content://sms` or `content://sms/inbox` with projection on thread_id, address, body, and date,
     * grouping by sender address so that every conversation item has the exact sender address
     * (e.g., 'B.QMEHRIRAN' or phone number) along with the latest message snippet and date.
     */
    fun getConversations(context: Context): List<SmsConversationInfo> {
        val conversations = LinkedHashMap<String, SmsConversationInfo>()
        val uri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "thread_id", "address", "body", "date")

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use { c ->
                val threadIdIndex = c.getColumnIndex("thread_id")
                val addressIndex = c.getColumnIndex("address")
                val bodyIndex = c.getColumnIndex("body")
                val dateIndex = c.getColumnIndex("date")

                while (c.moveToNext()) {
                    val address = if (addressIndex != -1) c.getString(addressIndex)?.trim() ?: "" else ""
                    if (address.isBlank()) continue

                    val threadId = if (threadIdIndex != -1) c.getLong(threadIdIndex) else 0L
                    val body = if (bodyIndex != -1) c.getString(bodyIndex)?.trim() ?: "" else ""
                    val date = if (dateIndex != -1) c.getLong(dateIndex) else 0L

                    val existing = conversations[address]
                    if (existing == null) {
                        conversations[address] = SmsConversationInfo(
                            threadId = threadId,
                            address = address,
                            snippet = body.take(120),
                            date = date,
                            messageCount = 1
                        )
                    } else {
                        conversations[address] = existing.copy(
                            messageCount = existing.messageCount + 1
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to inbox if general sms uri is restricted
            try {
                val inboxUri = Uri.parse("content://sms/inbox")
                val cursor = context.contentResolver.query(
                    inboxUri,
                    projection,
                    null,
                    null,
                    "date DESC"
                )
                cursor?.use { c ->
                    val threadIdIndex = c.getColumnIndex("thread_id")
                    val addressIndex = c.getColumnIndex("address")
                    val bodyIndex = c.getColumnIndex("body")
                    val dateIndex = c.getColumnIndex("date")

                    while (c.moveToNext()) {
                        val address = if (addressIndex != -1) c.getString(addressIndex)?.trim() ?: "" else ""
                        if (address.isBlank()) continue

                        val threadId = if (threadIdIndex != -1) c.getLong(threadIdIndex) else 0L
                        val body = if (bodyIndex != -1) c.getString(bodyIndex)?.trim() ?: "" else ""
                        val date = if (dateIndex != -1) c.getLong(dateIndex) else 0L

                        val existing = conversations[address]
                        if (existing == null) {
                            conversations[address] = SmsConversationInfo(
                                threadId = threadId,
                                address = address,
                                snippet = body.take(120),
                                date = date,
                                messageCount = 1
                            )
                        } else {
                            conversations[address] = existing.copy(
                                messageCount = existing.messageCount + 1
                            )
                        }
                    }
                }
            } catch (ignored: Exception) {
                // Return whatever collected or empty
            }
        }

        return conversations.values.sortedByDescending { it.date }
    }
}
