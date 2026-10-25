package com.lukeneedham.poetrybook.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * The view model of the page being drawn, created by [create] the first time and kept for as long as the page is on the
 * back stack. Each page asks for its own: no view model is shared between pages.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (Application) -> VM): VM {
    val application = LocalContext.current.applicationContext as Application
    return viewModel<VM>(factory = viewModelFactory { initializer { create(application) } })
}
