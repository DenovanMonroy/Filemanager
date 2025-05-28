package com.example.filemanager.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.FileType
import com.example.filemanager.viewmodel.FileListViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class,
    ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    viewModel: FileListViewModel = viewModel(),
    onNavigateToTextViewer: (FileItem) -> Unit,
    onNavigateToImageViewer: (FileItem) -> Unit,
    onShareViaBluetoothClicked: (FileItem) -> Unit
) {
    // Utiliza collectAsState() de forma segura sin acceder directamente a .value en la UI
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val isExternalStorage by viewModel.isExternalStorage.collectAsState()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsState()
    val context = LocalContext.current

    Log.d("FileListScreen", "Renderizando con ${files.size} archivos, loading: $isLoading, error: $errorMessage")

    val permissionState = rememberPermissionState(
        permission = android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // Lanzador mejorado para el selector de directorios
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d("FileListScreen", "Directorio seleccionado: $uri")

            // Tomar permisos persistentes
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                Toast.makeText(context, "Permisos obtenidos para: $uri", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("FileListScreen", "Error al tomar permisos persistentes", e)
            }

            viewModel.loadExternalStorageFiles(uri)
        } else {
            Log.d("FileListScreen", "Selección de directorio cancelada")
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d("FileListScreen", "Archivo seleccionado: $uri")
            // Aquí puedes hacer algo con el archivo seleccionado
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Toast.makeText(context, "Archivo seleccionado: $uri", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isExternalStorage -> "Almacenamiento Externo"
                            currentPath == "MediaStore Query" -> "Todos los archivos (MediaStore)"
                            currentPath != null -> "Interno: ${currentPath?.substringAfterLast('/', "Raíz")}"
                            else -> "Almacenamiento Interno"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d("FileListScreen", "Botón Atrás presionado")
                        viewModel.navigateUp()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Log.d("FileListScreen", "Botón Inicio presionado")
                        viewModel.loadInternalStorageFiles()
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "Inicio")
                    }

                    // Botón para mostrar/ocultar archivos ocultos
                    IconButton(onClick = {
                        viewModel.toggleHiddenFiles()
                    }) {
                        Icon(
                            imageVector = if (showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (showHiddenFiles) "Ocultar archivos ocultos" else "Mostrar archivos ocultos"
                        )
                    }

                    // Botón para usar MediaStore
                    IconButton(onClick = {
                        Log.d("FileListScreen", "Botón MediaStore presionado")
                        viewModel.loadAllFilesWithMediaStore()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Cargar todos los archivos (MediaStore)"
                        )
                    }

                    IconButton(
                        onClick = {
                            Log.d("FileListScreen", "Botón almacenamiento externo presionado")
                            if (permissionState.status.isGranted) {
                                directoryPickerLauncher.launch(null)
                            } else {
                                Log.d("FileListScreen", "Solicitando permiso de almacenamiento")
                                permissionState.launchPermissionRequest()
                            }
                        }
                    ) {
                        Icon(Icons.Default.SdCard, contentDescription = "Almacenamiento Externo")
                    }

                    // Botón para mostrar ruta actual
                    IconButton(onClick = {
                        // No usar currentPath.value, usar currentPath directamente
                        val path = currentPath ?: "Ninguna ruta seleccionada"
                        Toast.makeText(context, "Ruta actual: $path", Toast.LENGTH_LONG).show()
                        Log.d("FileListScreen", "Ruta actual: $path")

                        // Forzar depuración y recarga
                        viewModel.debugAvailableStorageLocations()
                        viewModel.loadSafeLocation()
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Mostrar ruta actual")
                    }

                    // Botón para probar cada directorio
                    IconButton(onClick = {
                        viewModel.testAllDirectories()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Probar todos los directorios")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "Error desconocido",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else if (files.isEmpty()) {
                // Mensaje mejorado cuando no hay archivos
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No se encontraron archivos en esta ubicación",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            Log.d("FileListScreen", "Botón cambiar ubicación presionado")
                            if (isExternalStorage) {
                                directoryPickerLauncher.launch(null)
                            } else {
                                viewModel.loadSafeLocation()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cambiar ubicación")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón para abrir el selector de documentos
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Seleccionar archivo del dispositivo")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón para abrir el almacenamiento raíz
                    Button(
                        onClick = {
                            try {
                                val rootUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:")
                                directoryPickerLauncher.launch(rootUri)
                            } catch (e: Exception) {
                                Log.e("FileListScreen", "Error al abrir selector de directorios", e)
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                directoryPickerLauncher.launch(null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir almacenamiento raíz")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón para solicitar permiso especial en Android 11+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    val uri = Uri.fromParts("package", context.packageName, null)
                                    intent.data = uri
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("FileListScreen", "Error al solicitar permiso completo", e)
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Solicitar permiso de acceso completo")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(files) { fileItem ->
                        FileItemRow(
                            fileItem = fileItem,
                            onItemClick = {
                                Log.d("FileListScreen", "Archivo seleccionado: ${fileItem.name}, tipo: ${fileItem.type}")
                                when {
                                    fileItem.isDirectory -> viewModel.openFile(fileItem)
                                    fileItem.type == FileType.TEXT -> onNavigateToTextViewer(fileItem)
                                    fileItem.type == FileType.IMAGE -> onNavigateToImageViewer(fileItem)
                                    else -> {
                                        // Mostrar un mensaje para tipos de archivo no soportados
                                        Toast.makeText(
                                            context,
                                            "No se puede abrir este tipo de archivo: ${fileItem.fileExtension ?: "desconocido"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onShareViaBluetoothClicked = {
                                onShareViaBluetoothClicked(fileItem)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    fileItem: FileItem,
    onItemClick: () -> Unit,
    onShareViaBluetoothClicked: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = { showContextMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono según tipo de archivo
        Icon(
            imageVector = when {
                fileItem.isDirectory -> Icons.Default.Folder
                fileItem.type == FileType.TEXT -> Icons.Default.TextSnippet
                fileItem.type == FileType.IMAGE -> Icons.Default.Image
                fileItem.type == FileType.AUDIO -> Icons.Default.MusicNote
                fileItem.type == FileType.VIDEO -> Icons.Default.Videocam
                fileItem.type == FileType.PDF -> Icons.Default.PictureAsPdf
                fileItem.type == FileType.ARCHIVE -> Icons.Default.FolderZip
                else -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            tint = when {
                fileItem.isDirectory -> MaterialTheme.colorScheme.primary
                fileItem.type == FileType.TEXT -> MaterialTheme.colorScheme.secondary
                fileItem.type == FileType.IMAGE -> MaterialTheme.colorScheme.tertiary
                fileItem.type == FileType.AUDIO -> Color(0xFF4CAF50) // Verde
                fileItem.type == FileType.VIDEO -> Color(0xFFF44336) // Rojo
                fileItem.type == FileType.PDF -> Color(0xFFFF9800) // Naranja
                fileItem.type == FileType.ARCHIVE -> Color(0xFF9C27B0) // Púrpura
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mostrar extensión para archivos que no sean carpetas
                Text(
                    text = if (!fileItem.isDirectory) {
                        fileItem.fileExtension?.uppercase() ?: "DESCONOCIDO"
                    } else {
                        fileItem.formattedDate
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Text(
                    text = fileItem.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Añadir botón de compartir para archivos que no sean directorios
        if (!fileItem.isDirectory) {
            IconButton(onClick = { showContextMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Más opciones",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Compartir vía Bluetooth") },
                    onClick = {
                        showContextMenu = false
                        onShareViaBluetoothClicked()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }

    Divider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    )
}