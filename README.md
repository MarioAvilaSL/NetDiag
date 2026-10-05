# Señales

Aplicación Android de diagnóstico de señales y conectividad. Mide la calidad de
la red Wi-Fi, analiza la red móvil y la SIM, sigue la posición por GNSS, mide la
precisión real del GPS sobre el mapa y guarda todas las pruebas en un historial
local.

Todo funciona sin cuenta, sin servidor propio y sin enviar datos a terceros: los
resultados se quedan en el dispositivo.

**Código fuente:** <https://github.com/MarioAvilaSL/NetDiag> · **Nombre del proyecto:** NetDiag

## Qué hace

| Módulo | Qué mide |
| --- | --- |
| **Panel** | Resumen de la red activa: transporte, IP, DNS, enlace, GNSS y batería. |
| **Wi-Fi** | SSID, BSSID, banda, canal, estándar, velocidad de enlace, latencia con resolución DNS real, descarga/subida y una prueba de estabilidad de 30 s. |
| **Escáner** | Redes Wi-Fi cercanas, seguridad, ocupación por canal y canal menos saturado. |
| **Móvil** | Operador, tipo de red y generación, RSRP/RSRQ/SINR, CELL ID, TAC, PCI, itinerancia, forzado de red a 4G/5G, latencia y velocidad. |
| **SIM** | Ranuras, operador, estado, MCC/MNC, eSIM y suscripción de datos. |
| **GNSS** | Fix, precisión, altitud, velocidad, rumbo, TTFF y satélites en vista con su C/N0. |
| **Cielo GNSS** | Sky plot por constelación (GPS, GLONASS, Galileo, BeiDou, QZSS) con azimut y elevación. |
| **Mapa y ruta** | Grabación de la trayectoria sobre un mapa offline dibujado con Canvas. |
| **Precisión GPS** | Prueba cronometrada con el dispositivo quieto: deriva, dispersión y veredicto. |
| **Historial** | Todas las pruebas guardadas, con detalle yexportables como CSV. |
| **Más** | Tema, destinos de prueba y duración por defecto. |
| **Información** | Versión, enlace al repositorio, tecnología y privacidad. |

## Avisos y notas

Los recuadros de aviso, en rojo o con el color normal, se cierran con la X de la
esquina. Al cerrarlos desaparece solo el aviso y el resto de la pantalla no se
toca. El cierre se guarda únicamente en memoria, mientras la app esté abierta:
al cerrar y volver a abrir NetDiag todos los avisos vuelven a aparecer. No se
escribe nada en disco.

## Requisitos

- Android 7.0 (API 24) o superior.
- JDK 17 para compilar. En la máquina de desarrollo basta el JBR que trae Android Studio.
- Ningún hardware es obligatorio: Wi-Fi, móvil, GPS, Bluetooth y NFC son todos
  opcionales y la app se adapta a lo que encuentre.

## Compilar

Desde Android Studio: `File > Open` sobre la raíz del proyecto y ejecutar el
módulo `app`.

Desde la línea de comandos:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\mario\Desktop\android\NetDiag
.\gradlew.bat :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. La variante debug usa
el paquete `com.diagnostico.senales.debug`, así que convive con una instalación
de la variante de publicación.

El proyecto está en una ruta solo ASCII a propósito: AGP bloquea las rutas no
ASCII en Windows. Si mueves la carpeta a un nombre con eñes o acentos, compila
fallará y tendrás que descomentar `android.overridePathCheck=true` en
`gradle.properties`.

Si quieres compilar tú mismo, `gradlew.bat` descarga el JDK de Android Studio y todas
las dependencias declaradas en `gradle/libs.versions.toml`. No hace falta instalar
Android Studio, pero sí tener el SDK de Android con la plataforma 35.

## Licencia

MIT. El texto completo está en [`LICENSE`](LICENSE).

```
Copyright (c) 2026 Mario Avila SL
```

Se permite usar, copiar, modificar, publicar y distribuir este software, también
en uso comercial, siempre que se mantenga el aviso de copyright y el aviso de
licencia. El software se distribuye sin garantía de ningún tipo.

Esta app muestra datos de tu propio dispositivo y solo pide los permisos que
Android exige para leerlos. No hay analítica, publicidad ni seguimiento, y las
únicas conexiones salientes son las que tú inicias al medir latencia o velocidad
contra el destino que configures.

## Dependencias

Solo AndroidX y Jetpack Compose, gestionadas por el catálogo de versiones de
Gradle:

