package com.dexclassloader.demo;

import android.app.ProgressDialog;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class MainActivity extends AppCompatActivity {

    private TextView tv;
    private static Handler mHandler = new Handler();
    private ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pd=new ProgressDialog(this);
        pd.setTitle("正在加载SD卡上的类");
        pd.setMessage("请稍后....");

        findViewById(R.id.test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(pd!=null&&!pd.isShowing()){
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


        ClassLoader loader = MainActivity.class.getClassLoader();
        while (loader != null) {
            Log.e("LGC", loader.toString());
            loader = loader.getParent();
        }

        tv = (TextView) findViewById(R.id.main_text);

    }


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
                    Toast.makeText(MainActivity.this,"文件不存在",Toast.LENGTH_LONG);

                }
            });
            return;
        }

        if(!apkFile.canRead()){
            // 如果没有读权限,确定你在 AndroidManifest 中是否声明了读写权限
            // 如果是6.0以上手机要查看手机的权限管理，你的这个app是否具有读写权限
            Log.d("LGC", "apkFile.canRead()= " + apkFile.canRead());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this,"没有读写权限",Toast.LENGTH_LONG);

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
