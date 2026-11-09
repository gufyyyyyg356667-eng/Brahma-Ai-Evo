package com.brahma.connect.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Matches "SMS sent" callbacks from the system to the request that started them.
 * Requests that never receive a callback are forgotten by the caller on timeout.
 */
object SmsStatusTracker {
    private val pending = ConcurrentHashMap<String, CompletableFuture<Int>>()

    fun expect(requestId: String): CompletableFuture<Int> {
        val future = CompletableFuture<Int>()
        pending[requestId] = future
        return future
    }

    fun complete(requestId: String, resultCode: Int) {
        pending.remove(requestId)?.complete(resultCode)
    }

    fun forget(requestId: String) {
        pending.remove(requestId)
    }
}

/** Receives the system's sent-status callback for each SMS this app queues. Not exported. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        SmsStatusTracker.complete(requestId, resultCode)
    }

    companion object {
        const val ACTION = "com.brahma.connect.SMS_STATUS"
        const val EXTRA_REQUEST_ID = "sms_request_id"
    }
}
