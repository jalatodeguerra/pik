package com.pik.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.data.db.TransactionRow
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.TransactionType
import com.pik.app.ui.theme.CategoryStyle
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles

val CardShape = RoundedCornerShape(20.dp)

@Composable
fun PikCard(
    modifier: Modifier = Modifier,
    color: Color = PikColors.Surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = CardShape,
            color = color,
            contentColor = PikColors.TextPrimary,
        ) { Column(content = content) }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CardShape,
            color = color,
            contentColor = PikColors.TextPrimary,
        ) { Column(content = content) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = PikColors.TextPrimary,
        modifier = modifier,
    )
}

@Composable
fun pikPrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = PikColors.Lime,
    contentColor = PikColors.OnLime,
    disabledContainerColor = PikColors.SurfaceRaised,
    disabledContentColor = PikColors.TextSecondary,
)

@Composable
fun pikSecondaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = PikColors.SurfaceRaised,
    contentColor = PikColors.TextPrimary,
    disabledContainerColor = PikColors.Surface,
    disabledContentColor = PikColors.TextSecondary,
)

@Composable
fun PikPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = pikPrimaryButtonColors(),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun PikSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = pikSecondaryButtonColors(),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Explicit colors so text never renders black on dark. */
@Composable
fun pikTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PikColors.TextPrimary,
    unfocusedTextColor = PikColors.TextPrimary,
    disabledTextColor = PikColors.TextSecondary,
    focusedContainerColor = PikColors.Surface,
    unfocusedContainerColor = PikColors.Surface,
    disabledContainerColor = PikColors.Surface,
    cursorColor = PikColors.Lime,
    focusedBorderColor = PikColors.Lime,
    unfocusedBorderColor = PikColors.Outline,
    focusedLabelColor = PikColors.Lime,
    unfocusedLabelColor = PikColors.TextSecondary,
    focusedPlaceholderColor = PikColors.TextSecondary,
    unfocusedPlaceholderColor = PikColors.TextSecondary,
    focusedSuffixColor = PikColors.TextSecondary,
    unfocusedSuffixColor = PikColors.TextSecondary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PikSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = PikColors.Lime,
                    activeContentColor = PikColors.OnLime,
                    activeBorderColor = PikColors.Lime,
                    inactiveContainerColor = PikColors.Surface,
                    inactiveContentColor = PikColors.TextPrimary,
                    inactiveBorderColor = PikColors.Outline,
                ),
                icon = {},
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun IconCircle(
    icon: ImageVector,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 44,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
fun TransactionListItem(
    row: TransactionRow,
    showDate: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val title = when (row.type) {
        TransactionType.TRANSFER -> stringResource(R.string.transaction_transfer)
        TransactionType.ADJUSTMENT -> stringResource(R.string.transaction_adjustment)
        else -> row.categoryName ?: stringResource(R.string.transaction_uncategorized)
    }
    val accountText = if (row.type == TransactionType.TRANSFER) {
        stringResource(R.string.transfer_route, row.accountName, row.toAccountName.orEmpty())
    } else {
        row.accountName
    }
    val subtitle = listOfNotNull(
        row.note.takeIf { it.isNotBlank() },
        accountText,
        if (showDate) DateFormats.shortDate(row.date) else null,
    ).joinToString(" · ")

    val (amountText, amountColor) = when (row.type) {
        TransactionType.EXPENSE -> Money.format(-row.amountCents) to PikColors.Expense
        TransactionType.INCOME -> Money.format(row.amountCents, showPlusSign = true) to PikColors.Income
        TransactionType.TRANSFER -> Money.format(row.amountCents) to PikColors.TextSecondary
        TransactionType.ADJUSTMENT -> Money.format(row.amountCents, showPlusSign = true) to
            (if (row.amountCents < 0) PikColors.Expense else PikColors.Income)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (row.type) {
            TransactionType.TRANSFER -> IconCircle(Icons.Filled.SwapHoriz, PikColors.TextSecondary, null)
            TransactionType.ADJUSTMENT -> IconCircle(Icons.Filled.Tune, PikColors.TextSecondary, null)
            else -> IconCircle(
                icon = CategoryStyle.icon(row.categoryIconKey),
                tint = CategoryStyle.color(row.categoryColorKey),
                contentDescription = null,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PikColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = amountText,
            style = PikNumberStyles.Medium,
            color = amountColor,
            maxLines = 1,
        )
    }
}


/** Top bar for pushed screens: back arrow and title. */
@Composable
fun PikBackTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = PikColors.TextPrimary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = PikColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Text-only selectable chip with explicit colors. */
@Composable
fun PikFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = PikColors.Surface,
            labelColor = PikColors.TextPrimary,
            selectedContainerColor = PikColors.Lime,
            selectedLabelColor = PikColors.OnLime,
        ),
        border = null,
    )
}
