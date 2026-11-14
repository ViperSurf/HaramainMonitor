package com.haramain.monitor.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.haramain.monitor.MainActivity
import com.haramain.monitor.R

/**
 * Quick Settings tile: start / stop monitoring without opening the app.
 * System-side only — zero cost while monitoring runs.
 */
class MonitorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val target = !MonitorState.isRunning.value
        // Optimistic flip: light the tile NOW instead of waiting for the
        // service round-trip (stream open takes a few hundred ms, which
        // read as lag). onStartListening reconciles with the truth.
        qsTile?.let { t ->
            t.state = if (target) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            t.subtitle = if (target) "Monitoring" else "Off"
            t.updateTile()
        }
        if (!target) {
            startService(MicMonitorService.stopIntent(this))
        } else {
            try {
                ContextCompat.startForegroundService(this, MicMonitorService.startIntent(this))
            } catch (e: Exception) {
                // Some Android versions refuse a foreground-service start
                // from a tile; fall back to opening the app.
                @Suppress("DEPRECATION")
                startActivityAndCollapse(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val running = MonitorState.isRunning.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Haramain Monitor"
        tile.subtitle = if (running) "Monitoring" else "Off"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_headphones)
        tile.updateTile()
    }

    companion object {
        /** Ask the system to re-read the tile (called on start/stop). */
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, MonitorTileService::class.java),
                )
            } catch (e: Exception) {
                // Tile not placed by the user yet — nothing to update.
            }
        }
    }
}
