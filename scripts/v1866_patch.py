from pathlib import Path
import re

p=Path('app/src/main/java/com/pablo/controlgastos/MainActivity.java');s=p.read_text()
pat=r' private void exportExcel\(\)\{.*?\n @Override protected void onActivityResult'
repl=''' private void exportExcel(){
  LinearLayout box=form();Switch include=new Switch(this);include.setText("Incluir correo y tarjetas de crédito");include.setTextSize(16);include.setTextColor(TEXT);include.setChecked(false);include.setPadding(0,dp(8),0,dp(8));box.addView(include);box.addView(tv("Al activarlo se guardarán la dirección y configuración del correo, la credencial de aplicación codificada, las tarjetas de crédito, estados de cuenta, movimientos y cuotas. Guardá ese archivo en un lugar privado.",14,MUTED));
  new AlertDialog.Builder(this).setTitle("Exportar respaldo").setView(box).setPositiveButton("Continuar",(d,w)->{exportPrivate=include.isChecked();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.ms-excel");i.putExtra(Intent.EXTRA_TITLE,"ControlGastos_RESPALDO_"+new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date())+".xls");startActivityForResult(i,REQ_EXPORT);}).setNegativeButton("Cancelar",null).show();
 }
 @Override protected void onActivityResult'''
s,n=re.subn(pat,lambda m:repl,s,flags=re.S)
if n!=1: raise SystemExit(f'export patch failed: {n}')
p.write_text(s)

p=Path('app/src/main/java/com/pablo/controlgastos/AnalyticsActivity.java');s=p.read_text()
pat=r' private void showCategory\(\)\{.*?\n private void showCompare\(\)'
repl=''' private void showCategory(){active="cat";clear("Gastos por categoría",annual?"Distribución del año seleccionado":"Distribución del mes seleccionado");Calendar a=(Calendar)selected.clone();a.set(Calendar.DAY_OF_MONTH,1);a.set(Calendar.HOUR_OF_DAY,0);a.set(Calendar.MINUTE,0);a.set(Calendar.SECOND,0);a.set(Calendar.MILLISECOND,0);if(annual)a.set(Calendar.MONTH,0);Calendar b=(Calendar)a.clone();b.add(annual?Calendar.YEAR:Calendar.MONTH,1);LinkedHashSet<String> allowed=new LinkedHashSet<>(DetectionRules.categoryNames(this));HashMap<String,String> canonical=new HashMap<>();for(String x:allowed)canonical.put(DetectionRules.norm(x),x);LinkedHashMap<String,Double>sums=new LinkedHashMap<>();for(String x:allowed)sums.put(x,0d);if(!sums.containsKey("Otros"))sums.put("Otros",0d);Cursor q=db.getReadableDatabase().rawQuery("SELECT category,amount FROM tx WHERE type='GASTO' AND currency='UYU' AND ts>=? AND ts<?",new String[]{""+a.getTimeInMillis(),""+b.getTimeInMillis()});double total=0;while(q.moveToNext()){String raw=q.getString(0);double v=q.getDouble(1);String key=canonical.get(DetectionRules.norm(raw));if(key==null||key.trim().isEmpty())key="Otros";sums.put(key,sums.containsKey(key)?sums.get(key)+v:v);total+=v;}q.close();ArrayList<Pt>pts=new ArrayList<>();for(Map.Entry<String,Double>e:sums.entrySet())if(e.getValue()>0)pts.add(new Pt(e.getKey(),e.getValue()));Collections.sort(pts,(x,y)->Double.compare(y.v,x.v));TextView sum=tv("Total del período\\n$ "+money.format(total),20,TEXT);sum.setTypeface(null,Typeface.BOLD);sum.setBackground(bg(CARD,14));content.addView(sum);if(!pts.isEmpty())content.addView(new Donut(this,pts),new LinearLayout.LayoutParams(-1,dp(285)));for(Pt x:pts)content.addView(tv(x.n+"     $ "+money.format(x.v),15,TEXT));if(pts.isEmpty())content.addView(tv("No hay gastos para este período.",15,MUTED));}
 private void showCompare()'''
s,n=re.subn(pat,lambda m:repl,s,flags=re.S)
if n!=1: raise SystemExit(f'category patch failed: {n}')
marker=' class SignedLine extends View{'
donut=''' class Donut extends View{ArrayList<Pt>a;Paint p=new Paint(1);Donut(Context c,ArrayList<Pt>x){super(c);a=x;}protected void onDraw(Canvas c){super.onDraw(c);if(a==null||a.isEmpty())return;float size=Math.min(getWidth()-dp(32),getHeight()-dp(28));float left=(getWidth()-size)/2f,top=(getHeight()-size)/2f;RectF oval=new RectF(left,top,left+size,top+size);double total=0;for(Pt x:a)total+=x.v;if(total<=0)return;float start=-90f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(size*.22f);p.setStrokeCap(Paint.Cap.BUTT);for(Pt x:a){float sweep=(float)(360d*x.v/total);p.setColor(CategoryPrefs.color(AnalyticsActivity.this,x.n));c.drawArc(oval,start,sweep,false,p);start+=sweep;}p.setStyle(Paint.Style.FILL);p.setColor(TEXT);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(18));c.drawText("$ "+money.format(total),getWidth()/2f,getHeight()/2f+dp(6),p);p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);}}
'''
if marker not in s: raise SystemExit('SignedLine marker missing')
s=s.replace(marker,donut+marker,1);p.write_text(s)

g=Path('app/build.gradle');x=g.read_text();x=re.sub(r'versionCode\s+\d+','versionCode 88',x,1);x=re.sub(r"versionName\s+'[^']+'","versionName '1.8.66'",x,1);g.write_text(x)

m=Path('app/src/main/java/com/pablo/controlgastos/MainActivity.java').read_text();b=Path('app/src/main/java/com/pablo/controlgastos/BackupXlsManager.java').read_text();a=Path('app/src/main/java/com/pablo/controlgastos/AnalyticsActivity.java').read_text()
assert 'BackupXlsManager.appendBackup(this,wb,db,exportPrivate)' in m
assert 'if(includePrivate)appendPrivate' in b and '_CG_CORREO' in b and '_CG_TARJETAS' in b and '_CG_ESTADOS_TARJETA' in b and '_CG_MOV_TARJETA' in b
assert 'new Donut(this,pts)' in a and 'DetectionRules.categoryNames(this)' in a
assert 'Total del período\\n$ ' in a
print('v1.8.66 source checks OK')
