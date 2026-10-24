package com.kurocanvas

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kuro Canvas — Creative Workspace"
    ) {
        App()
    }
}
