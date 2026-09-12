package com.pablo.controlgastos;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jxl.*;
import jxl.write.*;

public final class BackupXlsManager {
    private static final String MAGIC="CONTROLGASTOS_BACKUP";
    private static final String FORMAT="2";
    private static final String[] PREF_FILES={"detection_rules","blacklist_rules","category_prefs","budget_projection","appearance","security","behavior"};
    private BackupXlsManager(){}

    public static class Info {
        public boolean isBackup;
        public int categories;
        public int recurring;
        public int prefEntries;
    }

    public static class RestoreResult {
        public int categories;
        public int recurring;
        public int prefEntries;
        public int movedToOther;
    }

    public static void appendBackup(Context c, WritableWorkbook wb, ExpenseDb db) throws Exception {
        int idx=wb.getNumberOfSheets();
        WritableSheet meta=wb.createSheet("_CG_BACKUP_META",idx++);
        meta.addCell(new Label(0,0,"magic"));meta.addCell(new Label(1,0,MAGIC));
        meta.addCell(new Label(0,1,"format"));meta.addCell(new Label(1,1,FORMAT));
        meta.addCell(new Label(0,2,"app"));meta.addCell(new Label(1,2,"ControlGastos"));

        WritableSheet prefs=wb.createSheet("_CG_PREFS",idx++);
        prefs.addCell(new Label(0,0,"archivo"));prefs.addCell(new Label(1,0,"clave"));prefs.addCell(new Label(2,0,"tipo"));prefs.addCell(new Label(3,0,"valor"));
        int pr=1;
        for(String file:PREF_FILES){
            Map<String,?> all=c.getSharedPreferences(file,Context.MODE_PRIVATE).getAll();
            for(Map.Entry<String,?> e:all.entrySet()){
                String[] enc=encodeValue(e.getValue());if(enc==null)continue;
                prefs.addCell(new Label(0,pr,file));prefs.addCell(new Label(1,pr,e.getKey()));prefs.addCell(new Label(2,pr,enc[0]));prefs.addCell(new Label(3,pr,enc[1]));pr++;
            }
        }

        LinkedHashSet<String> cats=new LinkedHashSet<>(CategoryPrefs.definedCategories(c));
        Cursor dc=db.getReadableDatabase().rawQuery("SELECT DISTINCT category FROM tx WHERE category IS NOT NULL AND TRIM(category)<>'' ORDER BY category COLLATE NOCASE",null);
        try{while(dc.moveToNext())cats.add(dc.getString(0));}finally{dc.close();}
        WritableSheet cs=wb.createSheet("_CG_CATEGORIAS",idx++);
        cs.addCell(new Label(0,0,"Categoría"));cs.addCell(new Label(1,0,"Color"));int cr=1;
        for(String cat:cats){if(cat==null||cat.trim().isEmpty())continue;cs.addCell(new Label(0,cr,cat));cs.addCell(new jxl.write.Number(1,cr,CategoryPrefs.color(c,cat)));cr++;}

        WritableSheet rec=wb.createSheet("_CG_RECURRENTES",idx++);
        String[] rh={"Tipo","Monto","Moneda","Categoría","Descripción","Día","Activo","Último mes"};for(int i=0;i<rh.length;i++)rec.addCell(new Label(i,0,rh[i]));
        Cursor rc=db.getReadableDatabase().rawQuery("SELECT type,amount,currency,category,description,day,active,last_ym FROM recurring ORDER BY id",null);int rr=1;
        try{while(rc.moveToNext()){
            rec.addCell(new Label(0,rr,nz(rc.getString(0))));rec.addCell(new jxl.write.Number(1,rr,rc.getDouble(1)));rec.addCell(new Label(2,rr,nz(rc.getString(2))));rec.addCell(new Label(3,rr,nz(rc.getString(3))));rec.addCell(new Label(4,rr,nz(rc.getString(4))));rec.addCell(new jxl.write.Number(5,rr,rc.getInt(5)));rec.addCell(new jxl.write.Number(6,rr,rc.getInt(6)));rec.addCell(new Label(7,rr,nz(rc.getString(7))));rr++;
        }}finally{rc.close();}
    }

    public static Info inspect(Context c, Uri uri){
        Info info=new Info();InputStream in=null;Workbook wb=null;
        try{in=c.getContentResolver().openInputStream(uri);if(in==null)return info;wb=Workbook.getWorkbook(in);if(!valid(wb))return info;info.isBackup=true;Sheet cats=wb.getSheet("_CG_CATEGORIAS");if(cats!=null)info.categories=Math.max(0,cats.getRows()-1);Sheet rec=wb.getSheet("_CG_RECURRENTES");if(rec!=null)info.recurring=Math.max(0,rec.getRows()-1);Sheet p=wb.getSheet("_CG_PREFS");if(p!=null)info.prefEntries=Math.max(0,p.getRows()-1);return info;}catch(Exception ignored){return info;}finally{if(wb!=null)wb.close();if(in!=null)try{in.close();}catch(Exception ignored){}}
    }

    public static RestoreResult restore(Context c,Uri uri,ExpenseDb db)throws Exception{
        InputStream in=c.getContentResolver().openInputStream(uri);if(in==null)throw new IOException("No se pudo abrir el respaldo");Workbook wb=null;
        try{wb=Workbook.getWorkbook(in);if(!valid(wb))throw new IOException("El archivo no contiene un respaldo completo de ControlGastos");RestoreResult result=new RestoreResult();restorePrefs(c,wb,result);LinkedHashSet<String> allowed=restoreCategories(c,wb,result);restoreRecurring(db,wb,result);result.movedToOther=applyCategoryConfiguration(db,allowed);db.reapplySalaryMonthRule();return result;}finally{if(wb!=null)wb.close();try{in.close();}catch(Exception ignored){}}
    }

