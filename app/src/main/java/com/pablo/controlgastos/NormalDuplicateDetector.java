package com.pablo.controlgastos;

import android.content.Context;import android.database.Cursor;import java.text.Normalizer;import java.util.*;

public final class NormalDuplicateDetector{
 private NormalDuplicateDetector(){}
 public static boolean hasImportedDuplicate(Context c,ExpenseDb db,String type,double amount,String currency,String description,long ts){return find(c,db,type,amount,currency,description,ts,true);}
 public static boolean hasNotificationDuplicate(Context c,ExpenseDb db,String type,double amount,String currency,String description,long ts){return find(c,db,type,amount,currency,description,ts,false);}
 private static boolean find(Context c,ExpenseDb db,String type,double amount,String currency,String description,long ts,boolean imported){
  Cursor q=db.getReadableDatabase().rawQuery("SELECT amount,currency,description,COALESCE(original_ts,ts) FROM tx WHERE type=? AND "+(imported?"(source LIKE 'banco-xls%' OR source LIKE 'conciliado:%')":"source LIKE 'notificacion:%'"),new String[]{type});
  try{while(q.moveToNext()){
   if(NormalDuplicatePrefs.sameAmount(c)&&Math.abs(q.getDouble(0)-amount)>0.001d)continue;
   if(NormalDuplicatePrefs.sameCurrency(c)&&!eq(q.getString(1),currency))continue;
   if(NormalDuplicatePrefs.sameDate(c)&&!sameDay(q.getLong(3),ts))continue;
   String old=q.getString(2);
   if(NormalDuplicatePrefs.exactName(c)&&!nameKey(old).equals(nameKey(description)))continue;
   if(NormalDuplicatePrefs.similarName(c)&&!ExpenseDb.merchantMatches(description,old))continue;
   return true;
  }return false;}finally{q.close();}
 }
 private static boolean eq(String a,String b){return a==null?b==null:a.equalsIgnoreCase(b==null?"":b);}
 private static boolean sameDay(long a,long b){Calendar x=Calendar.getInstance(),y=Calendar.getInstance();x.setTimeInMillis(a);y.setTimeInMillis(b);return x.get(Calendar.YEAR)==y.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==y.get(Calendar.DAY_OF_YEAR);}
 private static String nameKey(String s){if(s==null)return"";return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim();}
}
