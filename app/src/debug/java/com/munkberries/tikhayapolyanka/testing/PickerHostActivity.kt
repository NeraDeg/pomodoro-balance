package com.munkberries.tikhayapolyanka.testing

import android.app.Activity
import android.content.Context
import com.munkberries.tikhayapolyanka.russianContext

/** Debug-only window host. No ViewModel, runtime or user database is opened. */
class PickerHostActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(russianContext(newBase))
    }
}
