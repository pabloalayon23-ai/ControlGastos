package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class AnalyticsTabStyleOverlay {
    private static final WeakHashMap<Activity,Boolean> INSTALLED=new WeakHashMap<>();
    private AnalyticsTabStyleOverlay(){}

    public static void install(Activity a){
        if(!(a instanceof AnalyticsActivity))return;
        synchronized(INSTALLED){if(INSTALLED.containsKey(a))return;INSTALLED.put(a,true);}
        a.getWindow().getDecorView().postDelayed(()->apply(a),650);
    }

    private static void apply(Activity a){
        if(a.isFinishing())return;
        HorizontalScrollView hs=findHorizontalScroll(a.getWindow().getDecorView());
        if(hs==null||hs.getChildCount()==0||!(hs.getChildAt(0) instanceof LinearLayout))return;
        LinearLayout tabs=(LinearLayout)hs.getChildAt(0);
        for(int i=0;i<tabs.getChildCount();i++){
            View v=tabs.getChildAt(i);
            if(!(v instanceof Button))continue;
            Button b=(Button)v;
            String s=b.getText()==null?"":b.getText().toString();
            if("Análisis".equals(s)||"Vista anual".equals(s))style(a,b);
        }
    }

    private static void style(Context c,Button b){
        boolean light=ThemePrefs.isLight(c);
        b.setTextColor(light?Color.rgb(31,38,44):Color.rgb(242,245,247));
        b.setAllCaps(false);b.setTextSize(14);
        GradientDrawable g=new GradientDrawable();g.setColor(light?Color.WHITE:Color.rgb(22,32,41));g.setCornerRadius(dp(c,12));b.setBackground(g);
    }

    private static HorizontalScrollView findHorizontalScroll(View v){if(v instanceof HorizontalScrollView)return(HorizontalScrollView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){HorizontalScrollView x=findHorizontalScroll(g.getChildAt(i));if(x!=null)return x;}}return null;}
    private static int dp(Context c,int n){return(int)(n*c.getResources().getDisplayMetrics().density+.5f);}
}
