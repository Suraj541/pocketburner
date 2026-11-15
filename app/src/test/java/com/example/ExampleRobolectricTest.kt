package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.engine.UsbOtgManagerService
import com.example.engine.WifiOtaFlashingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PocketBurner", appName)
  }

  @Test
  fun `usb otg manager service binds and tracks running state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = AppContainer.getInstance(context)
    val controller = Robolectric.buildService(UsbOtgManagerService::class.java).create()
    val service = controller.get()

    assertTrue(container.transportManager.usbServiceRunning.value)
    assertTrue(service.scanSerialDevices().isEmpty())

    service.stopUsbService()
    assertFalse(container.transportManager.usbServiceRunning.value)
    controller.destroy()
  }

  @Test
  fun `wifi ota flashing service manifest and lifecycle tracking`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val serviceInfo = context.packageManager.getServiceInfo(
      ComponentName(context, WifiOtaFlashingService::class.java),
      PackageManager.GET_META_DATA,
    )
    assertNotNull(serviceInfo)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, serviceInfo.foregroundServiceType)
    }

    val container = AppContainer.getInstance(context)
    val controller = Robolectric.buildService(WifiOtaFlashingService::class.java).create()
    val service = controller.get()
    val binder = service.onBind(null) as WifiOtaFlashingService.WifiOtaBinder
    assertEquals(service, binder.getService())
    assertTrue(container.transportManager.wifiOtaServiceRunning.value)

    service.cancelOtaTransfer()
    assertEquals("Cancelled", container.transportManager.wifiOtaState.value.statusText)

    service.stopOtaService()
    assertFalse(container.transportManager.wifiOtaServiceRunning.value)
    controller.destroy()
  }
}
