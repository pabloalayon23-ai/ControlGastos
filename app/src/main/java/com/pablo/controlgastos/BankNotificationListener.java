package com.pablo.controlgastos;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.*;

public class BankNotificationListener extends NotificationListenerService {
    private static final Pattern MONEY=Pattern.compile("(?i)(U\\$S|USD|US\\$|\\$U|UYU|\\$)\\s*([0-9]{1,3}(?:[. ][0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)");
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(sbn==null || sbn.getNotification()==null) return;
        Bundle e=sbn.getNotification().extras;
        String title=s(e.getCharSequence(Notification.EXTRA_TITLE));
        String text=s(e.getCharSequence(Notification.EXTRA_TEXT));
        String big=s(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String full=(title+" "+text+" "+big).trim();
        if(full.isEmpty()) return;
        String norm=Normalizer.normalize(full,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        if(!(norm.contains("compra")||norm.contains("pago")||norm.contains("debito")||norm.contains("consumo")||norm.contains("transaccion")||norm.contains("transferencia"))) return;
        if(norm.contains("anulad")||norm.contains("rechaz")||norm.contains("devolucion")||norm.contains("recibiste")||norm.contains("acredit")) return;
        Matcher m=MONEY.matcher(full); if(!m.find()) return;
        String sym=m.group(1).toUpperCase(Locale.ROOT); String raw=m.group(2).replace(" ","");
        double amount=parseLatam(raw); if(amount<=0) return;
        String currency=(sym.contains("USD")||sym.contains("U$S")||sym.contains("US$"))?"USD":"UYU";
        ExpenseDb db=new ExpenseDb(this);
        db.addTx("GASTO",amount,currency,"Sin categoría",title.isEmpty()?"Notificación bancaria":title,full,System.currentTimeMillis(),sbn.getPackageName());
    }
    private static String s(CharSequence x){ return x==null?"":x.toString(); }
    private static double parseLatam(String x){
        try{
            int comma=x.lastIndexOf(','), dot=x.lastIndexOf('.');
            if(comma>=0 && dot>=0){ if(comma>dot) x=x.replace(".","").replace(',','.'); else x=x.replace(",",""); }
            else if(comma>=0) x=x.replace(".","").replace(',','.');
            else if(dot>=0){ int after=x.length()-dot-1; if(after==3) x=x.replace(".",""); }
            return Double.parseDouble(x);
        }catch(Exception ex){ return -1; }
    }
}
