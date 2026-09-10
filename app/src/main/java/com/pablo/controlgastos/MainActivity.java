package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    ExpenseDb db; LinearLayout list; TextView monthTitle, income, expense, balance; Button notificationAccess; DecimalFormat money=new DecimalFormat("#,##0.00");
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
    private TextView tv(String t,int sp){ TextView v=new TextView(this); v.setText(t); v.setTextSize(sp); v.setPadding(12,10,12,10); return v; }
    private Button btn(String t){ Button b=new Button(this); b.setText(t); return b; }
    private void buildUi(){
        ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,30); root.setBackgroundColor(Color.rgb(246,247,248)); sc.addView(root);
        monthTitle=tv("",24); root.addView(monthTitle);
        LinearLayout cards=new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL); income=tv("",18); expense=tv("",18); balance=tv("",20); cards.addView(income); cards.addView(expense); cards.addView(balance); root.addView(cards);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); Button add=btn("+ Movimiento"); Button rec=btn("Recurrentes"); actions.addView(add,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(rec,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        notificationAccess=btn(""); root.addView(notificationAccess); updateNotificationAccessButton(); Button export=btn("Exportar CSV"); root.addView(export);
        root.addView(tv("Movimientos del mes",20)); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list); setContentView(sc);
        add.setOnClickListener(v->showAdd(false)); rec.setOnClickListener(v->showRecurring()); notificationAccess.setOnClickListener(v->openNotificationAccessSettings()); export.setOnClickListener(v->exportCsv());
    }

    private boolean isNotificationAccessEnabled(){
        String enabled=Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if(enabled==null) return false;
        String me=new ComponentName(this, BankNotificationListener.class).flattenToString();
        return enabled.contains(me) || enabled.contains(getPackageName());
    }
    private void openNotificationAccessSettings(){
        try{ startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS")
                .putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", new ComponentName(this, BankNotificationListener.class).flattenToString()));
        }catch(Exception e){ startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
    }
    private void updateNotificationAccessButton(){
        if(notificationAccess==null) return;
        if(isNotificationAccessEnabled()) notificationAccess.setText("🟢 Lectura de notificaciones: Activa");
        else notificationAccess.setText("🔴 Lectura de notificaciones: Desactivada — Activar");
    }
    private void showOnboarding(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36,50,36,36); root.setGravity(Gravity.CENTER_HORIZONTAL); root.setBackgroundColor(Color.rgb(246,247,248));
        TextView title=tv("Control de Gastos",28); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView info=tv("Para registrar automáticamente tus compras, Control de Gastos necesita acceso a las notificaciones del banco.\n\nLa app no entra a tu cuenta bancaria ni conoce tus claves. Solo procesa las notificaciones que llegan al teléfono.",18); info.setGravity(Gravity.CENTER); root.addView(info);
        Button allow=btn("Habilitar acceso a notificaciones"); root.addView(allow,new LinearLayout.LayoutParams(-1,-2));
        Button continueBtn=btn("Comenzar"); continueBtn.setEnabled(false); root.addView(continueBtn,new LinearLayout.LayoutParams(-1,-2));
        TextView status=tv("🔴 Acceso todavía no habilitado",16); status.setGravity(Gravity.CENTER); root.addView(status);
        allow.setOnClickListener(v->openNotificationAccessSettings());
        continueBtn.setOnClickListener(v->{ buildUi(); refresh(); });
        setContentView(root);
        root.postDelayed(new Runnable(){ public void run(){
            boolean ok=isNotificationAccessEnabled();
            status.setText(ok?"🟢 Acceso habilitado":"🔴 Acceso todavía no habilitado"); continueBtn.setEnabled(ok);
            if(root.getWindowToken()!=null) root.postDelayed(this,700);
        }},300);
    }

    private void refresh(){
        db.materializeRecurring(); list.removeAllViews(); Calendar now=Calendar.getInstance(); monthTitle.setText(new SimpleDateFormat("MMMM yyyy",new Locale("es","UY")).format(now.getTime()));
        double inUY=0,outUY=0,inUS=0,outUS=0; Cursor c=db.monthTx(); SimpleDateFormat df=new SimpleDateFormat("dd/MM HH:mm",Locale.US);
        while(c.moveToNext()){
            long id=c.getLong(0); String type=c.getString(1); double amt=c.getDouble(2); String cur=c.getString(3), cat=c.getString(4), desc=c.getString(5), src=c.getString(8); long ts=c.getLong(7);
            if("INGRESO".equals(type)){ if("USD".equals(cur)) inUS+=amt; else inUY+=amt; } else { if("USD".equals(cur)) outUS+=amt; else outUY+=amt; }
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); TextView a=tv(("INGRESO".equals(type)?"+ ":"- ")+cur+" "+money.format(amt)+" · "+desc,17); TextView d=tv(cat+" · "+df.format(new Date(ts))+" · "+src,13); row.addView(a); row.addView(d); row.setPadding(6,5,6,5); row.setOnLongClickListener(v->{ new AlertDialog.Builder(this).setTitle("Eliminar movimiento").setMessage(desc).setPositiveButton("Eliminar",(x,w)->{db.deleteTx(id);refresh();}).setNegativeButton("Cancelar",null).show(); return true; }); list.addView(row); }
        c.close();
        income.setText("Ingresos: $ "+money.format(inUY)+"   |   USD "+money.format(inUS)); expense.setText("Gastos: $ "+money.format(outUY)+"   |   USD "+money.format(outUS)); balance.setText("Saldo del mes: $ "+money.format(inUY-outUY)+"   |   USD "+money.format(inUS-outUS));
    }
    private void showAdd(boolean recurring){
        LinearLayout f=new LinearLayout(this); f.setOrientation(LinearLayout.VERTICAL); f.setPadding(30,10,30,0);
        Spinner type=new Spinner(this); type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"GASTO","INGRESO"})); f.addView(type);
        EditText amount=new EditText(this); amount.setHint("Monto"); amount.setInputType(2|8192); f.addView(amount);
        Spinner currency=new Spinner(this); currency.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"UYU","USD"})); f.addView(currency);
        EditText desc=new EditText(this); desc.setHint("Descripción"); f.addView(desc); EditText cat=new EditText(this); cat.setHint("Categoría (ej. Supermercado)"); f.addView(cat);
        EditText day=null; if(recurring){ day=new EditText(this); day.setHint("Día del mes (1-31)"); day.setInputType(2); f.addView(day); }
        final EditText dayF=day;
        new AlertDialog.Builder(this).setTitle(recurring?"Nuevo recurrente":"Nuevo movimiento").setView(f).setPositiveButton("Guardar",(x,w)->{
            try{ double a=Double.parseDouble(amount.getText().toString().replace(',','.')); String de=desc.getText().toString().trim(); if(de.isEmpty())de="Sin descripción"; String ca=cat.getText().toString().trim(); if(ca.isEmpty())ca="Sin categoría"; if(recurring){int dy=Integer.parseInt(dayF.getText().toString()); db.addRecurring(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,dy);} else db.addTx(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,"",System.currentTimeMillis(),"manual"); refresh(); }catch(Exception ex){ Toast.makeText(this,"Revisá el monto/día",Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("Cancelar",null).show();
    }
    private void showRecurring(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); ScrollView sc=new ScrollView(this); sc.addView(box); Cursor c=db.allRecurring();
        while(c.moveToNext()){ long id=c.getLong(0); String line=c.getString(1)+" · "+c.getString(3)+" "+money.format(c.getDouble(2))+" · día "+c.getInt(6)+" · "+c.getString(5); TextView v=tv(line,15); v.setOnLongClickListener(z->{new AlertDialog.Builder(this).setTitle("Eliminar recurrente").setMessage(line).setPositiveButton("Eliminar",(a,b)->{db.deleteRecurring(id);}).setNegativeButton("Cancelar",null).show();return true;}); box.addView(v);} c.close();
        Button add=btn("+ Agregar recurrente"); add.setOnClickListener(v->{ ((AlertDialog)v.getTag()).dismiss(); showAdd(true); }); box.addView(add);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Movimientos recurrentes").setView(sc).setNegativeButton("Cerrar",null).create(); add.setTag(d); d.show();
    }
    private void exportCsv(){ Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/csv"); i.putExtra(Intent.EXTRA_TITLE,"gastos_"+new SimpleDateFormat("yyyy_MM",Locale.US).format(new Date())+".csv"); startActivityForResult(i,44); }
    @Override protected void onActivityResult(int r,int c,Intent data){ super.onActivityResult(r,c,data); if(r==44&&c==RESULT_OK&&data!=null){ writeCsv(data.getData()); } }
    private void writeCsv(Uri u){ try(OutputStream os=getContentResolver().openOutputStream(u); OutputStreamWriter w=new OutputStreamWriter(os)){ w.write("fecha,tipo,moneda,monto,categoria,descripcion,origen,texto_original\n"); Cursor c=db.monthTx(); SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US); while(c.moveToNext()){ w.write(csv(df.format(new Date(c.getLong(7))))+","+csv(c.getString(1))+","+csv(c.getString(3))+","+c.getDouble(2)+","+csv(c.getString(4))+","+csv(c.getString(5))+","+csv(c.getString(8))+","+csv(c.getString(6))+"\n"); } c.close(); Toast.makeText(this,"CSV exportado",Toast.LENGTH_LONG).show(); }catch(Exception e){Toast.makeText(this,"No se pudo exportar",Toast.LENGTH_LONG).show();} }
    private String csv(String s){ if(s==null)s=""; return "\""+s.replace("\"","\"\"")+"\""; }
}
