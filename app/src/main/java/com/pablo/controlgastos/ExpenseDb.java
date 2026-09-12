package com.pablo.controlgastos;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.text.*;
import java.util.*;

public class ExpenseDb extends SQLiteOpenHelper {
    public static final String DB="gastos.db";
    private static final int DUPLICATE_DAY_TOLERANCE=4;
    private static final double DUPLICATE_AMOUNT_TOLERANCE=0.01d;

    public ExpenseDb(Context c){ super(c,DB,null,2); }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE tx(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, category TEXT, description TEXT, original_text TEXT, ts INTEGER NOT NULL, source TEXT NOT NULL, fingerprint TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_fingerprint ON tx(fingerprint) WHERE fingerprint IS NOT NULL AND fingerprint<>''");
        db.execSQL("CREATE TABLE recurring(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, category TEXT, description TEXT, day INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1, last_ym TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            try{ db.execSQL("ALTER TABLE tx ADD COLUMN fingerprint TEXT"); }catch(Exception ignored){}
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_fingerprint ON tx(fingerprint) WHERE fingerprint IS NOT NULL AND fingerprint<>''");
        }
    }

    public long addTx(String type,double amount,String currency,String category,String description,String original,long ts,String source){
        ContentValues v=new ContentValues(); v.put("type",type); v.put("amount",amount); v.put("currency",currency); v.put("category",category); v.put("description",description); v.put("original_text",original); v.put("ts",ts); v.put("source",source);
        return getWritableDatabase().insert("tx",null,v);
    }

    public boolean updateTx(long id,String type,double amount,String currency,String category,String description,long ts){
        ContentValues v=new ContentValues();
        v.put("type",type); v.put("amount",amount); v.put("currency",currency); v.put("category",category); v.put("description",description); v.put("ts",ts);
        return getWritableDatabase().update("tx",v,"id=?",new String[]{String.valueOf(id)})==1;
    }

