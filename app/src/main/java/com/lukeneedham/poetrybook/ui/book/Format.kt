package com.lukeneedham.poetrybook.ui.book

import java.text.DateFormat
import java.util.Date

/** [millis] (epoch) as a date and time in the user's locale, e.g. "14 Oct 2026, 18:32". */
fun formatTimestamp(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
