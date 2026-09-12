from pathlib import Path

# ---- ExpenseDb.java ----
p = Path('app/src/main/java/com/pablo/controlgastos/ExpenseDb.java')
s = p.read_text()
assert 'super(c,DB,null,3)' in s
s = s.replace('super(c,DB,null,3)', 'super(c,DB,null,4)', 1)

needle = '        db.execSQL("CREATE TABLE recurring(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, category TEXT, description TEXT, day INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1, last_ym TEXT)");'
assert needle in s
s = s.replace(needle, needle + '\n        createRecoveryTables(db);', 1)

upgrade_marker = '    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){'
assert upgrade_marker in s
recovery_tables = '''    private void createRecoveryTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS trash(id INTEGER PRIMARY KEY AUTOINCREMENT, original_id INTEGER, type TEXT, amount REAL, currency TEXT, category TEXT, description TEXT, original_text TEXT, ts INTEGER, source TEXT, fingerprint TEXT, original_ts INTEGER, deleted_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tx_history(id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT, tx_id INTEGER, type TEXT, amount REAL, currency TEXT, category TEXT, description TEXT, original_text TEXT, ts INTEGER, source TEXT, fingerprint TEXT, original_ts INTEGER, rules_snapshot TEXT, created_at INTEGER, undone INTEGER NOT NULL DEFAULT 0)");
    }

'''
s = s.replace(upgrade_marker, recovery_tables + upgrade_marker, 1)

salary_marker = '    public static boolean salaryMonthShiftEnabled(Context c)'
idx_salary = s.index(salary_marker)
pre = s[:idx_salary]
# Insert v4 upgrade immediately before the closing brace of onUpgrade.
close = pre.rfind('    }\n\n')
assert close >= 0
pre = pre[:close] + '        if(oldVersion<4)createRecoveryTables(db);\n    }\n\n' + pre[close+7:]
s = pre + s[idx_salary:]

old = '    public boolean updateTx(long id,String type,double amount,String currency,String category,String description,long ts){\n        ContentValues v=new ContentValues();'
assert old in s
s = s.replace(old, '    public boolean updateTx(long id,String type,double amount,String currency,String category,String description,long ts){\n        recordTxHistory(id,"EDIT",DetectionRules.rulesSnapshot(context));\n        ContentValues v=new ContentValues();', 1)

