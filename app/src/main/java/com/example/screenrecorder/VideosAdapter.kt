package com.example.screenrecorder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class VideosAdapter(private val onClick: (Video) -> Unit) :
    ListAdapter<Video, VideosAdapter.VideoItemViewHolder>(VideoItemDiffCallback) {

    inner class VideoItemViewHolder(
        private val itemView: View,
        val onClick: (Video) -> Unit) : RecyclerView.ViewHolder(itemView) {

        private val textView: TextView = itemView.findViewById(R.id.text_view)

        // Bind data to view
        fun bind(video: Video) {
            textView.text = video.label
            itemView.setOnClickListener {
                onClick(video)
            }
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
