package com.sina.weibo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.BaseDexClassLoader;

public class DebugFastHotFix {

    public static void setupClassLoader(Context base) {
        if (null == base || !isDebug(base)) {
            return;
        }
        File codeCacheDir = base.getCacheDir();
        final String packageName = base.getPackageName();
        List<String> dexList = getDexList(packageName);
        if (null == dexList || dexList.size() < 1) {
            return;
        }
        String nativeLibDir = "";
        //String dataDir = base.getApplicationInfo().dataDir;
        //try {
        //    // We cannot use the .so files pushed by adb for some reason: even if permissions are 777
        //    // and they are chowned to the user of the app from a root shell, dlopen() returns with
        //    // "Permission denied". For some reason, copying them over makes them work (at the cost of
        //    // some execution time and complexity here, of course)
        //    nativeLibDir = copyNativeLibs(dataDir);
        //} catch (IOException e) {
        //    throw new IllegalStateException(e);
        //}

        IncrementalClassLoader.inject(
                WeiboApplication.class.getClassLoader(),
                packageName,
                codeCacheDir,
                nativeLibDir,
                dexList);
    }

    private static List<String> getDexList(String packageName) {
        List<String> result = new ArrayList<>();
        String dexDirectory = "/sdcard/Android/data" + "/" + packageName + "/dex";
        File[] dexes = new File(dexDirectory).listFiles();
        if (dexes == null) {
            return null;
        }

        for (File dex : dexes) {
            if (dex.getName().endsWith(".dex")) {
                result.add(dex.getPath());
            }
            if (dex.getName().endsWith(".jar")) {
                result.add(dex.getPath());
            }
        }

        return result;
    }

    /**
     * A class loader that loads classes from any .dex file in a particular directory on the SD card.
     *
     * <p>Used to implement incremental deployment to Android phones.
     */
    public static class IncrementalClassLoader extends ClassLoader {
        private final DelegateClassLoader delegateClassLoader;

        public IncrementalClassLoader(ClassLoader original,
                String packageName, File codeCacheDir, String nativeLibDir, List<String> dexes) {
            super(original.getParent());

            // TODO: For some mysterious reason, we need to use two class loaders so that
            // everything works correctly. Investigate why that is the case so that the code can be
            // simplified.
            delegateClassLoader = createDelegateClassLoader(codeCacheDir, nativeLibDir, dexes, original);
        }

        @Override
        public Class<?> findClass(String className) throws ClassNotFoundException {
            return delegateClassLoader.findClass(className);
        }

        /**
         * A class loader whose only purpose is to make {@code findClass()} public.
         */
        private static class DelegateClassLoader extends BaseDexClassLoader {
            private DelegateClassLoader(
                    String dexPath, File optimizedDirectory, String libraryPath, ClassLoader parent) {
                super(dexPath, optimizedDirectory, libraryPath, parent);
            }

            @Override
            public Class<?> findClass(String name) throws ClassNotFoundException {
                return super.findClass(name);
            }
        }

        private static DelegateClassLoader createDelegateClassLoader(
                File codeCacheDir, String nativeLibDir, List<String> dexes, ClassLoader original) {
            StringBuilder pathBuilder = new StringBuilder();
            boolean first = true;
            for (String dex : dexes) {
                if (first) {
                    first = false;
                } else {
                    pathBuilder.append(File.pathSeparator);
                }

                pathBuilder.append(dex);
            }

            Log.v("IncrementalClassLoader", "Incremental dex path is " + pathBuilder);
            Log.v("IncrementalClassLoader", "Native lib dir is " + nativeLibDir);
            return new DelegateClassLoader(pathBuilder.toString(), codeCacheDir,
                    nativeLibDir, original);
        }

        private static void setParent(ClassLoader classLoader, ClassLoader newParent) {
            try {
                Field parent = ClassLoader.class.getDeclaredField("parent");
                parent.setAccessible(true);
                parent.set(classLoader, newParent);
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public static void inject(
                ClassLoader classLoader, String packageName, File codeCacheDir,
                String nativeLibDir, List<String> dexes) {
            IncrementalClassLoader incrementalClassLoader =
                    new IncrementalClassLoader(classLoader, packageName, codeCacheDir, nativeLibDir, dexes);
            setParent(classLoader, incrementalClassLoader);
        }
    }

    private static boolean isDebug(Context context) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);

            return 0 != (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE);

        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

    }
}
