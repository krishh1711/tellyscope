package app.tellyscope.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ExternalTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = MouseOverlayService.instance ?: return
        when (intent.action) {
            "app.tellyscope.helper.MOVE" -> {
                val x = intent.getFloatExtra("x", 0.5f)
                val y = intent.getFloatExtra("y", 0.5f)
                service.setCursorPosition(x, y)
            }
            "app.tellyscope.helper.CLICK" -> {
                val x = intent.getFloatExtra("x", 0.5f)
                val y = intent.getFloatExtra("y", 0.5f)
                service.performClickAt(x, y)
            }
            "app.tellyscope.helper.TOGGLE", "ru.vasys.keycursor.action.MOUSE_TOGGLE" -> {
                service.toggleVisibility()
            }
        }
    }
}
