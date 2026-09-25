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

    fun hasOfflinePatch(context: Context): Boolean {
        return FILES.all { File(context.filesDir, it).exists() }
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
            // Optional extras: data version info and per-rank meta stats listed in it.
            // Missing files are skipped so older data repos still update fine.
            if (downloadFile(context, DataVersion.FILE_NAME)) {
                val version = DataVersion.parse(File(context.filesDir, DataVersion.FILE_NAME).readText())
                version.ranks.map { it.file }.filter { it !in FILES }.distinct().forEach { rankFile ->
                    onProgress(0.99f, rankFile)
                    downloadFile(context, rankFile)
                }
            }
            MetaRankStore.load(context, force = true)
            onProgress(1.0f, "Completed")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update patches", e)
            Result.failure(e)
        }
    }

    /** Download one optional file; returns false (without throwing) when unavailable. */
    private fun downloadFile(context: Context, fileName: String): Boolean {
        return try {
            val conn = URL("$BASE_URL/$fileName").openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "MLBBPicker/1.0")
            if (conn.responseCode != 200) {
                Log.w(TAG, "Optional file $fileName not available: HTTP ${conn.responseCode}")
                return false
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val trimmed = text.trim()
            if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) return false
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(text)
            tempFile.renameTo(File(context.filesDir, fileName))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download optional file $fileName", e)
            false
        }
    }

    suspend fun clearPatches(context: Context): Boolean = withContext(Dispatchers.IO) {
        var success = true
        val versionFile = File(context.filesDir, DataVersion.FILE_NAME)
        val extraFiles = if (versionFile.exists()) {
            listOf(DataVersion.FILE_NAME) + DataVersion.parse(versionFile.readText()).ranks.map { it.file }
        } else {
            emptyList()
        }
        (FILES + extraFiles).distinct().forEach { fileName ->
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) success = false
            }
        }
        MetaRankStore.load(context, force = true)
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
