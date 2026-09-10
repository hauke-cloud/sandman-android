package cloud.hauke.sandman.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cloud.hauke.sandman.AppContainer
import cloud.hauke.sandman.ui.detail.DeviceDetailScreen
import cloud.hauke.sandman.ui.detail.DeviceDetailViewModel
import cloud.hauke.sandman.ui.devices.DeviceListScreen
import cloud.hauke.sandman.ui.devices.DeviceListViewModel
import cloud.hauke.sandman.ui.settings.SettingsScreen
import cloud.hauke.sandman.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
object DevicesRoute

@Serializable
data class DeviceRoute(val name: String)

@Serializable
object SettingsRoute

@Composable
fun SandmanApp(container: AppContainer) {
  val navController = rememberNavController()

  NavHost(navController = navController, startDestination = DevicesRoute) {
    composable<DevicesRoute> {
      val viewModel: DeviceListViewModel = viewModel(factory = SandmanViewModelFactory(container))
      DeviceListScreen(
        viewModel = viewModel,
        onOpenDevice = { name -> navController.navigate(DeviceRoute(name)) },
        onOpenSettings = { navController.navigate(SettingsRoute) },
      )
    }

    composable<DeviceRoute> { entry ->
      val name = entry.toRoute<DeviceRoute>().name
      // Keyed by name, so opening a second device does not hand back the
      // first one's view model from the back stack entry's store.
      val viewModel: DeviceDetailViewModel = viewModel(
        key = "device:$name",
        factory = SandmanViewModelFactory(container, name),
      )
      DeviceDetailScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
      )
    }

    composable<SettingsRoute> {
      val viewModel: SettingsViewModel = viewModel(factory = SandmanViewModelFactory(container))
      SettingsScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
      )
    }
  }
}
