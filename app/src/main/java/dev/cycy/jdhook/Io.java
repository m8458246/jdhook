package dev.cycy.jdhook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 只写不读：模块运行在京东进程里，写自己的 files 目录；root 脚本负责读。
 * 反向（脚本写、模块读）会踩 SELinux 文件上下文，所以不做。
 */
final class Io {

    private static final Object LOCK = new Object();

    static File dir() {
        File d = new File(Const.DIR);
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    static void write(String name, String content) {
        synchronized (LOCK) {
            FileOutputStream fos = null;
            try {
                File f = new File(dir(), name);
                fos = new FileOutputStream(f, true);
                OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
                w.write(content);
                w.write("\n");
                w.flush();
                //noinspection ResultOfMethodCallIgnored
                f.setReadable(true, false);
            } catch (Throwable ignored) {
                // 模块里任何异常都不能外泄，否则会被目标 App 的崩溃收集看到
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    static void log(String s) {
        write("hook.log", new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + " " + s);
    }
}
