package com.pablo.controlgastos;
import android.app.*;import android.content.*;import android.graphics.drawable.Drawable;import android.view.*;import android.widget.*;
public final class EmailSettingsOverlay{
 private EmailSettingsOverlay(){}
 public static void install(Activity a){if(!(a instanceof MainActivity))return;View root=a.findViewById(android.R.id.content);if(root==null)return;root.postDelayed(()->inject(a,root),250);}
 private static void inject(Activity a,View root){if(root.findViewWithTag("email_settings")!=null)return;Button anchor=findButton(root,"Importar archivo eBROU");if(anchor==null||!(anchor.getParent() instanceof LinearLayout))return;LinearLayout p=(LinearLayout)anchor.getParent();Button b=new Button(a);b.setTag("email_settings");b.setText("▧ Correo y lecturas");b.setAllCaps(false);b.setTextColor(anchor.getTextColors());Drawable d=anchor.getBackground();if(d!=null&&d.getConstantState()!=null)b.setBackground(d.getConstantState().newDrawable().mutate());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,58));lp.setMargins(0,dp(a,7),0,dp(a,7));p.addView(b,p.indexOfChild(anchor)+1,lp);b.setOnClickListener(v->a.startActivity(new Intent(a,EmailSettingsActivity.class)));}
 private static Button findButton(View v,String text){if(v instanceof Button&&((Button)v).getText()!=null&&((Button)v).getText().toString().contains(text))return(Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
 private static int dp(Activity a,int n){return(int)(n*a.getResources().getDisplayMetrics().density+.5f);}
}
