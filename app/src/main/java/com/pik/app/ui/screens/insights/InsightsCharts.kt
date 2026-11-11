package com.pik.app.ui.screens.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pik.app.domain.DonutMath
import com.pik.app.domain.PeriodBar
import com.pik.app.ui.theme.PikColors
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Paired bars (income, spending) for each period. Spending is stacked: the part that paid debts is
 * blue on top of the coral rest. Tap a pair to select it (the caller shows its numbers as text);
 * the selected pair is full colour, the others are dimmed. Labels sit under the bars.
 */
@Composable
fun IncomeSpendingBars(
    bars: List<PeriodBar>,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val maxCents = max(1L, bars.maxOfOrNull { max(it.incomeCents, it.spendingCents) } ?: 1L)
    Row(
        // One description for the whole chart; the numbers are also shown as text above it.
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        bars.forEachIndexed { index, bar ->
            val selected = index == selectedIndex
            val isCurrent = index == bars.lastIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_HEIGHT),
                ) {
                    val alpha = if (selected) 1f else 0.5f
                    val barWidth = size.width * 0.32f
                    val gap = size.width * 0.06f
                    val left = (size.width - 2 * barWidth - gap) / 2f
                    val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    val baseline = size.height

                    fun barHeight(cents: Long): Float =
                        if (cents <= 0L) 0f else max(2.dp.toPx(), size.height * cents.toFloat() / maxCents.toFloat())

                    // income
                    val incomeHeight = barHeight(bar.incomeCents)
                    if (incomeHeight > 0f) {
                        drawRoundRect(
                            color = PikColors.Lime.copy(alpha = alpha),
                            topLeft = Offset(left, baseline - incomeHeight),
                            size = Size(barWidth, incomeHeight),
                            cornerRadius = corner,
                        )
                    }
                    // spending: coral, with the debt part in blue at the top
                    val spendingHeight = barHeight(bar.spendingCents)
                    if (spendingHeight > 0f) {
                        val x = left + barWidth + gap
                        val top = baseline - spendingHeight
                        val debtHeight = if (bar.spendingCents > 0L) {
                            spendingHeight * bar.debtCents.toFloat() / bar.spendingCents.toFloat()
                        } else {
                            0f
                        }
                        val shape = Path().apply {
                            addRoundRect(RoundRect(x, top, x + barWidth, baseline, corner))
                        }
                        clipPath(shape) {
                            drawRect(
                                color = PikColors.Coral.copy(alpha = alpha),
                                topLeft = Offset(x, top),
                                size = Size(barWidth, spendingHeight),
                            )
                            if (debtHeight > 0f) {
                                drawRect(
                                    color = PikColors.Blue.copy(alpha = alpha),
                                    topLeft = Offset(x, top),
                                    size = Size(barWidth, debtHeight),
                                )
                            }
                        }
                    }
                    drawLine(
                        color = PikColors.Outline,
                        start = Offset(0f, baseline),
                        end = Offset(size.width, baseline),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected || isCurrent) PikColors.TextPrimary else PikColors.TextSecondary,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
            }
        }
    }
}

private val CHART_HEIGHT = 150.dp

/** One donut slice: its value (for the angle) and color. */
data class DonutSlice(val cents: Long, val color: Color)

/**
 * A ring split by [slices]. Tapping a slice calls [onSliceClick] with its index. [center] is drawn in
 * the hole. The whole chart has one [contentDescription]; the legend below it carries the same
 * numbers as text.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    onSliceClick: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit = {},
) {
    val values = remember(slices) { slices.map { it.cents } }
    val arcs = remember(values) { DonutMath.arcs(values) }
    Box(
        modifier = modifier
            .size(DONUT_SIZE)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(values) {
                    detectTapGestures { tap ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tap.x - cx
                        val dy = tap.y - cy
                        val radius = hypot(dx, dy)
                        val outer = minOf(cx, cy)
                        // taps in the ring (a little forgiving towards the hole)
                        if (radius < outer * 0.45f || radius > outer) return@detectTapGestures
                        val degreesFromTop = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                        DonutMath.indexAt(values, degreesFromTop)?.let(onSliceClick)
                    }
                },
        ) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (slices.isEmpty() || values.sum() <= 0L) {
                drawArc(
                    color = PikColors.Outline,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            } else {
                slices.forEachIndexed { i, slice ->
                    val arc = arcs[i]
                    if (arc.sweepDegrees > 0f) {
                        drawArc(
                            color = slice.color,
                            startAngle = arc.startDegrees - 90f,
                            sweepAngle = arc.sweepDegrees,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                    }
                }
            }
        }
        center()
    }
}

private val DONUT_SIZE = 190.dp

/**
 * A small line of cumulative values (spending so far in the period) with a soft fill under it and a
 * dot on the latest value. Needs at least two values to draw a line; one value draws just the dot.
 */
@Composable
fun Sparkline(
    values: List<Long>,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(SPARK_HEIGHT)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        if (values.isEmpty()) return@Canvas
        val maxValue = max(1L, values.max()).toFloat()
        val pad = 4.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun x(i: Int): Float = if (values.size == 1) pad + w else pad + w * i / (values.size - 1).toFloat()
        fun y(v: Long): Float = pad + h * (1f - v.toFloat() / maxValue)

        if (values.size > 1) {
            val line = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) lineTo(x(i), y(values[i]))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(values.lastIndex), size.height - pad)
                lineTo(x(0), size.height - pad)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x(values.lastIndex), y(values.last())))
    }
}

private val SPARK_HEIGHT = 56.dp
