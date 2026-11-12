package com.pik.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NotificationParserTest {

    private val day = LocalDate.of(2026, 11, 12)

    private fun parse(source: ImportSource, title: String, text: String, label: String = "") =
        NotificationParser.parse(source, title, text, day, label)

    private fun assertPayment(p: ParsedPayment?, cents: Long, direction: ImportDirection, merchant: String) {
        assertNotNull("expected a payment", p)
        assertEquals(ParsedPayment(cents, direction, merchant), p)
    }

    // ---- Nexi / Relax (Italian) ----

    @Test fun nexiPaymentAtAShop() = assertPayment(
        parse(ImportSource.NEXI, "Nexi Pay", "Pagamento di 12,50 EUR presso CONAD SUPERSTORE con carta ****1234 il 12/11"),
        1250, ImportDirection.OUT, "CONAD SUPERSTORE",
    )

    @Test fun relaxPaymentWithThousands() = assertPayment(
        parse(ImportSource.RELAX, "Relax Banking", "Pagamento di 1.234,56 € presso MEDIAWORLD con carta ****1234"),
        123456, ImportDirection.OUT, "MEDIAWORLD",
    )

    @Test fun authorisationOfIsAPayment() = assertPayment(
        parse(ImportSource.NEXI, "Nexi Pay", "Autorizzazione di 12,50 € ... presso AMAZON IT"),
        1250, ImportDirection.OUT, "AMAZON IT",
    )

    @Test fun cashWithdrawal() = assertPayment(
        parse(ImportSource.RELAX, "Relax Banking", "Prelievo di 50,00 € presso ATM"),
        5000, ImportDirection.OUT, "ATM",
    )

    @Test fun merchantEndsAtADot() = assertPayment(
        parse(ImportSource.NEXI, "Nexi", "Pagamento di 3,00 EUR presso Bar Roma. Grazie"),
        300, ImportDirection.OUT, "Bar Roma",
    )

    @Test fun merchantKeepsADotInsideAName() = assertPayment(
        parse(ImportSource.NEXI, "Nexi", "Acquisto di 9,99 EUR presso AMAZON.IT con carta ****1234"),
        999, ImportDirection.OUT, "AMAZON.IT",
    )

    @Test fun merchantEndsAtDel() = assertPayment(
        parse(ImportSource.RELAX, "Relax", "Pagamento di 20,00 EUR presso FARMACIA ROSSI del 12/11/2026"),
        2000, ImportDirection.OUT, "FARMACIA ROSSI",
    )

    @Test fun bankIncomingTransferWithDa() = assertPayment(
        parse(ImportSource.RELAX, "Relax Banking", "Accredito di 100,00 EUR da ROSSI MARIO"),
        10000, ImportDirection.IN, "ROSSI MARIO",
    )

    @Test fun bankOutgoingWithDa() = assertPayment(
        parse(ImportSource.RELAX, "Relax Banking", "Addebito di 25,00 EUR da ENEL ENERGIA"),
        2500, ImportDirection.OUT, "ENEL ENERGIA",
    )

    @Test fun bankIncomingBonifico() = assertPayment(
        parse(ImportSource.RELAX, "Relax Banking", "Bonifico in entrata di 1.500,00 € da ACME SRL"),
        150000, ImportDirection.IN, "ACME SRL",
    )

    @Test fun refundIsIncoming() = assertPayment(
        parse(ImportSource.NEXI, "Nexi", "Rimborso di 12,50 EUR da AMAZON"),
        1250, ImportDirection.IN, "AMAZON",
    )

    // ---- PayPal ----

    @Test fun paypalSentPayment() = assertPayment(
        parse(ImportSource.PAYPAL, "PayPal", "Hai inviato un pagamento di 20,00 EUR a Amazon"),
        2000, ImportDirection.OUT, "Amazon",
    )

    @Test fun paypalSentToAPerson() = assertPayment(
        parse(ImportSource.PAYPAL, "PayPal", "Hai inviato 20,00 € a Mario"),
        2000, ImportDirection.OUT, "Mario",
    )

    @Test fun paypalReceived() = assertPayment(
        parse(ImportSource.PAYPAL, "PayPal", "Hai ricevuto 15,00 € da Luca"),
        1500, ImportDirection.IN, "Luca",
    )

    @Test fun paypalReceivedAPayment() = assertPayment(
        parse(ImportSource.PAYPAL, "PayPal", "Hai ricevuto un pagamento di 15,00 EUR da Luca"),
        1500, ImportDirection.IN, "Luca",
    )

    // ---- Revolut (English, emoji) ----

    @Test fun revolutCardEmoji() = assertPayment(
        parse(ImportSource.REVOLUT, "Revolut", "💳 €12.50 at Lidl"),
        1250, ImportDirection.OUT, "Lidl",
    )

    @Test fun revolutYouPaid() = assertPayment(
        parse(ImportSource.REVOLUT, "Revolut", "You paid €12.50 to Lidl"),
        1250, ImportDirection.OUT, "Lidl",
    )

    @Test fun revolutPaidAt() = assertPayment(
        parse(ImportSource.REVOLUT, "Revolut", "Paid €4.20 at Bar Roma"),
        420, ImportDirection.OUT, "Bar Roma",
    )

    @Test fun revolutMoneyAdded() = assertPayment(
        parse(ImportSource.REVOLUT, "Revolut", "Money added: €50"),
        5000, ImportDirection.IN, "",
    )

    @Test fun revolutPlusFromPerson() = assertPayment(
        parse(ImportSource.REVOLUT, "Revolut", "+€50 from Mario"),
        5000, ImportDirection.IN, "Mario",
    )

    @Test fun revolutThousandsEnglish() = assertPayment(
        parse(ImportSource.REVOLUT, "Revolut", "You paid €1,234.56 to Apple"),
        123456, ImportDirection.OUT, "Apple",
    )

    @Test fun revolutReceivedFromPerson() = assertPayment(
        parse(ImportSource.REVOLUT, "Revolut", "You received €30.00 from Anna"),
        3000, ImportDirection.IN, "Anna",
    )

    // ---- direction without words ----

    @Test fun knownAppWithoutWordsIsOutgoing() = assertPayment(
        parse(ImportSource.REVOLUT, "Lidl", "-€12.50"),
        1250, ImportDirection.OUT, "Lidl",
    )

    @Test fun knownAppWithoutWordsAndNoSign() = assertPayment(
        parse(ImportSource.NEXI, "Nexi", "12,50 EUR"),
        1250, ImportDirection.OUT, "",
    )

    @Test fun unknownAppNeedsAPaymentWord() {
        assertNull(parse(ImportSource.OTHER_APP, "Some app", "Your total is 12,50 EUR"))
        assertPayment(
            parse(ImportSource.OTHER_APP, "Some app", "Pagamento di 12,50 EUR presso Bar Sport"),
            1250, ImportDirection.OUT, "Bar Sport",
        )
    }

    // ---- title fallback ----

    @Test fun titleIsTheMerchantWhenTextHasNone() = assertPayment(
        parse(ImportSource.REVOLUT, "Lidl", "You paid €12.50"),
        1250, ImportDirection.OUT, "Lidl",
    )

    @Test fun titleThatIsTheAppNameIsNotAMerchant() = assertPayment(
        parse(ImportSource.PAYPAL, "PayPal", "Hai inviato 20,00 EUR"),
        2000, ImportDirection.OUT, "",
    )

    @Test fun titleEqualToTheAppLabelIsNotAMerchant() = assertPayment(
        parse(ImportSource.OTHER_APP, "Mia Banca", "Pagamento di 12,50 EUR", label = "Mia Banca"),
        1250, ImportDirection.OUT, "",
    )

    @Test fun genericTitleIsNotAMerchant() = assertPayment(
        parse(ImportSource.NEXI, "Pagamento", "12,50 EUR"),
        1250, ImportDirection.OUT, "",
    )

    @Test fun titleWithPaymentWordsIsNotAMerchant() = assertPayment(
        parse(ImportSource.REVOLUT, "You paid", "€12.50"),
        1250, ImportDirection.OUT, "",
    )

    @Test fun merchantIsLimitedTo60Characters() {
        val long = "A".repeat(100)
        val p = parse(ImportSource.NEXI, "Nexi", "Pagamento di 1,00 EUR presso $long")
        assertEquals(60, p!!.merchant.length)
    }

    // ---- things that are not payments ----

    @Test fun securityAndLoginMessagesAreIgnored() {
        val texts = listOf(
            "Conferma il pagamento di 12,50 EUR presso CONAD",
            "Autorizza il pagamento di 12,50 EUR",
            "Codice OTP 123456 per il pagamento di 12,50 EUR",
            "Your OTP is 4821. Approve the payment of €12.50",
            "Please confirm your payment of €12.50",
            "Verify your payment of €12.50 to Lidl",
            "Nuovo accesso: accedi per vedere 12,50 EUR",
            "Login effettuato",
            "Hai raggiunto il limite di 500,00 EUR",
            "Pagamento di 12,50 EUR rifiutato",
            "Payment of €12.50 was declined",
            "Pagamento di 12,50 EUR non riuscito",
            "Payment of €12.50 failed",
        )
        for (text in texts) assertNull(text, parse(ImportSource.NEXI, "Nexi", text))
    }

    @Test fun balanceNoticesAreIgnored() {
        assertNull(parse(ImportSource.RELAX, "Relax Banking", "Saldo disponibile: 1.234,56 EUR"))
        assertNull(parse(ImportSource.REVOLUT, "Revolut", "Your balance is €100.00"))
    }

    @Test fun textWithoutAnAmountIsIgnored() {
        assertNull(parse(ImportSource.NEXI, "Nexi", "Pagamento effettuato presso CONAD"))
        assertNull(parse(ImportSource.NEXI, "", ""))
    }

    @Test fun zeroAmountIsIgnored() {
        assertNull(parse(ImportSource.NEXI, "Nexi", "Pagamento di 0,00 EUR presso CONAD"))
    }

    @Test fun cardDigitsAreNotAmounts() {
        assertNull(parse(ImportSource.NEXI, "Nexi", "Carta ****1234 bloccata il 12/11"))
    }

    // ---- several amounts ----

    @Test fun firstAmountWinsWhenAPaymentWordIsNextToIt() = assertPayment(
        parse(
            ImportSource.NEXI, "Nexi",
            "Pagamento di 12,50 EUR presso CONAD. Saldo disponibile 100,00 EUR",
        ),
        1250, ImportDirection.OUT, "CONAD",
    )

    @Test fun twoDifferentAmountsWithoutAPaymentWordAreRejected() {
        assertNull(parse(ImportSource.NEXI, "Nexi", "Soglia 5,00 EUR su 10,00 EUR"))
    }

    @Test fun theSameAmountTwiceIsFine() = assertPayment(
        parse(ImportSource.NEXI, "Nexi", "Pagamento di 12,50 EUR presso CONAD. Importo 12,50 EUR"),
        1250, ImportDirection.OUT, "CONAD",
    )

    // ---- words ----

    @Test fun shortWordsNeedToBeWholeWords() {
        // "sent" inside "presente" is not a payment word; the notification has no payment word at all
        assertNull(parse(ImportSource.OTHER_APP, "App", "Il presente avviso costa 5,00 EUR"))
    }

    @Test fun mixedCaseIsFine() = assertPayment(
        parse(ImportSource.NEXI, "NEXI", "PAGAMENTO DI 12,50 EUR PRESSO CONAD"),
        1250, ImportDirection.OUT, "CONAD",
    )

    @Test fun newLinesInTheTextAreFine() = assertPayment(
        parse(ImportSource.NEXI, "Nexi Pay", "Pagamento\nImporto 12,50 EUR\nPresso CONAD\nCarta ****1234"),
        1250, ImportDirection.OUT, "CONAD",
    )
}
