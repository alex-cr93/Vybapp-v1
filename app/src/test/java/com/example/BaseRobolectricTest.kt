package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class TestFirebaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:abcdef123456")
                .setApiKey("AIzaSyDummyKeyForRobolectricTests")
                .setProjectId("vyb-test-project")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestFirebaseApplication::class)
abstract class BaseRobolectricTest {

    @Before
    fun setUpFirebase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:abcdef123456")
                .setApiKey("AIzaSyDummyKeyForRobolectricTests")
                .setProjectId("vyb-test-project")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }
}
