package tech.ula.utils

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.io.InputStream
import java.util.ArrayList
import java.lang.ProcessBuilder
import kotlin.text.Charsets.UTF_8


class ExecUtility(val fileUtility: FileUtility, val preferenceUtility: PreferenceUtility) {

    companion object {
        val EXEC_DEBUG_LOGGER = { line: String -> Unit
            Log.d("EXEC_DEBUG_LOGGER", line)
        }

        val NOOP_CONSUMER: (line: String) -> Int = { 0 }
    }

    fun execLocal(
        executionDirectory: File,
        command: ArrayList<String>,
        listener: (String) -> Any = NOOP_CONSUMER,
        doWait: Boolean = true,
        wrapped: Boolean = false
    ): Process {

        // TODO refactor naming convention to command debugging log
        val prootDebuggingEnabled = preferenceUtility.getProotDebuggingEnabled()
        val prootDebuggingLevel =
                if (prootDebuggingEnabled) preferenceUtility.getProotDebuggingLevel()
                else "-1"
        val prootDebugLogLocation = preferenceUtility.getProotDebugLogLocation()

        val env = if (wrapped) hashMapOf("LD_LIBRARY_PATH" to (fileUtility.getSupportDirPath()),
                "ROOT_PATH" to fileUtility.getFilesDirPath(),
                "ROOTFS_PATH" to "${fileUtility.getFilesDirPath()}/${executionDirectory.name}",
                "PROOT_DEBUG_LEVEL" to prootDebuggingLevel,
                "EXTRA_BINDINGS" to "-b ${Environment.getExternalStorageDirectory().getAbsolutePath()}:/sdcard")
        else hashMapOf()

        try {
            val pb = ProcessBuilder(command)
            pb.directory(executionDirectory)
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)

            listener("Running: ${pb.command()} \n with env $env")

            val process = pb.start()
            val logProot = prootDebuggingEnabled && command.any { it.contains("execInProot") }

            if (logProot) {
                writeDebugLogFile(process.inputStream, prootDebugLogLocation)
                listener("Output being redirected to PRoot debug log.")
            }

            if (doWait) {
                if (!logProot) {
                    collectOutput(process.inputStream, listener)
                }
                if (process.waitFor() != 0) {
                    listener("Exec: Failed to execute command ${pb.command()}")
                }
            }

            return process
        } catch (err: Exception) {
            listener("Exec: $err")
            throw RuntimeException(err)
        }
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        val buf = inputStream.bufferedReader(UTF_8)
        var str : String = ""
        buf.forEachLine {
            listener(it)
            str += it
        }

