# Tellyscope TV Remote Mouse Control & Companion Helper System

## Overview
The **Tellyscope TV Remote Mouse Control System** provides real-time virtual mouse cursor tracking, automatic pointer overlays, and tree-structure file management on Android TV and Fire TV devices over ADB.

---

## 1. Remote Control Modes

The Remote interface features three dedicated control modes accessible via top Segmented Tabs or the header menu (`⋮`):

| Control Mode | Interaction | Mechanics |
| :--- | :--- | :--- |
| **D-Pad** | Directional keys + OK | `input keyevent 19/20/21/22/23` |
| **Touchpad** | Swipe gestures + Tap | Gesture vector mapping to directional keys |
| **Mouse Cursor** | Trackpad viewport + Left/Right Click | Real-time `input motionevent MOVE` & `input tap x y` |

---

## 2. Generating & Bundling the TV Companion Helper APK

### Step A: Build the Companion Module
Run the Gradle assemble task to generate the TV Mouse Helper APK:

```bash
./gradlew :companion:assembleDebug
```

### Step B: Copy to Phone App Assets
Copy the compiled APK output into the phone app's `assets/` directory:

```bash
cp companion/build/outputs/apk/debug/companion-debug.apk app/src/main/assets/tellyscope-mouse-helper.apk
```

---

## 3. Installation Flow & Automatic File Cleanup

1. **Detection**:
   When entering **Mouse Mode** on the Remote screen, Tellyscope checks if `app.tellyscope.helper` is installed on the TV:
   ```bash
   adb shell pm list packages app.tellyscope.helper
   ```
2. **Installation from Asset**:
   If not installed, the **"Install TV Helper"** button is displayed. Tapping it:
   * Sideloads `tellyscope-mouse-helper.apk` from `assets/` to `/data/local/tmp/tellyscope-mouse-helper.apk` on the TV.
   * Runs `pm install -r -g /data/local/tmp/tellyscope-mouse-helper.apk`.
   * Automatically grants Accessibility Service permissions:
     ```bash
     settings put secure enabled_accessibility_services app.tellyscope.helper/.MouseOverlayService
     settings put secure accessibility_enabled 1
     ```
3. **Post-Installation Temporary File Cleanup**:
   After installation completes, Tellyscope automatically deletes the temporary APK from the TV:
   ```bash
   adb shell rm -f /data/local/tmp/tellyscope-mouse-helper.apk
   ```
4. **Status Text on Remote Screen**:
   * **Not Installed**: Displays **"Install TV Helper"** button.
   * **Installed & Active**: Displays **"TV Helper Active"** status badge in top bar.

---

## 4. Auto Drag Show/Hide Cursor Mechanics

* **Drag-to-Show**: Dragging your finger across the Mousepad surface dispatches real-time pointer motion events (`MOVE`) and auto-enables the TV pointer overlay (`pointer_location 1` & `show_touches 1`).
* **High-Contrast 3D Mouse Arrow**: The companion Accessibility Service (`MouseOverlayService`) draws a sharp 3D white mouse arrow cursor with a black outline over all TV apps and windows.
* **Point & Click**:
  * **Trackpad Tap / Left Click**: Dispatches `input tap x y` / `performClickAt`.
  * **Right Click**: Dispatches `RemoteKey.MENU` (TV context menu / options).
