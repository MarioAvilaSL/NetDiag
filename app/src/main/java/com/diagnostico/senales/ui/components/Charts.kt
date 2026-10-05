package com.diagnostico.senales.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diagnostico.senales.core.util.FMT
import com.diagnostico.senales.core.util.SignalLevel
import com.diagnostico.senales.data.model.Constellation
import com.diagnostico.senales.data.model.SatelliteInfo
import com.diagnostico.senales.data.model.TrackPoint
import com.diagnostico.senales.ui.privacy.LocalPrivacyMode
import com.diagnostico.senales.ui.privacy.Masks
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Gráficas propias dibujadas con Canvas.
 *
 * Se evita una librería externa porque las cuatro visualizaciones que pide la app
 * (series temporales, barras de señal, ocupación de canales y sky plot) son
 * simples y solo necesitan interacción puntual. Si en el futuro hacen falta más
 * tipos, este es el punto único donde crecería una librería de gráficas.
 */

private fun textPaint(color: Color, sizePx: Float, align: Paint.Align = Paint.Align.LEFT): Paint =
    Paint().apply {
        this.color = color.toArgb()
        textSize = sizePx
        textAlign = align
        isAntiAlias = true
    }

private fun DrawScope.drawLabel(text: String, x: Float, y: Float, paint: Paint) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

fun signalLevelColor(level: SignalLevel): Color = when (level) {
    SignalLevel.EXCELENTE -> Color(0xFF1B8E3C)
    SignalLevel.BUENA -> Color(0xFF62A80F)
    SignalLevel.REGULAR -> Color(0xFFD98200)
    SignalLevel.DEBIL -> Color(0xFFC62828)
    SignalLevel.MUY_DEBIL -> Color(0xFF6D4C41)
}

fun constellationColor(constellation: Constellation): Color = when (constellation) {
    Constellation.GPS -> Color(0xFF1E88E5)
    Constellation.GLONASS -> Color(0xFFD81B60)
    Constellation.GALILEO -> Color(0xFF43A047)
    Constellation.BEIDOU -> Color(0xFF8E24AA)
    Constellation.QZSS -> Color(0xFFFB8C00)
    Constellation.NAVIC -> Color(0xFF00ACC1)
    Constellation.SBAS -> Color(0xFF6D4C41)
    Constellation.UNKNOWN -> Color(0xFF757575)
}

// ---------------------------------------------------------------------------
// Series temporales
// ---------------------------------------------------------------------------

data class ChartSeries(val label: String, val points: List<Float?>, val color: Color)

@Composable
fun LineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    yMin: Float? = null,
    yMax: Float? = null,
    yFormatter: (Float) -> String = { String.format(FMT, "%.0f", it) },
    xLabels: List<String>? = null,
    fillArea: Boolean = true,
    gridLines: Int = 4,
    height: Dp = 170.dp,
) {
    MultiLineChart(
        series = listOf(ChartSeries("", values, color)),
        modifier = modifier,
        yMin = yMin,
        yMax = yMax,
        yFormatter = yFormatter,
        xLabels = xLabels,
        fillArea = fillArea,
        gridLines = gridLines,
        height = height,
    )
}

