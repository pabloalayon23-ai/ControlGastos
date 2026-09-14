package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.text.*;
import java.text.Normalizer;
import java.util.*;
import jxl.*;

public final class SmartImportOverlay {
    private static final WeakHashMap<Activity,Boolean> installed=new WeakHashMap<>();
    private static final String PREF="smart_import";
    private static final int REQ_PICK=941;
    private SmartImportOverlay(){}

    public static void install(Activity a){
        if(!(a instanceof MainActivity))return;
        synchronized(installed){if(installed.containsKey(a))return;installed.put(a,true);}
        a.getWindow().getDecorView().postDelayed(()->attach(a),520);
    }

    private static void attach(Activity a){
        if(a.isFinishing())return;
        Button anchor=findButton(a.getWindow().getDecorView(),"Importar archivo eBROU");
        if(anchor==null||!(anchor.getParent() instanceof LinearLayout))return;
        LinearLayout parent=(LinearLayout)anchor.getParent();
        for(int i=0;i<parent.getChildCount();i++)if(parent.getChildAt(i) instanceof Button&&((Button)parent.getChildAt(i)).getText().toString().contains("Importación inteligente"))return;
        Button b=new Button(a);b.setText("🧠 Importación inteligente");b.setAllCaps(false);
        boolean light=ThemePrefs.isLight(a);b.setTextColor(light?Color.rgb(31,38,44):Color.rgb(242,245,247));
        GradientDrawable bg=new GradientDrawable();bg.setColor(light?Color.WHITE:Color.rgb(22,32,41));bg.setCornerRadius(dp(a,14));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,58));p.setMargins(0,dp(a,7),0,dp(a,7));
        int index=parent.indexOfChild(anchor);parent.addView(b,index+1,p);b.setOnClickListener(v->showConfig(a));
    }

    private static final class Config{
        int skipRows=0,emptyRows=10,emptyCols=10,maxRows=10000,maxCols=200;
    }

    private static Config loadConfig(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);Config x=new Config();
        x.skipRows=p.getInt("skip_rows",0);x.emptyRows=p.getInt("empty_rows",10);x.emptyCols=p.getInt("empty_cols",10);x.maxRows=p.getInt("max_rows",10000);x.maxCols=p.getInt("max_cols",200);return x;
    }
    private static void saveConfig(Context c,Config x){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt("skip_rows",x.skipRows).putInt("empty_rows",x.emptyRows).putInt("empty_cols",x.emptyCols).putInt("max_rows",x.maxRows).putInt("max_cols",x.maxCols).apply();}

    private static EditText num(Context c,String label,int value){EditText e=new EditText(c);e.setHint(label);e.setText(String.valueOf(value));e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);return e;}
    private static void showConfig(Activity a){
        Config cfg=loadConfig(a);LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(a,24),0,dp(a,24),0);
        TextView info=new TextView(a);info.setText("ControlGastos recorrerá la hoja hasta encontrar el límite configurado. Después intentará reconocer automáticamente fecha, descripción, importe, débito/crédito, moneda y otras columnas útiles.");info.setTextSize(14);info.setPadding(0,0,0,dp(a,8));box.addView(info);
        EditText skip=num(a,"Filas iniciales a saltear",cfg.skipRows),er=num(a,"Filas vacías consecutivas para detener",cfg.emptyRows),ec=num(a,"Columnas vacías consecutivas para detener",cfg.emptyCols),mr=num(a,"Máximo de filas a analizar",cfg.maxRows),mc=num(a,"Máximo de columnas a analizar",cfg.maxCols);
        box.addView(label(a,"Filas iniciales a saltear"));box.addView(skip);box.addView(label(a,"Filas vacías consecutivas para detener"));box.addView(er);box.addView(label(a,"Columnas vacías consecutivas para detener"));box.addView(ec);box.addView(label(a,"Máximo de filas a analizar"));box.addView(mr);box.addView(label(a,"Máximo de columnas a analizar"));box.addView(mc);
        ScrollView sc=new ScrollView(a);sc.addView(box);
        AlertDialog d=new AlertDialog.Builder(a).setTitle("🧠 Importación inteligente").setView(sc).setNeutralButton("Restaurar recomendados",null).setPositiveButton("Seleccionar archivo",null).setNegativeButton("Cancelar",null).create();
        d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{skip.setText("0");er.setText("10");ec.setText("10");mr.setText("10000");mc.setText("200");});d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{Config c=new Config();c.skipRows=readInt(skip,0,100000);c.emptyRows=readInt(er,1,1000);c.emptyCols=readInt(ec,1,1000);c.maxRows=readInt(mr,1,200000);c.maxCols=readInt(mc,1,2000);saveConfig(a,c);d.dismiss();pickFile(a);}catch(Exception ex){Toast.makeText(a,"Revisá los valores de búsqueda",Toast.LENGTH_LONG).show();}});});d.show();
    }
    private static TextView label(Context c,String s){TextView t=new TextView(c);t.setText(s);t.setTextSize(13);t.setPadding(0,dp(c,8),0,0);return t;}
    private static int readInt(EditText e,int min,int max)throws Exception{int n=Integer.parseInt(e.getText().toString().trim());if(n<min||n>max)throw new Exception();return n;}

    private static void pickFile(Activity a){
        PickerFragment f=(PickerFragment)a.getFragmentManager().findFragmentByTag("smart-import-picker");
        if(f==null){f=new PickerFragment();a.getFragmentManager().beginTransaction().add(f,"smart-import-picker").commit();a.getFragmentManager().executePendingTransactions();}
        f.pick();
    }

    public static class PickerFragment extends Fragment{
        public void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.ms-excel");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/vnd.ms-excel","application/octet-stream","*/*"});startActivityForResult(i,REQ_PICK);}
        @Override public void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_PICK&&resultCode==Activity.RESULT_OK&&data!=null&&data.getData()!=null&&getActivity()!=null)preview(getActivity(),data.getData(),loadConfig(getActivity()));}
    }

    private static final class Mapping{
        int header=-1,date=-1,desc=-1,debit=-1,credit=-1,amount=-1,currency=-1,type=-1,rowEnd=-1,colEnd=-1,score=0;
        String sheet="";
        String summary(){return "Hoja: "+sheet+"\nRango analizado: filas "+(header+2)+"–"+(rowEnd+1)+", columnas 1–"+(colEnd+1)+"\nFecha: "+col(date)+"\nDescripción: "+col(desc)+"\nDébito: "+col(debit)+"\nCrédito: "+col(credit)+"\nImporte: "+col(amount)+"\nMoneda: "+col(currency)+"\nTipo: "+col(type)+"\nConfianza: "+Math.min(100,score)+"%";}
        private String col(int x){return x<0?"no detectada":columnName(x);}
    }

    private static final class Parsed{final BankXlsImporter.Result result=new BankXlsImporter.Result();final ArrayList<Mapping> maps=new ArrayList<>();int sheetsUsed=0;}

    private static void preview(Activity a,Uri uri,Config cfg){
        ProgressDialog p=ProgressDialog.show(a,"Importación inteligente","Buscando la estructura del archivo…",true,false);
        new Thread(()->{try{Parsed parsed=parse(a,uri,cfg);ExpenseDb db=new ExpenseDb(a);int existing=0,linkable=0;for(BankXlsImporter.Tx tx:parsed.result.rows){if(db.hasFingerprint(tx.fingerprint))existing++;else if(db.wouldLinkImportedToNotification(tx.type,tx.amount,tx.currency,tx.description,tx.ts))linkable++;}int ex=existing,li=linkable,ne=parsed.result.rows.size()-existing-linkable;StringBuilder map=new StringBuilder();for(Mapping m:parsed.maps){if(map.length()>0)map.append("\n\n");map.append(m.summary());}String msg="Movimientos detectados: "+parsed.result.rows.size()+"\nYa importados: "+ex+"\nPara conciliar con notificación: "+li+"\nNuevos: "+ne+"\n\n"+map; a.runOnUiThread(()->{p.dismiss();new AlertDialog.Builder(a).setTitle("Vista previa · Importación inteligente").setMessage(msg).setPositiveButton("Importar",(d,w)->commit(a,parsed.result)).setNegativeButton("Cancelar",null).show();});}catch(Exception e){a.runOnUiThread(()->{p.dismiss();new AlertDialog.Builder(a).setTitle("No pude interpretar el archivo").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show();});}}).start();
    }

    private static void commit(Activity a,BankXlsImporter.Result r){ExpenseDb db=new ExpenseDb(a);int added=0,dup=0;for(BankXlsImporter.Tx tx:r.rows){if(db.addImportedTx(tx.type,tx.amount,tx.currency,tx.category,tx.description,tx.original,tx.ts,"banco-inteligente",tx.fingerprint))added++;else dup++;}Toast.makeText(a,"Importación inteligente: "+added+" nuevos · "+dup+" existentes o conciliados",Toast.LENGTH_LONG).show();}

    private static Parsed parse(Context c,Uri uri,Config cfg)throws Exception{
        InputStream in=c.getContentResolver().openInputStream(uri);if(in==null)throw new IOException("No se pudo abrir el archivo");Workbook wb=null;
        try{wb=Workbook.getWorkbook(in);Parsed out=new Parsed();Map<String,Integer> occ=new HashMap<>();for(Sheet sh:wb.getSheets()){Mapping m=detect(sh,cfg);if(m==null)continue;int before=out.result.rows.size();parseSheet(c,sh,m,out.result,occ);if(out.result.rows.size()>before){out.maps.add(m);out.sheetsUsed++;}}if(out.result.rows.isEmpty())throw new IOException("No encontré una tabla de movimientos reconocible. Probá ajustar ‘Filas iniciales a saltear’ o los límites de búsqueda.");return out;}finally{if(wb!=null)wb.close();try{in.close();}catch(Exception ignored){}}
    }

    private static Mapping detect(Sheet sh,Config cfg){
        int physicalRows=Math.min(sh.getRows(),cfg.maxRows),physicalCols=Math.min(sh.getColumns(),cfg.maxCols);if(physicalRows<=cfg.skipRows||physicalCols<1)return null;
        int rowEnd=findRowEnd(sh,cfg.skipRows,physicalRows,physicalCols,cfg.emptyRows);if(rowEnd<cfg.skipRows)return null;
        int colEnd=findColEnd(sh,cfg.skipRows,rowEnd,physicalCols,cfg.emptyCols);if(colEnd<0)return null;
        Mapping best=null;int headerLimit=Math.min(rowEnd,cfg.skipRows+80);
        for(int r=cfg.skipRows;r<=headerLimit;r++){Mapping m=mapHeader(sh,r,rowEnd,colEnd);if(m==null)continue;if(best==null||m.score>best.score)best=m;}
        if(best==null){best=inferWithoutHeader(sh,cfg.skipRows,rowEnd,colEnd);}
        if(best==null||best.date<0||best.desc<0||(best.amount<0&&best.debit<0&&best.credit<0))return null;best.sheet=sh.getName();best.rowEnd=rowEnd;best.colEnd=colEnd;return best;
    }

    private static int findRowEnd(Sheet sh,int start,int maxRows,int maxCols,int stop){int empty=0,last=start-1;for(int r=start;r<maxRows;r++){boolean any=false;for(int c=0;c<maxCols;c++)if(!clean(sh.getCell(c,r).getContents()).isEmpty()){any=true;break;}if(any){empty=0;last=r;}else if(++empty>=stop)break;}return last;}
    private static int findColEnd(Sheet sh,int startRow,int rowEnd,int maxCols,int stop){int empty=0,last=-1;for(int c=0;c<maxCols;c++){boolean any=false;for(int r=startRow;r<=rowEnd;r++)if(!clean(sh.getCell(c,r).getContents()).isEmpty()){any=true;break;}if(any){empty=0;last=c;}else if(++empty>=stop)break;}return last;}

    private static Mapping mapHeader(Sheet sh,int header,int rowEnd,int colEnd){Mapping m=new Mapping();m.header=header;int keywordScore=0;for(int c=0;c<=colEnd;c++){String s=norm(sh.getCell(c,header).getContents());if(s.isEmpty())continue;
        if(m.date<0&&has(s,"fecha","date","data","f op","f. op","fecha operacion","fecha movimiento")){m.date=c;keywordScore+=22;continue;}
        if(m.desc<0&&has(s,"descripcion","descripción","concepto","detalle","movimiento","leyenda","referencia","comercio","establecimiento","historico","histórico","descricao","descrição")){m.desc=c;keywordScore+=18;continue;}
        if(m.debit<0&&has(s,"debito","débito","debe","egreso","cargo","retiro","saida","saída")){m.debit=c;keywordScore+=16;continue;}
        if(m.credit<0&&has(s,"credito","crédito","haber","ingreso","deposito","depósito","entrada","abono")){m.credit=c;keywordScore+=16;continue;}
        if(m.currency<0&&has(s,"moneda","currency","moeda","divisa")){m.currency=c;keywordScore+=8;continue;}
        if(m.type<0&&has(s,"tipo","naturaleza","type")){m.type=c;keywordScore+=5;continue;}
        if(m.amount<0&&has(s,"importe","monto","amount","valor")&&!has(s,"saldo","balance")){m.amount=c;keywordScore+=14;}
    }
        int sampleStart=header+1,sampleEnd=Math.min(rowEnd,header+30);if(sampleStart>sampleEnd)return null;
        if(m.date<0)m.date=bestDateColumn(sh,sampleStart,sampleEnd,colEnd);
        if(m.desc<0)m.desc=bestTextColumn(sh,sampleStart,sampleEnd,colEnd,m.date,m.debit,m.credit,m.amount);
        if(m.amount<0&&m.debit<0&&m.credit<0)m.amount=bestAmountColumn(sh,sampleStart,sampleEnd,colEnd,m.date,m.desc);
        int dataScore=0;if(m.date>=0)dataScore+=20;if(m.desc>=0)dataScore+=15;if(m.debit>=0||m.credit>=0)dataScore+=25;else if(m.amount>=0)dataScore+=18;
        m.score=Math.min(99,keywordScore+dataScore);return m;
    }

    private static Mapping inferWithoutHeader(Sheet sh,int start,int rowEnd,int colEnd){Mapping m=new Mapping();m.header=start-1;int sampleEnd=Math.min(rowEnd,start+40);m.date=bestDateColumn(sh,start,sampleEnd,colEnd);m.desc=bestTextColumn(sh,start,sampleEnd,colEnd,m.date,-1,-1,-1);m.amount=bestAmountColumn(sh,start,sampleEnd,colEnd,m.date,m.desc);if(m.date<0||m.desc<0||m.amount<0)return null;m.score=55;return m;}

    private static int bestDateColumn(Sheet sh,int rs,int re,int ce){int best=-1,score=0;for(int c=0;c<=ce;c++){int ok=0,seen=0;for(int r=rs;r<=re;r++){String s=clean(sh.getCell(c,r).getContents());if(s.isEmpty())continue;seen++;if(dateFromCell(sh.getCell(c,r),s)!=null)ok++;}if(seen>=2&&ok*100/seen>score){score=ok*100/seen;best=c;}}return score>=45?best:-1;}
    private static int bestTextColumn(Sheet sh,int rs,int re,int ce,int...exclude){HashSet<Integer>x=new HashSet<>();for(int i:exclude)if(i>=0)x.add(i);int best=-1,bestScore=0;for(int c=0;c<=ce;c++){if(x.contains(c))continue;int text=0,seen=0,totalLen=0;for(int r=rs;r<=re;r++){String s=clean(sh.getCell(c,r).getContents());if(s.isEmpty())continue;seen++;if(parseNumber(s)==null&&dateFromCell(sh.getCell(c,r),s)==null){text++;totalLen+=s.length();}}int score=text*10+Math.min(100,totalLen);if(seen>=2&&text*100/seen>=45&&score>bestScore){bestScore=score;best=c;}}return best;}
    private static int bestAmountColumn(Sheet sh,int rs,int re,int ce,int...exclude){HashSet<Integer>x=new HashSet<>();for(int i:exclude)if(i>=0)x.add(i);int best=-1,bestScore=0;for(int c=0;c<=ce;c++){if(x.contains(c))continue;String head=rs>0?norm(sh.getCell(c,rs-1).getContents()):"";if(has(head,"saldo","balance"))continue;int nums=0,seen=0;for(int r=rs;r<=re;r++){String s=clean(sh.getCell(c,r).getContents());if(s.isEmpty())continue;seen++;Double n=cellNumber(sh.getCell(c,r),s);if(n!=null&&Math.abs(n)>0.0001)nums++;}int score=seen==0?0:nums*100/seen;if(nums>=2&&score>bestScore){bestScore=score;best=c;}}return bestScore>=45?best:-1;}

    private static void parseSheet(Context c,Sheet sh,Mapping m,BankXlsImporter.Result out,Map<String,Integer> occ){int start=Math.max(0,m.header+1);for(int r=start;r<=m.rowEnd;r++){Date date=dateFromCell(sh.getCell(m.date,r),clean(sh.getCell(m.date,r).getContents()));if(date==null){out.ignored++;continue;}AmountInfo ai=amountAt(sh,r,m);if(ai==null||ai.amount<=0.0001){out.ignored++;continue;}String desc=clean(sh.getCell(m.desc,r).getContents());if(desc.isEmpty())desc="Movimiento bancario";String[] cells=new String[m.colEnd+1];for(int col=0;col<=m.colEnd;col++)cells[col]=clean(sh.getCell(col,r).getContents());String original=join(cells);String currency=currencyAt(sh,r,m,original);String category=DetectionRules.categoryFor(c,original+"\n"+desc,"Banco");String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(date);String canonical=day+"|"+ai.type+"|"+currency+"|"+String.format(Locale.US,"%.2f",ai.amount)+"|"+norm(desc)+"|"+norm(original);int n=occ.containsKey(canonical)?occ.get(canonical)+1:1;occ.put(canonical,n);BankXlsImporter.Tx tx=new BankXlsImporter.Tx();tx.type=ai.type;tx.amount=ai.amount;tx.currency=currency;tx.category=category;tx.description=desc;tx.original=original;tx.ts=noon(date).getTime();tx.fingerprint=sha256("smart-xls|"+canonical+"|occ="+n);out.rows.add(tx);}}

    private static final class AmountInfo{String type;double amount;AmountInfo(String t,double a){type=t;amount=a;}}
    private static AmountInfo amountAt(Sheet sh,int r,Mapping m){Double d=m.debit>=0?cellNumber(sh.getCell(m.debit,r),clean(sh.getCell(m.debit,r).getContents())):null;Double c=m.credit>=0?cellNumber(sh.getCell(m.credit,r),clean(sh.getCell(m.credit,r).getContents())):null;if(d!=null&&Math.abs(d)>0.0001)return new AmountInfo("GASTO",Math.abs(d));if(c!=null&&Math.abs(c)>0.0001)return new AmountInfo("INGRESO",Math.abs(c));if(m.amount<0)return null;Double v=cellNumber(sh.getCell(m.amount,r),clean(sh.getCell(m.amount,r).getContents()));if(v==null||Math.abs(v)<=0.0001)return null;if(v<0)return new AmountInfo("GASTO",Math.abs(v));String type=m.type>=0?norm(sh.getCell(m.type,r).getContents()):"";if(has(type,"credito","crédito","haber","ingreso","deposito","depósito","abono","entrada"))return new AmountInfo("INGRESO",v);if(has(type,"debito","débito","debe","egreso","cargo","retiro","salida","saida"))return new AmountInfo("GASTO",v);String row="";for(int x=0;x<=m.colEnd;x++)row+=" "+norm(sh.getCell(x,r).getContents());if(has(row,"credito","crédito","haber","ingreso","deposito","depósito","abono"))return new AmountInfo("INGRESO",v);return new AmountInfo("GASTO",v);}

    private static String currencyAt(Sheet sh,int r,Mapping m,String original){String s=m.currency>=0?clean(sh.getCell(m.currency,r).getContents()):"";String all=(s+" "+original+" "+sh.getName()).toUpperCase(Locale.ROOT);if(all.contains("USD")||all.contains("U$S")||all.contains("US$")||all.contains("DOLAR")||all.contains("DÓLAR"))return"USD";return"UYU";}

    private static Date dateFromCell(Cell cell,String text){if(cell instanceof DateCell)try{return((DateCell)cell).getDate();}catch(Exception ignored){}if(cell instanceof NumberCell)try{double serial=((NumberCell)cell).getValue();if(serial>20000&&serial<80000)return excelDate(serial);}catch(Exception ignored){}String s=clean(text);String[] p={"dd/MM/yyyy","d/M/yyyy","dd/MM/yy","d/M/yy","dd-MM-yyyy","d-M-yyyy","yyyy-MM-dd","MM/dd/yyyy","M/d/yyyy","dd.MM.yyyy","d.M.yyyy","dd/MM/yyyy HH:mm","d/M/yyyy HH:mm"};for(String f:p)try{SimpleDateFormat x=new SimpleDateFormat(f,Locale.US);x.setLenient(false);Date d=x.parse(s);if(d!=null)return d;}catch(Exception ignored){}Double serial=parseNumber(s);if(serial!=null&&serial>20000&&serial<80000)return excelDate(serial);return null;}
    private static Date excelDate(double serial){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"),Locale.US);c.clear();c.set(1899,Calendar.DECEMBER,30,0,0,0);return new Date(c.getTimeInMillis()+Math.round(serial*86400000d));}
    private static Date noon(Date d){Calendar c=Calendar.getInstance();c.setTime(d);c.set(Calendar.HOUR_OF_DAY,12);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTime();}
    private static Double cellNumber(Cell cell,String text){if(cell instanceof NumberCell)try{return((NumberCell)cell).getValue();}catch(Exception ignored){}return parseNumber(text);}
    private static Double parseNumber(String src){if(src==null)return null;String s=src.trim().replace("\u00a0","").replace(" ","");if(s.isEmpty())return null;boolean neg=s.startsWith("-")||(s.startsWith("(")&&s.endsWith(")"));s=s.replaceAll("[^0-9,.-]","");if(s.isEmpty()||s.equals("-")||s.equals(".")||s.equals(","))return null;s=s.replace("-","");int comma=s.lastIndexOf(','),dot=s.lastIndexOf('.');if(comma>=0&&dot>=0){if(comma>dot)s=s.replace(".","").replace(',','.');else s=s.replace(",","");}else if(comma>=0)s=s.replace(',','.');try{double v=Double.parseDouble(s);return neg?-v:v;}catch(Exception e){return null;}}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String norm(String s){if(s==null)return"";return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim();}
    private static boolean has(String s,String...parts){String n=norm(s);for(String p:parts)if(n.contains(norm(p)))return true;return false;}
    private static String join(String[] cells){StringBuilder b=new StringBuilder();for(String s:cells)if(s!=null&&!s.isEmpty()){if(b.length()>0)b.append(" | ");b.append(s);}return b.toString();}
    private static String sha256(String s){try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=md.digest(s.getBytes("UTF-8"));StringBuilder x=new StringBuilder();for(byte z:b)x.append(String.format(Locale.US,"%02x",z&255));return x.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
    private static String columnName(int n){StringBuilder s=new StringBuilder();int x=n+1;while(x>0){int r=(x-1)%26;s.insert(0,(char)('A'+r));x=(x-1)/26;}return s.toString();}
    private static Button findButton(View v,String contains){if(v instanceof Button&&((Button)v).getText()!=null&&((Button)v).getText().toString().contains(contains))return(Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),contains);if(b!=null)return b;}}return null;}
    private static int dp(Context c,int n){return(int)(n*c.getResources().getDisplayMetrics().density+.5f);}
}
