package com.pablo.controlgastos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MovementReconciler {
    private MovementReconciler(){}

    public static class Candidate {
        public long idA,idB,ts;
        public String type,currency,descriptionA,descriptionB,categoryA,categoryB,sourceA,sourceB;
        public double amount;
        public String dateLabel;
    }

    private static class Row {
        long id,ts; double amount;
        String type,currency,category,description,original,source,fingerprint;
    }

    public static ArrayList<Candidate> findCandidates(ExpenseDb db){
        ArrayList<Row> rows=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,original_text,COALESCE(original_ts,ts),source,fingerprint FROM tx ORDER BY COALESCE(original_ts,ts),id",null);
        try{
            while(c.moveToNext()){
                Row r=new Row();r.id=c.getLong(0);r.type=c.getString(1);r.amount=c.getDouble(2);r.currency=c.getString(3);r.category=c.getString(4);r.description=c.getString(5);r.original=c.getString(6);r.ts=c.getLong(7);r.source=c.getString(8);r.fingerprint=c.getString(9);rows.add(r);
            }
        }finally{c.close();}

        LinkedHashMap<String,ArrayList<Row>> groups=new LinkedHashMap<>();
        SimpleDateFormat keyDate=new SimpleDateFormat("yyyyMMdd",Locale.US);
        for(Row r:rows){
            long cents=Math.round(r.amount*100.0d);
            String key=keyDate.format(new Date(r.ts))+"|"+safe(r.type)+"|"+safe(r.currency)+"|"+cents;
            ArrayList<Row> g=groups.get(key);if(g==null){g=new ArrayList<>();groups.put(key,g);}g.add(r);
        }

        ArrayList<Candidate> out=new ArrayList<>();
        SimpleDateFormat label=new SimpleDateFormat("dd/MM/yyyy",Locale.US);
        for(ArrayList<Row> g:groups.values()){
            if(g.size()<2)continue;
            for(int i=0;i<g.size();i++)for(int j=i+1;j<g.size();j++){
                Row a=g.get(i),b=g.get(j);
                if(!ExpenseDb.merchantMatches(a.description,b.description))continue;
                Candidate x=new Candidate();x.idA=a.id;x.idB=b.id;x.ts=Math.min(a.ts,b.ts);x.type=a.type;x.currency=a.currency;x.amount=a.amount;x.descriptionA=a.description;x.descriptionB=b.description;x.categoryA=a.category;x.categoryB=b.category;x.sourceA=a.source;x.sourceB=b.source;x.dateLabel=label.format(new Date(x.ts));out.add(x);
            }
        }
        return out;
    }

    public static boolean merge(ExpenseDb db,Candidate candidate){
        Row a=load(db,candidate.idA),b=load(db,candidate.idB);if(a==null||b==null)return false;
        if(!sameDay(a.ts,b.ts)||!safe(a.type).equals(safe(b.type))||!safe(a.currency).equals(safe(b.currency))||Math.round(a.amount*100.0d)!=Math.round(b.amount*100.0d)||!ExpenseDb.merchantMatches(a.description,b.description))return false;

        Row keep=priority(a)>=priority(b)?a:b,drop=keep==a?b:a;
        String bestCategory=betterCategory(keep.category,drop.category);
        String bestDescription=betterDescription(keep.description,drop.description);
        String original=mergeText(keep.original,drop.original);

        db.recordTxHistory(keep.id,"RECONCILE",DetectionRules.rulesSnapshot(dbContext(db)));
        ContentValues v=new ContentValues();
        v.put("category",bestCategory);v.put("description",bestDescription);v.put("original_text",original);v.put("source","conciliado:manual");
        SQLiteDatabase sql=db.getWritableDatabase();
        boolean updated=sql.update("tx",v,"id=?",new String[]{String.valueOf(keep.id)})==1;
        if(!updated)return false;
        db.deleteTx(drop.id);
        return true;
    }

    private static android.content.Context dbContext(ExpenseDb db){
        try{java.lang.reflect.Field f=ExpenseDb.class.getDeclaredField("context");f.setAccessible(true);return (android.content.Context)f.get(db);}catch(Exception e){return null;}
    }

    private static Row load(ExpenseDb db,long id){
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,original_text,COALESCE(original_ts,ts),source,fingerprint FROM tx WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});
        try{if(!c.moveToFirst())return null;Row r=new Row();r.id=c.getLong(0);r.type=c.getString(1);r.amount=c.getDouble(2);r.currency=c.getString(3);r.category=c.getString(4);r.description=c.getString(5);r.original=c.getString(6);r.ts=c.getLong(7);r.source=c.getString(8);r.fingerprint=c.getString(9);return r;}finally{c.close();}
    }

    private static int priority(Row r){String s=safe(r.source).toLowerCase(Locale.ROOT);if(s.startsWith("notificacion:"))return 40;if(s.startsWith("conciliado:"))return 35;if(s.equals("manual"))return 30;if(s.startsWith("banco-xls"))return 20;return 10;}
    private static String betterCategory(String a,String b){boolean ga=genericCategory(a),gb=genericCategory(b);if(ga&&!gb)return b;if(!ga)return a;return safe(a).isEmpty()?b:a;}
    private static boolean genericCategory(String s){String n=safe(s).trim().toLowerCase(Locale.ROOT);return n.isEmpty()||n.equals("banco")||n.equals("otros")||n.equals("sin categoría")||n.equals("sin categoria");}
    private static String betterDescription(String a,String b){String x=safe(a).trim(),y=safe(b).trim();if(x.isEmpty())return y;if(y.isEmpty())return x;String nx=stripPrefix(x),ny=stripPrefix(y);if(ny.length()>nx.length()+2)return y;return x;}
    private static String stripPrefix(String s){return s.replaceFirst("(?i)^\\s*comercio\\s*:\\s*","").trim();}
    private static String mergeText(String a,String b){String x=safe(a).trim(),y=safe(b).trim();if(x.isEmpty())return y;if(y.isEmpty()||x.contains(y))return x;return x+"\n--- Conciliado ---\n"+y;}
    private static boolean sameDay(long a,long b){Calendar x=Calendar.getInstance(),y=Calendar.getInstance();x.setTimeInMillis(a);y.setTimeInMillis(b);return x.get(Calendar.ERA)==y.get(Calendar.ERA)&&x.get(Calendar.YEAR)==y.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==y.get(Calendar.DAY_OF_YEAR);}
    private static String safe(String s){return s==null?"":s;}
}
