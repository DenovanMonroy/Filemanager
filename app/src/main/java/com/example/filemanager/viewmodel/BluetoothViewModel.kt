package com.example.filemanager.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanager.bluetooth.BluetoothService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothService = BluetoothService(application.applicationContext)

    val pairedDevices = bluetoothService.pairedDevices
    val discoveredDevices = bluetoothService.discoveredDevices
    val connectionState = bluetoothService.connectionState
    val transferProgress = bluetoothService.transferProgress

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName

    init {
        // Se comenta esta línea para evitar el error reportado
        // enableAdvancedLogging()

        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == BluetoothService.STATE_NONE) {
                    _deviceName.value = null
                }
            }
        }
    }

    // Función modificada para manejar el caso cuando el campo no existe
    private fun enableAdvancedLogging() {
        try {
            Log.d("BluetoothViewModel", "Configurando depuración avanzada de Bluetooth")

            // En lugar de intentar modificar campos internos, usamos una estrategia alternativa
            // Configuramos nuestro propio nivel de logging detallado
            val isDebuggable = 0 != getApplication<Application>().applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE

            if (isDebuggable) {
                Log.d("BluetoothViewModel", "Modo depuración habilitado, se mostrarán todos los logs")
            }

        } catch (e: Exception) {
            // Solo registramos el error, pero no afecta a la funcionalidad
            Log.e("BluetoothViewModel", "No se pudo configurar la depuración avanzada: ${e.message}")
        }
    }

    fun isBluetoothSupported() = bluetoothService.isBluetoothSupported()

    fun isBluetoothEnabled() = bluetoothService.isBluetoothEnabled()

    fun getPairedDevices() {
        bluetoothService.getPairedDevices()
    }

    fun startDiscovery() {
        bluetoothService.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothService.stopDiscovery()
    }

    fun startServer() {
        bluetoothService.startServer()
    }

    fun connectToDevice(device: BluetoothDevice) {
        _deviceName.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.alias ?: device.address
        } else {
            device.name ?: device.address
        }
        bluetoothService.connectToDevice(device)
    }

    fun sendFile(file: File) {
        viewModelScope.launch {
            _isSending.value = true
            val success = bluetoothService.sendFile(file)
            _isSending.value = false

            Log.d("BluetoothViewModel", "Archivo enviado: $success")
        }
    }

    fun disconnect() {
        bluetoothService.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothService.onDestroy()
    }
}