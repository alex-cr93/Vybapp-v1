package com.example.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class VybPermissionGroup(
    val title: String,
    val description: String,
    val testTagSuffix: String
) {
    CONTACTS(
        title = "Contactos",
        description = "Encuentra y sincroniza tus contactos de confianza al instante.",
        testTagSuffix = "contacts"
    ),
    CALLS(
        title = "Llamadas, audio y video",
        description = "Habilita micrófono, cámara y gestión segura de llamadas P2P y grupales.",
        testTagSuffix = "calls"
    ),
    NOTIFICATIONS(
        title = "Notificaciones en tiempo real",
        description = "Recibe mensajes, bips, cobros SINPE y llamadas aunque la app esté cerrada.",
        testTagSuffix = "notifications"
    ),
    LOCATION(
        title = "Ubicación y mapa en vivo",
        description = "Comparte tu punto o ubicación GPS en tiempo real dentro de tus chats.",
        testTagSuffix = "location"
    ),
    BATTERY(
        title = "Segundo plano sin restricciones",
        description = "Evita que Android suspenda mensajes entrantes y tonos de llamada.",
        testTagSuffix = "battery"
    )
}

data class VybPermissionsSnapshot(
    val contactsGranted: Boolean,
    val callsGranted: Boolean,
    val notificationsGranted: Boolean,
    val locationGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val batteryRequired: Boolean,
    val permanentlyDeniedPermissions: Set<String>
) {
    val allRuntimeGranted: Boolean
        get() = contactsGranted && callsGranted && notificationsGranted && locationGranted

    val allRequiredGranted: Boolean
        get() = allRuntimeGranted && (!batteryRequired || batteryUnrestricted)

    val grantedStepsCount: Int
        get() = listOf(
            contactsGranted,
            callsGranted,
            notificationsGranted,
            locationGranted,
            if (batteryRequired) batteryUnrestricted else true
        ).count { it }

    val totalStepsCount: Int = 5

    fun isGroupSatisfied(group: VybPermissionGroup): Boolean = when (group) {
        VybPermissionGroup.CONTACTS -> contactsGranted
        VybPermissionGroup.CALLS -> callsGranted
        VybPermissionGroup.NOTIFICATIONS -> notificationsGranted
        VybPermissionGroup.LOCATION -> locationGranted
        VybPermissionGroup.BATTERY -> !batteryRequired || batteryUnrestricted
    }

    fun isGroupPermanentlyDenied(group: VybPermissionGroup): Boolean {
        if (isGroupSatisfied(group)) return false
        return when (group) {
            VybPermissionGroup.CONTACTS -> Manifest.permission.READ_CONTACTS in permanentlyDeniedPermissions
            VybPermissionGroup.CALLS -> listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_PHONE_STATE
            ).any { it in permanentlyDeniedPermissions }
            VybPermissionGroup.NOTIFICATIONS ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    Manifest.permission.POST_NOTIFICATIONS in permanentlyDeniedPermissions
            VybPermissionGroup.LOCATION -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).all { it in permanentlyDeniedPermissions }
            VybPermissionGroup.BATTERY -> false
        }
    }
}

object VybPermissionsHelper {

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun runtimePermissionsForGroup(group: VybPermissionGroup): List<String> = when (group) {
        VybPermissionGroup.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
        VybPermissionGroup.CALLS -> listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE
        )
        VybPermissionGroup.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
        VybPermissionGroup.LOCATION -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        VybPermissionGroup.BATTERY -> emptyList()
    }

    fun missingPermissionsForGroup(context: Context, group: VybPermissionGroup): Array<String> {
        return when (group) {
            VybPermissionGroup.LOCATION -> {
                val fine = isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                val coarse = isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (fine || coarse) {
                    emptyArray()
                } else {
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                }
            }
            else -> runtimePermissionsForGroup(group)
                .filterNot { isPermissionGranted(context, it) }
                .toTypedArray()
        }
    }

    fun canResolveBatteryOptimizationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = context.packageManager
        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val detailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        return batteryIntent.resolveActivity(pm) != null || detailsIntent.resolveActivity(pm) != null
    }

    fun snapshot(
        context: Context,
        activity: Activity?,
        requestedHistory: Set<String>
    ): VybPermissionsSnapshot {
        val contactsOk = isPermissionGranted(context, Manifest.permission.READ_CONTACTS)
        val callsOk = runtimePermissionsForGroup(VybPermissionGroup.CALLS)
            .all { isPermissionGranted(context, it) }
        val notifOk = NotificationHelper.hasNotificationPermission(context)
        val locationOk = isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val batteryRequired = canResolveBatteryOptimizationSettings(context)
        val batteryOk = NotificationHelper.isIgnoringBatteryOptimizations(context)

        val allRuntime = buildList {
            addAll(runtimePermissionsForGroup(VybPermissionGroup.CONTACTS))
            addAll(runtimePermissionsForGroup(VybPermissionGroup.CALLS))
            addAll(runtimePermissionsForGroup(VybPermissionGroup.NOTIFICATIONS))
            addAll(runtimePermissionsForGroup(VybPermissionGroup.LOCATION))
        }

        val permanentlyDenied = if (activity == null) {
            emptySet()
        } else {
            allRuntime.filterTo(mutableSetOf()) { perm ->
                !isPermissionGranted(context, perm) &&
                    perm in requestedHistory &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
        }

        return VybPermissionsSnapshot(
            contactsGranted = contactsOk,
            callsGranted = callsOk,
            notificationsGranted = notifOk,
            locationGranted = locationOk,
            batteryUnrestricted = batteryOk,
            batteryRequired = batteryRequired,
            permanentlyDeniedPermissions = permanentlyDenied
        )
    }

    fun nextMissingGroup(context: Context): VybPermissionGroup? {
        if (!isPermissionGranted(context, Manifest.permission.READ_CONTACTS)) {
            return VybPermissionGroup.CONTACTS
        }
        if (missingPermissionsForGroup(context, VybPermissionGroup.CALLS).isNotEmpty()) {
            return VybPermissionGroup.CALLS
        }
        if (!NotificationHelper.hasNotificationPermission(context)) {
            return VybPermissionGroup.NOTIFICATIONS
        }
        if (missingPermissionsForGroup(context, VybPermissionGroup.LOCATION).isNotEmpty()) {
            return VybPermissionGroup.LOCATION
        }
        if (canResolveBatteryOptimizationSettings(context) &&
            !NotificationHelper.isIgnoringBatteryOptimizations(context)
        ) {
            return VybPermissionGroup.BATTERY
        }
        return null
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        NotificationHelper.openBatteryOptimizationSettings(context)
    }
}
