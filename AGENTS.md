# AGENTS.md

Guía de trabajo para este repositorio. Antes de tocar código, léela entera: casi
todos los errores que aparecen aquí no son de Kotlin, sino de usar APIs que Android
no publica.

## Qué es este proyecto

App Android de diagnóstico de señales, en Kotlin y Jetpack Compose. Un solo
módulo de aplicación (`app`), sin biblioteca propia, sin KSP, sin base de datos y
sin dependencias de terceros. Todo el andamiaje está hecho a mano: contenedor de
dependencias, gráficas en Canvas, mapa offline, almacenamiento en JSON y clientes
de red.

- `applicationId` `com.diagnostico.senales`, con sufijo `.debug` en debug.
- compileSdk y targetSdk 35, minSdk 24, Java 17, AGP 8.13, Kotlin 2.2.
- La interfaz está en español de España. Los textos van directamente en el código,
  sin recursos de string.

## Comandos

El proyecto vive en `C:\Users\mario\Desktop\android\NetDiag`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

El Java del PATH es el 8, así que `JAVA_HOME` tiene que apuntar al JBR de Android
Studio. La carpeta es solo ASCII a propósito; AGP rechaza las rutas con eñes o
acentos y, si algún día hay que moverla, se descomenta
`android.overridePathCheck=true` en `gradle.properties`.

En este entorno el proyecto no se busca por ruta absoluta, se localiza así:

```powershell
$dir = (Get-ChildItem -Path "C:\Users\mario\Desktop\android" -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "app\build.gradle.kts") } |
    Select-Object -First 1).FullName
```

Para leer ficheros desde PowerShell usa siempre `-Encoding UTF8`. Los archivos
están en UTF-8 sin BOM y sin acentos rotos.

## Avisos y notas

`ExplanationCard` y `UnavailableNote` son los dos únicos componentes que pintan
avisos, y los dos se cierran con la X. El registro vive en
`DismissedNoteStore`, que está en `AppContainer`, así que el cierre se pierde al
matar el proceso: es lo que hace que las notas vuelvan al abrir la app.

- La clave de cada nota es su propio texto, así que dos pantallas que enseñan el
  mismo aviso comparten el cierre.
- Si añades un aviso cuyo texto se arma con un valor variable, el cierre no
 persistirá entre pantallas: mejor texto fijo y el dato aparte.
- No guardes notas descartadas en `AppPreferences`; eso sí sobrevive a la app.

## Reglas del proyecto

1. **Solo API pública.** Antes de usar una clase o método de `android.*`,
   comprueba que existe en el SDK público. Varios `CellInfo`, `CellIdentity*` y
   `ScanResult` ya nos han dado sorpresas: `Sensor.getAccuracy()`,
   `TelephonyManager.SIM_STATE_ACTIVE`, `SubscriptionInfo.getSimState()`,
   `CellInfo.getCellIdentityNr()` y los getters por tipo de `CellInfo*` no son
   accesibles. Para comprobarlo está `javap` sobre
   `platforms/android-35/android.jar`.
2. **Nada de datos identificativos.** Nunca leer ni pedir IMSI, ICCID, número de
   línea, claves Wi-Fi, cámara, micrófono, contactos, SMS ni almacenamiento
   externo. Si el dato no está disponible, se muestra `NO_DISPONIBLE` con una
   nota que explique por qué.
3. **No inventes medidas.** Si Android no publica un valor, la interfaz lo dice.
   Las estimaciones van etiquetadas como tales, tanto en el código (comentario)
   como en pantalla.
4. **Explica los límites del sistema.** Cuando el motivo de un dato ausente es una
   restricción de la plataforma (escaneo limitado, MAC aleatoria, ancho de canal no
   público, latencia ICMP sin socket público), dilo junto al dato, no en un
   comentario de código.
5. **El estado es explícito.** Cada módulo usa `data class` de estado +
   `MutableStateFlow` privado + `StateFlow` público. Las funciones Compose solo
   leen estado y lanzan acciones; el trabajo pesado va en `viewModelScope`.
6. **Nada de librerías nuevas** sin motivo claro. Las gráficas y el mapa se
   resuelven en `ui/components/Charts.kt`. Si hace falta una dependencia, que esté
   justificada en el README.

## Trampas de Kotlin que ya han costado tiempo

- **`to` tiene menor precedencia que `?:`.** `"x" to a ?: b` se parsea como
  `(x to a) ?: b` y no compila. Escribe siempre `"x" to (a ?: b)`.
