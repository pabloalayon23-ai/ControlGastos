package com.pablo.controlgastos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class HourlyNotificationCheckReceiver extends BroadcastReceiver {
    private static final int REQ=1207;
    private static long interval(Context c){int m=c.getSharedPreferences("notification_check",Context.MODE_PRIVATE).getInt("minutes",60);return Math.max(1,m)*60L*1000L;}

    @Override public void onReceive(Context context, Intent intent){
        schedule(context);
        if(BankNotificationListener.isConnected()) BankNotificationListener.hourlyCheck();
        else BankNotificationListener.requestListenerReconnect();
    }

    public static void schedule(Context context){
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(am==null) return;
        Intent i=new Intent(context,HourlyNotificationCheckReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(context,REQ,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long every=interval(context);long first=SystemClock.elapsedRealtime()+every;
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,first,every,pi);
    }
}