@Composable
fun MultiLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yMin: Float? = null,
    yMax: Float? = null,
    yFormatter: (Float) -> String = { String.format(FMT, "%.0f", it) },
    xLabels: List<String>? = null,
    fillArea: Boolean = false,
    gridLines: Int = 4,
    height: Dp = 170.dp,
    showLegend: Boolean = series.size > 1,
) {
    val outline = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val leftPad = 48.dp.toPx()
            val bottomPad = 20.dp.toPx()
            val topPad = 10.dp.toPx()
            val rightPad = 12.dp.toPx()
            val plotWidth = size.width - leftPad - rightPad
            val plotHeight = size.height - topPad - bottomPad
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

            val allValues = series.flatMap { it.points }.filterNotNull()
            if (allValues.isEmpty()) {
                drawLabel(
                    "Sin datos todavía",
                    size.width / 2f - 40.dp.toPx(),
                    size.height / 2f,
                    textPaint(emptyColor, 12.sp.toPx()),
                )
                return@Canvas
            }

            val minValue = yMin ?: allValues.min()
            val maxValue = yMax ?: allValues.max()
            val range = (maxValue - minValue).takeIf { abs(it) > 0.0001f } ?: 1f
            val pointCount = series.maxOf { it.points.size }.coerceAtLeast(1)

            fun xAt(index: Int): Float = leftPad + plotWidth * index / (pointCount - 1).coerceAtLeast(1)
            fun yAt(value: Float): Float = topPad + plotHeight * (1f - (value - minValue) / range)

            val valuePaint = textPaint(labelColor, 10.sp.toPx(), Paint.Align.RIGHT)
            repeat(gridLines + 1) { index ->
                val value = minValue + range * index / gridLines
                val y = yAt(value)
                drawLine(
                    color = outline.copy(alpha = 0.25f),
                    start = Offset(leftPad, y),
                    end = Offset(size.width - rightPad, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                )
                drawLabel(yFormatter(value), leftPad - 6.dp.toPx(), y + 3.5f, valuePaint)
            }

            drawLine(
                color = outline.copy(alpha = 0.6f),
                start = Offset(leftPad, topPad),
                end = Offset(leftPad, topPad + plotHeight),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = outline.copy(alpha = 0.6f),
                start = Offset(leftPad, topPad + plotHeight),
                end = Offset(size.width - rightPad, topPad + plotHeight),
                strokeWidth = 1.5f,
            )

            series.forEach { single ->
                val path = Path()
                var started = false
                var firstIndex = -1
                var lastIndex = -1
                single.points.forEachIndexed { index, value ->
                    if (value == null) return@forEachIndexed
                    val x = xAt(index)
                    val y = yAt(value)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                        firstIndex = index
                    } else {
                        path.lineTo(x, y)
                    }
                    lastIndex = index
                }
                if (!started) return@forEach

                if (fillArea && series.size == 1) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xAt(lastIndex), topPad + plotHeight)
                        lineTo(xAt(firstIndex), topPad + plotHeight)
                        close()
                    }
                    drawPath(fill, single.color.copy(alpha = 0.16f))
                }
                drawPath(
                    path = path,
                    color = single.color,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )

                val lastValue = single.points[lastIndex]
                if (lastValue != null) {
                    drawCircle(
                        color = single.color,
                        radius = 3.5.dp.toPx(),
                        center = Offset(xAt(lastIndex), yAt(lastValue)),
                    )
                }
            }

            if (xLabels != null && xLabels.isNotEmpty()) {
                val axisPaint = textPaint(labelColor, 10.sp.toPx(), Paint.Align.CENTER)
                val indices = listOf(0, xLabels.lastIndex)
                indices.distinct().forEach { index ->
                    val label = xLabels.getOrNull(index) ?: return@forEach
                    drawLabel(label, xAt(index), size.height - 4.dp.toPx(), axisPaint)
                }
            }
        }

        if (showLegend) {
            Text(
                text = series.joinToString("   ") { it.label },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Barras de señal por red
// ---------------------------------------------------------------------------

@Composable
fun SignalBarsChart(
    entries: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    maxEntries: Int = 12,
    barHeight: Dp = 22.dp,
) {
    val outline = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val visible = entries.sortedByDescending { it.second }.take(maxEntries)
    if (visible.isEmpty()) {
        EmptyState("No hay redes detectadas todavía")
        return
    }

    val chartHeight = barHeight * visible.size + 24.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            val leftPad = 96.dp.toPx()
            val rightPad = 52.dp.toPx()
            val trackWidth = size.width - leftPad - rightPad
            if (trackWidth <= 0f) return@Canvas

            val axisPaint = textPaint(labelColor, 9.sp.toPx(), Paint.Align.LEFT)
            val valuePaint = textPaint(labelColor, 10.sp.toPx(), Paint.Align.LEFT)

            // Escala fija de -100 a -30 dBm para que las barras sean comparables.
            listOf(-30, -50, -70, -90).forEach { reference ->
                val x = leftPad + trackWidth * (-30 - reference) / 70f
                drawLine(
                    color = outline.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height - 14.dp.toPx()),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
                )
                drawLabel("$reference", x - 8.dp.toPx(), size.height - 2.dp.toPx(), axisPaint)
            }

            visible.forEachIndexed { index, (label, dbm) ->
                val y = index * barHeight.toPx() + 4.dp.toPx()
                val barHeightPx = barHeight.toPx() - 8.dp.toPx()
                val fraction = ((-30 - dbm.coerceIn(-100, -30)) / 70f).coerceIn(0f, 1f)
                val level = SignalLevel.fromDbm(dbm)
                val barColor = signalLevelColor(level)

                drawRoundRect(
                    color = outline.copy(alpha = 0.12f),
                    topLeft = Offset(leftPad, y),
                    size = Size(trackWidth, barHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(leftPad, y),
                    size = Size(trackWidth * fraction, barHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
                drawLabel(
                    text = label.take(14),
                    x = 0f,
                    y = y + barHeightPx - 2.dp.toPx(),
                    paint = textPaint(labelColor, 10.sp.toPx()),
                )
                drawLabel(
                    text = "$dbm dBm",
                    x = leftPad + trackWidth + 6.dp.toPx(),
                    y = y + barHeightPx - 2.dp.toPx(),
                    paint = valuePaint,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ocupación de canales
// ---------------------------------------------------------------------------

data class ChannelBar(
    val channel: Int,
    val networkCount: Int,
    val averageRssi: Int?,
    val isRecommended: Boolean,
)

@Composable
fun ChannelOccupancyChart(
    bars: List<ChannelBar>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    if (bars.isEmpty()) {
        EmptyState("Sin datos de canales: ejecuta un escaneo")
        return
    }
    val outline = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val maxCount = bars.maxOf { it.networkCount }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val leftPad = 26.dp.toPx()
            val bottomPad = 30.dp.toPx()
            val topPad = 10.dp.toPx()
            val plotWidth = size.width - leftPad - 8.dp.toPx()
            val plotHeight = size.height - topPad - bottomPad
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

            val slot = plotWidth / bars.size
            val barWidth = slot * 0.62f

            val gridPaint = textPaint(labelColor, 9.sp.toPx(), Paint.Align.RIGHT)
            repeat(3) { index ->
                val value = maxCount * index / 2
                val y = topPad + plotHeight * (1f - index / 2f)
                drawLine(
                    color = outline.copy(alpha = 0.2f),
                    start = Offset(leftPad, y),
                    end = Offset(size.width - 8.dp.toPx(), y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
                )
                drawLabel(value.toString(), leftPad - 5.dp.toPx(), y + 3f, gridPaint)
            }

            bars.forEachIndexed { index, bar ->
                val centerX = leftPad + slot * index + slot / 2f
                val fraction = bar.networkCount.toFloat() / maxCount
                val barHeightPx = plotHeight * fraction
                val top = topPad + plotHeight - barHeightPx
                val barColor = if (bar.isRecommended) accent else outline.copy(alpha = 0.55f)

                drawRoundRect(
                    color = barColor.copy(alpha = if (bar.isRecommended) 0.9f else 0.6f),
                    topLeft = Offset(centerX - barWidth / 2f, top),
                    size = Size(barWidth, barHeightPx.coerceAtLeast(1.5f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
                drawLabel(
                    text = bar.channel.toString(),
                    x = centerX,
                    y = size.height - 14.dp.toPx(),
                    paint = textPaint(labelColor, 9.sp.toPx(), Paint.Align.CENTER),
                )
                if (bar.networkCount > 0) {
                    drawLabel(
                        text = bar.networkCount.toString(),
                        x = centerX,
                        y = top - 3.dp.toPx(),
                        paint = textPaint(labelColor, 9.sp.toPx(), Paint.Align.CENTER),
                    )
                }
            }

            drawLine(
                color = outline.copy(alpha = 0.6f),
                start = Offset(leftPad, topPad + plotHeight),
                end = Offset(size.width - 8.dp.toPx(), topPad + plotHeight),
                strokeWidth = 1.5f,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Indicador circular
// ---------------------------------------------------------------------------

@Composable
fun Gauge(
    percent: Int?,
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 128.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    caption: String? = null,
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val unknownColor = MaterialTheme.colorScheme.outline
    val value = percent?.coerceIn(0, 100)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (value != null) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * value / 100f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (value != null) "$value %" else "—",
                style = MaterialTheme.typography.titleLarge,
                color = if (value != null) color else unknownColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sky plot GNSS
// ---------------------------------------------------------------------------

/**
 * Cielo GNSS: norte arriba, centro en el cénit y borde en el horizonte.
 *
 * Android no publica la posición orbital real de cada satélite: solo la señal
 * medida, con su azimut y elevación. El dibujo es por tanto una proyección
 * geométrica de esos valores, no un mapa del cielo exacto.
 */
@Composable
fun SkyPlot(
    satellites: List<SatelliteInfo>,
    modifier: Modifier = Modifier,
    onSelect: (SatelliteInfo) -> Unit = {},
) {
    val outline = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .pointerInput(satellites) {
                detectTapGestures { tap ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val radius = min(size.width, size.height) / 2f - 12.dp.toPx()
                    val hit = satellites
                        .filter { it.azimuthDegrees != null && it.elevationDegrees != null }
                        .minByOrNull { satellite ->
                            val azimuth = satellite.azimuthDegrees ?: 0f
                            val elevation = satellite.elevationDegrees ?: 0f
                            val radial = ((90f - elevation) / 90f) * radius
                            val x = centerX + radial * sin(Math.toRadians(azimuth.toDouble())).toFloat()
                            val y = centerY - radial * cos(Math.toRadians(azimuth.toDouble())).toFloat()
                            val dx = x - tap.x
                            val dy = y - tap.y
                            dx * dx + dy * dy
                        }
                    if (hit != null) {
                        val azimuth = hit.azimuthDegrees ?: 0f
                        val elevation = hit.elevationDegrees ?: 0f
                        val radial = ((90f - elevation) / 90f) * radius
                        val x = centerX + radial * sin(Math.toRadians(azimuth.toDouble())).toFloat()
                        val y = centerY - radial * cos(Math.toRadians(azimuth.toDouble())).toFloat()
                        val dx = x - tap.x
                        val dy = y - tap.y
                        if (dx * dx + dy * dy <= (22.dp.toPx()).let { it * it }) onSelect(hit)
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - 12.dp.toPx()
        if (radius <= 0f) return@Canvas

        listOf(0f, 30f, 60f).forEach { elevation ->
            val ringRadius = ((90f - elevation) / 90f) * radius
            drawCircle(
                color = outline.copy(alpha = if (elevation == 0f) 0.7f else 0.25f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = if (elevation == 0f) 1.6f else 1f),
            )
        }
        drawLine(
            color = outline.copy(alpha = 0.3f),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
        )
        drawLine(
            color = outline.copy(alpha = 0.3f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
        )

        val cardinalPaint = textPaint(labelColor, 12.sp.toPx(), Paint.Align.CENTER)
        drawLabel("N", center.x, center.y - radius - 4.dp.toPx(), cardinalPaint)
        drawLabel("S", center.x, center.y + radius + 14.dp.toPx(), cardinalPaint)
        drawLabel("E", center.x + radius + 12.dp.toPx(), center.y + 4.dp.toPx(), cardinalPaint)
        drawLabel("O", center.x - radius - 12.dp.toPx(), center.y + 4.dp.toPx(), cardinalPaint)

        val elevationPaint = textPaint(labelColor, 9.sp.toPx(), Paint.Align.LEFT)
        listOf(0f, 30f, 60f).forEach { elevation ->
            val ringRadius = ((90f - elevation) / 90f) * radius
            drawLabel("$elevation°", center.x + 4.dp.toPx(), center.y - ringRadius + 10.dp.toPx(), elevationPaint)
        }

        satellites.forEach { satellite ->
            val azimuth = satellite.azimuthDegrees ?: return@forEach
            val elevation = satellite.elevationDegrees ?: return@forEach
            val radial = ((90f - elevation) / 90f) * radius
            val x = center.x + radial * sin(Math.toRadians(azimuth.toDouble())).toFloat()
            val y = center.y - radial * cos(Math.toRadians(azimuth.toDouble())).toFloat()
            val color = constellationColor(satellite.constellation)
            if (satellite.usedInFix) {
                drawCircle(color = color, radius = 6.dp.toPx(), center = Offset(x, y))
                drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = 10.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 1.5f),
                )
            } else {
                drawCircle(
                    color = color,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 1.8f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mapa de trayectoria (proyección local, sin teselas)
// ---------------------------------------------------------------------------

/**
 * Mapa sin teselas: dibuja la trayectoria sobre una rejilla de latitud/longitud.
 * Funciona sin conexión ni clave de API. Para mapas con callejero hay que
 * sustituir este componente por Google Maps Compose u osmdroid.
 */
@Composable
fun TrackMap(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    onSelect: (TrackPoint) -> Unit = {},
) {
    if (points.isEmpty()) {
        EmptyState("Sin puntos de ruta: inicia el registro de trayectoria")
        return
    }
    val outline = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.primary
    val startColor = Color(0xFF1B8E3C)
    val endColor = Color(0xFFC62828)
    // La forma de la ruta se sigue viendo, pero los ejes darían la posición
    // exacta: con el modo privacidad activo se sustituyen por un texto fijo.
    val privacy = LocalPrivacyMode.current
    val coordLabel = if (privacy) Masks.COORD else null

    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val pad = 16.dp.toPx()
                    val plotWidth = size.width - pad * 2
                    val plotHeight = size.height - pad * 2
                    val latSpan = (maxLat - minLat).takeIf { it > 1e-7 } ?: 1e-7
                    val lonSpan = (maxLon - minLon).takeIf { it > 1e-7 } ?: 1e-7
                    val scale = min(plotWidth / lonSpan, plotHeight / latSpan)
                    val offsetX = pad + (plotWidth - lonSpan * scale) / 2f
                    val offsetY = pad + (plotHeight - latSpan * scale) / 2f
                    val nearest = points.minByOrNull { point ->
                        val x = offsetX + ((point.longitude - minLon) * scale).toFloat()
                        val y = offsetY + ((maxLat - point.latitude) * scale).toFloat()
                        val dx = x - tap.x
                        val dy = y - tap.y
                        dx * dx + dy * dy
                    }
                    if (nearest != null) {
                        val x = offsetX + ((nearest.longitude - minLon) * scale).toFloat()
                        val y = offsetY + ((maxLat - nearest.latitude) * scale).toFloat()
                        val dx = x - tap.x
                        val dy = y - tap.y
                        if (dx * dx + dy * dy <= (24.dp.toPx()).let { it * it }) onSelect(nearest)
                    }
                }
            },
    ) {
        val pad = 16.dp.toPx()
        val plotWidth = size.width - pad * 2
        val plotHeight = size.height - pad * 2
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        val latSpan = (maxLat - minLat).takeIf { it > 1e-7 } ?: 1e-7
        val lonSpan = (maxLon - minLon).takeIf { it > 1e-7 } ?: 1e-7
        val scale = min(plotWidth / lonSpan, plotHeight / latSpan)
        val offsetX = pad + (plotWidth - lonSpan * scale) / 2f
        val offsetY = pad + (plotHeight - latSpan * scale) / 2f

        fun projectX(lon: Double) = (offsetX + ((lon - minLon) * scale)).toFloat()
        fun projectY(lat: Double) = (offsetY + ((maxLat - lat) * scale)).toFloat()

        repeat(5) { index ->
            val y = pad + plotHeight * index / 4f
            drawLine(
                color = outline.copy(alpha = 0.2f),
                start = Offset(pad, y),
                end = Offset(size.width - pad, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            )
            val latitude = maxLat - latSpan * index / 4.0
            drawLabel(
                text = coordLabel ?: String.format(FMT, "%.5f", latitude),
                x = 2.dp.toPx(),
                y = y + 3f,
                paint = textPaint(labelColor, 9.sp.toPx()),
            )
        }
        repeat(5) { index ->
            val x = pad + plotWidth * index / 4f
            drawLine(
                color = outline.copy(alpha = 0.2f),
                start = Offset(x, pad),
                end = Offset(x, size.height - pad),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            )
            val longitude = minLon + lonSpan * index / 4.0
            drawLabel(
                text = coordLabel ?: String.format(FMT, "%.5f", longitude),
                x = x - 22.dp.toPx(),
                y = size.height - 4.dp.toPx(),
                paint = textPaint(labelColor, 9.sp.toPx()),
            )
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = projectX(point.longitude)
            val y = projectY(point.latitude)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = trackColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        val last = points.last()
        last.accuracyMeters?.let { accuracy ->
            drawCircle(
                color = trackColor.copy(alpha = 0.18f),
                radius = (accuracy * scale).toFloat().coerceIn(4f, 120f),
                center = Offset(projectX(last.longitude), projectY(last.latitude)),
            )
        }

        drawCircle(
            color = startColor,
            radius = 6.dp.toPx(),
            center = Offset(projectX(points.first().longitude), projectY(points.first().latitude)),
        )
        drawCircle(
            color = endColor,
            radius = 6.dp.toPx(),
            center = Offset(projectX(last.longitude), projectY(last.latitude)),
        )

        val legendPaint = textPaint(labelColor, 10.sp.toPx())
        drawLabel("Inicio", pad + 6.dp.toPx(), pad + 12.dp.toPx(), legendPaint)
        drawLabel(
            "Fin · ${points.size} puntos",
            pad + 6.dp.toPx(),
            pad + 26.dp.toPx(),
            legendPaint,
        )
    }
}

/** Escala visual de la calidad de una señal en dBm, reutilizada en tarjetas. */
fun rssiToPercentInt(dbm: Int?): Int? {
    if (dbm == null) return null
    return (((dbm + 100) * 100) / 70).coerceIn(0, 100)
}