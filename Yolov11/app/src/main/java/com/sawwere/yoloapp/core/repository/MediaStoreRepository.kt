package com.sawwere.yoloapp.core.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sawwere.yoloapp.core.config.SaveConfig
import java.io.IOException

class MediaStoreRepository {

    fun saveToGallery(context: Context, bitmap: Bitmap, folderName: String): SaveResult {
        return try {
            val collection = getMediaCollectionUri()
            val contentValues = createContentValues(folderName)
            val resolver = context.contentResolver

            val uri = resolver.insert(collection, contentValues)
                ?: return SaveResult.Error("Failed to create MediaStore entry")

            writeBitmapToStream(resolver, uri, bitmap, contentValues)

            SaveResult.Success(getSavedPath(uri, contentValues))
        } catch (e: Exception) {
            SaveResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun getMediaCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun createContentValues(folderName: String): ContentValues {
        return ContentValues().apply {
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$folderName"
                )
            } else {
                val directory = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val folder = directory.resolve(folderName).apply { mkdirs() }
                put(MediaStore.Images.Media.DATA, folder.resolve(fileName).absolutePath)
            }
        }
    }

    private fun writeBitmapToStream(
        resolver: ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
        contentValues: ContentValues
    ) {
        resolver.openOutputStream(uri)?.use { outputStream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, SaveConfig.quality, outputStream)) {
                throw IOException("Failed to compress bitmap")
            }
        } ?: throw IOException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    private fun getSavedPath(uri: Uri, contentValues: ContentValues): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Pictures/${contentValues.getAsString(MediaStore.Images.Media.RELATIVE_PATH)}"
        } else {
            contentValues.getAsString(MediaStore.Images.Media.DATA) ?: "Unknown path"
        }
    }
}

sealed class SaveResult {
    data class Success(val path: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
}
