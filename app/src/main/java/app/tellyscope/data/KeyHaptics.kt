// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.io.File
import java.util.Properties

enum class RemoteMode { DPAD, TOUCHPAD, MOUSE }

/**
 * The Remote's own settings, kept in [file]: whether a key press vibrates (on until the user turns
 * it off), whether the Screen off tip has been shown, and whether touchpad or mouse takes the D-pad's place.
 */
class KeyHaptics(private val file: File) {
    @get:Synchronized @set:Synchronized
    var on: Boolean
        get() = props().getProperty("off") != "true"
        set(on) = store { if (on) remove("off") else setProperty("off", "true") }

    @get:Synchronized @set:Synchronized
    var screenOffTipSeen: Boolean
        get() = props().getProperty("screenOffTip") == "seen"
        set(seen) = store { if (seen) setProperty("screenOffTip", "seen") else remove("screenOffTip") }

    @get:Synchronized @set:Synchronized
    var remoteMode: RemoteMode
        get() = when (props().getProperty("pad")) {
            "mouse" -> RemoteMode.MOUSE
            "touchpad" -> RemoteMode.TOUCHPAD
            else -> RemoteMode.DPAD
        }
        set(mode) = store {
            when (mode) {
                RemoteMode.MOUSE -> setProperty("pad", "mouse")
                RemoteMode.TOUCHPAD -> setProperty("pad", "touchpad")
                RemoteMode.DPAD -> remove("pad")
            }
        }

    @get:Synchronized @set:Synchronized
    var touchpad: Boolean
        get() = remoteMode == RemoteMode.TOUCHPAD
        set(on) { remoteMode = if (on) RemoteMode.TOUCHPAD else RemoteMode.DPAD }

    @get:Synchronized @set:Synchronized
    var defaultUrlAppPkg: String?
        get() = props().getProperty("urlAppPkg")
        set(pkg) = store { if (pkg != null) setProperty("urlAppPkg", pkg) else remove("urlAppPkg") }

    @get:Synchronized @set:Synchronized
    var mouseEnabled: Boolean
        get() = props().getProperty("mouseOff") != "true"
        set(on) = store { if (on) remove("mouseOff") else setProperty("mouseOff", "true") }

    private fun store(change: Properties.() -> Unit) {
        val p = props().apply(change)
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { p.store(it, null) }
        }
    }

    private fun props() = Properties().apply {
        runCatching { file.takeIf { it.exists() }?.inputStream()?.use(::load) }
    }
}
