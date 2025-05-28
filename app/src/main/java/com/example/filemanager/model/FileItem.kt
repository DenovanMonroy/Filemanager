package com.example.filemanager.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Parcelize
data class FileItem(
    val name: String,
    val path: String,
    val uri: Uri? = null,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val type: FileType
) : Parcelable {

    val formattedSize: String
        get() = when {
            isDirectory -> "Carpeta"
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }

    val formattedDate: String
        get() {
            val date = Date(lastModified)
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return format.format(date)
        }

    val fileExtension: String?
        get() = if (!isDirectory && name.contains(".")) {
            name.substringAfterLast(".")
        } else {
            null
        }

    companion object {
        fun fromFile(file: File): FileItem {
            val extension = if (file.isFile && file.name.contains(".")) {
                file.name.substringAfterLast(".")
            } else {
                null
            }

            val type = if (file.isDirectory) {
                FileType.FOLDER
            } else {
                FileType.fromExtension(extension)
            }

            return FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
                type = type
            )
        }
    }
}