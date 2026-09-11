package com.pablo.controlgastos;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.Normalizer;
import java.util.*;

public final class DetectionRules {
    public static final int MAX_RULES=50;
    private static final String PREF="detection_rules";
    private static final String KEY_INITIALIZED="initialized_v1";
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

    private static void ensureDefaults(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(p.getBoolean(KEY_INITIALIZED,false)) return;
        SharedPreferences.Editor e=p.edit();
        for(int i=0;i<MAX_RULES;i++){
            if(i<DEFAULTS.length){e.putString("word_"+i,DEFAULTS[i][0]);e.putString("cat_"+i,DEFAULTS[i][1]);}
            else {e.putString("word_"+i,"");e.putString("cat_"+i,"");}
        }
        e.putBoolean(KEY_INITIALIZED,true).apply();
    }

    public static ArrayList<Rule> load(Context c){
        ensureDefaults(c);
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        ArrayList<Rule> out=new ArrayList<>();
        for(int i=0;i<MAX_RULES;i++){
            String w=p.getString("word_"+i,"").trim();
            String cat=p.getString("cat_"+i,"").trim();
            if(!w.isEmpty()) out.add(new Rule(w,cat));
        }
        return out;
    }

    public static void save(Context c,List<String> words,List<String> cats){
        SharedPreferences.Editor e=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();
        for(int i=0;i<MAX_RULES;i++){
            String w=i<words.size()?words.get(i).trim():"";
            String cat=i<cats.size()?cats.get(i).trim():"";
            e.putString("word_"+i,w);e.putString("cat_"+i,cat);
        }
        e.putBoolean(KEY_INITIALIZED,true).apply();
    }

    public static boolean matches(Context c,String text){
        String n=norm(text);
        for(Rule r:load(c)) if(n.contains(norm(r.word))) return true;
        return false;
    }

    public static String categoryFor(Context c,String text,String fallback){
        String n=norm(text);
        for(Rule r:load(c)) if(n.contains(norm(r.word))&&!r.category.isEmpty()) return r.category;
        return fallback;
    }

    public static String norm(String s){
        if(s==null)return "";
        return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }
}
