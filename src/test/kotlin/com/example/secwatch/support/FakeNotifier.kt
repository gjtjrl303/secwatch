package com.example.secwatch.support

import com.example.secwatch.notify.Notifier
import com.example.secwatch.notify.NotifyFailedException
import java.util.concurrent.CopyOnWriteArrayList

class FakeNotifier : Notifier {
    val sent: MutableList<Pair<String, String>> = CopyOnWriteArrayList()
    var fail = false
    var delayMillis = 0L
    var onSend: (() -> Unit)? = null

    fun reset() {
        sent.clear()
        fail = false
        delayMillis = 0L
        onSend = null
    }

    override fun send(webhookUrl: String, message: String) {
        if (delayMillis > 0) Thread.sleep(delayMillis)
        onSend?.invoke()
        if (fail) throw NotifyFailedException("fake failure")
        sent.add(webhookUrl to message)
    }
}
