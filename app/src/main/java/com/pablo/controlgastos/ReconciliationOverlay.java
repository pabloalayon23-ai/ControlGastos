package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.text.DecimalFormat;
import java.util.*;

public final class ReconciliationOverlay {
    private static final WeakHashMap<Activity,Boolean> installed=new WeakHashMap<>();
    private static final DecimalFormat MONEY=new DecimalFormat("#,##0.00");
    private ReconciliationOverlay(){}

    public static void install(Activity a){
        synchronized(installed){if(installed.containsKey(a))return;installed.put(a,true);}
        if(!(a instanceof MainActivity))return;
        a.getWindow().getDecorView().postDelayed(()->attach(a),420);
    }

    private static void attach(Activity a){
        if(a.isFinishing())return;
        Button anchor=findButton(a.getWindow().getDecorView(),"Importar archivo eBROU");
        if(anchor==null||!(anchor.getParent() instanceof LinearLayout))return;
        LinearLayout parent=(LinearLayout)anchor.getParent();
        for(int i=0;i<parent.getChildCount();i++)if(parent.getChildAt(i) instanceof Button&&((Button)parent.getChildAt(i)).getText().toString().contains("Conciliar movimientos"))return;
        Button b=new Button(a);b.setText("🔎 Conciliar movimientos");b.setAllCaps(false);b.setTextColor(ThemePrefs.isLight(a)?Color.rgb(31,38,44):Color.rgb(242,245,247));GradientDrawable bg=new GradientDrawable();bg.setColor(ThemePrefs.isLight(a)?Color.WHITE:Color.rgb(22,32,41));bg.setCornerRadius(dp(a,14));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,58));p.setMargins(0,dp(a,7),0,dp(a,7));int index=parent.indexOfChild(anchor);parent.addView(b,index+1,p);b.setOnClickListener(v->scan(a));
    }

    private static void scan(Activity a){
        ProgressDialog p=ProgressDialog.show(a,"Conciliando","Buscando posibles duplicados…",true,false);
        new Thread(()->{
            ExpenseDb db=new ExpenseDb(a);ArrayList<MovementReconciler.Candidate> items=MovementReconciler.findCandidates(db);
            a.runOnUiThread(()->{p.dismiss();if(items.isEmpty()){new AlertDialog.Builder(a).setTitle("Conciliar movimientos").setMessage("No encontré posibles duplicados con la regla actual: misma fecha, mismo importe, misma moneda, mismo tipo y nombre parecido.").setPositiveButton("Cerrar",null).show();return;}new AlertDialog.Builder(a).setTitle("Conciliar movimientos").setMessage("Encontré "+items.size()+" posible"+(items.size()==1?" duplicado":"s duplicados")+".\n\nLa búsqueda es deliberadamente conservadora: exige misma fecha, importe exacto, moneda y tipo, además de una parte del nombre suficientemente parecida.\n\nNada se unifica sin que lo confirmes.").setPositiveButton("Revisar",(d,w)->review(a,db,items,0,0)).setNegativeButton("Cancelar",null).show();});
        }).start();
    }

    private static void review(Activity a,ExpenseDb db,ArrayList<MovementReconciler.Candidate> items,int index,int merged){
        if(index>=items.size()){new AlertDialog.Builder(a).setTitle("Conciliación terminada").setMessage("Movimientos unificados: "+merged+".\n\nLas copias eliminadas quedan en la Papelera.").setPositiveButton("Cerrar",null).show();return;}
        MovementReconciler.Candidate c=items.get(index);String sign="GASTO".equals(c.type)?"-":"+";String amount=("USD".equals(c.currency)?"USD ":"$ ")+MONEY.format(c.amount);
        String msg="Fecha: "+c.dateLabel+"\nImporte: "+sign+amount+"\n\n1) "+safe(c.descriptionA)+"\nCategoría: "+safeCat(c.categoryA)+"\nOrigen: "+safe(c.sourceA)+"\n\n2) "+safe(c.descriptionB)+"\nCategoría: "+safeCat(c.categoryB)+"\nOrigen: "+safe(c.sourceB)+"\n\n¿Son el mismo movimiento?";
        new AlertDialog.Builder(a).setTitle((index+1)+" de "+items.size()+" · Posible duplicado").setMessage(msg).setPositiveButton("Unificar",(d,w)->{boolean ok=MovementReconciler.merge(db,c);Toast.makeText(a,ok?"Movimientos unificados":"No se pudo unificar",Toast.LENGTH_SHORT).show();review(a,db,items,index+1,merged+(ok?1:0));}).setNeutralButton("Omitir",(d,w)->review(a,db,items,index+1,merged)).setNegativeButton("Cerrar",null).show();
    }

    private static Button findButton(View v,String contains){if(v instanceof Button&&((Button)v).getText()!=null&&((Button)v).getText().toString().contains(contains))return(Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),contains);if(b!=null)return b;}}return null;}
    private static int dp(Context c,int n){return(int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    private static String safe(String s){return s==null||s.trim().isEmpty()?"Sin descripción":s;}
    private static String safeCat(String s){return s==null||s.trim().isEmpty()?"Sin categoría":s;}
}
