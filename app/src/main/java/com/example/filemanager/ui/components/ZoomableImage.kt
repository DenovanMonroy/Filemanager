package com.example.filemanager.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ZoomableImage(
    imageUri: Any,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    initialRotation: Float = 0f,
    onRotationChange: (Float) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(initialRotation) }

    Log.d("ZoomableImage", "Cargando imagen: $imageUri")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUri)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { Log.d("ZoomableImage", "Imagen cargada correctamente") },
            onError = { Log.e("ZoomableImage", "Error al cargar imagen: $imageUri") },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    rotationZ = rotation,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotationChange ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)

                        // Aplicar la rotación y notificar al componente padre
                        val newRotation = rotation + rotationChange
                        rotation = newRotation
                        onRotationChange(newRotation)

                        // Ajustar el desplazamiento según la escala
                        val maxX = (size.width * (scale - 1) / 2f)
                        val maxY = (size.height * (scale - 1) / 2f)

                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
        )
    }
}