package com.scrnstr

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.scrnstr.actions.BillOrganizer
import com.scrnstr.actions.CalendarAdder
import com.scrnstr.actions.LetterboxdAction
import com.scrnstr.actions.WhatsAppAction

object ActionExecutor {

    suspend fun execute(context: Context, category: String, data: JsonObject, screenshotUri: Uri) {
        when (category) {
            "food_bill" -> BillOrganizer.organize(context, screenshotUri)
            "event" -> CalendarAdder.addEvent(context, data)
            "tech_article" -> WhatsAppAction.share(context, data)
            "movie" -> LetterboxdAction.addToWatchlist(context, data)
        }
    }
}
