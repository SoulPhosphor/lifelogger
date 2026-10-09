package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LuckyListViewModelFactoryTest {
    @Test fun androidFactoryCanCreateBothOrdinaryAndLuckyListEditors() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val factory = ViewModelProvider.AndroidViewModelFactory(application)
        assertNotNull(factory.create(ChecklistViewModel::class.java))
        assertNotNull(factory.create(LuckyListViewModel::class.java))
    }
}
