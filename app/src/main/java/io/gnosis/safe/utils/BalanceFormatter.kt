package io.gnosis.safe.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

class BalanceFormatter {

    private val formatter1k = DecimalFormat("#.#####")
    private val formatter10k = DecimalFormat("#,###.####")
    private val formatter100k = DecimalFormat("##,###.###")
    private val formatter1M = DecimalFormat("###,###.##")
    private val formatter10M = DecimalFormat("#,###,###.#")
    private val formatter100M = DecimalFormat("#,###,###")
    private val formatterBigNumber = DecimalFormat("#.###").apply {
        roundingMode = RoundingMode.DOWN
    }

    fun shortAmount(value: BigDecimal): String = when {
        value < LOWEST_LIMIT -> {
            "< 0.00001"
        }
        value <= THOUSAND_LIMIT -> {
            formatter1k.format(value)
        }
        value <= TEN_THOUSAND_LIMIT -> {
            formatter10k.format(value)
        }
        value <= HUNDRED_THOUSAND_LIMIT -> {
            formatter100k.format(value)
        }
        value <= MILLION_LIMIT -> {
            formatter1M.format(value)
        }
        value <= TEN_MILLION_LIMIT -> {
            formatter10M.format(value)
        }
        value <= HUNDRED_MILLION_LIMIT -> {
            formatter100M.format(value)
        }
        value <= BILLION_LIMIT -> {
            val formattedValue = value.divide(BigDecimal.TEN.pow(6))
            formatterBigNumber.format(formattedValue) + "M"
        }
        value <= TRILLION_LIMIT -> {
            val formattedValue = value.divide(BigDecimal.TEN.pow(9))
            formatterBigNumber.format(formattedValue) + "B"
        }
        value <= THOUSAND_TRILLION_LIMIT -> {
            val formattedValue = value.divide(BigDecimal.TEN.pow(12))
            formatterBigNumber.format(formattedValue) + "T"
        }
        else -> {
            "> 999T"
        }
    }

    companion object {
        private val LOWEST_LIMIT = BigDecimal.ONE.divide(BigDecimal.TEN.pow(5))
        private val THOUSAND_LIMIT = BigDecimal.TEN.pow(3)
        private val TEN_THOUSAND_LIMIT = BigDecimal.TEN.pow(4)
        private val HUNDRED_THOUSAND_LIMIT = BigDecimal.TEN.pow(5)
        private val MILLION_LIMIT = BigDecimal.TEN.pow(6)
        private val TEN_MILLION_LIMIT = BigDecimal.TEN.pow(7)
        private val HUNDRED_MILLION_LIMIT = BigDecimal.TEN.pow(8)
        private val BILLION_LIMIT = BigDecimal.TEN.pow(9)
        private val TRILLION_LIMIT = BigDecimal.TEN.pow(12)
        private val THOUSAND_TRILLION_LIMIT = BigDecimal.TEN.pow(15)
    }
}
