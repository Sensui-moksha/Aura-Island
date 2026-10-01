/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.service

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import com.moksha.auraisland.data.AuraNotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SystemEventReceiverTest {

    @Test
    fun testPowerDisconnectedRemovesBatteryNotificationWhenNormal() {
        val repo = mockk<AuraNotificationRepository>(relaxed = true)
        val receiver = SystemEventReceiver(repo)
        
        val context = mockk<Context>()
        val pm = mockk<PowerManager>(relaxed = true)
        every { pm.isPowerSaveMode } returns false
        every { context.getSystemService(Context.POWER_SERVICE) } returns pm

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_POWER_DISCONNECTED
        every { intent.hasExtra(BatteryManager.EXTRA_LEVEL) } returns true
        every { intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 80
        every { intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        
        receiver.onReceive(context, intent)
        
        verify { repo.removeNotification("system_battery") }
    }

    @Test
    fun testBatteryLowPostsLowBatteryNotification() {
        val repo = mockk<AuraNotificationRepository>(relaxed = true)
        val receiver = SystemEventReceiver(repo)
        
        val context = mockk<Context>()
        val pm = mockk<PowerManager>(relaxed = true)
        every { pm.isPowerSaveMode } returns false
        every { context.getSystemService(Context.POWER_SERVICE) } returns pm

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BATTERY_LOW
        every { intent.hasExtra(BatteryManager.EXTRA_LEVEL) } returns true
        every { intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 15
        every { intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        
        receiver.onReceive(context, intent)
        
        verify {
            repo.postNotification(
                match { it.key == "system_battery" && it.title == "Low Battery" && it.text == "15%" },
                autoExpand = true
            )
        }
    }

    @Test
    fun testSmartwatchConnectionIgnored() {
        val repo = mockk<AuraNotificationRepository>(relaxed = true)
        val receiver = SystemEventReceiver(repo)
        val context = mockk<Context>()

        val watchDevice = mockk<BluetoothDevice>()
        val bluetoothClass = mockk<BluetoothClass>()
        every { bluetoothClass.majorDeviceClass } returns BluetoothClass.Device.Major.WEARABLE
        every { bluetoothClass.deviceClass } returns BluetoothClass.Device.WEARABLE_WRIST_WATCH
        every { watchDevice.bluetoothClass } returns bluetoothClass
        every { watchDevice.name } returns "Galaxy Watch6"
        every { watchDevice.address } returns "11:22:33:44:55:66"

        val intent = mockk<Intent>()
        every { intent.action } returns BluetoothDevice.ACTION_ACL_CONNECTED
        every { intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) } returns watchDevice
        @Suppress("DEPRECATION")
        every { intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns watchDevice
        every { intent.getIntExtra(any(), -1) } returns -1

        receiver.onReceive(context, intent)

        // Smartwatch connection should NOT post any notification or expand Aura
        verify(exactly = 0) { repo.postNotification(any(), any()) }
    }

    @Test
    fun testAudioDeviceConnectionAllowedAndDebounced() {
        val repo = mockk<AuraNotificationRepository>(relaxed = true)
        val receiver = SystemEventReceiver(repo)
        val context = mockk<Context>()

        val audioDevice = mockk<BluetoothDevice>()
        val bluetoothClass = mockk<BluetoothClass>()
        every { bluetoothClass.majorDeviceClass } returns BluetoothClass.Device.Major.AUDIO_VIDEO
        every { bluetoothClass.deviceClass } returns BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
        every { audioDevice.bluetoothClass } returns bluetoothClass
        every { audioDevice.name } returns "Galaxy Buds Pro"
        every { audioDevice.address } returns "AA:BB:CC:DD:EE:01"

        val intent = mockk<Intent>()
        every { intent.action } returns BluetoothDevice.ACTION_ACL_CONNECTED
        every { intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) } returns audioDevice
        @Suppress("DEPRECATION")
        every { intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns audioDevice
        every { intent.getIntExtra(any(), -1) } returns 85

        // First connection: autoExpand = true
        receiver.onReceive(context, intent)
        verify(exactly = 1) {
            repo.postNotification(
                match { it.key == "system_bluetooth" && it.title == "Galaxy Buds Pro" && it.text == "Connected • 85%" },
                autoExpand = true
            )
        }

        // Rapid second connection event for same device: autoExpand = false (debounced)
        receiver.onReceive(context, intent)
        verify(exactly = 1) {
            repo.postNotification(
                match { it.key == "system_bluetooth" },
                autoExpand = false
            )
        }
    }

    @Test
    fun testWearableDisconnectDoesNotDismissActiveAudioDeviceNotification() {
        val repo = mockk<AuraNotificationRepository>(relaxed = true)
        val receiver = SystemEventReceiver(repo)
        val context = mockk<Context>()

        val audioDevice = mockk<BluetoothDevice>()
        val audioBtClass = mockk<BluetoothClass>()
        every { audioBtClass.majorDeviceClass } returns BluetoothClass.Device.Major.AUDIO_VIDEO
        every { audioBtClass.deviceClass } returns BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
        every { audioDevice.bluetoothClass } returns audioBtClass
        every { audioDevice.name } returns "Galaxy Buds Pro"
        every { audioDevice.address } returns "AA:BB:CC:DD:EE:01"

        // Connect audio device
        val connectIntent = mockk<Intent>()
        every { connectIntent.action } returns BluetoothDevice.ACTION_ACL_CONNECTED
        every { connectIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) } returns audioDevice
        @Suppress("DEPRECATION")
        every { connectIntent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns audioDevice
        every { connectIntent.getIntExtra(any(), -1) } returns 90
        receiver.onReceive(context, connectIntent)

        // Now an unrelated watch disconnects
        val watchDevice = mockk<BluetoothDevice>()
        every { watchDevice.address } returns "99:88:77:66:55:44"
        val watchDisconnectIntent = mockk<Intent>()
        every { watchDisconnectIntent.action } returns BluetoothDevice.ACTION_ACL_DISCONNECTED
        every { watchDisconnectIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) } returns watchDevice
        @Suppress("DEPRECATION")
        every { watchDisconnectIntent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns watchDevice

        receiver.onReceive(context, watchDisconnectIntent)

        // The audio device's system_bluetooth notification must NOT be removed!
        verify(exactly = 0) { repo.removeNotification("system_bluetooth") }

        // Now the actual audio device disconnects
        val audioDisconnectIntent = mockk<Intent>()
        every { audioDisconnectIntent.action } returns BluetoothDevice.ACTION_ACL_DISCONNECTED
        every { audioDisconnectIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) } returns audioDevice
        @Suppress("DEPRECATION")
        every { audioDisconnectIntent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns audioDevice

        receiver.onReceive(context, audioDisconnectIntent)

        // Now it SHOULD be removed
        verify(exactly = 1) { repo.removeNotification("system_bluetooth") }
    }

    @Test
    fun testPowerConnectedYieldsToNativeIslandPopupThenShows() = kotlinx.coroutines.test.runTest {
        val repo = mockk<AuraNotificationRepository>(relaxed = true)
        val settings = com.moksha.auraisland.data.AuraSettings.Default.copy(
            deviceType = com.moksha.auraisland.util.OemDeviceType.VIVO_IQOO.name,
            yieldToSystemIsland = true,
            enableBatteryMode = true,
            enableCutoutRingMode = false,
            enableBatteryRing = false
        )
        val receiver = SystemEventReceiver(
            notificationRepository = repo,
            settingsProvider = { settings },
            coroutineScope = this
        )

        val context = mockk<Context>()
        val pm = mockk<PowerManager>(relaxed = true)
        every { pm.isPowerSaveMode } returns false
        every { context.getSystemService(Context.POWER_SERVICE) } returns pm

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_POWER_CONNECTED
        every { intent.hasExtra(BatteryManager.EXTRA_LEVEL) } returns true
        every { intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 75
        every { intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        every { intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_CHARGING

        receiver.onReceive(context, intent)

        // At t = 0 (native dynamic island popup is showing), Aura Island has NOT posted yet
        verify(exactly = 0) { repo.postNotification(match { it.key == "system_battery" }, any()) }

        // Advance virtual time by 3.5s (native dynamic island popup completes and disappears)
        testScheduler.advanceTimeBy(3501L)

        // As soon as native dynamic island disappears, Aura battery island pops up!
        verify(exactly = 1) {
            repo.postNotification(
                match { it.key == "system_battery" && it.text == "75%" },
                autoExpand = true
            )
        }
    }

    @Test
    fun testPowerConnectedImmediateOnStandardDevice() = kotlinx.coroutines.test.runTest {
        val repo = mockk<AuraNotificationRepository>(relaxed = true)
        val settings = com.moksha.auraisland.data.AuraSettings.Default.copy(
            deviceType = com.moksha.auraisland.util.OemDeviceType.GOOGLE_PIXEL.name,
            yieldToSystemIsland = true,
            enableBatteryMode = true,
            enableCutoutRingMode = false,
            enableBatteryRing = false
        )
        val receiver = SystemEventReceiver(
            notificationRepository = repo,
            settingsProvider = { settings },
            coroutineScope = this
        )

        val context = mockk<Context>()
        val pm = mockk<PowerManager>(relaxed = true)
        every { pm.isPowerSaveMode } returns false
        every { context.getSystemService(Context.POWER_SERVICE) } returns pm

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_POWER_CONNECTED
        every { intent.hasExtra(BatteryManager.EXTRA_LEVEL) } returns true
        every { intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 75
        every { intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        every { intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_CHARGING

        receiver.onReceive(context, intent)

        // On device with no native dynamic island, Aura battery island pops up immediately
        verify(exactly = 1) {
            repo.postNotification(
                match { it.key == "system_battery" && it.text == "75%" },
                autoExpand = true
            )
        }
    }
}
