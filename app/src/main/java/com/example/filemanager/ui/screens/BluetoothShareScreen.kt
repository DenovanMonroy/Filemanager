package com.example.filemanager.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.filemanager.bluetooth.BluetoothService
import com.example.filemanager.model.FileItem
import com.example.filemanager.viewmodel.BluetoothViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BluetoothShareScreen(
    fileItem: FileItem,
    viewModel: BluetoothViewModel = viewModel(),
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current

    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val connectedDeviceName by viewModel.deviceName.collectAsState()

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionsState = rememberMultiplePermissionsState(bluetoothPermissions)

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (viewModel.isBluetoothEnabled()) {
            viewModel.getPairedDevices()
        } else {
            Toast.makeText(context, "Bluetooth debe estar activado para compartir archivos", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.isBluetoothSupported()) {
            Toast.makeText(context, "El dispositivo no soporta Bluetooth", Toast.LENGTH_LONG).show()
            onBackPressed()
            return@LaunchedEffect
        }

        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        } else if (!viewModel.isBluetoothEnabled()) {
            val enableBtIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            viewModel.getPairedDevices()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compartir vía Bluetooth") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.disconnect()
                        onBackPressed()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (connectionState == BluetoothService.STATE_NONE ||
                        connectionState == BluetoothService.STATE_CONNECTION_FAILED) {
                        IconButton(onClick = { viewModel.startServer() }) {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = "Hacer visible")
                        }

                        IconButton(onClick = { viewModel.startDiscovery() }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar dispositivos")
                        }
                    } else {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.Close, contentDescription = "Desconectar")
                        }
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
            when (connectionState) {
                BluetoothService.STATE_NONE,
                BluetoothService.STATE_CONNECTION_FAILED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Archivo a compartir: ${fileItem.name}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (connectionState == BluetoothService.STATE_CONNECTION_FAILED) {
                            Text(
                                text = "Falló la conexión. Intenta de nuevo.",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        if (pairedDevices.isNotEmpty()) {
                            Text(
                                text = "Dispositivos emparejados",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(pairedDevices) { device ->
                                    DeviceItem(
                                        device = device,
                                        onClick = { viewModel.connectToDevice(device) }
                                    )
                                }
                            }
                        }

                        if (discoveredDevices.isNotEmpty()) {
                            Text(
                                text = "Dispositivos encontrados",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(discoveredDevices) { device ->
                                    DeviceItem(
                                        device = device,
                                        onClick = { viewModel.connectToDevice(device) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { viewModel.getPairedDevices() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Actualizar")
                            }

                            Spacer(Modifier.width(16.dp))

                            Button(
                                onClick = { viewModel.startServer() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Esperar conexión")
                            }
                        }
                    }
                }

                BluetoothService.STATE_LISTEN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(100.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Esperando conexión...",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = { viewModel.disconnect() }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cancelar")
                        }
                    }
                }

                BluetoothService.STATE_CONNECTING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(100.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Conectando a $connectedDeviceName...",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = { viewModel.disconnect() }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cancelar")
                        }
                    }
                }

                BluetoothService.STATE_CONNECTED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Conectado a $connectedDeviceName",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Archivo: ${fileItem.name}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(Modifier.height(24.dp))

                        if (isSending) {
                            Text(
                                text = "Enviando archivo... ${(transferProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { transferProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                        } else {
                            Button(
                                onClick = { viewModel.sendFile(File(fileItem.path)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Enviar archivo")
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.disconnect() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Desconectar")
                        }
                    }
                }

                BluetoothService.STATE_MESSAGE_RECEIVED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(100.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "¡Archivo recibido correctamente!",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = { viewModel.disconnect() }
                        ) {
                            Text("Volver")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        device.alias ?: device.address
    } else {
        device.name ?: device.address
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}