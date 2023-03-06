package com.example.screenrecorder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import android.widget.RelativeLayout.LayoutParams
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.marginLeft
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.screenrecorder.MediaProjectionCompanion.Companion.mediaProjection
import com.example.screenrecorder.MediaProjectionCompanion.Companion.mediaProjectionManager
import com.example.screenrecorder.databinding.ActivityBrowserBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florescu.android.rangeseekbar.RangeSeekBar
import java.io.File


const val SCREEN_CAPTURE_PERMISSION_CODE = 1
const val OVERLAY_PERMISSION_CODE = 2

class MediaProjectionCompanion {
    companion object {
        var mediaProjectionManager: MediaProjectionManager? = null
        var mediaProjection: MediaProjection? = null
    }
}

class BrowserActivityViewModel : ViewModel() {
    var job: Job = Job()
    lateinit var activity: BrowserActivity
}
class BrowserActivity: AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var videosAdapter: VideosAdapter
    private var currentVideo: Video? = null
    private lateinit var displayMetrics: DisplayMetrics
    private var mediaPlayer: MediaPlayer? = null
    private var retriever = MediaMetadataRetriever()
    private val handler = Handler(Looper.getMainLooper())
    private var duration = -1
    private lateinit var viewModel: BrowserActivityViewModel
    private var baseDir = ""
    private lateinit var rangeSeekBar: RangeSeekBar<*>
    private var rangeSeekBarMap = hashMapOf("offset" to -1, "width" to -1)

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        ImageView(this).let { image ->
            val params = RelativeLayout.LayoutParams(60, 60)
            binding.toolbarLayout.gravity = Gravity.CENTER_VERTICAL
            params.rightMargin = 30
            image.layoutParams = params
            image.setImageResource(R.drawable.ic_baseline_close_white_100)
            binding.toolbarLayout.addView(image)
            image.setOnClickListener {
                val serviceIntent = Intent(this, MainActivity.Overlay::class.java)
                val displayMetrics: DisplayMetrics = DisplayMetrics()
                windowManager.getDefaultDisplay().getMetrics(displayMetrics)
                serviceIntent.putExtra("width", displayMetrics.widthPixels)
                serviceIntent.putExtra("height", displayMetrics.heightPixels)
                finish()
                startService(serviceIntent)
            }
        }

        baseDir = this.filesDir.toString()
        viewModel = ViewModelProvider(this).get(BrowserActivityViewModel::class.java)

        videosAdapter = VideosAdapter { video -> adapterOnClick(video) }
        val recyclerView: RecyclerView = binding.recyclerView
        recyclerView.adapter = videosAdapter
        val videos = mutableListOf<Video>()
        File(this.filesDir.toString()).walk().forEach { file ->
            if (file.extension == "mp4") {
                val video = Video(file)
                videos.add(video)
            }
        }
        videosAdapter.submitList(videos)

        rangeSeekBar = binding.rangeSeekBar
        rangeSeekBar.post {

            // set rangeSeekBar offset
            rangeSeekBarMap["offset"] = dpToPx(46f)    // 16 * 3 - 2, I think??
            val params = binding.current.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = rangeSeekBarMap["offset"]!!
            binding.current.layoutParams = params

            // Set rangeSeekBar width
            rangeSeekBarMap["width"] = rangeSeekBar.width - rangeSeekBarMap["offset"]!! * 2 + dpToPx(2f)
        }

        binding.play.setOnClickListener { play() }
        binding.pause.setOnClickListener { pause() }
        binding.stop.setOnClickListener { stop() }
        binding.back10.setOnClickListener { seek(-10) }
        binding.forward10.setOnClickListener { seek(10) }
