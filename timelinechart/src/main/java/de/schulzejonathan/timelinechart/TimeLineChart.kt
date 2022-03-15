package de.schulzejonathan.timelinechart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil



class TimeLineChart(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var data: MutableList<TimeChartEntry>? = null
        set(value) { field = if (value.isNullOrEmpty()) null else value }
    var maxValue: Long? = null
    var minValue: Long? = null
    var timeStart: Long? = null
    var timeStop: Long? = null

    var paddingTop = 30f
    var paddingBottom = 30f
    var paddingLeft = 30f
    var paddingRight = 30f

    var paddingPathTop = 20f
    var paddingPathBottom = 40f

    var averagePointsEnabled = true
    var valueMarkerFormatter: (l: Long) -> String = { "${it}" }

    val backgroundPaint = Paint().apply {
        color = Color.LTGRAY
    }
    val textPaint = Paint().apply {
        flags = Paint.ANTI_ALIAS_FLAG
        isAntiAlias = true
        color = Color.WHITE
        textSize = 30f
        typeface = Typeface.DEFAULT
    }
    val legendTextPaint = Paint(textPaint).apply {
        color = Color.BLACK
        textSize = 35f
    }
    val linepathPaint = Paint().apply {
        flags = Paint.ANTI_ALIAS_FLAG
        isAntiAlias = true
        isFilterBitmap = true
        color = Color.BLUE
        strokeWidth = 5f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    val circlePaint = Paint().apply {
        flags = Paint.ANTI_ALIAS_FLAG
        isAntiAlias = true
        isFilterBitmap = true
        color =  Color.BLUE
        style = Paint.Style.FILL
    }

    val circleInsetPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val yTextBackgroundPaint = Paint().apply {
        color = Color.DKGRAY
    }

    private val minute: Long = 60000
    private val tenMinutes: Long = minute * 10
    private val hour: Long = tenMinutes * 6
    private val day: Long = hour * 24
    private val week: Long = day * 7

    init {
        if (isInEditMode) {
            data = generateSampleData(15 * 60000L)
            invalidate()
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) return

        // if data is null, only proceed if timeStart and timeStop are defined
        var data = this.data?.sorted() ?: if (timeStart != null && timeStop != null) listOf() else return
        var start = timeStart ?: data.first().time
        var stop = timeStop ?: data.last().time

        if (start == stop) stop = start + hour // ensure start and stop are not equal
        val timeIntervalRaw = stop - start

        val spacerInterval: Long
        val timeLegendFormatter: (t: Long) -> String
        when {
            timeIntervalRaw <= hour -> {
                spacerInterval = tenMinutes
                timeLegendFormatter = { SimpleDateFormat("HH:mm", Locale.GERMANY).format(it) }
            }
            timeIntervalRaw <= day -> {
                spacerInterval = hour
                timeLegendFormatter = { "${SimpleDateFormat("H", Locale.GERMANY).format(it)} h" }
            }
            timeIntervalRaw <= week -> {
                spacerInterval = day
                timeLegendFormatter = { SimpleDateFormat("E", Locale.GERMANY).format(it) }
            }
            else -> {
                spacerInterval = week
                timeLegendFormatter = { "${SimpleDateFormat("w", Locale.GERMANY).format(it)}. W" }
            }
        }

        // adjust the start and stop timestamps to be on the start and end of the spacerInterval
        start -= (start % spacerInterval)
        stop = stop - stop % spacerInterval + spacerInterval
        val timeInterval = stop - start

        // filter out old and jung data
        data = data.filter { it.time in start..stop }

        val maxValue = this.maxValue ?: (data.maxOf { it.value } * 1.1).toLong()
        val _minValue = data.minOf { it.value }
        val minValue = this.minValue ?: (if (_minValue < 0) _minValue * 1.1 else 0 ).toLong()

        /*
        * Compute path vertices
        */
        val innerWidth = width - (paddingLeft + paddingRight)
        val innerHeight = height - (paddingTop + paddingBottom) - (paddingPathBottom + paddingPathTop)

        val pathVertices: MutableList<ChartVertex> = mutableListOf()
        for (entry in data) {

            val y = height - (innerHeight * (entry.value - minValue) / (maxValue - minValue)) - paddingBottom - paddingPathBottom
            val x = innerWidth * (entry.time - start) / (timeInterval) + paddingLeft

            val lastVertex = pathVertices.lastOrNull()

            // if the current x is inside the radius of the last vertex, replace the last entry with a average entry
            if (lastVertex != null && x - lastVertex.x < lastVertex.radius * 1.8f && averagePointsEnabled) {
                pathVertices[pathVertices.lastIndex] = ChartVertex(
                    x = lastVertex.x,
                    y = (lastVertex.y + y) / 2,
                    color = if (lastVertex.color != circlePaint.color) lastVertex.color else circlePaint.color,
                    radius = 11f
                )
            } else {
                pathVertices.add(ChartVertex(x = x,  y = y))
            }
        }


        /*
        * Draw the background spacers, to indicate Time Intervals
        */

        val spacerAmount = (timeInterval / spacerInterval).toInt()
        val spacerWidth = innerWidth / spacerAmount
        for (n in 0 until spacerAmount step 2) {
            canvas.drawRect(
                n * spacerWidth + paddingLeft,
                0f + paddingTop,
                (n + 1) * spacerWidth + paddingLeft,
                height - paddingBottom,
                backgroundPaint
            )
        }


        /*
        * Draw chart Path and circles above each vertex
        */

        if (pathVertices.size > 0) {
            // linePath
            canvas.drawPath(
                Path().apply {
                    moveTo(pathVertices[0].x, pathVertices[0].y)
                    for (coordinate in pathVertices) lineTo(coordinate.x, coordinate.y)
                },
                linepathPaint
            )
            // circle
            for (cord in pathVertices) {
                canvas.drawCircle(cord.x, cord.y, cord.radius, circlePaint.apply { color = cord.color })
                canvas.drawCircle(cord.x, cord.y, cord.radius - 4f, circleInsetPaint)
            }
        }

        val measuringRect = Rect()

        /*
        * Draw time axis legend, each previously drawn background spacer gets one formatted timestamp
        * Formatter is determined by the time interval for each spacer at start of onDraw.
        */

        val (legendTextWidth, _) = measuringRect.calcStringDimensions(timeLegendFormatter(start), legendTextPaint)
        val legendStep = ceil((legendTextWidth + 20f) / spacerWidth).toInt()
        for (n in spacerAmount-1 downTo 0 step legendStep) {
            if (legendStep > 1 && n == spacerAmount-1) continue
            canvas.drawText(
                timeLegendFormatter(start + n * spacerInterval),
                n * spacerWidth + 10f + paddingLeft,
                height - paddingBottom - 10f,
                legendTextPaint
            )
        }


        /*
        * Draw value Markers on left side of Chart
        * Format the values with valueMarkerFormatter
        */

        val (_, textHeight) = measuringRect.calcStringDimensions("Test123", textPaint)
        val valueMarkers = mapOf<Float, Long>(
            paddingPathTop + paddingTop + 10f + textHeight to maxValue,
            (height.toFloat() - paddingPathBottom - paddingPathTop) /2 + paddingPathTop + textHeight / 2 to (maxValue + minValue) /2,
            height - paddingPathBottom - paddingBottom - 10f to minValue
        )
        for ((y, t) in valueMarkers) {
            val text = valueMarkerFormatter(t)
            textPaint.getTextBounds(text, 0, text.length, measuringRect)
            canvas.drawRect(
                10f - 4f,
                y - measuringRect.height() - 4f,
                10f + measuringRect.width() + 6f,
                y + 5f,
                yTextBackgroundPaint
            )
            canvas.drawText(
                text,
                10f, // to left of graph
                y,
                textPaint
            )
        }
        canvas.save()
        canvas.restore()
    }

    data class ChartVertex(
        val x: Float,
        val y: Float,
        val color: Int = Color.BLUE,
        val radius: Float = 9f
    )

    class TimeChartEntry(
        val time: Long,
        val value: Long,
        val color: Int? = null
    ): Comparable<TimeChartEntry> {
        override fun compareTo(other: TimeChartEntry): Int = this.time.compareTo(other.time)
        override fun toString(): String = "t:$time v:$value c:$color"
    }

    private fun Rect.calcStringDimensions(str: String, paint: Paint): Pair<Int, Int> {
        paint.getTextBounds(str, 0, str.length, this)
        return this.width() to this.height()
    }

    fun generateSampleData(interval: Long): MutableList<TimeChartEntry> {
        val data: MutableList<TimeChartEntry> = mutableListOf()
        val stop = this.timeStop ?: System.currentTimeMillis()
        val start = this.timeStart ?: stop - tenMinutes * (1..1008).random()
        var value = 500L
        for (time in start until stop step interval) {
            data.add(
                TimeChartEntry(
                    time + (-minute..minute).random(),
                    value
                )
            )
            value += (-75..75).random()
            value += 25
        }
//        data.add(TimeChartEntry(start, -100))
//        data.add(TimeChartEntry(stop, 100))
        return data
    }
}