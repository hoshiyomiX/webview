package com.hoshiyomi.devbattery

import android.content.Context
import android.content.res.Configuration

/**
 * ThemeManager - System theme detection utility
 * 
 * Detects current system theme (dark/light) using Android's native
 * configuration API. Compatible with Android 10+ dark mode.
 */
class ThemeManager(private val context: Context) {
    
    /**
     * Get current system theme
     * 
     * @return "dark" or "light"
     */
    fun getCurrentTheme(): String {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        
        return when (nightMode) {
            Configuration.UI_MODE_NIGHT_YES -> "dark"
            Configuration.UI_MODE_NIGHT_NO -> "light"
            else -> "dark" // Default to dark if undefined
        }
    }
    
    /**
     * Check if currently in dark mode
     * 
     * @return true if dark mode, false if light mode
     */
    fun isDarkMode(): Boolean {
        return getCurrentTheme() == "dark"
    }
    
    /**
     * Get night mode value (for debugging)
     * 
     * @return Configuration.UI_MODE_NIGHT_YES, UI_MODE_NIGHT_NO, or UI_MODE_NIGHT_UNDEFINED
     */
    fun getNightModeValue(): Int {
        return context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }
}