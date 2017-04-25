package com.dexclassloader.demo;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class MainActivity extends AppCompatActivity {

    private TextView tv;
    private static Handler mHandler = new Handler();
    private ProgressDialog pd;

    public static final String LOCALDIR=Environment.getExternalStorageDirectory().getPath() + File.separator + "apptest-release.apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pd = new ProgressDialog(this);
        pd.setTitle("正在加载SD卡上的类");
        pd.setMessage("请稍后....");

        findViewById(R.id.test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pd != null && !pd.isShowing()) {
                    pd.show();
                }
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        load();
                    }
                }.start();
            }
        });

        findViewById(R.id.test_res).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pd != null && !pd.isShowing()) {
                    pd.show();
                }
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        loadRes();
                    }
                }.start();
            }
        });


        ClassLoader loader = MainActivity.class.getClassLoader();
        while (loader != null) {
            Log.e("LGC", loader.toString());
            loader = loader.getParent();
        }

        tv = (TextView) findViewById(R.id.main_text);

    }

    /**
     * 加载资源
     */
    private void loadRes() {
        String packagename = getUninstallApkInfo(this, LOCALDIR);
        try {
            dynamicLoadApk(LOCALDIR, packagename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取未安装apk的信息
     *
     * @param context
     * @param archiveFilePath apk文件的path
     * @return
     */
    private String getUninstallApkInfo(Context context, String archiveFilePath) {
        String pkgName=null;
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(archiveFilePath, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
//            String versionName = pkgInfo.versionName;//版本号
//            Drawable icon = pm.getApplicationIcon(appInfo);//图标
            String appName = pm.getApplicationLabel(appInfo).toString();//app名称
            pkgName = appInfo.packageName;//包名
//            info[0] = appName;
        }
        return pkgName;
    }

    /**
     * @param DexPath
     * @return 得到对应插件的Resource对象
     */
    private Resources getPluginResources(String DexPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);//反射调用方法addAssetPath(String path)
            //第二个参数是apk的路径：Environment.getExternalStorageDirectory().getPath()+File.separator+"plugin"+File.separator+"apkplugin.apk"
            addAssetPath.invoke(assetManager, DexPath);//将未安装的Apk文件的添加进AssetManager中，第二个参数为apk文件的路径带apk名
            Resources superRes = this.getResources();
            Resources mResources = new Resources(assetManager, superRes.getDisplayMetrics(),
                    superRes.getConfiguration());
            return mResources;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 加载apk获得内部资源
     *
     * @param apkDir  apk目录
     * @throws Exception
     */
    private void dynamicLoadApk(String apkDir, String apkPackageName) throws Exception {

        DexClassLoader dexClassLoader =
                new DexClassLoader(apkDir, getExternalCacheDir().getAbsolutePath(), null, getClassLoader());

        Class<?> clazz = dexClassLoader.loadClass(apkPackageName + ".R$string");//通过使用apk自己的类加载器，反射出R类中相应的内部类进而获取我们需要的资源id
        Field field = clazz.getDeclaredField("showtext");//得到名为one的这张图片字段
        final int resId = field.getInt(R.id.class);//得到图片id
        final Resources mResources = getPluginResources(apkDir);//得到插件apk中的Resource
        if (mResources != null) {
            //通过插件apk中的Resource得到resId对应的资源
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    pd.dismiss();
                    ((TextView) findViewById(R.id.main_res)).setText(mResources.getString(resId));
                }
            });

        }
    }

    /**
     * 加载类
     */
    private void load() {
        // 获取到包含 class.dex 的 jar 包文件
        final File apkFile =
                new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "apptest-release.apk");

        if (!apkFile.exists()) {

            Log.e("LGC", "文件不存在");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this, "文件不存在", Toast.LENGTH_LONG);

                }
            });
            return;
        }

        if (!apkFile.canRead()) {
            // 如果没有读权限,确定你在 AndroidManifest 中是否声明了读写权限
            // 如果是6.0以上手机要查看手机的权限管理，你的这个app是否具有读写权限
            Log.d("LGC", "apkFile.canRead()= " + apkFile.canRead());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this, "没有读写权限", Toast.LENGTH_LONG);

                }
            });
            return;
        }


        // getCodeCacheDir() 方法在 API 21 才能使用,实际测试替换成 getExternalCacheDir() 等也是可以的
        // 只要有读写权限的路径均可
        Log.i("LGC", "getExternalCacheDir().getAbsolutePath()=" + getExternalCacheDir().getAbsolutePath());
        Log.i("LGC", "apkFile.getAbsolutePath()=" + apkFile.getAbsolutePath());

        try {
            DexFile dx = DexFile.loadDex(apkFile.getAbsolutePath(), File.createTempFile("opt", "dex", getApplicationContext().getCacheDir()).getPath(), 0);

            // Print all classes in the DexFile
            for (Enumeration<String> classNames = dx.entries(); classNames.hasMoreElements(); ) {
                String className = classNames.nextElement();
                if (className.equals("com.yibao.test.IDexTestImpl")) {
                    Log.d("LGC", "#########################################################" + className);
                    Log.d("LGC", className);
                    Log.d("LGC", "#########################################################" + className);


                }
                Log.d("LGC", "Analyzing dex content, fonud class: " + className);
            }
        } catch (IOException e) {
            Log.d("LGC", "Error opening " + apkFile.getAbsolutePath(), e);
        }
        DexClassLoader dexClassLoader =
                new DexClassLoader(apkFile.getAbsolutePath(), getExternalCacheDir().getAbsolutePath(), null, getClassLoader());
        try {
            // 加载 com.test.IDexTestImpl 类
            Class clazz = dexClassLoader.loadClass("com.test.IDexTestImpl");

            Object dexTest = clazz.newInstance();

            Method getText = clazz.getMethod("getText");

            final String result = getText.invoke(dexTest).toString();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    pd.dismiss();
                    if (!TextUtils.isEmpty(result)) {
                        tv.setText(result);
                    }

                }
            });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }
}
