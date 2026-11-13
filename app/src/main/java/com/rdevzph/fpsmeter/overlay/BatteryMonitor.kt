package com.rdevzph.fpsmeter.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Monitors battery telemetry including temperature, charge level percentage,
 * voltage, and live power output in Watts (or current in mA fallback).
 */
class BatteryMonitor(private val context: Context) {

    private var batteryTemp: Float = 0f
    private var batteryLevel: Int = -1
    private var batteryVoltageMv: Int = -1
    private var batteryStatus: Int = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var isRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent?.let { updateFromIntent(it) }
        }
    }

    fun start() {
        if (!isRegistered) {
            val sticky = context.registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            sticky?.let { updateFromIntent(it) }
            isRegistered = true
        }

        if (batteryLevel < 0) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (cap in 0..100) {
                batteryLevel = cap
            }
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
            isRegistered = false
        }
    }

    fun getTemperature(): Float = batteryTemp

    fun getLevel(): Int = batteryLevel

    fun getVoltageMv(): Int = batteryVoltageMv

    fun getRawCurrentNow(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
    }

    fun getRawCurrentAverage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: Int.MIN_VALUE
    }

    /**
     * Calculates real-time power consumption or charging rate in Watts.
     * If the calculated power is zero or voltage is missing, falls back to displaying current in mA.
     */
    fun getPowerText(singleLine: Boolean, showCurrent: Boolean = true): String? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        var rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (rawCurrent == Int.MIN_VALUE || rawCurrent == 0) {
            rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }
        if (rawCurrent == Int.MIN_VALUE) {
            return null
        }

        // Determine current in milliamperes (mA).
        // Standard Android reports current in microamperes (uA), e.g. 350000.
        // Some devices (like many Samsung devices) report directly in mA, e.g. 350.
        val absRaw = abs(rawCurrent)
        val currentMa = when {
            absRaw >= 10_000 -> rawCurrent / 1000.0 // from uA to mA
            absRaw in 1..9_999 -> rawCurrent.toDouble() // already mA
            else -> 0.0
        }

        // Standardize sign: negative = discharging, positive = charging
        val isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        val signedCurrentMa = if (!isCharging && currentMa > 0) {
            -currentMa
        } else if (isCharging && currentMa < 0) {
            -currentMa
        } else {
            currentMa
        }

        // Ensure voltage is known
        if (batteryVoltageMv <= 0) {
            try {
                val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val volt = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
                if (volt > 0) {
                    batteryVoltageMv = volt
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Convert voltage to Volts
        val voltageVolts = when {
            batteryVoltageMv > 20_000 -> batteryVoltageMv / 1_000_000.0 // reported in uV
            batteryVoltageMv in 100..20_000 -> batteryVoltageMv / 1000.0 // reported in mV
            batteryVoltageMv in 1..20 -> batteryVoltageMv.toDouble() // reported in V
            else -> 0.0 // unknown
        }

        // Calculate power in Watts: P = I (Amps) * V (Volts)
        val watts = if (voltageVolts > 0.0 && abs(signedCurrentMa) > 0.0) {
            (abs(signedCurrentMa) / 1000.0) * voltageVolts
        } else {
            0.0
        }

        val space = if (singleLine) "" else " "
        val sign = if (signedCurrentMa < 0) "-" else if (signedCurrentMa > 0 && isCharging) "+" else ""
        val maInt = abs(signedCurrentMa).roundToInt()
        val maFormatted = if (sign.isNotEmpty()) "$sign$maInt${space}mA" else "$maInt${space}mA"

        // If calculated watts is >= 0.05W (displays as at least 0.1W), format as Watts and mA (e.g. -0.7W (-70mA)).
        // Otherwise, if watt output is zero or voltage is missing, fallback to mA only (e.g. -70mA).
        return if (watts >= 0.05) {
            if (showCurrent) {
                String.format(Locale.US, "%s%.1f%sW (%s)", sign, watts, space, maFormatted)
            } else {
                String.format(Locale.US, "%s%.1f%sW", sign, watts, space)
            }
        } else if (maInt >= 1) {
            if (showCurrent) {
                maFormatted
            } else {
                String.format(Locale.US, "0.0%sW", space)
            }
        } else {
            null
        }
    }

    private fun updateFromIntent(intent: Intent) {
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        batteryTemp = temp / 10f

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            batteryLevel = (level * 100) / scale
        }

        val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (volt > 0) {
            batteryVoltageMv = volt
        }

        batteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    }
}

