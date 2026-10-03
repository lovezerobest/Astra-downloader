import re
f="/workspace/mounts/codex/AstraDownloader/app/src/main/java/com/astradownloader/DownloadManager.kt"
s=open(f).read()

# 1) 加 dlog 方法（在 saveDir/scope 声明后插入一个位置，比如在 class 开头成员后）
anchor="""    private val tasks = ConcurrentHashMap<Long, Task>()"""
helper="""    private val tasks = ConcurrentHashMap<Long, Task>()

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
"""
assert anchor in s, "anchor"
s=s.replace(anchor, helper)

# 2) 把 Log.d("DL", "xxx") 替换为 dlog("xxx")
s=re.sub(r'android\.util\.Log\.d\("DL", ([^;]+)\)', r'dlog(\1)', s)

open(f,"w").write(s)
print("done")
print(s.count('dlog('))
PY