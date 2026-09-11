package com.pablo.controlgastos;

import android.content.*;import java.text.Normalizer;import java.util.*;

public final class BlacklistRules{
 private static final String PREF="blacklist_rules",COUNT="rule_count",INIT2="initialized_dynamic_v2";
 private static final String[] DEFAULTS={"rechazado","rechazada","anulado","anulada","pendiente","recordatorio","vencimiento","devolucion","acreditacion","saldo disponible"};
 private BlacklistRules(){}
 private static void ensure(Context c){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);if(p.getBoolean(INIT2,false))return;ArrayList<String>w=new ArrayList<>();if(p.getBoolean("initialized_v1",false)){for(int i=0;i<50;i++){String x=p.getString("word_"+i,"").trim();if(!x.isEmpty())w.add(x);}}else Collections.addAll(w,DEFAULTS);saveInternal(c,w);}
 private static void saveInternal(Context c,List<String>words){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);int old=p.getInt(COUNT,50);SharedPreferences.Editor e=p.edit();int n=0;for(String s:words){String w=s==null?"":s.trim();if(w.isEmpty())continue;e.putString("word_"+n,w);n++;}for(int i=n;i<Math.max(old,50);i++)e.remove("word_"+i);e.putInt(COUNT,n).putBoolean(INIT2,true).putBoolean("initialized_v1",true).apply();}
 public static ArrayList<String>load(Context c){ensure(c);SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);ArrayList<String>out=new ArrayList<>();int n=p.getInt(COUNT,0);for(int i=0;i<n;i++){String w=p.getString("word_"+i,"").trim();if(!w.isEmpty())out.add(w);}return out;}
 public static void save(Context c,List<String>words){ensure(c);saveInternal(c,words);}
 public static boolean matches(Context c,String text){String n=norm(text);for(String w:load(c))if(n.contains(norm(w)))return true;return false;}
 private static String norm(String s){if(s==null)return"";return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
}
