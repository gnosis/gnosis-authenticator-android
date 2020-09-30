package io.gnosis.safe.utils

import io.gnosis.safe.utils.mnemonic.InlineWordListProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.toHexString

class MnemonicKeyAndAddressDerivationTest {

    val keyAndAddressDerivation: MnemonicKeyAndAddressDerivation = MnemonicKeyAndAddressDerivation(InlineWordListProvider())

    @Before
    fun setUp() {
        keyAndAddressDerivation.initialize("creek banner employ mix teach sunny sure mutual pole mom either lion")

    }

    @Test
    fun `addressesForRange (0-0) should return first address`() {
        val firstAddress = keyAndAddressDerivation.addressesForRange(0L..0L)

        assertEquals("Wrong list size", 1, firstAddress.size)
        assertEquals("0xE86935943315293154c7AD63296b4e1adAc76364".asEthereumAddress(), firstAddress[0])
    }

    @Test
    fun `addressesForRange (0-2) should return first three addresses`() {
        val firstAddress = keyAndAddressDerivation.addressesForRange(0L..2L)

        assertEquals("Wrong list size", 3, firstAddress.size)
        assertEquals("0xE86935943315293154c7AD63296b4e1adAc76364".asEthereumAddress(), firstAddress[0])
        assertEquals("0x5c9E7b93900536D9cc5559b881375Bae93c933D0".asEthereumAddress(), firstAddress[1])
        assertEquals("0xD28293bf13549Abb49Ed1D83D515301A05E3Fc8d".asEthereumAddress(), firstAddress[2])
    }

    @Test
    fun `addressesForRange (2-2) should return third address`() {
        val firstAddress = keyAndAddressDerivation.addressesForRange(2L..2L)

        assertEquals("Wrong list size", 1, firstAddress.size)
        assertEquals("0xD28293bf13549Abb49Ed1D83D515301A05E3Fc8d".asEthereumAddress(), firstAddress[0])
    }

    @Test
    fun `addressesForPage (PageSize 1) should return first address`() {

        val firstAddress = keyAndAddressDerivation.addressesForPage(0, 1)

        assertEquals("Wrong list size", 1, firstAddress.size)
        assertEquals("0xE86935943315293154c7AD63296b4e1adAc76364".asEthereumAddress(), firstAddress[0])
    }

    @Test
    fun `addressesForPage (PageSize 0) should return empty list`() {

        val firstAddress = keyAndAddressDerivation.addressesForPage(0, 0)

        assertEquals("Wrong list size", 0, firstAddress.size)
    }

    @Test
    fun `addressesForPage (PageSize -1) should return empty list`() {

        val firstAddress = keyAndAddressDerivation.addressesForPage(0, -1)

        assertEquals("Wrong list size", 0, firstAddress.size)
    }

    @Test
    fun `keyForIndex (inde 0) should return correct private key`() {
        val secretKey = keyAndAddressDerivation.keyForIndex(0L)

        assertEquals("Wrong secret key", "0xda18066dda40499e6ef67a392eda0fd90acf804448a765db9fa9b6e7dd15c322", secretKey.toHexString())
    }
}
