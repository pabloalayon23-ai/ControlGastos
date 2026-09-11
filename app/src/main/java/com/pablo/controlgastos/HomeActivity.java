package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public class HomeActivity extends Activity {
    private static final int REQ_DEVICE_CREDENTIAL=7001;
    private ExpenseDb db;
    private LinearLayout list;
    private TextView gastos, ingresos, saldo, month;
    private boolean unlocked=false, authInProgress=false;
    private final DecimalFormat money=new DecimalFormat("#,##0.00");
    private int BG,CARD,TEXT,MUTED,YELLOW,NAV;

    @Override public void onCreate(Bundle b){
        ThemePrefs.applyBaseTheme(this);
        super.onCreate(b);
        initPalette();
        db=new ExpenseDb(this);
        migrateScreenshotSetting();
        applyScreenSecurity();
        build();
    }
    private void initPalette(){boolean light=ThemePrefs.isLight(this);BG=light?Color.rgb(246,248,249):Color.rgb(11,18,24);CARD=light?Color.WHITE:Color.rgb(22,32,41);TEXT=light?Color.rgb(31,38,44):Color.rgb(242,245,247);MUTED=light?Color.rgb(95,105,112):Color.rgb(158,169,178);YELLOW=light?Color.rgb(170,126,0):Color.rgb(255,207,52);NAV=light?Color.rgb(232,237,240):Color.rgb(17,25,32);}
    @Override protected void onResume(){ super.onResume(); applyScreenSecurity(); refresh(); getWindow().getDecorView().postDelayed(this::maybeAuthenticate,180); }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView tv(String s,int size,int color){ TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(8),dp(6),dp(8),dp(6));return v; }
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackground(bg(CARD,14));return b;}

    private void build(){
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((v,insets)->{
            int bottom=insets.getSystemWindowInsetBottom();
            v.setPadding(0,0,0,bottom);
            return insets;
        });
        outer.requestApplyInsets();

        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(16),dp(14),dp(16),dp(4));
        LinearLayout titleRow=new LinearLayout(this); titleRow.setOrientation(LinearLayout.HORIZONTAL); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("ControlGastos",28,TEXT); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button help=button("? Ayuda"); help.setTextSize(13); help.setOnClickListener(v->showHelp()); titleRow.addView(help,new LinearLayout.LayoutParams(dp(94),dp(44)));
        header.addView(titleRow);
        month=tv("",15,MUTED); header.addView(month); outer.addView(header);

        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.HORIZONTAL);summary.setPadding(dp(12),dp(5),dp(12),dp(10));
        gastos=summaryCard("Gastos",Color.rgb(255,111,111)); ingresos=summaryCard("Ingresos",Color.rgb(87,220,143)); saldo=summaryCard("Saldo",Color.rgb(115,175,255));
        summary.addView(gastos,new LinearLayout.LayoutParams(0,dp(94),1)); summary.addView(ingresos,new LinearLayout.LayoutParams(0,dp(94),1)); summary.addView(saldo,new LinearLayout.LayoutParams(0,dp(94),1)); outer.addView(summary);

        if(!notificationEnabled()){
            Button warn=button("⚠ Activar lectura de notificaciones");warn.setOnClickListener(v->openNotificationSettings());LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.setMargins(dp(14),0,dp(14),dp(8));outer.addView(warn,wp);
        }

        TextView mov=tv("Movimientos",20,TEXT);mov.setTypeface(Typeface.DEFAULT,Typeface.BOLD);mov.setPadding(dp(18),dp(5),0,dp(4));outer.addView(mov);
        ScrollView sc=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(12),0,dp(12),dp(10));sc.addView(list);outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(dp(5),dp(4),dp(5),dp(8));nav.setBackgroundColor(NAV);
        Button inicio=navBtn("⌂\nInicio",true), graficos=navBtn("▥\nGráficos",false), add=navBtn("＋\nAgregar",true), importar=navBtn("⇧\nImportar",false), ajustes=navBtn("⚙\nAjustes",false);
        nav.addView(inicio,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(graficos,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(add,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(importar,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(ajustes,new LinearLayout.LayoutParams(0,dp(66),1));outer.addView(nav);
        graficos.setOnClickListener(v->startActivity(new Intent(this,AnalyticsActivity.class))); add.setOnClickListener(v->showAdd()); importar.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class))); ajustes.setOnClickListener(v->showSecuritySettings());
        setContentView(outer);
    }

    private TextView summaryCard(String label,int accent){TextView v=tv(label+"\n$ 0",16,TEXT);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable g=bg(CARD,14);g.setStroke(dp(1),Color.argb(90,Color.red(accent),Color.green(accent),Color.blue(accent)));v.setBackground(g);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(94),1);p.setMargins(dp(3),0,dp(3),0);v.setLayoutParams(p);return v;}
    private Button navBtn(String s,boolean hi){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(hi?YELLOW:MUTED);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(0,0,0,0);return b;}

    private void refresh(){
        db.materializeRecurring(); list.removeAllViews(); Calendar now=Calendar.getInstance();String mt=new SimpleDateFormat("MMMM yyyy",new Locale("es","UY")).format(now.getTime());month.setText(mt.substring(0,1).toUpperCase()+mt.substring(1));
        double in=0,out=0; Cursor c=db.monthTx(); ArrayList<Object[]> rows=new ArrayList<>(); while(c.moveToNext()){String type=c.getString(1),cur=c.getString(3);double a=c.getDouble(2);if("UYU".equals(cur)){if("INGRESO".equals(type))in+=a;else out+=a;}rows.add(new Object[]{c.getLong(0),type,a,cur,c.getString(4),c.getString(5),c.getLong(7),c.getString(8)});}c.close();
        gastos.setText("Gastos\n$ "+money.format(out));ingresos.setText("Ingresos\n$ "+money.format(in));saldo.setText("Saldo\n$ "+money.format(in-out));
        SimpleDateFormat dayKey=new SimpleDateFormat("yyyyMMdd",Locale.US), dayLabel=new SimpleDateFormat("EEE, d 'de' MMM",new Locale("es","UY")),time=new SimpleDateFormat("HH:mm",Locale.US);String last="";
        for(Object[] r:rows){long id=(Long)r[0],ts=(Long)r[6];String dk=dayKey.format(new Date(ts));if(!dk.equals(last)){TextView h=tv(dayLabel.format(new Date(ts)),15,MUTED);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setPadding(dp(8),dp(12),0,dp(4));list.addView(h);last=dk;}
            String type=(String)r[1],cur=(String)r[3],cat=(String)r[4],desc=(String)r[5],source=(String)r[7];double a=(Double)r[2];
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(9),dp(10),dp(9));row.setBackground(bg(CARD,12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(6));row.setLayoutParams(rp);
            TextView icon=tv(iconFor(cat,desc),23,TEXT);icon.setGravity(Gravity.CENTER);icon.setBackground(bg(colorFor(cat,desc),24));row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);TextView d=tv(desc==null?"Sin descripción":desc,17,TEXT);d.setTypeface(Typeface.DEFAULT,Typeface.BOLD);TextView sub=tv((cat==null?"Sin categoría":cat)+" · "+time.format(new Date(ts)),13,MUTED);mid.addView(d);mid.addView(sub);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1);mp.setMargins(dp(8),0,dp(5),0);row.addView(mid,mp);
            TextView amt=tv(("GASTO".equals(type)?"-":"+")+("USD".equals(cur)?"USD ":"$ ")+money.format(a),16,"GASTO".equals(type)?Color.rgb(220,75,75):Color.rgb(45,160,90));amt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);amt.setGravity(Gravity.RIGHT);row.addView(amt);
            final String fdesc=desc; final long fid=id, fts=ts; final double fa=a; final String ftype=type,fcur=cur,fcat=cat,fsrc=source;
            row.setOnClickListener(v->showEdit(fid,ftype,fa,fcur,fcat,fdesc,fts,fsrc));
            row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Eliminar movimiento").setMessage((fdesc==null?"Movimiento":fdesc)+"\n\nEsta acción no se puede deshacer.").setPositiveButton("Eliminar",(x,w)->{db.deleteTx(fid);refresh();}).setNegativeButton("Cancelar",null).show();return true;});list.addView(row);
        }
        if(rows.isEmpty()){TextView empty=tv("Todavía no hay movimientos este mes.\nUsá + Agregar, importá el Excel del banco o activá las notificaciones.",15,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(40),dp(20),dp(40));list.addView(empty);}
    }

    private String iconFor(String cat,String desc){String s=((cat==null?"":cat)+" "+(desc==null?"":desc)).toLowerCase(Locale.ROOT);if(s.contains("super")||s.contains("disco")||s.contains("devoto")||s.contains("geant"))return "🛒";if(s.contains("combust")||s.contains("ancap"))return "⛽";if(s.contains("salud")||s.contains("farm"))return "❤";if(s.contains("comida")||s.contains("rest"))return "🍴";if(s.contains("transporte")||s.contains("peaje"))return "🚗";if(s.contains("servicio")||s.contains("ute")||s.contains("antel"))return "⌂";if(s.contains("ingreso"))return "$";return "●";}
    private int colorFor(String cat,String desc){String i=iconFor(cat,desc);if("🛒".equals(i))return Color.rgb(232,170,36);if("⛽".equals(i))return Color.rgb(72,128,238);if("❤".equals(i))return Color.rgb(78,135,244);if("🍴".equals(i))return Color.rgb(58,178,98);if("🚗".equals(i))return Color.rgb(74,180,190);return Color.rgb(123,91,190);}

    private void showAdd(){
        LinearLayout f=form(); Spinner type=typeSpinner("GASTO"); EditText amount=amountEdit(0); Spinner currency=currencySpinner("UYU");EditText desc=textEdit("Descripción","");EditText cat=textEdit("Categoría","");
        f.addView(type);f.addView(amount);f.addView(currency);f.addView(desc);f.addView(cat);
        new AlertDialog.Builder(this).setTitle("Nuevo movimiento").setView(f).setPositiveButton("Guardar",(d,w)->{try{double a=parseAmount(amount);String de=desc.getText().toString().trim();if(de.isEmpty())de="Sin descripción";String ca=cat.getText().toString().trim();if(ca.isEmpty())ca="Sin categoría";db.addTx(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,"",System.currentTimeMillis(),"manual");refresh();}catch(Exception e){Toast.makeText(this,"Revisá el monto",Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancelar",null).show();
    }

    private void showEdit(long id,String currentType,double currentAmount,String currentCurrency,String currentCategory,String currentDesc,long currentTs,String source){
        LinearLayout f=form(); Spinner type=typeSpinner(currentType); EditText amount=amountEdit(currentAmount); Spinner currency=currencySpinner(currentCurrency); EditText desc=textEdit("Descripción",currentDesc); EditText cat=textEdit("Categoría",currentCategory);
        final Calendar chosen=Calendar.getInstance();chosen.setTimeInMillis(currentTs);Button date=button("Fecha: "+new SimpleDateFormat("dd/MM/yyyy",Locale.US).format(chosen.getTime()));
        date.setOnClickListener(v->new DatePickerDialog(this,(view,y,m,d)->{chosen.set(Calendar.YEAR,y);chosen.set(Calendar.MONTH,m);chosen.set(Calendar.DAY_OF_MONTH,d);date.setText("Fecha: "+new SimpleDateFormat("dd/MM/yyyy",Locale.US).format(chosen.getTime()));},chosen.get(Calendar.YEAR),chosen.get(Calendar.MONTH),chosen.get(Calendar.DAY_OF_MONTH)).show());
        TextView src=tv("Origen: "+sourceLabel(source),13,MUTED);f.addView(type);f.addView(amount);f.addView(currency);f.addView(desc);f.addView(cat);f.addView(date);f.addView(src);
        new AlertDialog.Builder(this).setTitle("Editar movimiento").setView(f).setPositiveButton("Guardar cambios",(d,w)->{try{double a=parseAmount(amount);String de=desc.getText().toString().trim();if(de.isEmpty())de="Sin descripción";String ca=cat.getText().toString().trim();if(ca.isEmpty())ca="Sin categoría";db.updateTx(id,type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,chosen.getTimeInMillis());refresh();}catch(Exception e){Toast.makeText(this,"Revisá el monto",Toast.LENGTH_LONG).show();}}).setNeutralButton("Eliminar",(d,w)->confirmDelete(id,currentDesc)).setNegativeButton("Cancelar",null).show();
    }

    private void confirmDelete(long id,String desc){new AlertDialog.Builder(this).setTitle("Eliminar movimiento").setMessage((desc==null?"Movimiento":desc)+"\n\nEsta acción no se puede deshacer.").setPositiveButton("Eliminar",(d,w)->{db.deleteTx(id);refresh();}).setNegativeButton("Cancelar",null).show();}
    private LinearLayout form(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(24),0,dp(24),0);return f;}
    private Spinner typeSpinner(String selected){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"GASTO","INGRESO"}));s.setSelection("INGRESO".equals(selected)?1:0);return s;}
    private Spinner currencySpinner(String selected){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"UYU","USD"}));s.setSelection("USD".equals(selected)?1:0);return s;}
    private EditText amountEdit(double value){EditText e=new EditText(this);e.setHint("Monto");e.setInputType(2|8192);if(value>0)e.setText(String.format(Locale.US,"%.2f",value));return e;}
    private EditText textEdit(String hint,String value){EditText e=new EditText(this);e.setHint(hint);if(value!=null)e.setText(value);return e;}
    private double parseAmount(EditText e){double v=Double.parseDouble(e.getText().toString().trim().replace(',','.'));if(v<=0)throw new IllegalArgumentException();return v;}
    private String sourceLabel(String s){if(s==null)return "desconocido";String n=s.toLowerCase(Locale.ROOT);if(n.contains("paganza"))return "Paganza";if(n.startsWith("notificacion:"))return "Notificación bancaria";if(n.contains("xls")||n.contains("excel")||n.contains("banco"))return "Excel bancario";if(n.contains("recurrente"))return "Recurrente";if(n.contains("manual"))return "Manual";return s;}

    private SharedPreferences securityPrefs(){return getSharedPreferences("security",MODE_PRIVATE);}
    private boolean biometricLockEnabled(){return securityPrefs().getBoolean("biometric_lock",false);}
    private boolean screenshotsBlocked(){return securityPrefs().getBoolean("block_screenshots",false);}
    private void migrateScreenshotSetting(){
        SharedPreferences p=securityPrefs();
        if(!p.getBoolean("screenshots_default_migrated_v142",false)){
            p.edit().putBoolean("block_screenshots",false).putBoolean("screenshots_default_migrated_v142",true).apply();
        }
    }
    private void applyScreenSecurity(){if(screenshotsBlocked())getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}

    private void showSecuritySettings(){
        LinearLayout f=form();
        Switch light=new Switch(this);light.setText("Modo claro");light.setChecked(ThemePrefs.isLight(this));
        Switch bio=new Switch(this);bio.setText("Bloquear al abrir con huella/biometría");bio.setChecked(biometricLockEnabled());
        Switch shots=new Switch(this);shots.setText("Bloquear capturas y vista en recientes");shots.setChecked(screenshotsBlocked());
        Button tools=button("Importar · recurrentes · exportar");tools.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        TextView note=tv("Podés usar ControlGastos en modo oscuro o claro. La app no tiene permiso de Internet, los datos quedan en el almacenamiento privado de Android y las copias de seguridad del sistema están desactivadas.",13,MUTED);
        f.addView(light);f.addView(bio);f.addView(shots);f.addView(note);f.addView(tools);
        new AlertDialog.Builder(this).setTitle("Configuración").setView(f).setPositiveButton("Guardar",(d,w)->{
            boolean was=biometricLockEnabled();
            boolean themeChanged=ThemePrefs.isLight(this)!=light.isChecked();
            ThemePrefs.setLight(this,light.isChecked());
            securityPrefs().edit().putBoolean("biometric_lock",bio.isChecked()).putBoolean("block_screenshots",shots.isChecked()).apply();
            applyScreenSecurity();
            if(themeChanged){recreate();return;}
            if(bio.isChecked()&&!was){unlocked=false;getWindow().getDecorView().postDelayed(this::maybeAuthenticate,250);}else if(!bio.isChecked())unlocked=true;
        }).setNegativeButton("Cancelar",null).show();
    }

    private void maybeAuthenticate(){
        if(!biometricLockEnabled()||unlocked||authInProgress||isFinishing()) return;
        authInProgress=true;
        if(Build.VERSION.SDK_INT>=28) authenticateBiometric(); else authenticateDeviceCredential();
    }

    private void authenticateBiometric(){
        if(Build.VERSION.SDK_INT<28){authenticateDeviceCredential();return;}
        try{
            CancellationSignal cancel=new CancellationSignal();
            BiometricPrompt prompt=new BiometricPrompt.Builder(this)
                .setTitle("Desbloquear ControlGastos")
                .setSubtitle("Confirmá tu identidad para ver tus movimientos")
                .setNegativeButton("Cancelar",getMainExecutor(),(dialog,which)->{authInProgress=false;finish();})
                .build();
            prompt.authenticate(cancel,getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){super.onAuthenticationSucceeded(result);authInProgress=false;unlocked=true;}
                @Override public void onAuthenticationError(int code,CharSequence msg){super.onAuthenticationError(code,msg);if(isFinishing())return;authInProgress=false;if(code==BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS||code==BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT||code==BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE)authenticateDeviceCredential();}
            });
        }catch(Exception e){authInProgress=false;authenticateDeviceCredential();}
    }

    private void authenticateDeviceCredential(){
        try{
            KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
            if(km==null||!km.isKeyguardSecure()){
                authInProgress=false;securityPrefs().edit().putBoolean("biometric_lock",false).apply();
                Toast.makeText(this,"Configurá primero un PIN, patrón o biometría en Android.",Toast.LENGTH_LONG).show();return;
            }
            Intent i=km.createConfirmDeviceCredentialIntent("Desbloquear ControlGastos","Confirmá tu identidad para continuar");
            if(i==null){authInProgress=false;return;}
            startActivityForResult(i,REQ_DEVICE_CREDENTIAL);
        }catch(Exception e){authInProgress=false;}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_DEVICE_CREDENTIAL){authInProgress=false;if(resultCode==RESULT_OK)unlocked=true;else finish();}
    }

    private void showHelp(){
        ScrollView sc=new ScrollView(this);TextView t=tv(
            "CÓMO FUNCIONA\n\n"+
            "1. Registro automático\nControlGastos puede leer notificaciones de pagos de eBROU/BROU y Paganza cuando autorizás el acceso a notificaciones. No necesita tu usuario ni contraseña bancaria.\n\n"+
            "2. Paganza\nLos pagos reconocidos desde Paganza se guardan automáticamente. Si después el banco muestra un débito identificado como PAGANZA, se ignora para evitar contar el mismo gasto dos veces.\n\n"+
            "3. Importar Excel del banco\nEn Importar podés seleccionar el archivo .xls descargado del banco. La app usa huellas internas para evitar volver a cargar movimientos ya importados y concilia compras capturadas previamente por notificación.\n\n"+
            "4. Agregar manualmente\nTocá + Agregar para cargar efectivo u otros movimientos que no lleguen automáticamente.\n\n"+
            "5. Editar o eliminar\nTocá cualquier movimiento para modificar tipo, monto, moneda, descripción, categoría o fecha. Desde la misma ventana también podés eliminarlo. Mantener apretada una fila permite eliminar rápidamente.\n\n"+
            "6. Gráficos y comercios\nEn Gráficos podés analizar gastos por categoría y por comercio. Variantes del mismo comercio se agrupan para mostrar el total gastado. También podés comparar meses y usar presupuestos por categoría.\n\n"+
            "7. Apariencia y seguridad\nEn Ajustes podés alternar entre modo oscuro y modo claro, activar bloqueo biométrico y, si querés, bloquear las capturas de pantalla.\n\n"+
            "8. Recurrentes y exportación\nEn Ajustes tocá Importar · recurrentes · exportar para acceder a esas herramientas.\n\n"+
            "SI FALTA UNA COMPRA\nRevisá que el acceso a notificaciones esté activado. Android puede restringir servicios en segundo plano; abrir ControlGastos permite volver a revisar notificaciones activas. Como respaldo siempre podés importar el Excel del banco.\n\n"+
            "PRIVACIDAD\nLos movimientos se guardan en la base de datos local privada de ControlGastos. El permiso de notificaciones se utiliza para detectar mensajes compatibles con pagos.",15,TEXT);t.setPadding(dp(22),dp(10),dp(22),dp(18));sc.addView(t);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Ayuda · ControlGastos").setView(sc).setPositiveButton("Entendido",null).create();dlg.show();
    }

    private boolean notificationEnabled(){String e=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return e!=null&&e.contains(getPackageName());}
    private void openNotificationSettings(){try{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}catch(Exception ignored){}}
}
