package com.pablo.controlgastos;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import javax.mail.*;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;

public final class EmailSyncEngine {
    private EmailSyncEngine(){}

    public static final String PREF="card_email";
    private static final String PROCESSED="email_processed";

    public static class Result {
        public int scanned, imported, skipped, failed;
        public String detail="";
        @Override public String toString(){return "Revisados: "+scanned+"\nImportados/actualizados: "+imported+"\nOmitidos: "+skipped+(failed>0?"\nCon error: "+failed:"");}
    }

    public static Result readCards(Context c, boolean force) throws Exception {
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(!p.getBoolean("enable_cards",true))throw new Exception("La lectura de estados de tarjeta está desactivada.");
        return read(c,force,true);
    }

    public static Result readExpenses(Context c, boolean force) throws Exception {
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(!p.getBoolean("enable_expenses",false))throw new Exception("La lectura de gastos por correo está desactivada.");
        return read(c,force,false);
    }

    public static void testConnection(Context c) throws Exception {
        Store st=openStore(c);try{}finally{try{st.close();}catch(Exception ignored){}}
    }

    private static Result read(Context c, boolean force, boolean cards) throws Exception {
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        int months=Math.max(1,Math.min(24,p.getInt("months_back",6)));
        String whitelist=p.getString(cards?"card_whitelist":"expense_whitelist",cards?"sistarbanc|Estado de cuenta":"");
        if(whitelist.trim().isEmpty())throw new Exception("La lista blanca de "+(cards?"tarjetas":"gastos")+" está vacía.");

        Calendar cal=Calendar.getInstance();cal.add(Calendar.MONTH,-months);Date since=cal.getTime();
        Store st=openStore(c);Folder in=null;Result r=new Result();
        try{
            in=st.getFolder("INBOX");in.open(Folder.READ_ONLY);
            javax.mail.Message[] all;
            try{all=in.search(new ReceivedDateTerm(ComparisonTerm.GE,since));}
            catch(Exception e){all=in.getMessages();}
            int start=Math.max(0,all.length-600);
            for(int i=all.length-1;i>=start;i--){
                javax.mail.Message m=all[i];Date d=m.getReceivedDate();if(d==null)d=m.getSentDate();if(d!=null&&d.before(since))continue;
                String from=addresses(m.getFrom()),subject=safe(m.getSubject()),body=body(m),text=cleanLines(body);
                if(!matchesWhitelist(whitelist,from,subject,text))continue;
                r.scanned++;
                String key=(cards?"card:":"expense:")+messageKey(m,from,subject);
                if(!force&&wasProcessed(c,key)){r.skipped++;continue;}
                try{
                    boolean ok=cards?parseCard(c,text):parseExpense(c,m,from,subject,text);
                    if(ok){r.imported++;markProcessed(c,key);}else r.skipped++;
                }catch(Exception ex){r.failed++;r.detail=ex.getMessage()==null?ex.toString():ex.getMessage();}
            }
        } finally {
            if(in!=null&&in.isOpen())try{in.close(false);}catch(Exception ignored){}
            try{st.close();}catch(Exception ignored){}
        }
        return r;
    }

    private static Store openStore(Context c)throws Exception{
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String email=p.getString("email","").trim(),pass=SecretStore.get(c),host=p.getString("host","imap.gmail.com").trim();
        if(email.isEmpty()||pass.isEmpty())throw new Exception("Primero configurá el correo y la contraseña de aplicación.");
        Properties q=new Properties();q.put("mail.store.protocol","imaps");q.put("mail.imaps.ssl.enable","true");
        Session se=Session.getInstance(q);Store st=se.getStore("imaps");st.connect(host,email,pass);return st;
    }

    private static boolean matchesWhitelist(String rules,String from,String subject,String body){
        String f=norm(from),s=norm(subject),b=norm(body);
        for(String line:rules.split("\\r?\\n")){
            line=line.trim();if(line.isEmpty())continue;String[] x=line.split("\\|",-1);
            String rf=x.length>0?norm(x[0]):"",rs=x.length>1?norm(x[1]):"",rb=x.length>2?norm(x[2]):"";
            if((rf.isEmpty()||f.contains(rf))&&(rs.isEmpty()||s.contains(rs))&&(rb.isEmpty()||b.contains(rb)))return true;
        }
        return false;
    }

