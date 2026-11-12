package com.pik.app.domain.importing

import com.pik.app.domain.TransactionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** An account as the matcher sees it. [isBank] is true for the "Bank" account type. */
data class AccountRef(val id: Long, val name: String, val isBank: Boolean)

/** An existing transaction of Pik. */
data class TxRef(
    val id: Long,
    val type: TransactionType,
    val amountCents: Long,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long?,
)

/** An inbox item as the matcher sees it. */
data class ItemRef(
    val id: Long,
    val source: ImportSource,
    val accountId: Long?,
    val direction: ImportDirection,
    val amountCents: Long,
    val merchant: String,
    val rawText: String,
    val date: LocalDate,
    val status: ImportStatus,
    val suggestion: ImportSuggestion,
    val transferAccountId: Long?,
    val suggestedCategoryId: Long?,
    /** The user brought it back from "Already in Pik": never marked as a duplicate again. */
    val restored: Boolean,
)

data class MatchResult(
    val itemId: Long,
    val status: ImportStatus,
    val suggestion: ImportSuggestion,
    val transferAccountId: Long?,
    val suggestedCategoryId: Long?,
)

/**
 * Everything the matcher knows besides the items it works on. [otherItems] are all inbox items that are not
 * dismissed (the items being matched may be in it). [linkedTransactionIds] are the transactions that an
 * inbox item already created. [rules] maps a normalised merchant to a category id.
 */
class MatchContext(
    val accounts: List<AccountRef>,
    val transactions: List<TxRef>,
    val linkedTransactionIds: Set<Long>,
    val otherItems: List<ItemRef>,
    val rules: Map<String, Long>,
    val bankAccountId: Long?,
)

/** Pure matching of inbox items: duplicates, transfers between own accounts, and category suggestions. */
object ImportMatcher {
    const val DATE_TOLERANCE_DAYS = 3L

    private val WALLETS = listOf("PAYPAL", "REVOLUT")

    fun defaultSuggestion(direction: ImportDirection): ImportSuggestion =
        if (direction == ImportDirection.OUT) ImportSuggestion.EXPENSE else ImportSuggestion.INCOME

    /** True for an account named like a wallet app (PayPal, Revolut). */
    fun isWalletName(name: String): Boolean {
        val letters = MerchantKey.letters(name)
        return WALLETS.any { letters.contains(it) }
    }

    /**
     * The account that money for transfers comes from: the first of [preferredIds] (accounts mapped to the
     * bank apps) that exists, else the first bank account that is not a wallet.
     */
    fun pickBankAccount(accounts: List<AccountRef>, preferredIds: List<Long>): Long? {
        for (id in preferredIds) if (accounts.any { it.id == id }) return id
        return accounts.firstOrNull { it.isBank && !isWalletName(it.name) }?.id
    }

    /** The Pik account that probably belongs to an app: by name for Revolut and PayPal, the bank account for the others. */
    fun defaultAccountFor(source: ImportSource, accounts: List<AccountRef>): Long? = when (source) {
        ImportSource.REVOLUT -> accounts.firstOrNull { MerchantKey.letters(it.name).contains("REVOLUT") }?.id
        ImportSource.PAYPAL -> accounts.firstOrNull { MerchantKey.letters(it.name).contains("PAYPAL") }?.id
        ImportSource.RELAX, ImportSource.NEXI -> pickBankAccount(accounts, emptyList())
        else -> null
    }

    /**
     * Works out status, suggestion and category for the [pending] items (in the order of their ids). Pass 1
     * decides what each item would become; pass 2 finds the duplicates, so a transfer's two legs find each
     * other whatever order they arrived in.
     */
    fun matchPending(pending: List<ItemRef>, ctx: MatchContext): List<MatchResult> {
        val ordered = pending.sortedBy { it.id }
        val state = HashMap<Long, ItemRef>()
        for (other in ctx.otherItems) state[other.id] = other

        // pass 1: suggestions and categories
        for (item in ordered) {
            val (suggestion, transferId) = suggest(item, ctx)
            val category = when (suggestion) {
                ImportSuggestion.EXPENSE, ImportSuggestion.INCOME ->
                    item.suggestedCategoryId ?: ctx.rules[MerchantKey.normalise(item.merchant)]
                else -> null
            }
            state[item.id] = item.copy(
                suggestion = suggestion,
                transferAccountId = transferId,
                suggestedCategoryId = category,
                status = ImportStatus.PENDING,
            )
        }

        // pass 2: duplicates
        val claimed = HashSet<Long>()
        val results = ArrayList<MatchResult>()
        for (item in ordered) {
            val current = state.getValue(item.id)
            var duplicate = false
            if (!item.restored && item.accountId != null) {
                val tx = findTransaction(current, ctx, claimed)
                if (tx != null) {
                    claimed.add(tx.id)
                    duplicate = true
                } else if (matchesOlderItem(current, state.values)) {
                    duplicate = true
                } else if (current.suggestion == ImportSuggestion.TRANSFER_IN && hasOutLeg(current, state.values)) {
                    duplicate = true
                }
            }
            val status = if (duplicate) ImportStatus.DUPLICATE else ImportStatus.PENDING
            state[item.id] = current.copy(status = status)
            results.add(
                MatchResult(item.id, status, current.suggestion, current.transferAccountId, current.suggestedCategoryId),
            )
        }
        return results
    }

