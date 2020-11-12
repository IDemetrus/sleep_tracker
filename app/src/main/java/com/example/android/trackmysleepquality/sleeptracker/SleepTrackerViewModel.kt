/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabase
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimerTask as TimerTask

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(val database: SleepDatabaseDao,
                            application: Application) : AndroidViewModel(application) {
    //Use Coroutine
    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var tonight = MutableLiveData<SleepNight?>()
    private val nights = database.getAllNights()

    //For timer
    var timeLeft : Long = 60000*45
    @SuppressLint("SimpleDateFormat")
    var timeString = SimpleDateFormat("HH:mm:ss")

    private val timer = object : CountDownTimer(timeLeft, 1000){
        override fun onTick(millisUntilFinished: Long) {
            Log.i("SleepTrackerViewModel", "->" +
                    " Time left: ${timeString.format(Date(millisUntilFinished - (1000*60*60*3)))}") }

        override fun onFinish() {
            Log.i("SleepTrackerViewModel", "-> Finish")
        }

    }

    val nightsString = Transformations.map(nights){
        nights -> formatNights(nights, application.resources)
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO){
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }
    }

    fun onStartTracking() {
        Log.i("SleepTrackerViewModel", "-> onStartTracking")
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            timer.start()
            Log.i("SleepTrackerViewModel", "-> Timer started")
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun insert(newNight: SleepNight) {
        withContext(Dispatchers.IO){
            database.insert(newNight)
        }
    }

    fun onStopTracking() {
        Log.i("SleepTrackerViewModel", "-> onStopTracking")
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)

            timer.cancel()
            Log.i("SleepTrackerViewModel", "-> Timer canceled")

        }
    }

    private suspend fun update(oldNight: SleepNight) {
        withContext(Dispatchers.IO){
            database.update(oldNight)
        }
    }

    fun onClear() {
        Log.i("SleepTrackerViewModel", "-> onClear")
        uiScope.launch {
            clear()
            tonight.value = null
        }
    }
    private suspend fun clear() {
        withContext(Dispatchers.IO){
            database.clear()
        }
    }
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}
