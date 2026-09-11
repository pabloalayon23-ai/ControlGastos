package com.pablo.controlgastos;

import android.content.Context;
import android.net.Uri;
import java.io.*;import java.security.MessageDigest;import java.text.*;import java.util.*;import jxl.*;

public class BankXlsImporter {
 public static class Tx{public String type,currency,category,description,original,fingerprint;public double amount;public long ts;}
 public static class Result{public final ArrayList<Tx> rows=new ArrayList<>();public int ignored=0;}

 public static Result parse(Context context,Uri uri)throws Exception{
  InputStream in=context.getContentResolver().openInputStream(uri);
  if(in==null)throw new IOException("No se pudo abrir el archivo");
  Workbook wb=null;
  try{
   wb=Workbook.getWorkbook(in);
   Result out=new Result();Map<String,Integer> occ=new HashMap<>();
   for(Sheet sh:wb.getSheets())parseSheet(context,sh,out,occ);
   if(out.rows.isEmpty())throw new IOException("No encontré movimientos reconocibles en el Excel del banco");
   return out;
  }finally{if(wb!=null)wb.close();try{in.close();}catch(Exception ignored){}}
 }

 private static void parseSheet(Context context,Sheet sh,Result out,Map<String,Integer> occ){
  int rows=sh.getRows(),cols=sh.getColumns();if(rows<1||cols<1)return;
  int header=findHeaderRow(sh);if(header<0)return;
  HeaderMap hm=buildHeaderMap(sh,header);if(hm.date<0||(hm.debit<0&&hm.credit<0&&hm.amount<0))return;
  boolean started=false;int invalidAfterStart=0;
  for(int r=header+1;r<rows;r++){
   String[] cells=new String[cols];boolean any=false;
   for(int c=0;c<cols;c++){cells[c]=clean(sh.getCell(c,r).getContents());if(!cells[c].isEmpty())any=true;}
   if(!any){if(started&&++invalidAfterStart>=8)break;continue;}
   String original=joinRaw(cells);
   if(looksLikeFooter(original)){if(started)break;else continue;}
   Date date=findDate(sh,r,hm,cells);
   AmountInfo ai=findAmount(sh,r,hm,cells);
   if(date==null||ai==null||ai.amount<=0){out.ignored++;if(started&&++invalidAfterStart>=8)break;continue;}
   started=true;invalidAfterStart=0;
   String desc=findDescription(hm,cells),currency=findCurrency(cells,sh.getName());
   String category=DetectionRules.categoryFor(context,original,classify(desc));
   String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(date);
   String canonical=day+"|"+ai.type+"|"+currency+"|"+String.format(Locale.US,"%.2f",ai.amount)+"|"+norm(desc)+"|"+norm(original);
   int n=occ.containsKey(canonical)?occ.get(canonical)+1:1;occ.put(canonical,n);
   Tx tx=new Tx();tx.type=ai.type;tx.amount=ai.amount;tx.currency=currency;tx.category=category;tx.description=desc.isEmpty()?"Movimiento bancario":desc;tx.original=original;tx.ts=atNoon(date).getTime();tx.fingerprint=sha256("bank-xls|"+canonical+"|occ="+n);out.rows.add(tx);
  }
 }

 private static class HeaderMap{int date=-1,desc=-1,debit=-1,credit=-1,amount=-1;}
 private static class AmountInfo{String type;double amount;AmountInfo(String t,double a){type=t;amount=a;}}

 private static int findHeaderRow(Sheet sh){
  int best=-1,bestScore=-1;
  for(int r=0;r<sh.getRows();r++){
   boolean hasDate=false,hasDebit=false,hasCredit=false,hasDesc=false,hasAmount=false;int score=0;
   for(int c=0;c<sh.getColumns();c++){
    String s=norm(sh.getCell(c,r).getContents());
    if(s.equals("fecha")||s.equals("date")){hasDate=true;score+=5;}
    if(hasAny(s,"descripcion","concepto","detalle","movimiento","leyenda")){hasDesc=true;score+=3;}
    if(hasAny(s,"debito","debe","egreso","cargo","retiro")){hasDebit=true;score+=3;}
    if(hasAny(s,"credito","haber","ingreso","deposito")){hasCredit=true;score+=3;}
    if(hasAny(s,"importe","monto","amount")){hasAmount=true;score+=2;}
   }
   boolean valid=hasDate&&((hasDebit||hasCredit)||hasAmount);
   if(valid&&score>bestScore){bestScore=score;best=r;}
  }
  return best;
 }

 private static HeaderMap buildHeaderMap(Sheet sh,int header){
  HeaderMap h=new HeaderMap();if(header<0)return h;
  for(int c=0;c<sh.getColumns();c++){
   String s=norm(sh.getCell(c,header).getContents());
   if(h.date<0&&(s.equals("fecha")||s.equals("date")))h.date=c;
   if(h.desc<0&&hasAny(s,"concepto","descripcion","detalle","movimiento","leyenda","referencia"))h.desc=c;
   if(h.debit<0&&hasAny(s,"debito","debe","egreso","cargo","retiro"))h.debit=c;
   if(h.credit<0&&hasAny(s,"credito","haber","ingreso","deposito"))h.credit=c;
   if(h.amount<0&&hasAny(s,"importe","monto","amount"))h.amount=c;
  }
  return h;
 }

 private static Date findDate(Sheet sh,int r,HeaderMap h,String[] cells){
  if(h.date>=0&&h.date<cells.length){Date d=dateFromCell(sh.getCell(h.date,r),cells[h.date]);if(d!=null)return d;}
  for(int c=0;c<cells.length;c++){Date d=dateFromCell(sh.getCell(c,r),cells[c]);if(d!=null)return d;}
  return null;
 }

