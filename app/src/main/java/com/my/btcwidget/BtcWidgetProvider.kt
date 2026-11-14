package com.my.btcwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.pow

class BtcWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelUpdate(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = try { goAsync() } catch (e: Exception) { null }
        fetchAndUpdateAll(context, appWidgetManager, appWidgetIds, pendingResult)
        scheduleNextUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "REFRESH_WIDGET" || intent.action == "AUTO_REFRESH_WIDGET") {
            val pendingResult = try { goAsync() } catch (e: Exception) { null }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BtcWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                fetchAndUpdateAll(context, manager, ids, pendingResult)
                scheduleNextUpdate(context)
            } else {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 30_000L

        private fun fetchJson(urlStr: String): String {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            return conn.inputStream.bufferedReader().use { it.readText() }
        }

        private fun getAutoRefreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, BtcWidgetProvider::class.java).apply {
                action = "AUTO_REFRESH_WIDGET"
            }
            return PendingIntent.getBroadcast(
                context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun scheduleNextUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerTime = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerTime, getAutoRefreshIntent(context))
        }

        fun cancelUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(getAutoRefreshIntent(context))
        }

        fun fetchAndUpdateAll(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            pendingResult: BroadcastReceiver.PendingResult?
        ) {
            val clickIntent = Intent(context, BtcWidgetProvider::class.java).apply { action = "REFRESH_WIDGET" }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            thread {
                try {
                    val p1 = listOf("ht", "tps://", "api.", "upbit", ".com/v1/ticker?markets=KRW-BTC").joinToString("")
                    val upbitJson = fetchJson(p1)
                    val upbitObj = JSONArray(upbitJson).getJSONObject(0)
                    val krwPrice = upbitObj.getDouble("trade_price")
                    val changeRate = upbitObj.getDouble("signed_change_rate") * 100.0

                    val p2 = listOf("ht", "tps://", "api.", "binance", ".com/api/v3/ticker/price?symbol=BTCUSDT").joinToString("")
                    val binanceJson = fetchJson(p2)
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

                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_layout)
                        views.setOnClickPendingIntent(R.id.widget_root, clickPendingIntent)

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

                        views.setTextViewText(R.id.tv_update_time, "실시간 자동갱신(30초): ${timeFmt.format(Date())}")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_layout)
                        views.setOnClickPendingIntent(R.id.widget_root, clickPendingIntent)
                        views.setTextViewText(R.id.tv_update_time, "오류: ${e.message}")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
