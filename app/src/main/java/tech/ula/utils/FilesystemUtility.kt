package tech.ula.utils

import android.app.Activity
import android.net.Uri
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment.getExternalStorageDirectory
import android.content.ContentResolver
import android.content.Context
import android.os.Environment
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class FilesystemUtility(
    private val execUtility: ExecUtility,
    private val fileUtility: FileUtility,
    private val buildUtility: BuildUtility
) {

    private fun getSupportDirectory(targetDirectoryName: String): File {
        return File("${fileUtility.getFilesDirPath()}/$targetDirectoryName/support")
    }

    fun extractFilesystem(targetDirectoryName: String, listener: (String) -> Any) {
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"
        execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, listener)
    }

    fun assetsArePresent(targetDirectoryName: String): Boolean {
        val supportDirectory = getSupportDirectory(targetDirectoryName)
        return supportDirectory.exists() &&
                supportDirectory.isDirectory &&
                supportDirectory.listFiles().isNotEmpty()
    }

    fun removeRootfsFilesFromFilesystem(targetDirectoryName: String) {
        val supportDirectory = getSupportDirectory(targetDirectoryName)
        supportDirectory.walkTopDown().forEach {
            if (it.name.contains("rootfs.tar.gz")) it.delete()
        }
    }

    fun deleteFilesystem(filesystemId: Long): Boolean {
        val directory = fileUtility.createAndGetDirectory(filesystemId.toString())
        return directory.deleteRecursively()
    }

    fun getArchType(): String {
        val supportedABIS = buildUtility.getSupportedAbis()
                .map {
                    translateABI(it)
                }
                .filter {
                    isSupported(it)
                }
        if (supportedABIS.size == 1 && supportedABIS[0] == "") {
            throw Exception("No supported ABI!")
        } else {
            return supportedABIS[0]
        }
    }

    private fun isSupported(abi: String): Boolean {
        val supportedABIs = listOf("arm64", "arm", "x86_64", "x86")
        return supportedABIs.contains(abi)
    }

    private fun translateABI(abi: String): String {
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> ""
        }
    }

    fun backupFilesystemByLocation(execDirectory: String, directoryName: String, backupName: String, destinationDir: File) {
        val backupLocation = "../${backupName}"
        val backupLocationTmp = "${backupLocation}.tmp"
        val exclude = "--exclude=sys --exclude=dev --exclude=proc --exclude=data --exclude=mnt --exclude=host-rootfs --exclude=sdcard --exclude=etc/mtab --exclude=etc/ld.so.preload"
        val commandCompress = "rm -rf ${backupLocationTmp} && tar ${exclude} -cvpzf ${backupLocationTmp} ../${directoryName}"
        val commandMove = "rm -rf ${fileUtility.getFilesDirPath()}/${backupName} && mv ${backupLocationTmp} ${backupLocation}"
        // TODO add in progress notification
        execUtility.wrapWithBusyboxAndExecute("${execDirectory}", commandCompress, doWait = true)
        execUtility.wrapWithBusyboxAndExecute("${execDirectory}", commandMove, doWait = true)
        val backupLocationF = File("${fileUtility.getFilesDirPath()}/${backupName}")
        // TODO refactor to not using str.replace
        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val now = Date()
        val fileName = "." + formatter.format(now) + ".tar.gz"
        backupLocationF.copyTo(File(destinationDir, backupName.replace(".tar.gz",fileName)), overwrite = true)
    }

    // https://developertip.wordpress.com/2012/12/11/android-copy-file-programmatically-from-its-uri/
    fun copyFileFromUri(context: Context, saveFilePath: String, fileUri: Uri): Boolean {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            val content = context.getContentResolver()
            inputStream = content.openInputStream(fileUri)

            val root = Environment.getExternalStorageDirectory()
            if (root == null) {
                throw Exception("Failed to get root")
            }

            // create a directory
            //val saveDirectory = File(Environment.getExternalStorageDirectory().path + File.separator + "directory_name" + File.separator)
            // create direcotory if it doesn't exists
            //saveDirectory.mkdirs()

            outputStream = FileOutputStream(saveFilePath)//saveDirectory.path + "f.tar.gz") // filename.png, .mp3, .mp4 ...
            if (outputStream != null) {
                //Log.e(TAG, "Output Stream Opened successfully")
            }

            val buffer = ByteArray(1000)
            // TODO progress loading could go here
            while ((inputStream.read(buffer, 0, buffer.size)) >= 0) {
                outputStream!!.write(buffer, 0, buffer.size)
            }
        } catch (e: Exception) {
            throw e
        }
        return true
    }

    // TODO move to FilesystemListFragment menu
    fun restoreFilesystemByLocation(execDirectory: String, activity: Activity, backupUri: Uri, restoreDirName: String) {
        // TODO add in progress notification
        val backupFileName = backupUri.path.substring(backupUri.path.lastIndexOf('/')+1, backupUri.path.length).replace(".tar.gz","")
        val restoreFileNameTmp = "${backupFileName}.tar.gz.restore.tmp"
        val restoreDirNameTmp = "${restoreDirName}.restore.tmp"
        val restoreFileTmp = "${fileUtility.getFilesDirPath()}/${restoreFileNameTmp}"
        val restoreDirTmp = "${fileUtility.getFilesDirPath()}/${restoreDirNameTmp}"
        val filesystemDir = "${fileUtility.getFilesDirPath()}/${restoreDirName}"
        //val backFile = File(URI(backupUri.toString()))
        val commandExtract = "rm -rf ${restoreDirTmp} && tar xpvzf ${restoreFileTmp} -C ${restoreDirTmp} && rm -rf ${filesystemDir} && mv ${restoreDirTmp} ${filesystemDir}"
        copyFileFromUri(context = activity, saveFilePath = restoreFileTmp, fileUri = backupUri)
        //backFile.copyTo(File(restoreFileTmp), overwrite = true)
        execUtility.wrapWithBusyboxAndExecute(execDirectory, commandExtract, doWait = true)
    }
}
