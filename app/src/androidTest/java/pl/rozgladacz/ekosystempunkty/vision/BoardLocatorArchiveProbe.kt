package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File

/** Local analysis helper; private fixture data is never included in APK assets. */
class BoardLocatorArchiveProbe {
    @Test
    fun compareAutomaticCornersWithConfirmedArchive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val root = "/data/local/tmp/ekosystem-locator-archive"
        fun readShell(command: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command),
        ).use { it.readBytes() }
        check(OpenCVLoader.initLocal())
        val output = JSONArray()
        val names = readShell("ls $root/metadata").decodeToString().trim().lines().sorted()
        assumeTrue("Private capture archive is not included in Git or CI",
            names.isNotEmpty() && names.all { it.matches(Regex("[0-9a-f-]+\\.json")) })
        names.forEach { name ->
            check(name.matches(Regex("[0-9a-f-]+\\.json")))
            val metadata = JSONObject(readShell("cat $root/metadata/$name").decodeToString())
            val id = metadata.getString("capture_id")
            check(id.matches(Regex("[0-9a-f-]+")))
            val bytes = readShell("cat $root/images/$id.jpg")
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                val table = metadata.optInt("schema_version", 1) == 2
                val locations = (if (table) MultiBoardLocator.locate(bitmap) else listOf(BoardLocator.locate(bitmap))).toMutableList()
                val boards = if (table) metadata.getJSONArray("boards").let { array -> List(array.length()) { array.getJSONObject(it) } } else listOf(metadata)
                boards.filter { it.optString("status") == "labeled" }.forEach { board ->
                    val confirmed = board.getJSONArray("corners")
                    val match = locations.flatMapIndexed { index, location ->
                        (0..3).map { turn ->
                            val points = location.corners.drop(turn) + location.corners.take(turn)
                            val error = points.indices.sumOf { i ->
                                val point = confirmed.getJSONArray(i)
                                kotlin.math.hypot(points[i].x - point.getDouble(0), points[i].y - point.getDouble(1))
                            }
                            Triple(index, points, error)
                        }
                    }.minByOrNull { it.third }
                    output.put(JSONObject().apply {
                        put("capture_id", id)
                        if (table) put("board_id", board.getString("board_id"))
                        put("confirmed_corners", confirmed)
                        put("detected", match != null)
                        if (match != null) put("automatic_corners", JSONArray().apply {
                            match.second.forEach { put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
                        })
                    })
                    if (match != null) locations.removeAt(match.first)
                }
            } finally {
                bitmap.recycle()
            }
        }
        File(context.getExternalFilesDir(null), "locator-archive-results.json").writeText(output.toString(2))
    }
}
