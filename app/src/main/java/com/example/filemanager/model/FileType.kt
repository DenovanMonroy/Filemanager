package com.example.filemanager.model

enum class FileType {
    FOLDER,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    PDF,
    ARCHIVE,
    OTHER;  // Tipo por defecto para archivos desconocidos

    companion object {
        fun fromExtension(extension: String?): FileType {
            return when (extension?.lowercase()) {
                "txt", "md", "html", "xml", "json", "csv", "log", "kt", "java", "c", "cpp", "py", "js", "css" -> TEXT
                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> IMAGE
                "mp3", "wav", "ogg", "aac", "flac" -> AUDIO
                "mp4", "mkv", "avi", "mov", "webm", "3gp" -> VIDEO
                "pdf" -> PDF
                "zip", "rar", "7z", "tar", "gz" -> ARCHIVE
                else -> OTHER
            }
        }
    }
}