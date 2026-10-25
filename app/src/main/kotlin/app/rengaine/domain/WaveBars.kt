package app.rengaine.domain

/**
 * Hauteurs des barres de la forme d'onde, calculées comme `waveBars` du
 * site (`Waveform.jsx`) pour que les deux montrent la même chose :
 *
 * - toujours [count] barres, quelle que soit la largeur ;
 * - une barre garde le **pic** de ses tranches (pas la moyenne) ;
 * - hauteur linéaire en dB sur 30 dB sous la tranche la plus forte de la
 *   piste, au moins 0,06 ; un groupe tout en silence (`null`) donne 0 :
 *   pas de barre, un trou.
 *
 * Résultat vide si la piste n'a que du silence.
 */
object WaveBars {
    const val COUNT = 120
    private const val RANGE_DB = 30f
    private const val MIN_HEIGHT = 0.06f

    fun of(levels: List<Float?>, count: Int = COUNT): FloatArray {
        val n = levels.size
        val max = levels.filterNotNull().maxOrNull() ?: return FloatArray(0)
        return FloatArray(count) { i ->
            val from = i * n / count
            val to = maxOf(from + 1, (i + 1) * n / count).coerceAtMost(n)
            var peak: Float? = null
            for (k in from until to) {
                val v = levels[k] ?: continue
                if (peak == null || v > peak) peak = v
            }
            peak?.let { (1f - (max - it) / RANGE_DB).coerceIn(MIN_HEIGHT, 1f) } ?: 0f
        }
    }
}
