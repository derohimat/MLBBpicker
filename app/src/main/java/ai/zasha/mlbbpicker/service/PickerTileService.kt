package ai.zasha.mlbbpicker.service

import android.service.quicksettings.TileService

/** Quick Settings tile: tap to show/hide the floating MLBB Picker overlay. */
class PickerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val visible = FloatingOverlayService.isRunning && FloatingOverlayService.isOverlayVisible
        OverlayTiles.updateTile(qsTile, visible, if (visible) "Shown" else "Hidden")
    }

    override fun onClick() {
        super.onClick()
        OverlayTiles.sendOverlayAction(this, FloatingOverlayService.ACTION_TOGGLE_OVERLAY)
    }
}
