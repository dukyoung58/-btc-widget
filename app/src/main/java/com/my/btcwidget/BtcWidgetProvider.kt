package com.my.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.pow

class BtcWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "REFRESH_WIDGET") {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BtcWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val intent = Intent(context, BtcWidgetProvider::class.java).apply { action = "REFRESH_WIDGET" }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            thread {
                try {
                    val upbitJson = URL("https://upbit.com").readText()
                    val upbitObj = JSONArray(upbitJson).getJSONObject(0)
                    val krwPrice = upbitObj.getDouble("trade_price")
                    val changeRate = upbitObj.getDouble("signed_change_rate") * 100.0

                    val binanceJson = URL("https://binance.com").readText()
                    val usdtPrice = JSONObject(binanceJson).getDouble("price")

                    val fxRate = krwPrice / usdtPrice

                    val genesisMillis = 1230940800000L
                    val days = (System.currentTimeMillis() - genesisMillis) / (1000.0 * 60 * 60 * 24)
                    val fairPrice = 10.0.pow(-17.014) * days.pow(5.845)
                    val supportPrice = fairPrice * 0.3548
                    val resistPrice = fairPrice * 3.447
                    val deviation = ((usdtPrice - fairPrice) / fairPrice) * 100.0

                    val fair1Y = 10.0.pow(-17.014) * (days + 365.0).pow(5.845)
                    val sup1Y = fair1Y * 0.3548
                    val fair2Y = 10.0.pow(-17.014) * (days + 730.0).pow(5.845)
                    val sup2Y = fair2Y * 0.3548

                    val intFmt = DecimalFormat("#,###")
                    val decFmt = DecimalFormat("#,##0.0")
                    val signFmt = DecimalFormat("+0.00;-0.00")
                    val devFmt = DecimalFormat("+0.0;-0.0")
                    val timeFmt = SimpleDateFormat("a h:mm:ss", Locale.KOREA)

                    views.setTextViewText(R.id.tv_fx, "US $1 = ₩${decFmt.format(fxRate)}")
                    views.setTextViewText(R.id.tv_krw_price, "₩${intFmt.format(krwPrice)}")
                    views.setTextViewText(R.id.tv_usdt_price, "$${decFmt.format(usdtPrice)}")

                    val changeText = "${signFmt.format(changeRate)}% (24시간)"
                    views.setTextViewText(R.id.tv_change_24h, changeText)
                    views.setTextColor(R.id.tv_change_24h, if (changeRate >= 0) Color.parseColor("#4FD1C5") else Color.parseColor("#FF6B6B"))

                    views.setTextViewText(R.id.tv_fair, "$${intFmt.format(fairPrice)}")
                    views.setTextViewText(R.id.tv_support, "$${intFmt.format(supportPrice)}")
                    views.setTextViewText(R.id.tv_resist, "$${intFmt.format(resistPrice)}")
                    views.setTextViewText(R.id.tv_dev, "${devFmt.format(deviation)}%")

                    views.setTextViewText(R.id.tv_1y_fair, "$${intFmt.format(fair1Y)}")
                    views.setTextViewText(R.id.tv_1y_sup, "$${intFmt.format(sup1Y)}")
                    views.setTextViewText(R.id.tv_2y_fair, "$${intFmt.format(fair2Y)}")
                    views.setTextViewText(R.id.tv_2y_sup, "$${intFmt.format(sup2Y)}")

                    views.setTextViewText(R.id.tv_update_time, "마지막 업데이트: ${timeFmt.format(Date())} (터치 시 갱신)")
                } catch (e: Exception) {
                    views.setTextViewText(R.id.tv_update_time, "갱신 실패 - 터치하여 재시도")
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
