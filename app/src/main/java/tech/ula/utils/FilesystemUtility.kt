package tech.ula.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    fun extractAndShareFilesystemByLocation(backupLocation: String, directory: String) {
        val backupLocationTmp = "${directory}.tmp"
        val command = "tar xpvzf ${backupLocation} ${backupLocationTmp} && rm -rf ${directory} && mv ${backupLocationTmp} ${backupLocation}"
        // TODO add in progress notification
        execUtility.wrapWithBusyboxAndExecute("${backupLocation}/support", command, doWait = true)
    }
}
