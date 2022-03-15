package de.schulzejonathan.timelinechartexampleapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import de.schulzejonathan.timelinechart.TimeLineChart

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chart: TimeLineChart = findViewById(R.id.time_line_chart)
        chart.apply {
            data = generateSampleData(5 * 60000L)
            invalidate()
        }
    }
}