package com.pablo.controlgastos;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

public final class ThemePrefs {
    private static final String PREFS="appearance";
    private static final String KEY_LIGHT="light_mode";
    private ThemePrefs(){}

    public static boolean isLight(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(KEY_LIGHT,false);
    }

    public static void setLight(Context c,boolean light){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY_LIGHT,light).apply();
    }

    public static void applyBaseTheme(Activity a){
        boolean light=isLight(a);
        a.setTheme(light ? android.R.style.Theme_Material_Light_NoActionBar : android.R.style.Theme_Material_NoActionBar);
        Window w=a.getWindow();
        int bg=light?Color.rgb(246,248,249):Color.rgb(11,18,24);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if(Build.VERSION.SDK_INT>=23){
            int flags=w.getDecorView().getSystemUiVisibility();
            if(light) flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else flags&=~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if(Build.VERSION.SDK_INT>=26){
                if(light) flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; else flags&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
        }
        MonthlyInsightsOverlay.install(a);
        CategoryYearBarsOverlay.install(a);
        AnalyticsTabStyleOverlay.install(a);
        ReconciliationOverlay.install(a);
        BiometricExternalGate.install(a);
    }
}
