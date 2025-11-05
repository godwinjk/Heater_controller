package com.godwin.heater.util

import kotlin.math.exp

object TemperatureUtil {

    /**
     * Calculate "feels like" temperature (°C) given air temp in °C and relative humidity in %.
     *
     * Behavior:
     *  - For tempC >= 26.0 (≈79°F) we compute the NOAA Heat Index (convert to °F, apply HI, convert back).
     *  - For tempC < 26.0 we compute a simple apparent temperature using vapor pressure:
     *      T_app = T + 0.33 * e - 4.0   (v = 0 assumed, indoor/calm)
     *    where e = 6.105 * exp(17.27 T / (237.7 + T)) * (RH/100)
     *
     * This avoids using the Fahrenheit-based polynomial directly on Celsius input.
     */
    fun calculateFeelsLike(tempC: Double, humidityPercent: Double): Double {
        val T = tempC
        val RH = humidityPercent.coerceIn(0.0, 100.0)

        return if (T >= 26.0) {
            // Use NOAA Heat Index (works in °F) -> convert °C to °F, compute HI, convert back
            val tf = celsiusToFahrenheit(T)
            val hiF = heatIndexFahrenheit(tf, RH)
            fahrenheitToCelsius(hiF)
        } else {
            // Use apparent temperature (handles cooler temps better)
            apparentTempCelsius(T, RH)
        }
    }

    private fun celsiusToFahrenheit(c: Double) = c * 9.0 / 5.0 + 32.0
    private fun fahrenheitToCelsius(f: Double) = (f - 32.0) * 5.0 / 9.0

    // NOAA Heat Index polynomial (expects T in °F and RH in percent)
    private fun heatIndexFahrenheit(Tf: Double, RH: Double): Double {
        val T = Tf
        val R = RH
        val hi = (-42.379 +
                2.04901523 * T +
                10.14333127 * R -
                0.22475541 * T * R -
                6.83783e-3 * T * T -
                5.481717e-2 * R * R +
                1.22874e-3 * T * T * R +
                8.5282e-4 * T * R * R -
                1.99e-6 * T * T * R * R)

        // Corrections from NOAA for extreme humidity ranges (optional but good)
        var hiAdjusted = hi
        if (R < 13.0 && T in 80.0..112.0) {
            val adjustment =
                ((13.0 - R) / 4.0) * Math.sqrt((17.0 - kotlin.math.abs(T - 95.0)) / 17.0)
            hiAdjusted -= adjustment
        } else if (R > 85.0 && T in 80.0..87.0) {
            val adjustment = ((R - 85.0) / 10.0) * ((87.0 - T) / 5.0)
            hiAdjusted += adjustment
        }
        return hiAdjusted
    }

    // Apparent temperature approximation (for cooler temps) in °C
    private fun apparentTempCelsius(Tc: Double, RH: Double): Double {
        // vapor pressure e (hPa)
        val e = 6.105 * exp((17.27 * Tc) / (237.7 + Tc)) * (RH / 100.0)
        val v = 0.0 // assumed wind speed (m/s). set to a value if you have wind.
        return Tc + 0.33 * e - 0.70 * v - 4.0
    }
}
