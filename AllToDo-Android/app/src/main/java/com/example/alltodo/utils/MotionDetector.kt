package com.example.alltodo.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.alltodo.utils.OptimizationLogger
import com.example.alltodo.utils.LogType
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Interface for callback
interface MotionListener {
    fun onMotionStateChanged(isMoving: Boolean)
}

class MotionDetector(private val context: Context, private val listener: MotionListener) {
    private val TAG = "MotionDetector"
    private var isTracking = false
    
    // Using Transition API for battery efficiency (instead of continuous updates)
    // We want to know when user STARTS moving (STILL -> WALKING/RUNNING/VEHICLE)
    // and when user STOPS moving (WALKING/RUNNING/VEHICLE -> STILL)

    private val transitions = listOf(
        // Still -> Moving
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            .build(),
        // Moving -> Still
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()
    )

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(MotionTransitionReceiver.ACTION_PROCESS_UPDATES)
        // Intent explicit targeting is safer
        intent.setPackage(context.packageName)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private val receiver = MotionTransitionReceiver(listener)

    fun start() {
        if (isTracking) return
        
        // Register Broadcast Receiver
        // In Android 14+ specific export flags might be needed, using Context.RECEIVER_NOT_EXPORTED if possible
        // but for implicit/local broadcasts standard register is ok or explicit intent.
        // Since we use explicit intent with setPackage, it's safer.
        // For dynamic receiver:
        context.registerReceiver(receiver, IntentFilter(MotionTransitionReceiver.ACTION_PROCESS_UPDATES), Context.RECEIVER_NOT_EXPORTED)

        val request = ActivityTransitionRequest(transitions)
        val client = ActivityRecognition.getClient(context)

        client.requestActivityTransitionUpdates(request, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "API Success: Activity Recognition Started")
                isTracking = true
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "API Failed: Activity Recognition", e)
                 CoroutineScope(Dispatchers.IO).launch {
                    OptimizationLogger.log(context, LogType.ERROR, "MotionDetector Start Failed: ${e.message}")
                }
            }
    }

    fun stop() {
        if (!isTracking) return
        
        // Unregister
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {}

        val client = ActivityRecognition.getClient(context)
        client.removeActivityTransitionUpdates(pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "API Success: Activity Recognition Stopped")
                isTracking = false
            }
            .addOnFailureListener { e ->
                 Log.e(TAG, "API Failed: Stop Activity Recognition", e)
            }
    }
}

class MotionTransitionReceiver(private val listener: MotionListener? = null) : BroadcastReceiver() {
    companion object {
        const val ACTION_PROCESS_UPDATES = "com.example.alltodo.action.PROCESS_UPDATES"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return
            
            for (event in result.transitionEvents) {
                // Log Event
                CoroutineScope(Dispatchers.IO).launch {
                    val typeStr = getActivityString(event.activityType)
                    val transStr = getTransitionString(event.transitionType)
                    OptimizationLogger.log(context, LogType.MOTION_CHANGE, "$typeStr -> $transStr")
                }

                // Logic
                if (event.activityType == DetectedActivity.STILL) {
                     if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                         // Entered STILL -> Not Moving
                         listener?.onMotionStateChanged(isMoving = false)
                     } else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                         // Exited STILL -> Start Moving
                         listener?.onMotionStateChanged(isMoving = true)
                     }
                }
            }
        }
    }

    private fun getActivityString(type: Int): String {
        return when (type) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "($type)"
        }
    }

    private fun getTransitionString(type: Int): String {
        return when (type) {
             ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
             ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
             else -> "UNKNOWN"
        }
    }
}
