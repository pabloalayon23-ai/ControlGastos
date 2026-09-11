package com.pablo.controlgastos;

import android.app.Notification;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

public class BankNotificationListener extends NotificationListenerService {
    private static volatile BankNotificationListener activeInstance;
    private static final Pattern MONEY = Pattern.compile("(?i)(U\\$S|USD|US\\$|\\$U|UYU|\\$)\\s*([0-9]+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:[. ][0-9]{3})+(?:,[0-9]{1,2})?)");
    private static final Pattern BROU_IMPORTE = Pattern.compile("(?i)Importe\\s*:\\s*(UYU|USD|U\\$S|US\\$|\\$U|\\$)\\s*([0-9][0-9., ]*)");
    private static final Pattern BROU_COMERCIO = Pattern.compile("(?i)Comercio\\s*:\\s*([^\\n\\r]+)");
    private static final Pattern BROU_APROBADO = Pattern.compile("(?i)Aprobado\\s*:\\s*([0-9A-Za-z-]+)");
    private static final Pattern BROU_FECHA = Pattern.compile("(?i)Fecha\\s*:\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern PAGANZA_SERVICE = Pattern.compile("(?i)(?:servicio|empresa|comercio|beneficiario|concepto)\\s*:\\s*([^\\n\\r]+)");
    private static final Pattern PAGANZA_REFERENCE = Pattern.compile("(?i)(?:referencia|operaci[oó]n|comprobante|transacci[oó]n)\\s*:?\\s*([0-9A-Za-z-]+)");

    @Override public void onListenerConnected(){
        super.onListenerConnected();
        activeInstance=this;
        HourlyNotificationCheckReceiver.schedule(this);
        scanActiveNotifications();
    }

    @Override public void onListenerDisconnected(){
        activeInstance=null;
        super.onListenerDisconnected();
    }

    @Override public void onDestroy(){
        activeInstance=null;
        super.onDestroy();
    }

    public static void hourlyCheck(){
        BankNotificationListener instance=activeInstance;
        if(instance!=null) instance.scanActiveNotifications();
    }

    public static boolean isConnected(){ return activeInstance!=null; }

    public static void requestListenerReconnect(){
        try{ requestRebind(new ComponentName("com.pablo.controlgastos","com.pablo.controlgastos.BankNotificationListener")); }
        catch(Exception ignored){}
    }

    private void scanActiveNotifications(){
        try{
            StatusBarNotification[] active=getActiveNotifications();
            if(active==null) return;
            for(StatusBarNotification sbn:active) processNotification(sbn);
        }catch(Exception ignored){}
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn){ processNotification(sbn); }

    private void processNotification(StatusBarNotification sbn){
        if(sbn==null || sbn.getNotification()==null) return;
        Bundle e=sbn.getNotification().extras;
        String title=s(e.getCharSequence(Notification.EXTRA_TITLE));
        String full=collectNotificationText(e);
        if(full.isEmpty()) return;

        String pkg=sbn.getPackageName()==null?"":sbn.getPackageName();
        String norm=Normalizer.normalize(full,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        String titleNorm=Normalizer.normalize(title,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        String pkgNorm=pkg.toLowerCase(Locale.ROOT);

        boolean fromPaganzaApp=pkgNorm.contains("paganza") || titleNorm.contains("paganza");
        boolean mentionsPaganza=norm.contains("paganza");

        // Paganza es la fuente primaria de sus propios pagos. Si una app bancaria
        // informa un débito cuyo texto dice PAGANZA, se ignora para no duplicar
        // el mismo gasto que ya registra la notificación de Paganza.
        if(fromPaganzaApp){
            processPaganza(sbn,title,full,norm);
            return;
        }
        if(mentionsPaganza) return;

        processBankNotification(sbn,title,full,norm);
    }

    private void processBankNotification(StatusBarNotification sbn,String title,String full,String norm){
        if(!(norm.contains("compra")||norm.contains("pago")||norm.contains("debito")||norm.contains("consumo")||norm.contains("transaccion")||norm.contains("transferencia"))) return;
        if(norm.contains("anulad")||norm.contains("rechaz")||norm.contains("devolucion")||norm.contains("recibiste")||norm.contains("acredit")) return;

        String sym=null, raw=null;
        Matcher bi=BROU_IMPORTE.matcher(full);
        if(bi.find()){ sym=bi.group(1); raw=bi.group(2).trim(); }
        else {
            Matcher m=MONEY.matcher(full); if(!m.find()) return;
            sym=m.group(1); raw=m.group(2).trim();
        }
        saveExpense(sbn,title,full,sym,raw,false);
    }

    private void processPaganza(StatusBarNotification sbn,String title,String full,String norm){
        if(!(norm.contains("pago")||norm.contains("pagaste")||norm.contains("pagado")||norm.contains("abonado")||norm.contains("debito"))) return;
        if(norm.contains("rechaz")||norm.contains("fall")||norm.contains("pendiente")||norm.contains("venc")||norm.contains("recordatorio")||norm.contains("devolucion")||norm.contains("anulad")) return;

        Matcher m=MONEY.matcher(full);
        if(!m.find()) return;
        String sym=m.group(1), raw=m.group(2).trim();
        double amount=parseLatam(raw.replace(" ","")); if(amount<=0) return;
        String upper=sym.toUpperCase(Locale.ROOT);
        String currency=(upper.contains("USD")||upper.contains("U$S")||upper.contains("US$"))?"USD":"UYU";

        String description=title.isEmpty()?"Pago Paganza":title;
        Matcher sm=PAGANZA_SERVICE.matcher(full);
        if(sm.find()) description=sm.group(1).trim();
        if(description.toLowerCase(Locale.ROOT).contains("paganza") && description.length()<18) description="Pago Paganza";

        long ts=System.currentTimeMillis();
        String unique="";
        Matcher rm=PAGANZA_REFERENCE.matcher(full);
        if(rm.find()) unique="PAGANZA_REF:"+rm.group(1).trim();
        if(unique.isEmpty()) unique="PAGANZA:"+currency+":"+amount+":"+normalizeKey(description)+":"+(ts/60000L);

        ExpenseDb db=new ExpenseDb(this);
        if(db.existsOriginal(unique)) return;
        db.addTx("GASTO",amount,currency,classifyPaganza(description),description,unique,ts,"notificacion:Paganza");
    }

    private void saveExpense(StatusBarNotification sbn,String title,String full,String sym,String raw,boolean paganza){
        double amount=parseLatam(raw.replace(" ","")); if(amount<=0) return;
        String upper=sym.toUpperCase(Locale.ROOT);
        String currency=(upper.contains("USD")||upper.contains("U$S")||upper.contains("US$"))?"USD":"UYU";

        String description=title.isEmpty()?"Notificación bancaria":title;
        Matcher cm=BROU_COMERCIO.matcher(full);
        if(cm.find()) description=cm.group(1).trim();

        long ts=System.currentTimeMillis();
        Matcher fm=BROU_FECHA.matcher(full);
        if(fm.find()){
            try { ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).parse(fm.group(1)).getTime(); } catch(Exception ignored){}
        }

        String unique="";
        Matcher am=BROU_APROBADO.matcher(full);
        if(am.find()) unique="BROU_APROBADO:"+am.group(1).trim();
        if(unique.isEmpty()) unique="NOTIF:"+currency+":"+amount+":"+description+":"+ts;

        ExpenseDb db=new ExpenseDb(this);
        if(db.existsOriginal(unique)) return;
        db.addTx("GASTO",amount,currency,"Sin categoría",description,unique,ts,"notificacion:"+sbn.getPackageName());
    }

    private static String classifyPaganza(String description){
        String s=normalizeKey(description);
        if(s.contains("ute")||s.contains("ose")||s.contains("antel")||s.contains("movistar")||s.contains("claro")||s.contains("internet")) return "Servicios";
        if(s.contains("contribucion")||s.contains("tribut")||s.contains("patente")||s.contains("sucive")||s.contains("intendencia")) return "Impuestos";
        if(s.contains("colegio")||s.contains("escuela")||s.contains("universidad")) return "Educación";
        if(s.contains("seguro")||s.contains("bse")) return "Seguros";
        return "Pagos";
    }

    private static String normalizeKey(String s){
        if(s==null) return "";
        return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }

    private static String collectNotificationText(Bundle e){
        LinkedHashSet<String> parts=new LinkedHashSet<>();
        add(parts,e.getCharSequence(Notification.EXTRA_TITLE));
        add(parts,e.getCharSequence(Notification.EXTRA_TEXT));
        add(parts,e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(parts,e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(parts,e.getCharSequence(Notification.EXTRA_INFO_TEXT));
        add(parts,e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if(lines!=null) for(CharSequence line:lines) add(parts,line);
        StringBuilder b=new StringBuilder();
        for(String p:parts){ if(b.length()>0)b.append('\n'); b.append(p); }
        return b.toString().trim();
    }
    private static void add(Set<String> set,CharSequence x){ if(x!=null){String v=x.toString().trim(); if(!v.isEmpty())set.add(v);} }
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
