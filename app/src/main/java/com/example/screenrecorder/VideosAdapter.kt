package com.example.screenrecorder

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class VideosAdapter(private val onClick: (Video) -> Unit) :
    ListAdapter<Video, VideosAdapter.VideoItemViewHolder>(VideoItemDiffCallback) {

    var currentItemView: View? = null

    inner class VideoItemViewHolder(
        private val itemView: View,
        val onClick: (Video) -> Unit) : RecyclerView.ViewHolder(itemView) {

        private val textView: TextView = itemView.findViewById(R.id.text_view)

        // Bind data to view
        fun bind(video: Video) {
            textView.text = video.label

            itemView.setOnTouchListener(object : View.OnTouchListener {
                private var handler = Handler(Looper.getMainLooper())
                private var isLong = false

                var callback = Runnable {
                    isLong = true
                }
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    if (event == null || v == null) {
                        return true
                    }
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            handler.postDelayed(callback,500)
                            return true
                        }
                    }
                    when (event.action) {
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(callback)
                            if (isLong) {
                                Log.v("TEST", "long click")
                            } else {
                                currentItemView?.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.white))
                                itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.gray2))
                                onClick(video)
                                currentItemView = itemView
                            }
                            isLong = false
                            return true
                        }
                    }
                    return true
                }
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoItemViewHolder(view, onClick)
    }

    override fun onBindViewHolder(viewHolder: VideoItemViewHolder, position: Int) {
        val listItem = getItem(position)
        viewHolder.bind(listItem)
    }

    override fun onViewRecycled(holder: VideoItemViewHolder) {
        super.onViewRecycled(holder)
    }


}

object VideoItemDiffCallback : DiffUtil.ItemCallback<Video>() {
    override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean {
        return oldItem.filename == newItem.filename
    }
    override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean {
        return oldItem.filename == newItem.filename
    }
}
