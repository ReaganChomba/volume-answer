package de.robv.android.xposed;

public abstract class XC_MethodHook {
    public static class MethodHookParam {
        public Object[] args;
        public Object result;

        public void setResult(Object result) {
            this.result = result;
        }
    }
}
