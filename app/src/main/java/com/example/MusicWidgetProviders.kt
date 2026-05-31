package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

open class BaseMusicWidgetProvider(private val layoutId: Int) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, layoutId)
            
            // Fetch current stats from active ViewModel instance
            val model = MusicPlayerViewModel.instance
            val isPlaying = model?.isPlaying?.value ?: false
            val currentSong = model?.currentSong?.value
            val sysVolume = model?.systemVolume?.value ?: 70
            
            val isAirpodsActive = (model?.isAirPodsSimulatedEnabled?.value ?: false) || (model?.isBluetoothAudioActive?.value ?: false)
            val lBattery = model?.airpodsLeftBattery?.value ?: 88
            val rBattery = model?.airpodsRightBattery?.value ?: 92
            val cBattery = model?.airpodsCaseBattery?.value ?: 100
            
            // Core Text Binding
            views.setTextViewText(R.id.widget_song_title, currentSong?.title ?: "Mahnı seçilməyib")
            views.setTextViewText(R.id.widget_song_artist, currentSong?.artist ?: "Pleylist boşdur")
            
            // Icon Toggling for Play/Pause
            views.setImageViewResource(
                R.id.btn_play_pause,
                if (isPlaying) R.drawable.ic_pause_widget else R.drawable.ic_play_widget
            )
            
            // Volume representation
            try {
                views.setTextViewText(R.id.widget_volume_text, "Səs: $sysVolume%")
            } catch (ignored: Exception) {}
            
            // AirPods Monitor binding
            try {
                // Determine language, default Azerbaijani or similar for widgets if selected
                val isAz = model?.selectedLanguage?.value == "AZ"
                val solText = if (isAz) "Sol: $lBattery%" else "Left: $lBattery%"
                val sagText = if (isAz) "Sağ: $rBattery%" else "Right: $rBattery%"
                val qutuText = if (isAz) "Qutu: $cBattery%" else "Case: $cBattery%"
                
                views.setTextViewText(R.id.widget_battery_l, if (isAirpodsActive) solText else (if (isAz) "Sol: Sönülü" else "Left: Off"))
                views.setTextViewText(R.id.widget_battery_r, if (isAirpodsActive) sagText else (if (isAz) "Sağ: Sönülü" else "Right: Off"))
                views.setTextViewText(R.id.widget_battery_case, if (isAirpodsActive) qutuText else (if (isAz) "Qutu: Sönülü" else "Case: Off"))
            } catch (ignored: Exception) {}
            
            // Setup click PendingIntents
            setOnClickBroadcast(context, views, R.id.btn_play_pause, "com.example.ACTION_WIDGET_PLAY_PAUSE")
            setOnClickBroadcast(context, views, R.id.btn_next, "com.example.ACTION_WIDGET_NEXT")
            setOnClickBroadcast(context, views, R.id.btn_prev, "com.example.ACTION_WIDGET_PREV")
            setOnClickBroadcast(context, views, R.id.btn_vol_up, "com.example.ACTION_WIDGET_VOL_UP")
            setOnClickBroadcast(context, views, R.id.btn_vol_down, "com.example.ACTION_WIDGET_VOL_DOWN")
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    private fun setOnClickBroadcast(context: Context, views: RemoteViews, viewId: Int, actionStr: String) {
        val intent = Intent(context, this::class.java).apply {
            action = actionStr
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, viewId, intent, flags)
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        
        val viewModel = MusicPlayerViewModel.instance
        if (viewModel != null) {
            when (action) {
                "com.example.ACTION_WIDGET_PLAY_PAUSE" -> {
                    viewModel.togglePlayback(context)
                }
                "com.example.ACTION_WIDGET_NEXT" -> {
                    viewModel.playNext(context)
                }
                "com.example.ACTION_WIDGET_PREV" -> {
                    viewModel.playPrevious(context)
                }
                "com.example.ACTION_WIDGET_VOL_UP" -> {
                    val currentVol = viewModel.systemVolume.value
                    viewModel.setSystemVolume(context, (currentVol + 10).coerceAtMost(100))
                }
                "com.example.ACTION_WIDGET_VOL_DOWN" -> {
                    val currentVol = viewModel.systemVolume.value
                    viewModel.setSystemVolume(context, (currentVol - 10).coerceAtLeast(0))
                }
            }
            // Broaden update to keep all widgets synced instantly
            updateAllWidgetsCombined(context)
        } else {
            // If the app is fully dormant, wake it and carry over the intent action to process
            val matchActions = listOf(
                "com.example.ACTION_WIDGET_PLAY_PAUSE",
                "com.example.ACTION_WIDGET_NEXT",
                "com.example.ACTION_WIDGET_PREV",
                "com.example.ACTION_WIDGET_VOL_UP",
                "com.example.ACTION_WIDGET_VOL_DOWN"
            )
            if (action in matchActions) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("widget_action", action)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}

// Five distinct widget providers maps to each aesthetic option
class MusicWidgetProvider1 : BaseMusicWidgetProvider(R.layout.widget_layout_aura)
class MusicWidgetProvider2 : BaseMusicWidgetProvider(R.layout.widget_layout_retro)
class MusicWidgetProvider3 : BaseMusicWidgetProvider(R.layout.widget_layout_full)
class MusicWidgetProvider4 : BaseMusicWidgetProvider(R.layout.widget_layout_airpods)
class MusicWidgetProvider5 : BaseMusicWidgetProvider(R.layout.widget_layout_disc)

fun updateAllWidgetsCombined(context: Context) {
    val widgetClasses = listOf(
        MusicWidgetProvider1::class.java,
        MusicWidgetProvider2::class.java,
        MusicWidgetProvider3::class.java,
        MusicWidgetProvider4::class.java,
        MusicWidgetProvider5::class.java
    )
    for (clazz in widgetClasses) {
        val intent = Intent(context, clazz).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, clazz))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}