old = '    public void deleteTx(long id){ getWritableDatabase().delete("tx","id=?",new String[]{String.valueOf(id)}); }\n    public void deleteRecurring(long id){'
assert old in s
recovery_api = r'''    public void recordTxHistory(long id,String action,String rulesSnapshot){
        Cursor c=getReadableDatabase().rawQuery("SELECT id,type,amount,currency,category,description,original_text,ts,source,fingerprint,COALESCE(original_ts,ts) FROM tx WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});
        try{if(!c.moveToFirst())return;ContentValues v=new ContentValues();v.put("action",action);v.put("tx_id",c.getLong(0));v.put("type",c.getString(1));v.put("amount",c.getDouble(2));v.put("currency",c.getString(3));v.put("category",c.getString(4));v.put("description",c.getString(5));v.put("original_text",c.getString(6));v.put("ts",c.getLong(7));v.put("source",c.getString(8));v.put("fingerprint",c.getString(9));v.put("original_ts",c.getLong(10));v.put("rules_snapshot",rulesSnapshot==null?"":rulesSnapshot);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insert("tx_history",null,v);}finally{c.close();}
    }
    public void updateCategoryWithHistory(long id,String category,String rulesSnapshot){recordTxHistory(id,"CATEGORY",rulesSnapshot);ContentValues v=new ContentValues();v.put("category",category);getWritableDatabase().update("tx",v,"id=?",new String[]{String.valueOf(id)});}
    public void deleteTx(long id){
        SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT id,type,amount,currency,category,description,original_text,ts,source,fingerprint,COALESCE(original_ts,ts) FROM tx WHERE id=? LIMIT 1",new String[]{String.valueOf(id)});
        try{if(!c.moveToFirst())return;recordTxHistory(id,"DELETE",DetectionRules.rulesSnapshot(context));ContentValues v=new ContentValues();v.put("original_id",c.getLong(0));v.put("type",c.getString(1));v.put("amount",c.getDouble(2));v.put("currency",c.getString(3));v.put("category",c.getString(4));v.put("description",c.getString(5));v.put("original_text",c.getString(6));v.put("ts",c.getLong(7));v.put("source",c.getString(8));v.put("fingerprint",c.getString(9));v.put("original_ts",c.getLong(10));v.put("deleted_at",System.currentTimeMillis());db.insert("trash",null,v);db.delete("tx","id=?",new String[]{String.valueOf(id)});}finally{c.close();}
    }
    public Cursor trashItems(){return getReadableDatabase().rawQuery("SELECT id,original_id,type,amount,currency,category,description,ts,deleted_at FROM trash ORDER BY deleted_at DESC",null);}
    public boolean restoreTrash(long trashId){SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT original_id,type,amount,currency,category,description,original_text,ts,source,fingerprint,original_ts FROM trash WHERE id=? LIMIT 1",new String[]{String.valueOf(trashId)});try{if(!c.moveToFirst())return false;ContentValues v=new ContentValues();v.put("id",c.getLong(0));v.put("type",c.getString(1));v.put("amount",c.getDouble(2));v.put("currency",c.getString(3));v.put("category",c.getString(4));v.put("description",c.getString(5));v.put("original_text",c.getString(6));v.put("ts",c.getLong(7));v.put("source",c.getString(8));v.put("fingerprint",c.getString(9));v.put("original_ts",c.getLong(10));long r=db.insertWithOnConflict("tx",null,v,SQLiteDatabase.CONFLICT_ABORT);if(r!=-1){db.delete("trash","id=?",new String[]{String.valueOf(trashId)});return true;}return false;}catch(Exception e){return false;}finally{c.close();}}
    public void emptyTrash(){getWritableDatabase().delete("trash",null,null);}
    public boolean canUndo(){Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM tx_history WHERE undone=0 ORDER BY id DESC LIMIT 1",null);boolean ok=c.moveToFirst();c.close();return ok;}
    public String undoLastChange(){SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT id,action,tx_id,type,amount,currency,category,description,original_text,ts,source,fingerprint,original_ts,rules_snapshot FROM tx_history WHERE undone=0 ORDER BY id DESC LIMIT 1",null);try{if(!c.moveToFirst())return"No hay cambios para deshacer";long hid=c.getLong(0),txid=c.getLong(2);String action=c.getString(1),rules=c.getString(13);boolean ok=false;if("DELETE".equals(action)){Cursor t=db.rawQuery("SELECT id FROM trash WHERE original_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(txid)});try{if(t.moveToFirst())ok=restoreTrash(t.getLong(0));}finally{t.close();}}else{ContentValues v=new ContentValues();v.put("type",c.getString(3));v.put("amount",c.getDouble(4));v.put("currency",c.getString(5));v.put("category",c.getString(6));v.put("description",c.getString(7));v.put("original_text",c.getString(8));v.put("ts",c.getLong(9));v.put("source",c.getString(10));v.put("fingerprint",c.getString(11));v.put("original_ts",c.getLong(12));ok=db.update("tx",v,"id=?",new String[]{String.valueOf(txid)})==1;}if(ok){ContentValues u=new ContentValues();u.put("undone",1);db.update("tx_history",u,"id=?",new String[]{String.valueOf(hid)});if(rules!=null&&!rules.isEmpty())DetectionRules.restoreRulesSnapshot(context,rules);return"Cambio deshecho";}return"No se pudo deshacer el cambio";}finally{c.close();}}
    public void deleteRecurring(long id){'''
s = s.replace(old, recovery_api, 1)
p.write_text(s)

# ---- DetectionRules.java ----
p = Path('app/src/main/java/com/pablo/controlgastos/DetectionRules.java')
s = p.read_text()
marker = '    public static void assignMovementCategory(Context c,long id,String newCategory){'
assert marker in s
helpers = r'''    public static String rulesSnapshot(Context c){StringBuilder b=new StringBuilder();for(Rule r:load(c)){if(b.length()>0)b.append('\n');b.append(android.util.Base64.encodeToString(r.word.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP)).append('|').append(android.util.Base64.encodeToString(r.category.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP));}return b.toString();}
    public static void restoreRulesSnapshot(Context c,String snapshot){if(snapshot==null)return;ArrayList<String>w=new ArrayList<>(),cats=new ArrayList<>();for(String line:snapshot.split("\\n")){int k=line.indexOf('|');if(k<0)continue;try{w.add(new String(android.util.Base64.decode(line.substring(0,k),android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8));cats.add(new String(android.util.Base64.decode(line.substring(k+1),android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}}if(!w.isEmpty())saveInternal(c,w,cats);}
'''
s = s.replace(marker, helpers + marker, 1)
old = '        ContentValues v=new ContentValues();v.put("category",category);db.getWritableDatabase().update("tx",v,"id=?",new String[]{String.valueOf(id)});'
assert old in s
s = s.replace(old, '        db.updateCategoryWithHistory(id,category,rulesSnapshot(c));', 1)
p.write_text(s)

