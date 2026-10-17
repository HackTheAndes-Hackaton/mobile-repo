package com.tiskz.app

import android.app.Application
import com.tiskz.app.call.CallManager
import com.tiskz.app.call.CallNotifications
import com.tiskz.app.data.AppContainer

class TiskzApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CallManager.init(this, container)
        CallNotifications.createChannel(this)
    }
}
