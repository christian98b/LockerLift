package com.lockerlift.mobile

import android.app.Application
import com.lockerlift.core.database.LockerLiftDatabase

class LockerLiftMobileApp : Application() {

    val database by lazy { LockerLiftDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
