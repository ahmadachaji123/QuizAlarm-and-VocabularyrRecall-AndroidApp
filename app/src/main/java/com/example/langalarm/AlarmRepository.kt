package com.example.langalarm

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AlarmRepository {
    private const val PREFS_NAME = "alarms_prefs_v2"
    private const val KEY_ALARMS_JSON = "alarms_json"
    private val gson = Gson()

    fun saveAlarms(context: Context, alarms: List<Alarm>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(alarms)
        prefs.edit().putString(KEY_ALARMS_JSON, json).apply()
    }

    fun loadAlarms(context: Context): MutableList<Alarm> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ALARMS_JSON, null) ?: return mutableListOf()
        val type = object : TypeToken<List<Alarm>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            mutableListOf()
        }
    }
    
    fun getAlarmById(context: Context, id: Long): Alarm? {
        return loadAlarms(context).find { it.id == id }
    }
    
    fun updateAlarm(context: Context, updatedAlarm: Alarm) {
        val alarms = loadAlarms(context)
        val index = alarms.indexOfFirst { it.id == updatedAlarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
            saveAlarms(context, alarms)
        }
    }
}
