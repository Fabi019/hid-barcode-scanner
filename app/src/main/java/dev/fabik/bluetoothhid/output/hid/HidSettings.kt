package dev.fabik.bluetoothhid.output.hid

import dev.fabik.bluetoothhid.utils.ExtraKeys

class HidSettings(
    val sendDelay: Long,
    val extraKeys: ExtraKeys,
    val layoutLocale: String,
    val template: String,
    val expand: Boolean,
    val directlyApplyTemplate: Boolean,
)