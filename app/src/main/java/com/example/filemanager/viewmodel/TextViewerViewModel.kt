package com.example.filemanager.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanager.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TextViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application.applicationContext)

    private val _textContent = MutableStateFlow<String>("")
    val textContent: StateFlow<String> = _textContent

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _fileName = MutableStateFlow<String>("")
    val fileName: StateFlow<String> = _fileName

    private val _isMarkdown = MutableStateFlow(false)
    val isMarkdown: StateFlow<Boolean> = _isMarkdown

    fun loadTextFile(path: String, name: String) {
        _fileName.value = name
        _isMarkdown.value = name.endsWith(".md")
        _isLoading.value = true

        Log.d("TextViewerViewModel", "Cargando archivo de texto desde path: $path")

        viewModelScope.launch {
            try {
                _textContent.value = fileRepository.readTextFile(path)
                Log.d("TextViewerViewModel", "Archivo de texto cargado correctamente, longitud: ${_textContent.value.length}")
            } catch (e: Exception) {
                Log.e("TextViewerViewModel", "Error al cargar archivo de texto", e)
                _textContent.value = "Error al cargar el archivo: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTextFile(uri: Uri, name: String) {
        _fileName.value = name
        _isMarkdown.value = name.endsWith(".md")
        _isLoading.value = true

        Log.d("TextViewerViewModel", "Cargando archivo de texto desde URI: $uri")

        viewModelScope.launch {
            try {
                _textContent.value = fileRepository.readTextFile(uri)
                Log.d("TextViewerViewModel", "Archivo de texto cargado correctamente, longitud: ${_textContent.value.length}")
            } catch (e: Exception) {
                Log.e("TextViewerViewModel", "Error al cargar archivo de texto desde URI", e)
                _textContent.value = "Error al cargar el archivo: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}