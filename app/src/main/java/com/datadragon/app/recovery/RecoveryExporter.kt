package com.datadragon.app.recovery

import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The recovery build's file copier.
 *
 * This is a raw byte copier and nothing else. It never opens SQLite, never
 * builds a Room database, never reads a file through `SharedPreferences`, and
 * never interprets, parses or rewrites the bytes it moves. Every source file is
 * opened read-only, streamed into a ZIP entry, and left exactly as it was found
 * — same contents, same name, same modification time. Nothing is deleted.
 *
 * The ZIP is streamed straight to the `ContentResolver` output stream of the
 * document the user picked, so no intermediate archive is ever written into the
 * app's own private storage.
 */
object RecoveryExporter {

    /** The generated inventory, written as the last entry in the ZIP. */
    const val MANIFEST_NAME = "RECOVERY_MANIFEST.txt"

    /**
     * The directories that matter most. When one of these exists it is captured
     * whole: every file inside it is copied, with no extension or cache
     * filtering applied, because a file's name says nothing about whether it
     * holds recoverable state. `databases/` in particular must yield every file
     * it contains — the `-wal` and `-shm` files are as important as the `.db`.
     */
    private val ALWAYS_INCLUDED = listOf("databases", "shared_prefs", "files", "no_backup")

    /**
     * Directory names that hold runtime caches or executable code, never user
     * or application state. Matched case-insensitively at any depth outside the
     * always-included directories.
     */
    private val EXCLUDED_DIR_NAMES = setOf("cache", "code_cache", "lib", "oat", "dalvik-cache")

    /**
     * Compiled, packaged or optimized code artifacts. Matched case-insensitively
     * at any depth outside the always-included directories.
     */
    private val EXCLUDED_EXTENSIONS = setOf("apk", "apex", "art", "dex", "odex", "oat", "so", "vdex")

    /**
     * An *unknown* top-level directory bigger than this is inventoried rather
     * than copied, so one unexpectedly enormous generated directory can't
     * swallow the export. The always-included directories are never measured
     * against this limit — they are copied whatever their size.
     */
    private const val UNKNOWN_DIR_SIZE_LIMIT_BYTES = 512L * 1024L * 1024L

    private const val BUFFER_BYTES = 64 * 1024

    /** Number of files copied, and number that existed but could not be read. */
    data class ExportResult(val copied: Int, val failed: Int)

    /** A default document name for the Create Document flow. */
    fun suggestedFileName(): String = "data-dragon-recovery-${LocalDate.now()}.zip"

