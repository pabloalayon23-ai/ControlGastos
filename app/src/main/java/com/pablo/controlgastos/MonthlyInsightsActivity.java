package com.pablo.controlgastos;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MonthlyInsightsActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);TextView v=new TextView(this);v.setText("Análisis mensual");setContentView(v);}
}
