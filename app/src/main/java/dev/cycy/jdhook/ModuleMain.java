package dev.cycy.jdhook;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ModuleMain extends XposedModule {

    private static String sProc = "unknown";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        try {
            sProc = param.getProcessName();
        } catch (Throwable ignored) {
        }
        log(Log.INFO, Const.TAG, "loaded in " + sProc);
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!Const.PKG.equals(param.getPackageName())) {
            return;
        }
        try {
            Hooks.install(this, param.getClassLoader(), sProc);
        } catch (Throwable t) {
            log(Log.ERROR, Const.TAG, "install failed: " + Log.getStackTraceString(t));
        }
    }
}
