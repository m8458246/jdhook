package dev.cycy.jdhook;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 用京东自己的网络栈发签到请求。
 *
 * 为什么要这么做：签到页是 TTT/Taro 小程序，请求由 hybrid SDK 签名（h5st，appId=9d49c），
 * 纯 curl 拿不到签名，服务端直接回 402「活动现在挤不进去呀」。
 * 走 HttpGroup 发，签名/设备指纹由 app 自己补，等于复用它自己的链路。
 */
final class SignTask {

    private static final String FID = "signBeanAct";
    private static final String MARK = "last_sign.txt";
    private static volatile boolean sStarted;

    static void schedule(final ClassLoader cl, final String proc) {
        if (sStarted || !Const.PKG.equals(proc)) {
            return;
        }
        sStarted = true;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(15000L);
                run(cl);
            } catch (Throwable t2) {
                Io.log("sign task fail: " + t2);
            }
        }, "jdhook-sign");
        t.setDaemon(true);
        t.start();
    }

    private static boolean doneToday() {
        try {
            File f = new File(Io.dir(), MARK);
            if (!f.exists()) {
                return false;
            }
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String s = r.readLine();
            r.close();
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            return today.equals(s == null ? "" : s.trim());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void markToday() {
        try {
            File f = new File(Io.dir(), MARK);
            java.io.FileWriter w = new java.io.FileWriter(f, false);
            w.write(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            w.close();
            f.setReadable(true, false);
        } catch (Throwable ignored) {
        }
    }

    private static void run(ClassLoader cl) {
        if (doneToday()) {
            Io.log("sign: already done today, skip");
            return;
        }
        Io.log("sign: start " + FID);
        try {
            Class<?> settingCls = Class.forName("com.jingdong.jdsdk.network.toolbox.HttpSetting", false, cl);
            Class<?> groupUtils = Class.forName("com.jingdong.common.network.HttpGroupUtils", false, cl);
            Class<?> groupCls = Class.forName("com.jingdong.jdsdk.network.toolbox.HttpGroup", false, cl);
            Class<?> listenerCls = Class.forName("com.jingdong.jdsdk.network.toolbox.HttpGroup$OnAllListener", false, cl);

            Object setting = settingCls.getDeclaredConstructor().newInstance();

            Method setFid = settingCls.getMethod("setFunctionId", String.class);
            setFid.invoke(setting, FID);

            try {
                settingCls.getMethod("setPost", boolean.class).invoke(setting, true);
            } catch (Throwable ignored) {
            }

            try {
                settingCls.getMethod("setJsonParams", String.class).invoke(setting, buildBody());
            } catch (Throwable t) {
                Io.log("sign: setJsonParams fail " + t);
            }

            try {
                settingCls.getMethod("setConnectTimeout", int.class).invoke(setting, 15000);
                settingCls.getMethod("setReadTimeout", int.class).invoke(setting, 15000);
            } catch (Throwable ignored) {
            }

            Object listener = Proxy.newProxyInstance(cl, new Class<?>[]{listenerCls}, (p, m, args) -> {
                String n = m.getName();
                if ("onEnd".equals(n) && args != null && args.length > 0) {
                    onEnd(args[0]);
                } else if ("onError".equals(n) && args != null && args.length > 0) {
                    Io.write("result.json", "{\"ok\":false,\"err\":\"onError\",\"raw\":"
                            + quote(String.valueOf(args[0])) + "}");
                }
                return null;
            });

            Method setListener = null;
            for (Method mm : settingCls.getMethods()) {
                if ("setListener".equals(mm.getName()) && mm.getParameterTypes().length == 1) {
                    setListener = mm;
                    break;
                }
            }
            if (setListener == null) {
                Io.write("result.json", "{\"ok\":false,\"err\":\"no setListener\"}");
                return;
            }
            setListener.invoke(setting, listener);

            Object group = groupUtils.getMethod("getHttpGroupaAsynPool").invoke(null);

            Method add = null;
            for (Method mm : groupCls.getMethods()) {
                if ("add".equals(mm.getName()) && mm.getParameterTypes().length == 1
                        && mm.getParameterTypes()[0].isAssignableFrom(settingCls)) {
                    add = mm;
                    break;
                }
            }
            if (add == null) {
                Io.write("result.json", "{\"ok\":false,\"err\":\"no HttpGroup.add\"}");
                return;
            }
            add.invoke(group, setting);
            Io.log("sign: request submitted");
        } catch (Throwable t) {
            Io.log("sign: exception " + t);
            Io.write("result.json", "{\"ok\":false,\"err\":" + quote(String.valueOf(t)) + "}");
        }
    }

    private static void onEnd(Object response) {
        String raw = "";
        int code = -1;
        try {
            for (String mn : new String[]{"getJSONObject", "getFastJsonObject", "getJsonObject"}) {
                try {
                    Method m = response.getClass().getMethod(mn);
                    Object o = m.invoke(response);
                    if (o != null) {
                        raw = String.valueOf(o);
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (raw.isEmpty()) {
                try {
                    Object o = response.getClass().getMethod("getString").invoke(response);
                    raw = o == null ? "" : String.valueOf(o);
                } catch (Throwable ignored) {
                }
            }
            try {
                Object c = response.getClass().getMethod("getCode").invoke(response);
                code = c == null ? -1 : Integer.parseInt(String.valueOf(c));
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Io.log("sign: onEnd parse fail " + t);
        }
        if (raw.length() > 4000) {
            raw = raw.substring(0, 4000);
        }
        Io.write("result.json", "{\"ok\":" + (code == 200) + ",\"http\":" + code + ",\"raw\":" + quote(raw) + "}");
        Io.log("sign: onEnd " + raw);
        if (raw.contains("\"code\":\"0\"") || raw.contains("\"code\":0")) {
            markToday();
        }
    }

    private static String buildBody() {
        return "{\"appid\":\"signed_wh5_ihub\",\"client\":\"android\",\"clientVersion\":\"16.1.50\","
                + "\"networkType\":\"wifi\",\"osVersion\":\"14\",\"body\":{}}";
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    private SignTask() {
    }
}
