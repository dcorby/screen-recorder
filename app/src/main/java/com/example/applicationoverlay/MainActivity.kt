package com.example.applicationoverlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            Log.v("TEST", "1")
            startActivityForResult(intent, 1)
        } else {
            Log.v("TEST", "2")
            startService(Intent(this, Overlay::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            startService(Intent(this, Overlay::class.java))
        }
    }








    class Overlay() : Service() {
        private var overlay: RelativeLayout? = null
        private var windowManager: WindowManager? = null

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate() {
            super.onCreate()

            Log.v("TEST", "onCreate() called")

            val params = WindowManager.LayoutParams(
                50,
                50,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.horizontalMargin = 50.0.toFloat()
            params.verticalMargin = 50.0.toFloat()

            // https://stackoverflow.com/questions/6446221/get-context-in-a-service
            overlay = RelativeLayout(this)
            overlay!!.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager!!.addView(overlay, params)
            val hand = Handler()
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            /* We want this service to continue running until it is explicitly
            * stopped, so return sticky.
            */
            Log.v("TEST", "onStartCommand()")
            return START_STICKY
        }

        override fun onDestroy() {
            super.onDestroy()

            if (overlay != null) {
                try {
                    windowManager!!.removeView(overlay)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

}

