package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("جیب‌بان", appName)
  }

  @Test
  fun `inflate widget layout`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val views = android.widget.RemoteViews(context.packageName, R.layout.widget_sms_scan)
    val inflated = views.apply(context, android.widget.FrameLayout(context))
    org.junit.Assert.assertNotNull(inflated)
  }
}
