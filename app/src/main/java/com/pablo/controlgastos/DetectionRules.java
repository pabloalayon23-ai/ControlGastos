package com.pablo.controlgastos;

import android.content.*;
import android.database.Cursor;
import java.text.Normalizer;
import java.util.*;

public final class DetectionRules {
    private static final String PREF="detection_rules", COUNT="rule_count", INIT2="initialized_dynamic_v2";
    private DetectionRules(){}

    public static class Rule {
        public final String word,category;
        Rule(String w,String c){word=w;category=c;}
    }

    private static final String[][] DEFAULTS={
        {"farmacia","Salud"},{"san roque","Salud"},{"farmashop","Salud"},
        {"disco","Supermercado"},{"devoto","Supermercado"},{"geant","Supermercado"},{"tata","Supermercado"},{"tienda inglesa","Supermercado"},{"supermercado","Supermercado"},
        {"ancap","Combustible"},{"combustible","Combustible"},{"nafta","Combustible"},{"gasoil","Combustible"},{"estacion de servicio","Combustible"},
        {"ute","Servicios"},{"ose","Servicios"},{"antel","Servicios"},{"movistar","Servicios"},{"claro","Servicios"},{"internet","Servicios"},
        {"restaurante","Comida"},{"restaurant","Comida"},{"delivery","Comida"},{"pedidosya","Comida"},{"pedidos ya","Comida"},
        {"transferencia","Transferencias"},{"transf","Transferencias"},{"paganza","Pagos"},
        {"colegio","Educación"},{"escuela","Educación"},{"universidad","Educación"},
        {"seguro","Seguros"},{"bse","Seguros"},{"patente","Impuestos"},{"sucive","Impuestos"},{"intendencia","Impuestos"},{"contribucion","Impuestos"}
    };

    private static void ensure(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(p.getBoolean(INIT2,false))return;
        ArrayList<String>w=new ArrayList<>(),cats=new ArrayList<>();
        if(p.getBoolean("initialized_v1",false)){
            for(int i=0;i<50;i++){String x=p.getString("word_"+i,"").trim();if(!x.isEmpty()){w.add(x);cats.add(p.getString("cat_"+i,"").trim());}}
        }else{
            for(String[]d:DEFAULTS){w.add(d[0]);cats.add(d[1]);}
        }
        saveInternal(c,w,cats);
    }

    private static void saveInternal(Context c,List<String> words,List<String> cats){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);int old=p.getInt(COUNT,50);SharedPreferences.Editor e=p.edit();int n=0;
        for(int i=0;i<words.size();i++){
            String w=words.get(i)==null?"":words.get(i).trim();if(w.isEmpty())continue;
            String cat=i<cats.size()&&cats.get(i)!=null?cats.get(i).trim():"";
            e.putString("word_"+n,w);e.putString("cat_"+n,cat);n++;
        }
        for(int i=n;i<Math.max(old,50);i++){e.remove("word_"+i);e.remove("cat_"+i);}
        e.putInt(COUNT,n).putBoolean(INIT2,true).putBoolean("initialized_v1",true).apply();
    }

    public static ArrayList<Rule> load(Context c){ensure(c);SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);ArrayList<Rule>out=new ArrayList<>();int n=p.getInt(COUNT,0);for(int i=0;i<n;i++){String w=p.getString("word_"+i,"").trim();if(!w.isEmpty())out.add(new Rule(w,p.getString("cat_"+i,"").trim()));}return out;}
    public static void save(Context c,List<String> words,List<String> cats){ensure(c);saveInternal(c,words,cats);}

    public static LinkedHashSet<String> categoryNames(Context c){LinkedHashSet<String>out=new LinkedHashSet<>();for(Rule r:load(c))if(!r.category.trim().isEmpty()&&!r.category.equalsIgnoreCase("Otros"))out.add(r.category.trim());out.add("Otros");return out;}
    public static boolean matches(Context c,String text){String n=norm(text);for(Rule r:load(c))if(n.contains(norm(r.word)))return true;return false;}
    public static String categoryFor(Context c,String text,String fallback){String n=norm(text);for(Rule r:load(c))if(n.contains(norm(r.word))&&!r.category.isEmpty())return r.category;return fallback;}

    public static void renameCategory(Context c,String oldName,String newName){
        ArrayList<Rule>rules=load(c);ArrayList<String>w=new ArrayList<>(),cats=new ArrayList<>();for(Rule r:rules){w.add(r.word);cats.add(r.category.equalsIgnoreCase(oldName)?newName:r.category);}saveInternal(c,w,cats);
        ExpenseDb db=new ExpenseDb(c);ContentValues v=new ContentValues();v.put("category",newName);db.getWritableDatabase().update("tx",v,"category=? COLLATE NOCASE",new String[]{oldName});
    }

    public static void reclassifyExisting(Context c){
        ExpenseDb db=new ExpenseDb(c);Cursor q=db.getReadableDatabase().rawQuery("SELECT id,description,original_text,source,type FROM tx",null);ArrayList<Object[]>changes=new ArrayList<>();
        while(q.moveToNext()){
            String source=q.getString(3),type=q.getString(4);if(!"GASTO".equals(type))continue;
            if(!(source!=null&&(source.startsWith("notificacion:")||source.equals("banco-xls"))))continue;
            String text=(q.getString(2)==null?"":q.getString(2))+"\n"+(q.getString(1)==null?"":q.getString(1));String cat=categoryFor(c,text,"Otros");changes.add(new Object[]{q.getLong(0),cat});
        }q.close();
        for(Object[]x:changes){ContentValues v=new ContentValues();v.put("category",(String)x[1]);db.getWritableDatabase().update("tx",v,"id=?",new String[]{String.valueOf((Long)x[0])});}
    }

    public static String norm(String s){if(s==null)return"";return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
}
