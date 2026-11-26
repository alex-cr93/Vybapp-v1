package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.PreferencesStore
import com.example.core.VybPermissionGroup
import com.example.core.VybPermissionsHelper
import com.example.core.VybPermissionsSnapshot
import com.example.data.model.ChatMessage
import com.example.ui.screens.PermissionsOnboardingScreen
import com.example.ui.viewmodel.AssistantViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows

class PermissionsAndAiHardeningRobolectricTest : BaseRobolectricTest() {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `permissions onboarding screen displays all 5 groups and permanent denial dialog`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesStore(context)
        var signOutClicked = false

        val snapshot = VybPermissionsSnapshot(
            contactsGranted = false,
            callsGranted = false,
            notificationsGranted = false,
            locationGranted = false,
            batteryUnrestricted = false,
            batteryRequired = true,
            permanentlyDeniedPermissions = setOf(Manifest.permission.READ_CONTACTS)
        )

        composeRule.setContent {
            PermissionsOnboardingScreen(
                snapshot = snapshot,
                preferencesStore = prefs,
                onRefreshPermissions = {},
                onSignOut = { signOutClicked = true }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("permissions_onboarding_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("permission_card_contacts").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("permission_card_calls").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("permission_card_notifications").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("permission_card_location").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("permission_card_battery").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("permissions_continue_button").assertIsDisplayed()
        composeRule.onNodeWithTag("permissions_signout_button").assertIsDisplayed()

        // Tapping a permanently denied card opens the Settings alert dialog
        composeRule.onNodeWithTag("permission_card_contacts").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("permissions_open_settings_button").assertIsDisplayed()
        composeRule.onNodeWithText("Cerrar").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("permissions_signout_button").performClick()
        assertTrue(signOutClicked)
    }

    @Test
    fun `vyb permissions helper tracks sequential runtime and battery grants`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadowPm = Shadows.shadowOf(pm)

        shadowApp.denyPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        shadowPm.setIgnoringBatteryOptimizations(app.packageName, false)

        val initial = VybPermissionsHelper.snapshot(app, null, emptySet())
        assertFalse(initial.allRuntimeGranted)
        assertFalse(initial.allRequiredGranted)
        assertEquals(VybPermissionGroup.CONTACTS, VybPermissionsHelper.nextMissingGroup(app))

        shadowApp.grantPermissions(Manifest.permission.READ_CONTACTS)
        assertEquals(VybPermissionGroup.CALLS, VybPermissionsHelper.nextMissingGroup(app))

        shadowApp.grantPermissions(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE
        )
        assertEquals(VybPermissionGroup.NOTIFICATIONS, VybPermissionsHelper.nextMissingGroup(app))

        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(VybPermissionGroup.LOCATION, VybPermissionsHelper.nextMissingGroup(app))

        shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val afterRuntime = VybPermissionsHelper.snapshot(app, null, emptySet())
        assertTrue(afterRuntime.allRuntimeGranted)

        if (VybPermissionsHelper.canResolveBatteryOptimizationSettings(app)) {
            assertEquals(VybPermissionGroup.BATTERY, VybPermissionsHelper.nextMissingGroup(app))
            assertFalse(afterRuntime.allRequiredGranted)
            shadowPm.setIgnoringBatteryOptimizations(app.packageName, true)
        }

        val completed = VybPermissionsHelper.snapshot(app, null, emptySet())
        assertTrue(completed.allRequiredGranted)
        assertNull(VybPermissionsHelper.nextMissingGroup(app))
    }

    @Test
    fun `assistant transcript sanitizer strips control chars and skips deleted messages`() {
        val longText = "A".repeat(700)
        val messages = listOf(
            ChatMessage(
                id = "m1",
                senderName = "Ana\n\tMaría\u0000",
                text = "Hola\r\nmundo\t\u0007!"
            ),
            ChatMessage(
                id = "m2",
                senderName = "Luis",
                text = "Mensaje borrado",
                deletedForEveryone = true
            ),
            ChatMessage(
                id = "m3",
                senderName = "   ",
                text = "   "
            ),
            ChatMessage(
                id = "m4",
                senderName = "",
                text = longText
            )
        )

        val transcript = AssistantViewModel.buildSanitizedTranscript(messages)
        val lines = transcript.lines()
        assertEquals(2, lines.size)
        assertEquals("Ana María: Hola mundo !", lines[0])
        assertTrue(lines[1].startsWith("Usuario: "))
        assertEquals("Usuario: " + "A".repeat(500), lines[1])
    }
}
