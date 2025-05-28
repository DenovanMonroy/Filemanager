package com.example.filemanager.data.repository

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class FileRepository(private val context: Context) {

    // Explorar directorios del almacenamiento interno
    suspend fun getInternalStorageFiles(path: String? = null, includeHidden: Boolean = true): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val directory = if (path != null) {
                File(path).also {
                    Log.d("FileRepository", "Intentando acceder a directorio específico: $path")
                }
            } else {
                // Acceder directamente al almacenamiento interno real
                val internalStorageDir = Environment.getExternalStorageDirectory()
                Log.d("FileRepository", "Intentando acceder al almacenamiento interno: ${internalStorageDir.absolutePath}")

                // Si no podemos acceder al almacenamiento interno, intentar con alternativas
                if (!internalStorageDir.exists() || !internalStorageDir.canRead()) {
                    Log.w("FileRepository", "No se puede acceder al almacenamiento interno, probando alternativas")

                    // Intentar con diferentes directorios comunes
                    val options = listOf(
                        context.filesDir,
                        context.getExternalFilesDir(null),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        File(context.filesDir, "samples")
                    )

                    // Usar el primer directorio que exista y sea legible
                    options.firstOrNull { it != null && it.exists() && it.canRead() } ?: context.filesDir
                } else {
                    internalStorageDir
                }
            }

            Log.d("FileRepository", "Directorio seleccionado: ${directory.absolutePath}")
            Log.d("FileRepository", "¿El directorio existe? ${directory.exists()}")
            Log.d("FileRepository", "¿El directorio se puede leer? ${directory.canRead()}")

            if (!directory.exists()) {
                Log.e("FileRepository", "El directorio no existe")
                return@withContext emptyList<FileItem>()
            }

            if (!directory.isDirectory) {
                Log.e("FileRepository", "La ruta no es un directorio")
                return@withContext emptyList<FileItem>()
            }

            if (!directory.canRead()) {
                Log.e("FileRepository", "No se tienen permisos de lectura en el directorio")
                return@withContext emptyList<FileItem>()
            }

            val files = directory.listFiles()

            if (files == null) {
                Log.e("FileRepository", "No se pudo listar los archivos (null)")
                return@withContext emptyList<FileItem>()
            }

            if (files.isEmpty()) {
                Log.w("FileRepository", "El directorio está vacío")
                return@withContext emptyList<FileItem>()
            }

            Log.d("FileRepository", "Número de archivos encontrados: ${files.size}")

            // Incluir TODOS los archivos, filtrando archivos ocultos si es necesario
            val result = files
                .filter { file -> includeHidden || !file.name.startsWith(".") }
                .map { file ->
                    Log.d("FileRepository", "Archivo encontrado: ${file.name}, tamaño: ${file.length()}, directorio: ${file.isDirectory}")
                    FileItem.fromFile(file)
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            Log.d("FileRepository", "Número total de archivos procesados: ${result.size}")
            return@withContext result

        } catch (e: Exception) {
            Log.e("FileRepository", "Error al obtener archivos internos", e)
            return@withContext emptyList<FileItem>()
        }
    }

    // Explorar directorios del almacenamiento externo (usando SAF para Android 11+)
    suspend fun getExternalStorageFiles(uri: Uri?): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            if (uri == null) {
                // Fallback a método tradicional para versiones antiguas o sin permisos SAF
                Log.d("FileRepository", "URI es null, intentando usar método tradicional")
                val externalDir = Environment.getExternalStorageDirectory()

                if (externalDir.exists() && externalDir.canRead()) {
                    val files = externalDir.listFiles() ?: return@withContext emptyList<FileItem>()
                    Log.d("FileRepository", "Archivos externos encontrados: ${files.size}")
                    return@withContext files.map { FileItem.fromFile(it) }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                }

                Log.w("FileRepository", "No se pudo acceder al almacenamiento externo tradicional")
                return@withContext emptyList()
            } else {
                // Usar Storage Access Framework para Android 11+
                Log.d("FileRepository", "Usando SAF para acceder a: $uri")
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                    ?: return@withContext emptyList<FileItem>()

                val result = documentFile.listFiles().map { file ->
                    Log.d("FileRepository", "Archivo SAF encontrado: ${file.name}")

                    // Obtener la extensión del archivo
                    val extension = file.name?.let {
                        if (it.contains(".")) it.substringAfterLast(".") else null
                    }

                    FileItem(
                        name = file.name ?: "Unknown",
                        path = file.uri.toString(),
                        uri = file.uri,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        type = when {
                            file.isDirectory -> FileType.FOLDER
                            else -> FileType.fromExtension(extension)
                        }
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                Log.d("FileRepository", "Archivos SAF procesados: ${result.size}")
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e("FileRepository", "Error al obtener archivos externos", e)
            return@withContext emptyList<FileItem>()
        }
    }

    // Leer contenido de archivos de texto
    suspend fun readTextFile(uri: Uri?): String = withContext(Dispatchers.IO) {
        try {
            if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    return@withContext inputStream.bufferedReader().use { it.readText() }
                }
            } else {
                throw FileNotFoundException("URI no válida")
            }
        } catch (e: Exception) {
            Log.e("FileRepository", "Error al leer archivo desde URI", e)
            return@withContext "Error al leer el archivo: ${e.message}"
        }
        return@withContext "No se pudo leer el archivo"
    }

    suspend fun readTextFile(path: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return@withContext file.readText()
            } else {
                throw FileNotFoundException("Archivo no encontrado o no se puede leer: $path")
            }
        } catch (e: Exception) {
            Log.e("FileRepository", "Error al leer archivo desde path: $path", e)
            return@withContext "Error al leer el archivo: ${e.message}"
        }
    }

    // Obtener todos los directorios de almacenamiento disponibles
    suspend fun getStorageDirectories(): List<File> = withContext(Dispatchers.IO) {
        val directories = mutableListOf<File>()

        try {
            // Agregar almacenamiento interno principal
            directories.add(context.filesDir)

            // Directorio de la aplicación en almacenamiento externo
            context.getExternalFilesDir(null)?.let { directories.add(it) }

            // Para Android 11 y superior, el acceso es más restringido
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Usar directorios específicos de la app para Android 11+
                val externalDirs = context.getExternalFilesDirs(null)
                for (dir in externalDirs) {
                    if (dir != null) {
                        directories.add(dir)
                    }
                }
            } else {
                // Para versiones anteriores, intentar acceder al almacenamiento externo
                val externalStorage = Environment.getExternalStorageDirectory()
                if (externalStorage.exists() && externalStorage.canRead()) {
                    directories.add(externalStorage)
                }

                // Directorios públicos comunes
                listOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_DOCUMENTS
                ).forEach { type ->
                    val dir = Environment.getExternalStoragePublicDirectory(type)
                    if (dir.exists() && dir.canRead()) {
                        directories.add(dir)
                    }
                }
            }

            // Intenta acceder a tarjetas SD en dispositivos que las soporten
            try {
                val extStoragePath = System.getenv("SECONDARY_STORAGE")
                if (extStoragePath != null) {
                    val paths = extStoragePath.split(":")
                    for (path in paths) {
                        val file = File(path)
                        if (file.exists() && file.canRead() && file.isDirectory) {
                            directories.add(file)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileRepository", "Error accediendo a almacenamiento secundario", e)
            }

        } catch (e: Exception) {
            Log.e("FileRepository", "Error obteniendo directorios de almacenamiento", e)
        }

        Log.d("FileRepository", "Directorios de almacenamiento encontrados: ${directories.size}")
        directories.forEach {
            Log.d("FileRepository", "  - ${it.absolutePath} (${if (it.exists()) "existe" else "no existe"}, ${if (it.canRead()) "legible" else "no legible"})")
        }

        return@withContext directories
    }

    // Nuevo método para acceder a todos los archivos usando MediaStore
    @SuppressLint("InlinedApi")
    suspend fun getAllFilesWithMediaStore(): List<FileItem> = withContext(Dispatchers.IO) {
        val fileItems = mutableListOf<FileItem>()

        try {
            // Usar MediaStore para acceder a los archivos
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA
            )

            // Consultar todos los archivos - no solo media
            val selection = null
            val selectionArgs = null
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                Log.d("FileRepository", "MediaStore encontró ${cursor.count} archivos")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unknown"
                    val size = cursor.getLong(sizeColumn)
                    val date = cursor.getLong(dateColumn) * 1000 // Convertir a milisegundos
                    val mimeType = cursor.getString(mimeTypeColumn) ?: ""
                    val path = cursor.getString(dataColumn) ?: ""

                    val uri = ContentUris.withAppendedId(queryUri, id)

                    val extension = if (name.contains(".")) {
                        name.substringAfterLast(".")
                    } else {
                        null
                    }

                    val type = FileType.fromExtension(extension)
                    val isDirectory = File(path).isDirectory

                    val fileItem = FileItem(
                        name = name,
                        path = path,
                        uri = uri,
                        isDirectory = isDirectory,
                        size = size,
                        lastModified = date,
                        type = if (isDirectory) FileType.FOLDER else type
                    )

                    fileItems.add(fileItem)
                    Log.d("FileRepository", "Archivo MediaStore: $name, tipo: $type")
                }
            }
        } catch (e: Exception) {
            Log.e("FileRepository", "Error al obtener archivos con MediaStore", e)
        }

        return@withContext fileItems
    }
}