# ---- MainActivity.java ----
p = Path('app/src/main/java/com/pablo/controlgastos/MainActivity.java')
s = p.read_text()
old = 'Button appearance=btn("⚙ Apariencia y seguridad"),exp=btn("⬇ Exportar Excel compatible eBROU"),add=btn("＋ Agregar movimiento manual");addButton(root,appearance);addButton(root,exp);addButton(root,add);'
assert old in s
s = s.replace(old, 'Button appearance=btn("⚙ Apariencia y seguridad"),recovery=btn("🗑 Papelera y deshacer"),exp=btn("⬇ Exportar Excel compatible eBROU"),add=btn("＋ Agregar movimiento manual");addButton(root,appearance);addButton(root,recovery);addButton(root,exp);addButton(root,add);', 1)
old = 'appearance.setOnClickListener(v->showAppearanceSecurity());}'
assert old in s
s = s.replace(old, 'appearance.setOnClickListener(v->showAppearanceSecurity());recovery.setOnClickListener(v->showRecovery());}', 1)
marker = ' private void scanActiveNotifications(){'
assert marker in s
helper = r''' private void showRecovery(){LinearLayout box=form();Button undo=btn("↶ Deshacer último cambio");box.addView(undo);TextView h=tv("PAPELERA",13,MUTED);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(h);Cursor c=db.trashItems();boolean any=false;SimpleDateFormat df=new SimpleDateFormat("dd/MM/yyyy",Locale.US);while(c.moveToNext()){any=true;long trashId=c.getLong(0);String type=c.getString(2),cur=c.getString(4),cat=c.getString(5),desc=c.getString(6);double amount=c.getDouble(3);long ts=c.getLong(7);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(8),dp(6),dp(8),dp(6));row.addView(tv((desc==null?"Sin descripción":desc)+" · "+cur+" "+money.format(amount),15,TEXT));row.addView(tv(df.format(new Date(ts))+" · "+type+" · "+(cat==null?"Sin categoría":cat),12,MUTED));Button restore=btn("Restaurar");row.addView(restore);box.addView(row);restore.setOnClickListener(v->{if(db.restoreTrash(trashId)){Toast.makeText(this,"Movimiento restaurado",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,"No se pudo restaurar",Toast.LENGTH_LONG).show();});}c.close();if(!any)box.addView(tv("La papelera está vacía.",14,MUTED));Button empty=btn("Vaciar papelera");box.addView(empty);AlertDialog d=new AlertDialog.Builder(this).setTitle("Papelera y deshacer").setView(box).setNegativeButton("Cerrar",null).create();undo.setEnabled(db.canUndo());undo.setOnClickListener(v->{Toast.makeText(this,db.undoLastChange(),Toast.LENGTH_LONG).show();d.dismiss();});empty.setEnabled(any);empty.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Vaciar papelera").setMessage("Esta acción elimina definitivamente los elementos de la papelera.").setPositiveButton("Vaciar",(x,y)->{db.emptyTrash();d.dismiss();Toast.makeText(this,"Papelera vaciada",Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancelar",null).show());d.show();}
'''
s = s.replace(marker, helper + marker, 1)
p.write_text(s)

# ---- HomeActivity help ----
p = Path('app/src/main/java/com/pablo/controlgastos/HomeActivity.java')
s = p.read_text()
privacy = '   "PRIVACIDAD\\nLos movimientos, reglas y presupuestos se guardan localmente.'
if privacy in s:
    s = s.replace(privacy, '   "PAPELERA Y DESHACER\\nEn Ajustes → Papelera y deshacer podés restaurar movimientos eliminados. ‘Deshacer último cambio’ recupera el estado anterior de la última edición, cambio de categoría o eliminación. Si deshacés una categoría, también se recupera la lista blanca anterior. Vaciar la papelera elimina definitivamente esos elementos.",\n   "PRIVACIDAD\\nLos movimientos, reglas y presupuestos se guardan localmente.', 1)
p.write_text(s)

# ---- Version ----
p = Path('app/build.gradle')
s = p.read_text()
assert "versionName '1.8.22'" in s and 'versionCode 44' in s
s = s.replace('versionCode 44','versionCode 45',1).replace("versionName '1.8.22'","versionName '1.8.23'",1)
p.write_text(s)
print('Recovery patch applied')