 private static Date dateFromCell(Cell cell,String text){
  String s=text==null?"":text.trim();
  if(!s.isEmpty()){
   String[] p={"MM/dd/yyyy","M/d/yyyy","MM/dd/yy","M/d/yy","dd/MM/yyyy","d/M/yyyy","dd/MM/yy","d/M/yy","dd-MM-yyyy","d-M-yyyy","yyyy-MM-dd","MM/dd/yyyy HH:mm","M/d/yyyy HH:mm","dd/MM/yyyy HH:mm","d/M/yyyy HH:mm"};
   for(String x:p)try{SimpleDateFormat f=new SimpleDateFormat(x,Locale.US);f.setLenient(false);Date d=f.parse(s);if(d!=null)return d;}catch(Exception ignored){}
  }
  if(cell instanceof DateCell)try{return((DateCell)cell).getDate();}catch(Exception ignored){}
  if(cell instanceof NumberCell)try{double serial=((NumberCell)cell).getValue();if(serial>20000&&serial<80000)return excelSerialToDate(serial);}catch(Exception ignored){}
  return null;
 }

 private static Date excelSerialToDate(double serial){
  Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"),Locale.US);c.clear();c.set(1899,Calendar.DECEMBER,30,0,0,0);long ms=c.getTimeInMillis()+Math.round(serial*86400000d);return new Date(ms);
 }

 private static AmountInfo findAmount(Sheet sh,int r,HeaderMap h,String[] cells){
  if(h.debit>=0){Double v=cellNumber(sh.getCell(h.debit,r),cells[h.debit]);if(v!=null&&Math.abs(v)>0.0001)return new AmountInfo("GASTO",Math.abs(v));}
  if(h.credit>=0){Double v=cellNumber(sh.getCell(h.credit,r),cells[h.credit]);if(v!=null&&Math.abs(v)>0.0001)return new AmountInfo("INGRESO",Math.abs(v));}
  if(h.amount>=0){Double v=cellNumber(sh.getCell(h.amount,r),cells[h.amount]);if(v!=null&&Math.abs(v)>0.0001)return new AmountInfo(v<0?"GASTO":"INGRESO",Math.abs(v));}
  return null;
 }

 private static Double cellNumber(Cell cell,String text){
  if(cell instanceof NumberCell)try{return((NumberCell)cell).getValue();}catch(Exception ignored){}
  return parseNumber(text);
 }

 private static String findDescription(HeaderMap h,String[] cells){if(h.desc>=0&&h.desc<cells.length&&!cells[h.desc].isEmpty())return cells[h.desc];String best="";for(String s:cells)if(s.length()>best.length()&&parseNumber(s)==null&&!looksLikeDate(s))best=s;return best;}
 private static boolean looksLikeDate(String s){return s.matches("\\d{1,4}[-/]\\d{1,2}[-/]\\d{1,4}.*");}
 private static boolean looksLikeFooter(String s){String n=norm(s);return n.contains("esta informacion es la que consta en los sistemas del banco")||n.contains("supeditada a los ajustes");}

 private static Double parseNumber(String src){if(src==null)return null;String s=src.trim().replace("\u00a0","").replace(" ","");if(s.isEmpty())return null;boolean neg=s.startsWith("-")||(s.startsWith("(")&&s.endsWith(")"));s=s.replaceAll("[^0-9,.-]","");if(s.isEmpty()||s.equals("-")||s.equals(".")||s.equals(","))return null;s=s.replace("-","");int comma=s.lastIndexOf(','),dot=s.lastIndexOf('.');if(comma>=0&&dot>=0){if(comma>dot)s=s.replace(".","").replace(',','.');else s=s.replace(",","");}else if(comma>=0)s=s.replace(',','.');try{double v=Double.parseDouble(s);return neg?-v:v;}catch(Exception e){return null;}}
 private static String findCurrency(String[] cells,String sheetName){String all=(sheetName+" "+joinRaw(cells)).toUpperCase(Locale.ROOT);if(all.contains("USD")||all.contains("U$S")||all.contains("US$")||all.contains("DOLAR")||all.contains("DÓLAR"))return"USD";return"UYU";}
 private static String classify(String desc){String s=norm(desc);if(hasAny(s,"super","tienda inglesa","devoto","disco","tata","geant"))return"Supermercado";if(hasAny(s,"ancap","combustible","estacion de servicio","nafta","gasoil"))return"Combustible";if(hasAny(s,"ute","ose","antel","movistar","claro"))return"Servicios";if(hasAny(s,"farmacia","san roque","farmashop"))return"Salud";if(hasAny(s,"restaurante","restaurant","delivery","pedidos ya","pedidosya"))return"Comida";if(hasAny(s,"transferencia","transf"))return"Transferencias";if(hasAny(s,"paganza"))return"Pagos";return"Banco";}
 private static Date atNoon(Date d){Calendar c=Calendar.getInstance();c.setTime(d);c.set(Calendar.HOUR_OF_DAY,12);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTime();}
 private static String clean(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ').trim();}
 private static String joinRaw(String[] a){StringBuilder b=new StringBuilder();for(String s:a){if(s==null||s.isEmpty())continue;if(b.length()>0)b.append(" | ");b.append(s);}return b.toString();}
 private static String norm(String s){if(s==null)return"";String n=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");return n.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
 private static boolean hasAny(String s,String...w){for(String x:w)if(s.contains(x))return true;return false;}
 private static String sha256(String s){try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[]x=md.digest(s.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte v:x)b.append(String.format(Locale.US,"%02x",v&255));return b.toString();}catch(Exception e){return String.valueOf(s.hashCode());}}
}
