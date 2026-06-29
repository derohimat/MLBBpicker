package ai.zasha.mlbbpicker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object DataPatchManager {
    private const val TAG = "DataPatchManager"
    private const val BASE_URL = "https://raw.githubusercontent.com/derohimat/MLBBpicker/main/app/src/main/assets"

    val FILES = listOf(
        "heroes.json",
        "counters.json",
        "synergies.json",
        "builds.json",
        "meta_stats.json"
    )

    fun getLocalFileText(context: Context, fileName: String): String {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read local patch file: $fileName, falling back to assets", e)
                context.assets.open(fileName).bufferedReader().use { it.readText() }
            }
        } else {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        }
    }

    suspend fun checkPatchExists(context: Context, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            File(context.filesDir, fileName).exists()
        }
    }

    suspend fun updatePatches(
        context: Context,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FILES.forEachIndexed { index, fileName ->
                onProgress(index.toFloat() / FILES.size, fileName)
                Log.d(TAG, "Downloading patch file: $fileName")
                val url = URL("$BASE_URL/$fileName")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "MLBBPicker/1.0")

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    // Validate JSON before saving
                    if (text.trim().startsWith("[") || text.trim().startsWith("{")) {
                        val tempFile = File(context.filesDir, "$fileName.tmp")
                        tempFile.writeText(text)
                        val targetFile = File(context.filesDir, fileName)
                        tempFile.renameTo(targetFile)
                        Log.d(TAG, "Successfully updated patch file: $fileName")
                    } else {
                        throw Exception("Invalid data received for $fileName")
                    }
                } else {
                    throw Exception("Failed to fetch $fileName: HTTP ${conn.responseCode}")
                }
            }
            onProgress(1.0f, "Completed")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update patches", e)
            Result.failure(e)
        }
    }

    suspend fun clearPatches(context: Context): Boolean = withContext(Dispatchers.IO) {
        var success = true
        FILES.forEach { fileName ->
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) success = false
            }
        }
        success
    }

    fun getLastUpdateTime(context: Context): String {
        val file = File(context.filesDir, "heroes.json")
        if (!file.exists()) return "Bundled Assets"
        val lastModified = file.lastModified()
        if (lastModified == 0L) return "Bundled Assets"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(lastModified))
    }
}