- `androidx.core`, `activity-compose`, `lifecycle` (runtime, viewmodel y compose)
- `compose-bom` con `ui`, `graphics`, `material3` e `icons-extended`
- `navigation-compose` y `kotlinx-coroutines-android`

No hay librerías de terceros para gráficas, mapas, JSON ni red: el mapa, los
histogramas, las curvas de línea, el sky plot y el almacenamiento en JSON están
escritos a mano en `ui/components/Charts.kt` y `data/history/HistoryRepository.kt`.

## Permisos

La app pide solo lo imprescindible y explica en pantalla para qué sirve cada
permiso y qué se pierde si se deniega.

| Permiso | Para qué |
| --- | --- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Android solo revela SSID, BSSID, resultados de escaneo y datos GNSS con ubicación concedida. |
| `NEARBY_WIFI_DEVICES` | Enumerar redes cercanas desde Android 13. Se declara con `neverForLocation` porque la app no deduce la posición a partir de él. |
| `READ_PHONE_STATE` | Operador, estado de la SIM y tipo de red. |
| `BLUETOOTH_CONNECT` | Consultar si el adaptador está activo y su nombre. |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE` | Descargas, latencia, DNS y diagnóstico de conectividad. |

La app **no** solicita ni intenta leer IMSI, ICCID, número de línea, claves Wi-Fi,
cámara, micrófono, contactos, SMS ni almacenamiento externo. Cuando un dato no es
accesible por las reglas de la plataforma, se muestra `No disponible` en lugar de
inventar un valor.

## Límites reales de la plataforma

Estos no son errores de la app, son restricciones de Android que la interfaz
explica en el punto donde aparecen:

- **Escaneo Wi-Fi limitado.** Desde Android 9 el sistema limita a cuatro lecturas
  cada dos minutos por aplicación.
- **MAC aleatoria.** Desde Android 10 el sistema sustituye la MAC del escaneo por
  un identificador aleatorio, así que no sirve para localizar un router concreto.
- **SSID oculto aunque haya permiso.** Si concedes la ubicación mientras ya
  estás conectado, Android sigue devolviendo `<unknown ssid>` hasta que
  reinicias la conexión: hay que apagar y encender el Wi-Fi. La app lo indica en
  pantalla en lugar de decir que no hay red.
- **Ancho de canal.** Android no lo publica en la mayoría de versiones; cuando el
  sistema lo informa se usa, y si no se recurre al ancho que declara el usuario y
  se marca como supuesto.
- **Latencia ICMP.** Android no ofrece un socket ICMP público. `PingEngine` lo
  dice con claridad en la tarjeta del resultado: mide por TCP/HTTP salvo que se
  añada una dependencia nativa.
- **Seguridad Wi-Fi.** Las capacidades llegan como texto (`[WPA2-PSK-CCMP][ESS]`),
  así que se analizan esas etiquetas y, si no dicen nada, se informa de que la
  seguridad es desconocida en vez de asumir que la red es abierta.
- **Métricas móviles.** RSRP, RSRQ y SINR solo existen en 4G/5G. En 2G y 3G el
  sistema solo entrega barras 0..4 o un valor ASU.
- **Precisión de sensor.** `Sensor.getAccuracy()` no está en el SDK público, así
  que la app no muestra precisión de sensor en lugar de suponerla.

## Estructura

```
app/src/main/java/com/diagnostico/senales/
├── MainActivity.kt            Activity, tema y esqueleto con cajón y barra inferior
├── AppContainer.kt            Inyección de dependencias a mano
├── core/
│   ├── permission/            Grupos de permisos y motivo de cada uno
│   └── util/                  Formatos, conversiones y evaluaciones automáticas
├── data/
│   ├── model/                 Modelos de datos de cada dominio
│   ├── cellular/              Operador, SIM, celda y conversión de frecuencias
│   ├── connectivity/          Red activa, transporte, IP y DNS
│   ├── device/                Hardware, batería, sensores y direcciones
│   ├── gnss/                  LocationManager y proveedor de satélites
│   ├── history/               Historial en JSON y exportación a CSV
│   ├── net/                   Transferencia HTTP, ping y test de velocidad
│   ├── prefs/                 Preferencias con SharedPreferences
│   └── wifi/                  Estado de conexión y escaneo
└── ui/
    ├── AppViewModelFactory.kt Fábrica única de ViewModels
    ├── about/                 Información, versión y enlace al repositorio
    ├── components/            Componentes, avisos descartables y gráficas Canvas
    ├── nav/                   Rutas, destinos y NavHost
    ├── privacy/               Contexto de privacidad y máscaras de datos
    ├── theme/                 Colores y temas
    └── <módulo>/              Una pantalla y su ViewModel por módulo
