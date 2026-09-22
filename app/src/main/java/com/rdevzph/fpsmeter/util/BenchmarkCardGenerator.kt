package com.rdevzph.fpsmeter.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rdevzph.fpsmeter.model.FpsSessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility for generating high-resolution, dark-themed benchmark scorecard template images
 * from recorded FPS sessions and launching the standard Android Sharesheet.
 */
object BenchmarkCardGenerator {

    suspend fun shareSessionCard(context: Context, session: FpsSessionRecord) {
        withContext(Dispatchers.IO) {
            try {
                val imageFile = generateCardBitmapFile(context, session)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "FPS Benchmark for ${session.appName}: Avg ${session.avgFps} FPS, Peak ${session.maxFps} FPS, Min ${session.minFps} FPS recorded with FPS Meter Android\nhttps://github.com/rdevz-ph/FPS-Meter-Android"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Benchmark Scorecard").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to generate benchmark card: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateCardBitmapFile(context: Context, session: FpsSessionRecord): File {
        val width = 1080
        val height = 620
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

        // 1. Outer Background Card with Rounded Corners
        val bgRect = RectF(16f, 16f, (width - 16).toFloat(), (height - 16).toFloat())
        val bgGradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.parseColor("#12151B"), Color.parseColor("#1A1F27"),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgGradient
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(bgRect, 36f, 36f, paint)
        paint.shader = null

        // 2. Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#2C3442")
        canvas.drawRoundRect(bgRect, 36f, 36f, paint)

        // 3. Header Section (App Icon + Game Title + Timestamp + Branding Badge)
        val iconSize = 100
        val iconLeft = 48f
        val iconTop = 48f
        val appIconBitmap = loadRoundedAppIcon(context, session.packageName, iconSize)
        if (appIconBitmap != null) {
            canvas.drawBitmap(appIconBitmap, iconLeft, iconTop, null)
        } else {
            // Fallback icon placeholder
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#262D3B")
            val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            canvas.drawRoundRect(iconRect, 22f, 22f, paint)

            paint.color = Color.parseColor("#00E5FF")
            paint.textSize = 40f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val initial = session.appName.take(1).uppercase()
            canvas.drawText(initial, iconLeft + 36f, iconTop + 65f, paint)
        }

        // Top-Right Branding Badge
        val badgeText = "FPS METER • BENCHMARK"
        paint.textSize = 22f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        val badgeTextWidth = paint.measureText(badgeText)
        val badgePaddingH = 20f
        val badgeHeight = 44f
        val badgeRight = (width - 48).toFloat()
        val badgeLeft = badgeRight - badgeTextWidth - (badgePaddingH * 2)
        val badgeTop = 54f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeTop + badgeHeight)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1F2735")
        canvas.drawRoundRect(badgeRect, 22f, 22f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#00E5FF")
        paint.alpha = 100
        canvas.drawRoundRect(badgeRect, 22f, 22f, paint)
        paint.alpha = 255

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawText(badgeText, badgeLeft + badgePaddingH, badgeTop + 29f, paint)

        // Game Title (truncated if necessary)
        val titleLeft = iconLeft + iconSize + 24f
        val maxTitleWidth = badgeLeft - titleLeft - 20f
        paint.color = Color.WHITE
        paint.textSize = 42f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val truncatedTitle = truncateText(session.appName, paint, maxTitleWidth)
        canvas.drawText(truncatedTitle, titleLeft, iconTop + 46f, paint)

        // Subtitle (Timestamp + Duration summary)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(session.startTime))
        paint.color = Color.parseColor("#8E9AA8")
        paint.textSize = 25f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(formattedDate, titleLeft, iconTop + 86f, paint)

        // Horizontal Divider
        val dividerY = 178f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.parseColor("#262E3B")
        canvas.drawLine(48f, dividerY, (width - 48).toFloat(), dividerY, paint)

        // 4. Metrics Grid (4 Metric Tiles)
        val gridTop = 206f
        val gridHeight = 270f
        val gridBottom = gridTop + gridHeight
        val totalGridWidth = (width - 96).toFloat()
        val spacing = 20f

        // Tile 1: Average FPS (Larger, colored tile)
        val avgTileWidth = 360f
        val avgRect = RectF(48f, gridTop, 48f + avgTileWidth, gridBottom)
        val avgColor = when {
            session.avgFps >= 60 -> Color.parseColor("#4CAF50")
            session.avgFps >= 45 -> Color.parseColor("#FBC02D")
            session.avgFps >= 30 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#151922")
        canvas.drawRoundRect(avgRect, 24f, 24f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = avgColor
        canvas.drawRoundRect(avgRect, 24f, 24f, paint)

        // Tile 1 Content
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#9EAAB8")
        paint.textSize = 24f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        canvas.drawText("AVERAGE FPS", avgRect.left + 28f, avgRect.top + 52f, paint)

        paint.color = avgColor
        paint.textSize = 100f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${session.avgFps}", avgRect.left + 28f, avgRect.top + 160f, paint)

        val avgFpsNumWidth = paint.measureText("${session.avgFps}")
        paint.textSize = 34f
        paint.color = Color.parseColor("#C8D4E2")
        canvas.drawText("FPS", avgRect.left + 28f + avgFpsNumWidth + 14f, avgRect.top + 155f, paint)

        val perfTag = when {
            session.avgFps >= 60 -> "Ultra Smooth"
            session.avgFps >= 45 -> "Playable & Smooth"
            session.avgFps >= 30 -> "Acceptable Performance"
            else -> "Heavy Throttling"
        }
        paint.textSize = 22f
        paint.color = avgColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("• $perfTag", avgRect.left + 28f, avgRect.top + 220f, paint)

        // Remaining 3 tiles: Peak FPS, Min FPS, Duration
        val remainingWidth = totalGridWidth - avgTileWidth - (spacing * 3)
        val colWidth = remainingWidth / 3f

        fun drawStatTile(left: Float, label: String, value: String, unit: String? = null) {
            val tileRect = RectF(left, gridTop, left + colWidth, gridBottom)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#161A22")
            canvas.drawRoundRect(tileRect, 24f, 24f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#28303E")
            canvas.drawRoundRect(tileRect, 24f, 24f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#8E9CAE")
            paint.textSize = 22f
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            canvas.drawText(label, tileRect.left + 22f, tileRect.top + 52f, paint)

            paint.color = Color.WHITE
            paint.textSize = 52f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(value, tileRect.left + 22f, tileRect.top + 148f, paint)

            if (unit != null) {
                val valWidth = paint.measureText(value)
                paint.textSize = 24f
                paint.color = Color.parseColor("#8E9CAE")
                canvas.drawText(unit, tileRect.left + 22f + valWidth + 8f, tileRect.top + 145f, paint)
            }
        }

        val tile2Left = 48f + avgTileWidth + spacing
        drawStatTile(tile2Left, "PEAK FPS", "${session.maxFps}", "FPS")

        val tile3Left = tile2Left + colWidth + spacing
        drawStatTile(tile3Left, "LOWEST FPS", "${session.minFps}", "FPS")

        val tile4Left = tile3Left + colWidth + spacing
        val durationMin = session.durationSeconds / 60
        val durationSec = session.durationSeconds % 60
        val durationStr = if (durationMin > 0) "${durationMin}m ${durationSec}s" else "${durationSec}s"
        drawStatTile(tile4Left, "DURATION", durationStr)

        // 5. Footer Section (Device Hardware Info & Attribution)
        val footerY = height - 42f
        paint.style = Paint.Style.FILL
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#8592A3")

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        val deviceModel = "$manufacturer ${Build.MODEL}"
        canvas.drawText("Device: $deviceModel", 48f, footerY, paint)

        val projectUrl = "FPS Meter for Android • rdevz-ph"
        paint.color = Color.parseColor("#00E5FF")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val projectUrlWidth = paint.measureText(projectUrl)
        canvas.drawText(projectUrl, (width - 48 - projectUrlWidth), footerY, paint)

        // 6. Save bitmap to cache directory
        val cacheFolder = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        val outputFile = File(cacheFolder, "benchmark_${session.id}.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return outputFile
    }

    private fun loadRoundedAppIcon(context: Context, packageName: String, size: Int): Bitmap? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val rawBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val rawCanvas = Canvas(rawBitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(rawCanvas)

            // Clip into rounded rectangle
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val outputCanvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = BitmapShader(rawBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            outputCanvas.drawRoundRect(rect, 22f, 22f, paint)
            output
        } catch (e: Exception) {
            null
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated...") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return if (truncated.isNotEmpty()) "$truncated..." else text
    }
}
