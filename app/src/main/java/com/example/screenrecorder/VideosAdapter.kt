package com.example.screenrecorder

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VideosAdapter(private val videos: MutableList<Video>,
                    private val onClick: (Video) -> Unit,
                    private val onRename: (Video?) -> Unit,
                    private val onDelete: (Video, (() -> Unit)) -> Unit) :
    ListAdapter<Video, VideosAdapter.VideoItemViewHolder>(VideoItemDiffCallback) {

    var recyclerView: RecyclerView? = null

    // 3 states to keep track of
    var currentVideo: Video? = null
    var currentLayout: LinearLayout? = null

    var editVideo: Video? = null
    var editLayout: LinearLayout? = null

    var renameVideo: Video? = null
    var renameLayout: LinearLayout? = null

    var getDefaultBackground = { context: Context -> ContextCompat.getDrawable(context, R.drawable.border_black_top) }
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

            // Set current
            if (video.stateCurrent) {
                layout.background = getSelectedBackground(itemView.context)
                currentVideo = video
                currentLayout = layout
            }

            // Set editing
            if (video.stateEditing) {
                delete.visibility = Button.VISIBLE
                rename.visibility = Button.VISIBLE
                edit.visibility = ImageView.GONE
                editVideo = video
                editLayout = layout
            } else {
                delete.visibility = Button.GONE
                rename.visibility = Button.GONE
                edit.visibility = ImageView.VISIBLE
            }

            // Show New!
            isNew.visibility = if (video.isNew) TextView.VISIBLE else TextView.GONE

            fun enterRename() {
                delete.visibility = RelativeLayout.GONE
                rename.visibility = RelativeLayout.GONE
                renameVideo = editVideo
                editVideo?.stateRenaming = true
                renameVideo?.stateRenaming = true
                renameLayout = (rename.parent.parent.parent as View).findViewById(R.id.item_rename)
                renameLayout!!.visibility = LinearLayout.VISIBLE

                val editText = renameLayout!!.findViewById<EditText>(R.id.name)
                editText.setText(renameVideo?.renameTo)
                val button = renameLayout!!.findViewById<Button>(R.id.do_rename)
                val close = renameLayout!!.findViewById<ImageView>(R.id.close)
                editText.addTextChangedListener { editable ->
                    renameVideo?.renameTo = editable.toString()
                }
                button.setOnClickListener inner@{
                    val name = editText.text.toString().replace(".mp4", "").trim()
                    if (name == "") {
                        onRename(null)
                        return@inner
                    }
                    val newFile = File(renameVideo?.file?.parent, "${name}.mp4")
                    renameVideo?.file?.renameTo(newFile)
                    exitRename()
                    onRename(renameVideo)
                }
                close.setOnClickListener {
                    exitRename()
                }
            }
            rename.setOnClickListener {
                enterRename()
            }
            if (video.stateRenaming) {
                enterRename()
            }

            // Handle delete
            delete.setOnClickListener {
                editVideo?.file?.delete()
                onDelete(editVideo!!) {
                    delete.visibility = RelativeLayout.GONE
                    rename.visibility = RelativeLayout.GONE
                    video.stateEditing = false
                }
            }

            // Item select
            itemView.setOnClickListener {
                currentLayout?.background = getDefaultBackground(itemView.context)
                currentVideo?.stateCurrent = false

                // Select current
                layout.background = getSelectedBackground(itemView.context)
                video.stateCurrent = true

                // Activity callback
                onClick(video)

                // Reset current
                currentLayout = layout
                currentVideo = video

                deactivateEdit()
                renameLayout?.visibility = LinearLayout.GONE
            }

            // Edit mode
            edit.setOnClickListener {
                deactivateEdit()

                edit.visibility = ImageView.GONE
                delete.visibility = Button.VISIBLE
                rename.visibility = Button.VISIBLE
                video.stateEditing = true

                editVideo = video
                editLayout = layout
            }
        }

        // Handle rename
        private fun exitRename() {
            renameLayout?.visibility = LinearLayout.GONE
            editVideo?.stateEditing = false
            editVideo?.stateRenaming = false
            renameVideo?.stateEditing = false
            renameVideo?.stateRenaming = false
            renameVideo?.renameTo = ""
            editVideo = null
            editLayout = null
            renameVideo = null
            renameLayout = null
            edit.visibility = ImageView.VISIBLE
        }

        // Deactivate current editVideo
        private fun deactivateEdit() {
            if (editVideo != null && editLayout != null) {
                val edit: ImageView = editLayout!!.findViewById(R.id.edit)
                val rename: Button = editLayout!!.findViewById(R.id.rename)
                val delete: Button = editLayout!!.findViewById(R.id.delete)
                edit.visibility = ImageView.VISIBLE
                delete.visibility = Button.GONE
                rename.visibility = Button.GONE
                editVideo!!.stateEditing = false

                editVideo = null
                editLayout = null
            }
            if (renameVideo != null && renameLayout != null) {
                exitRename()
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

    override fun onAttachedToRecyclerView(_recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(_recyclerView)
        recyclerView = _recyclerView
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