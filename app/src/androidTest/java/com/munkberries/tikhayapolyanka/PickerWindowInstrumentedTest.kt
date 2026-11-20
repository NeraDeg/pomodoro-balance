package com.munkberries.tikhayapolyanka

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.munkberries.tikhayapolyanka.testing.PickerHostActivity
import com.munkberries.tikhayapolyanka.ui.chooseDate
import com.munkberries.tikhayapolyanka.ui.chooseTime
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real windows; inflating a picker alone cannot detect a bad window token.
 * The debug-only host never opens the user's database or creates a timer.
 * Prepared here, but must be RUN on Android before accepting the candidate.
 */
@RunWith(AndroidJUnit4::class)
class PickerWindowInstrumentedTest {
    @Test fun date_dialog_opens_confirms_and_preserves_russian_activity_context() {
        val expected = LocalDate.of(2026, 10, 26)
        val chosen = AtomicReference<LocalDate?>(null)
        var dialog: DatePickerDialog? = null
        ActivityScenario.launch(PickerHostActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    dialog = chooseDate(activity, expected) { chosen.set(it) }
                    assertTrue(dialog!!.isShowing)
                    assertEquals("ru", dialog!!.context.resources.configuration.locales[0].language)
                    dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                }
                // AlertDialog dispatches button callbacks through the main Handler.
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(expected, chosen.get())
            } finally { scenario.onActivity { dialog?.dismiss() } }
        }
    }

    @Test fun time_dialog_opens_confirms_and_cancels_without_changing_value() {
        val expected = LocalTime.of(13, 45)
        val chosen = AtomicReference<LocalTime?>(null)
        var dialog: TimePickerDialog? = null
        ActivityScenario.launch(PickerHostActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    dialog = chooseTime(activity, expected) { chosen.set(it) }
                    assertTrue(dialog!!.isShowing)
                    assertEquals("ru", dialog!!.context.resources.configuration.locales[0].language)
                    dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(expected, chosen.get())
                chosen.set(null)
                scenario.onActivity { activity ->
                    dialog = chooseTime(activity, expected) { chosen.set(it) }
                    assertTrue(dialog!!.isShowing)
                    dialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertNull(chosen.get())
            } finally { scenario.onActivity { dialog?.dismiss() } }
        }
    }
}
