package dev.fabik.bluetoothhid.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.absoluteValue

data class ScanAreaData(
    val posX: Float = 0f,
    val posY: Float = 0f,
    val sizeX: Float = 100f,
    val sizeY: Float = 100f
) {
    // Converts to screen Rect using the same formula as OverlayCanvas CUSTOM type
    fun toRect(screenWidth: Float, screenHeight: Float): Rect {
        val cx = screenWidth / 2
        val cy = screenHeight / 2
        return Rect(
            Offset(cx + posX - sizeX.absoluteValue, cy + posY - sizeY.absoluteValue),
            Offset(cx + posX + sizeX.absoluteValue, cy + posY + sizeY.absoluteValue)
        )
    }

    fun withPosition(posX: Float, posY: Float) = copy(posX = posX, posY = posY)
    fun withSize(sizeX: Float, sizeY: Float) = copy(sizeX = sizeX, sizeY = sizeY)

    private fun toJson() = JSONObject().apply {
        put("posX", posX.toDouble())
        put("posY", posY.toDouble())
        put("sizeX", sizeX.toDouble())
        put("sizeY", sizeY.toDouble())
    }

    companion object {
        val DEFAULT = ScanAreaData()

        fun fromPrefs(posX: Float, posY: Float, sizeX: Float, sizeY: Float) =
            ScanAreaData(posX, posY, sizeX, sizeY)

        fun fromJsonArray(json: String): List<ScanAreaData> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ScanAreaData(
                        obj.getDouble("posX").toFloat(),
                        obj.getDouble("posY").toFloat(),
                        obj.getDouble("sizeX").toFloat(),
                        obj.getDouble("sizeY").toFloat()
                    )
                }
            }.getOrDefault(emptyList())
        }

        fun toJsonArray(areas: List<ScanAreaData>): String =
            JSONArray(areas.map { it.toJson() }).toString()

        // Computes the Compose Size for an area (for drag handle placement)
        fun ScanAreaData.toOverlaySize() = Size(sizeX, sizeY)
        fun ScanAreaData.toOverlayOffset() = Offset(posX, posY)
    }
}
