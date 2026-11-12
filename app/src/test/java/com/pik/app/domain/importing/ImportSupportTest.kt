package com.pik.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSupportTest {

    // ---- merchant keys ----

    @Test fun normaliseUppercasesAndStripsDigitsAndPunctuation() {
        assertEquals("CONAD SUPERSTORE", MerchantKey.normalise("Conad Superstore 0123"))
        assertEquals("BAR ROMA", MerchantKey.normalise("  bar   roma!! "))
        assertEquals("AMAZON IT", MerchantKey.normalise("AMAZON.IT"))
    }

    @Test fun normaliseStripsPaypalPrefixes() {
        assertEquals("AMAZON EU", MerchantKey.normalise("PAYPAL *AMAZON EU 123"))
        assertEquals("AMAZON EU", MerchantKey.normalise("PayPal*Amazon EU"))
        assertEquals("AMAZON EU", MerchantKey.normalise("PP*AMAZON EU"))
    }

    @Test fun normaliseKeepsAccents() {
        assertEquals("CAFFÈ CENTRALE", MerchantKey.normalise("caffè centrale 5"))
    }

    @Test fun normaliseCanBeEmpty() {
        assertEquals("", MerchantKey.normalise("12345 -- 6"))
        assertEquals("", MerchantKey.normalise(""))
    }

    @Test fun lettersKeepsThePaypalPrefix() {
        assertTrue(MerchantKey.letters("PAYPAL *AMAZON").contains("PAYPAL"))
    }

    // ---- funding words ----

    @Test fun fundingWords() {
        assertTrue(FundingHints.isFunding("Money added"))
        assertTrue(FundingHints.isFunding("Top-Up by *1234"))
        assertTrue(FundingHints.isFunding("TOPUP"))
        assertTrue(FundingHints.isFunding("Bonifico bancario sul conto PayPal"))
        assertTrue(FundingHints.isFunding("Trasferimento generale con carta"))
        assertTrue(FundingHints.isFunding("Bank Deposit to PP Account"))
        assertFalse(FundingHints.isFunding("Mario"))
    }

    // ---- keys ----

    @Test fun sha256IsHex() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ImportKeys.sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ImportKeys.sha256Hex("abc"))
    }

    @Test fun notificationKeysIgnoreSecondsButNotMinutesOrText() {
        val a = ImportKeys.notificationKey("p", "k", 1_700_000_000_000, "text")
        assertEquals(a, ImportKeys.notificationKey("p", "k", 1_700_000_000_000 + 1_000, "text"))
        assertNotEquals(a, ImportKeys.notificationKey("p", "k", 1_700_000_000_000 + 120_000, "text"))
        assertNotEquals(a, ImportKeys.notificationKey("p", "k", 1_700_000_000_000, "other"))
        assertNotEquals(a, ImportKeys.notificationKey("q", "k", 1_700_000_000_000, "text"))
        assertNotEquals(a, ImportKeys.notificationKey("p", "j", 1_700_000_000_000, "text"))
    }

    // ---- app settings ----

    @Test fun appConfigRoundTrip() {
        val a = ImportAppConfig("com.revolut.revolut", ImportSource.REVOLUT, 4)
        assertEquals("com.revolut.revolut|REVOLUT|4", a.encode())
        assertEquals(a, ImportAppConfig.decode(a.encode()))
        val none = ImportAppConfig("com.x.y", ImportSource.OTHER_APP, null)
        assertEquals(none, ImportAppConfig.decode(none.encode()))
    }

    @Test fun appConfigRejectsGarbage() {
        assertNull(ImportAppConfig.decode(""))
        assertNull(ImportAppConfig.decode("a|b"))
        assertNull(ImportAppConfig.decode("a|NOPE|1"))
        assertNull(ImportAppConfig.decode("a|RELAX|x"))
        assertNull(ImportAppConfig.decode("|RELAX|1"))
    }

    // ---- known apps ----

    @Test fun knownAppsByLabel() {
        assertEquals(ImportSource.RELAX, KnownApps.sourceFor("it.bcc.relax", "Relax Banking"))
        assertEquals(ImportSource.NEXI, KnownApps.sourceFor("it.nexi.pay", "Nexi Pay"))
        assertEquals(ImportSource.REVOLUT, KnownApps.sourceFor("x", "REVOLUT"))
        assertEquals(ImportSource.PAYPAL, KnownApps.sourceFor("x", "PayPal"))
        assertNull(KnownApps.sourceFor("com.whatsapp", "WhatsApp"))
    }

    @Test fun knownAppsByPackage() {
        assertEquals(ImportSource.REVOLUT, KnownApps.sourceFor("com.revolut.revolut", "Banca"))
        assertEquals(ImportSource.PAYPAL, KnownApps.sourceFor("com.paypal.android.p2pmobile", "Pagamenti"))
    }

    @Test fun financeSources() {
        assertTrue(KnownApps.isFinance(ImportSource.NEXI))
        assertFalse(KnownApps.isFinance(ImportSource.OTHER_APP))
        assertFalse(KnownApps.isFinance(ImportSource.FILE))
    }
}
