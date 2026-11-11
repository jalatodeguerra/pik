package com.pik.app.domain

/** "Check balance": compare what the bank app shows with what Pik calculates. */
object BalanceCheck {
    /**
     * Signed amount of the ADJUSTMENT transaction that makes Pik match the real balance:
     * real - current. Zero means "already matches" and no transaction is needed.
     */
    fun adjustmentCents(realCents: Long, currentCents: Long): Long = realCents - currentCents
}