    private static boolean parseCard(Context c,String lines){
        String flat=lines.replaceAll("\\s+"," ");
        Matcher card=Pattern.compile("(?i)TARJETA\\s*(?:No)?[: ]+\\s*([0-9]{4,6})\\*+([0-9]{4})").matcher(flat);
        if(!card.find())return false;
        CreditCardDb db=new CreditCardDb(c);
        long cid=db.card("BROU MasterCard",card.group(2));
        String close=find(flat,"(?i)Fecha de cierre[: ]+([0-9/]+)"),due=find(flat,"(?i)Vencimiento actual[: ]+([0-9/]+)");
        String totalRaw=find(flat,"(?i)SALDO CONTADO\\s+(?:UYU\\s*)?([0-9.,]+)");double total=num(totalRaw);
        long sid=db.statement(cid,close,due,total,"UYU");db.clearItems(sid);
        Matcher row=Pattern.compile("([0-3]?\\d/[01]?\\d/\\d{2,4})\\s+(.{3,100}?)\\s+([0-9][0-9.,]*)\\s+(?:\\.00|0[.,]00)",Pattern.CASE_INSENSITIVE).matcher(flat);
        int count=0;
        while(row.find()){
            String desc=row.group(2).trim();if(desc.matches("(?i).*(SALDO ANTERIOR|PAGOS|TOTAL TARJETA).*"))continue;
            String inst=find(desc,"(?i)(CUOTA\\s*0*\\d{1,2}/0*\\d{1,2})");
            db.item(sid,row.group(1),desc,inst,num(row.group(3)),"UYU");count++;
        }
        db.close();return count>0||total>0;
    }

