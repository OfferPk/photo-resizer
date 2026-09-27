package com.offerpk.photoresizer.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.offerpk.photoresizer.AppViewModel
import com.offerpk.photoresizer.ads.AdsManager
import com.offerpk.photoresizer.ui.components.BusyOverlay
import com.offerpk.photoresizer.ui.screens.AboutScreen
import com.offerpk.photoresizer.ui.screens.CompressScreen
import com.offerpk.photoresizer.ui.screens.HomeScreen
import com.offerpk.photoresizer.ui.screens.PassportScreen
import com.offerpk.photoresizer.ui.screens.ResizeScreen
import com.offerpk.photoresizer.ui.screens.ResultScreen
import com.offerpk.photoresizer.ui.screens.SignatureScreen

object Routes {
    const val HOME = "home"
    const val COMPRESS = "compress"
    const val RESIZE = "resize"
    const val PASSPORT = "passport"
    const val SIGNATURE = "signature"
    const val RESULT = "result"
    const val ABOUT = "about"
}

@Composable
fun AppNavigation(vm: AppViewModel) {
    val nav = rememberNavController()
    val activity = LocalActivity.current
    var pendingRoute by rememberSaveable { mutableStateOf<String?>(null) }

    // System Photo Picker only – no storage/media permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val route = pendingRoute
        pendingRoute = null
        if (uri != null) vm.load(uri) {
            if (route != null && nav.currentDestination?.route != route) nav.navigate(route)
        }
    }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    fun pickFor(route: String?) { pendingRoute = route; picker.launch(request) }
    val changePhoto = { pickFor(null) }

    fun leaveResult(toHome: Boolean) {
        val go = {
            if (toHome) nav.popBackStack(Routes.HOME, inclusive = false) else nav.popBackStack()
            Unit
        }
        if (vm.exported && activity != null) {
            vm.exported = false
            AdsManager.maybeShowInterstitialAfterExport(activity, go)
        } else go()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(onTool = { pickFor(it) }, onAbout = { nav.navigate(Routes.ABOUT) })
            }
            composable(Routes.COMPRESS) { CompressScreen(vm, onBack = { nav.popBackStack() }, onPick = changePhoto, onResult = { nav.navigate(Routes.RESULT) }) }
            composable(Routes.RESIZE) { ResizeScreen(vm, onBack = { nav.popBackStack() }, onPick = changePhoto, onResult = { nav.navigate(Routes.RESULT) }) }
            composable(Routes.PASSPORT) { PassportScreen(vm, onBack = { nav.popBackStack() }, onPick = changePhoto, onResult = { nav.navigate(Routes.RESULT) }) }
            composable(Routes.SIGNATURE) { SignatureScreen(vm, onBack = { nav.popBackStack() }, onPick = changePhoto, onResult = { nav.navigate(Routes.RESULT) }) }
            composable(Routes.RESULT) { ResultScreen(vm, onBack = { leaveResult(false) }, onDone = { leaveResult(true) }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
        }
        if (vm.loading) BusyOverlay("Opening photo…")
        vm.error?.let { msg ->
            AlertDialog(
                onDismissRequest = { vm.error = null },
                confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } },
                title = { Text("Oops") },
                text = { Text(msg) },
            )
        }
    }
}
