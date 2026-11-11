package com.pik.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps stored colorKey / iconKey strings to UI values. */
object CategoryStyle {

    private val colors: Map<String, Color> = mapOf(
        "coral" to Color(0xFFFF8F80),
        "amber" to Color(0xFFFFC857),
        "lime" to Color(0xFFC6F36B),
        "blue" to Color(0xFF84BDFF),
        "mint" to Color(0xFF7FE3C0),
        "violet" to Color(0xFFB79CFF),
        "pink" to Color(0xFFFF9ACB),
        "orange" to Color(0xFFFFAA5C),
        "teal" to Color(0xFF5FD4E0),
        "slate" to Color(0xFF9DB0C2),
    )

    private val icons: Map<String, ImageVector> = mapOf(
        "cart" to Icons.Filled.ShoppingCart,
        "restaurant" to Icons.Filled.Restaurant,
        "coffee" to Icons.Filled.LocalCafe,
        "bus" to Icons.Filled.DirectionsBus,
        "home" to Icons.Filled.Home,
        "bills" to Icons.Filled.Receipt,
        "bag" to Icons.Filled.ShoppingBag,
        "health" to Icons.Filled.HealthAndSafety,
        "movie" to Icons.Filled.Movie,
        "subscriptions" to Icons.Filled.Subscriptions,
        "gift" to Icons.Filled.CardGiftcard,
        "other" to Icons.Filled.MoreHoriz,
        "salary" to Icons.Filled.Payments,
        "refund" to Icons.Filled.Replay,
        "redeem" to Icons.Filled.Redeem,
        "income" to Icons.Filled.AddCircle,
    )

    /** Palette keys in display order. */
    val colorKeys: List<String> = colors.keys.toList()

    /** Icon keys in display order. */
    val iconKeys: List<String> = icons.keys.toList()

    fun color(key: String?): Color = colors[key] ?: PikColors.TextSecondary

    fun icon(key: String?): ImageVector = icons[key] ?: Icons.Filled.MoreHoriz
}