    private static boolean valid(Workbook wb){Sheet s=wb.getSheet("_CG_BACKUP_META");return s!=null&&s.getRows()>0&&s.getColumns()>1&&MAGIC.equals(s.getCell(1,0).getContents());}

    private static void restorePrefs(Context c,Workbook wb,RestoreResult out)throws Exception{
        Sheet s=wb.getSheet("_CG_PREFS");if(s==null)throw new IOException("Falta la configuración del respaldo");
        HashMap<String,SharedPreferences.Editor> editors=new HashMap<>();
        for(String file:PREF_FILES)editors.put(file,c.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear());
        for(int r=1;r<s.getRows();r++){
            String file=s.getCell(0,r).getContents(),key=s.getCell(1,r).getContents(),type=s.getCell(2,r).getContents(),value=s.getCell(3,r).getContents();SharedPreferences.Editor e=editors.get(file);if(e==null||key.isEmpty())continue;decodeInto(e,key,type,value);out.prefEntries++;
        }
        for(SharedPreferences.Editor e:editors.values())e.commit();
    }

    private static LinkedHashSet<String> restoreCategories(Context c,Workbook wb,RestoreResult out)throws Exception{
        Sheet s=wb.getSheet("_CG_CATEGORIAS");if(s==null)throw new IOException("Falta la lista de categorías del respaldo");LinkedHashSet<String> cats=new LinkedHashSet<>();
        c.getSharedPreferences("category_prefs",Context.MODE_PRIVATE).edit().clear().commit();
        for(int r=1;r<s.getRows();r++){
            String cat=s.getCell(0,r).getContents().trim();if(cat.isEmpty())continue;cats.add(cat);try{double d=((NumberCell)s.getCell(1,r)).getValue();CategoryPrefs.setColor(c,cat,(int)Math.round(d));}catch(Exception ignored){}out.categories++;
        }
        CategoryPrefs.replaceRegistry(c,cats);cats.add("Otros");return cats;
    }

    private static void restoreRecurring(ExpenseDb db,Workbook wb,RestoreResult out)throws Exception{
        Sheet s=wb.getSheet("_CG_RECURRENTES");if(s==null)return;SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{sql.delete("recurring",null,null);for(int r=1;r<s.getRows();r++){
            String type=s.getCell(0,r).getContents().trim();if(type.isEmpty())continue;double amount=cellDouble(s.getCell(1,r));String currency=s.getCell(2,r).getContents();String category=s.getCell(3,r).getContents();String description=s.getCell(4,r).getContents();int day=(int)Math.round(cellDouble(s.getCell(5,r)));int active=(int)Math.round(cellDouble(s.getCell(6,r)));String last=s.getCell(7,r).getContents();ContentValues v=new ContentValues();v.put("type",type);v.put("amount",amount);v.put("currency",currency);v.put("category",category);v.put("description",description);v.put("day",day);v.put("active",active);v.put("last_ym",last);sql.insert("recurring",null,v);out.recurring++;}sql.setTransactionSuccessful();}finally{sql.endTransaction();}
    }

    private static int applyCategoryConfiguration(ExpenseDb db,Set<String> allowed){HashSet<String> norm=new HashSet<>();for(String x:allowed)norm.add(DetectionRules.norm(x));Cursor c=db.getReadableDatabase().rawQuery("SELECT id,category FROM tx WHERE category IS NOT NULL AND TRIM(category)<>''",null);ArrayList<Long> ids=new ArrayList<>();try{while(c.moveToNext()){String cat=c.getString(1);if(!norm.contains(DetectionRules.norm(cat)))ids.add(c.getLong(0));}}finally{c.close();}ContentValues v=new ContentValues();v.put("category","Otros");for(Long id:ids)db.getWritableDatabase().update("tx",v,"id=?",new String[]{String.valueOf(id)});return ids.size();}

    private static double cellDouble(Cell c){if(c instanceof NumberCell)return((NumberCell)c).getValue();try{return Double.parseDouble(c.getContents().replace(',','.'));}catch(Exception e){return 0;}}
    private static String nz(String s){return s==null?"":s;}
    private static String b64(String s){return Base64.encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);}
    private static String ub64(String s){return new String(Base64.decode(s,Base64.DEFAULT),StandardCharsets.UTF_8);}

    private static String[] encodeValue(Object v){if(v instanceof Boolean)return new String[]{"B",String.valueOf(v)};if(v instanceof Integer)return new String[]{"I",String.valueOf(v)};if(v instanceof Long)return new String[]{"L",String.valueOf(v)};if(v instanceof Float)return new String[]{"F",String.valueOf(v)};if(v instanceof String)return new String[]{"S",b64((String)v)};if(v instanceof Set){StringBuilder b=new StringBuilder();for(Object x:(Set<?>)v){if(b.length()>0)b.append(',');b.append(b64(String.valueOf(x)));}return new String[]{"SS",b.toString()};}return null;}
    private static void decodeInto(SharedPreferences.Editor e,String key,String type,String value){try{switch(type){case"B":e.putBoolean(key,Boolean.parseBoolean(value));break;case"I":e.putInt(key,Integer.parseInt(value));break;case"L":e.putLong(key,Long.parseLong(value));break;case"F":e.putFloat(key,Float.parseFloat(value));break;case"S":e.putString(key,ub64(value));break;case"SS":HashSet<String> set=new HashSet<>();if(!value.isEmpty())for(String x:value.split(","))set.add(ub64(x));e.putStringSet(key,set);break;}}catch(Exception ignored){}}
}