```

Cada módulo sigue el mismo patrón: un `data class` de estado, un ViewModel con un
`MutableStateFlow` privado y un `StateFlow` público, y funciones Compose que solo
leen estado y lanzan acciones. La pantalla de información es la excepción: no
tiene estado, solo muestra datos estáticos.

## Informes y errores

Este proyecto es software libre: todo el código está en
<https://github.com/MarioAvilaSL/NetDiag> y se publica bajo la licencia MIT (ver
[`LICENSE`](LICENSE)). Cualquiera puede usarlo, modificarlo y redistribuirlo
manteniendo el aviso de copyright.

Los binarios se distribuyen como *Releases* de GitHub en lugar de subirse al
historial: `.gitignore` excluye `*.apk` a propósito, porque un binario en cada
clonado ensucia el repositorio y no aporta nada al código fuente. Para instalar la
última versión publicada, descarga el `.apk` desde la sección *Releases*.

Si encuentras un fallo o quieres proponer un cambio, abre una *issue* describiendo
el modelo de teléfono, la versión de Android y los pasos para reproducirlo. Casi
todos los problemas de esta app vienen del fabricante: lo que Android expone para
la batería, la temperatura, los sensores o la celda cambia por modelo, así que el
modelo es la información más útil que puedes dar.

## Estructura del repositorio

| Ruta | Contenido |
| --- | --- |
| `app/src/main/java/.../data/` | Repositorios que leen de Android, sin lógica de Compose |
| `app/src/main/java/.../ui/` | Una carpeta por módulo, con su pantalla y su ViewModel |
| `app/src/main/java/.../ui/privacy/` | Enmascarado de datos identificativos |
| `LICENSE` | Licencia MIT |

## Privacidad

- Sin analítica, sin publicidad y sin SDK de terceros.
- Las pruebas se guardan en el almacenamiento interno de la app, en un JSON que
  solo ella lee.
- El historial se exporta como CSV generándolo en memoria y compartiéndolo con
  `ACTION_SEND`. La app no escribe en carpetas ajenas ni pide permiso de
  almacenamiento externo: el destino lo elige quien recibe el archivo.
- Las medidas de descarga y latencia necesitan Internet y contactan con los
  destinos configurados en *Más*, que son editables y se pueden cambiar por
  cualquier servidor propio.

### Modo privacidad

*Más* tiene un interruptor que sustituye los datos identificativos por una máscara
de longitud fija antes de dibujarlos. El ajuste se guarda en las preferencias y se
recuerda al reabrir la app; mientras está activo, un candado aparece en la barra
superior de cada pantalla. Por defecto viene apagado.

Quedan enmascarados: SSID, BSSID, dirección MAC, IPv4 e IPv6, gateway, servidores
DNS, operador, código de país, MCC/MNC, CELL ID, TAC, PCI, latitud y longitud, y las
etiquetas numéricas de los ejes del mapa de ruta. La forma de la trayectoria y los
círculos de precisión se siguen viendo, porque son el dato útil.

Siguen visibles a propósito: niveles en dBm, bandas, canales, frecuencias,
ancho de canal, seguridad, estándar Wi-Fi, RSRP/RSRQ/SINR, altitudes, velocidades,
precisión, estado de la batería, sensores, modelo y versión de Android. Sin ellos la
captura no serviría para diagnosticar nada.

Dos límites que conviene tener presentes:

- El historial guardado y el CSV que se comparte conservan los valores reales. El
  modo protege lo que se ve en pantalla, no lo que ya está en el almacenamiento de
  la app.
- No se usa `FLAG_SECURE`, así que el enmascarado es una ayuda para compartir
  capturas y no una garantía: basta con que alguien vuelva a desactivar el
  interruptor o con que se exporte el historial para que salgan los datos reales.

## Estado actual

El proyecto compila y genera el APK debug. La verificación en dispositivo se hace
en Android Studio, ya que el comportamiento real depende del fabricante: lo que
publica Android para la batería, la temperatura, los sensores o la celda cambia
por modelo, y la app lo refleja mostrando `No disponible` o una nota explicativa.