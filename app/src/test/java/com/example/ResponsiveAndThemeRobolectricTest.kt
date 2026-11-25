package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode
import com.example.ui.components.EmptyStateView
import com.example.ui.components.VybGoogleCircleButton
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.VYBTheme
import com.example.ui.theme.VybExtendedColors
import com.example.ui.theme.VybTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


class ResponsiveAndThemeRobolectricTest: BaseRobolectricTest() {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `light and amoled themes enforce 4-tier surface hierarchy and distinct accents`() {
        var lightExtended: VybExtendedColors? = null
        var lightBg: androidx.compose.ui.graphics.Color? = null
        var lightSurface: androidx.compose.ui.graphics.Color? = null

        var darkExtended: VybExtendedColors? = null
        var darkBg: androidx.compose.ui.graphics.Color? = null
        var darkSurface: androidx.compose.ui.graphics.Color? = null

        composeRule.setContent {
            VYBTheme(themeMode = ThemeMode.LIGHT) {
                lightExtended = LocalVybColors.current
                lightBg = MaterialTheme.colorScheme.background
                lightSurface = MaterialTheme.colorScheme.surface
            }
            VYBTheme(themeMode = ThemeMode.AMOLED) {
                darkExtended = LocalVybColors.current
                darkBg = MaterialTheme.colorScheme.background
                darkSurface = MaterialTheme.colorScheme.surface
            }
        }
        composeRule.waitForIdle()

        val lExt = lightExtended!!
        val dExt = darkExtended!!

        // Light theme distinct 4-tier hierarchy: background -> surface -> subtleSurface -> nestedSurface
        assertEquals(false, lExt.isDark)
        assertEquals(2.dp, lExt.cardElevationDp)
        assertNotEquals(lightBg, lightSurface)
        assertNotEquals(lightSurface, lExt.subtleSurface)
        assertNotEquals(lightSurface, lExt.nestedSurface)
        assertNotEquals(lExt.subtleSurface, lExt.nestedSurface)

        // AMOLED Dark theme distinct 4-tier hierarchy with 0.dp elevation and distinct dark accents
        assertEquals(true, dExt.isDark)
        assertEquals(0.dp, dExt.cardElevationDp)
        assertNotEquals(darkBg, darkSurface)
        assertNotEquals(darkSurface, dExt.subtleSurface)
        assertNotEquals(darkSurface, dExt.nestedSurface)
        assertNotEquals(lExt.linkAccent, dExt.linkAccent)
        assertNotEquals(lExt.missedAccent, dExt.missedAccent)
    }

    @Test
    fun `typography pairs outfit display and plus jakarta body cleanly`() {
        assertEquals(OutfitFontFamily, VybTypography.displayLarge.fontFamily)
        assertEquals(OutfitFontFamily, VybTypography.headlineSmall.fontFamily)
        assertEquals(OutfitFontFamily, VybTypography.titleLarge.fontFamily)
        assertEquals(PlusJakartaFontFamily, VybTypography.titleMedium.fontFamily)
        assertEquals(PlusJakartaFontFamily, VybTypography.bodyLarge.fontFamily)
        assertEquals(PlusJakartaFontFamily, VybTypography.bodyMedium.fontFamily)
        assertEquals(PlusJakartaFontFamily, VybTypography.labelMedium.fontFamily)
    }

    @Test
    fun `circular google sign in button meets touch target accessibility and click handling`() {
        var clicks = 0
        composeRule.setContent {
            VYBTheme(themeMode = ThemeMode.LIGHT) {
                androidx.compose.foundation.layout.Column {
                    VybGoogleCircleButton(
                        onClick = { clicks++ },
                        enabled = true,
                        contentDescription = "Iniciar sesión con Google",
                        testTag = "google_circle_active"
                    )
                    VybGoogleCircleButton(
                        onClick = {},
                        enabled = false,
                        isLoading = true,
                        contentDescription = "Crear cuenta con Google",
                        testTag = "google_circle_loading"
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("google_circle_active")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNode(hasContentDescription("Iniciar sesión con Google")).assertIsDisplayed()
        assertEquals(1, clicks)

        composeRule.onNodeWithTag("google_circle_loading")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun `window size class breakpoints classify compact medium and expanded properly`() {
        val compact = WindowSizeClass.calculateFromSize(DpSize(412.dp, 915.dp))
        val medium = WindowSizeClass.calculateFromSize(DpSize(700.dp, 960.dp))
        val expanded = WindowSizeClass.calculateFromSize(DpSize(1080.dp, 840.dp))

        assertEquals(WindowWidthSizeClass.Compact, compact.widthSizeClass)
        assertEquals(WindowWidthSizeClass.Medium, medium.widthSizeClass)
        assertEquals(WindowWidthSizeClass.Expanded, expanded.widthSizeClass)

        var actionClicked = false
        composeRule.setContent {
            VYBTheme(themeMode = ThemeMode.LIGHT) {
                EmptyStateView(
                    title = "Selecciona un chat",
                    subtitle = "Elige una conversación de la lista o busca contactos para empezar a vibrar.",
                    actionLabel = "Buscar contactos",
                    onAction = { actionClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Selecciona un chat").assertIsDisplayed()
        composeRule.onNodeWithText("Buscar contactos").assertIsDisplayed().performClick()
        assertTrue(actionClicked)
    }
}
