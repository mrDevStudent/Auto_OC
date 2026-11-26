package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.MeetRepository
import com.example.data.SettingsDataStore
import com.example.notifications.MeetNotificationManager
import com.example.scheduler.AlarmScheduler

class MeetPilotApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set
    lateinit var repository: MeetRepository
        private set
    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var notificationManager: MeetNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        alarmScheduler = AlarmScheduler(this)
        repository = MeetRepository(database.meetingDao(), alarmScheduler)
        settingsDataStore = SettingsDataStore(this)
        notificationManager = MeetNotificationManager(this)
    }
}
