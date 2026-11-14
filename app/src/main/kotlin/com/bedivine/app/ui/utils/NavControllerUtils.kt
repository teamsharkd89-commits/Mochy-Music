// NavControllerUtils.kt

package com.bedivine.app.ui.utils

import androidx.navigation.NavController
import com.bedivine.app.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
