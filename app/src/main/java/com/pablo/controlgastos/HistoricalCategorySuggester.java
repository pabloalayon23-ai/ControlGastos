package com.pablo.controlgastos;

import android.content.Context;
import android.database.Cursor;
import java.util.*;

public final class HistoricalCategorySuggester {
    private HistoricalCategorySuggester(){}

    public static class Suggestion {
        public final String word,category; public final int hits;
        Suggestion(String w,String c,int h){word=w;category=c;hits=h;}
    }

    private static final String[][] CANDIDATES={
        {"disco","Disco"},
        {"devoto","Supermercado"},{"geant","Supermercado"},{"tata","Supermercado"},{"tienda inglesa","Supermercado"},{"supermercado","Supermercado"},
        {"panaderia","Comida"},{"pedidosya","Comida"},{"pedidos ya","Comida"},{"restaurant","Comida"},{"restaurante","Comida"},{"delivery","Comida"},
        {"ancap","Transporte"},{"axion","Transporte"},{"telepeaje","Transporte"},{"peaje","Transporte"},{"nafta","Transporte"},{"gasoil","Transporte"},{"combustible","Transporte"},
        {"ute","Servicios"},{"ose","Servicios"},{"antel","Servicios"},{"movistar","Servicios"},{"claro","Servicios"},{"internet","Servicios"},
        {"martinelli","Salud"},{"farmashop","Salud"},{"farmacia","Salud"},{"san roque","Salud"},
        {"google","Tecnología y suscripciones"},{"claude","Tecnología y suscripciones"},{"netflix","Tecnología y suscripciones"},{"spotify","Tecnología y suscripciones"},{"icloud","Tecnología y suscripciones"},
        {"escuela","Educación"},{"colegio","Educación"},{"universidad","Educación"},{"graduacion","Educación"},
        {"taekwondo","Familia e hijos"},{"dia del niño","Familia e hijos"},
        {"regalo","Regalos y eventos"},{"cumple","Regalos y eventos"},
        {"ferreteria","Hogar"},{"sodimac","Hogar"},{"electrodomestico","Hogar"},
        {"ropa","Ropa y compras personales"},{"calzado","Ropa y compras personales"},
        {"bse","Seguros e impuestos"},{"seguro","Seguros e impuestos"},{"patente","Seguros e impuestos"},{"sucive","Seguros e impuestos"},{"contribucion","Seguros e impuestos"},{"intendencia","Seguros e impuestos"},
        {"pago de tc","Pago de tarjeta"},{"pago tc","Pago de tarjeta"},{"pago tarjeta","Pago de tarjeta"},{"pago de tarjeta","Pago de tarjeta"}
    };

    public static ArrayList<Suggestion> scan(Context c){
        HashMap<String,String> existing=new HashMap<>();for(DetectionRules.Rule r:DetectionRules.load(c))existing.put(DetectionRules.norm(r.word),r.category==null?"":r.category.trim());
        ArrayList<String> texts=new ArrayList<>();ExpenseDb db=new ExpenseDb(c);Cursor q=db.getReadableDatabase().rawQuery("SELECT description,original_text FROM tx WHERE type='GASTO'",null);while(q.moveToNext()){String d=q.getString(0),o=q.getString(1);texts.add(DetectionRules.norm((d==null?"":d)+" "+(o==null?"":o)));}q.close();
        ArrayList<Suggestion> out=new ArrayList<>();
        for(String[]x:CANDIDATES){String w=DetectionRules.norm(x[0]);String current=existing.get(w);if(current!=null&&current.equalsIgnoreCase(x[1]))continue;int hits=0;for(String t:texts)if(t.contains(w))hits++;if(hits>0)out.add(new Suggestion(x[0],x[1],hits));}
        out.sort((a,b)->{int d=Integer.compare(b.hits,a.hits);return d!=0?d:a.category.compareToIgnoreCase(b.category);});return out;
    }

    public static void apply(Context c,List<Suggestion> add){
        ArrayList<DetectionRules.Rule> old=DetectionRules.load(c);LinkedHashMap<String,String[]> map=new LinkedHashMap<>();for(DetectionRules.Rule r:old)map.put(DetectionRules.norm(r.word),new String[]{r.word,r.category});for(Suggestion s:add)map.put(DetectionRules.norm(s.word),new String[]{s.word,s.category});ArrayList<String>w=new ArrayList<>(),cats=new ArrayList<>();for(String[]x:map.values()){w.add(x[0]);cats.add(x[1]);}DetectionRules.save(c,w,cats);DetectionRules.reclassifyExisting(c);
    }
}
