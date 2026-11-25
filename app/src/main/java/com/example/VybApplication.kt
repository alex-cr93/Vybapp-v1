package com.example

import android.app.Application
import com.example.core.NotificationHelper

class VybApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }
}
