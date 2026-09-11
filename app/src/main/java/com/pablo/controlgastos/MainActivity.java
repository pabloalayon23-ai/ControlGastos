package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private ExpenseDb db;
    private int BG,CARD,TEXT,MUTED,YELLOW;
    private final DecimalFormat money=new DecimalFormat("#,##0.00");
    private static final int REQ_EXPORT=44, REQ_IMPORT=45;
    private Uri lastIncomingUri;

    @Override public void onCreate(Bundle b){
        ThemePrefs.applyBaseTheme(this);
        super.onCreate(b);
        initPalette();
        db=new ExpenseDb(this);
        build();
        handleIncomingExcel(getIntent());
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingExcel(intent);
    }

    private void handleIncomingExcel(Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        Uri uri=null;
        if(Intent.ACTION_VIEW.equals(action)) uri=intent.getData();
        else if(Intent.ACTION_SEND.equals(action)){
            try{uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);}catch(Exception ignored){}
            if(uri==null&&intent.getClipData()!=null&&intent.getClipData().getItemCount()>0)uri=intent.getClipData().getItemAt(0).getUri();
        }
        if(uri==null)return;
        if(uri.equals(lastIncomingUri))return;
        lastIncomingUri=uri;
        final Uri incoming=uri;
        getWindow().getDecorView().postDelayed(()->previewImport(incoming),180);
    }

    private void initPalette(){boolean light=ThemePrefs.isLight(this);BG=light?Color.rgb(246,248,249):Color.rgb(11,18,24);CARD=light?Color.WHITE:Color.rgb(22,32,41);TEXT=light?Color.rgb(31,38,44):Color.rgb(242,245,247);MUTED=light?Color.rgb(95,105,112):Color.rgb(158,169,178);YELLOW=Color.rgb(255,207,52);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView tv(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setPadding(dp(10),dp(8),dp(10),dp(8));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackground(bg(CARD,14));return b;}

    private void build(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(18),dp(18),dp(18),dp(30));sc.addView(root);
        TextView title=tv("Herramientas",28,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
        root.addView(tv("Importación, exportación y movimientos recurrentes",14,MUTED));

        Button importBank=btn("⬆ Importar banco (.xls)");
        Button export=btn("⬇ Exportar CSV");
        Button rec=btn("↻ Movimientos recurrentes");
        Button add=btn("＋ Agregar movimiento manual");
        Button notif=btn(notificationEnabled()?"🟢 Lectura de notificaciones: activa":"🔴 Activar lectura de notificaciones");
        for(Button b:new Button[]{importBank,export,rec,add,notif}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.setMargins(0,dp(7),0,dp(7));root.addView(b,p);}
        TextView note=tv("También podés abrir o compartir un Excel del banco directamente con ControlGastos.",13,MUTED);root.addView(note);
        setContentView(sc);

        importBank.setOnClickListener(v->pickBankFile());
        export.setOnClickListener(v->exportCsv());
        rec.setOnClickListener(v->showRecurring());
        add.setOnClickListener(v->showAdd(false));
        notif.setOnClickListener(v->openNotificationSettings());
    }

    private LinearLayout form(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(24),0,dp(24),0);return f;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));return s;}
    private void showAdd(boolean recurring){
        LinearLayout f=form();Spinner type=spinner(new String[]{"GASTO","INGRESO"});EditText amount=new EditText(this);amount.setHint("Monto");amount.setInputType(2|8192);Spinner currency=spinner(new String[]{"UYU","USD"});EditText desc=new EditText(this);desc.setHint("Descripción");EditText cat=new EditText(this);cat.setHint("Categoría");EditText day=null;
        f.addView(type);f.addView(amount);f.addView(currency);f.addView(desc);f.addView(cat);
        if(recurring){day=new EditText(this);day.setHint("Día del mes (1-31)");day.setInputType(2);f.addView(day);}final EditText dayF=day;
        new AlertDialog.Builder(this).setTitle(recurring?"Nuevo recurrente":"Nuevo movimiento").setView(f).setPositiveButton("Guardar",(d,w)->{try{double a=Double.parseDouble(amount.getText().toString().trim().replace(',','.'));if(a<=0)throw new Exception();String de=desc.getText().toString().trim();if(de.isEmpty())de="Sin descripción";String ca=cat.getText().toString().trim();if(ca.isEmpty())ca="Sin categoría";if(recurring)db.addRecurring(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,Integer.parseInt(dayF.getText().toString()));else db.addTx(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,"",System.currentTimeMillis(),"manual");Toast.makeText(this,"Guardado",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Revisá los datos",Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancelar",null).show();
    }

    private void showRecurring(){
        LinearLayout box=form();Cursor c=db.allRecurring();boolean any=false;
        while(c.moveToNext()){any=true;long id=c.getLong(0);String line=c.getString(1)+" · "+c.getString(3)+" "+money.format(c.getDouble(2))+" · día "+c.getInt(6)+" · "+c.getString(5);TextView v=tv(line,15,TEXT);v.setBackground(bg(CARD,10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(7));box.addView(v,p);v.setOnLongClickListener(z->{new AlertDialog.Builder(this).setTitle("Eliminar recurrente").setMessage(line).setPositiveButton("Eliminar",(a,b)->db.deleteRecurring(id)).setNegativeButton("Cancelar",null).show();return true;});}c.close();
        if(!any)box.addView(tv("No hay movimientos recurrentes.",15,MUTED));Button add=btn("+ Agregar recurrente");box.addView(add);AlertDialog d=new AlertDialog.Builder(this).setTitle("Movimientos recurrentes").setView(box).setNegativeButton("Cerrar",null).create();add.setOnClickListener(v->{d.dismiss();showAdd(true);});d.show();
    }

    private void pickBankFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.ms-excel");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/vnd.ms-excel","application/octet-stream","*/*"});startActivityForResult(i,REQ_IMPORT);}
    private void exportCsv(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"gastos_"+new SimpleDateFormat("yyyy_MM",Locale.US).format(new Date())+".csv");startActivityForResult(i,REQ_EXPORT);}

    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(c!=RESULT_OK||data==null)return;if(r==REQ_EXPORT)writeCsv(data.getData());else if(r==REQ_IMPORT)previewImport(data.getData());}
    private void previewImport(Uri uri){final ProgressDialog p=ProgressDialog.show(this,"Leyendo Excel","Analizando movimientos…",true,false);new Thread(()->{try{BankXlsImporter.Result result=BankXlsImporter.parse(this,uri);int dup=0;for(BankXlsImporter.Tx tx:result.rows)if(db.hasFingerprint(tx.fingerprint))dup++;int dups=dup;runOnUiThread(()->{p.dismiss();new AlertDialog.Builder(this).setTitle("Vista previa").setMessage("Movimientos detectados: "+result.rows.size()+"\nYa existentes: "+dups+"\nNuevos: "+(result.rows.size()-dups)).setPositiveButton("Importar nuevos",(d,w)->commitImport(result)).setNegativeButton("Cancelar",null).show();});}catch(Exception e){runOnUiThread(()->{p.dismiss();new AlertDialog.Builder(this).setTitle("No pude leer el Excel").setMessage(e.getMessage()+"\n\nUsá el archivo .xls original descargado del banco.").setPositiveButton("Aceptar",null).show();});}}).start();}
    private void commitImport(BankXlsImporter.Result result){int added=0,dup=0;for(BankXlsImporter.Tx tx:result.rows){if(db.addImportedTx(tx.type,tx.amount,tx.currency,tx.category,tx.description,tx.original,tx.ts,"banco-xls",tx.fingerprint))added++;else dup++;}Toast.makeText(this,"Importados "+added+" · "+dup+" duplicados omitidos",Toast.LENGTH_LONG).show();}
    private void writeCsv(Uri u){try(OutputStream os=getContentResolver().openOutputStream(u);OutputStreamWriter w=new OutputStreamWriter(os)){w.write("fecha,tipo,moneda,monto,categoria,descripcion,origen,texto_original\n");Cursor c=db.monthTx();SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);while(c.moveToNext())w.write(csv(df.format(new Date(c.getLong(7))))+","+csv(c.getString(1))+","+csv(c.getString(3))+","+c.getDouble(2)+","+csv(c.getString(4))+","+csv(c.getString(5))+","+csv(c.getString(8))+","+csv(c.getString(6))+"\n");c.close();Toast.makeText(this,"CSV exportado",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"No se pudo exportar",Toast.LENGTH_LONG).show();}}
    private String csv(String s){if(s==null)s="";return "\""+s.replace("\"","\"\"")+"\"";}
    private boolean notificationEnabled(){String e=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return e!=null&&e.contains(getPackageName());}
    private void openNotificationSettings(){try{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}catch(Exception ignored){}}
}
