package com.example.secwatch.notify

interface Notifier {
    fun send(webhookUrl: String, message: String)
}
