package com.example.screenrecorder

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.Image
import android.net.Uri
import android.opengl.Visibility
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import android.widget.RelativeLayout.LayoutParams
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.marginRight

class MainActivity : AppCompatActivity() {
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
        /* TODO show a toast notification to user knows it's running */
        finish()
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

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // https://stackoverflow.com/questions/6446221/get-context-in-a-service
            RelativeLayout(this).let { overlay ->
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
                windowManager?.addView(overlay, params)

                // Create a horizontal linear layout with three equally weighted children
                LinearLayout(this).let { layout ->
                    val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    layout.orientation = LinearLayout.HORIZONTAL
                    layout.layoutParams = params
                    layout.setBackgroundColor(Color.parseColor("#00FFFFFF"))
                    overlay.addView(layout)

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
                                val params = LinearLayout.LayoutParams(80, 80)
                                //params.leftMargin = 10
                                //params.topMargin = 10
                                params.setMargins(10, 10, 10, 10)
                                image.background = ContextCompat.getDrawable(this, R.drawable.record)
                                frame.layoutParams = params
                                frame.addView(image)
                            }

                            // Add the recordING image
                            ImageView(this).let { image ->
                                val params = LinearLayout.LayoutParams(80, 80)
                                //params.leftMargin = 10
                                //params.topMargin = 10
                                params.setMargins(10, 10, 10, 10)
                                image.visibility = LinearLayout.GONE
                                image.background = ContextCompat.getDrawable(this, R.drawable.recording)
                                frame.layoutParams = params
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
                    RelativeLayout(this).let { right ->
                        val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                        right.layoutParams = params
                        // color for test
                        right.setBackgroundColor(ContextCompat.getColor(this, R.color.pastel_red))
                        layout.addView(right)
                    }
                }
            }
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            /* We want this service to continue running until it is explicitly
            * stopped, so return sticky.
            */
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

