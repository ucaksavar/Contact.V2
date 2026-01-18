package com.example.contactv2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class FakeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("EXTRA_NAME") ?: "Bilinmeyen Numara"
        val number = intent.getStringExtra("EXTRA_NUMBER") ?: ""
        
        // Wake up the screen manually to avoid high-priority notification "peeking"
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "ContactV2:TriggerWakeup"
        )
        wakeLock.acquire(3000)

        val callIntent = Intent(context, FakeCallActivity::class.java).apply {
            putExtra("EXTRA_NAME", name)
            putExtra("EXTRA_NUMBER", number)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        
        // Direct start - works if Overlay permission is granted
        context.startActivity(callIntent)
    }
}
