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

/**
 * Edit Form's Locked Log, the create-only Never Allow Form Unlocking, and the
 * one-time timestamp conversion, through the real view models.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditFormLockingTest {

    private val dateTime = FieldDef(id = "when", label = "When", type = FieldType.DATETIME, required = true, defaultNow = true)

    @Test fun unlockingASwitchedOverFormInEditFormMakesItsEntriesEditableAndConvertsTimestamps() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val db = AppDatabase.getInstance(app)
            val name = "Odor Log ${System.nanoTime()}"
            // A form created locked with Automatic Timestamping, as before this change.
            val templateId = db.logTemplateDao().insert(
                LogTemplate(uuid = StableUuid.createNew(), name = name, createdAt = 1, schemaJson = "[]", locked = true, automaticTimestamping = true),
            )
            val entryId = db.logEntryDao().insert(
                LogEntry(templateId = templateId, createdAt = "2026-10-01T08:30:15-04:00", valuesJson = "{}"),
            )

            val editor = EditFormViewModel(app).also { store.put("edit", it) }
            editor.load(templateId)
            editor.name.first { it != null }
            assertTrue(editor.locked.value)
            assertFalse(editor.neverAllowUnlocking.value)
            assertTrue(editor.hasEntries.value)

            val saved = CompletableDeferred<Unit>()
            editor.save(name, listOf(dateTime), false, null, true, false, false,
                locked = false, convertTimestampsToFieldId = "when", onSaved = { saved.complete(Unit) })
            saved.await()

            val template = db.logTemplateDao().getById(templateId)!!
            assertFalse(template.locked)
            assertFalse(template.automaticTimestamping)
            val entry = db.logEntryDao().getById(entryId)!!
            assertEquals("""{"when":"2026-10-01T08:30"}""", entry.valuesJson)
            assertEquals("2026-10-01T08:30:15-04:00", entry.createdAt)

            // Locking can be turned back on, and saving without the checkbox converts nothing more.
            val relocked = CompletableDeferred<Unit>()
            editor.save(name, listOf(dateTime), false, null, true, false, false,
                locked = true, onSaved = { relocked.complete(Unit) })
            relocked.await()
            assertTrue(db.logTemplateDao().getById(templateId)!!.locked)
            assertEquals("""{"when":"2026-10-01T08:30"}""", db.logEntryDao().getById(entryId)!!.valuesJson)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun newFormsStartUnlockedAndAPermanentlyLockedFormStaysLocked() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val db = AppDatabase.getInstance(app)
            val create = CreateLogViewModel(app).also { store.put("create", it) }
            val name = "Locking ${System.nanoTime()}"

            val ordinary = CompletableDeferred<Unit>()
            create.save(name, "[]", "", false, false, false, null, true, onSaved = { ordinary.complete(Unit) })
            ordinary.await()
            val open = db.logTemplateDao().getAllOnce().single { it.name == name }
            assertFalse(open.locked)
            assertFalse(open.neverAllowUnlocking)

            // Never Allow Form Unlocking locks the form even if Locked Log was off.
            val permanent = CompletableDeferred<Unit>()
            create.save("$name permanent", "[]", "", false, false, false, null, true,
                neverAllowUnlocking = true, onSaved = { permanent.complete(Unit) })
            permanent.await()
            val locked = db.logTemplateDao().getAllOnce().single { it.name == "$name permanent" }
            assertTrue(locked.locked)
            assertTrue(locked.neverAllowUnlocking)

            val editor = EditFormViewModel(app).also { store.put("edit", it) }
            editor.load(locked.id)
            editor.name.first { it != null }
            assertTrue(editor.neverAllowUnlocking.value)
            val edited = CompletableDeferred<Unit>()
            editor.save("$name permanent", emptyList(), false, null, true, false, false,
                locked = false, onSaved = { edited.complete(Unit) })
            edited.await()
            val after = db.logTemplateDao().getById(locked.id)!!
            assertTrue(after.locked)
            assertTrue(after.neverAllowUnlocking)
            assertEquals(locked.uuid, after.uuid)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
