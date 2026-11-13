package com.example.aiphoneaction

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class GameActionController(private val service: TouchAccessibilityService) {
    fun moveJoystick(directionDeg: Float, intensity: Float, centerX: Int = 180, centerY: Int = 900) {
        val radius = 180f * intensity.coerceIn(0f, 1f)
        val radians = Math.toRadians(directionDeg.toDouble())
        val dx = (cos(radians) * radius).toFloat()
        val dy = (sin(radians) * radius).toFloat()
        val targetX = (centerX + dx).toInt()
        val targetY = (centerY + dy).toInt()
        service.swipe(centerX, centerY, targetX, targetY) { }
    }

    fun look(deltaX: Int, deltaY: Int, startX: Int = 860, startY: Int = 500, durationMs: Long = 110L) {
        val endX = (startX + deltaX).coerceIn(0, 1080)
        val endY = (startY + deltaY).coerceIn(0, 2000)
        service.swipe(startX, startY, endX, endY) { }
    }

    fun jump() = service.tap(920, 1620) { }
    fun crouch() = service.tap(770, 1620) { }
    fun prone() = service.longPress(770, 1620, 900L) { }
    fun reload() = service.tap(1010, 1500) { }
    fun heal() = service.tap(680, 1700) { }
    fun shoot() = service.tap(1010, 1700) { }
    fun loot() = service.tap(900, 1330) { }
    fun interact() = service.tap(900, 1180) { }
    fun switchWeapon(slot: Int) {
        val x = when (slot) { 1 -> 780; 2 -> 860; else -> 700 }
        service.tap(x, 1780) { }
    }

    fun aimThenShoot(targetX: Int, targetY: Int) {
        service.swipe(860, 500, targetX, targetY) { }
        service.tap(targetX, targetY) { }
    }

    fun moveLookAndShoot(moveDir: Float, lookDeltaX: Int, lookDeltaY: Int) {
        moveJoystick(moveDir, 0.8f)
        look(lookDeltaX, lookDeltaY)
        shoot()
    }

    fun openInventory() = service.tap(1040, 300) { }
    fun openMap() = service.tap(180, 250) { }
    fun useVehicle() = service.tap(900, 900) { }
    fun throwGrenade(directionDeg: Float, power: Float) {
        val targetX = 860 + (cos(Math.toRadians(directionDeg.toDouble())) * 180 * power).toInt()
        val targetY = 1500 + (sin(Math.toRadians(directionDeg.toDouble())) * 180 * power).toInt()
        service.swipe(700, 1500, targetX, targetY) { }
    }
}
