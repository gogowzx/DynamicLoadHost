package com.ryg.dynamicloadhost;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;

import dalvik.system.DexClassLoader;
import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;


public class ProxyService extends Service {

	private static final String TAG = "ProxyService";
	
	public static final String FROM = "extra.from";
    public static final int FROM_INTERNAL = 0;
    public static final int FROM_EXTERNAL = 1;
    
	public static final String EXTRA_DEX_PATH = "extra.dex.path";
    public static final String EXTRA_CLASS = "extra.class";

    private String mClass;
    private String mDexPath;
    private boolean bIsCreate = false;
    
    private ClassLoader mClassLoader;
    private Service mRemoteService;
    private AssetManager mAssetManager;
    private Resources mResources;
    private Theme mTheme;
    
    private HashMap<String, Method> mServiceLifecircleMethods = new HashMap<String, Method>();
    
    protected void loadResources() {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, mDexPath);
            mAssetManager = assetManager;
        } catch (Exception e) {
            e.printStackTrace();
        }
        Resources superRes = super.getResources();
        mResources = new Resources(mAssetManager, superRes.getDisplayMetrics(),
                superRes.getConfiguration());
        mTheme = mResources.newTheme();
        mTheme.setTo(super.getTheme());
    }
    
    @Override
    public AssetManager getAssets() {
        return mAssetManager == null ? super.getAssets() : mAssetManager;
    }

    @Override
    public Resources getResources() {
        return mResources == null ? super.getResources() : mResources;
    }

    @Override
    public Theme getTheme() {
        return mTheme == null ? super.getTheme() : mTheme;
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected void launchTargetService(final String className) {
        Log.d(TAG, "start launchTargetService, className=" + className);
        File dexOutputDir = this.getDir("dex", Context.MODE_PRIVATE);
        final String dexOutputPath = dexOutputDir.getAbsolutePath();
        ClassLoader localClassLoader = ClassLoader.getSystemClassLoader();
        DexClassLoader dexClassLoader = new DexClassLoader(mDexPath,
                dexOutputPath, null, localClassLoader);
        mClassLoader = dexClassLoader;
        try {
            Class<?> localClass = dexClassLoader.loadClass(className);
            Constructor<?> localConstructor = localClass.getConstructor(new Class[] {});
            Object instance = localConstructor.newInstance(new Object[] {});
            setRemoteService(instance);
            Log.d(TAG, "instance = " + instance);
            instantiateLifecircleMethods(localClass);

            Method setProxy = localClass.getMethod("setProxy", new Class[] { Service.class, String.class });
            setProxy.setAccessible(true);
            setProxy.invoke(instance, new Object[] { this, mDexPath });

            Method onCreate = mServiceLifecircleMethods.get("onCreate");            
            onCreate.invoke(instance, new Object[] {  });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected void setRemoteService(Object service) {
        try {
            mRemoteService = (Service) service;
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public ClassLoader getClassLoader() {
        ClassLoader classLoader = new ClassLoader(super.getClassLoader()) {
            @Override
            public Class<?> loadClass(String className) throws ClassNotFoundException {
                Class<?> clazz = null;
                clazz = mClassLoader.loadClass(className);
                Log.d(TAG, "load class:" + className);
                if (clazz == null) {
                    clazz = getParent().loadClass(className);
                }
                // still not found
                if (clazz == null) {
                    throw new ClassNotFoundException(className);
                }

                return clazz;
            }
        };

        return classLoader;
    }

    protected void instantiateLifecircleMethods(Class<?> localClass) {
        String[] methodNames = new String[] {                
                "onBind",
                "onUnbind",
                "onRebind",                
        };
        for (String methodName : methodNames) {
            Method method = null;
            try {
                method = localClass.getDeclaredMethod(methodName, new Class[] { Intent.class });
                method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            mServiceLifecircleMethods.put(methodName, method);
        }
        
        methodNames = new String[] {
        		"onCreate",
        		"onDestory",        		
        };
        for (String methodName : methodNames) {
            Method method = null;
            try {
                method = localClass.getDeclaredMethod(methodName, new Class[] {  });
                method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            mServiceLifecircleMethods.put(methodName, method);
        }

        Method onStart = null;
        try {
            onStart = localClass.getDeclaredMethod("onStart", new Class[] { Intent.class, int.class });
            onStart.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        mServiceLifecircleMethods.put("onStart", onStart);
        
        Method onStartCommand = null;
        try {
        	onStartCommand = localClass.getDeclaredMethod("onStartCommand", new Class[] { Intent.class, int.class, int.class });
        	onStartCommand.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        mServiceLifecircleMethods.put("onStartCommand", onStartCommand);
       
    }
    
	@Override
	public void onCreate() {		
		super.onCreate();
		bIsCreate = true;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void onStart(Intent intent, int startId) {	
		Method onStart = mServiceLifecircleMethods.get("onStart");
        if (onStart != null) {
            try {
                onStart.invoke(mRemoteService, new Object[] { intent, startId });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
		super.onStart(intent, startId);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		mDexPath = intent.getStringExtra(EXTRA_DEX_PATH);
        mClass = intent.getStringExtra(EXTRA_CLASS);
        if(bIsCreate) {
        	bIsCreate = false;
        	loadResources();
        	if(mClass != null) {
        		launchTargetService(mClass);
        	}
        }
        
        Method onStartCommand = mServiceLifecircleMethods.get("onStartCommand");
        if (onStartCommand != null) {
            try {
            	onStartCommand.invoke(mRemoteService, new Object[] { intent, flags, startId });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onDestroy() {
		Method onDestroy = mServiceLifecircleMethods.get("onDestroy");
        if (onDestroy != null) {
            try {
            	onDestroy.invoke(mRemoteService, new Object[] {  });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
		super.onDestroy();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		
		mDexPath = intent.getStringExtra(EXTRA_DEX_PATH);
        mClass = intent.getStringExtra(EXTRA_CLASS);
        if(bIsCreate) {
        	bIsCreate = false;
        	loadResources();
        	if(mClass != null) {
        		launchTargetService(mClass);
        	}
        }
        
		Method onBind = mServiceLifecircleMethods.get("onBind");
        if (onBind != null) {
            try {
            	return (IBinder)onBind.invoke(mRemoteService, new Object[] { intent });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
		
		return null;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Method onUnbind = mServiceLifecircleMethods.get("onUnbind");
        if (onUnbind != null) {
            try {
            	onUnbind.invoke(mRemoteService, new Object[] { intent });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
		return super.onUnbind(intent);
	}
	
	@Override
	public void onRebind(Intent intent) {
		Method onRebind = mServiceLifecircleMethods.get("onRebind");
        if (onRebind != null) {
            try {
            	onRebind.invoke(mRemoteService, new Object[] { intent });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
		
		super.onRebind(intent);
	}
		
}