    public boolean addImportedTx(String type,double amount,String currency,String category,String description,String original,long ts,String source,String fingerprint){
        if(hasFingerprint(fingerprint)) return false;
        if(linkImportedToNotification(type,amount,currency,category,description,original,ts,source,fingerprint)) return false;
        ContentValues v=new ContentValues(); v.put("type",type); v.put("amount",amount); v.put("currency",currency); v.put("category",category); v.put("description",description); v.put("original_text",original); v.put("ts",ts); v.put("source",source); v.put("fingerprint",fingerprint);
        return getWritableDatabase().insertWithOnConflict("tx",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }

    public boolean hasFingerprint(String fingerprint){
        if(fingerprint==null || fingerprint.isEmpty()) return false;
        Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM tx WHERE fingerprint=? LIMIT 1",new String[]{fingerprint});
        boolean found=c.moveToFirst(); c.close(); return found;
    }

    public boolean existsOriginal(String original){
        Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM tx WHERE original_text=? LIMIT 1",new String[]{original});
        boolean found=c.moveToFirst(); c.close(); return found;
    }

    private long[] duplicateBounds(long ts){
        Calendar a=Calendar.getInstance(); a.setTimeInMillis(ts); a.set(Calendar.HOUR_OF_DAY,0); a.set(Calendar.MINUTE,0); a.set(Calendar.SECOND,0); a.set(Calendar.MILLISECOND,0); a.add(Calendar.DAY_OF_MONTH,-DUPLICATE_DAY_TOLERANCE);
        Calendar b=Calendar.getInstance(); b.setTimeInMillis(ts); b.set(Calendar.HOUR_OF_DAY,0); b.set(Calendar.MINUTE,0); b.set(Calendar.SECOND,0); b.set(Calendar.MILLISECOND,0); b.add(Calendar.DAY_OF_MONTH,DUPLICATE_DAY_TOLERANCE+1);
        return new long[]{a.getTimeInMillis(),b.getTimeInMillis()};
    }

    public boolean hasImportedDuplicate(String type,double amount,String currency,String description,long ts){
        long[] d=duplicateBounds(ts);
        Cursor c=getReadableDatabase().rawQuery("SELECT description FROM tx WHERE type=? AND currency=? AND ABS(amount-?)<=? AND ts>=? AND ts<? AND source LIKE 'banco-xls%'",new String[]{type,currency,String.valueOf(amount),String.valueOf(DUPLICATE_AMOUNT_TOLERANCE),String.valueOf(d[0]),String.valueOf(d[1])});
        try{ while(c.moveToNext()) if(merchantMatches(description,c.getString(0))) return true; return false; }finally{ c.close(); }
    }

    public boolean linkImportedToNotification(String type,double amount,String currency,String category,String description,String original,long ts,String source,String fingerprint){
        if(fingerprint==null || fingerprint.isEmpty() || hasFingerprint(fingerprint)) return false;
        long[] d=duplicateBounds(ts);
        Cursor c=getReadableDatabase().rawQuery("SELECT id,description,category,original_text,ts,source FROM tx WHERE type=? AND currency=? AND ABS(amount-?)<=? AND ts>=? AND ts<? AND source LIKE 'notificacion:%' AND (fingerprint IS NULL OR fingerprint='') ORDER BY ABS(ts-?) ASC",new String[]{type,currency,String.valueOf(amount),String.valueOf(DUPLICATE_AMOUNT_TOLERANCE),String.valueOf(d[0]),String.valueOf(d[1]),String.valueOf(ts)});
        long id=-1,oldTs=0;String oldDesc=null,oldCat=null,oldOriginal=null,oldSource=null;
        try{ while(c.moveToNext()){ if(merchantMatches(description,c.getString(1))){ id=c.getLong(0);oldDesc=c.getString(1);oldCat=c.getString(2);oldOriginal=c.getString(3);oldTs=c.getLong(4);oldSource=c.getString(5);break; } } }finally{ c.close(); }
        if(id<0) return false;
        ContentValues v=new ContentValues();v.put("fingerprint",fingerprint);
        if(isRicherDescription(description,oldDesc))v.put("description",description);
        if(shouldReplaceCategory(oldCat,category))v.put("category",category);
        if(original!=null&&!original.trim().isEmpty())v.put("original_text",mergeOriginal(oldOriginal,original));
        if(ts>0&&Math.abs(ts-oldTs)<=DUPLICATE_DAY_TOLERANCE*86400000L)v.put("ts",ts);
        if(source!=null&&!source.isEmpty())v.put("source","conciliado:"+source+"+"+(oldSource==null?"notificacion":oldSource));
        return getWritableDatabase().update("tx",v,"id=? AND (fingerprint IS NULL OR fingerprint='')",new String[]{String.valueOf(id)})==1;
    }

    private static boolean isRicherDescription(String newer,String older){
        String n=newer==null?"":newer.trim(),o=older==null?"":older.trim();if(n.isEmpty())return false;if(o.isEmpty())return true;
        String ok=merchantKey(o),nk=merchantKey(n);if(nk.isEmpty())return false;
        boolean oldGeneric=ok.equals("transferencia")||ok.equals("notificacion bancaria")||ok.equals("compra visa")||ok.length()<5;
        return oldGeneric||n.length()>o.length()+3||nk.contains(ok);
    }
    private static boolean shouldReplaceCategory(String oldCat,String newCat){
        if(newCat==null||newCat.trim().isEmpty())return false;if(oldCat==null||oldCat.trim().isEmpty())return true;
        String o=oldCat.trim().toLowerCase(Locale.ROOT);return o.equals("sin categoría")||o.equals("sin categoria")||o.equals("otros")||o.equals("banco")||o.equals("transferencias");
    }
    private static String mergeOriginal(String a,String b){String x=a==null?"":a.trim(),y=b==null?"":b.trim();if(x.isEmpty())return y;if(y.isEmpty()||x.contains(y))return x;return x+"\n--- Excel eBROU ---\n"+y;}

    public static boolean merchantMatches(String a,String b){ String x=merchantKey(a),y=merchantKey(b); if(x.length()<4 || y.length()<4) return false; return x.contains(y) || y.contains(x); }
    private static String merchantKey(String s){ if(s==null)return ""; String n=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT); n=n.replaceAll("(?i)\\b(comercio|compra|debito|credito|tarjeta|visa|brou|presencial|transaccion|movimiento|notificacion|bancaria|transferencia|transf|trf)\\b"," "); return n.replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim(); }

