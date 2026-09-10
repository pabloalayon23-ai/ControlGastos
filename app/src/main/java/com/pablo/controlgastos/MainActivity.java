package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    ExpenseDb db;
    LinearLayout list;
    TextView monthTitle, income, expense, balance;
    Button notificationAccess;
    CategoryChartView categoryChart;
    FlowChartView flowChart;
    DecimalFormat money=new DecimalFormat("#,##0.00");
    static final int REQ_EXPORT=44, REQ_IMPORT=45;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new ExpenseDb(this);
        db.materializeRecurring();
        if(!isNotificationAccessEnabled()) showOnboarding(); else { buildUi(); refresh(); }
    }

    @Override protected void onResume(){
        super.onResume();
        if(notificationAccess!=null) updateNotificationAccessButton();
    }

    private int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private TextView tv(String t,int sp){ TextView v=new TextView(this); v.setText(t); v.setTextSize(sp); v.setTextColor(Color.rgb(38,45,50)); v.setPadding(dp(10),dp(8),dp(10),dp(8)); return v; }
    private Button btn(String t){ Button b=new Button(this); b.setText(t); b.setAllCaps(false); return b; }

    private TextView card(String title){
        TextView v=tv(title,18); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(16),dp(13),dp(16),dp(13));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1),Color.rgb(224,229,232)); v.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(8)); v.setLayoutParams(p); return v;
    }

    private void buildUi(){
        ScrollView sc=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(16),dp(16),dp(28)); root.setBackgroundColor(Color.rgb(246,248,249)); sc.addView(root);

        monthTitle=tv("",27); monthTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); monthTitle.setPadding(dp(2),dp(3),dp(2),dp(14)); root.addView(monthTitle);

        income=card(""); expense=card(""); balance=card("");
        root.addView(income); root.addView(expense); root.addView(balance);

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add=btn("＋ Movimiento"); Button rec=btn("↻ Recurrentes");
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1); half.setMargins(0,0,dp(5),0); actions.addView(add,half);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1); half2.setMargins(dp(5),0,0,0); actions.addView(rec,half2); root.addView(actions);

        LinearLayout bankActions=new LinearLayout(this); bankActions.setOrientation(LinearLayout.HORIZONTAL); bankActions.setPadding(0,dp(7),0,0);
        Button importBank=btn("⬆ Importar banco"); Button export=btn("⬇ Exportar CSV");
        LinearLayout.LayoutParams bh1=new LinearLayout.LayoutParams(0,-2,1); bh1.setMargins(0,0,dp(5),0); bankActions.addView(importBank,bh1);
        LinearLayout.LayoutParams bh2=new LinearLayout.LayoutParams(0,-2,1); bh2.setMargins(dp(5),0,0,0); bankActions.addView(export,bh2); root.addView(bankActions);

        notificationAccess=btn(""); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.setMargins(0,dp(8),0,dp(10)); root.addView(notificationAccess,np); updateNotificationAccessButton();

        TextView graphTitle=tv("Resumen visual",21); graphTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); graphTitle.setPadding(dp(2),dp(10),0,dp(3)); root.addView(graphTitle);
        flowChart=new FlowChartView(this); root.addView(flowChart,new LinearLayout.LayoutParams(-1,dp(190)));
        categoryChart=new CategoryChartView(this); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(250)); cp.setMargins(0,dp(8),0,dp(12)); root.addView(categoryChart,cp);

        TextView movements=tv("Movimientos del mes",21); movements.setTypeface(Typeface.DEFAULT,Typeface.BOLD); movements.setPadding(dp(2),dp(5),0,dp(5)); root.addView(movements);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list); setContentView(sc);

        add.setOnClickListener(v->showAdd(false)); rec.setOnClickListener(v->showRecurring()); notificationAccess.setOnClickListener(v->openNotificationAccessSettings()); export.setOnClickListener(v->exportCsv()); importBank.setOnClickListener(v->pickBankFile());
    }

    private boolean isNotificationAccessEnabled(){
        String enabled=Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if(enabled==null) return false;
        String me=new ComponentName(this, BankNotificationListener.class).flattenToString();
        return enabled.contains(me) || enabled.contains(getPackageName());
    }

    private void openNotificationAccessSettings(){
        try{ startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS").putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", new ComponentName(this, BankNotificationListener.class).flattenToString())); }
        catch(Exception e){ startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
    }

    private void updateNotificationAccessButton(){
        if(notificationAccess==null) return;
        if(isNotificationAccessEnabled()) notificationAccess.setText("🟢 Lectura de notificaciones: activa");
        else notificationAccess.setText("🔴 Lectura de notificaciones: desactivada — activar");
    }

    private void showOnboarding(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(30),dp(45),dp(30),dp(30)); root.setGravity(Gravity.CENTER_HORIZONTAL); root.setBackgroundColor(Color.rgb(246,248,249));
        TextView title=tv("Control de Gastos",28); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView info=tv("Para registrar automáticamente tus compras, Control de Gastos necesita acceso a las notificaciones del banco.\n\nLa app no entra a tu cuenta bancaria ni conoce tus claves. Solo procesa las notificaciones que llegan al teléfono.",18); info.setGravity(Gravity.CENTER); root.addView(info);
        Button allow=btn("Habilitar acceso a notificaciones"); root.addView(allow,new LinearLayout.LayoutParams(-1,-2));
        Button continueBtn=btn("Comenzar"); continueBtn.setEnabled(false); root.addView(continueBtn,new LinearLayout.LayoutParams(-1,-2));
        TextView status=tv("🔴 Acceso todavía no habilitado",16); status.setGravity(Gravity.CENTER); root.addView(status);
        allow.setOnClickListener(v->openNotificationAccessSettings()); continueBtn.setOnClickListener(v->{ buildUi(); refresh(); }); setContentView(root);
        root.postDelayed(new Runnable(){ public void run(){ boolean ok=isNotificationAccessEnabled(); status.setText(ok?"🟢 Acceso habilitado":"🔴 Acceso todavía no habilitado"); continueBtn.setEnabled(ok); if(root.getWindowToken()!=null) root.postDelayed(this,700); }},300);
    }

    private void refresh(){
        db.materializeRecurring(); list.removeAllViews(); Calendar now=Calendar.getInstance(); String title=new SimpleDateFormat("MMMM yyyy",new Locale("es","UY")).format(now.getTime()); monthTitle.setText(title.substring(0,1).toUpperCase()+title.substring(1));
        double inUY=0,outUY=0,inUS=0,outUS=0; LinkedHashMap<String,Double> cats=new LinkedHashMap<>(); Cursor c=db.monthTx(); SimpleDateFormat df=new SimpleDateFormat("dd/MM HH:mm",Locale.US);
        while(c.moveToNext()){
            long id=c.getLong(0); String type=c.getString(1); double amt=c.getDouble(2); String cur=c.getString(3), cat=c.getString(4), desc=c.getString(5), src=c.getString(8); long ts=c.getLong(7);
            if("INGRESO".equals(type)){ if("USD".equals(cur)) inUS+=amt; else inUY+=amt; }
            else { if("USD".equals(cur)) outUS+=amt; else { outUY+=amt; String key=(cat==null||cat.trim().isEmpty())?"Sin categoría":cat; cats.put(key,cats.containsKey(key)?cats.get(key)+amt:amt); } }

            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(13),dp(8),dp(13),dp(8));
            GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1),Color.rgb(229,233,235)); row.setBackground(bg);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.setMargins(0,0,0,dp(7)); row.setLayoutParams(rp);
            String symbol="INGRESO".equals(type)?"▲ ":"▼ "; TextView a=tv(symbol+cur+" "+money.format(amt)+"  ·  "+(desc==null?"Sin descripción":desc),17); a.setTypeface(Typeface.DEFAULT,Typeface.BOLD); a.setTextColor("INGRESO".equals(type)?Color.rgb(31,120,78):Color.rgb(180,62,62));
            TextView d=tv((cat==null?"Sin categoría":cat)+"  ·  "+df.format(new Date(ts))+"  ·  "+src,13); d.setTextColor(Color.rgb(100,108,112)); row.addView(a); row.addView(d);
            row.setOnLongClickListener(v->{ new AlertDialog.Builder(this).setTitle("Eliminar movimiento").setMessage(desc).setPositiveButton("Eliminar",(x,w)->{db.deleteTx(id);refresh();}).setNegativeButton("Cancelar",null).show(); return true; }); list.addView(row);
        }
        c.close();
        income.setText("INGRESOS\n$ "+money.format(inUY)+"   ·   USD "+money.format(inUS)); income.setTextColor(Color.rgb(31,120,78));
        expense.setText("GASTOS\n$ "+money.format(outUY)+"   ·   USD "+money.format(outUS)); expense.setTextColor(Color.rgb(180,62,62));
        balance.setText("SALDO DEL MES\n$ "+money.format(inUY-outUY)+"   ·   USD "+money.format(inUS-outUS)); balance.setTextColor(Color.rgb(45,77,98));
        flowChart.setValues(inUY,outUY); categoryChart.setData(cats);
    }

    private void showAdd(boolean recurring){
        LinearLayout f=new LinearLayout(this); f.setOrientation(LinearLayout.VERTICAL); f.setPadding(dp(30),dp(10),dp(30),0);
        Spinner type=new Spinner(this); type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"GASTO","INGRESO"})); f.addView(type);
        EditText amount=new EditText(this); amount.setHint("Monto"); amount.setInputType(2|8192); f.addView(amount);
        Spinner currency=new Spinner(this); currency.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"UYU","USD"})); f.addView(currency);
        EditText desc=new EditText(this); desc.setHint("Descripción"); f.addView(desc); EditText cat=new EditText(this); cat.setHint("Categoría (ej. Supermercado)"); f.addView(cat);
        EditText day=null; if(recurring){ day=new EditText(this); day.setHint("Día del mes (1-31)"); day.setInputType(2); f.addView(day); }
        final EditText dayF=day;
        new AlertDialog.Builder(this).setTitle(recurring?"Nuevo recurrente":"Nuevo movimiento").setView(f).setPositiveButton("Guardar",(x,w)->{ try{ double a=Double.parseDouble(amount.getText().toString().replace(',','.')); String de=desc.getText().toString().trim(); if(de.isEmpty())de="Sin descripción"; String ca=cat.getText().toString().trim(); if(ca.isEmpty())ca="Sin categoría"; if(recurring){int dy=Integer.parseInt(dayF.getText().toString()); db.addRecurring(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,dy);} else db.addTx(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,"",System.currentTimeMillis(),"manual"); refresh(); }catch(Exception ex){ Toast.makeText(this,"Revisá el monto/día",Toast.LENGTH_LONG).show(); } }).setNegativeButton("Cancelar",null).show();
    }

    private void showRecurring(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); ScrollView sc=new ScrollView(this); sc.addView(box); Cursor c=db.allRecurring();
        while(c.moveToNext()){ long id=c.getLong(0); String line=c.getString(1)+" · "+c.getString(3)+" "+money.format(c.getDouble(2))+" · día "+c.getInt(6)+" · "+c.getString(5); TextView v=tv(line,15); v.setOnLongClickListener(z->{new AlertDialog.Builder(this).setTitle("Eliminar recurrente").setMessage(line).setPositiveButton("Eliminar",(a,b)->{db.deleteRecurring(id); refresh();}).setNegativeButton("Cancelar",null).show();return true;}); box.addView(v);} c.close();
        Button add=btn("+ Agregar recurrente"); add.setOnClickListener(v->{ ((AlertDialog)v.getTag()).dismiss(); showAdd(true); }); box.addView(add);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Movimientos recurrentes").setView(sc).setNegativeButton("Cerrar",null).create(); add.setTag(d); d.show();
    }

    private void pickBankFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/vnd.ms-excel"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/vnd.ms-excel","application/octet-stream","*/*"}); startActivityForResult(i,REQ_IMPORT);
    }

    private void previewImport(Uri uri){
        final ProgressDialog p=ProgressDialog.show(this,"Leyendo Excel","Analizando movimientos y buscando duplicados…",true,false);
        new Thread(()->{
            try{
                BankXlsImporter.Result result=BankXlsImporter.parse(this,uri); int dup=0; double inUY=0,outUY=0,inUS=0,outUS=0; Date min=null,max=null;
                for(BankXlsImporter.Tx tx:result.rows){ if(db.hasFingerprint(tx.fingerprint))dup++; if("INGRESO".equals(tx.type)){if("USD".equals(tx.currency))inUS+=tx.amount;else inUY+=tx.amount;}else{if("USD".equals(tx.currency))outUS+=tx.amount;else outUY+=tx.amount;} Date d=new Date(tx.ts); if(min==null||d.before(min))min=d; if(max==null||d.after(max))max=d; }
                int duplicates=dup; String dates=(min==null?"":new SimpleDateFormat("dd/MM/yyyy",Locale.US).format(min)+" a "+new SimpleDateFormat("dd/MM/yyyy",Locale.US).format(max));
                String msg="Movimientos detectados: "+result.rows.size()+"\nYa existentes: "+duplicates+"\nNuevos a importar: "+(result.rows.size()-duplicates)+"\nPeríodo: "+dates+"\n\nUYU  Ingresos $ "+money.format(inUY)+" · Gastos $ "+money.format(outUY)+"\nUSD  Ingresos "+money.format(inUS)+" · Gastos "+money.format(outUS)+"\n\nLos movimientos ya importados se omiten automáticamente.";
                runOnUiThread(()->{ p.dismiss(); new AlertDialog.Builder(this).setTitle("Vista previa de importación").setMessage(msg).setPositiveButton("Importar nuevos",(d,w)->commitImport(result)).setNegativeButton("Cancelar",null).show(); });
            }catch(Exception e){ runOnUiThread(()->{p.dismiss(); new AlertDialog.Builder(this).setTitle("No pude leer el Excel").setMessage(e.getMessage()+"\n\nUsá el archivo .xls original descargado del banco.").setPositiveButton("Aceptar",null).show();}); }
        }).start();
    }

    private void commitImport(BankXlsImporter.Result result){
        int added=0,duplicates=0;
        for(BankXlsImporter.Tx tx:result.rows){ if(db.addImportedTx(tx.type,tx.amount,tx.currency,tx.category,tx.description,tx.original,tx.ts,"banco-xls",tx.fingerprint))added++; else duplicates++; }
        Toast.makeText(this,"Importados "+added+" nuevos · "+duplicates+" duplicados omitidos",Toast.LENGTH_LONG).show(); refresh();
    }

    private void exportCsv(){ Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/csv"); i.putExtra(Intent.EXTRA_TITLE,"gastos_"+new SimpleDateFormat("yyyy_MM",Locale.US).format(new Date())+".csv"); startActivityForResult(i,REQ_EXPORT); }

    @Override protected void onActivityResult(int r,int c,Intent data){ super.onActivityResult(r,c,data); if(c!=RESULT_OK||data==null)return; if(r==REQ_EXPORT) writeCsv(data.getData()); else if(r==REQ_IMPORT) previewImport(data.getData()); }

    private void writeCsv(Uri u){ try(OutputStream os=getContentResolver().openOutputStream(u); OutputStreamWriter w=new OutputStreamWriter(os)){ w.write("fecha,tipo,moneda,monto,categoria,descripcion,origen,texto_original\n"); Cursor c=db.monthTx(); SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US); while(c.moveToNext()){ w.write(csv(df.format(new Date(c.getLong(7))))+","+csv(c.getString(1))+","+csv(c.getString(3))+","+c.getDouble(2)+","+csv(c.getString(4))+","+csv(c.getString(5))+","+csv(c.getString(8))+","+csv(c.getString(6))+"\n"); } c.close(); Toast.makeText(this,"CSV exportado",Toast.LENGTH_LONG).show(); }catch(Exception e){Toast.makeText(this,"No se pudo exportar",Toast.LENGTH_LONG).show();} }
    private String csv(String s){ if(s==null)s=""; return "\""+s.replace("\"","\"\"")+"\""; }

    static class FlowChartView extends View {
        Paint p=new Paint(1); double income,expense; DecimalFormat f=new DecimalFormat("#,##0");
        FlowChartView(Context c){super(c); setBackgroundColor(Color.WHITE);}
        void setValues(double i,double e){income=i;expense=e;invalidate();}
        protected void onDraw(Canvas c){ super.onDraw(c); float w=getWidth(),h=getHeight(); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(36); p.setColor(Color.rgb(55,64,69)); c.drawText("Ingresos vs gastos (UYU)",24,42,p); double max=Math.max(1,Math.max(income,expense)); float base=h-38, top=65, bw=(w-110)/2f; drawBar(c,35,base,bw,(float)((base-top)*income/max),Color.rgb(106,184,132),"Ingresos",income); drawBar(c,75+bw,base,bw,(float)((base-top)*expense/max),Color.rgb(225,130,126),"Gastos",expense); }
        void drawBar(Canvas c,float x,float base,float bw,float bh,int color,String label,double value){ p.setColor(Color.rgb(238,241,242)); c.drawRoundRect(x,70,x+bw,base,18,18,p); p.setColor(color); c.drawRoundRect(x,base-bh,x+bw,base,18,18,p); p.setColor(Color.rgb(65,72,76)); p.setTextSize(28); p.setTypeface(Typeface.DEFAULT); c.drawText(label,x,base+30,p); p.setTextSize(25); c.drawText("$ "+f.format(value),x,base-bh-8,p); }
    }

    static class CategoryChartView extends View {
        Paint p=new Paint(1); ArrayList<Map.Entry<String,Double>> data=new ArrayList<>(); DecimalFormat f=new DecimalFormat("#,##0");
        CategoryChartView(Context c){super(c); setBackgroundColor(Color.WHITE);}
        void setData(Map<String,Double> map){ data=new ArrayList<>(map.entrySet()); Collections.sort(data,(a,b)->Double.compare(b.getValue(),a.getValue())); if(data.size()>5)data=new ArrayList<>(data.subList(0,5)); invalidate(); }
        protected void onDraw(Canvas c){ super.onDraw(c); p.setColor(Color.rgb(55,64,69)); p.setTextSize(36); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("Gastos por categoría (UYU)",24,42,p); if(data.isEmpty()){p.setTextSize(28);p.setTypeface(Typeface.DEFAULT);p.setColor(Color.GRAY);c.drawText("Todavía no hay gastos para graficar",24,95,p);return;} double max=data.get(0).getValue(); float y=72; for(Map.Entry<String,Double> e:data){ String name=e.getKey(); if(name.length()>22)name=name.substring(0,21)+"…"; p.setTextSize(27);p.setTypeface(Typeface.DEFAULT);p.setColor(Color.rgb(65,72,76));c.drawText(name,24,y,p); float x=24,barY=y+10,barW=(float)((getWidth()-50)*e.getValue()/Math.max(1,max)); p.setColor(Color.rgb(218,226,232));c.drawRoundRect(x,barY,getWidth()-24,barY+24,12,12,p); p.setColor(Color.rgb(96,145,174));c.drawRoundRect(x,barY,x+barW,barY+24,12,12,p); p.setTextSize(23);p.setColor(Color.rgb(65,72,76));c.drawText("$ "+f.format(e.getValue()),24,barY+52,p); y+=82; } }
    }
}
