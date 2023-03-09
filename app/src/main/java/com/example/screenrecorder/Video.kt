package com.example.screenrecorder

import java.io.File

class Video(file: File) {
    val file = file
    val filename: String = file.name
    val label = file.name.replace(".mp4", "")
    var editMode = false
}