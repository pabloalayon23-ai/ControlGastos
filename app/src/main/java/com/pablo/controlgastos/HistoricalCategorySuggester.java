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
        HashSet<String> existing=new HashSet<>();for(DetectionRules.Rule r:DetectionRules.load(c))existing.add(DetectionRules.norm(r.word));
        ArrayList<String> texts=new ArrayList<>();ExpenseDb db=new ExpenseDb(c);Cursor q=db.getReadableDatabase().rawQuery("SELECT description,original_text FROM tx WHERE type='GASTO'",null);while(q.moveToNext()){String d=q.getString(0),o=q.getString(1);texts.add(DetectionRules.norm((d==null?"":d)+" "+(o==null?"":o)));}q.close();
        ArrayList<Suggestion> out=new ArrayList<>();
        for(String[]x:CANDIDATES){String w=DetectionRules.norm(x[0]);if(existing.contains(w))continue;int hits=0;for(String t:texts)if(t.contains(w))hits++;if(hits>0)out.add(new Suggestion(x[0],x[1],hits));}
        out.sort((a,b)->{int d=Integer.compare(b.hits,a.hits);return d!=0?d:a.category.compareToIgnoreCase(b.category);});return out;
    }

    public static void apply(Context c,List<Suggestion> add){
        ArrayList<DetectionRules.Rule> old=DetectionRules.load(c);ArrayList<String>w=new ArrayList<>(),cats=new ArrayList<>();HashSet<String>seen=new HashSet<>();for(DetectionRules.Rule r:old){w.add(r.word);cats.add(r.category);seen.add(DetectionRules.norm(r.word));}
        for(Suggestion s:add)if(seen.add(DetectionRules.norm(s.word))){w.add(s.word);cats.add(s.category);}DetectionRules.save(c,w,cats);DetectionRules.reclassifyExisting(c);
    }
}
