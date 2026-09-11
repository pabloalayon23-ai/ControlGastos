package com.pablo.controlgastos;

import android.content.*;import android.graphics.Color;import java.util.*;

public final class CategoryPrefs{
 private static final String PREF="category_prefs",NAMES="defined_names";
 public static final String[] DEFAULTS={"Supermercado","Comida","Combustible","Transporte","Servicios","Salud","Tecnología","Regalos","Educación","Vivienda","Pagos","Transferencias","Otros"};
 public static final int[] PALETTE={Color.rgb(255,193,7),Color.rgb(76,175,80),Color.rgb(33,150,243),Color.rgb(255,87,34),Color.rgb(156,39,176),Color.rgb(0,188,212),Color.rgb(233,30,99),Color.rgb(121,85,72),Color.rgb(96,125,139),Color.rgb(139,195,74),Color.rgb(255,152,0),Color.rgb(63,81,181)};
 private CategoryPrefs(){}
 public static int color(Context c,String name){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String k=key(name);if(p.contains(k))return p.getInt(k,PALETTE[Math.abs(k.hashCode())%PALETTE.length]);return PALETTE[Math.abs(k.hashCode())%PALETTE.length];}
 public static void setColor(Context c,String name,int color){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);LinkedHashSet<String> names=new LinkedHashSet<>(p.getStringSet(NAMES,Collections.emptySet()));if(name!=null&&!name.trim().isEmpty())names.add(name.trim());p.edit().putInt(key(name),color).putStringSet(NAMES,names).apply();}
 public static void rename(Context c,String oldName,String newName){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String ko=key(oldName);SharedPreferences.Editor e=p.edit();if(p.contains(ko)){int v=p.getInt(ko,PALETTE[0]);e.remove(ko).putInt(key(newName),v);}LinkedHashSet<String> names=new LinkedHashSet<>(p.getStringSet(NAMES,Collections.emptySet()));if(oldName!=null)names.remove(oldName);if(newName!=null&&!newName.trim().isEmpty())names.add(newName.trim());e.putStringSet(NAMES,names).apply();}
 public static LinkedHashSet<String> definedCategories(Context c){LinkedHashSet<String> out=new LinkedHashSet<>(Arrays.asList(DEFAULTS));SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);out.addAll(p.getStringSet(NAMES,Collections.emptySet()));for(String k:p.getAll().keySet())if(k.startsWith("c_")&&k.length()>2){String n=k.substring(2);if(!n.isEmpty())out.add(title(n));}return out;}
 private static String title(String s){if(s==null||s.isEmpty())return s;return s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);}
 private static String key(String s){return "c_"+(s==null?"":s.trim().toLowerCase(Locale.ROOT));}
}