    private static boolean parseExpense(Context c,javax.mail.Message m,String from,String subject,String lines)throws Exception{
        ExpenseParsed x=parseExpenseFields(m,subject,lines);if(x==null||x.amount<=0)return false;
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        ExpenseDb db=new ExpenseDb(c);
        try{
            if(isExpenseDuplicate(c,db,x,p))return true;
            String mid=messageId(m);String fp="EMAIL:"+(mid.isEmpty()?Integer.toHexString((from+subject+x.ts+x.amount).hashCode()):mid);
            if(db.hasFingerprint(fp))return true;
            ContentValues v=new ContentValues();v.put("type","GASTO");v.put("amount",x.amount);v.put("currency",x.currency);v.put("category",DetectionRules.categoryFor(c,x.description+"\n"+lines,"Otros"));v.put("description",x.description);v.put("original_text",lines);v.put("ts",x.ts);v.put("original_ts",x.ts);v.put("source","correo");v.put("fingerprint",fp);
            return db.getWritableDatabase().insertWithOnConflict("tx",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
        }finally{db.close();}
    }

    private static class ExpenseParsed{double amount;String currency="UYU",description="Gasto por correo";long ts;}

    private static ExpenseParsed parseExpenseFields(javax.mail.Message m,String subject,String lines){
        ExpenseParsed x=new ExpenseParsed();x.ts=dateOf(m);
        Matcher a=Pattern.compile("(?im)(?:importe|monto|total|valor)\\s*[:=-]?\\s*(UYU|USD|U\\$S|\\$)?\\s*([0-9][0-9.,]*)").matcher(lines);
        if(!a.find()){
            a=Pattern.compile("(?im)(UYU|USD|U\\$S|\\$)\\s*([0-9][0-9.,]*)").matcher(lines);if(!a.find())return null;
        }
        String cur=a.group(1);x.currency=(cur!=null&&cur.toUpperCase(Locale.ROOT).contains("USD"))||"U$S".equalsIgnoreCase(cur)?"USD":"UYU";x.amount=num(a.group(2));
        Matcher d=Pattern.compile("(?im)^(?:comercio|establecimiento|descripci[oó]n|concepto)\\s*:\\s*(.+)$").matcher(lines);
        if(d.find())x.description=trimField(d.group(1));else if(subject!=null&&!subject.trim().isEmpty())x.description=subject.trim();
        Matcher dt=Pattern.compile("(?im)(?:fecha(?: de (?:compra|operaci[oó]n))?)\\s*:\\s*(\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{2}:\\d{2}(?::\\d{2})?)?|\\d{1,2}/\\d{1,2}/\\d{2,4}(?:\\s+\\d{2}:\\d{2}(?::\\d{2})?)?)").matcher(lines);
        if(dt.find()){long parsed=parseDate(dt.group(1));if(parsed>0)x.ts=parsed;}
        return x;
    }

    private static String trimField(String s){if(s==null)return"";String z=s.trim();int cut=z.indexOf("  ");if(cut>3)z=z.substring(0,cut).trim();return z.length()>90?z.substring(0,90).trim():z;}

    private static boolean isExpenseDuplicate(Context c,ExpenseDb db,ExpenseParsed x,SharedPreferences p){
        boolean date=p.getBoolean("expense_cmp_date",true),amount=p.getBoolean("expense_cmp_amount",true),currency=p.getBoolean("expense_cmp_currency",true),exact=p.getBoolean("expense_cmp_exact_name",false),similar=p.getBoolean("expense_cmp_similar_name",true);
        if(!date&&!amount&&!currency&&!exact&&!similar)return false;
        long lo=x.ts-3L*24*3600*1000,hi=x.ts+3L*24*3600*1000;
        Cursor q=db.getReadableDatabase().rawQuery("SELECT amount,currency,description,COALESCE(original_ts,ts) FROM tx WHERE type='GASTO' AND COALESCE(original_ts,ts)>=? AND COALESCE(original_ts,ts)<=? ORDER BY id DESC LIMIT 500",new String[]{String.valueOf(lo),String.valueOf(hi)});
        try{while(q.moveToNext()){
            if(date&&!sameDay(x.ts,q.getLong(3)))continue;
            if(amount&&Math.abs(x.amount-q.getDouble(0))>.01)continue;
            if(currency&&!x.currency.equalsIgnoreCase(safe(q.getString(1))))continue;
            String other=safe(q.getString(2));if(exact&&!norm(x.description).equals(norm(other)))continue;
            if(similar&&!ExpenseDb.merchantMatches(x.description,other))continue;
            return true;
        }}finally{q.close();}
        return false;
    }

    private static boolean sameDay(long a,long b){Calendar x=Calendar.getInstance(),y=Calendar.getInstance();x.setTimeInMillis(a);y.setTimeInMillis(b);return x.get(Calendar.YEAR)==y.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==y.get(Calendar.DAY_OF_YEAR);}
    private static long parseDate(String s){String[] p={"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm","yyyy-MM-dd'T'HH:mm:ss","dd/MM/yyyy HH:mm:ss","dd/MM/yyyy HH:mm","dd/MM/yyyy","dd/MM/yy HH:mm","dd/MM/yy"};for(String f:p)try{SimpleDateFormat d=new SimpleDateFormat(f,Locale.US);d.setLenient(false);return d.parse(s.trim()).getTime();}catch(Exception ignored){}return 0;}
    private static long dateOf(javax.mail.Message m){Date d=null;try{d=m.getSentDate();if(d==null)d=m.getReceivedDate();}catch(Exception ignored){}return d==null?System.currentTimeMillis():d.getTime();}
    private static String messageId(javax.mail.Message m){try{String[] h=m.getHeader("Message-ID");return h!=null&&h.length>0?h[0].replaceAll("[^A-Za-z0-9@._-]",""):"";}catch(Exception e){return"";}}
    private static String messageKey(javax.mail.Message m,String from,String subject){String id=messageId(m);return id.isEmpty()?Integer.toHexString((from+subject+dateOf(m)).hashCode()):id;}
    private static boolean wasProcessed(Context c,String key){return c.getSharedPreferences(PROCESSED,Context.MODE_PRIVATE).getBoolean(key,false);}
    private static void markProcessed(Context c,String key){c.getSharedPreferences(PROCESSED,Context.MODE_PRIVATE).edit().putBoolean(key,true).apply();}

    private static String addresses(Address[] a){if(a==null)return"";StringBuilder s=new StringBuilder();for(Address x:a){if(s.length()>0)s.append(' ');s.append(x.toString());}return s.toString();}
    private static String body(Part p)throws Exception{Object o=p.getContent();if(o instanceof String)return(String)o;if(o instanceof Multipart){Multipart m=(Multipart)o;StringBuilder s=new StringBuilder();for(int i=0;i<m.getCount();i++)s.append(body(m.getBodyPart(i))).append('\n');return s.toString();}return"";}
    private static String cleanLines(String h){if(h==null)return"";return h.replaceAll("(?is)<style.*?</style>"," ").replaceAll("(?i)<br\\s*/?>","\n").replaceAll("(?i)</(?:p|div|tr|li|h[1-6])>","\n").replaceAll("(?is)<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&quot;","\"").replaceAll("[ \\t]+"," ").replaceAll("\\n\\s+","\n").trim();}
    private static String find(String s,String r){Matcher m=Pattern.compile(r).matcher(s);return m.find()?m.group(1):"";}
    private static double num(String x){try{String q=safe(x).trim();int c=q.lastIndexOf(','),d=q.lastIndexOf('.');if(c>=0&&d>=0)q=d>c?q.replace(",",""):q.replace(".","").replace(',','.');else if(c>=0)q=(q.length()-c-1==2)?q.replace(',','.'):q.replace(",","");return Double.parseDouble(q);}catch(Exception e){return 0;}}
    private static String norm(String s){return java.text.Normalizer.normalize(safe(s),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static String safe(String s){return s==null?"":s;}
}
