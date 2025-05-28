# File Manager con Bluetooth

## Descripción
File Manager es una aplicación Android para gestionar archivos locales y compartirlos fácilmente a través de Bluetooth. Permite navegar por el sistema de archivos, ver detalles de archivos, y transferir documentos, imágenes y otros tipos de archivos entre dispositivos Android cercanos sin necesidad de conexión a internet.

## Características principales

- **Explorador de archivos**: Navega por tu dispositivo con una interfaz limpia e intuitiva
- **Transferencia Bluetooth**: Comparte archivos directamente entre dispositivos Android
- **Conexión robusta**: Sistema mejorado de emparejamiento y conexión compatible con diferentes versiones de Android
- **Guardado fácil**: Los archivos recibidos se guardan automáticamente en la carpeta de Descargas
- **Archivos de muestra**: Incluye archivos de prueba para facilitar las demostraciones
- **Gestión de permisos**: Solicitud inteligente de permisos según la versión de Android

## Requisitos

- Android 6.0 (Marshmallow) o superior
- Bluetooth habilitado en el dispositivo
- Permisos de almacenamiento y ubicación

## Estructura del proyecto

```
com.example.filemanager/
├── MainActivity.kt               # Actividad principal, gestión de permisos
├── bluetooth/
│   └── BluetoothService.kt       # Servicio de gestión de conexiones Bluetooth
├── data/
│   ├── FileRepository.kt         # Repositorio para acceso a archivos
│   └── models/
│       ├── FileItem.kt           # Modelo de datos para archivos
│       └── DeviceItem.kt         # Modelo para dispositivos Bluetooth
├── ui/
│   ├── file/
│   │   ├── FileListFragment.kt   # Lista de archivos
│   │   ├── FileDetailFragment.kt # Detalles de un archivo
│   │   └── FileAdapter.kt        # Adaptador para mostrar archivos en RecyclerView
│   ├── bluetooth/
│   │   ├── BluetoothShareScreen.kt  # Pantalla de compartir por Bluetooth
│   │   └── DeviceAdapter.kt      # Adaptador para lista de dispositivos
│   └── settings/
│       └── SettingsFragment.kt   # Configuración de la aplicación
├── utils/
│   ├── FileUtils.kt              # Utilidades para manipulación de archivos
│   └── PermissionUtils.kt        # Utilidades para gestión de permisos
└── viewmodel/
    ├── BluetoothViewModel.kt     # ViewModel para funcionalidad Bluetooth
    ├── FileViewModel.kt          # ViewModel para gestión de archivos
    └── SharedViewModel.kt        # ViewModel compartido entre fragmentos
```

## Capturas

## Permisos necesarios

La aplicación requiere los siguientes permisos:
- Bluetooth (conexión y búsqueda de dispositivos)
- Ubicación (necesaria para escaneo Bluetooth en Android 6.0+)
- Almacenamiento (lectura y escritura de archivos)

Para Android 11 o superior, la app solicita el permiso `MANAGE_EXTERNAL_STORAGE` para guardar archivos en la carpeta de Descargas.

## Guía de uso

### Transferir archivos

1. **Emisor (quien envía el archivo)**:
   - Selecciona un archivo que quieras compartir
   - Toca el botón de compartir y selecciona "Bluetooth"
   - Toca "Buscar dispositivos" para escanear dispositivos cercanos
   - Selecciona el dispositivo destino de la lista

2. **Receptor (quien recibe el archivo)**:
   - Abre la aplicación
   - Toca "Esperar conexión"
   - Asegúrate de que el Bluetooth está encendido y visible
   - Acepta la solicitud de conexión cuando aparezca

3. **Durante la transferencia**:
   - Se muestra una barra de progreso con el avance
   - No cierres la aplicación durante la transferencia
   - Los archivos recibidos se guardan automáticamente en la carpeta de Descargas

### Solución de problemas comunes

- **Error de conexión**: Asegúrate de que ambos dispositivos estén emparejados previamente
- **Dispositivo no visible**: Activa manualmente la visibilidad Bluetooth en la configuración del sistema
- **Transferencia lenta**: Mantén los dispositivos cercanos (menos de 1 metro)
- **Fallo de guardado**: Verifica que la app tenga permisos de almacenamiento en la configuración del dispositivo

## Componentes principales

### BluetoothService
Componente central para la gestión de conexiones Bluetooth. Maneja:
- Descubrimiento de dispositivos
- Emparejamiento
- Establecimiento de conexiones
- Transferencia de archivos
- Manejo de errores y reconexión

### BluetoothViewModel
Coordina las interacciones entre la UI y el BluetoothService:
- Expone estados de conexión como StateFlow
- Proporciona métodos para iniciar/detener conexiones
- Gestiona el progreso de transferencia

### Diseño de la interfaz
La aplicación utiliza:
- Navigation Component para la navegación entre pantallas
- RecyclerView para listas de archivos y dispositivos
- ProgressBar para mostrar el progreso de transferencia
- BottomNavigationView para la navegación principal

## Tecnologías utilizadas

- **Kotlin**: Lenguaje principal de programación
- **Android Jetpack**: Navigation Component, ViewModel, StateFlow
- **Bluetooth API**: Bluetooth Classic para transferencia de archivos
- **Coroutines**: Para operaciones asíncronas
- **Android Storage Framework**: Para acceso a archivos

## Arquitectura

La aplicación sigue el patrón de arquitectura MVVM (Model-View-ViewModel):

- **Model**: Clases de datos y lógica de negocio (BluetoothService, FileRepository)
- **View**: Actividades y fragmentos (MainActivity, FileListFragment, BluetoothShareScreen)
- **ViewModel**: Capa intermedia (BluetoothViewModel, FileViewModel)

## Compatibilidad de dispositivos

La aplicación está optimizada para funcionar en una amplia gama de dispositivos Android:

- Se adapta a los cambios en la API de Bluetooth en diferentes versiones de Android
- Implementa múltiples métodos de conexión Bluetooth para mayor compatibilidad
- Maneja correctamente los permisos según la versión de Android

## Seguridad

- La aplicación sólo permite compartir con dispositivos emparejados
- Las transferencias de archivos son directas entre dispositivos, sin pasar por servidores externos
- Se respetan las restricciones de almacenamiento de Android 10+ (Scoped Storage)

## Contribuciones

Si deseas contribuir a este proyecto:
1. Haz un fork del repositorio
2. Crea una rama para tu funcionalidad (`git checkout -b feature/nueva-funcionalidad`)
3. Realiza tus cambios y haz commit (`git commit -m 'Añadir nueva funcionalidad'`)
4. Haz push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

