package com.example.screenrecorder

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import android.widget.RelativeLayout.LayoutParams
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.screenrecorder.MediaProjectionCompanion.Companion.mediaProjectionManager
import java.io.IOException

// https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/media/projection/MediaProjectionDemo.java
// https://github.com/Mercandj/screen-recorder-android/blob/master/app/src/main/java/com/mercandalli/android/apps/screen_recorder/main/MainActivity.kt
// https://stackoverflow.com/questions/32381455/android-mediaprojectionmanager-in-service

class MediaProjectionCompanion {
    companion object {
        var mediaProjectionManager: MediaProjectionManager? = null
    }
}

class PermissionActivity : Activity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager!!.createScreenCaptureIntent(), 1)
    }
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(this, "Screen Recording Permission Denied", Toast.LENGTH_SHORT).show()
            return
        }
        finish()
    }
}

class MainActivity : Activity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1)
        } else {

            startService(Intent(this, Overlay::class.java))
        }
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            startService(Intent(this, Overlay::class.java))
        }
    }

    class Overlay : Service() {

        // Properties related to WindowManager and the UI
        private lateinit var overlay: RelativeLayout
        private lateinit var windowManager: WindowManager
        private lateinit var record: ImageView
        private lateinit var recording: ImageView

        // Properties related to the screen recording
        private var isRecording = false
        private lateinit var mediaProjection: MediaProjection
        private lateinit var virtualDisplay: VirtualDisplay
        private lateinit var mediaProjectionCallback: MediaProjectionCallback
        private lateinit var mediaRecorder: MediaRecorder

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate() {
            super.onCreate()

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            // https://stackoverflow.com/questions/6446221/get-context-in-a-service
            overlay = RelativeLayout(this)

            overlay.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
            //overlay.alpha = 0.50f
            overlay.setBackgroundColor(Color.parseColor("#80000000"))
            val params = WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                100,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            windowManager.addView(overlay, params)

            // Create a horizontal linear layout with three equally weighted children
            LinearLayout(this).let { layout ->
                val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                layout.orientation = LinearLayout.HORIZONTAL
                layout.layoutParams = params
                layout.setBackgroundColor(Color.parseColor("#00FFFFFF"))
                overlay?.addView(layout)

                // Create the left panel ("open full" and "go to current" icons)
                LinearLayout(this).let { left ->
                    val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                    left.layoutParams = params
                    //left.setBackgroundColor(ContextCompat.getColor(this, R.color.pastel_blue))
                    left.setBackgroundColor(Color.parseColor("#00FFFFFF"))
                    layout.addView(left)

                    // Add the "open full" icon
                    ImageView(this).let { image ->
                        val params = RelativeLayout.LayoutParams(60, 60)
                        params.leftMargin = 30
                        params.topMargin = 20
                        image.layoutParams = params
                        image.setImageResource(R.drawable.ic_baseline_open_in_full_100)
                        //image.setBackgroundColor(Color.parseColor("#80FFFFFF"))
                        left.addView(image)
                    }

                    //val imageCurrent
                    ImageView(this).let { image ->
                        val params = RelativeLayout.LayoutParams(80, 80)
                        params.leftMargin = 50
                        params.topMargin = 10
                        image.background = ContextCompat.getDrawable(this, R.drawable.rounded_white)
                        image.layoutParams = params
                        left.addView(image)
                    }
                }

                // Create the center panel (record button)
                LinearLayout(this).let { center ->
                    val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                    center.layoutParams = params
                    center.orientation = LinearLayout.HORIZONTAL
                    center.gravity = Gravity.CENTER
                    //center.setBackgroundColor(ContextCompat.getColor(this, R.color.pastel_green))
                    center.setBackgroundColor(Color.parseColor("#00FFFFFF"))
                    layout.addView(center)

                    // Add 3 views to center, l-frame-r
                    LinearLayout(this).let { l ->
                        val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                        l.layoutParams = params
                        l.gravity = Gravity.RIGHT
                        //l.setBackgroundColor(ContextCompat.getColor(this, R.color.pastel_blue))
                        TextView(this).let { textView ->
                            textView.text = "00:00:00"
                            textView.setTextColor(Color.parseColor("#eeeeee"))
                            textView.textSize = 18f
                            val params = LinearLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.MATCH_PARENT
                            )
                            params.rightMargin = 10
                            textView.gravity = Gravity.CENTER_VERTICAL
                            textView.layoutParams = params
                            l.addView(textView)
                        }
                        center.addView(l)
                    }

                    // Add a 100x100 frame, and add both record states to that frame
                    FrameLayout(this).let { frame ->
                        val params = LinearLayout.LayoutParams(100, 100, 0.0f)
                        params.gravity = Gravity.CENTER
                        frame.setBackgroundColor(Color.parseColor("#00FFFFFF"))
                        frame.layoutParams = params
                        center.addView(frame)

                        // Add the record image
                        ImageView(this).let { image ->
                            record = image
                            val params = LinearLayout.LayoutParams(80, 80)
                            params.setMargins(10, 10, 10, 10)
                            image.background = ContextCompat.getDrawable(this, R.drawable.record)
                            frame.layoutParams = params
                            image.setOnClickListener { record(it as ImageView) }
                            image.tag = "record"
                            frame.addView(image)
                        }

                        // Add the recordING image
                        ImageView(this).let { image ->
                            recording = image
                            val params = LinearLayout.LayoutParams(80, 80)
                            //params.leftMargin = 10
                            //params.topMargin = 10
                            params.setMargins(10, 10, 10, 10)
                            image.visibility = LinearLayout.GONE
                            image.background = ContextCompat.getDrawable(this, R.drawable.recording)
                            frame.layoutParams = params
                            image.setOnClickListener { record(it as ImageView) }
                            image.tag = "recording"
                            frame.addView(image)
                        }
                    }

                    RelativeLayout(this).let { r ->
                        val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                        r.layoutParams = params
                        center.addView(r)
                    }
                }

                // Create the right panel (X to close)
                LinearLayout(this).let { right ->
                    val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                    right.layoutParams = params
                    right.orientation = LinearLayout.HORIZONTAL
                    right.gravity = Gravity.RIGHT
                    ImageView(this).let { image ->
                        val params = RelativeLayout.LayoutParams(60, 60)
                        params.rightMargin = 30
                        params.topMargin = 20
                        image.layoutParams = params
                        image.setImageResource(R.drawable.ic_baseline_close_100)
                        image.setOnClickListener { exit() }
                        right.addView(image)
                    }
                    layout.addView(right)
                }
            }
        }

        private fun record(imageView: ImageView) {
            if (imageView.tag.toString() == "record") {
                record?.visibility = LinearLayout.GONE
                recording?.visibility = LinearLayout.VISIBLE
                startRecording()
            }
            if (imageView.tag.toString() == "recording") {
                recording?.visibility = LinearLayout.GONE
                record?.visibility = LinearLayout.VISIBLE
                stopRecording()
            }
        }

        private fun startRecording() {
            try {
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory().toString() + "/video.mp4")
                mediaRecorder.setVideoSize(500, 500)
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                mediaRecorder.setVideoEncodingBitRate(512 * 1_000)
                mediaRecorder.setVideoFrameRate(16)
                mediaRecorder.setVideoEncodingBitRate(3_000_000)
                mediaRecorder.setOrientationHint(0)
                mediaRecorder.prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            mediaProjection.registerCallback(mediaProjectionCallback, null)
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "MainActivity",
                500,
                500,
                2,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.surface,
                null,
                null
            )
            mediaRecorder.start()
            isRecording = true
        }

        private fun stopRecording() {
            virtualDisplay.release()
            mediaProjection.unregisterCallback(mediaProjectionCallback)
            mediaProjection.stop()
            mediaRecorder.stop()
            mediaRecorder.reset()
            isRecording = false
        }

        private inner class MediaProjectionCallback : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording()
            }
        }

        private fun exit() {
            cleanUp()
            stopForeground(true)
            stopSelf()
        }

        private fun cleanUp() {
            stopRecording()

            // Clean up the WindowManager
            if (overlay.parent == null) {
                // Already cleaned up
                return
            }
            try {
                windowManager.removeView(overlay)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            return START_STICKY
        }

        override fun onDestroy() {
            super.onDestroy()
            cleanUp()
        }
    }
}