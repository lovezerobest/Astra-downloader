package com.astradownloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.min

/** 单个下载任务的实时状态。 */
enum class DownloadState { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadStats(val speed: Long = 0, val remainMillis: Long = -1, val chunkCount: Int = 1)

/** 任务进度回调（供 Island 卡片 / UI 消费）。 */
interface DownloadListener {
    fun onProgress(taskId: Long, downloaded: Long, total: Long, stats: DownloadStats)
    fun onStateChanged(taskId: Long, state: DownloadState)
}

/**
 * 精简版分片下载管理器（进程内存态）。
 * - Range 分片 + 多线程并发（Semaphore 限并发）
 * - 断点续传（part 文件按已有大小续）
 * - 完成后顺序合并分片
 * - 暂停 / 取消
 */
class DownloadManager(
    private val downloader: ChunkDownloader,
    private val saveDir: File,
    private val threadCountProvider: () -> Int = { 8 },
    private val listener: DownloadListener? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val idCounter = AtomicLong(0)

    private class Task(
        val id: Long,
        val url: String,
        val fileName: String,
        val headers: Map<String, String>,
        val workDir: File
    ) {
        var total: Long = -1L
        var downloaded: Long = 0L
        @Volatile var state: DownloadState = DownloadState.PENDING
        @Volatile var job: Job? = null
        @Volatile var ignoreRange: Boolean = false
    }

    private val tasks = ConcurrentHashMap<Long, Task>()

    /** 诊断日志：打印打 logcat，同时追加写入可读文件（进程被杀也能留痕）。 */
    private fun dlog(msg: String) {
        runCatching {
            android.util.Log.d("DL", msg)
            val dir = java.io.File("/data/local/tmp/eta/astra_dl").apply { mkdirs() }
            java.io.File(dir, "dl.log").appendText(
                java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(java.util.Date()) + " " + msg + "\n"
            )
        }
    }

    private val taskMutex = ConcurrentHashMap<Long, Mutex>()
    private val downloadedByTask = ConcurrentHashMap<Long, AtomicLong>()
    private val lastStatsAt = ConcurrentHashMap<Long, AtomicLong>()

    fun enqueue(url: String, fileName: String, headers: Map<String, String> = emptyMap(), total: Long = -1L): Long {
        val safeName = fileName.ifBlank {
            url.substringAfterLast('/').substringBefore('?').ifBlank { "download_${System.currentTimeMillis()}" }
        }
        val id = idCounter.incrementAndGet()
        val workDir = File(saveDir, "task_$id").apply { mkdirs() }
        val task = Task(id, url, safeName, headers, workDir)
        if (total > 0) task.total = total
        tasks[id] = task
        start(id)
        return id
    }

    fun start(id: Long) {
        val task = tasks[id] ?: return
        if (task.state == DownloadState.DOWNLOADING) return
        listener?.onStateChanged(id, DownloadState.DOWNLOADING)
        task.state = DownloadState.DOWNLOADING
        task.job = scope.launch {
            try {
                runTask(task)
            } catch (e: CancellationException) {
                // 暂停 / 取消
            } catch (e: Exception) {
                task.state = DownloadState.FAILED
                listener?.onStateChanged(task.id, DownloadState.FAILED)
            }
        }
    }

    fun pause(id: Long) {
        val task = tasks[id] ?: return
        if (task.state != DownloadState.DOWNLOADING) return
        task.state = DownloadState.PAUSED
        listener?.onStateChanged(id, DownloadState.PAUSED)
        downloader.cancelCalls(id)
        task.job?.cancel()
    }

    fun cancel(id: Long) {
        val task = tasks[id] ?: return
        downloader.cancelCalls(id)
        task.job?.cancel()
        task.workDir.deleteRecursively()
        tasks.remove(id)
        listener?.onStateChanged(id, DownloadState.FAILED)
    }

    fun getState(id: Long): DownloadState? = tasks[id]?.state

    private suspend fun runTask(task: Task) = coroutineScope {
        taskMutex.getOrPut(task.id) { Mutex() }.withLock {
            var total = task.total
            if (total <= 0) {
                total = downloader.getTotalSize(task.url, task.headers) ?: -1L
                task.total = total
                dlog("runTask probed total=$total")
            }

            val workers = threadCountProvider().coerceIn(1, 64)

            if (total > 0) {
                runChunked(task, total, workers)
            } else {
                runFull(task)
            }

            dlog("runTask task=${task.id} total=$total downloaded=${task.downloaded} state=${task.state}")
            if (task.state == DownloadState.DOWNLOADING) {
                val merged = mergeParts(task)
                dlog("runTask mergeParts merged=$merged downloaded=${task.downloaded} total=$total")
                if (merged) {
                    task.state = DownloadState.COMPLETED
                    listener?.onStateChanged(task.id, DownloadState.COMPLETED)
                    dlog("runTask COMPLETED total=$total")
                } else {
                    dlog("runTask mergeParts FAILED, state stays ${task.state}")
                }
            }
        }
    }

    private fun findParts(task: Task): List<File> =
        task.workDir.listFiles { f -> f.name.startsWith("part_") }?.sortedBy { it.name }
            ?: emptyList()

    private suspend fun runChunked(task: Task, total: Long, workers: Int) = coroutineScope {
        task.workDir.listFiles()?.forEach { it.deleteAll() }
        downloadedByTask[task.id] = AtomicLong(0)
        task.downloaded = 0
        task.ignoreRange = false

        val chunkSize = maxOf(workers * 1_000_000L, 1L)
        val chunkCount = ceil(total.toDouble() / chunkSize).toInt().coerceAtLeast(1)
        val semaphore = Semaphore(workers)
        val onBytes: suspend (Long) -> Unit = { n ->
            val counter = downloadedByTask[task.id]
            if (counter != null) {
                task.downloaded = counter.addAndGet(n)
                maybeNotifyProgress(task)
            }
        }

        val jobs = (0 until chunkCount).map { idx ->
            async {
                semaphore.withPermit {
                    if (task.state != DownloadState.PAUSED && !task.ignoreRange) {
                        val start = idx.toLong() * chunkSize
                        val end = min(total - 1, start + chunkSize - 1)
                        val part = partFile(task, idx)
                        val result = downloader.downloadChunk(task.id, task.url, start, end, part, task.headers, onBytes)
                        if (result == ChunkResult.RANGE_IGNORED) {
                            task.ignoreRange = true
                        }
                    }
                }
            }
        }
        jobs.awaitAll()

        if (task.ignoreRange && task.state == DownloadState.DOWNLOADING) {
            runFull(task)
        }
    }

    private fun partFile(task: Task, idx: Int): File =
        File(task.workDir, "part_%06d".format(idx))

    private suspend fun runFull(task: Task) = coroutineScope {
        val part = File(task.workDir, "part_000000")
        task.workDir.listFiles()?.forEach { it.deleteAll() }
        downloadedByTask[task.id] = AtomicLong(0)
        task.downloaded = 0
        val onBytes: suspend (Long) -> Unit = { n ->
            val counter = downloadedByTask[task.id]
            if (counter != null) {
                task.downloaded = counter.addAndGet(n)
                maybeNotifyProgress(task)
            }
        }
        downloader.downloadFull(task.id, task.url, part, task.headers, task.total, onBytes)
    }

    private suspend fun maybeNotifyProgress(task: Task) {
        val now = System.currentTimeMillis()
        val acc = lastStatsAt.getOrPut(task.id) { AtomicLong() }
        val st = acc.getAndSet(now)
        val elapsedSec = if (st > 0 && now > st) (now - st) / 1000.0 else 0.0
        val speed = if (elapsedSec > 0 && task.downloaded > 0L)
            (task.downloaded / elapsedSec).toLong() else 0L
        val remain = if (speed > 0 && task.total > task.downloaded)
            ((task.total - task.downloaded) * 1000 / speed) else -1L
        listener?.onProgress(task.id, task.downloaded, task.total, DownloadStats(speed, remain))
    }

    private suspend fun mergeParts(task: Task): Boolean {
        return try {
            val parts = findParts(task)
            if (parts.isEmpty()) return false
            val target = File(saveDir, task.fileName)
            val out = target.outputStream().buffered()
            runCatching { downloader.mergeChunksToStream(parts, out) }
                .onSuccess { out.flush() }
            out.close()
            task.workDir.deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun File.deleteAll() {
        if (isDirectory) deleteRecursively() else delete()
    }
}