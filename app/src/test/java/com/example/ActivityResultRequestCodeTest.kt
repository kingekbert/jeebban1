package com.example

import android.Manifest
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityResultRequestCodeTest {

    @Test
    fun `requesting READ_SMS permission via ActivityResultRegistry does not throw 16-bit requestCode error`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val launcher = activity.activityResultRegistry.register(
            "test_sms_permission",
            ActivityResultContracts.RequestPermission()
        ) {}

        launcher.launch(Manifest.permission.READ_SMS)

        val permissionRequest = shadowOf(activity).lastRequestedPermission
        assertNotNull("Expected READ_SMS permission request to be dispatched", permissionRequest)
        assertEquals(listOf(Manifest.permission.READ_SMS), permissionRequest.requestedPermissions.toList())
    }

    @Test
    fun `opening backup document via OpenDocument does not throw 16-bit requestCode error`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val launcher = activity.activityResultRegistry.register(
            "test_open_document",
            ActivityResultContracts.OpenDocument()
        ) {}

        launcher.launch(arrayOf("*/*"))

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("Expected OpenDocument intent to be started", started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
    }
}
