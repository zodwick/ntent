package com.scrnstr

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.gson.JsonObject
import com.scrnstr.actions.BillOrganizer
import com.scrnstr.actions.CalendarAdder
import com.scrnstr.actions.LetterboxdAction
import com.scrnstr.actions.WhatsAppAction
import com.scrnstr.actions.CouponCopier
import com.scrnstr.actions.ContactSaver
import com.scrnstr.actions.WifiConnector
import com.scrnstr.actions.AddressOpener
import com.scrnstr.actions.ReminderSetter
import com.scrnstr.actions.TravelAdder

object ActionExecutor {

    suspend fun execute(context: Context, category: String, data: JsonObject, screenshotUri: Uri) {
        when (category) {
            "food_bill" -> {
                if (screenshotUri == Uri.EMPTY) {
                    Toast.makeText(context, "Bill organizer requires a screenshot", Toast.LENGTH_SHORT).show()
                } else {
                    BillOrganizer.organize(context, screenshotUri)
                }
            }
            "event" -> CalendarAdder.addEvent(context, data)
            "tech_article" -> WhatsAppAction.share(context, data)
            "movie" -> LetterboxdAction.addToWatchlist(context, data)
            "coupon_code" -> CouponCopier.copy(context, data)
            "contact" -> ContactSaver.save(context, data)
            "wifi_password" -> WifiConnector.connect(context, data)
            "address" -> AddressOpener.open(context, data)
            "reminder" -> ReminderSetter.setReminder(context, data)
            "travel" -> TravelAdder.add(context, data)
        }
    }
}
