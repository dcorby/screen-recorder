package com.example.screenrecorder

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class VideosAdapter(private val onClick: (Video, View) -> Unit,
                    private val onRename: (Video, Button, (() -> Unit)) -> Unit,
                    private val onDelete: (Video, Button) -> Unit) :
    ListAdapter<Video, VideosAdapter.VideoItemViewHolder>(VideoItemDiffCallback) {

    var currentLayout: View? = null

    inner class VideoItemViewHolder(
        private val itemView: View,
        val onClick: (Video, View) -> Unit,
        val onRename: (Video, Button, (() -> Unit)) -> Unit,
        val onDelete: (Video, Button) -> Unit)
        : RecyclerView.ViewHolder(itemView) {

        private val textView: TextView = itemView.findViewById(R.id.text_view)
        private val delete: Button = itemView.findViewById(R.id.delete)
        private val rename: Button = itemView.findViewById(R.id.rename)
        private val layout: LinearLayout = itemView.findViewById(R.id.item_layout)

        // Bind data to view
        fun bind(video: Video) {
            textView.text = video.label

            rename.setOnClickListener {
                onRename(video, rename) {
                    delete.visibility = RelativeLayout.GONE
                    rename.visibility = RelativeLayout.GONE
                }
            }

            delete.setOnClickListener {
                onDelete(video, delete)
            }

            itemView.setOnTouchListener(object : View.OnTouchListener {
                private var handler = Handler(Looper.getMainLooper())
                private var isLong = false

                var callback = Runnable {
                    isLong = true
                    if (delete.isVisible) {
                        delete.visibility = RelativeLayout.GONE
                        rename.visibility = RelativeLayout.GONE
                    } else {
                        delete.visibility = RelativeLayout.VISIBLE
                        rename.visibility = RelativeLayout.VISIBLE
                    }
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
//                                if (delete.isVisible) {
//                                    delete.visibility = RelativeLayout.GONE
//                                    rename.visibility = RelativeLayout.GONE
//                                } else {
//                                    delete.visibility = RelativeLayout.VISIBLE
//                                    rename.visibility = RelativeLayout.VISIBLE
//                                }
                            } else {
                                //currentLayout?.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.white))
                                currentLayout?.background = ContextCompat.getDrawable(itemView.context, R.drawable.border_black_top)
                                //layout.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.gray2))
                                layout.background = ContextCompat.getDrawable(itemView.context, R.drawable.border_gray_top)
                                onClick(video, layout)
                                currentLayout = layout
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
        return VideoItemViewHolder(view, onClick, onRename, onDelete)
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
