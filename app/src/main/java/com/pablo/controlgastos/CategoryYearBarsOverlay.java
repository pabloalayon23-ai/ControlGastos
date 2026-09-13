package com.pablo.controlgastos;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public final class CategoryYearBarsOverlay {
    private static final WeakHashMap<Activity,Boolean> INSTALLED=new WeakHashMap<>();
    private static final DecimalFormat MONEY=new DecimalFormat("#,##0.00");
    private CategoryYearBarsOverlay(){}

    public static void install(Activity a){
        if(!(a instanceof AnalyticsActivity))return;
        synchronized(INSTALLED){if(INSTALLED.containsKey(a))return;INSTALLED.put(a,true);}
        a.getWindow().getDecorView().postDelayed(()->addTab(a),420);
    }

    private static void addTab(Activity a){
        if(a.isFinishing())return;
        HorizontalScrollView hs=findHorizontalScroll(a.getWindow().getDecorView());
        if(hs==null||hs.getChildCount()==0||!(hs.getChildAt(0) instanceof LinearLayout))return;
        LinearLayout tabs=(LinearLayout)hs.getChildAt(0);
        for(int i=0;i<tabs.getChildCount();i++){
            View v=tabs.getChildAt(i);
            if(v instanceof Button&&"Vista anual".contentEquals(((Button)v).getText()))return;
        }
        Button b=styledButton(a,"Vista anual");
        b.setOnClickListener(v->showInPage(a));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(a,128),dp(a,46));
        p.setMargins(dp(a,3),0,dp(a,3),0);
        tabs.addView(b,p);
    }

    private static void showInPage(Activity a){
        LinearLayout content=getContent(a);
        if(content==null){Toast.makeText(a,"No pude abrir la vista anual",Toast.LENGTH_LONG).show();return;}
        final boolean light=ThemePrefs.isLight(a);
        final int text=light?Color.rgb(31,38,44):Color.rgb(242,245,247);
        final int muted=light?Color.rgb(95,105,112):Color.rgb(158,169,178);
        final int card=light?Color.WHITE:Color.rgb(22,32,41);

        ArrayList<String> cats=new ArrayList<>(DetectionRules.categoryNames(a));
        if(!cats.contains("Otros"))cats.add("Otros");
        Collections.sort(cats,String.CASE_INSENSITIVE_ORDER);
        if(cats.isEmpty()){Toast.makeText(a,"No hay categorías configuradas",Toast.LENGTH_LONG).show();return;}

        Calendar end=selectedMonth(a);
        content.removeAllViews();

        TextView title=label(a,"Vista anual por categoría",22,text);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setPadding(dp(a,8),dp(a,6),dp(a,8),dp(a,2));content.addView(title);
        TextView intro=label(a,"Últimos 12 meses hasta el período seleccionado",14,muted);intro.setPadding(dp(a,8),0,dp(a,8),dp(a,10));content.addView(intro);

        final int[] index={0};
        LinearLayout nav=new LinearLayout(a);nav.setGravity(Gravity.CENTER_VERTICAL);
        Button prev=styledButton(a,"‹"),catBtn=styledButton(a,cats.get(0)),next=styledButton(a,"›");
        nav.addView(prev,new LinearLayout.LayoutParams(dp(a,48),dp(a,46)));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(a,46),1);cp.setMargins(dp(a,6),0,dp(a,6),0);nav.addView(catBtn,cp);
        nav.addView(next,new LinearLayout.LayoutParams(dp(a,48),dp(a,46)));
        content.addView(nav);

        TextView period=label(a,"",13,muted);period.setGravity(Gravity.CENTER_HORIZONTAL);period.setPadding(0,dp(a,8),0,0);content.addView(period);

        CategoryBarsView chart=new CategoryBarsView(a,light);
        LinearLayout.LayoutParams chartP=new LinearLayout.LayoutParams(-1,dp(a,330));chartP.setMargins(0,dp(a,2),0,dp(a,8));content.addView(chart,chartP);

        TextView summary=label(a,"",15,text);summary.setPadding(dp(a,14),dp(a,11),dp(a,14),dp(a,11));GradientDrawable sg=new GradientDrawable();sg.setColor(card);sg.setCornerRadius(dp(a,14));summary.setBackground(sg);content.addView(summary);
        TextView hint=label(a,"Tocá una barra para ver el importe exacto de ese mes.",13,muted);hint.setPadding(dp(a,8),dp(a,8),dp(a,8),dp(a,4));content.addView(hint);

        Runnable refresh=()->{
            String cat=cats.get(index[0]);catBtn.setText(cat);
            ArrayList<MonthValue> values=load(a,cat,end);
            chart.setData(cat,values,CategoryPrefs.color(a,cat));
            Calendar first=(Calendar)end.clone();first.add(Calendar.MONTH,-11);
            SimpleDateFormat fmt=new SimpleDateFormat("MMM yyyy",new Locale("es","UY"));
            period.setText(cap(fmt.format(first.getTime()))+"  —  "+cap(fmt.format(end.getTime())));
            summary.setText(summary(values));
        };
        prev.setOnClickListener(v->{index[0]=(index[0]-1+cats.size())%cats.size();refresh.run();});
        next.setOnClickListener(v->{index[0]=(index[0]+1)%cats.size();refresh.run();});
        catBtn.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("Elegir categoría").setItems(cats.toArray(new String[0]),(d,w)->{index[0]=w;refresh.run();}).setNegativeButton("Cancelar",null).show());
        refresh.run();
    }

    static class MonthValue{String month;double value;MonthValue(String m,double v){month=m;value=v;}}

    private static ArrayList<MonthValue> load(Context c,String category,Calendar end){
        ExpenseDb db=new ExpenseDb(c);ArrayList<MonthValue> out=new ArrayList<>();
        Calendar cur=(Calendar)end.clone();cur.set(Calendar.DAY_OF_MONTH,1);cur.set(Calendar.HOUR_OF_DAY,0);cur.set(Calendar.MINUTE,0);cur.set(Calendar.SECOND,0);cur.set(Calendar.MILLISECOND,0);cur.add(Calendar.MONTH,-11);
        SimpleDateFormat mf=new SimpleDateFormat("MMM",new Locale("es","UY"));
        for(int i=0;i<12;i++){
            Calendar next=(Calendar)cur.clone();next.add(Calendar.MONTH,1);double total=0;
            Cursor q=db.getReadableDatabase().rawQuery("SELECT amount,category,description,currency FROM tx WHERE type='GASTO' AND ts>=? AND ts<?",new String[]{String.valueOf(cur.getTimeInMillis()),String.valueOf(next.getTimeInMillis())});
            while(q.moveToNext()){
                if(!"UYU".equals(q.getString(3)))continue;
                String raw=q.getString(1),desc=q.getString(2),key=categoryKey(c,raw,desc);
                if(category.equalsIgnoreCase(key))total+=q.getDouble(0);
            }
            q.close();out.add(new MonthValue(cap(mf.format(cur.getTime())),total));cur.add(Calendar.MONTH,1);
        }
        return out;
    }

    private static String categoryKey(Context c,String raw,String desc){String x=raw==null?"":raw.trim();for(String allowed:DetectionRules.categoryNames(c))if(allowed.equalsIgnoreCase(x))return allowed;return DetectionRules.categoryFor(c,desc,"Otros");}
    private static Calendar selectedMonth(Activity a){Calendar c=Calendar.getInstance();try{Field f=a.getClass().getDeclaredField("selected");f.setAccessible(true);c=(Calendar)((Calendar)f.get(a)).clone();}catch(Exception ignored){}c.set(Calendar.DAY_OF_MONTH,1);return c;}
    private static LinearLayout getContent(Activity a){try{Field f=a.getClass().getDeclaredField("content");f.setAccessible(true);Object o=f.get(a);return o instanceof LinearLayout?(LinearLayout)o:null;}catch(Exception e){return null;}}

    private static String summary(ArrayList<MonthValue> a){
        if(a.isEmpty())return "Sin datos.";double sum=0,max=-1,min=Double.MAX_VALUE;String maxM="",minM="";int nonzero=0;
        for(MonthValue m:a){sum+=m.value;if(m.value>max){max=m.value;maxM=m.month;}if(m.value<min){min=m.value;minM=m.month;}if(m.value>0)nonzero++;}
        double avg=sum/12.0,first=a.get(0).value,last=a.get(a.size()-1).value;String trend;
        if(first>0){double pct=(last-first)/first*100.0;trend=(pct>=0?"+":"")+new DecimalFormat("0.0").format(pct)+"% vs. inicio";}else trend=last>0?"Sin base inicial comparable":"Sin variación";
        return "Promedio mensual   $ "+MONEY.format(avg)+"\nMáximo   "+maxM+" · $ "+MONEY.format(Math.max(0,max))+"\nMínimo   "+minM+" · $ "+MONEY.format(min==Double.MAX_VALUE?0:min)+"\nMeses con gasto   "+nonzero+" de 12\nVariación   "+trend;
    }

    static class CategoryBarsView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final boolean light;private ArrayList<MonthValue> data=new ArrayList<>();private int barColor=Color.rgb(255,193,7);private String category="";private final RectF[] hit=new RectF[12];
        CategoryBarsView(Context c,boolean l){super(c);light=l;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setData(String cat,ArrayList<MonthValue> d,int color){category=cat;data=d;barColor=color;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(data==null||data.isEmpty())return;
            int text=light?Color.rgb(31,38,44):Color.rgb(242,245,247),muted=light?Color.rgb(105,115,123):Color.rgb(145,158,168);
            float L=dp(getContext(),62),R=getWidth()-dp(getContext(),6),T=dp(getContext(),24),B=getHeight()-dp(getContext(),42);
            double max=0;for(MonthValue m:data)max=Math.max(max,m.value);double step=nice(max/4.0),top=step<=0?1:Math.ceil(max/step)*step;if(top<=0)top=1;
            p.setStrokeWidth(dp(getContext(),1));p.setTextSize(dp(getContext(),10));p.setTypeface(Typeface.DEFAULT);p.setTextAlign(Paint.Align.RIGHT);
            for(int j=0;j<=4;j++){float y=T+(B-T)*j/4f;double val=top*(4-j)/4.0;p.setColor(Color.argb(light?38:48,150,160,170));c.drawLine(L,y,R,y,p);p.setColor(muted);c.drawText(shortMoney(val),L-dp(getContext(),6),y+dp(getContext(),4),p);}
            float slot=(R-L)/12f,barW=Math.max(dp(getContext(),9),slot*.56f);
            for(int i=0;i<12;i++){MonthValue m=data.get(i);float cx=L+slot*(i+.5f),h=(float)((B-T)*(m.value/top)),topY=B-h;RectF rect=new RectF(cx-barW/2,topY,cx+barW/2,B);hit[i]=rect;p.setColor(Color.argb(light?24:30,150,150,150));c.drawRoundRect(new RectF(cx-barW/2,T,cx+barW/2,B),dp(getContext(),5),dp(getContext(),5),p);p.setColor(barColor);c.drawRoundRect(rect,dp(getContext(),5),dp(getContext(),5),p);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(getContext(),9));p.setColor(text);c.drawText(m.month,cx,B+dp(getContext(),17),p);}
            p.setTextAlign(Paint.Align.LEFT);p.setTextSize(dp(getContext(),11));p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(text);c.drawText(category,L,T-dp(getContext(),7),p);p.setTypeface(Typeface.DEFAULT);
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX(),y=e.getY();for(int i=0;i<hit.length&&i<data.size();i++){RectF r=hit[i];if(r!=null&&x>=r.left-dp(getContext(),6)&&x<=r.right+dp(getContext(),6)&&y>=r.top-dp(getContext(),20)&&y<=r.bottom+dp(getContext(),10)){MonthValue m=data.get(i);Toast.makeText(getContext(),m.month+" · $ "+MONEY.format(m.value),Toast.LENGTH_SHORT).show();break;}}return true;}
        private static double nice(double x){if(x<=0)return 1;double p=Math.pow(10,Math.floor(Math.log10(x))),n=x/p,k=n<=1?1:n<=2?2:n<=5?5:10;return k*p;}
        private static String shortMoney(double v){if(v>=1000000)return "$"+new DecimalFormat("0.#").format(v/1000000)+"M";if(v>=1000)return "$"+new DecimalFormat("0.#").format(v/1000)+"k";return "$"+new DecimalFormat("0").format(v);}
    }

    private static Button styledButton(Context c,String s){boolean light=ThemePrefs.isLight(c);Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(light?Color.rgb(31,38,44):Color.rgb(242,245,247));GradientDrawable g=new GradientDrawable();g.setColor(light?Color.WHITE:Color.rgb(22,32,41));g.setCornerRadius(dp(c,12));b.setBackground(g);return b;}
    private static HorizontalScrollView findHorizontalScroll(View v){if(v instanceof HorizontalScrollView)return(HorizontalScrollView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){HorizontalScrollView x=findHorizontalScroll(g.getChildAt(i));if(x!=null)return x;}}return null;}
    private static TextView label(Context c,String s,int z,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(z);v.setTextColor(color);return v;}
    private static String cap(String s){if(s==null||s.isEmpty())return s;return s.substring(0,1).toUpperCase()+s.substring(1);}
    private static int dp(Context c,int n){return(int)(n*c.getResources().getDisplayMetrics().density+.5f);}
}
