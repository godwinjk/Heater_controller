package com.godwin.heater

import com.godwin.heater.util.TemperatureUtil
import org.junit.Test

class TemperatureUtilTest {
    @Test
    fun calculateFeelsLike() {

        var result = TemperatureUtil.calculateFeelsLike(40.0, 20.0)
        println(result)
        // should feel slightly cooler than air temp
        assert(result < 40.0)

        result = TemperatureUtil.calculateFeelsLike(35.0, 90.0)
        println(result)
        // should feel slightly cooler than air temp
        assert(result > 35.0)

    }

    @Test
    fun `test bounds consistency`() {
        // Ensure monotonically increasing with temp
        val cold = TemperatureUtil.calculateFeelsLike(10.0, 50.0)
        val warm = TemperatureUtil.calculateFeelsLike(20.0, 50.0)
        assert(warm > cold)
    }
}