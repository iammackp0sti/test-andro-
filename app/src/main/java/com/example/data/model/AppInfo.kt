package com.example.data.model

import android.graphics.drawable.Drawable

/**
 * UI representation of an installed application with its current configuration.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isMuted: Boolean = false,
    val isAlwaysAllowed: Boolean = false,
    val isSystemApp: Boolean = false,
    val categories: List<String> = emptyList()
)

/**
 * Helper class representing a category with count of apps.
 */
data class CategoryWithCount(
    val id: Long,
    val name: String,
    val appCount: Int,
    val appPackageNames: List<String> = emptyList()
)
