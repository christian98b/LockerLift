package com.lockerlift.wear

import android.app.Application
import com.lockerlift.core.database.LockerLiftDatabase

class LockerLiftWearApp : Application() {

    val database by lazy { LockerLiftDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
