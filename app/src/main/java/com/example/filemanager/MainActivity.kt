package com.example.filemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.FileType
import com.example.filemanager.ui.screens.BluetoothShareScreen
import com.example.filemanager.ui.screens.FileListScreen
import com.example.filemanager.ui.screens.ImageViewerScreen
import com.example.filemanager.ui.screens.TextViewerScreen
import com.example.filemanager.ui.theme.FileManagerTheme
import com.example.filemanager.viewmodel.FileListViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("MainActivity", "Todos los permisos concedidos")
            // Aquí podríamos llamar a un ViewModel compartido para iniciar la carga
            createSampleFiles()
        } else {
            Log.w("MainActivity", "Algunos permisos denegados: ${permissions.entries.filter { !it.value }.map { it.key }}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar y solicitar permisos
        checkAndRequestPermissions()

        setContent {
            FileManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileManagerApp()
                }
            }
        }
    }

    // Verificar y solicitar permiso especial de acceso completo en Android 11+
    private fun checkForAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                    Toast.makeText(this, "Por favor, concede permiso para acceder a todos los archivos", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error al solicitar permiso de almacenamiento completo", e)
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        try {
            // Para Android 11+, necesitamos permiso especial
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                checkForAllFilesPermission()
            }

            val permissionsToRequest = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isNotEmpty()) {
                Log.d("MainActivity", "Solicitando permisos: ${permissionsToRequest.joinToString()}")
                permissionLauncher.launch(permissionsToRequest)
            } else {
                Log.d("MainActivity", "Todos los permisos ya están concedidos")
                // Iniciar la carga de archivos aquí, ya que los permisos están concedidos
                createSampleFiles()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al verificar o solicitar permisos", e)
        }
    }

    // Función para crear archivos de ejemplo
    private fun createSampleFiles() {
        try {
            // Crear en múltiples ubicaciones para asegurar visibilidad
            val locations = listOf(
                applicationContext.filesDir,
                applicationContext.getExternalFilesDir(null) // Este puede ser null
            )

            for (location in locations) {
                // Usamos el operador de llamada segura (?.) para evitar NullPointerException
                location?.let { dir ->
                    val sampleDir = File(dir, "samples")

                    if (!sampleDir.exists()) {
                        val created = sampleDir.mkdir()
                        Log.d("MainActivity", "Directorio de muestras creado en ${dir.absolutePath}: $created")
                    }

                    // Crear archivo de texto
                    val textFile = File(sampleDir, "ejemplo_${System.currentTimeMillis()}.txt")
                    textFile.writeText("Este es un archivo de texto de ejemplo creado en: ${System.currentTimeMillis()}")
                    Log.d("MainActivity", "Archivo de texto creado: ${textFile.absolutePath}")

                    // Crear archivo markdown
                    val mdFile = File(sampleDir, "readme_${System.currentTimeMillis()}.md")
                    mdFile.writeText("# Archivo Markdown\n\nEste es un **archivo markdown** de ejemplo.\nCreado en: ${System.currentTimeMillis()}")
                    Log.d("MainActivity", "Archivo markdown creado: ${mdFile.absolutePath}")

                    // Crear una imagen simple (un archivo binario)
                    val imageFile = File(sampleDir, "imagen_${System.currentTimeMillis()}.jpg")
                    // Simulamos una imagen con bytes aleatorios
                    imageFile.writeBytes(ByteArray(1024) { (it % 256).toByte() })
                    Log.d("MainActivity", "Imagen simulada creada: ${imageFile.absolutePath}")

                    // Crear un archivo PDF (simulado)
                    val pdfFile = File(sampleDir, "documento_${System.currentTimeMillis()}.pdf")
                    pdfFile.writeBytes(ByteArray(2048) { (it % 256).toByte() })
                    Log.d("MainActivity", "PDF simulado creado: ${pdfFile.absolutePath}")

                    // Crear un archivo de audio (simulado)
                    val audioFile = File(sampleDir, "audio_${System.currentTimeMillis()}.mp3")
                    audioFile.writeBytes(ByteArray(1536) { (it % 256).toByte() })
                    Log.d("MainActivity", "Audio simulado creado: ${audioFile.absolutePath}")

                    // Crear un archivo de video (simulado)
                    val videoFile = File(sampleDir, "video_${System.currentTimeMillis()}.mp4")
                    videoFile.writeBytes(ByteArray(2560) { (it % 256).toByte() })
                    Log.d("MainActivity", "Video simulado creado: ${videoFile.absolutePath}")

                    // Crear un archivo ZIP (simulado)
                    val zipFile = File(sampleDir, "comprimido_${System.currentTimeMillis()}.zip")
                    zipFile.writeBytes(ByteArray(1792) { (it % 256).toByte() })
                    Log.d("MainActivity", "ZIP simulado creado: ${zipFile.absolutePath}")

                    Log.d("MainActivity", "Archivos de muestra creados en: ${sampleDir.absolutePath}")
                    Log.d("MainActivity", "Contenido del directorio: ${sampleDir.list()?.joinToString() ?: "no se pudo listar"}")
                } ?: run {
                    // Este bloque se ejecuta si location es null
                    Log.w("MainActivity", "Ubicación nula, no se pueden crear archivos de ejemplo aquí")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al crear archivos de ejemplo", e)
        }
    }
}

@Composable
fun FileManagerApp() {
    val navController = rememberNavController()
    val fileListViewModel: FileListViewModel = viewModel()

    // Usamos un NavHost para la navegación entre pantallas
    NavHost(navController = navController, startDestination = "fileList") {
        composable("fileList") {
            FileListScreen(
                viewModel = fileListViewModel,
                onNavigateToTextViewer = { fileItem ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("fileItem", fileItem)
                    navController.navigate("textViewer")
                },
                onNavigateToImageViewer = { fileItem ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("fileItem", fileItem)
                    navController.navigate("imageViewer")
                },
                onShareViaBluetoothClicked = { fileItem ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("fileItem", fileItem)
                    navController.navigate("bluetoothShare")
                }
            )
        }

        composable("textViewer") {
            val fileItem = navController.previousBackStackEntry?.savedStateHandle?.get<FileItem>("fileItem")

            if (fileItem != null && fileItem.type == FileType.TEXT) {
                TextViewerScreen(
                    fileItem = fileItem,
                    onBackPressed = { navController.popBackStack() },
                    onShareViaBluetoothClicked = {
                        navController.navigate("bluetoothShare")
                    }
                )
            }
        }

        composable("imageViewer") {
            val fileItem = navController.previousBackStackEntry?.savedStateHandle?.get<FileItem>("fileItem")

            if (fileItem != null && fileItem.type == FileType.IMAGE) {
                ImageViewerScreen(
                    fileItem = fileItem,
                    onBackPressed = { navController.popBackStack() },
                    onShareViaBluetoothClicked = {
                        navController.navigate("bluetoothShare")
                    }
                )
            }
        }

        composable("bluetoothShare") {
            val fileItem = navController.previousBackStackEntry?.savedStateHandle?.get<FileItem>("fileItem")

            if (fileItem != null) {
                BluetoothShareScreen(
                    fileItem = fileItem,
                    onBackPressed = { navController.popBackStack() }
                )
            }
        }
    }
}