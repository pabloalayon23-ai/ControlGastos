package com.pablo.controlgastos;
import android.app.*;import android.content.*;import android.os.SystemClock;
public class EmailSyncReceiver extends BroadcastReceiver{
 private static final int REQ=1210;
 public void onReceive(Context c,Intent i){schedule(c);android.content.SharedPreferences p=c.getSharedPreferences(EmailSyncEngine.PREF,0);new Thread(()->{try{if(p.getBoolean("enable_cards",true))EmailSyncEngine.readCards(c,false);if(p.getBoolean("enable_expenses",false))EmailSyncEngine.readExpenses(c,true);}catch(Exception ignored){}}).start();}
 public static void schedule(Context c){android.content.SharedPreferences p=c.getSharedPreferences(EmailSyncEngine.PREF,0);int m=p.getInt("auto_sync_minutes",30);AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;Intent i=new Intent(c,EmailSyncReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(c,REQ,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);if(m<=0){am.cancel(pi);return;}long every=Math.max(1,m)*60L*1000L;am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+every,every,pi);}
}