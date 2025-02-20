package com.godwin.heater.util

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }