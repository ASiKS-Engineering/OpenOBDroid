package com.openobdroid.app

import java.util.Locale

object DtcParser {

    fun parse(
        response: String,
    ): List<String> {

        val clean =
            response
                .replace(" ","")
                .replace("\r","")
                .replace("\n","")

        if (!clean.startsWith("43"))
            return emptyList()

        val result =
            mutableListOf<String>()

        var index = 2

        while (
            (index + 3) < clean.length
        ) {

            val code =
                clean.substring(
                    index,
                    index + 4
                )

            if (code == "0000")
                break

            result.add(
                decode(code)
            )

            index += 4
        }

        return result
    }

    private fun decode(
        hex:String
    ): String {

        val first =
            hex.substring(0,2)
                .toInt(16)

        val second =
            hex.substring(2,4)

        val prefix =
            when((first and 0xC0) shr 6) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }

        val d1 =
            ((first and 0x30) shr 4)

        val d2 =
            (first and 0x0F)

        return String.format(
            Locale.US,
            "%s%d%01X%s",
            prefix,
            d1,
            d2,
            second
        )
    }
}