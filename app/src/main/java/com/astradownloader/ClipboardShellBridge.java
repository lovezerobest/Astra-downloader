package com.astradownloader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/** Runs as shell UID, the only non-system identity allowed to read the clipboard in the background here. */
public final class ClipboardShellBridge {
    private static final String PACKAGE = "com.astradownloader";
    private static final String RECEIVER = PACKAGE + ".ClipboardReceiver";

    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 2000) throw new SecurityException("Shell UID required");
        Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        Context shell = new ContextWrapper(context) {
            @Override public String getOpPackageName() { return "com.android.shell"; }
        };
        ClipboardManager clipboard = ClipboardManager.class
                .getConstructor(Context.class, Handler.class)
                .newInstance(shell, new Handler(Looper.myLooper()));
        Handler handler = new Handler(Looper.myLooper());

        if (args.length > 0 && "--dump".equals(args[0])) {
            ClipData clip = clipboard.getPrimaryClip();
            System.out.println("DUMP items=" + (clip == null ? -1 : clip.getItemCount())
                    + " text=" + (clip == null || clip.getItemCount() == 0 ? "-"
                    : clip.getItemAt(0).coerceToText(shell).toString()));
            return;
        }
        if (args.length > 1 && "--set".equals(args[0])) {
            clipboard.setPrimaryClip(ClipData.newPlainText("test", args[1]));
            Thread.sleep(1500);
            return;
        }
        if (args.length > 0 && "--self-test".equals(args[0])) {
            ClipData original = clipboard.getPrimaryClip();
            clipboard.setPrimaryClip(ClipData.newPlainText("Astra test",
                    "https://example.com/download/astra-clipboard-event-check.bin"));
            Thread.sleep(2500);
            if (original != null) clipboard.setPrimaryClip(original);
            return;
        }

        RandomAccessFile lockFile = new RandomAccessFile("/data/local/tmp/astradownloader-clip.lock", "rw");
        FileLock lock = lockFile.getChannel().tryLock();
        if (lock == null) return;
        lockFile.setLength(0);
        lockFile.writeBytes(Integer.toString(Process.myPid()));

        clipboard.addPrimaryClipChangedListener(() -> handler.postDelayed(() -> deliver(shell, clipboard), 120));
        android.util.Log.i("AstraShellBridge", "clipboard listener ready");
        deliver(shell, clipboard);
        Looper.loop();
    }

    private static void deliver(Context shell, ClipboardManager clipboard) {
        try {
            ClipData clip = clipboard.getPrimaryClip();
            android.util.Log.i("AstraShellBridge", "clip event items=" + (clip == null ? -1 : clip.getItemCount()));
            if (clip == null || clip.getItemCount() <= 0) return;
            CharSequence raw = clip.getItemAt(0).coerceToText(shell);
            if (raw == null) return;
            String text = raw.toString().trim();
            if (text.isEmpty() || text.length() > 4096) return;
            Intent intent = new Intent(PACKAGE + ".CLIP_DETECTED");
            intent.setClassName(PACKAGE, RECEIVER);
            intent.putExtra("text", text);
            intent.putExtra("timestamp", clip.getDescription().getTimestamp());
            shell.sendBroadcast(intent);
        } catch (Exception error) {
            android.util.Log.w("AstraShellBridge", "clipboard delivery failed", error);
        }
    }
}
