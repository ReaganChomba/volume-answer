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

    private Context ctx;
    private SensorManager sm;
    private Sensor accel;
    private boolean monitoring;
    private long ringStart;
    private long lastEvent;
    private float lastX, lastY, lastZ;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"android".equals(lpparam.packageName)) return;
        try {
            Object app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
            ctx = (Context) app;
            sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(new PhoneStateListener() {
                @Override public void onCallStateChanged(int state, String number) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        ringStart = System.currentTimeMillis();
                        lastEvent = 0;
                        monitoring = true;
                        if (accel != null) sm.registerListener(SensorAnswer.this, accel, SensorManager.SENSOR_DELAY_NORMAL);
                    } else {
                        monitoring = false;
                        if (accel != null) sm.unregisterListener(SensorAnswer.this);
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Throwable t) { Log.e(TAG, "init failed", t); }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!monitoring) return;
        long now = System.currentTimeMillis();
        if (now - ringStart > RING_WINDOW) { monitoring = false; return; }
        float x = event.values[0], y = event.values[1], z = event.values[2];
        if (lastEvent != 0) {
            float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
            if (delta > THRESHOLD && now - lastEvent > DEBOUNCE) {
                answerCall();
                monitoring = false;
                if (accel != null) sm.unregisterListener(this);
            }
        }
        lastX = x; lastY = y; lastZ = z; lastEvent = now;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void answerCall() {
        try {
            Object smgr = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.os.ServiceManager", null), "getService", "phone");
            Object tel = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.android.internal.telephony.ITelephony$Stub", null), "asInterface", smgr);
            XposedHelpers.callMethod(tel, "answerRingingCall");
        } catch (Throwable t) { Log.e(TAG, "answer failed", t); }
    }
}
