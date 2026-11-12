package dev.cycy.jdhook;

import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class Hooks {

    private static volatile boolean sInstalled;

    static void install(XposedModule module, ClassLoader cl, String proc) {
        if (sInstalled) {
            return;
        }
        sInstalled = true;
        Io.log("install start proc=" + proc);
        try {
            hookLoadUrl(module, cl);
            Io.log("hook loadUrl ok");
        } catch (Throwable t) {
            Io.log("hook loadUrl fail: " + t);
        }
        try {
            hookPageFinished(module, cl);
            Io.log("hook onPageFinished ok");
        } catch (Throwable t) {
            Io.log("hook onPageFinished fail: " + t);
        }
        Io.log("install done proc=" + proc);
    }

    /** 所有 WebView.loadUrl 都会经过这里：记录 URL，命中签到页就排注入 */
    private static void hookLoadUrl(XposedModule module, ClassLoader cl) throws Exception {
        Class<?> wv = Class.forName("android.webkit.WebView", false, cl);
        for (Method m : wv.getDeclaredMethods()) {
            if (!"loadUrl".equals(m.getName())) {
                continue;
            }
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length < 1 || pt[0] != String.class) {
                continue;
            }
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object ret = chain.proceed();
                        try {
                            Object self = chain.getThisObject();
                            Object a0 = chain.getArg(0);
                            if (self instanceof WebView && a0 instanceof String) {
                                onPage((WebView) self, (String) a0);
                            }
                        } catch (Throwable ignored) {
                        }
                        return ret;
                    });
        }
    }

    /** 备用入口：有些容器不走 loadUrl，但页面完成时会回调 client */
    private static void hookPageFinished(XposedModule module, ClassLoader cl) throws Exception {
        Class<?> wvc = Class.forName("android.webkit.WebViewClient", false, cl);
        Class<?> wv = Class.forName("android.webkit.WebView", false, cl);
        Method m = wvc.getDeclaredMethod("onPageFinished", wv, String.class);
        module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object ret = chain.proceed();
                    try {
                        Object v = chain.getArg(0);
                        Object u = chain.getArg(1);
                        if (v instanceof WebView && u instanceof String) {
                            onPage((WebView) v, (String) u);
                        }
                    } catch (Throwable ignored) {
                    }
                    return ret;
                });
    }

    private static void onPage(WebView view, String url) {
        if (url == null) {
            return;
        }
        Io.log("page " + url);
        boolean sign = url.contains("pro.m.jd.com/mall/active")
                || url.contains("bean.m.jd.com")
                || url.contains("jdhook=");
        if (!sign) {
            return;
        }
        boolean click = url.contains("jdhook=click");
        try {
            view.postDelayed(() -> eval(view, Inject.ARM_JS, null), 2500L);
            view.postDelayed(() -> eval(view, Inject.DUMP_JS, "report.json"), 6000L);
            if (click) {
                view.postDelayed(() -> eval(view, Inject.CLICK_JS, "click.json"), 9000L);
                view.postDelayed(() -> eval(view, Inject.DUMP_JS, "report2.json"), 13000L);
            }
        } catch (Throwable t) {
            Io.log("schedule fail: " + t);
        }
    }

    private static void eval(WebView view, String js, String out) {
        try {
            ValueCallback<String> cb = out == null
                    ? null
                    : v -> Io.write(out, v == null ? "null" : v);
            view.evaluateJavascript(js, cb);
        } catch (Throwable t) {
            Io.log("eval fail: " + t);
        }
    }

    private Hooks() {
    }
}
