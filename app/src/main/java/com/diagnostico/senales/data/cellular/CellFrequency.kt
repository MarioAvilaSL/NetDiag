package com.diagnostico.senales.data.cellular

/**
 * Conversión de EARFCN (LTE) y NR-ARFCN (5G) a megahercios downlink.
 *
 * Son las aproximaciones estándar del 3GPP: Android no publica la frecuencia en
 * MHz de la celda, solo el índice, así que el valor derivado se indica como
 * aproximado en la interfaz.
 */
object CellFrequency {

    private data class LteBandSpec(val range: IntRange, val dlStartMhz: Double, val stepMhz: Double)

    private val lteBands = listOf(
        LteBandSpec(0..599, 2110.1, 0.1),
        LteBandSpec(600..1199, 1930.1, 0.1),
        LteBandSpec(1200..1949, 1805.1, 0.1),
        LteBandSpec(1950..2399, 2110.1, 0.1),
        LteBandSpec(2400..2749, 869.1, 0.1),
        LteBandSpec(2750..3449, 2620.1, 0.1),
        LteBandSpec(3450..3999, 925.1, 0.1),
        LteBandSpec(4000..4549, 729.1, 0.1),
        LteBandSpec(4550..4799, 746.1, 0.1),
        LteBandSpec(4800..5249, 734.1, 0.1),
        LteBandSpec(5250..5549, 860.1, 0.1),
        LteBandSpec(5550..6149, 875.1, 0.1),
        LteBandSpec(6150..6449, 791.1, 0.1),
        LteBandSpec(8040..8689, 1930.1, 0.1),
        LteBandSpec(8690..9039, 2010.1, 0.1),
        LteBandSpec(9230..9659, 758.1, 0.1),
        LteBandSpec(9660..9769, 717.1, 0.1),
        LteBandSpec(9920..10359, 1452.1, 0.1),
        LteBandSpec(10120..10359, 2010.1, 0.1),
        LteBandSpec(37750..38249, 2570.1, 0.1),
        LteBandSpec(38250..38649, 1880.1, 0.1),
        LteBandSpec(38650..39649, 2300.1, 0.1),
        LteBandSpec(39650..41539, 2496.1, 0.1),
        LteBandSpec(41540..43589, 3400.1, 0.15),
        LteBandSpec(43590..45539, 3600.1, 0.15),
        LteBandSpec(207600..208399, 5150.1, 0.05),
        LteBandSpec(636667..646666, 3550.1, 0.1),
        LteBandSpec(66436..67335, 2110.1, 0.1),
        LteBandSpec(617000..652555, 617.1, 0.1),
    )

    fun lteDownlinkMhz(earfcn: Int): Double? {
        if (earfcn < 0) return null
        val spec = lteBands.firstOrNull { earfcn in it.range } ?: return null
        return spec.dlStartMhz + spec.stepMhz * earfcn
    }

    fun nrDownlinkMhz(nrarfcn: Int): Double? = when {
        nrarfcn < 0 -> null
        nrarfcn <= 201_000 -> 2110.1 + 0.005 * nrarfcn
        nrarfcn <= 207_000 -> 2166.2 + 0.002 * (nrarfcn - 201_000)
        nrarfcn <= 255_000 -> 2200.1 + 0.005 * (nrarfcn - 207_000)
        nrarfcn <= 256_000 -> 2566.2 + 0.002 * (nrarfcn - 255_000)
        nrarfcn in 24_250..27_500 -> 22_000.1 + 0.01 * (nrarfcn - 24_250)
        nrarfcn in 37_000..44_000 -> 24_960.1 + 0.01 * (nrarfcn - 37_000)
        nrarfcn in 600_000..644_999 -> 25_500.1 + 0.002 * (nrarfcn - 600_000)
        else -> null
    }
}