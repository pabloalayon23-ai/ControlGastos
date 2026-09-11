package com.pablo.controlgastos;

import android.content.*;import android.graphics.Color;import java.util.*;

public final class CategoryPrefs{
 private static final String PREF="category_prefs";
 public static final int[] PALETTE={Color.rgb(255,193,7),Color.rgb(76,175,80),Color.rgb(33,150,243),Color.rgb(255,87,34),Color.rgb(156,39,176),Color.rgb(0,188,212),Color.rgb(233,30,99),Color.rgb(121,85,72),Color.rgb(96,125,139),Color.rgb(139,195,74),Color.rgb(255,152,0),Color.rgb(63,81,181)};
 private CategoryPrefs(){}
 public static int color(Context c,String name){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String k=key(name);if(p.contains(k))return p.getInt(k,PALETTE[Math.abs(k.hashCode())%PALETTE.length]);return PALETTE[Math.abs(k.hashCode())%PALETTE.length];}
 public static void setColor(Context c,String name,int color){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt(key(name),color).apply();}
 public static void rename(Context c,String oldName,String newName){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String ko=key(oldName);if(p.contains(ko)){int v=p.getInt(ko,PALETTE[0]);p.edit().remove(ko).putInt(key(newName),v).apply();}}
 public static LinkedHashSet<String> definedCategories(Context c){return DetectionRules.categoryNames(c);}
 private static String key(String s){return "c_"+(s==null?"":s.trim().toLowerCase(Locale.ROOT));}
}
