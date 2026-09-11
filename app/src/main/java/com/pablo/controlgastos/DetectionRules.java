package com.pablo.controlgastos;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.Normalizer;
import java.util.*;

public final class DetectionRules {
    public static final int MAX_RULES=50;
    private static final String PREF="detection_rules";
    private DetectionRules(){}

    public static class Rule {
        public final String word,category;
        Rule(String w,String c){word=w;category=c;}
    }

    public static ArrayList<Rule> load(Context c){
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
            e.putString("word_"+i,w); e.putString("cat_"+i,cat);
        }
        e.apply();
    }

    public static boolean matches(Context c,String text){
        String n=norm(text);
        for(Rule r:load(c)) if(n.contains(norm(r.word))) return true;
        return false;
    }

    public static String categoryFor(Context c,String text,String fallback){
        String n=norm(text);
        for(Rule r:load(c)) if(n.contains(norm(r.word)) && !r.category.isEmpty()) return r.category;
        return fallback;
    }

    public static String norm(String s){
        if(s==null)return "";
        return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }
}
