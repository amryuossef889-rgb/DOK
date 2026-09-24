package com.dok.editor.workspace

enum class WorkspaceMode { EDIT, CUT, COLOR, MOTION, AUDIO, DELIVER }

enum class DeviceLayout { PHONE_PORTRAIT, PHONE_LANDSCAPE, TABLET, EXTERNAL_DISPLAY }

data class WorkspaceState(
    val mode: WorkspaceMode = WorkspaceMode.EDIT,
    val layout: DeviceLayout = DeviceLayout.PHONE_PORTRAIT,
    val keyboardConnected: Boolean = false,
    val mouseConnected: Boolean = false,
    val stylusAvailable: Boolean = false
)