- **`MetricRow` toma `List<Pair<String, String?>>`.** Los literales se anotan
  `listOf<Pair<String, String?>>(` para que el compilador no infiera
  `List<Serializable>` cuando se mezclan valores nulos y no nulos.
- **`Float` contra `Double`.** Las medidas de `Location` y `GnssStatus` vienen en
  `Float`; `formatDecimal` y `formatDistance` piden `Double`. Convierte con
  `it.toDouble()`.
- **`signalLevelColor` no es `@Composable`.** Se llama dentro de `DrawScope`, en
  un `Canvas`. Si la marcas como `@Composable`, el compilador la rechaza ahí.
- **`historyRecord.verdict` es `Verdict?`, no un texto.** El enum vive en
  `core/util/SignalMath.kt`. Pasa el enum; para el texto está `Verdict.label`.
- **`Icons.Outlined.OpenInNew` está obsoleto.** Usa
  `Icons.AutoMirrored.Outlined.OpenInNew` con el import
  `androidx.compose.material.icons.automirrored.outlined.OpenInNew`.
- **No se puede llamar a un `@Composable` desde `DrawScope`.** Para reaccionar al
  modo privacidad dentro de un `Canvas`, lee `LocalPrivacyMode.current` en la
  función `@Composable` que lo contiene y pásale el resultado al bloque de dibujo.
  El texto de `Canvas` tampoco aparece en el árbol de accesibilidad, así que
  `uiautomator` no lo ve: para comprobarlo hay que leer el código o usar
  `screencap`.

## Al tocar cada área

- **Telephony / SIM:** respeta la nota de privacidad y usa `validInt` para
  descartar los centinelas `CellInfo.UNAVAILABLE` antes de mostrar un número.
- **Wi-Fi:** `ScanResult.capabilities` es un `String` (`[WPA2-PSK-CCMP][ESS]`),
  no bits. El ancho de canal solo existe en algunas versiones: usa
  `reportedWidthMhz` y, si es `null`, el ancho supuesto por el usuario.
- **GNSS:** `GnssStatus` se lee con el API público indexado por `getSatellites()`,
  no por los campos privados. El TTFF se deriva del reloj del proceso, porque
  `elapsedRealtimeNanos` no se expone al callback de forma estable.
- **Red:** `HttpTransfer` es el único punto que abre sockets. Las pruebas de
  descarga y subida necesitan Internet y usan destinos editables desde *Más*.
- **Historial:** el JSON es del almacenamiento interno y solo lo lee la app. El CSV
  se genera en memoria (`historyCsv`) y se ofrece con un `ACTION_SEND` de tipo
  `text/csv`; no se escribe en un fichero arbitrario ni se pide permiso de
  almacenamiento externo.
- **Privacidad:** cualquier dato identificativo nuevo que se muestre en pantalla
  tiene que pasar por un helper de `ui/privacy/PrivacyMode.kt` (`masked`,
  `maskedIp`, `maskedOperator`, `maskedSim`, `maskedCoords`). Si añades un campo
  sensible, envuélvelo en el mismo punto donde se compone el texto; no vale con
  enmascararlo en el repositorio, porque el historial y el CSV necesitan el valor
  real. Las máscaras son de longitud fija a propósito, para no filtrar el tamaño
  del valor original.

## Verificación

- Compila antes de dar cualquier tarea por terminada: `:app:compileDebugKotlin`.
- Los warnings deprecados de `CellInfo` y `NetworkType` son esperables; se usan
  como fallback en versiones antiguas donde no hay alternativa pública.
- El repositorio público es <https://github.com/MarioAvilaSL/NetDiag> y la URL vive
  en `ui/about/AboutScreen.kt`. Si lo cambias, actualiza también `README.md`.
- El comportamiento en dispositivo cambia por fabricante. Lo que aparece en
  pantalla como `No disponible` con nota es intencionado, no un fallo pendiente.
- `lint` está configurado con `abortOnError = false`: los avisos de permisos
  ausentes están documentados en pantalla, no silenciados.
- El modo privacidad se comprueba con `adb shell uiautomator dump`: con el
  interruptor activo, `content-desc` debe incluir `Modo privacidad activo` y los
  campos sensibles deben salir como `••••`. La propiedad se lee en
  `data/prefs/AppPreferences.kt` con la clave `privacy_mode`.