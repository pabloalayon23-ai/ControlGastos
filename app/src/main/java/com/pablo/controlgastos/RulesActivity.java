package com.pablo.controlgastos;

import android.app.*;import android.os.Bundle;import android.graphics.Color;import android.view.*;import android.widget.*;import java.util.*;

public class RulesActivity extends Activity{
 private final ArrayList<EditText> words=new ArrayList<>(),cats=new ArrayList<>();
 @Override public void onCreate(Bundle b){ThemePrefs.applyBaseTheme(this);super.onCreate(b);build();}
 private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
 private void build(){boolean light=ThemePrefs.isLight(this);int bg=light?Color.rgb(246,248,249):Color.rgb(11,18,24),text=light?Color.rgb(31,38,44):Color.rgb(242,245,247),muted=light?Color.DKGRAY:Color.LTGRAY;
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(bg);
  TextView title=new TextView(this);title.setText("Palabras y categorías");title.setTextSize(24);title.setTextColor(text);root.addView(title);
  TextView info=new TextView(this);info.setText("Hasta 50 reglas. Si una notificación contiene la palabra, ControlGastos intentará registrarla si encuentra un monto. En el Excel, la palabra asigna la categoría pero no excluye movimientos. No distingue mayúsculas ni tildes. Ej.: farmacia → Salud, grow → Jardín.");info.setTextSize(14);info.setTextColor(muted);info.setPadding(0,dp(8),0,dp(10));root.addView(info);
  ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);ArrayList<DetectionRules.Rule> saved=DetectionRules.load(this);
  for(int i=0;i<DetectionRules.MAX_RULES;i++){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);TextView n=new TextView(this);n.setText(String.valueOf(i+1));n.setTextColor(muted);n.setGravity(Gravity.CENTER);row.addView(n,new LinearLayout.LayoutParams(dp(34),dp(52)));EditText w=new EditText(this);w.setHint("Palabra o frase");w.setTextColor(text);w.setHintTextColor(muted);EditText c=new EditText(this);c.setHint("Categoría");c.setTextColor(text);c.setHintTextColor(muted);if(i<saved.size()){w.setText(saved.get(i).word);c.setText(saved.get(i).category);}words.add(w);cats.add(c);row.addView(w,new LinearLayout.LayoutParams(0,dp(52),3));row.addView(c,new LinearLayout.LayoutParams(0,dp(52),2));list.addView(row);}sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  Button save=new Button(this);save.setText("Guardar reglas");save.setOnClickListener(v->{ArrayList<String>w=new ArrayList<>(),c=new ArrayList<>();for(int i=0;i<words.size();i++){w.add(words.get(i).getText().toString());c.add(cats.get(i).getText().toString());}DetectionRules.save(this,w,c);Toast.makeText(this,"Reglas guardadas",Toast.LENGTH_SHORT).show();finish();});root.addView(save);setContentView(root);
 }
}
