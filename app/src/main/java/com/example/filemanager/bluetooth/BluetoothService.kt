package com.example.filemanager.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.UUID

class BluetoothService(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothService"
        private const val APP_NAME = "FileManagerBT"

        // Probamos varios UUIDs conocidos que funcionan en diferentes dispositivos
        private val SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP
        private val MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66") // UUID Aleatorio
        private val ANDROID_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66") // UUID común en Android

        // Lista de UUIDs para intentar
        private val UUID_LIST = arrayOf(SERIAL_UUID, MY_UUID, ANDROID_UUID)

        const val STATE_NONE = 0
        const val STATE_LISTEN = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3
        const val STATE_CONNECTION_FAILED = 4
        const val STATE_MESSAGE_RECEIVED = 5
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _connectionState = MutableStateFlow(STATE_NONE)
    val connectionState: StateFlow<Int> = _connectionState

    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var connectedSocket: BluetoothSocket? = null

    private var sendReceive: SendReceive? = null
    private var isConnecting = false

    // Flag para indicar si se está usando el método inseguro
    private var usingInsecureConnection = false

    // BroadcastReceiver para dispositivos descubiertos y eventos Bluetooth
    private val receiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val currentList = _discoveredDevices.value.toMutableList()
                        if (!currentList.contains(it)) {
                            currentList.add(it)
                            _discoveredDevices.value = currentList
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Descubrimiento de dispositivos finalizado")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        Log.d(TAG, "Dispositivo emparejado: ${device?.name}")
                        getPairedDevices()

                        // Si estamos en estado de conexión, intentar conectar
                        if (_connectionState.value == STATE_CONNECTING && device != null && !isConnecting) {
                            // Pequeña pausa para asegurar que el emparejamiento se completó
                            mainHandler.postDelayed({
                                ClientClass(device).start()
                            }, 1000)
                        }
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        Log.d(TAG, "Bluetooth apagado")
                        disconnect()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "ACL Conectado")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(TAG, "ACL Desconectado")
                    if (_connectionState.value == STATE_CONNECTED) {
                        disconnect()
                    }
                }
            }
        }
    }

    init {
        // Registrar el BroadcastReceiver para recibir notificaciones de dispositivos encontrados y cambios de emparejamiento
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
    }

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun getPairedDevices() {
        if (bluetoothAdapter == null) {
            _pairedDevices.value = emptyList()
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val devices = bluetoothAdapter.bondedDevices.toList()
            _pairedDevices.value = devices
        } else {
            _pairedDevices.value = emptyList()
        }
    }

    fun startDiscovery() {
        if (bluetoothAdapter == null) return

        _discoveredDevices.value = emptyList()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter.startDiscovery()
        }
    }

    fun stopDiscovery() {
        if (bluetoothAdapter == null) return

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        }
    }

    fun startServer() {
        Log.d(TAG, "Iniciando servidor...")

        // Asegurarse de que cualquier servidor anterior está cerrado
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar socket de servidor anterior: ${e.message}")
        }

        _connectionState.value = STATE_LISTEN

        // Verificar que Bluetooth está habilitado
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth no está habilitado")
            _connectionState.value = STATE_CONNECTION_FAILED
            return
        }

        // Intentar hacer el dispositivo visible
        makeDeviceDiscoverable()

        // Iniciar el servidor
        ServerClass().start()
    }

    private fun makeDeviceDiscoverable() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Falta permiso BLUETOOTH_CONNECT")
                return
            }

            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            discoverableIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(discoverableIntent)
            Log.d(TAG, "Solicitud para hacer el dispositivo visible enviada")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo hacer el dispositivo visible: ${e.message}")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Conectando a dispositivo: ${device.address}")

        // Cancelar cualquier descubrimiento en progreso
        stopDiscovery()

        // Reset del estado de conexión
        isConnecting = false
        usingInsecureConnection = false

        // Verificar si el dispositivo está emparejado
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val isPaired = bluetoothAdapter?.bondedDevices?.contains(device) == true

            if (!isPaired) {
                Log.d(TAG, "El dispositivo no está emparejado, iniciando emparejamiento...")
                try {
                    // Iniciar el emparejamiento (esto mostrará un diálogo al usuario)
                    device.createBond()
                    Toast.makeText(context, "Por favor, acepta la solicitud de emparejamiento", Toast.LENGTH_LONG).show()

                    // Necesitamos esperar a que el emparejamiento se complete
                    _connectionState.value = STATE_CONNECTING

                    // El emparejamiento continuará en segundo plano
                    // y el receptor de broadcast detectará cuando se complete

                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar emparejamiento: ${e.message}")
                    _connectionState.value = STATE_CONNECTION_FAILED
                }
            } else {
                Log.d(TAG, "El dispositivo ya está emparejado, conectando...")
                _connectionState.value = STATE_CONNECTING
                ClientClass(device).start()
            }
        } else {
            Log.e(TAG, "Falta permiso BLUETOOTH_CONNECT para verificar emparejamiento")
            _connectionState.value = STATE_CONNECTING
            ClientClass(device).start()
        }
    }

    suspend fun sendFile(file: File): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Enviando archivo: ${file.name}")

        if (sendReceive == null) {
            Log.e(TAG, "SendReceive es null, no se puede enviar el archivo")
            return@withContext false
        }

        try {
            // Primero enviamos el nombre del archivo
            sendReceive?.write(file.name.toByteArray())

            // Esperar un poco
            Thread.sleep(200)

            // Luego enviamos el tamaño del archivo
            val fileSize = file.length().toString().toByteArray()
            sendReceive?.write(fileSize)

            // Esperar un poco antes de enviar el archivo
            Thread.sleep(500)

            // Ahora enviamos el archivo en pequeños trozos
            val buffer = ByteArray(4096) // Reducir tamaño de buffer para mayor estabilidad
            val fileInputStream = FileInputStream(file)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                sendReceive?.write(buffer.copyOf(bytesRead))

                // Pequeña pausa para evitar sobrecarga
                Thread.sleep(10)

                totalBytesRead += bytesRead
                val progress = totalBytesRead.toFloat() / file.length()
                _transferProgress.value = progress
            }

            fileInputStream.close()
            _transferProgress.value = 1f

            Log.d(TAG, "Archivo enviado correctamente")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar archivo", e)
            _transferProgress.value = 0f
            return@withContext false
        }
    }

    fun disconnect() {
        try {
            serverSocket?.close()
            clientSocket?.close()
            connectedSocket?.close()
            sendReceive?.cancel()

            serverSocket = null
            clientSocket = null
            connectedSocket = null
            sendReceive = null
            isConnecting = false
            usingInsecureConnection = false

            _connectionState.value = STATE_NONE
            _transferProgress.value = 0f

        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar", e)
        }
    }

    fun onDestroy() {
        disconnect()
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // El receptor puede no estar registrado
            Log.e(TAG, "Error al desregistrar el receiver", e)
        }
    }

    // Método para crear un socket de cliente inseguro usando reflexión
    @Suppress("UNCHECKED_CAST")
    private fun createInsecureRfcommSocket(device: BluetoothDevice, channel: Int = 1): BluetoothSocket? {
        try {
            Log.d(TAG, "Intentando crear socket inseguro para ${device.address}")

            // Método para crear un socket inseguro
            val m: Method = device.javaClass.getMethod(
                "createInsecureRfcommSocketToServiceRecord",
                UUID::class.java
            )

            return m.invoke(device, SERIAL_UUID) as? BluetoothSocket
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear socket inseguro: ${e.message}")

            // Intento alternativo con otro método
            try {
                val m: Method = device.javaClass.getMethod(
                    "createRfcommSocket",
                    Int::class.java
                )
                return m.invoke(device, channel) as? BluetoothSocket
            } catch (e2: Exception) {
                Log.e(TAG, "Error al crear socket alternativo: ${e2.message}")
            }
        }
        return null
    }

    private inner class ServerClass : Thread() {
        override fun run() {
            try {
                Log.d(TAG, "ServerClass: Iniciando servidor Bluetooth...")

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Falta permiso BLUETOOTH_CONNECT")
                    _connectionState.value = STATE_CONNECTION_FAILED
                    return
                }

                // Intentar con diferentes UUIDs
                var connected = false

                for (uuid in UUID_LIST) {
                    if (connected) break

                    try {
                        // Primero intentamos con el método seguro
                        serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, uuid)
                        Log.d(TAG, "Servidor iniciado con UUID: $uuid")

                        // Ajustar el timeout del servidor
                        val timeout = 120000 // 2 minutos
                        val startTime = System.currentTimeMillis()

                        while (System.currentTimeMillis() - startTime < timeout) {
                            try {
                                Log.d(TAG, "Esperando conexiones entrantes...")
                                connectedSocket = serverSocket?.accept(30000) // 30 segundos de timeout

                                if (connectedSocket != null) {
                                    Log.d(TAG, "Conexión aceptada como servidor")
                                    _connectionState.value = STATE_CONNECTED

                                    sendReceive = SendReceive(connectedSocket!!)
                                    sendReceive?.start()

                                    connected = true
                                    break
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Error al aceptar conexión: ${e.message}")

                                // Si el error se debe a que el socket se cerró intencionalmente, salimos
                                if (e.message?.contains("closed") == true) {
                                    break
                                }

                                // Pequeña pausa para evitar un bucle demasiado rápido
                                try {
                                    sleep(1000)
                                } catch (ie: InterruptedException) {
                                    break
                                }
                            }
                        }

                        // Si salimos del bucle con conexión, terminamos
                        if (connected) break

                        // Si no se conectó, cerramos este socket de servidor
                        serverSocket?.close()
                        serverSocket = null

                    } catch (e: Exception) {
                        Log.e(TAG, "Error al crear servidor con UUID $uuid: ${e.message}")
                        serverSocket?.close()
                        serverSocket = null
                    }

                    // Si no funcionó el método seguro, intentamos con el inseguro
                    if (!connected) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, uuid)
                                Log.d(TAG, "Servidor inseguro iniciado con UUID: $uuid")

                                connectedSocket = serverSocket?.accept(30000)

                                if (connectedSocket != null) {
                                    Log.d(TAG, "Conexión insegura aceptada como servidor")
                                    _connectionState.value = STATE_CONNECTED

                                    sendReceive = SendReceive(connectedSocket!!)
                                    sendReceive?.start()

                                    connected = true
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al crear servidor inseguro con UUID $uuid: ${e.message}")
                            serverSocket?.close()
                            serverSocket = null
                        }
                    }
                }

                // Si no se conectó después de intentar con todos los UUIDs
                if (!connected) {
                    Log.d(TAG, "No se pudo establecer ninguna conexión como servidor")
                    _connectionState.value = STATE_CONNECTION_FAILED
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error general en ServerClass: ${e.message}")
                _connectionState.value = STATE_CONNECTION_FAILED
            }
        }
    }

    private inner class ClientClass(private val device: BluetoothDevice) : Thread() {
        override fun run() {
            try {
                if (isConnecting) {
                    Log.d(TAG, "Ya hay una conexión en progreso, cancelando esta intento")
                    return
                }

                isConnecting = true
                Log.d(TAG, "ClientClass iniciando conexión a dispositivo: ${device.address}")

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Falta permiso BLUETOOTH_CONNECT")
                    _connectionState.value = STATE_CONNECTION_FAILED
                    isConnecting = false
                    return
                }

                // Cancelar cualquier descubrimiento en progreso
                if (bluetoothAdapter?.isDiscovering == true) {
                    Log.d(TAG, "Cancelando descubrimiento para optimizar conexión")
                    bluetoothAdapter.cancelDiscovery()
                }

                // Intentar con diferentes UUIDs
                var connected = false

                // Si ya estamos usando el modo inseguro, empezamos con él
                val uuidOrder = if (usingInsecureConnection) {
                    Log.d(TAG, "Intentando primero con conexión insegura")
                    -1 // Código especial para conexión insegura
                } else {
                    0 // Empezamos con el primer UUID en modo normal
                }

                // Primer intento: probar UUIDs estándar
                if (uuidOrder >= 0) {
                    for (i in uuidOrder until UUID_LIST.size) {
                        val uuid = UUID_LIST[i]
                        if (connected) break

                        try {
                            Log.d(TAG, "Intentando conectar con UUID: $uuid")
                            clientSocket = device.createRfcommSocketToServiceRecord(uuid)

                            try {
                                Log.d(TAG, "Conectando socket...")
                                clientSocket?.connect()

                                if (clientSocket?.isConnected == true) {
                                    Log.d(TAG, "Conectado como cliente con UUID: $uuid")
                                    connectedSocket = clientSocket
                                    _connectionState.value = STATE_CONNECTED

                                    sendReceive = SendReceive(connectedSocket!!)
                                    sendReceive?.start()

                                    connected = true
                                    break
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Error al conectar con UUID $uuid: ${e.message}")
                                clientSocket?.close()
                                clientSocket = null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al crear socket con UUID $uuid: ${e.message}")
                        }
                    }
                }

                // Segundo intento: Método inseguro
                if (!connected) {
                    Log.d(TAG, "Intentando conexión insegura como último recurso")
                    usingInsecureConnection = true

                    // Primer método: listenUsingInsecureRfcommWithServiceRecord
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        for (uuid in UUID_LIST) {
                            if (connected) break

                            try {
                                clientSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)

                                try {
                                    Log.d(TAG, "Conectando socket inseguro...")
                                    clientSocket?.connect()

                                    if (clientSocket?.isConnected == true) {
                                        Log.d(TAG, "Conectado como cliente con socket inseguro y UUID: $uuid")
                                        connectedSocket = clientSocket
                                        _connectionState.value = STATE_CONNECTED

                                        sendReceive = SendReceive(connectedSocket!!)
                                        sendReceive?.start()

                                        connected = true
                                        break
                                    }
                                } catch (e: IOException) {
                                    Log.e(TAG, "Error al conectar con socket inseguro y UUID $uuid: ${e.message}")
                                    clientSocket?.close()
                                    clientSocket = null
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al crear socket inseguro con UUID $uuid: ${e.message}")
                            }
                        }
                    }

                    // Tercer intento: Método de reflexión
                    if (!connected) {
                        try {
                            Log.d(TAG, "Intentando método de conexión por reflexión...")

                            // Intentar con canales 1-30
                            for (channel in 1..5) {
                                if (connected) break

                                try {
                                    clientSocket = createInsecureRfcommSocket(device, channel)

                                    if (clientSocket != null) {
                                        try {
                                            Log.d(TAG, "Conectando socket de reflexión en canal $channel...")
                                            clientSocket?.connect()

                                            if (clientSocket?.isConnected == true) {
                                                Log.d(TAG, "Conectado como cliente con socket de reflexión en canal $channel")
                                                connectedSocket = clientSocket
                                                _connectionState.value = STATE_CONNECTED

                                                sendReceive = SendReceive(connectedSocket!!)
                                                sendReceive?.start()

                                                connected = true
                                                break
                                            }
                                        } catch (e: IOException) {
                                            Log.e(TAG, "Error al conectar con socket de reflexión en canal $channel: ${e.message}")
                                            clientSocket?.close()
                                            clientSocket = null
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error al crear socket de reflexión en canal $channel: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error general en método de reflexión: ${e.message}")
                        }
                    }
                }

                // Si no se pudo conectar después de todos los intentos
                if (!connected) {
                    Log.e(TAG, "No se pudo establecer ninguna conexión después de todos los intentos")
                    _connectionState.value = STATE_CONNECTION_FAILED
                    isConnecting = false

                    // Mostrar mensaje al usuario
                    mainHandler.post {
                        Toast.makeText(
                            context,
                            "No se pudo conectar con el dispositivo. Intenta nuevamente.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    isConnecting = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error general al conectar como cliente: ${e.message}")
                _connectionState.value = STATE_CONNECTION_FAILED
                isConnecting = false

                try {
                    clientSocket?.close()
                    clientSocket = null
                } catch (e2: IOException) {
                    Log.e(TAG, "Error al cerrar socket cliente: ${e2.message}")
                }
            }
        }
    }

    private inner class SendReceive(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream

        private var fileName: String? = null
        private var fileSize: Long = 0
        private var bytesReceived: Long = 0
        private var fileOutputStream: FileOutputStream? = null

        // Modificar esta línea para usar la carpeta Download
        private val saveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        init {
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }
        }

        override fun run() {
            val buffer = ByteArray(4096)
            var bytes: Int

            while (true) {
                try {
                    // Leer los datos
                    bytes = inputStream.read(buffer)

                    if (bytes > 0) {
                        val receivedBytes = buffer.copyOf(bytes)

                        if (fileName == null) {
                            // Primera lectura, es el nombre del archivo
                            fileName = String(receivedBytes)
                            Log.d(TAG, "Nombre de archivo recibido: $fileName")
                            continue
                        } else if (fileSize == 0L) {
                            // Segunda lectura, es el tamaño del archivo
                            fileSize = String(receivedBytes).toLong()
                            Log.d(TAG, "Tamaño de archivo recibido: $fileSize bytes")

                            // Crear el archivo para escribir
                            val file = File(saveDir, fileName!!)

                            try {
                                fileOutputStream = FileOutputStream(file)
                                Log.d(TAG, "Archivo creado en: ${file.absolutePath}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al crear archivo en Downloads: ${e.message}")

                                // Plan B: Si falla, intentar guardar en el almacenamiento privado
                                val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                if (fallbackDir != null) {
                                    val fallbackFile = File(fallbackDir, fileName!!)
                                    fileOutputStream = FileOutputStream(fallbackFile)
                                    Log.d(TAG, "Archivo creado en ubicación alternativa: ${fallbackFile.absolutePath}")

                                    // Notificar al usuario sobre la ubicación alternativa
                                    mainHandler.post {
                                        Toast.makeText(
                                            context,
                                            "El archivo se guardó en la carpeta privada de la app (no se pudo acceder a Downloads)",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                            continue
                        }

                        // Escribir los datos en el archivo
                        fileOutputStream?.write(receivedBytes)

                        bytesReceived += bytes
                        val progress = bytesReceived.toFloat() / fileSize
                        _transferProgress.value = progress

                        Log.d(TAG, "Recibidos $bytesReceived de $fileSize bytes (${(progress * 100).toInt()}%)")

                        if (bytesReceived >= fileSize) {
                            Log.d(TAG, "Archivo recibido completamente: $fileName")
                            fileOutputStream?.close()

                            // Avisar al usuario donde se guardó el archivo
                            mainHandler.post {
                                Toast.makeText(
                                    context,
                                    "Archivo guardado en Descargas: $fileName",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            // Notificar al sistema de archivos para que aparezca en la galería si es una imagen o video
                            try {
                                val file = File(saveDir, fileName!!)
                                if (file.exists()) {
                                    val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        // Para Android 10+
                                        MediaScannerConnection.scanFile(
                                            context,
                                            arrayOf(file.absolutePath),
                                            null,
                                            null
                                        )
                                    } else {
                                        // Para versiones anteriores
                                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                                            data = Uri.fromFile(file)
                                        })
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al notificar archivo a MediaScanner: ${e.message}")
                            }

                            // Reiniciar para el próximo archivo
                            fileName = null
                            fileSize = 0
                            bytesReceived = 0
                            fileOutputStream = null

                            _connectionState.value = STATE_MESSAGE_RECEIVED
                            _transferProgress.value = 1f
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error en la conexión de lectura/escritura: ${e.message}")
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                // Verificar que el array de bytes no está vacío
                if (bytes.isEmpty()) {
                    Log.w(TAG, "Intento de escribir un array de bytes vacío")
                    return
                }

                // Verificar que el socket está conectado
                if (!socket.isConnected) {
                    Log.e(TAG, "Socket no conectado, no se pueden enviar datos")
                    throw IOException("Socket desconectado")
                }

                // Escribir los datos
                outputStream.write(bytes)
                outputStream.flush()

                // Log para depuración
                Log.d(TAG, "Datos enviados: ${bytes.size} bytes")

            } catch (e: IOException) {
                Log.e(TAG, "Error al escribir en el socket: ${e.message}")
                // Propagar la excepción para que el método sendFile pueda manejarla
                throw e
            }
        }

        fun cancel() {
            try {
                fileOutputStream?.close()
                inputStream.close()
                outputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error al cerrar streams", e)
            }
        }
    }
}