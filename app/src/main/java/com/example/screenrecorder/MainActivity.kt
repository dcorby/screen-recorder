package com.example.screenrecorder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.*
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
import android.text.InputType
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    var isInit: Boolean = true
    var wasRecording: Boolean = false
    var sortBy: String? = null
    var videos = mutableListOf<Video>()
    var videoNew: Video? = null
    var videoCurrent: Video? = null

    var currentPosition: Int = -1
    var boundLeftPercent: Float = 0f
    var boundRightPercent: Float = 0f
    var status: String = ""
}
class BrowserActivity: AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var videosAdapter: VideosAdapter
    private lateinit var displayMetrics: DisplayMetrics
    private var mediaPlayer: MediaPlayer? = null
    private var retriever = MediaMetadataRetriever()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var viewModel: BrowserActivityViewModel
    private var baseDir = ""

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout actions
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Screen Recorder"

        // Layout the toolbar
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

        // Get some instance vars, including the view model
        baseDir = this.filesDir.toString()
        viewModel = ViewModelProvider(this).get(BrowserActivityViewModel::class.java)
        viewModel.wasRecording = intent.getBooleanExtra("wasRecording", false)
        viewModel.sortBy = if (viewModel.wasRecording) {
            "date"
        } else {
            viewModel.sortBy ?: "name"
        }

        // Set the adapter
        videosAdapter = VideosAdapter(
                            viewModel.videos,
                            { video -> adapterOnClick(video) },
                            { video -> adapterOnRename(video) },
                            { video, callback -> adapterOnDelete(video, callback) })
        val recyclerView: RecyclerView = binding.recyclerView
        recyclerView.adapter = videosAdapter
        toggleSort(viewModel.sortBy!!)
        if (viewModel.isInit) {
            updateVideos(true)
            videosAdapter.submitList(viewModel.videos)
            viewModel.isInit = false
        }
        binding.sortByName.setOnClickListener {
            toggleSort("name")
            updateVideos(false)
        }
        binding.sortByDate.setOnClickListener {
            toggleSort("date")
            updateVideos(false)
        }

        // Set controls listeners
        binding.play.setOnClickListener { play(false, null) }
        binding.pause.setOnClickListener { pause() }
        binding.stop.setOnClickListener { stop(true) }
        binding.back10.setOnClickListener { seekDiff(-10) }
        binding.forward10.setOnClickListener { seekDiff(10) }
        binding.bar.post {
            val width = binding.bar.width
            val params = binding.boundRight.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = width
            binding.boundRight.layoutParams = params
        }
        binding.nameClose.setOnClickListener {
            binding.nameLayout.visibility = RelativeLayout.GONE
        }

        // Handle cut
        binding.cut.setOnClickListener {
            val cutFrom = binding.timeFrom.currentTextColor == ContextCompat.getColor(this, R.color.red)
            val cutTo = binding.timeTo.currentTextColor == ContextCompat.getColor(this, R.color.red)
            if (cutFrom || cutTo) {
                pause()
                binding.nameLayout.visibility = RelativeLayout.VISIBLE
                binding.nameButton.setOnClickListener {
                    val name = binding.name.text.toString().replace(".mp4", "").trim()
                    if (name == "") {
                        Toast.makeText(this, "Enter a name for the cut file", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    cut(name) { success ->
                        if (success) {
                            stop(true)
                            val file = File(this.filesDir.toString() + "/${name}.mp4")
                            val video = Video(file)
                            viewModel.videos.add(video)
                            binding.nameLayout.visibility = RelativeLayout.GONE
                            binding.nameButton.setOnClickListener(null)
                            updateVideos(false)
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Select bounds", Toast.LENGTH_SHORT).show()
            }
        }

        // Display metrics are needed for framesHolder
        displayMetrics = DisplayMetrics()
        windowManager.getDefaultDisplay().getMetrics(displayMetrics)

        // Re-init current position and bounds offsets if necessary
        if (viewModel.status == "playing" || viewModel.status == "paused") {
            val pause = viewModel.status == "paused"
            play(true) {
                (binding.boundLeft.layoutParams as FrameLayout.LayoutParams).leftMargin =
                    (viewModel.boundLeftPercent * binding.bar.width).toInt()
                (binding.boundRight.layoutParams as FrameLayout.LayoutParams).leftMargin =
                    (viewModel.boundRightPercent * binding.bar.width).toInt()
                // binding.videoView.seekTo(viewModel.currentPosition)
                // ^ Doesn't seek reliably, sets to zero. Why?
                mediaPlayer!!.seekTo(viewModel.currentPosition.toLong(), MediaPlayer.SEEK_CLOSEST)
                if (pause) {
                    pause()
                }
            }
        }
    }

    private fun toggleSort(by: String) {
        if (by == "name") {
            binding.sortByDate.setTextColor(ContextCompat.getColor(this, R.color.graybeige3))
            binding.sortByDate.background = ContextCompat.getDrawable(this, R.drawable.rounded_button_borders)
            binding.sortByName.setTextColor(ContextCompat.getColor(this, R.color.red))
            binding.sortByName.background = ContextCompat.getDrawable(this, R.drawable.rounded_button_borders_active)
            viewModel.sortBy = "name"
        }
        if (by == "date") {
            binding.sortByDate.setTextColor(ContextCompat.getColor(this, R.color.red))
            binding.sortByDate.background = ContextCompat.getDrawable(this, R.drawable.rounded_button_borders_active)
            binding.sortByName.setTextColor(ContextCompat.getColor(this, R.color.graybeige3))
            binding.sortByName.background = ContextCompat.getDrawable(this, R.drawable.rounded_button_borders)
            viewModel.sortBy = "date"
        }
    }

    private fun adapterOnClick(video: Video) {
        viewModel.videoCurrent = video
        retriever.setDataSource(applicationContext, video.file.toUri())
    }

    private fun adapterOnRename(video: Video?) {
        if (video == null) {
            Toast.makeText(this, "Enter a name for the file", Toast.LENGTH_SHORT).show()
        } else {
            updateVideos(false)
        }
    }

    private fun adapterOnDelete(video: Video, callback: (() -> Unit)) {
        viewModel.videos.remove(video)
        Toast.makeText(this, "Video ${video.filename} deleted", Toast.LENGTH_SHORT).show()
        callback()
    }

    private fun updateVideos(isInit: Boolean) {
        if (isInit) {
            File(this.filesDir.toString()).walk().forEach { file ->
                if (file.extension == "mp4") {
                    val video = Video(file)
                    viewModel.videos.add(video)
                }
            }
        } else {
            viewModel.videoNew?.isNew = false
            viewModel.videoNew = null
        }
        if (viewModel.sortBy == "name") {
            viewModel.videos.sortBy { it.filename }
        } else {
            viewModel.videos.sortByDescending { it.file.lastModified() }
            if (viewModel.wasRecording) {
                viewModel.videos[0].isNew = true
                viewModel.videoNew = viewModel.videos[0]
                viewModel.wasRecording = false
            }
        }
        videosAdapter.notifyItemRangeRemoved(0, viewModel.videos.size + 1)
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun play(start: Boolean, callback: (() -> Unit)?) {
        viewModel.status = "playing"
        if (viewModel.videoCurrent == null) {
            Toast.makeText(this, "Select a video", Toast.LENGTH_SHORT).show()
        } else {
            if (mediaPlayer != null) {
                binding.play.visibility = View.GONE
                binding.pause.visibility = View.VISIBLE
                binding.videoView.start()
                startTimerPlayer()
                if (callback != null) {
                    callback()
                }
            } else {
                toggleUiForVideo("show")
                binding.videoView.setMediaController(null)
                binding.videoView.setVideoURI(viewModel.videoCurrent!!.file.toUri())
                binding.videoView.requestFocus()
                binding.videoView.setOnPreparedListener {
                    mediaPlayer = it
                    binding.timeFrom.text = "00:00:00"
                    binding.timeTo.text = Utils.getTime(binding.videoView.duration)
                    binding.videoView.setOnCompletionListener {
                        binding.videoView.seekTo(binding.videoView.duration)
                        pause()
                        binding.videoView.seekTo(0)
                    }
                    enableSeekBar()
                    updateCurrent()
                    if (start) {
                        play(false, callback)
                    }
                }
            }
        }
    }

    private fun toggleUiForVideo(status: String) {
        if (status == "show") {
            supportActionBar?.hide()
            binding.videoFrame.visibility = View.VISIBLE
            binding.videoView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        }
        if (status == "hide") {
            supportActionBar?.show()
            binding.videoFrame.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pause() {
        binding.videoView.pause()
        binding.pause.visibility = RelativeLayout.GONE
        binding.play.visibility = RelativeLayout.VISIBLE
        handler.removeCallbacksAndMessages(null)
        updateCurrent()
        viewModel.status = "paused"
    }

    private fun stop(setStatus: Boolean) {
        binding.pause.visibility = RelativeLayout.GONE
        binding.play.visibility = RelativeLayout.VISIBLE
        handler.removeCallbacksAndMessages(null)
        binding.status.text = "00:00:00"
        binding.duration.text = "00:00:00"
        binding.videoView.stopPlayback()
        mediaPlayer = null

        val boundLeftParams = binding.boundLeft.layoutParams as FrameLayout.LayoutParams
        boundLeftParams.leftMargin = 0
        binding.boundLeft.layoutParams = boundLeftParams

        val boundRightParams = binding.boundRight.layoutParams as FrameLayout.LayoutParams
        boundRightParams.leftMargin = binding.bar.width
        binding.boundRight.layoutParams = boundRightParams

        val currentParams = binding.current.layoutParams as FrameLayout.LayoutParams
        currentParams.leftMargin = dpToPx(15f).toInt()
        binding.current.layoutParams = currentParams

        binding.timeFrom.text = "00:00:00"
        binding.timeFrom.setTextColor(Color.parseColor("#ffffff"))
        binding.timeTo.text = "00:00:00"
        binding.timeTo.setTextColor(Color.parseColor("#ffffff"))

        disableSeekBar()
        toggleUiForVideo("hide")

        if (setStatus) {
            viewModel.status = "stopped"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun seekDiff(diff: Int) {
        if (mediaPlayer != null) {
            var to = binding.videoView.currentPosition + diff * 1000
            if (to < 0) {
                to = 0
            }
            if (to > binding.videoView.duration) {
                to = binding.videoView.duration
                pause()
            }
            mediaPlayer!!.seekTo(to.toLong(), MediaPlayer.SEEK_CLOSEST)
            updateCurrent()
            binding.status.text = Utils.getTime(binding.videoView.currentPosition)
        }
    }

    private fun cut(name: String, callback: ((Boolean) -> Unit)) {
        fun showToast(msg: String) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.job = viewModel.viewModelScope.launch(Dispatchers.IO) {

            // https://github.com/arthenica/ffmpeg-kit
            // https://ffmpeg.org/ffmpeg.html
            // https://ffmpeg.org/ffmpeg-utils.html#time-duration-syntax
            // This command produced the best quality video. libx264 required a special lib version (see gradle)
            val cmd = arrayOf(
                "-ss", "00:00:05",  // position (time duration specification)
                "-y",   // overwrite output files without asking
                "-i", baseDir + "/${viewModel.videoCurrent!!.file.nameWithoutExtension}.mp4",
                "-codec:v", "libx264",
                "-t", "00:00:08",  // duration, mutually exclusive with -to
                "-map", "0",
                "-s", "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}",
                "-r", "23",
                baseDir + "/${name}.mp4"
            )

            val result = FFmpeg.execute(cmd)
            withContext(Dispatchers.Main) {
                when (result) {
                    RETURN_CODE_SUCCESS -> {
                        Log.v("TEST", "Command completed successfully")
                        showToast("Video created (${name}.mp4)")
                        callback(true)
                    }
                    RETURN_CODE_CANCEL -> {
                        Log.v("TEST", "Command cancelled")
                        showToast("Error creating video")
                        callback(false)
                    }
                    else -> {
                        Log.v("TEST", String.format("Command failed with result=%d", result))
                        showToast("Error creating video")
                        callback(false)
                    }
                }
            }
        }
    }

    private fun startTimerPlayer() {
        binding.status.text = Utils.getTime(binding.videoView.currentPosition)
        handler.postDelayed(object : Runnable {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun run() {
                handler.postDelayed(this, 100)
                updateCurrent()
            }
        }, 100)
        binding.duration.text = Utils.getTime(binding.videoView.duration)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateCurrent() {
        checkBounds()
        val percent = binding.videoView.currentPosition / binding.videoView.duration.toFloat()
        val offset = percent * binding.bar.width
        val params = binding.current.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = offset.toInt() + dpToPx(15f).toInt()
        binding.current.layoutParams = params
        binding.status.text = Utils.getTime(binding.videoView.currentPosition)
        updateBounds()
    }

    private fun updateBounds() {
        // Update bound times
        if ((binding.boundLeft.layoutParams as FrameLayout.LayoutParams).leftMargin > 0) {
            binding.timeFrom.text = Utils.getTime(getBoundPosition(binding.boundLeft))
            binding.timeFrom.setTextColor(Color.parseColor("#9e1a1a"))
        } else {
            binding.timeFrom.text = "00:00:00"
            binding.timeFrom.setTextColor(Color.parseColor("#ffffff"))
        }
        if ((binding.boundRight.layoutParams as FrameLayout.LayoutParams).leftMargin < binding.bar.width) {
            binding.timeTo.text = Utils.getTime(getBoundPosition(binding.boundRight))
            binding.timeTo.setTextColor(Color.parseColor("#9e1a1a"))
        } else {
            binding.timeTo.text = Utils.getTime(binding.videoView.duration)
            binding.timeTo.setTextColor(Color.parseColor("#ffffff"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkBounds() {
        val leftPosition = getBoundPosition(binding.boundLeft)
        if (binding.videoView.currentPosition < leftPosition) {
            mediaPlayer!!.seekTo(leftPosition.toLong(), MediaPlayer.SEEK_CLOSEST)
        }
        val rightPosition = getBoundPosition(binding.boundRight)
        if (binding.videoView.currentPosition > rightPosition) {
            mediaPlayer!!.seekTo(rightPosition.toLong(), MediaPlayer.SEEK_CLOSEST)
            pause()
        }
    }

    private fun getBoundPosition(v: View) : Int {
        val params = v.layoutParams as FrameLayout.LayoutParams
        val pct = params.leftMargin / binding.bar.width.toFloat()
        val ms = (pct * binding.videoView.duration).toInt()
        return ms
    }

    private fun dpToPx(dp: Float) : Float {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
        return px
    }

    private fun enableSeekBar() {
        binding.boundLeft.setImageResource(R.drawable.ic_baseline_circle_30)
        binding.boundRight.setImageResource(R.drawable.ic_baseline_circle_30)
        binding.bar.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        binding.current.visibility = RelativeLayout.VISIBLE
        binding.boundLeft.setOnTouchListener(onBoundTouchListener)
        binding.boundRight.setOnTouchListener(onBoundTouchListener)
        binding.barHolder.setOnTouchListener(onBarTouchListener)
    }

    private fun disableSeekBar() {
        binding.boundLeft.setImageResource(R.drawable.ic_baseline_circle_30_gray)
        binding.boundRight.setImageResource(R.drawable.ic_baseline_circle_30_gray)
        binding.bar.setBackgroundColor(ContextCompat.getColor(this, R.color.gray3))
        binding.current.visibility = RelativeLayout.GONE
        binding.boundLeft.setOnTouchListener(null)
        binding.boundRight.setOnTouchListener(null)
        binding.barHolder.setOnTouchListener(null)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun seek() {
        val params = binding.current.layoutParams as FrameLayout.LayoutParams
        val pct = (params.leftMargin - dpToPx(15f)) / binding.bar.width.toFloat()
        val ms = pct * binding.videoView.duration
        mediaPlayer!!.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST)
        updateCurrent()
    }

    private val onBarTouchListener = object : View.OnTouchListener {
        var prevX = 0
        var params: FrameLayout.LayoutParams? = null
        var leftMargin = -1

        @RequiresApi(Build.VERSION_CODES.O)
        private fun moveCurrent(diffX: Int, event: MotionEvent) {
            val currentLocation = IntArray(2)
            val leftLocation = IntArray(2)
            val rightLocation = IntArray(2)
            binding.current.getLocationOnScreen(currentLocation)
            binding.current.getLocationOnScreen(leftLocation)
            binding.current.getLocationOnScreen(rightLocation)
            if (currentLocation[0] < leftLocation[0] || currentLocation[0] > rightLocation[0]) {
                return
            }

            leftMargin += diffX
            params!!.leftMargin = leftMargin
            binding.current.layoutParams = params
            prevX = event.rawX.toInt()
            seek()
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (v == null || event == null) {
                return false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    params = binding.current.layoutParams as FrameLayout.LayoutParams
                    leftMargin = params!!.leftMargin
                    val location = IntArray(2)
                    binding.current.getLocationOnScreen(location)
                    val diffX = event.rawX.toInt() - location[0]
                    moveCurrent(diffX, event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX.toInt() - prevX
                    moveCurrent(diffX, event)
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.rawX.toInt() - prevX
                    moveCurrent(diffX, event)
                }
            }
            return true
        }
    }

    private val onBoundTouchListener = object : View.OnTouchListener {
        var prevX = 0
        var tag = ""
        var params: FrameLayout.LayoutParams? = null
        var other: ImageView? = null
        var otherParams: FrameLayout.LayoutParams? = null
        var currentParams: FrameLayout.LayoutParams? = null
        var seek = false

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (v == null || event == null) {
                return false
            }
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX.toInt() - prevX
                    if (params != null && otherParams != null) {
                        val leftMargin = params!!.leftMargin + diffX
                        if (tag == "left" && leftMargin < otherParams!!.leftMargin) {
                            if (leftMargin < 0) {
                                return true
                            }
                            params!!.leftMargin = leftMargin
                            v.layoutParams = params
                            if (leftMargin + dpToPx(15f) > currentParams!!.leftMargin) {
                                currentParams!!.leftMargin = leftMargin + dpToPx(15f).toInt()
                                binding.current.layoutParams = currentParams
                                seek = true
                            }
                            prevX = event.rawX.toInt()
                        }
                        if (tag == "right" && leftMargin > otherParams!!.leftMargin) {
                            if (leftMargin > binding.bar.width) {
                                return true
                            }
                            params!!.leftMargin = leftMargin
                            v.layoutParams = params
                            if (leftMargin + dpToPx(15f) < currentParams!!.leftMargin) {
                                currentParams!!.leftMargin = leftMargin + dpToPx(15f).toInt()
                                binding.current.layoutParams = currentParams
                                seek = true
                            }
                            prevX = event.rawX.toInt()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (seek) {
                        seek()
                    }
                    prevX = 0
                    tag = ""
                    params = null
                    other = null
                    otherParams = null
                    currentParams = null
                    seek = false
                    updateBounds()
                }
                MotionEvent.ACTION_DOWN -> {
                    prevX = event.rawX.toInt()
                    params = v.layoutParams as FrameLayout.LayoutParams
                    tag = v.tag.toString()
                    other = if (tag == "left") {
                        binding.boundRight
                    } else {
                        binding.boundLeft
                    }
                    otherParams = other!!.layoutParams as FrameLayout.LayoutParams
                    currentParams = binding.current.layoutParams as FrameLayout.LayoutParams
                }
            }
            return true
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.currentPosition = binding.videoView.currentPosition
        viewModel.boundLeftPercent =
            (binding.boundLeft.layoutParams as FrameLayout.LayoutParams).leftMargin / binding.bar.width.toFloat()
        viewModel.boundRightPercent =
            (binding.boundRight.layoutParams as FrameLayout.LayoutParams).leftMargin / binding.bar.width.toFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        stop(false)
        Log.v("TEST", "saving with viewModel.status=${viewModel.status}")
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
        private lateinit var overlay: FrameLayout
        private lateinit var form: LinearLayout
        private lateinit var windowManager: WindowManager
        private lateinit var record: ImageView
        private lateinit var recording: ImageView
        private lateinit var openFull: ImageView

        // Properties related to the screen recording
        private var isRecording = false
        private lateinit var virtualDisplay: VirtualDisplay
        private lateinit var mediaProjectionCallback: MediaProjectionCallback
        private lateinit var windowParams: WindowManager.LayoutParams
        private lateinit var timeView: TextView
        private lateinit var toastView: TextView
        private val handler = Handler(Looper.getMainLooper())
        private var mediaRecorder = MediaRecorder()
        private var screenWidth = -1
        private var screenHeight = -1
        private var hideLayout = false
        private var recordingName = ""

        @SuppressLint("ClickableViewAccessibility")
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate() {
            super.onCreate()

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            // https://stackoverflow.com/questions/6446221/get-context-in-a-service
            overlay = FrameLayout(this)
            //overlay.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
            //overlay.setBackgroundColor(Color.parseColor("#80000000"))
            windowParams = WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                100,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            windowParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            windowManager.addView(overlay, windowParams)

            // Create a horizontal linear layout with three equally weighted children
            LinearLayout(this).let { layout ->
                val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                layout.orientation = LinearLayout.HORIZONTAL
                layout.layoutParams = params
                layout.setBackgroundColor(Color.parseColor("#80000000"))
                overlay?.addView(layout)

                // Create the left panel ("open full" and "go to current" icons)
                LinearLayout(this).let { left ->
                    val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                    left.layoutParams = params
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
                        image.setOnClickListener {
                            launchBrowser(false)
                        }
                        left.addView(image)
                    }
                }

                // Create the center panel (record button)
                LinearLayout(this).let { center ->
                    val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                    center.layoutParams = params
                    center.orientation = LinearLayout.HORIZONTAL
                    center.gravity = Gravity.CENTER
                    center.setBackgroundColor(Color.parseColor("#00FFFFFF"))
                    layout.addView(center)

                    // Add 3 views to center, l-frame-r
                    LinearLayout(this).let { l ->
                        val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                        l.layoutParams = params
                        l.gravity = Gravity.RIGHT
                        TextView(this).let { textView ->
                            timeView = textView
                            timeView.text = "00:00:00"
                            timeView.setTextColor(Color.parseColor("#eeeeee"))
                            timeView.textSize = 18f
                            val params = LinearLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.MATCH_PARENT
                            )
                            params.rightMargin = 10
                            timeView.gravity = Gravity.CENTER_VERTICAL
                            timeView.layoutParams = params
                            l.addView(timeView)
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
                                    if (isChecked) {
                                        toastView.visibility = TextView.VISIBLE
                                        toastView.alpha = 0.80f
                                        handler.postDelayed({
                                            toastView.animate().alpha(0.0f).setDuration(700).withEndAction {
                                                toastView.visibility = TextView.GONE
                                            }
                                        }, 1500)
                                    }
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
            // end layout

            // begin form (not in use)
            LinearLayout(this).let {
                form = it
                form.visibility = LinearLayout.GONE
                form.setBackgroundColor(resources.getColor(R.color.white))

                EditText(this).let { editText ->
                    editText.tag = "name"
                    val params = LinearLayout.LayoutParams(
                        0,
                        LayoutParams.MATCH_PARENT,
                        1.0f
                    )
                    params.leftMargin = dpToPx(50f).toInt()
                    editText.layoutParams = params
                    editText.inputType = InputType.TYPE_CLASS_TEXT
                    form.addView(editText)
                    // Keyboard issues...
                }
                Button(this).let { button ->
                    button.tag = "record"
                    val params = LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.MATCH_PARENT
                    )
                    params.leftMargin = dpToPx(10f).toInt()
                    params.rightMargin = dpToPx(25f).toInt()
                    params.topMargin = dpToPx(10f).toInt()
                    params.bottomMargin = dpToPx(10f).toInt()
                    button.layoutParams = params
                    button.text = "Record"
                    button.setTextColor(Color.parseColor("#ffffff"))
                    button.setBackgroundColor(resources.getColor(R.color.red))
                    form.addView(button)
                }
                ImageView(this).let { imageView ->
                    imageView.tag = "close"
                    val params = LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    )
                    params.leftMargin = dpToPx(25f).toInt()
                    params.rightMargin = dpToPx(15f).toInt()
                    params.gravity = Gravity.CENTER
                    imageView.layoutParams = params
                    imageView.setImageResource(R.drawable.ic_baseline_close_24)
                    form.addView(imageView)
                }
                //overlay.addView(form)
            }
            // end form

            // begin hide notice
            TextView(this, null,
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).let { textView ->

                toastView = textView
                toastView.visibility = TextView.GONE
                overlay.addView(toastView)
                val params = toastView.layoutParams as FrameLayout.LayoutParams
                params.gravity = Gravity.CENTER
                params.width = LayoutParams.WRAP_CONTENT
                params.height = LayoutParams.WRAP_CONTENT
                toastView.text = "When the recording starts, double tap anywhere to finish"
                toastView.setBackgroundColor(resources.getColor(R.color.black))
                toastView.setTextColor(resources.getColor(R.color.white))
                toastView.setPadding(12, 12, 12, 12)
                toastView.alpha = 0.80f
                toastView.gravity = Gravity.CENTER
                toastView.layoutParams = params
            }
            // end hide notice
        }

        private fun dpToPx(dp: Float) : Float {
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                resources.displayMetrics
            )
            return px
        }

        private fun launchBrowser(wasRecording: Boolean) {
            val intent = Intent(this, BrowserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("wasRecording", wasRecording)
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
                val sdf = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss")
                recordingName = sdf.format(Date())
                record?.visibility = LinearLayout.GONE
                recording?.visibility = LinearLayout.VISIBLE
                if (hideLayout) {
                    windowManager.removeView(overlay)
                    val panel = PanelLayout(this, ::stopRecording)
                    val params = WindowManager.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        -999,
                        PixelFormat.TRANSLUCENT
                    )
                    params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    windowManager.addView(panel, params)
                }
                windowParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                windowManager.updateViewLayout(overlay, windowParams)
                startRecording()
            }
            if (imageView.tag.toString() == "recording") {
                recording?.visibility = LinearLayout.GONE
                record?.visibility = LinearLayout.VISIBLE
                windowParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                windowManager.updateViewLayout(overlay, windowParams)
                stopRecording(true)
            }
        }

        private fun startRecording() {
            openFull.visibility = RelativeLayout.INVISIBLE
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setOutputFile(this.filesDir.toString() + "/${recordingName}.mp4")
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
            startTimerRecording()
        }

        private fun startTimerRecording() {
            var time = 0
            handler.postDelayed(object : Runnable {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun run() {
                    time += 100
                    timeView.text = Utils.getTime(time)
                    handler.postDelayed(this, 100)
                }
            }, 100)
        }

        private fun stopRecording(launchBrowser: Boolean) {
            if (isRecording) {
                virtualDisplay.release()
                mediaProjection!!.unregisterCallback(mediaProjectionCallback)
                // Don't loose this, causes security exception on return to service
                //mediaProjection!!.stop()
                mediaRecorder.stop()
                mediaRecorder.reset()
                isRecording = false
                if (launchBrowser) {
                    launchBrowser(true)
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

class Utils {
    companion object {
        fun getTime(ms: Int): String {
            val h = ms / (1000 * 60 * 60) % 24
            val m = ms / (1000 * 60) % 60
            val s = (ms / 1000) % 60
            return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        }
    }
}