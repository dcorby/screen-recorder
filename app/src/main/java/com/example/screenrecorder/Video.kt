package com.example.screenrecorder

import java.io.File

class Video(file: File) {
    val file = file
    var filename = file.name
    var label = file.name
}