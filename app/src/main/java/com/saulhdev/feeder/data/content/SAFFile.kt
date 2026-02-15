package com.saulhdev.feeder.data.content

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.saulhdev.feeder.R
import java.io.FileInputStream
import java.io.FileOutputStream

// Source: Neo Store
class SAFFile(
    context: Context,
    val uri: Uri,
) {
    private val mContext: Context = context

    fun read(): String? =
        try {
            mContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read $uri", t)
            null
        }

    fun delete(): Boolean =
        try {
            mContext.contentResolver.delete(uri, null, null) != 0
        } catch (e: UnsupportedOperationException) {
            DocumentFile.fromSingleUri(mContext, uri)?.delete() ?: false
        }

    fun share(context: Context) {
        val shareTitle = context.getString(R.string.share)
        val shareText = context.getString(R.string.file_share_text)
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = MAIN_MIME_TYPE
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareTitle)
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
        context.startActivity(Intent.createChooser(shareIntent, shareTitle))
    }

    companion object {
        const val TAG: String = "SAFFile"

        const val MAIN_MIME_TYPE = "application/octet-stream"
        const val BOOKMARKS_EXTENSION = "bkm"
        const val BOOKMARKS_MIME_TYPE = "application/vnd.neostore.bkm"
        val BOOKMARKS_MIME_ARRAY = arrayOf(BOOKMARKS_MIME_TYPE, MAIN_MIME_TYPE)

        @SuppressLint("MissingPermission")
        fun write(
            context: Context,
            location: Uri,
            content: String,
        ): Boolean =
            try {
                context.contentResolver.openFileDescriptor(location, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).bufferedWriter().use { writer ->
                        writer.write(content)
                    }
                }
                Log.d(TAG, "Success to create backup")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create backup", t)
                false
            }
    }
}
