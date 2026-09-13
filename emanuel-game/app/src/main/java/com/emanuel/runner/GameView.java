package com.emanuel.runner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import java.io.*;
import java.util.*;

public class GameView extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final Random rnd=new Random();
 private final List<Enemy> enemies=new ArrayList<>();
 private final List<Bark> barks=new ArrayList<>();
 private final List<Coin> coins=new ArrayList<>();
 private final SharedPreferences prefs;
 private Bitmap bg,sprites;
 private float density,groundY,playerX,playerY,playerW,playerH,velocityY,speed,worldOffset;
 private boolean onGround=true,started=false,gameOver=false,ponyActive=false,kicking=false;
 private long lastFrame=0,nextSpawn=0,nextCoin=0,ponyUntil=0,ponyCooldownUntil=0,invulnerableUntil=0,kickUntil=0,kickCooldownUntil=0,barkCooldownUntil=0;
 private int score=0,best=0,life=100;

 public GameView(Context c){
  super(c); density=getResources().getDisplayMetrics().density;
  prefs=c.getSharedPreferences("emanuel_runner",Context.MODE_PRIVATE); best=prefs.getInt("best",0);
  p.setTypeface(Typeface.DEFAULT_BOLD); bg=loadBg(); sprites=loadOne(R.raw.sprites_v4); setLayerType(View.LAYER_TYPE_SOFTWARE,null);
 }
 private Bitmap decodeText(int... ids){
  try{StringBuilder sb=new StringBuilder(); byte[] buf=new byte[4096];
   for(int id:ids){InputStream in=getResources().openRawResource(id);ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=in.read(buf))>0)out.write(buf,0,n);in.close();sb.append(out.toString("UTF-8").trim());}
   byte[] d=Base64.decode(sb.toString(),Base64.DEFAULT); return BitmapFactory.decodeByteArray(d,0,d.length);
  }catch(Exception e){return null;}
 }
 private Bitmap loadBg(){return decodeText(R.raw.bg0,R.raw.bg1);}
 private Bitmap loadOne(int id){return decodeText(id);}

 protected void onSizeChanged(int w,int h,int ow,int oh){groundY=h*.78f;playerW=Math.max(82*density,h*.17f);playerH=playerW*.95f;playerX=w*.20f;speed=Math.max(245*density,w*.26f);reset(false);}
 private void reset(boolean go){enemies.clear();barks.clear();coins.clear();score=0;life=100;velocityY=0;onGround=true;gameOver=false;started=go;ponyActive=false;kicking=false;worldOffset=0;ponyUntil=ponyCooldownUntil=invulnerableUntil=kickUntil=kickCooldownUntil=barkCooldownUntil=0;playerY=groundY-playerH;long n=System.currentTimeMillis();nextSpawn=n+1700;nextCoin=n+1000;lastFrame=0;speed=Math.max(245*density,getWidth()*.26f);invalidate();}
 protected void onDraw(Canvas c){long now=System.currentTimeMillis();if(lastFrame==0)lastFrame=now;float dt=Math.min(.033f,(now-lastFrame)/1000f);lastFrame=now;if(started&&!gameOver)update(dt,now);drawWorld(c);for(Coin q:coins)drawCoin(c,q);for(Enemy e:enemies)drawEnemy(c,e);for(Bark b:barks)drawBark(c,b);drawPlayer(c,now);drawHud(c,now);if(!started)message(c,"EMANUEL AVENTURAS","Tocá SALTAR para empezar");if(gameOver)message(c,"SIN VIDA","Tocá para volver a jugar");postInvalidateOnAnimation();}

 private void update(float dt,long now){
  if(ponyActive&&now>=ponyUntil){ponyActive=false;ponyCooldownUntil=now+5000;}
  if(kicking&&now>=kickUntil)kicking=false;
  worldOffset+=speed*dt; speed+=2.7f*density*dt;
  velocityY+=1700*density*dt; playerY+=velocityY*dt;if(playerY>=groundY-playerH){playerY=groundY-playerH;velocityY=0;onGround=true;}
  if(now>=nextSpawn){Enemy e=new Enemy();e.w=(58+rnd.nextInt(25))*density;e.h=e.w*.9f;e.x=getWidth()+40*density;e.alive=true;enemies.add(e);nextSpawn=now+1050+rnd.nextInt(850);}
  if(now>=nextCoin){Coin q=new Coin();q.r=15*density;q.x=getWidth()+50*density;q.y=groundY-(75+rnd.nextInt(90))*density;coins.add(q);nextCoin=now+800+rnd.nextInt(900);}
  for(Bark b:barks)b.x+=780*density*dt;
  for(Iterator<Bark> it=barks.iterator();it.hasNext();)if(it.next().x>getWidth()+160*density)it.remove();
  for(Iterator<Coin> it=coins.iterator();it.hasNext();){Coin q=it.next();q.x-=speed*dt;if(q.x< -40*density){it.remove();continue;}RectF qr=new RectF(q.x-q.r,q.y-q.r,q.x+q.r,q.y+q.r);if(RectF.intersects(playerHit(),qr)){score+=5;it.remove();}}
  for(Iterator<Enemy> it=enemies.iterator();it.hasNext();){Enemy e=it.next();e.x-=speed*dt;if(!e.alive||e.x+e.w<0){if(e.x+e.w<0)score+=10;it.remove();continue;}RectF eh=new RectF(e.x+e.w*.12f,groundY-e.h+e.h*.12f,e.x+e.w*.88f,groundY);
   boolean killed=false;for(Bark b:barks){if(RectF.intersects(new RectF(b.x,b.y-b.h/2,b.x+b.w,b.y+b.h/2),eh)){killed=true;break;}}if(killed){e.alive=false;score+=35;continue;}
   if(kicking&&!ponyActive){RectF kh=new RectF(playerX+playerW*.55f,playerY+playerH*.25f,playerX+playerW*1.35f,playerY+playerH*.82f);if(RectF.intersects(kh,eh)){e.alive=false;score+=25;continue;}}
   if(RectF.intersects(playerHit(),eh)){float bottom=playerY+playerH*.92f;boolean stomp=!ponyActive&&velocityY>0&&bottom<groundY-e.h+e.h*.55f;if(stomp){e.alive=false;velocityY=-430*density;onGround=false;score+=30;}else if(now>=invulnerableUntil){life=Math.max(0,life-10);invulnerableUntil=now+1100;if(life<=0){gameOver=true;if(score>best){best=score;prefs.edit().putInt("best",best).apply();}}}}
  }
 }
 private RectF playerHit(){return ponyActive?new RectF(playerX-playerW*.12f,playerY+playerH*.2f,playerX+playerW*1.45f,playerY+playerH*.96f):new RectF(playerX+playerW*.2f,playerY+playerH*.08f,playerX+playerW*.82f,playerY+playerH*.94f);}
 private void jump(){if(onGround&&started&&!gameOver){velocityY=-(ponyActive?920:735)*density;onGround=false;}}
 private RectF ponyButton(){float r=45*density;return new RectF(getWidth()-2*r-15*density,getHeight()-2*r-18*density,getWidth()-15*density,getHeight()-18*density);}
 private RectF attackButton(){float r=45*density;return new RectF(getWidth()-4*r-28*density,getHeight()-2*r-18*density,getWidth()-2*r-28*density,getHeight()-18*density);}
 private RectF jumpButton(){float r=45*density;return new RectF(getWidth()-6*r-41*density,getHeight()-2*r-18*density,getWidth()-4*r-41*density,getHeight()-18*density);}
 private void summon(long now){if(started&&!gameOver&&!ponyActive&&now>=ponyCooldownUntil){ponyActive=true;ponyUntil=now+10000;}}
 private void attack(long now){if(gameOver||!started)return;if(ponyActive){if(now>=barkCooldownUntil){Bark b=new Bark();b.x=playerX+playerW*1.2f;b.y=playerY+playerH*.55f;b.w=150*density;b.h=90*density;barks.add(b);barkCooldownUntil=now+850;}}else if(now>=kickCooldownUntil){kicking=true;kickUntil=now+280;kickCooldownUntil=now+520;}}
 public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){long now=System.currentTimeMillis();if(gameOver){reset(true);return true;}if(!started){reset(true);jump();return true;}if(ponyButton().contains(e.getX(),e.getY()))summon(now);else if(attackButton().contains(e.getX(),e.getY()))attack(now);else if(jumpButton().contains(e.getX(),e.getY()))jump();else jump();return true;}return true;}

 private void drawWorld(Canvas c){
  c.drawColor(Color.rgb(45,76,58));
  if(bg!=null){Rect src=new Rect(Math.min(250,bg.getWidth()/3),0,bg.getWidth(),Math.min(bg.getHeight(),245));RectF dst=new RectF(0,0,getWidth(),groundY+18*density);c.drawBitmap(bg,src,dst,p);}else{LinearGradient g=new LinearGradient(0,0,0,groundY,Color.rgb(84,164,220),Color.rgb(62,110,75),Shader.TileMode.CLAMP);p.setShader(g);c.drawRect(0,0,getWidth(),groundY,p);p.setShader(null);}
  LinearGradient shade=new LinearGradient(0,groundY*.55f,0,groundY,Color.TRANSPARENT,Color.argb(95,10,25,10),Shader.TileMode.CLAMP);p.setShader(shade);c.drawRect(0,groundY*.55f,getWidth(),groundY,p);p.setShader(null);
  p.setShadowLayer(12*density,0,-3*density,Color.argb(120,0,0,0));p.setColor(Color.rgb(74,128,48));c.drawRect(0,groundY-14*density,getWidth(),groundY+10*density,p);p.clearShadowLayer();
  LinearGradient soil=new LinearGradient(0,groundY,0,getHeight(),Color.rgb(111,73,42),Color.rgb(45,31,22),Shader.TileMode.CLAMP);p.setShader(soil);c.drawRect(0,groundY+8*density,getWidth(),getHeight(),p);p.setShader(null);
  p.setColor(Color.argb(110,188,155,104));float step=95*density;float sh=-(worldOffset*.7f)%step;for(int i=-1;i<18;i++){float x=sh+i*step;c.drawOval(new RectF(x,groundY+26*density,x+36*density,groundY+34*density),p);}
 }
 private Rect cell(int n){int cw=sprites==null?1:sprites.getWidth()/2;int ch=sprites==null?1:sprites.getHeight()/2;int col=n%2,row=n/2;return new Rect(col*cw,row*ch,(col+1)*cw,(row+1)*ch);}
 private void drawCell(Canvas c,int n,RectF dst){if(sprites!=null){p.setShadowLayer(10*density,0,6*density,Color.argb(120,0,0,0));c.drawBitmap(sprites,cell(n),dst,p);p.clearShadowLayer();}else{p.setColor(Color.argb(220,55,70,60));c.drawRoundRect(dst,18*density,18*density,p);}}
 private void drawPlayer(Canvas c,long now){if(now<invulnerableUntil&&((now/90)%2==0))return;float bob=onGround?(float)Math.sin(now/105.0)*3*density:0;if(ponyActive){RectF dog=new RectF(playerX-playerW*.18f,playerY+playerH*.48f+bob,playerX+playerW*1.55f,playerY+playerH*1.08f+bob);drawCell(c,1,dog);RectF kid=new RectF(playerX+playerW*.12f,playerY-playerH*.10f+bob,playerX+playerW*.98f,playerY+playerH*.72f+bob);drawCell(c,0,kid);}else{c.save();float angle=kicking?-9f:(!onGround?-4f:0f);c.rotate(angle,playerX+playerW*.5f,playerY+playerH*.5f);RectF kid=new RectF(playerX,playerY+bob,playerX+playerW,playerY+playerH+bob);drawCell(c,0,kid);c.restore();}}
 private void drawEnemy(Canvas c,Enemy e){RectF d=new RectF(e.x,groundY-e.h,e.x+e.w,groundY);drawCell(c,2,d);}
 private void drawCoin(Canvas c,Coin q){RectF d=new RectF(q.x-q.r,q.y-q.r,q.x+q.r,q.y+q.r);drawCell(c,3,d);}
 private void drawBark(Canvas c,Bark b){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7*density);p.setColor(Color.rgb(70,180,255));p.setShadowLayer(15*density,0,0,Color.CYAN);for(int i=0;i<3;i++){float dx=i*25*density;c.drawArc(new RectF(b.x+dx,b.y-b.h*.55f,b.x+b.w+dx,b.y+b.h*.55f),-58,116,false,p);}p.clearShadowLayer();p.setStyle(Paint.Style.FILL);}

 private void drawHud(Canvas c,long now){
  p.setColor(Color.argb(205,53,35,23));RectF panel=new RectF(16*density,14*density,getWidth()*.36f,82*density);p.setShadowLayer(12*density,0,5*density,Color.argb(140,0,0,0));c.drawRoundRect(panel,18*density,18*density,p);p.clearShadowLayer();
  p.setColor(Color.rgb(255,225,155));p.setTextSize(19*density);c.drawText("PUNTOS  "+score,28*density,39*density,p);
  float lx=28*density,ly=49*density,lw=getWidth()*.24f,lh=18*density;p.setColor(Color.rgb(55,43,38));c.drawRoundRect(new RectF(lx,ly,lx+lw,ly+lh),9*density,9*density,p);p.setColor(life>20?Color.rgb(237,52,42):Color.rgb(180,35,35));c.drawRoundRect(new RectF(lx,ly,lx+lw*life/100f,ly+lh),9*density,9*density,p);p.setColor(Color.WHITE);p.setTextSize(13*density);c.drawText("VIDA "+life+" / 100",lx+7*density,ly+14*density,p);
  drawButton(c,jumpButton(),"SALTAR",Color.rgb(55,65,72));drawButton(c,attackButton(),ponyActive?(now<barkCooldownUntil?"LADRIDO\nRECARGA":"MEGA\nLADRIDO"):(now<kickCooldownUntil?"PATADA\nRECARGA":"PATADA"),ponyActive?Color.rgb(48,122,219):Color.rgb(65,70,72));drawButton(c,ponyButton(),ponyActive?"PONY\n"+Math.max(0,(ponyUntil-now+999)/1000)+" s":now<ponyCooldownUntil?"PONY\n"+Math.max(0,(ponyCooldownUntil-now+999)/1000)+" s":"PONY",ponyActive?Color.rgb(240,165,35):now<ponyCooldownUntil?Color.rgb(95,95,100):Color.rgb(240,165,35));
 }
 private void drawButton(Canvas c,RectF b,String text,int color){p.setShadowLayer(10*density,0,4*density,Color.argb(140,0,0,0));p.setColor(color);c.drawOval(b,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*density);p.setColor(Color.WHITE);c.drawOval(b,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(13*density);p.setColor(Color.WHITE);String[] s=text.split("\\n");if(s.length==1)c.drawText(s[0],b.centerX(),b.centerY()+5*density,p);else{c.drawText(s[0],b.centerX(),b.centerY()-2*density,p);c.drawText(s[1],b.centerX(),b.centerY()+16*density,p);}p.setTextAlign(Paint.Align.LEFT);}
 private void message(Canvas c,String a,String b){p.setColor(Color.argb(210,30,25,20));RectF box=new RectF(getWidth()*.22f,getHeight()*.22f,getWidth()*.78f,getHeight()*.60f);c.drawRoundRect(box,26*density,26*density,p);p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(255,218,125));p.setTextSize(getHeight()*.072f);c.drawText(a,getWidth()/2f,getHeight()*.38f,p);p.setColor(Color.WHITE);p.setTextSize(getHeight()*.038f);c.drawText(b,getWidth()/2f,getHeight()*.50f,p);p.setTextAlign(Paint.Align.LEFT);}
 private static class Enemy{float x,w,h;boolean alive;} private static class Bark{float x,y,w,h;} private static class Coin{float x,y,r;}
}
