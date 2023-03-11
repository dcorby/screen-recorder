package com.example.screenrecorder

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VideosAdapter(private val videos: MutableList<Video>,
                    private val onClick: (Video) -> Unit,
                    private val onRename: (Video?) -> Unit,
                    private val onDelete: (Video, (() -> Unit)) -> Unit) :
    ListAdapter<Video, VideosAdapter.VideoItemViewHolder>(VideoItemDiffCallback) {

    var currentLayout: View? = null
    var currentVideo: Video? = null

    // Can only edit one video at once, keep a reference to it and its layout
    var editVideo: Video? = null
    var editLayout: LinearLayout? = null

    var getDefaultBackground = { context: Context -> ContextCompat.getDrawable(context, R.drawable.border_black_top)}
    var getSelectedBackground = { context: Context -> ContextCompat.getDrawable(context, R.drawable.border_gray_top) }

    inner class VideoItemViewHolder(
        private val itemView: View,
        val onClick: (Video) -> Unit,
        val onRename: (Video?) -> Unit,
        val onDelete: (Video, (() -> Unit)) -> Unit)
        : RecyclerView.ViewHolder(itemView) {

        private val layout: LinearLayout = itemView.findViewById(R.id.item_layout)
        private val textView: TextView = layout.findViewById(R.id.text_view)
        private val delete: Button = layout.findViewById(R.id.delete)
        private val rename: Button = layout.findViewById(R.id.rename)
        private val isNew: TextView = layout.findViewById(R.id.is_new)
        private val edit: ImageView = layout.findViewById(R.id.edit)

        // Bind data to view
        fun bind(video: Video) {
            textView.text = video.label

            // Show editMode
            if (video.editMode) {
                delete.visibility = RelativeLayout.VISIBLE
                rename.visibility = RelativeLayout.VISIBLE
            } else {
                delete.visibility = RelativeLayout.GONE
                rename.visibility = RelativeLayout.GONE
            }

            // Show New!
            isNew.visibility = if (video.isNew) TextView.VISIBLE else TextView.GONE

            // Handle rename
            rename.setOnClickListener {
                delete.visibility = RelativeLayout.GONE
                rename.visibility = RelativeLayout.GONE

                val form = (rename.parent.parent.parent as View).findViewById<LinearLayout>(R.id.item_rename)
                form.visibility = LinearLayout.VISIBLE
                val editText = form.findViewById<EditText>(R.id.name)
                val button = form.findViewById<Button>(R.id.do_rename)
                val close = form.findViewById<ImageView>(R.id.close)
                button.setOnClickListener inner@{
                    val name = editText.text.toString().replace(".mp4", "").trim()
                    if (name == "") {
                        onRename(null)
                        return@inner
                    }
                    val newFile = File(video.file.parent, "${name}.mp4")
                    video.file.renameTo(newFile)
                    video.editMode = false
                    form.visibility = LinearLayout.GONE
                    edit.visibility = ImageView.VISIBLE
                    onRename(editVideo)
                }
                close.setOnClickListener {
                    video.editMode = false
                    form.visibility = LinearLayout.GONE
                    edit.visibility = ImageView.VISIBLE
                }
            }

            // Handle delete
            delete.setOnClickListener {
                video.file.delete()
                onDelete(video) {
                    delete.visibility = RelativeLayout.GONE
                    rename.visibility = RelativeLayout.GONE
                    video.editMode = false
                }
            }

            // Item select
            itemView.setOnClickListener {
                currentLayout?.background = getDefaultBackground(itemView.context)
                currentVideo?.selected = false

                // Select current
                layout.background = getSelectedBackground(itemView.context)
                video.selected = true

                // Activity callback
                onClick(video)

                // Reset current
                currentLayout = layout
                currentVideo = video

                deactivateEdit()
            }

            // Edit mode
            edit.setOnClickListener {
                deactivateEdit()

                edit.visibility = ImageView.GONE
                delete.visibility = Button.VISIBLE
                rename.visibility = Button.VISIBLE
                video.editMode = true

                editVideo = video
                editLayout = layout
            }
        }

        // Deactivate current editVideo
        private fun deactivateEdit() {
            if (editVideo != null) {
                val edit: ImageView = editLayout!!.findViewById(R.id.edit)
                val rename: Button = editLayout!!.findViewById(R.id.rename)
                val delete: Button = editLayout!!.findViewById(R.id.delete)
                edit.visibility = ImageView.VISIBLE
                delete.visibility = Button.GONE
                rename.visibility = Button.GONE
                editVideo!!.editMode = false

                editVideo = null
                editLayout = null
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoItemViewHolder(view, onClick, onRename, onDelete)
    }

    override fun onBindViewHolder(viewHolder: VideoItemViewHolder, position: Int) {
        val video = videos[position]
        viewHolder.bind(video)
    }

    override fun getItemCount(): Int {
        return videos.size
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
