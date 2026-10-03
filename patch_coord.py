
f="DownloadCoordinator.kt"
s=open(f).read()

old_p="""                override fun onProgress(taskId: Long, downloaded: Long, total: Long, stats: DownloadStats) {
                    val cardId = cardToTask.entries.firstOrNull { it.value == taskId }?.key
                    if (cardId != null) {
                        bridge.updateDownloadProgress(cardId, downloaded, total)
                    }
                }

                override fun onStateChanged(taskId: Long, state: DownloadState) {
                    val cardId = cardToTask.entries.firstOrNull { it.value == taskId }?.key ?: return
                    when (state) {
                        DownloadState.COMPLETED -> bridge.finishDownload(cardId, true, "下载完成")
                        DownloadState.FAILED -> bridge.finishDownload(cardId, false, "下载失败")
                        DownloadState.PAUSED -> bridge.finishDownload(cardId, false, "已停止")
                        else -> {}
                    }
                }"""
new_p="""                override fun onProgress(taskId: Long, downloaded: Long, total: Long, stats: DownloadStats) {
                    val cardId = cardToTask.entries.firstOrNull { it.value == taskId }?.key
                    if (cardId != null) {
                        bridge.updateDownloadProgress(cardId, downloaded, total)
                    }
                    itemById[taskId]?.let { cur ->
                        updateItemAt(taskId, cur.copy(downloaded = downloaded, total = if (total > 0) total else cur.total, speed = stats.speed))
                    }
                }

                override fun onStateChanged(taskId: Long, state: DownloadState) {
                    val cardId = cardToTask.entries.firstOrNull { it.value == taskId }?.key ?: return
                    when (state) {
                        DownloadState.COMPLETED -> bridge.finishDownload(cardId, true, "下载完成")
                        DownloadState.FAILED -> bridge.finishDownload(cardId, false, "下载失败")
                        DownloadState.PAUSED -> bridge.finishDownload(cardId, false, "已停止")
                        else -> {}
                    }
                    itemById[taskId]?.let { cur ->
                        updateItemAt(taskId, cur.copy(state = state, speed = 0L))
                    }
                }"""
s=s.replace(old_p,new_p)

old_sd="""    private fun startDownload(cardId: String, url: String, fileName: String, headers: Map<String, String>) {
        val taskId = manager.enqueue(url, fileName, headers)
        cardToTask[cardId] = taskId
        bridge.updateDownloadProgress(cardId, 0, -1)
    }"""
new_sd="""    private fun startDownload(cardId: String, url: String, fileName: String, headers: Map<String, String>) {
        val taskId = manager.enqueue(url, fileName, headers)
        cardToTask[cardId] = taskId
        bridge.updateDownloadProgress(cardId, 0, -1)
        upsertItem(DownloadItem(taskId, fileName, url, DownloadState.DOWNLOADING, 0, 0, 0))
    }

    private fun upsertItem(item: DownloadItem) {
        itemById[item.id] = item
        _items.value = itemById.values.sortedByDescending { it.id }
    }

    private fun updateItemAt(id: Long, item: DownloadItem) {
        itemById[id] = item
        _items.value = itemById.values.sortedByDescending { it.id }
    }

    fun pauseItem(id: Long) {
        itemById[id]?.let { if (it.state == DownloadState.DOWNLOADING) manager.pause(id) }
    }

    fun resumeItem(id: Long) {
        itemById[id]?.let {
            if (it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED) manager.start(id)
        }
    }

    fun removeItem(id: Long) {
        if (itemById.remove(id) != null) manager.cancel(id)
        _items.value = itemById.values.sortedByDescending { it.id }
        cardToTask.entries.removeAll { it.value == id }
    }

    fun openItem(id: Long) {
        val item = itemById[id] ?: return
        if (item.state != DownloadState.COMPLETED) return
        val file = File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads/${item.fileName}")
        if (!file.exists()) return
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(intent)
        }
    }"""
s=s.replace(old_sd,new_sd)

open(f,"w").write(s)
print("done")
PY