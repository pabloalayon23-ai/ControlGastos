package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;

public final class MonthlyInsightsOverlay {
 private static final WeakHashMap<Activity,Boolean> installed=new WeakHashMap<>();
 private static final DecimalFormat MONEY=new DecimalFormat("#,##0.00");
 private MonthlyInsightsOverlay(){}

 public static void install(Activity a){
  synchronized(installed){if(installed.containsKey(a))return;installed.put(a,true);}
  a.getWindow().getDecorView().postDelayed(()->{
   if(a.isFinishing())return;
   if(a instanceof AnalyticsActivity)installAnalytics(a);
   if(a instanceof HomeActivity)installHelp(a);
  },300);
 }

 private static void installAnalytics(Activity a){
  View root=a.getWindow().getDecorView();
  HorizontalScrollView hs=findHorizontalScroll(root);
  if(hs==null||hs.getChildCount()==0||!(hs.getChildAt(0) instanceof LinearLayout))return;
  LinearLayout tabs=(LinearLayout)hs.getChildAt(0);
  for(int i=0;i<tabs.getChildCount();i++)if(tabs.getChildAt(i) instanceof Button&&"Análisis".contentEquals(((Button)tabs.getChildAt(i)).getText()))return;
  Button b=new Button(a);b.setText("Análisis");b.setAllCaps(false);b.setTextSize(14);b.setOnClickListener(v->showAnalysis(a));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(a,118),dp(a,46));p.setMargins(dp(a,3),0,dp(a,3),0);tabs.addView(b,p);
 }

 private static void installHelp(Activity a){
  Button help=findButton(a.getWindow().getDecorView(),"Ayuda");
  if(help==null)return;
  help.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("Ayuda · ControlGastos").setItems(new String[]{"Ayuda general","Análisis automático del mes"},(d,w)->{if(w==0)invokeOriginalHelp(a);else showAnalysisHelp(a);}).setNegativeButton("Cerrar",null).show());
 }

 private static void invokeOriginalHelp(Activity a){
  try{Method m=a.getClass().getDeclaredMethod("showHelp");m.setAccessible(true);m.invoke(a);}catch(Exception e){Toast.makeText(a,"No se pudo abrir la ayuda general",Toast.LENGTH_LONG).show();}
 }

 private static void showAnalysisHelp(Activity a){
  String t="ANÁLISIS AUTOMÁTICO DEL MES\n\nEn Gráficos → Análisis, ControlGastos evalúa el período seleccionado usando solamente los movimientos guardados en el teléfono.\n\nPuede mostrar ahorro o déficit y tasa de ahorro, comparar el gasto con los 6 meses anteriores, detectar categorías fuera de lo común, gastos individuales abruptos, acumulación de gastos pequeños y tendencias de tres meses.\n\nTambién estima cuánto habría que reducir para llegar a un ahorro del 20% de los ingresos y señala la categoría que más se aparta de su histórico.\n\nCon poco historial la app lo indica en lugar de inventar conclusiones. El análisis es orientativo, se recalcula al cambiar de mes y no envía movimientos a Internet ni usa IA externa.";
  TextView tv=new TextView(a);tv.setText(t);tv.setTextSize(15);tv.setPadding(dp(a,24),dp(a,16),dp(a,24),dp(a,16));new AlertDialog.Builder(a).setTitle("Análisis automático").setView(tv).setPositiveButton("Entendido",null).show();
 }

 private static void showAnalysis(Activity a){
  try{
   Calendar selected=Calendar.getInstance();boolean annual=false;
   try{Field f=a.getClass().getDeclaredField("selected");f.setAccessible(true);selected=(Calendar)((Calendar)f.get(a)).clone();}catch(Exception ignored){}
   try{Field f=a.getClass().getDeclaredField("annual");f.setAccessible(true);annual=f.getBoolean(a);}catch(Exception ignored){}
   String report=buildReport(a,selected,annual);
   ScrollView sc=new ScrollView(a);TextView tv=new TextView(a);tv.setText(report);tv.setTextSize(15);tv.setPadding(dp(a,22),dp(a,14),dp(a,22),dp(a,22));sc.addView(tv);
   new AlertDialog.Builder(a).setTitle("Análisis del período").setView(sc).setPositiveButton("Cerrar",null).show();
  }catch(Exception e){new AlertDialog.Builder(a).setTitle("Análisis").setMessage("No pude calcular el análisis con los datos actuales.").setPositiveButton("Cerrar",null).show();}
 }

 private static String buildReport(Context c,Calendar selected,boolean annual){
  ExpenseDb db=new ExpenseDb(c);double[] totals=totals(db,selected,annual);double income=totals[0],expense=totals[1],balance=income-expense;StringBuilder out=new StringBuilder();
  if(annual){out.append("Resumen anual\n\nIngresos: $ ").append(MONEY.format(income)).append("\nGastos: $ ").append(MONEY.format(expense)).append("\n").append(balance>=0?"Ahorro: $ ":"Déficit: $ ").append(MONEY.format(Math.abs(balance))).append("\n\nPara detectar gastos abruptos, categorías anómalas y tendencias con mayor precisión, cambiá a modo Mes.");return out.toString();}
  double rate=income>0?balance/income*100:0;
  out.append(balance>=0?"MES CON AHORRO":"MES CON DÉFICIT").append("\n");
  out.append("Ingresos: $ ").append(MONEY.format(income)).append("\nGastos: $ ").append(MONEY.format(expense)).append("\n");
  out.append(balance>=0?"Ahorro: $ ":"Déficit: $ ").append(MONEY.format(Math.abs(balance))).append("\n");
  if(income>0)out.append("Tasa de ahorro: ").append(new DecimalFormat("0.0").format(rate)).append("%\n");

  double avg6=0;for(int i=1;i<=6;i++){Calendar m=(Calendar)selected.clone();m.add(Calendar.MONTH,-i);avg6+=totals(db,m,false)[1];}avg6/=6.0;
  if(avg6>0){double pct=(expense-avg6)/avg6*100;if(Math.abs(pct)>=10)out.append("\nGASTO MENSUAL ").append(pct>0?"ELEVADO":"MENOR A LO HABITUAL").append("\nEste mes gastaste ").append(new DecimalFormat("0").format(Math.abs(pct))).append("% ").append(pct>0?"más":"menos").append(" que el promedio de los 6 meses anteriores ($ ").append(MONEY.format(avg6)).append(").\n");}

  Map<String,Double> current=categoryTotals(db,selected);Map<String,Double> avgCats=new HashMap<>();for(int i=1;i<=6;i++){Calendar m=(Calendar)selected.clone();m.add(Calendar.MONTH,-i);for(Map.Entry<String,Double>e:categoryTotals(db,m).entrySet())avgCats.put(e.getKey(),avgCats.getOrDefault(e.getKey(),0.0)+e.getValue()/6.0);}
  String abrupt=null;double abruptPct=0,abruptVal=0;for(Map.Entry<String,Double>e:current.entrySet()){double av=avgCats.getOrDefault(e.getKey(),0.0);if(av>0&&e.getValue()>av*1.5&&e.getValue()-av>1000){double p=(e.getValue()-av)/av*100;if(p>abruptPct){abrupt=e.getKey();abruptPct=p;abruptVal=e.getValue();}}}
  if(abrupt!=null)out.append("\nCATEGORÍA FUERA DE LO COMÚN\n").append(abrupt).append(" llegó a $ ").append(MONEY.format(abruptVal)).append(", ").append(new DecimalFormat("0").format(abruptPct)).append("% por encima de su promedio reciente.\n");

  Calendar a=start(selected,false),b=(Calendar)a.clone();b.add(Calendar.MONTH,1);Cursor q=db.getReadableDatabase().rawQuery("SELECT amount,description FROM tx WHERE type='GASTO' AND currency='UYU' AND ts>=? AND ts<?",new String[]{String.valueOf(a.getTimeInMillis()),String.valueOf(b.getTimeInMillis())});
  double sum=0,big=0,smallSum=0;int n=0,smallN=0;String bigDesc="";while(q.moveToNext()){double v=q.getDouble(0);sum+=v;n++;if(v>big){big=v;bigDesc=q.getString(1);}if(v<=500){smallN++;smallSum+=v;}}q.close();double avgTx=n>0?sum/n:0;
  if(big>0&&avgTx>0&&big>=avgTx*3)out.append("\nGASTO INDIVIDUAL ABRUPTO\n").append(bigDesc==null?"Un movimiento":bigDesc).append(" fue de $ ").append(MONEY.format(big)).append(", unas ").append(new DecimalFormat("0.0").format(big/avgTx)).append(" veces el gasto promedio del mes.\n");
  if(smallN>=5&&smallSum>=1000)out.append("\nGASTOS PEQUEÑOS ACUMULADOS\n").append(smallN).append(" gastos de hasta $ 500 suman $ ").append(MONEY.format(smallSum)).append(".\n");

  Calendar m1=(Calendar)selected.clone();m1.add(Calendar.MONTH,-1);Calendar m2=(Calendar)selected.clone();m2.add(Calendar.MONTH,-2);double e1=totals(db,m1,false)[1],e2=totals(db,m2,false)[1];if(e2>0&&e1>e2&&expense>e1)out.append("\nTENDENCIA ASCENDENTE\nTus gastos subieron durante tres meses seguidos: $ ").append(MONEY.format(e2)).append(" → $ ").append(MONEY.format(e1)).append(" → $ ").append(MONEY.format(expense)).append(".\n");

  if(income>0&&rate<20){double target=Math.max(0,expense-income*0.80);if(target>0){String cat="";double excess=0;for(Map.Entry<String,Double>e:current.entrySet()){double ex=Math.max(0,e.getValue()-avgCats.getOrDefault(e.getKey(),0.0));if(ex>excess){excess=ex;cat=e.getKey();}}out.append("\nOBJETIVO DE AHORRO DEL 20%\nPara llegar a un ahorro del 20% tendrías que reducir aproximadamente $ ").append(MONEY.format(target)).append(".");if(!cat.isEmpty())out.append(" La categoría con mayor exceso frente a tu histórico es ").append(cat).append(".");out.append("\n");}}
  if(avg6==0)out.append("\nFALTA HISTÓRICO\nTodavía no hay suficiente historial para hacer comparaciones confiables.\n");
  out.append("\nEste análisis se calcula localmente en tu teléfono y es orientativo.");return out.toString();
 }

 private static double[] totals(ExpenseDb db,Calendar base,boolean annual){Calendar a=start(base,annual),b=(Calendar)a.clone();b.add(annual?Calendar.YEAR:Calendar.MONTH,1);Cursor q=db.getReadableDatabase().rawQuery("SELECT type,amount,currency FROM tx WHERE ts>=? AND ts<?",new String[]{String.valueOf(a.getTimeInMillis()),String.valueOf(b.getTimeInMillis())});double in=0,out=0;while(q.moveToNext()){if(!"UYU".equals(q.getString(2)))continue;if("INGRESO".equals(q.getString(0)))in+=q.getDouble(1);else if("GASTO".equals(q.getString(0)))out+=q.getDouble(1);}q.close();return new double[]{in,out};}
 private static Map<String,Double> categoryTotals(ExpenseDb db,Calendar base){Calendar a=start(base,false),b=(Calendar)a.clone();b.add(Calendar.MONTH,1);Cursor q=db.getReadableDatabase().rawQuery("SELECT amount,category FROM tx WHERE type='GASTO' AND currency='UYU' AND ts>=? AND ts<?",new String[]{String.valueOf(a.getTimeInMillis()),String.valueOf(b.getTimeInMillis())});Map<String,Double>m=new HashMap<>();while(q.moveToNext()){String k=q.getString(1);if(k==null||k.trim().isEmpty())k="Otros";m.put(k,m.getOrDefault(k,0.0)+q.getDouble(0));}q.close();return m;}
 private static Calendar start(Calendar base,boolean annual){Calendar a=(Calendar)base.clone();a.set(Calendar.DAY_OF_MONTH,1);a.set(Calendar.HOUR_OF_DAY,0);a.set(Calendar.MINUTE,0);a.set(Calendar.SECOND,0);a.set(Calendar.MILLISECOND,0);if(annual)a.set(Calendar.MONTH,Calendar.JANUARY);return a;}
 private static HorizontalScrollView findHorizontalScroll(View v){if(v instanceof HorizontalScrollView)return(HorizontalScrollView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){HorizontalScrollView x=findHorizontalScroll(g.getChildAt(i));if(x!=null)return x;}}return null;}
 private static Button findButton(View v,String contains){if(v instanceof Button&&((Button)v).getText()!=null&&((Button)v).getText().toString().contains(contains))return(Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),contains);if(b!=null)return b;}}return null;}
 private static int dp(Context c,int n){return(int)(n*c.getResources().getDisplayMetrics().density+.5f);}
}
