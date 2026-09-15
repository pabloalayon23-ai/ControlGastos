package com.pablo.controlgastos;
import android.app.Activity;import android.content.*;import android.graphics.Color;import android.os.Build;import android.view.*;
public final class ThemePrefs{
 private static final String PREFS="appearance",KEY_LIGHT="light_mode";private ThemePrefs(){}
 public static boolean isLight(Context c){return c.getSharedPreferences(PREFS,0).getBoolean(KEY_LIGHT,false);}
 public static void setLight(Context c,boolean l){c.getSharedPreferences(PREFS,0).edit().putBoolean(KEY_LIGHT,l).apply();}
 public static void applyBaseTheme(Activity a){
  boolean l=isLight(a);a.setTheme(l?android.R.style.Theme_Material_Light_NoActionBar:android.R.style.Theme_Material_NoActionBar);Window w=a.getWindow();int bg=l?Color.rgb(246,248,249):Color.rgb(11,18,24);w.setStatusBarColor(bg);w.setNavigationBarColor(bg);
  if(Build.VERSION.SDK_INT>=23){int f=w.getDecorView().getSystemUiVisibility();if(l)f|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;else f&=~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(Build.VERSION.SDK_INT>=26){if(l)f|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;else f&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;}w.getDecorView().setSystemUiVisibility(f);}
  // Android 15+ fuerza edge-to-edge para apps target 35. Reservamos físicamente
  // las barras del sistema en el decor de TODAS las pantallas: el contenido y
  // los botones de ControlGastos quedan arriba de los 3 botones de navegación.
  final View decor=w.getDecorView();final int pl=decor.getPaddingLeft(),pt=decor.getPaddingTop(),pr=decor.getPaddingRight(),pb=decor.getPaddingBottom();
  decor.setOnApplyWindowInsetsListener((v,in)->{int top=in.getSystemWindowInsetTop(),bottom=in.getSystemWindowInsetBottom(),left=in.getSystemWindowInsetLeft(),right=in.getSystemWindowInsetRight();v.setPadding(pl+left,pt+top,pr+right,pb+bottom);return in.consumeSystemWindowInsets();});
  decor.requestApplyInsets();
  MonthlyInsightsOverlay.install(a);CategoryYearBarsOverlay.install(a);AnalyticsTabStyleOverlay.install(a);ReconciliationOverlay.install(a);SmartImportOverlay.install(a);NormalDuplicatePrefs.install(a);EmailSettingsOverlay.install(a);CreditCardsOverlay.install(a);CardBrowserOverlay.install(a);BiometricExternalGate.install(a);
 }
}
