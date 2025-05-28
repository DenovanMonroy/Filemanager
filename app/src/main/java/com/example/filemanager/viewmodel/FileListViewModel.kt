package com.example.filemanager.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanager.data.repository.FileRepository
import com.example.filemanager.model.FileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileListViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application.applicationContext)

    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isExternalStorage = MutableStateFlow(false)
    val isExternalStorage: StateFlow<Boolean> = _isExternalStorage

    // Añadir estado para archivos ocultos
    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles

    fun loadInternalStorageFiles(path: String? = null) {
        _isExternalStorage.value = false
        _currentPath.value = path
        _currentUri.value = null
        _isLoading.value = true

        Log.d("FileListViewModel", "Cargando archivos internos desde: $path")

        viewModelScope.launch {
            try {
                val filesList = fileRepository.getInternalStorageFiles(path, _showHiddenFiles.value)
                Log.d("FileListViewModel", "Archivos cargados: ${filesList.size}")
                _files.value = filesList
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("FileListViewModel", "Error al cargar archivos", e)
                _errorMessage.value = "Error al cargar archivos: ${e.message}"
                _files.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadExternalStorageFiles(uri: Uri? = null) {
        _isExternalStorage.value = true
        _currentUri.value = uri
        _currentPath.value = null
        _isLoading.value = true

        Log.d("FileListViewModel", "Cargando archivos externos con URI: $uri")

        viewModelScope.launch {
            try {
                val filesList = fileRepository.getExternalStorageFiles(uri)
                Log.d("FileListViewModel", "Archivos externos cargados: ${filesList.size}")
                _files.value = filesList
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("FileListViewModel", "Error al cargar archivos externos", e)
                _errorMessage.value = "Error al cargar archivos externos: ${e.message}"
                _files.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Nuevo método para cargar archivos usando MediaStore
    fun loadAllFilesWithMediaStore() {
        _isExternalStorage.value = false
        _currentPath.value = "MediaStore Query"
        _currentUri.value = null
        _isLoading.value = true

        Log.d("FileListViewModel", "Cargando todos los archivos usando MediaStore")

        viewModelScope.launch {
            try {
                val filesList = fileRepository.getAllFilesWithMediaStore()
                Log.d("FileListViewModel", "Archivos cargados con MediaStore: ${filesList.size}")
                _files.value = filesList
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("FileListViewModel", "Error al cargar archivos con MediaStore", e)
                _errorMessage.value = "Error al cargar archivos: ${e.message}"
                _files.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Método para alternar la visualización de archivos ocultos
    fun toggleHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        // Recargar los archivos con la nueva configuración
        loadInternalStorageFiles(_currentPath.value)
    }

    fun navigateUp(): Boolean {
        if (_isExternalStorage.value) {
            // Para navegación en almacenamiento externo con SAF
            Log.d("FileListViewModel", "Intentando navegar hacia arriba en almacenamiento externo")
            loadExternalStorageFiles(null)
            return true
        } else {
            // Para navegación en almacenamiento interno
            val currentPath = _currentPath.value ?: return false
            Log.d("FileListViewModel", "Intentando navegar hacia arriba desde: $currentPath")

            val parentFile = File(currentPath).parentFile ?: return false

            if (parentFile.canRead()) {
                Log.d("FileListViewModel", "Navegando al directorio padre: ${parentFile.absolutePath}")
                loadInternalStorageFiles(parentFile.absolutePath)
                return true
            }

            Log.w("FileListViewModel", "No se puede navegar hacia arriba, no se puede leer el directorio padre")
            return false
        }
    }

    fun openFile(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            if (_isExternalStorage.value && fileItem.uri != null) {
                Log.d("FileListViewModel", "Abriendo directorio externo: ${fileItem.name}")
                loadExternalStorageFiles(fileItem.uri)
            } else {
                Log.d("FileListViewModel", "Abriendo directorio interno: ${fileItem.path}")
                loadInternalStorageFiles(fileItem.path)
            }
        } else {
            Log.d("FileListViewModel", "Archivo seleccionado: ${fileItem.name}, tipo: ${fileItem.type}")
            // La navegación a la pantalla de visualización se maneja en la UI
        }
    }

    // Función para depurar los directorios disponibles
    fun debugAvailableStorageLocations() {
        viewModelScope.launch {
            Log.d("FileListViewModel", "====== DEPURACIÓN DE ALMACENAMIENTO ======")

            val context = getApplication<Application>().applicationContext

            // Comprobar filesDir (almacenamiento interno de la app)
            val appFiles = context.filesDir
            Log.d("FileListViewModel", "filesDir: ${appFiles.absolutePath}")
            Log.d("FileListViewModel", "  ¿Existe? ${appFiles.exists()}")
            Log.d("FileListViewModel", "  ¿Legible? ${appFiles.canRead()}")
            Log.d("FileListViewModel", "  Archivos: ${appFiles.list()?.size ?: 0}")

            // Comprobar getExternalFilesDir (almacenamiento externo específico de la app)
            val externalAppFiles = context.getExternalFilesDir(null)
            Log.d("FileListViewModel", "getExternalFilesDir: ${externalAppFiles?.absolutePath}")
            Log.d("FileListViewModel", "  ¿Existe? ${externalAppFiles?.exists()}")
            Log.d("FileListViewModel", "  ¿Legible? ${externalAppFiles?.canRead()}")
            Log.d("FileListViewModel", "  Archivos: ${externalAppFiles?.list()?.size ?: 0}")

            // Comprobar almacenamiento externo público
            val externalStorage = Environment.getExternalStorageDirectory()
            Log.d("FileListViewModel", "Almacenamiento externo: ${externalStorage.absolutePath}")
            Log.d("FileListViewModel", "  ¿Existe? ${externalStorage.exists()}")
            Log.d("FileListViewModel", "  ¿Legible? ${externalStorage.canRead()}")
            Log.d("FileListViewModel", "  Archivos: ${externalStorage.list()?.size ?: 0}")

            // Comprobar directorios públicos comunes
            val directoriesToCheck = listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_DOCUMENTS
            )

            for (dirType in directoriesToCheck) {
                val dir = Environment.getExternalStoragePublicDirectory(dirType)
                Log.d("FileListViewModel", "Directorio $dirType: ${dir.absolutePath}")
                Log.d("FileListViewModel", "  ¿Existe? ${dir.exists()}")
                Log.d("FileListViewModel", "  ¿Legible? ${dir.canRead()}")
                Log.d("FileListViewModel", "  Archivos: ${dir.list()?.size ?: 0}")
            }

            // Comprobar directorio de muestras
            val sampleDir = File(context.filesDir, "samples")
            Log.d("FileListViewModel", "Directorio de muestras: ${sampleDir.absolutePath}")
            Log.d("FileListViewModel", "  ¿Existe? ${sampleDir.exists()}")
            Log.d("FileListViewModel", "  ¿Legible? ${sampleDir.canRead()}")
            Log.d("FileListViewModel", "  Archivos: ${sampleDir.list()?.joinToString() ?: "ninguno"}")

            Log.d("FileListViewModel", "========================================")
        }
    }

    // Función para probar todos los directorios posibles
    fun testAllDirectories() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            // Lista de directorios a probar
            val dirsToTest = mutableListOf<File>()

            // Agregar directorio interno de la app
            dirsToTest.add(context.filesDir)

            // Agregar directorio de muestras
            dirsToTest.add(File(context.filesDir, "samples"))

            // Agregar directorio externo específico de la app
            context.getExternalFilesDir(null)?.let { dirsToTest.add(it) }

            // Agregar directorio de almacenamiento externo principal
            dirsToTest.add(Environment.getExternalStorageDirectory())

            // Agregar directorios públicos comunes
            dirsToTest.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            dirsToTest.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
            dirsToTest.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
            dirsToTest.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))

            // Probar cada directorio
            for (dir in dirsToTest) {
                Log.d("FileListViewModel", "Probando directorio: ${dir.absolutePath}")
                if (dir.exists() && dir.canRead()) {
                    val files = dir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        Log.d("FileListViewModel", "¡Directorio con archivos encontrado! ${dir.absolutePath}")
                        loadInternalStorageFiles(dir.absolutePath)
                        return@launch
                    }
                }
            }

            // Si llegamos aquí, no se encontró ningún directorio con archivos
            Log.d("FileListViewModel", "No se encontró ningún directorio con archivos.")

            // Intentar crear archivos en un directorio de la app y luego cargarlo
            try {
                val sampleDir = File(context.filesDir, "samples")
                if (!sampleDir.exists()) {
                    sampleDir.mkdir()
                }

                // Crear un archivo de texto
                val textFile = File(sampleDir, "ejemplo_test.txt")
                textFile.writeText("Archivo de prueba creado: ${System.currentTimeMillis()}")

                Log.d("FileListViewModel", "Archivo de prueba creado en: ${textFile.absolutePath}")
                loadInternalStorageFiles(sampleDir.absolutePath)
            } catch (e: Exception) {
                Log.e("FileListViewModel", "Error al crear archivo de prueba", e)
            }
        }
    }

    // Función para cargar una ubicación segura
    fun loadSafeLocation() {
        Log.d("FileListViewModel", "Buscando una ubicación segura para cargar archivos")

        viewModelScope.launch {
            try {
                // Obtener todos los directorios de almacenamiento disponibles
                val storageDirectories = fileRepository.getStorageDirectories()

                // Probar cada directorio hasta encontrar uno con archivos
                for (dir in storageDirectories) {
                    if (dir.exists() && dir.canRead()) {
                        val files = dir.listFiles()
                        if (files != null && files.isNotEmpty()) {
                            Log.d("FileListViewModel", "Directorio con archivos encontrado: ${dir.absolutePath}")
                            loadInternalStorageFiles(dir.absolutePath)
                            return@launch
                        }
                    }
                }

                // Si no encontramos ningún directorio con archivos, crear archivos de ejemplo
                val context = getApplication<Application>().applicationContext
                val sampleDir = File(context.filesDir, "samples")
                if (!sampleDir.exists()) {
                    sampleDir.mkdir()
                }

                // Verificar si ya tiene archivos
                if (sampleDir.exists() && sampleDir.list()?.isEmpty() != false) {
                    // Crear archivos de ejemplo
                    try {
                        // Crear un archivo de texto
                        val textFile = File(sampleDir, "ejemplo_fallback.txt")
                        textFile.writeText("Archivo de fallback creado: ${System.currentTimeMillis()}")

                        // Crear una imagen simple
                        val imageFile = File(sampleDir, "imagen_fallback.jpg")
                        imageFile.writeBytes(ByteArray(1024) { (it % 256).toByte() })

                        Log.d("FileListViewModel", "Archivos de fallback creados en: ${sampleDir.absolutePath}")
                    } catch (e: Exception) {
                        Log.e("FileListViewModel", "Error al crear archivos de fallback", e)
                    }
                }

                // Cargar el directorio de muestras
                loadInternalStorageFiles(sampleDir.absolutePath)

            } catch (e: Exception) {
                Log.e("FileListViewModel", "Error al cargar ubicación segura", e)
                _errorMessage.value = "Error al cargar ubicación segura: ${e.message}"
                loadInternalStorageFiles() // Fallback
            }
        }
    }

    // Inicializar con una ubicación segura
    init {
        Log.d("FileListViewModel", "Inicializando ViewModel")
        debugAvailableStorageLocations()
        loadSafeLocation()
    }
}