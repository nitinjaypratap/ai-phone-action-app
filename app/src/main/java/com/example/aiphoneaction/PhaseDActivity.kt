package com.example.aiphoneaction

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PhaseDActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var controller: GameActionController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        statusText = TextView(this).apply {
            text = "Phase D — gameplay control layer"
            textSize = 20f
        }
        root.addView(statusText)

        fun addButton(label: String, click: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { click() }
            })
        }

        addButton("Move joystick") { if (serviceReady()) controller.moveJoystick(45f, 0.9f) }
        addButton("Look camera") { if (serviceReady()) controller.look(80, 30) }
        addButton("Jump") { if (serviceReady()) controller.jump() }
        addButton("Crouch") { if (serviceReady()) controller.crouch() }
        addButton("Prone") { if (serviceReady()) controller.prone() }
        addButton("Reload") { if (serviceReady()) controller.reload() }
        addButton("Heal") { if (serviceReady()) controller.heal() }
        addButton("Shoot") { if (serviceReady()) controller.shoot() }
        addButton("Loot") { if (serviceReady()) controller.loot() }
        addButton("Interact") { if (serviceReady()) controller.interact() }
        addButton("Switch weapon 1") { if (serviceReady()) controller.switchWeapon(1) }
        addButton("Switch weapon 2") { if (serviceReady()) controller.switchWeapon(2) }
        addButton("Throw grenade") { if (serviceReady()) controller.throwGrenade(120f, 0.7f) }
        addButton("Aim + shoot") { if (serviceReady()) controller.aimThenShoot(820, 650) }
        addButton("Move + look + shoot") { if (serviceReady()) controller.moveLookAndShoot(90f, 80, 25) }

        setContentView(root)
    }

    private fun serviceReady(): Boolean {
        val service = TouchAccessibilityService.instance
        if (service == null) {
            statusText.text = "Accessibility service is not active."
            return false
        }
        controller = GameActionController(service)
        return true
    }
}
