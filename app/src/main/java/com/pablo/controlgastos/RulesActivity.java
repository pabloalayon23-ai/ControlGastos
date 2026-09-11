package com.pablo.controlgastos;

import android.app.*;import android.os.Bundle;import android.graphics.Color;import android.view.*;import android.widget.*;import java.util.*;

public class RulesActivity extends Activity{
 private static class Row{EditText word;AutoCompleteTextView cat;LinearLayout view;}
 private final ArrayList<Row> rows=new ArrayList<>();private LinearLayout list;private int TEXT,MUTED;
 @Override public void onCreate(Bundle b){ThemePrefs.applyBaseTheme(this);super.onCreate(b);build();}
 private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
 private void build(){boolean light=ThemePrefs.isLight(this);int bg=light?Color.rgb(246,248,249):Color.rgb(11,18,24);TEXT=light?Color.rgb(31,38,44):Color.rgb(242,245,247);MUTED=light?Color.DKGRAY:Color.LTGRAY;
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(bg);root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(14),i.getSystemWindowInsetTop()+dp(34),dp(14),i.getSystemWindowInsetBottom()+dp(14));return i;});root.requestApplyInsets();
  TextView title=new TextView(this);title.setText("Lista blanca · palabras y categorías");title.setTextSize(24);title.setTextColor(TEXT);root.addView(title);
  TextView info=new TextView(this);info.setText("No hay límite fijo de reglas. Cada palabra o frase asigna automáticamente una categoría a notificaciones y movimientos importados. Las categorías de esta lista son las que usan Gráficos y Presupuesto. 'Otros' queda reservado para lo que no coincide con ninguna regla.");info.setTextSize(14);info.setTextColor(MUTED);info.setPadding(0,dp(8),0,dp(10));root.addView(info);
  ScrollView sc=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  ArrayList<DetectionRules.Rule> saved=DetectionRules.load(this);for(DetectionRules.Rule r:saved)addRow(r.word,r.category);if(saved.isEmpty())addRow("","");
  LinearLayout actions=new LinearLayout(this);Button add=new Button(this);add.setText("+ Agregar regla");add.setAllCaps(false);Button save=new Button(this);save.setText("Guardar");save.setAllCaps(false);actions.addView(add,new LinearLayout.LayoutParams(0,dp(54),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(54),1);sp.setMargins(dp(8),0,0,0);actions.addView(save,sp);root.addView(actions);setContentView(root);
  add.setOnClickListener(v->addRow("",""));save.setOnClickListener(v->saveRules());
 }
 private void addRow(String word,String category){Row r=new Row();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);EditText w=new EditText(this);w.setHint("Palabra o frase");w.setText(word);w.setTextColor(TEXT);w.setHintTextColor(MUTED);AutoCompleteTextView c=new AutoCompleteTextView(this);c.setHint("Categoría");c.setText(category);c.setTextColor(TEXT);c.setHintTextColor(MUTED);c.setThreshold(0);refreshSuggestions(c);Button del=new Button(this);del.setText("×");del.setTextSize(20);row.addView(w,new LinearLayout.LayoutParams(0,dp(56),3));row.addView(c,new LinearLayout.LayoutParams(0,dp(56),2));row.addView(del,new LinearLayout.LayoutParams(dp(48),dp(48)));r.word=w;r.cat=c;r.view=row;rows.add(r);list.addView(row);del.setOnClickListener(v->{if(rows.size()==1){w.setText("");c.setText("");return;}rows.remove(r);list.removeView(row);});c.setOnClickListener(v->{refreshSuggestions(c);c.showDropDown();});}
 private void refreshSuggestions(AutoCompleteTextView c){ArrayList<String> names=new ArrayList<>(DetectionRules.categoryNames(this));names.remove("Otros");c.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,names));}
 private void saveRules(){ArrayList<String>w=new ArrayList<>(),cats=new ArrayList<>();for(Row r:rows){String x=r.word.getText().toString().trim();if(x.isEmpty())continue;w.add(x);String cat=r.cat.getText().toString().trim();cats.add(cat.isEmpty()?"Otros":cat);}DetectionRules.save(this,w,cats);DetectionRules.reclassifyExisting(this);Toast.makeText(this,"Reglas guardadas y movimientos reclasificados",Toast.LENGTH_LONG).show();finish();}
}
