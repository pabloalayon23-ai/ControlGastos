package com.pablo.controlgastos;

import android.content.*;import android.graphics.Color;import java.util.*;

public final class CategoryPrefs{
 private static final String PREF="category_prefs",REG="category_registry",REG_COUNT="count",REG_INIT="initialized";
 public static final int[] PALETTE={Color.rgb(255,193,7),Color.rgb(76,175,80),Color.rgb(33,150,243),Color.rgb(255,87,34),Color.rgb(156,39,176),Color.rgb(0,188,212),Color.rgb(233,30,99),Color.rgb(121,85,72),Color.rgb(96,125,139),Color.rgb(139,195,74),Color.rgb(255,152,0),Color.rgb(63,81,181)};
 private CategoryPrefs(){}
 public static int color(Context c,String name){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String k=key(name);if(p.contains(k))return p.getInt(k,PALETTE[Math.abs(k.hashCode())%PALETTE.length]);return PALETTE[Math.abs(k.hashCode())%PALETTE.length];}
 public static void setColor(Context c,String name,int color){register(c,name);c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt(key(name),color).apply();}
 public static void rename(Context c,String oldName,String newName){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String ko=key(oldName);if(p.contains(ko)){int v=p.getInt(ko,PALETTE[0]);p.edit().remove(ko).putInt(key(newName),v).apply();}LinkedHashSet<String> cats=registry(c);cats.removeIf(x->x.equalsIgnoreCase(oldName));cats.add(newName);replaceRegistry(c,cats);}
 public static void register(Context c,String name){if(name==null||name.trim().isEmpty())return;LinkedHashSet<String> cats=registry(c);cats.add(name.trim());replaceRegistry(c,cats);}
 public static LinkedHashSet<String> registry(Context c){SharedPreferences p=c.getSharedPreferences(REG,Context.MODE_PRIVATE);LinkedHashSet<String> out=new LinkedHashSet<>();int n=p.getInt(REG_COUNT,0);for(int i=0;i<n;i++){String s=p.getString("cat_"+i,"").trim();if(!s.isEmpty())out.add(s);}return out;}
 public static void replaceRegistry(Context c,Collection<String> names){SharedPreferences p=c.getSharedPreferences(REG,Context.MODE_PRIVATE);SharedPreferences.Editor e=p.edit().clear();int i=0;LinkedHashSet<String> uniq=new LinkedHashSet<>();if(names!=null)for(String s:names)if(s!=null&&!s.trim().isEmpty()&&!s.trim().equalsIgnoreCase("Otros"))uniq.add(s.trim());for(String s:uniq)e.putString("cat_"+(i++),s);e.putInt(REG_COUNT,i).putBoolean(REG_INIT,true).commit();}
 public static LinkedHashSet<String> definedCategories(Context c){LinkedHashSet<String> out=registry(c);for(String s:DetectionRules.categoryNames(c))if(!s.equalsIgnoreCase("Otros"))out.add(s);out.add("Otros");return out;}
 private static String key(String s){return "c_"+(s==null?"":s.trim().toLowerCase(Locale.ROOT));}
}
