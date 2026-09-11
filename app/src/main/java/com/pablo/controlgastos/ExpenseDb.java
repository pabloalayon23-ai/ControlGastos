package com.pablo.controlgastos;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.text.*;
import java.util.*;

public class ExpenseDb extends SQLiteOpenHelper {
    public static final String DB="gastos.db";
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
        if("GASTO".equals(type) && linkImportedToNotification(type,amount,currency,ts,fingerprint)) return false;
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

    private long[] dayBounds(long ts){
        Calendar a=Calendar.getInstance(); a.setTimeInMillis(ts); a.set(Calendar.HOUR_OF_DAY,0); a.set(Calendar.MINUTE,0); a.set(Calendar.SECOND,0); a.set(Calendar.MILLISECOND,0);
        Calendar b=(Calendar)a.clone(); b.add(Calendar.DAY_OF_MONTH,1);
        return new long[]{a.getTimeInMillis(),b.getTimeInMillis()};
    }

    public int countUnmatchedNotificationMatches(String type,double amount,String currency,long ts){
        long[] d=dayBounds(ts);
        Cursor c=getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM tx WHERE type=? AND currency=? AND ABS(amount-?)<0.005 AND ts>=? AND ts<? AND source LIKE 'notificacion:%' AND (fingerprint IS NULL OR fingerprint='')",
            new String[]{type,currency,String.valueOf(amount),String.valueOf(d[0]),String.valueOf(d[1])});
        int n=0; if(c.moveToFirst()) n=c.getInt(0); c.close(); return n;
    }

    public boolean linkImportedToNotification(String type,double amount,String currency,long ts,String fingerprint){
        if(fingerprint==null || fingerprint.isEmpty() || hasFingerprint(fingerprint)) return false;
        long[] d=dayBounds(ts);
        Cursor c=getReadableDatabase().rawQuery(
            "SELECT id FROM tx WHERE type=? AND currency=? AND ABS(amount-?)<0.005 AND ts>=? AND ts<? AND source LIKE 'notificacion:%' AND (fingerprint IS NULL OR fingerprint='') ORDER BY ts LIMIT 1",
            new String[]{type,currency,String.valueOf(amount),String.valueOf(d[0]),String.valueOf(d[1])});
        if(!c.moveToFirst()){ c.close(); return false; }
        long id=c.getLong(0); c.close();
        ContentValues v=new ContentValues(); v.put("fingerprint",fingerprint);
        return getWritableDatabase().update("tx",v,"id=? AND (fingerprint IS NULL OR fingerprint='')",new String[]{String.valueOf(id)})==1;
    }

    public long addRecurring(String type,double amount,String currency,String category,String description,int day){
        ContentValues v=new ContentValues(); v.put("type",type); v.put("amount",amount); v.put("currency",currency); v.put("category",category); v.put("description",description); v.put("day",day); v.put("active",1);
        return getWritableDatabase().insert("recurring",null,v);
    }

    public void materializeRecurring(){
        Calendar now=Calendar.getInstance(); String ym=new SimpleDateFormat("yyyy-MM",Locale.US).format(now.getTime());
        Cursor c=getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,day,last_ym FROM recurring WHERE active=1",null);
        while(c.moveToNext()){
            String last=c.getString(7); if(ym.equals(last)) continue;
            int day=Math.max(1,Math.min(c.getInt(6),now.getActualMaximum(Calendar.DAY_OF_MONTH)));
            if(now.get(Calendar.DAY_OF_MONTH) < day) continue;
            Calendar d=(Calendar)now.clone(); d.set(Calendar.DAY_OF_MONTH,day); d.set(Calendar.HOUR_OF_DAY,12); d.set(Calendar.MINUTE,0); d.set(Calendar.SECOND,0); d.set(Calendar.MILLISECOND,0);
            addTx(c.getString(1),c.getDouble(2),c.getString(3),c.getString(4),c.getString(5),"Movimiento recurrente",d.getTimeInMillis(),"recurrente");
            ContentValues v=new ContentValues(); v.put("last_ym",ym); getWritableDatabase().update("recurring",v,"id=?",new String[]{String.valueOf(c.getLong(0))});
        }
        c.close();
    }

    public Cursor monthTx(){
        Calendar a=Calendar.getInstance(); a.set(Calendar.DAY_OF_MONTH,1); a.set(Calendar.HOUR_OF_DAY,0); a.set(Calendar.MINUTE,0); a.set(Calendar.SECOND,0); a.set(Calendar.MILLISECOND,0);
        Calendar b=(Calendar)a.clone(); b.add(Calendar.MONTH,1);
        return getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,original_text,ts,source FROM tx WHERE ts>=? AND ts<? ORDER BY ts DESC",new String[]{String.valueOf(a.getTimeInMillis()),String.valueOf(b.getTimeInMillis())});
    }

    public Cursor allRecurring(){ return getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,day,active FROM recurring ORDER BY type DESC,description",null); }
    public void deleteTx(long id){ getWritableDatabase().delete("tx","id=?",new String[]{String.valueOf(id)}); }
    public void deleteRecurring(long id){ getWritableDatabase().delete("recurring","id=?",new String[]{String.valueOf(id)}); }
}