        buf.close()
    }

    private fun writeDebugLogFile(inputStream: InputStream, debugLogLocation: String) {
        // TODO Fix this bug. If logging is enabled and it doesn't write to a file, isServerInProcTree can't find dropbear.
        launch(CommonPool) {
            async {
                val reader = inputStream.bufferedReader(UTF_8)
                val writer = File(debugLogLocation).writer(UTF_8)
                reader.forEachLine {
                    writer.write("$it\n")
                }
                reader.close()
                writer.flush()
                writer.close()
            }
        }
    }

    fun wrapWithBusyboxAndExecute(targetDirectoryName: String, commandToWrap: String, listener: (String) -> Any = NOOP_CONSUMER, doWait: Boolean = true): Process {
        val executionDirectory = fileUtility.createAndGetDirectory(targetDirectoryName)
        val command = arrayListOf("../support/busybox", "sh", "-c", commandToWrap)

        return execLocal(executionDirectory, command, listener, doWait, wrapped = true)
    }


    fun backupFilesystemByLocation(execDirectory: String, directoryName: String, backupName: String, destinationDir: File) {
        val backupLocation = "../${backupName}"
        //launch { kotlinx.coroutines.experimental.async {
        val backupLocationTmp = "${backupLocation}.tmp"
        //val exclude = "--exclude=../1/sys --exclude=../1/dev --exclude=../1/proc --exclude=../1/data --exclude=../1/mnt --exclude=../1/host-rootfs --exclude=../1/sdcard --exclude=../1/etc/mtab --exclude=../1/etc/ld.so.preload"
        val exclude = "--exclude=../1/sys --exclude=../1/dev --exclude=../1/proc --exclude=../1/data --exclude=../1/mnt --exclude=../1/host-rootfs --exclude=../1/sdcard --exclude=../1/etc/mtab --exclude=../1/etc/ld.so.preload"
        //val command = "rm -rf ${backupLocationTmp} && tar ${exclude} -cvpzf ${backupLocationTmp} ${directoryName} && rm -rf ${backupLocation} && mv ${backupLocationTmp} ${backupLocation}"
        val command = "rm -rf ${backupLocationTmp} && tar ${exclude} -cvpzf ${backupLocationTmp} ../${directoryName}"
        // TODO add in progress notification
        wrapWithBusyboxAndExecute("${execDirectory}", "pwd", doWait = true)
        wrapWithBusyboxAndExecute("${execDirectory}", command, doWait = true)
        wrapWithBusyboxAndExecute("${execDirectory}", "rm -rf ${fileUtility.getFilesDirPath()}/${backupName} && mv ${backupLocationTmp} ${backupLocation}", doWait = true)
        val backupLocationF = File("${fileUtility.getFilesDirPath()}/${backupName}")
        backupLocationF.copyTo(File(destinationDir, backupName), overwrite = true)
        //}}
    }

    fun backupFilesystemByLocation2(execDirectory: String, directory: String, backupLocation: String, destinationDir: File) {
        //launch { kotlinx.coroutines.experimental.async {
        val backupLocationTmp = "${backupLocation}.tmp"
        val exclude = "--exclude=/data/user/0/tech.ula/files/1/sys --exclude=/data/user/0/tech.ula/files/1/dev --exclude=/data/user/0/tech.ula/files/1/proc --exclude=/data/user/0/tech.ula/files/1/data --exclude=/data/user/0/tech.ula/files/1/mnt --exclude=/data/user/0/tech.ula/files/1/host-rootfs --exclude=/data/user/0/tech.ula/files/1/sdcard --exclude=/data/user/0/tech.ula/files/1/etc/mtab --exclude=/data/user/0/tech.ula/files/1/etc/ld.so.preload"
        val command = "rm -rf ${backupLocationTmp} && tar ${exclude} cvpzf ${backupLocationTmp} ${directory} && rm -rf ${backupLocation} && mv ${backupLocationTmp} ${backupLocation}"
        // TODO add in progress notification
        wrapWithBusyboxAndExecute("${execDirectory}", command, doWait = true)
        val backupLocationF = File("${backupLocation}")
        backupLocationF.copyTo(destinationDir, overwrite = true)
        //}}
    }

    fun compressAndCopyFilesystemByLocation(directory: String, backupLocation: String, destination: String) {
        //launch { kotlinx.coroutines.experimental.async {
        val backupLocationTmp = "${backupLocation}.tmp"
        val command = "rm -rf ${backupLocationTmp} && tar cvpzf ${backupLocationTmp} ${directory} && mv ${backupLocationTmp} ${backupLocation}"

        // TODO add in progress notification
        wrapWithBusyboxAndExecute("${directory}/support", command, doWait = true)
        //}}
        val backupLocationF = File(backupLocation)
        val destinationF = File(destination)
        backupLocationF.copyTo(destinationF, overwrite = true)
        //Files.copy(new Path(backupLocation),destination);
    }


    fun extractAndShareFilesystemByLocation(backupLocation: String, directory: String) {
        //launch { kotlinx.coroutines.experimental.async {
        val backupLocationTmp = "${directory}.tmp"
        val command = "tar xpvzf ${backupLocation} ${backupLocationTmp} && rm -rf ${directory} && mv ${backupLocationTmp} ${backupLocation}"
        // TODO add in progress notification
        wrapWithBusyboxAndExecute("${backupLocation}/support", command, doWait = true)
        //}}
    }
}