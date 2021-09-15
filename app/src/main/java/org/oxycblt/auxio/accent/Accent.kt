/*
 * Copyright (c) 2021 Auxio Project
 * Accent.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.accent

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.Spanned
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.text.HtmlCompat
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.resolveStateList

/**
 * A list of all possible accents.
 */
val ACCENTS = arrayOf(
    Accent(R.color.red, R.style.Theme_Auxio_Red, R.style.Theme_Auxio_Black_Red, R.string.clr_red),
    Accent(R.color.pink, R.style.Theme_Auxio_Pink, R.style.Theme_Auxio_Black_Pink, R.string.clr_pink),
    Accent(R.color.purple, R.style.Theme_Auxio_Purple, R.style.Theme_Auxio_Black_Purple, R.string.clr_purple),
    Accent(
        R.color.deep_purple,
        R.style.Theme_Auxio_DeepPurple,
        R.style.Theme_Auxio_Black_DeepPurple,
        R.string.clr_deep_purple
    ),
    Accent(R.color.indigo, R.style.Theme_Auxio_Indigo, R.style.Theme_Auxio_Black_Indigo, R.string.clr_indigo),
    Accent(R.color.blue, R.style.Theme_Auxio_Blue, R.style.Theme_Auxio_Black_Blue, R.string.clr_blue),
    Accent(
        R.color.light_blue,
        R.style.Theme_Auxio_LightBlue,
        R.style.Theme_Auxio_Black_LightBlue,
        R.string.clr_light_blue
    ),
    Accent(R.color.cyan, R.style.Theme_Auxio_Cyan, R.style.Theme_Auxio_Black_Cyan, R.string.clr_cyan),
    Accent(R.color.teal, R.style.Theme_Auxio_Teal, R.style.Theme_Auxio_Black_Teal, R.string.clr_teal),
    Accent(R.color.green, R.style.Theme_Auxio_Green, R.style.Theme_Auxio_Black_Green, R.string.clr_green),
    Accent(
        R.color.light_green,
        R.style.Theme_Auxio_LightGreen,
        R.style.Theme_Auxio_Black_LightGreen,
        R.string.clr_light_green
    ),
    Accent(R.color.lime, R.style.Theme_Auxio_Lime, R.style.Theme_Auxio_Black_Lime, R.string.clr_lime),
    Accent(R.color.yellow, R.style.Theme_Auxio_Yellow, R.style.Theme_Auxio_Black_Yellow, R.string.clr_yellow),
    Accent(R.color.orange, R.style.Theme_Auxio_Orange, R.style.Theme_Auxio_Black_Orange, R.string.clr_orange),
    Accent(
        R.color.deep_orange,
        R.style.Theme_Auxio_DeepOrange,
        R.style.Theme_Auxio_Black_DeepOrange,
        R.string.clr_deep_orange
    ),
    Accent(R.color.brown, R.style.Theme_Auxio_Brown, R.style.Theme_Auxio_Brown, R.string.clr_label_brown),
    Accent(R.color.grey, R.style.Theme_Auxio_Grey, R.style.Theme_Auxio_Black_Grey, R.string.clr_label_grey),
    Accent(
        R.color.blue_grey,
        R.style.Theme_Auxio_BlueGrey,
        R.style.Theme_Auxio_Black_BlueGrey,
        R.string.clr_blue_grey
    ),
)

/**
 * The data object for an accent.
 * @property color The color resource for this accent
 * @property theme The theme resource for this accent
 * @property blackTheme The black theme resource for this accent
 * @property name  The name of this accent
 * @author OxygenCobalt
 */
data class Accent(
    @ColorRes val color: Int,
    @StyleRes val theme: Int,
    @StyleRes val blackTheme: Int,
    @StringRes val name: Int
) {
    /**
     * Get a [ColorStateList] of the accent
     */
    fun getStateList(context: Context) = color.resolveStateList(context)

    /**
     * Get the name (in bold) and the hex value of a accent.
     */
    @SuppressLint("ResourceType")
    fun getDetailedSummary(context: Context): Spanned {
        val name = context.getString(name)
        val hex = context.getString(color).uppercase()

        return HtmlCompat.fromHtml(
            context.getString(R.string.fmt_accent_desc, name, hex),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
    }

    companion object {
        @Volatile
        private var CURRENT: Accent? = null

        /**
         * Get the current accent.
         * @return The current accent
         * @throws IllegalStateException When the accent has not been set.
         */
        fun get(): Accent {
            val cur = CURRENT

            if (cur != null) {
                return cur
            }

            error("Accent must be set before retrieving it.")
        }

        /**
         * Set the current accent.
         * @return The new accent
         */
        fun set(accent: Accent): Accent {
            synchronized(this) {
                CURRENT = accent
            }

            return accent
        }
    }
}
