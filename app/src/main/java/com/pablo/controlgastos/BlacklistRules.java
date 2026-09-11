package com.pablo.controlgastos;

import android.content.Context;import android.content.SharedPreferences;import java.text.Normalizer;import java.util.*;

public final class BlacklistRules{
 public static final int MAX_RULES=50;private static final String PREF="blacklist_rules",INIT="initialized_v1";
 private static final String[] DEFAULTS={"rechazado","rechazada","anulado","anulada","pendiente","recordatorio","vencimiento","devolucion","acreditacion","saldo disponible"};
 private BlacklistRules(){}
 private static void ensureDefaults(Context c){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);if(p.getBoolean(INIT,false))return;SharedPreferences.Editor e=p.edit();for(int i=0;i<MAX_RULES;i++)e.putString("word_"+i,i<DEFAULTS.length?DEFAULTS[i]:"");e.putBoolean(INIT,true).apply();}
 public static ArrayList<String> load(Context c){ensureDefaults(c);SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);ArrayList<String> out=new ArrayList<>();for(int i=0;i<MAX_RULES;i++){String w=p.getString("word_"+i,"").trim();if(!w.isEmpty())out.add(w);}return out;}
 public static void save(Context c,List<String> words){SharedPreferences.Editor e=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();for(int i=0;i<MAX_RULES;i++)e.putString("word_"+i,i<words.size()?words.get(i).trim():"");e.putBoolean(INIT,true).apply();}
 public static boolean matches(Context c,String text){String n=norm(text);for(String w:load(c))if(n.contains(norm(w)))return true;return false;}
 private static String norm(String s){if(s==null)return"";return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
}
