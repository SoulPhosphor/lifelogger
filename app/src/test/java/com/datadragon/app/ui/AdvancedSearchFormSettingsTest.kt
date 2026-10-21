package com.datadragon.app.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.datadragon.app.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedSearchFormSettingsTest {
    @Test fun creationAndEditingPersistTheOptInWithoutChangingIdentity() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val db = AppDatabase.getInstance(app)
            val create = CreateLogViewModel(app).also { store.put("create", it) }
            val name = "Advanced test ${System.nanoTime()}"
            val saved = CompletableDeferred<Unit>()
            create.save(name, "[]", "", true, false, false, null, true,
                advancedSearchEnabled = true, onSaved = { saved.complete(Unit) })
            saved.await()
            val original = db.logTemplateDao().getAllOnce().single { it.name == name }
            assertTrue(original.advancedSearchEnabled)
            val editor = EditFormViewModel(app).also { store.put("edit", it) }
            editor.load(original.id)
            editor.name.first { it != null }
            assertTrue(editor.advancedSearchEnabled.value)
            val edited = CompletableDeferred<Unit>()
            editor.save(name, emptyList(), false, null, true, false, false,
                advancedSearchEnabled = false, locked = true, onSaved = { edited.complete(Unit) })
            edited.await()
            val restored = db.logTemplateDao().getById(original.id)!!
            assertFalse(restored.advancedSearchEnabled)
            assertEquals(original.uuid, restored.uuid)
            val defaultSaved = CompletableDeferred<Unit>()
            create.save("$name default", "[]", "", true, false, false, null, true,
                onSaved = { defaultSaved.complete(Unit) })
            defaultSaved.await()
            assertFalse(db.logTemplateDao().getAllOnce().single { it.name == "$name default" }.advancedSearchEnabled)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
