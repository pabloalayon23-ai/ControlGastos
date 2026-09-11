package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public class HomeActivity extends Activity {
    private ExpenseDb db;
    private LinearLayout list;
    private TextView gastos, ingresos, saldo, month;
    private final DecimalFormat money=new DecimalFormat("#,##0.00");
    private final int BG=Color.rgb(11,18,24), CARD=Color.rgb(22,32,41), TEXT=Color.rgb(242,245,247), MUTED=Color.rgb(158,169,178), YELLOW=Color.rgb(255,207,52);

    @Override public void onCreate(Bundle b){ super.onCreate(b); db=new ExpenseDb(this); build(); }
    @Override protected void onResume(){ super.onResume(); refresh(); }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView tv(String s,int size,int color){ TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(8),dp(6),dp(8),dp(6));return v; }
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackground(bg(CARD,14));return b;}

    private void build(){
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(16),dp(14),dp(16),dp(4));
        TextView title=tv("ControlGastos",28,TEXT); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); header.addView(title);
        month=tv("",15,MUTED); header.addView(month); outer.addView(header);

        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.HORIZONTAL);summary.setPadding(dp(12),dp(5),dp(12),dp(10));
        gastos=summaryCard("Gastos",Color.rgb(255,111,111)); ingresos=summaryCard("Ingresos",Color.rgb(87,220,143)); saldo=summaryCard("Saldo",Color.rgb(115,175,255));
        summary.addView(gastos,new LinearLayout.LayoutParams(0,dp(94),1)); summary.addView(ingresos,new LinearLayout.LayoutParams(0,dp(94),1)); summary.addView(saldo,new LinearLayout.LayoutParams(0,dp(94),1)); outer.addView(summary);

        if(!notificationEnabled()){
            Button warn=button("⚠ Activar lectura de notificaciones");warn.setOnClickListener(v->openNotificationSettings());LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.setMargins(dp(14),0,dp(14),dp(8));outer.addView(warn,wp);
        }

        TextView mov=tv("Movimientos",20,TEXT);mov.setTypeface(Typeface.DEFAULT,Typeface.BOLD);mov.setPadding(dp(18),dp(5),0,dp(4));outer.addView(mov);
        ScrollView sc=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(12),0,dp(12),dp(10));sc.addView(list);outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(dp(5),dp(4),dp(5),dp(8));nav.setBackgroundColor(Color.rgb(17,25,32));
        Button inicio=navBtn("⌂\nInicio",true), graficos=navBtn("▥\nGráficos",false), add=navBtn("＋\nAgregar",true), importar=navBtn("⇧\nImportar",false), ajustes=navBtn("⚙\nAjustes",false);
        nav.addView(inicio,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(graficos,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(add,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(importar,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(ajustes,new LinearLayout.LayoutParams(0,dp(66),1));outer.addView(nav);
        graficos.setOnClickListener(v->startActivity(new Intent(this,AnalyticsActivity.class))); add.setOnClickListener(v->showAdd()); importar.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class))); ajustes.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
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
            String type=(String)r[1],cur=(String)r[3],cat=(String)r[4],desc=(String)r[5];double a=(Double)r[2];
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(9),dp(10),dp(9));row.setBackground(bg(CARD,12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(6));row.setLayoutParams(rp);
            TextView icon=tv(iconFor(cat,desc),23,TEXT);icon.setGravity(Gravity.CENTER);icon.setBackground(bg(colorFor(cat,desc),24));row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);TextView d=tv(desc==null?"Sin descripción":desc,17,TEXT);d.setTypeface(Typeface.DEFAULT,Typeface.BOLD);TextView sub=tv((cat==null?"Sin categoría":cat)+" · "+time.format(new Date(ts)),13,MUTED);mid.addView(d);mid.addView(sub);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1);mp.setMargins(dp(8),0,dp(5),0);row.addView(mid,mp);
            TextView amt=tv(("GASTO".equals(type)?"-":"+")+("USD".equals(cur)?"USD ":"$ ")+money.format(a),16,"GASTO".equals(type)?Color.rgb(255,112,112):Color.rgb(92,224,146));amt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);amt.setGravity(Gravity.RIGHT);row.addView(amt);
            final String fdesc=desc;row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Eliminar movimiento").setMessage(fdesc).setPositiveButton("Eliminar",(x,w)->{db.deleteTx(id);refresh();}).setNegativeButton("Cancelar",null).show();return true;});list.addView(row);
        }
    }

    private String iconFor(String cat,String desc){String s=((cat==null?"":cat)+" "+(desc==null?"":desc)).toLowerCase(Locale.ROOT);if(s.contains("super")||s.contains("disco")||s.contains("devoto")||s.contains("geant"))return "🛒";if(s.contains("combust")||s.contains("ancap"))return "⛽";if(s.contains("salud")||s.contains("farm"))return "❤";if(s.contains("comida")||s.contains("rest"))return "🍴";if(s.contains("transporte")||s.contains("peaje"))return "🚗";if(s.contains("servicio")||s.contains("ute")||s.contains("antel"))return "⌂";if(s.contains("ingreso"))return "$";return "●";}
    private int colorFor(String cat,String desc){String i=iconFor(cat,desc);if("🛒".equals(i))return Color.rgb(232,170,36);if("⛽".equals(i))return Color.rgb(72,128,238);if("❤".equals(i))return Color.rgb(78,135,244);if("🍴".equals(i))return Color.rgb(58,178,98);if("🚗".equals(i))return Color.rgb(74,180,190);return Color.rgb(123,91,190);}

    private void showAdd(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(24),0,dp(24),0);Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"GASTO","INGRESO"}));EditText amount=new EditText(this);amount.setHint("Monto");amount.setInputType(2|8192);Spinner currency=new Spinner(this);currency.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"UYU","USD"}));EditText desc=new EditText(this);desc.setHint("Descripción");EditText cat=new EditText(this);cat.setHint("Categoría");f.addView(type);f.addView(amount);f.addView(currency);f.addView(desc);f.addView(cat);new AlertDialog.Builder(this).setTitle("Nuevo movimiento").setView(f).setPositiveButton("Guardar",(d,w)->{try{double a=Double.parseDouble(amount.getText().toString().replace(',','.'));String de=desc.getText().toString().trim();if(de.isEmpty())de="Sin descripción";String ca=cat.getText().toString().trim();if(ca.isEmpty())ca="Sin categoría";db.addTx(type.getSelectedItem().toString(),a,currency.getSelectedItem().toString(),ca,de,"",System.currentTimeMillis(),"manual");refresh();}catch(Exception e){Toast.makeText(this,"Revisá el monto",Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancelar",null).show();}
    private boolean notificationEnabled(){String e=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return e!=null&&e.contains(getPackageName());}
    private void openNotificationSettings(){try{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}catch(Exception ignored){}}
}