    public long addRecurring(String type,double amount,String currency,String category,String description,int day){
        Calendar prev=Calendar.getInstance();prev.add(Calendar.MONTH,-1);String last=new SimpleDateFormat("yyyy-MM",Locale.US).format(prev.getTime());
        ContentValues v=new ContentValues(); v.put("type",type); v.put("amount",amount); v.put("currency",currency); v.put("category",category); v.put("description",description); v.put("day",day); v.put("active",1);v.put("last_ym",last); return getWritableDatabase().insert("recurring",null,v);
    }
    public void materializeRecurring(){
        Calendar now=Calendar.getInstance();SimpleDateFormat ymf=new SimpleDateFormat("yyyy-MM",Locale.US);String currentYm=ymf.format(now.getTime());Cursor c=getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,day,last_ym FROM recurring WHERE active=1",null);
        while(c.moveToNext()){
            long id=c.getLong(0);String last=c.getString(7);int ruleDay=c.getInt(6);
            Calendar month=Calendar.getInstance();month.set(Calendar.DAY_OF_MONTH,1);month.set(Calendar.HOUR_OF_DAY,0);month.set(Calendar.MINUTE,0);month.set(Calendar.SECOND,0);month.set(Calendar.MILLISECOND,0);
            if(last!=null&&!last.trim().isEmpty())try{Date ld=ymf.parse(last);month.setTime(ld);month.add(Calendar.MONTH,1);}catch(Exception ignored){}
            else {month.setTime(now.getTime());month.set(Calendar.DAY_OF_MONTH,1);}
            int guard=0;String newest=last;
            while(!ymf.format(month.getTime()).equals(currentYm)&&month.before(now)&&guard++<120){materializeRecurringMonth(c,month,ruleDay);newest=ymf.format(month.getTime());month.add(Calendar.MONTH,1);}
            if(ymf.format(month.getTime()).equals(currentYm)&&now.get(Calendar.DAY_OF_MONTH)>=Math.max(1,Math.min(ruleDay,now.getActualMaximum(Calendar.DAY_OF_MONTH)))){materializeRecurringMonth(c,month,ruleDay);newest=currentYm;}
            if(newest!=null&&!newest.equals(last)){ContentValues v=new ContentValues();v.put("last_ym",newest);getWritableDatabase().update("recurring",v,"id=?",new String[]{String.valueOf(id)});}
        }c.close();
    }
    private void materializeRecurringMonth(Cursor c,Calendar month,int ruleDay){Calendar d=(Calendar)month.clone();int day=Math.max(1,Math.min(ruleDay,d.getActualMaximum(Calendar.DAY_OF_MONTH)));d.set(Calendar.DAY_OF_MONTH,day);d.set(Calendar.HOUR_OF_DAY,12);d.set(Calendar.MINUTE,0);d.set(Calendar.SECOND,0);d.set(Calendar.MILLISECOND,0);String marker="REC:"+c.getLong(0)+":"+new SimpleDateFormat("yyyy-MM",Locale.US).format(d.getTime());if(existsOriginal(marker))return;addTx(c.getString(1),c.getDouble(2),c.getString(3),c.getString(4),c.getString(5),marker,d.getTimeInMillis(),"recurrente");}

    public Cursor monthTx(){ Calendar a=Calendar.getInstance();a.set(Calendar.DAY_OF_MONTH,1);a.set(Calendar.HOUR_OF_DAY,0);a.set(Calendar.MINUTE,0);a.set(Calendar.SECOND,0);a.set(Calendar.MILLISECOND,0);Calendar b=(Calendar)a.clone();b.add(Calendar.MONTH,1);return getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,original_text,ts,source FROM tx WHERE ts>=? AND ts<? ORDER BY ts DESC",new String[]{String.valueOf(a.getTimeInMillis()),String.valueOf(b.getTimeInMillis())}); }
    public Cursor allTx(){ return getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,original_text,ts,source,fingerprint FROM tx ORDER BY ts ASC,id ASC",null); }
    public Cursor allRecurring(){ return getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,day,active FROM recurring ORDER BY type DESC,description",null); }
    public void deleteTx(long id){ getWritableDatabase().delete("tx","id=?",new String[]{String.valueOf(id)}); }
    public void deleteRecurring(long id){ getWritableDatabase().delete("recurring","id=?",new String[]{String.valueOf(id)}); }
}
