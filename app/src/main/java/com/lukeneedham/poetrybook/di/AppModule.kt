package com.lukeneedham.poetrybook.di

import com.lukeneedham.poetrybook.data.HistoryRepository
import com.lukeneedham.poetrybook.data.PoetryRepository
import com.lukeneedham.poetrybook.ui.bin.BinBookViewModel
import com.lukeneedham.poetrybook.ui.bin.BinPoemViewModel
import com.lukeneedham.poetrybook.ui.bin.BinViewModel
import com.lukeneedham.poetrybook.ui.book.BookViewModel
import com.lukeneedham.poetrybook.ui.history.HistoryViewModel
import com.lukeneedham.poetrybook.ui.search.SearchViewModel
import com.lukeneedham.poetrybook.ui.settings.SettingsViewModel
import com.lukeneedham.poetrybook.ui.shelf.ShelfViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { PoetryRepository(androidContext()) }
    singleOf(::HistoryRepository)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ShelfViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::BinViewModel)
    viewModel { (bookId: String, poemToShow: String?) -> BookViewModel(bookId, poemToShow, get(), get()) }
    viewModel { (poemId: String) -> HistoryViewModel(poemId, get(), get()) }
    viewModel { (bookId: String) -> BinBookViewModel(bookId, get()) }
    viewModel { (poemId: String) -> BinPoemViewModel(poemId, get()) }
}