//        binding.pinFrom.setOnClickListener {
//            if (duration == -1) {
//                return@setOnClickListener
//            }
//            if (binding.pinFrom.tag == "active") {
//                binding.pinFrom.tag = "inactive"
//                binding.pinFrom.setColorFilter(Color.parseColor("#ffffff"))
//                binding.timeFrom.setTextColor(Color.parseColor("#ffffff"))
//                binding.timeFrom.text = "00:00:00"
//            } else {
//                val time = getTime(mediaPlayer?.currentPosition ?: 0)
//                if (time >= binding.timeTo.text.toString()) {
//                    Toast.makeText(this, "Invalid", Toast.LENGTH_SHORT).show()
//                } else {
//                    binding.pinFrom.tag = "active"
//                    binding.pinFrom.setColorFilter(Color.parseColor("#9e1a1a"))
//                    binding.timeFrom.setTextColor(Color.parseColor("#9e1a1a"))
//                    binding.timeFrom.text = time
//                }
//            }
//        }
//        binding.pinTo.setOnClickListener {
//            if (duration == -1) {
//                return@setOnClickListener
//            }
//            if (binding.pinTo.tag == "active") {
//                binding.pinTo.tag = "inactive"
//                binding.pinTo.setColorFilter(Color.parseColor("#ffffff"))
//                binding.timeTo.setTextColor(Color.parseColor("#ffffff"))
//                binding.timeTo.text = getTime(duration)
//            } else {
//                val time = getTime(mediaPlayer?.currentPosition ?: 0)
//                if (time <= binding.timeFrom.text.toString()) {
//                    Toast.makeText(this, "Invalid", Toast.LENGTH_SHORT).show()
//                } else {
//                    binding.pinTo.tag = "active"
//                    binding.pinTo.setColorFilter(Color.parseColor("#9e1a1a"))
//                    binding.timeTo.setTextColor(Color.parseColor("#9e1a1a"))
//                    binding.timeTo.text = time
//                }
//            }
//        }
        binding.cut.setOnClickListener {
            stop()
            cut()
        }

        // Display metrics are needed for framesHolder
        displayMetrics = DisplayMetrics()
        windowManager.getDefaultDisplay().getMetrics(displayMetrics)
    }

    private fun adapterOnClick(video: Video) {
        currentVideo = video
        retriever.setDataSource(applicationContext, video.file.toUri())
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun play() {
        if (currentVideo == null) {
            Toast.makeText(this, "Select a video", Toast.LENGTH_SHORT).show()
        } else {
            if (mediaPlayer != null) {
                binding.videoView.start()
                startTimer()
            } else {
                binding.videoView.visibility = RelativeLayout.VISIBLE
                binding.recyclerView.visibility = RelativeLayout.GONE
                binding.videoView.setMediaController(MediaController(this))
                binding.videoView.setVideoURI(currentVideo!!.file.toUri())
                binding.videoView.requestFocus()
                binding.videoView.setOnPreparedListener {
                    mediaPlayer = it
                    binding.videoView.start()
                    binding.videoView.setMediaController(null)
                    startTimer()
                    duration = mediaPlayer!!.duration

                    //rangeSeekBar.setRangeValues(0, duration)  // Hmm??
                    rangeSeekBar.selectedMinValue = 0
                    rangeSeekBar.selectedMaxValue = duration
                    rangeSeekBar.isEnabled = true

                    rangeSeekBar.setOnRangeSeekBarChangeListener { bar, minValue, maxValue ->
                        onSeekChange(bar, minValue, maxValue)
                    }

                    binding.timeFrom.text = "00:00:00"
                    binding.timeTo.text = getTime(mediaPlayer!!.duration)
                    mediaPlayer?.setOnCompletionListener { pause() }
                }
            }
            binding.play.visibility = RelativeLayout.GONE
            binding.pause.visibility = RelativeLayout.VISIBLE
        }
    }

    private fun onSeekChange(bar: RangeSeekBar<*>, minValue: Number, maxValue: Number) {

    }

    private fun pause() {
        binding.videoView.pause()
        binding.pause.visibility = RelativeLayout.GONE
        binding.play.visibility = RelativeLayout.VISIBLE
        handler.removeCallbacksAndMessages(null)
    }

    private fun stop() {
        binding.pause.visibility = RelativeLayout.GONE
        binding.play.visibility = RelativeLayout.VISIBLE
        handler.removeCallbacksAndMessages(null)
        binding.status.text = "00:00:00"

        // I believe these are redundant operations
        binding.videoView.stopPlayback()
        //mediaPlayer?.stop()
        //mediaPlayer?.reset()
        //mediaPlayer?.release()

        mediaPlayer = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun seek(diff: Int) {
        if (mediaPlayer != null) {
            var to = mediaPlayer!!.currentPosition + diff * 1000
            if (to < 0) {
                to = 0
            }
            if (to > mediaPlayer!!.duration) {
                to = mediaPlayer!!.duration
            }
            mediaPlayer!!.seekTo(to.toLong(), MediaPlayer.SEEK_CLOSEST)
            binding.status.text = getTime(mediaPlayer!!.currentPosition)
        }
    }

    private fun cut() {
        viewModel.activity = this
        viewModel.job = viewModel.viewModelScope.launch(Dispatchers.IO) {

            // https://github.com/arthenica/ffmpeg-kit
            // https://ffmpeg.org/ffmpeg.html
            // https://ffmpeg.org/ffmpeg-utils.html#time-duration-syntax
            val cmd = arrayOf(
                "-ss", "00:00:05",  // position (time duration specification)
                "-y",   // overwrite output files without asking
                "-to", "00:00:13",   // mutually exclusive with -t
                "-i", baseDir + "/video.mp4",
                //"-t", "00:00:07",  // duration, mutually exclusive with -to
                "-s", "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}",
                "-r", "15",
                "-vcodec", "mpeg4",
                "-b:v", "2097152",
                "-b:a", "48000",
                "-ac", "2",
                "-ar", "22050",
                baseDir + "/video-trim.mp4"
            )

            val result = FFmpeg.execute(cmd)
            withContext(Dispatchers.Main) {
                when (result) {
                    RETURN_CODE_SUCCESS -> {
                        Log.v("TEST", "Command completed successfully")
                        Toast.makeText(viewModel.activity,
                            "Video created (video-trim.mp4)",
                            Toast.LENGTH_SHORT).show()
                    }
                    RETURN_CODE_CANCEL -> {
                        Log.v("TEST", "Command cancelled by user")
                    }
                    else -> {
                        Log.v("TEST",
                            String.format("Command execution failed with result=%d and the output below.",
                                result))
                    }
                }
            }
        }
    }

    private fun startTimer() {
        binding.status.text = getTime(mediaPlayer!!.currentPosition)
        handler.postDelayed(object : Runnable {
            override fun run() {
                binding.status.text = getTime(mediaPlayer!!.currentPosition)
                val params = binding.current.layoutParams as FrameLayout.LayoutParams
                params.leftMargin = getCurrentOffset()
                    binding.current.layoutParams = params
                handler.postDelayed(this, 100)
            }
        }, 100)
        binding.duration.text = getTime(mediaPlayer!!.duration)
    }

    private fun getCurrentOffset(): Int {
        val pct = mediaPlayer!!.currentPosition / mediaPlayer!!.duration.toFloat()
        val offset = rangeSeekBarMap["width"]!! * pct + rangeSeekBarMap["offset"]!!
        return offset.toInt()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    private fun getTime(ms: Int): String {
        val h = ms / (1000 * 60 * 60) % 24
        val m = ms / (1000 * 60) % 60
        val s = (ms / 1000) % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }
}

class MainActivity : Activity() {

    private val displayMetrics: DisplayMetrics = DisplayMetrics()
    private lateinit var serviceIntent: Intent

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager!!.createScreenCaptureIntent(), SCREEN_CAPTURE_PERMISSION_CODE)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_PERMISSION_CODE) {

            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "Screen Recording Permission Denied", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            mediaProjection = mediaProjectionManager!!.getMediaProjection(resultCode, data!!);

            // Set intent and put display metrics
            serviceIntent = Intent(this, Overlay::class.java)
            windowManager.getDefaultDisplay().getMetrics(displayMetrics)
            serviceIntent.putExtra("width", displayMetrics.widthPixels)
            serviceIntent.putExtra("height", displayMetrics.heightPixels)

            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
            } else {
                startService(serviceIntent)
                finish()
            }
        }

        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay Permission Denied", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            startService(serviceIntent)
            finish()
        }
    }

    class Overlay : Service() {

        // Properties related to WindowManager and the UI
        private lateinit var overlay: RelativeLayout
        private lateinit var windowManager: WindowManager
        private lateinit var record: ImageView
        private lateinit var recording: ImageView
        private lateinit var openFull: ImageView

        // Properties related to the screen recording
        private var isRecording = false
        private lateinit var virtualDisplay: VirtualDisplay
        private lateinit var mediaProjectionCallback: MediaProjectionCallback
        private var mediaRecorder = MediaRecorder()
        private var screenWidth = -1
        private var screenHeight = -1
        private var hideLayout = false

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
                        openFull = image
                        val params = RelativeLayout.LayoutParams(60, 60)
                        params.leftMargin = 30
                        params.topMargin = 20
                        image.layoutParams = params
                        image.setImageResource(R.drawable.ic_baseline_open_in_full_100)
                        //image.setBackgroundColor(Color.parseColor("#80FFFFFF"))
                        image.setOnClickListener {
                            launchBrowser()
                        }
                        left.addView(image)
                    }

                    //val imageCurrent
                    /*
                    ImageView(this).let { image ->
                        val params = RelativeLayout.LayoutParams(80, 80)
                        params.leftMargin = 50
                        params.topMargin = 10
                        image.background = ContextCompat.getDrawable(this, R.drawable.rounded_white)
                        image.layoutParams = params
                        left.addView(image)
                    }
                    */
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
                        LinearLayout(this).let { hide ->
                            hide.orientation = LinearLayout.HORIZONTAL
                            CheckBox(this).let { checkBox ->
                                checkBox.buttonTintList = ContextCompat.getColorStateList(this, R.color.white)
                                hide.addView(checkBox)
                                checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
                                    hideLayout = isChecked
                                }
                            }
                            TextView(this).let { textView ->
                                textView.text = "Hide"
                                textView.setTextColor(Color.parseColor("#eeeeee"))
                                hide.addView(textView)
                            }
                            val params = LinearLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.MATCH_PARENT)
                            params.leftMargin = 25
                            hide.layoutParams = params
                            hide.gravity = Gravity.CENTER_VERTICAL
                            r.addView(hide)
                        }
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

        private fun launchBrowser() {
            // Start the BrowserActivity
            val intent = Intent(this, BrowserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            exit()
        }

        class PanelLayout(context: Context, var stopRecording: ((Boolean) -> Unit)) : RelativeLayout(context) {
            var lastTouch: Long = 0
            override fun onTouchEvent(event: MotionEvent?): Boolean {
                if (lastTouch > 0 && System.currentTimeMillis() - lastTouch < 1000) {
                    stopRecording(true)
                }
                lastTouch = System.currentTimeMillis()
                return super.onTouchEvent(event)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun record(imageView: ImageView) {
            if (imageView.tag.toString() == "record") {
                record?.visibility = LinearLayout.GONE
                recording?.visibility = LinearLayout.VISIBLE
                if (hideLayout) {
                    windowManager.removeView(overlay)
                    val panel = PanelLayout(this, ::stopRecording)
                    //overlay.alpha = 0.50f
                    //overlay.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
                    //overlay.setBackgroundColor(Color.parseColor("#00000000"))
                    val params = WindowManager.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        -999,
                        PixelFormat.TRANSLUCENT
                    )
                    windowManager.addView(panel, params)
                }
                startRecording()
            }
            if (imageView.tag.toString() == "recording") {
                recording?.visibility = LinearLayout.GONE
                record?.visibility = LinearLayout.VISIBLE
                stopRecording(true)
            }
        }

        private fun startRecording() {
            openFull.visibility = RelativeLayout.INVISIBLE
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setOutputFile(this.filesDir.toString() + "/video.mp4")
            mediaRecorder.setVideoSize(screenWidth, screenHeight)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setVideoEncodingBitRate(512 * 1_000)
            mediaRecorder.setVideoFrameRate(16)
            mediaRecorder.setVideoEncodingBitRate(3_000_000)
            mediaRecorder.setOrientationHint(0)
            mediaRecorder.prepare()
            mediaProjectionCallback = MediaProjectionCallback()
            mediaProjection!!.registerCallback(mediaProjectionCallback, null)
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "MainActivity",
                screenWidth,
                screenHeight,
                getDpi(),
                //DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mediaRecorder.surface,
                null,
                null
            )
            mediaRecorder.setPreviewDisplay(mediaRecorder.surface)
            mediaRecorder.start()
            isRecording = true
        }

        private fun stopRecording(launchBrowser: Boolean) {
            if (isRecording) {
                virtualDisplay.release()
                mediaProjection!!.unregisterCallback(mediaProjectionCallback)
                mediaProjection!!.stop()
                mediaRecorder.stop()
                mediaRecorder.reset()
                isRecording = false
                if (launchBrowser) {
                    launchBrowser()
                }
            }
        }

        private fun getDpi() : Int {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            return metrics.densityDpi
        }

        private inner class MediaProjectionCallback : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording(false)
            }
        }

        private fun exit() {
            cleanUp()
            stopForeground(true)
            stopSelf()
        }

        private fun cleanUp() {
            stopRecording(false)

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
            if (intent != null) {
                screenWidth = intent.getIntExtra("width", -1)
                screenHeight = intent.getIntExtra("height", -1)
            }
            return START_STICKY
        }

        override fun onDestroy() {
            super.onDestroy()
            cleanUp()
        }
    }
}