    /**
     * Copies the app's private data directory into a ZIP written to [target].
     *
     * Throws only when the export as a whole fails — the output document can't
     * be opened, or the ZIP can't be written. A single unreadable source file
     * is recorded in the manifest and does not fail the export, and never
     * affects the source file itself.
     */
    fun export(context: Context, target: Uri): ExportResult {
        val dataDir = File(context.applicationInfo.dataDir)
        val ledger = Ledger()

        val output = context.contentResolver.openOutputStream(target)
            ?: throw IOException("The chosen document could not be opened for writing.")

        output.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                copyDataDir(dataDir, zip, ledger)
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(buildManifest(context, dataDir, ledger).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        return ExportResult(copied = ledger.copied.size, failed = ledger.failed.size)
    }

    // ---------------------------------------------------------------- walking

    /**
     * Walks the top level of the private data directory, deciding the fate of
     * every entry: copied, intentionally excluded, or recorded as a failure.
     * Nothing is passed over silently.
     */
    private fun copyDataDir(dataDir: File, zip: ZipOutputStream, ledger: Ledger) {
        val children = dataDir.listFiles()
        if (children == null) {
            ledger.failed += Failure(dataDir.name, "The private data directory could not be listed.")
            return
        }

        // The irreplaceable directories go into the archive first, databases/
        // ahead of everything, so an export that dies part way through — out of
        // space, a crash, an interrupted write — still contains the files that
        // actually matter. Everything else follows alphabetically.
        val ordered = children.sortedWith(
            compareBy<File>({ priorityOf(it.name) }, { it.name.lowercase(Locale.US) }),
        )

        for (child in ordered) {
            val name = child.name
            val alwaysIncluded = ALWAYS_INCLUDED.any { it.equals(name, ignoreCase = true) }

            when {
                isSymbolicLink(child) ->
                    ledger.inventory += Inventory(name, "link", EXCLUDED, "Symbolic link — not followed.")

                child.isDirectory && alwaysIncluded -> {
                    copyTree(child, name, zip, ledger, filtered = false)
                    ledger.inventory += Inventory(name + "/", "directory", COPIED, "Captured whole.")
                }

                child.isDirectory && name.lowercase(Locale.US) in EXCLUDED_DIR_NAMES ->
                    ledger.inventory += Inventory(
                        name + "/",
                        "directory",
                        EXCLUDED,
                        "Runtime cache or executable code — excluded by policy.",
                    )

                child.isDirectory -> copyUnknownDirectory(child, name, zip, ledger)

                child.isFile && extensionOf(name) in EXCLUDED_EXTENSIONS ->
                    ledger.inventory += Inventory(
                        name,
                        "file",
                        EXCLUDED,
                        "Compiled or packaged code artifact — excluded by policy.",
                    )

                child.isFile -> {
                    val ok = copyFile(child, name, zip, ledger)
                    ledger.inventory += Inventory(
                        name,
                        "file",
                        if (ok) COPIED else FAILED,
                        if (ok) "Root-level private file." else "See the failure list below.",
                    )
                }

                else -> ledger.inventory += Inventory(
                    name,
                    "other",
                    EXCLUDED,
                    "Not a regular file or directory.",
                )
            }
        }

        for (expected in ALWAYS_INCLUDED) {
            if (children.none { it.name.equals(expected, ignoreCase = true) }) {
                ledger.missing += "$expected/"
            }
        }
    }

    /**
     * An application-private directory we don't recognize. It is copied unless
     * it is so large that it is plainly generated runtime material, in which
     * case it is inventoried with its measured size and the reason it was
     * skipped. Measuring reads directory metadata only.
     */
    private fun copyUnknownDirectory(dir: File, path: String, zip: ZipOutputStream, ledger: Ledger) {
        val size = treeSize(dir)
        if (size > UNKNOWN_DIR_SIZE_LIMIT_BYTES) {
            ledger.inventory += Inventory(
                "$path/",
                "directory",
                EXCLUDED,
                "Unexpectedly large unknown directory ($size bytes, over the " +
                    "$UNKNOWN_DIR_SIZE_LIMIT_BYTES byte limit) — treated as generated runtime material.",
            )
            return
        }
        copyTree(dir, path, zip, ledger, filtered = true)
        ledger.inventory += Inventory("$path/", "directory", COPIED, "Unknown private directory ($size bytes).")
    }

    /**
     * Recursively copies [dir] into the ZIP under [path], preserving the
     * original directory structure. When [filtered] is true the cache-directory
     * and code-artifact exclusions apply inside the tree; inside the
     * always-included directories they never do.
     */
    private fun copyTree(
        dir: File,
        path: String,
        zip: ZipOutputStream,
        ledger: Ledger,
        filtered: Boolean,
    ) {
        val children = dir.listFiles()
        if (children == null) {
            ledger.failed += Failure("$path/", "The directory could not be listed.")
            return
        }

        for (child in children.sortedBy { it.name.lowercase(Locale.US) }) {
            val childPath = "$path/${child.name}"
            when {
                isSymbolicLink(child) ->
                    ledger.skipped += Skipped(childPath, "Symbolic link — not followed.")

                child.isDirectory && filtered && child.name.lowercase(Locale.US) in EXCLUDED_DIR_NAMES ->
                    ledger.skipped += Skipped(
                        "$childPath/",
                        "Runtime cache or executable code — excluded by policy.",
                    )

                child.isDirectory -> copyTree(child, childPath, zip, ledger, filtered)

                child.isFile && filtered && extensionOf(child.name) in EXCLUDED_EXTENSIONS ->
                    ledger.skipped += Skipped(
                        childPath,
                        "Compiled or packaged code artifact — excluded by policy.",
                    )

                child.isFile -> copyFile(child, childPath, zip, ledger)

                else -> ledger.skipped += Skipped(childPath, "Not a regular file or directory.")
            }
        }
    }

    // ---------------------------------------------------------------- copying

    /**
     * Streams one source file into a ZIP entry, hashing the bytes as they pass
     * so the source is read exactly once and never written to.
     *
     * The source is opened read-only. If the read fails part way the partial
     * entry is closed and the file is recorded as a failure — the source file
     * itself is left untouched either way.
     */
    private fun copyFile(source: File, path: String, zip: ZipOutputStream, ledger: Ledger): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        var entryOpen = false
        return try {
            zip.putNextEntry(ZipEntry(path))
            entryOpen = true
            FileInputStream(source).use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    zip.write(buffer, 0, read)
                    written += read
                }
            }
            zip.closeEntry()
            ledger.copied += Copied(path, written, hex(digest.digest()))
            true
        } catch (t: Throwable) {
            if (entryOpen) {
                runCatching { zip.closeEntry() }
            }
            ledger.failed += Failure(
                path,
                "${t.javaClass.simpleName}: ${t.message ?: "no detail"} " +
                    "($written bytes copied before the failure; the source file was not modified).",
            )
            false
        }
    }

    // ------------------------------------------------------------- inspection

    /** Read-only metadata walk. Symbolic links are counted but not followed. */
    private fun treeSize(dir: File): Long {
        var total = 0L
        val children = dir.listFiles() ?: return total
        for (child in children) {
            if (isSymbolicLink(child)) continue
            total += if (child.isDirectory) treeSize(child) else child.length()
        }
        return total
    }

    private fun isSymbolicLink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    /** Sort key putting the always-included directories first, in listed order. */
    private fun priorityOf(name: String): Int {
        val index = ALWAYS_INCLUDED.indexOfFirst { it.equals(name, ignoreCase = true) }
        return if (index >= 0) index else ALWAYS_INCLUDED.size
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    // --------------------------------------------------------------- manifest

    /**
     * Builds the generated inventory. It documents what was copied, what was
     * intentionally left out, and what could not be read. It is a record of the
     * export — never a substitute for any source file.
     */
    private fun buildManifest(context: Context, dataDir: File, ledger: Ledger): String {
        val packageName = context.packageName
        var versionName = "unknown"
        var versionCode = "unknown"
        runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            versionName = info.versionName ?: "unknown"
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toString()
            }
        }

        return buildString {
            appendLine("Data Dragon — Recovery Manifest")
            appendLine("===============================")
            appendLine()
            appendLine("Export timestamp:   ${OffsetDateTime.now()}")
            appendLine("Package name:       $packageName")
            appendLine("Rescue versionName: $versionName")
            appendLine("Rescue versionCode: $versionCode")
            appendLine("Android version:    ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device model:       ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Private data dir:   ${dataDir.absolutePath}")
            appendLine()
            appendLine("This recovery build copied raw bytes only. It did not open the database")
            appendLine("through SQLite or Room, did not run a migration, checkpoint or vacuum, and")
            appendLine("did not read any file through the SharedPreferences API. No source file was")
            appendLine("written to, renamed, deleted, or had its modification time changed.")
            appendLine()

            section("TOP-LEVEL INVENTORY OF ${dataDir.absolutePath}", ledger.inventory.size)
            for (entry in ledger.inventory) {
                appendLine("${entry.status.padEnd(9)} ${entry.name}  [${entry.type}]")
                appendLine("          ${entry.reason}")
            }
            appendLine()

            section("FILES COPIED", ledger.copied.size)
            for (file in ledger.copied) {
                appendLine(file.path)
                appendLine("    bytes:  ${file.bytes}")
                appendLine("    sha256: ${file.sha256}")
            }
            appendLine()

            section("FILES THAT EXISTED BUT COULD NOT BE COPIED", ledger.failed.size)
            if (ledger.failed.isEmpty()) appendLine("(none)")
            for (failure in ledger.failed) {
                appendLine(failure.path)
                appendLine("    ${failure.reason}")
            }
            appendLine()

            section("ENTRIES SKIPPED INSIDE COPIED DIRECTORIES", ledger.skipped.size)
            if (ledger.skipped.isEmpty()) appendLine("(none)")
            for (skip in ledger.skipped) {
                appendLine(skip.path)
                appendLine("    ${skip.reason}")
            }
            appendLine()

            section("EXPECTED LOCATIONS THAT DID NOT EXIST", ledger.missing.size)
            if (ledger.missing.isEmpty()) appendLine("(none)")
            for (missing in ledger.missing) appendLine(missing)
        }
    }

    private fun StringBuilder.section(title: String, count: Int) {
        appendLine("-".repeat(74))
        appendLine("$title ($count)")
        appendLine("-".repeat(74))
    }

    // ---------------------------------------------------------------- records

    private const val COPIED = "COPIED"
    private const val EXCLUDED = "EXCLUDED"
    private const val FAILED = "FAILED"

    private class Ledger {
        val copied = mutableListOf<Copied>()
        val failed = mutableListOf<Failure>()
        val skipped = mutableListOf<Skipped>()
        val inventory = mutableListOf<Inventory>()
        val missing = mutableListOf<String>()
    }

    private data class Copied(val path: String, val bytes: Long, val sha256: String)

    private data class Failure(val path: String, val reason: String)

    private data class Skipped(val path: String, val reason: String)

    private data class Inventory(
        val name: String,
        val type: String,
        val status: String,
        val reason: String,
    )
}