    /** Transfer to a wallet account, funding of a wallet from the bank, or a plain expense / income. */
    private fun suggest(item: ItemRef, ctx: MatchContext): Pair<ImportSuggestion, Long?> {
        val accountId = item.accountId ?: return defaultSuggestion(item.direction) to null
        if (item.direction == ImportDirection.OUT) {
            val text = MerchantKey.letters(item.merchant)
            for (wallet in WALLETS) {
                if (!text.contains(wallet)) continue
                val target = ctx.accounts.firstOrNull { it.id != accountId && MerchantKey.letters(it.name).contains(wallet) }
                if (target != null) return ImportSuggestion.TRANSFER_OUT to target.id
            }
        } else {
            val own = ctx.accounts.firstOrNull { it.id == accountId }
            val bank = ctx.bankAccountId
            if (own != null && isWalletName(own.name) && bank != null && bank != accountId &&
                FundingHints.isFunding(item.merchant + " " + item.rawText)
            ) {
                return ImportSuggestion.TRANSFER_IN to bank
            }
        }
        return defaultSuggestion(item.direction) to null
    }

    private fun near(a: LocalDate, b: LocalDate): Boolean = abs(ChronoUnit.DAYS.between(a, b)) <= DATE_TOLERANCE_DAYS

    /** The existing transaction on the same account, amount, direction and (nearly) day, not yet used. */
    private fun findTransaction(item: ItemRef, ctx: MatchContext, claimed: Set<Long>): TxRef? {
        val account = item.accountId ?: return null
        return ctx.transactions
            .filter { it.id !in ctx.linkedTransactionIds && it.id !in claimed }
            .filter { near(it.date, item.date) && sameMoney(it, item, account) }
            .minByOrNull { abs(ChronoUnit.DAYS.between(it.date, item.date)) }
    }

    private fun sameMoney(tx: TxRef, item: ItemRef, account: Long): Boolean {
        val out = item.direction == ImportDirection.OUT
        return when (tx.type) {
            TransactionType.EXPENSE -> out && tx.accountId == account && tx.amountCents == item.amountCents
            TransactionType.INCOME -> !out && tx.accountId == account && tx.amountCents == item.amountCents
            TransactionType.TRANSFER ->
                tx.amountCents == item.amountCents &&
                    ((out && tx.accountId == account) || (!out && tx.toAccountId == account))
            TransactionType.ADJUSTMENT ->
                tx.accountId == account && abs(tx.amountCents) == item.amountCents &&
                    ((tx.amountCents < 0) == out)
        }
    }

    /**
     * An older, not dismissed item for the same payment. A notification and a file row are the same payment;
     * two notifications (or two file rows) with the same amount are two payments (two coffees), so those are
     * not compared with each other.
     */
    private fun matchesOlderItem(item: ItemRef, others: Collection<ItemRef>): Boolean =
        others.any {
            it.id < item.id &&
                (it.status == ImportStatus.PENDING || it.status == ImportStatus.ADDED) &&
                it.accountId == item.accountId &&
                it.direction == item.direction &&
                it.amountCents == item.amountCents &&
                near(it.date, item.date) &&
                (it.source == ImportSource.FILE) != (item.source == ImportSource.FILE)
        }

    /** The OUT leg of a transfer into this wallet account is also in the inbox (or already added). */
    private fun hasOutLeg(item: ItemRef, others: Collection<ItemRef>): Boolean =
        others.any {
            it.id != item.id &&
                (it.status == ImportStatus.PENDING || it.status == ImportStatus.ADDED) &&
                it.suggestion == ImportSuggestion.TRANSFER_OUT &&
                it.transferAccountId == item.accountId &&
                it.amountCents == item.amountCents &&
                near(it.date, item.date)
        }
}
