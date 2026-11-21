package com.example.sensoranswer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SensorAnswer implements IXposedHookLoadPackage, SensorEventListener {
    private static final String TAG = "SensorAnswer";
    private static final float THRESHOLD = 14f;
    private static final long DEBOUNCE = 800;
    private static final long RING_WINDOW = 4000;

    private static volatile SensorAnswer INSTANCE;
    private static volatile boolean INITIALIZED;

    private Context ctx;
    private SensorManager sm;
    private Sensor accel;
    private TelephonyManager tm;
    private boolean monitoring;
    private long ringStart;
    private long lastEvent;
    private float lastX, lastY, lastZ;

    public static SensorAnswer getInstance() {
        if (INSTANCE == null) {
            synchronized (SensorAnswer.class) {
                if (INSTANCE == null) INSTANCE = new SensorAnswer();
            }
        }
        return INSTANCE;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String pkg = lpparam.packageName;
        Log.d(TAG, "handleLoadPackage: " + pkg);

        if (!isTarget(pkg)) return;
        if (INSTANCE != null && INITIALIZED) return;

        try {
            if (initialize(pkg)) {
                INSTANCE = this;
                INITIALIZED = true;
                Log.d(TAG, "Initialized successfully in " + pkg);
            } else {
                Log.e(TAG, "Init returned false in " + pkg + " (will retry on next load)");
            }
        } catch (Throwable t) {
            Log.e(TAG, "init threw in " + pkg, t);
        }
    }

    private boolean isTarget(String pkg) {
        return pkg.equals("com.android.phone")
                || pkg.equals("android")
                || pkg.equals("com.android.systemui");
    }

    private boolean initialize(String pkg) throws Throwable {
        Log.d(TAG, "Initializing for package: " + pkg);

        ctx = getAppContext();
        if (ctx == null) {
            Log.e(TAG, "Could not get Context");
            return false;
        }
        Log.d(TAG, "Got Context: " + ctx.getPackageName());

        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Log.d(TAG, "SensorManager: " + (sm != null ? "ok" : "null"));
        if (sm == null) {
            Log.e(TAG, "No SensorManager available");
            return false;
        }

        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Log.d(TAG, "Accelerometer: " + (accel != null ? "ok" : "null"));

        tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        Log.d(TAG, "TelephonyManager: " + (tm != null ? "ok" : "null"));
        if (tm == null) {
            Log.e(TAG, "No TelephonyManager available");
            return false;
        }

        tm.listen(new PhoneStateListener() {
            @Override public void onCallStateChanged(int state, String number) {
                Log.d(TAG, "Call state changed: " + state + " number=" + number);
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    ringStart = System.currentTimeMillis();
                    lastEvent = 0;
                    monitoring = true;
                    Log.d(TAG, "Ringing started, monitoring enabled");
                    if (accel != null) {
                        boolean registered = sm.registerListener(SensorAnswer.this, accel, SensorManager.SENSOR_DELAY_NORMAL);
                        Log.d(TAG, "Accelerometer registered: " + registered);
                    }
                } else {
                    Log.d(TAG, "Call state changed to " + state + ", stopping monitoring");
                    monitoring = false;
                    if (accel != null && sm != null) sm.unregisterListener(SensorAnswer.this);
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
        Log.d(TAG, "PhoneStateListener registered");
        return true;
    }

    private Context getAppContext() {
        try {
            Object app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
            if (app != null) return (Context) app;
        } catch (Throwable t) {
            Log.d(TAG, "currentApplication failed: " + t.getMessage());
        }
        try {
            Object at = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread");
            if (at != null) {
                Object sc = XposedHelpers.callMethod(at, "getSystemContext");
                if (sc != null) return (Context) sc;
            }
        } catch (Throwable t) {
            Log.d(TAG, "getSystemContext failed: " + t.getMessage());
        }
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!monitoring) return;
        long now = System.currentTimeMillis();
        if (now - ringStart > RING_WINDOW) {
            Log.d(TAG, "Ring window expired");
            monitoring = false;
            return;
        }
        float x = event.values[0], y = event.values[1], z = event.values[2];
        if (lastEvent != 0) {
            float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
            Log.d(TAG, "Sensor delta: " + delta);
            if (delta > THRESHOLD && now - lastEvent > DEBOUNCE) {
                Log.d(TAG, "Shake detected, answering call");
                answerCall();
                monitoring = false;
                if (accel != null && sm != null) sm.unregisterListener(this);
            }
        }
        lastX = x; lastY = y; lastZ = z; lastEvent = now;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void answerCall() {
        try {
            Log.d(TAG, "Attempting to answer call via ITelephony");
            Object smgr = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.os.ServiceManager", null), "getService", "phone");
            Log.d(TAG, "ServiceManager.getService(phone): " + (smgr != null ? "ok" : "null"));
            if (smgr == null) return;

            Object tel = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.android.internal.telephony.ITelephony$Stub", null), "asInterface", smgr);
            Log.d(TAG, "ITelephony.asInterface: " + (tel != null ? "ok" : "null"));
            if (tel == null) return;

            XposedHelpers.callMethod(tel, "answerRingingCall");
            Log.d(TAG, "answerRingingCall called successfully");
        } catch (Throwable t) {
            Log.e(TAG, "answer failed", t);
        }
    }
}
