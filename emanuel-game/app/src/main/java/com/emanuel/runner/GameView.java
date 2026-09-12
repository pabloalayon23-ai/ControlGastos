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
 private final Random random=new Random();
 private final List<Enemy> enemies=new ArrayList<>();
 private final List<Bark> barks=new ArrayList<>();
 private final SharedPreferences prefs;
 private Bitmap atlas;
 private float density,groundY,playerX,playerY,playerW,playerH,velocityY,speed,worldOffset;
 private boolean onGround=true,started=false,gameOver=false,ponyActive=false,kicking=false;
 private long lastFrame=0,nextSpawn=0,ponyUntil=0,ponyCooldownUntil=0,invulnerableUntil=0,kickUntil=0,kickCooldownUntil=0,barkCooldownUntil=0;
 private int score=0,best=0,life=100;

 public GameView(Context c){
  super(c); density=getResources().getDisplayMetrics().density;
  prefs=c.getSharedPreferences("emanuel_runner",Context.MODE_PRIVATE); best=prefs.getInt("best",0);
  p.setTypeface(Typeface.DEFAULT_BOLD); atlas=loadAtlas(); setLayerType(View.LAYER_TYPE_SOFTWARE,null);
 }

 private Bitmap loadAtlas(){
  try{
   int[] ids={R.raw.atlas0,R.raw.atlas1,R.raw.atlas2,R.raw.atlas3,R.raw.atlas4,R.raw.atlas5};
   StringBuilder sb=new StringBuilder(15000);
   for(int id:ids){InputStream in=getResources().openRawResource(id); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[2048]; int n; while((n=in.read(buf))>0)out.write(buf,0,n); in.close(); sb.append(out.toString("UTF-8"));}
   byte[] data=Base64.decode(sb.toString(),Base64.DEFAULT); return BitmapFactory.decodeByteArray(data,0,data.length);
  }catch(Exception e){return null;}
 }

 protected void onSizeChanged(int w,int h,int ow,int oh){
  groundY=h*.79f; playerW=Math.max(70*density,h*.145f); playerH=playerW*1.12f; playerX=w*.17f;
  speed=Math.max(250*density,w*.27f); reset(false);
 }
 private void reset(boolean go){
  enemies.clear(); barks.clear(); score=0; life=100; velocityY=0; onGround=true; gameOver=false; started=go; ponyActive=false; kicking=false;
  ponyUntil=ponyCooldownUntil=invulnerableUntil=kickUntil=kickCooldownUntil=barkCooldownUntil=0;
  playerY=groundY-playerH; nextSpawn=System.currentTimeMillis()+1500; lastFrame=0; speed=Math.max(250*density,getWidth()*.27f); invalidate();
 }
 protected void onDraw(Canvas c){
  long now=System.currentTimeMillis(); if(lastFrame==0)lastFrame=now; float dt=Math.min(.033f,(now-lastFrame)/1000f); lastFrame=now;
  if(started&&!gameOver)update(dt,now); drawWorld(c,now); for(Enemy e:enemies)drawEnemy(c,e); for(Bark b:barks)drawBark(c,b);
  drawPlayer(c,now); drawHud(c,now);
  if(!started)message(c,"EMANUEL AVENTURAS","Tocá para empezar");
  if(gameOver)message(c,"SIN VIDA","Tocá para volver a jugar");
  postInvalidateOnAnimation();
 }

 private void update(float dt,long now){
  if(ponyActive&&now>=ponyUntil){ponyActive=false;ponyCooldownUntil=now+5000;}
  if(kicking&&now>=kickUntil)kicking=false;
  worldOffset+=speed*dt; speed+=3.6f*density*dt;
  velocityY+=1700*density*dt; playerY+=velocityY*dt;
  if(playerY>=groundY-playerH){playerY=groundY-playerH;velocityY=0;onGround=true;}
  if(now>=nextSpawn){Enemy e=new Enemy();e.type=random.nextInt(3);e.w=(54+random.nextInt(22))*density;e.h=e.w*1.15f;e.x=getWidth()+40*density;e.alive=true;enemies.add(e);nextSpawn=now+900+random.nextInt(850);}
  for(Bark b:barks)b.x+=760*density*dt;
  for(Iterator<Bark> bi=barks.iterator();bi.hasNext();)if(bi.next().x>getWidth()+120*density)bi.remove();
  for(Iterator<Enemy> it=enemies.iterator();it.hasNext();){
   Enemy e=it.next();e.x-=speed*dt;if(!e.alive||e.x+e.w<0){if(e.x+e.w<0)score+=10;it.remove();continue;}
   RectF eh=new RectF(e.x+e.w*.15f,groundY-e.h+e.h*.12f,e.x+e.w*.85f,groundY);
   boolean killed=false;for(Bark b:barks){RectF bh=new RectF(b.x,b.y-b.h/2,b.x+b.w,b.y+b.h/2);if(RectF.intersects(bh,eh)){killed=true;break;}}
   if(killed){e.alive=false;score+=35;continue;}
   if(kicking&&!ponyActive){RectF kh=new RectF(playerX+playerW*.55f,playerY+playerH*.22f,playerX+playerW*1.35f,playerY+playerH*.8f);if(RectF.intersects(kh,eh)){e.alive=false;score+=25;continue;}}
   if(RectF.intersects(playerHit(),eh)){
    float bottom=playerY+playerH*.92f;boolean stomp=!ponyActive&&velocityY>0&&bottom<groundY-e.h+e.h*.55f;
    if(stomp){e.alive=false;velocityY=-430*density;onGround=false;score+=30;}
    else if(now>=invulnerableUntil){life=Math.max(0,life-10);invulnerableUntil=now+1100;if(life<=0){gameOver=true;if(score>best){best=score;prefs.edit().putInt("best",best).apply();}}}
   }
  }
 }
 private RectF playerHit(){return ponyActive?new RectF(playerX,playerY+playerH*.15f,playerX+playerW*1.45f,playerY+playerH*.95f):new RectF(playerX+playerW*.2f,playerY+playerH*.08f,playerX+playerW*.8f,playerY+playerH*.94f);}
 private void jump(){if(onGround&&started&&!gameOver){velocityY=-(ponyActive?920:735)*density;onGround=false;}}
 private RectF ponyButton(){float r=43*density;return new RectF(getWidth()-2*r-18*density,getHeight()-2*r-18*density,getWidth()-18*density,getHeight()-18*density);}
 private RectF attackButton(){float r=43*density;return new RectF(getWidth()-4*r-32*density,getHeight()-2*r-18*density,getWidth()-2*r-32*density,getHeight()-18*density);}
 private void summon(long now){if(started&&!gameOver&&!ponyActive&&now>=ponyCooldownUntil){ponyActive=true;ponyUntil=now+10000;}}
 private void attack(long now){if(gameOver||!started)return;if(ponyActive){if(now>=barkCooldownUntil){Bark b=new Bark();b.x=playerX+playerW*1.15f;b.y=playerY+playerH*.55f;b.w=145*density;b.h=92*density;barks.add(b);barkCooldownUntil=now+850;}}else if(now>=kickCooldownUntil){kicking=true;kickUntil=now+280;kickCooldownUntil=now+520;}}
 public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){long now=System.currentTimeMillis();if(!started){reset(true);jump();}else if(gameOver)reset(true);else if(ponyButton().contains(e.getX(),e.getY()))summon(now);else if(attackButton().contains(e.getX(),e.getY()))attack(now);else jump();return true;}return true;}

 private void drawWorld(Canvas c,long now){
  LinearGradient sky=new LinearGradient(0,0,0,groundY,Color.rgb(102,177,224),Color.rgb(216,231,194),Shader.TileMode.CLAMP);p.setShader(sky);c.drawRect(0,0,getWidth(),groundY,p);p.setShader(null);
  p.setColor(Color.argb(90,255,236,174));c.drawCircle(getWidth()*.82f,getHeight()*.16f,getHeight()*.10f,p);
  drawMountains(c,worldOffset*.08f,groundY*.47f,Color.rgb(104,127,131),.28f);
  drawMountains(c,worldOffset*.14f,groundY*.61f,Color.rgb(69,105,92),.18f);
  float treeBase=groundY-8*density;float shift=-(worldOffset*.32f)%(180*density);
  for(int i=-1;i<9;i++){float x=shift+i*180*density;drawPine(c,x,treeBase,58*density,Color.rgb(35,85,55));drawPine(c,x+80*density,treeBase,45*density,Color.rgb(46,100,62));}
  p.setColor(Color.rgb(77,132,57));c.drawRect(0,groundY-13*density,getWidth(),groundY+11*density,p);
  LinearGradient soil=new LinearGradient(0,groundY,0,getHeight(),Color.rgb(103,76,48),Color.rgb(53,42,31),Shader.TileMode.CLAMP);p.setShader(soil);c.drawRect(0,groundY+10*density,getWidth(),getHeight(),p);p.setShader(null);
  p.setColor(Color.argb(55,255,255,255));for(int i=0;i<22;i++){float x=(i*89*density-(worldOffset*.55f)%89*density);c.drawOval(new RectF(x,groundY+26*density,x+28*density,groundY+33*density),p);}
 }
 private void drawMountains(Canvas c,float off,float base,int color,float amp){p.setColor(color);Path path=new Path();path.moveTo(-100,base);float span=260*density;float sh=-(off%span);for(int i=-1;i<8;i++){float x=sh+i*span;path.lineTo(x,base);path.lineTo(x+span*.5f,base-getHeight()*amp*(.7f+(i%3)*.12f));path.lineTo(x+span,base);}path.lineTo(getWidth()+100,groundY);path.lineTo(-100,groundY);path.close();c.drawPath(path,p);}
 private void drawPine(Canvas c,float x,float base,float s,int color){p.setColor(Color.rgb(81,60,42));c.drawRect(x-s*.08f,base-s*.4f,x+s*.08f,base,p);p.setColor(color);Path t=new Path();t.moveTo(x,base-s*1.8f);t.lineTo(x-s*.58f,base-s*.55f);t.lineTo(x+s*.58f,base-s*.55f);t.close();c.drawPath(t,p);t.reset();t.moveTo(x,base-s*1.35f);t.lineTo(x-s*.72f,base-s*.18f);t.lineTo(x+s*.72f,base-s*.18f);t.close();c.drawPath(t,p);}

 private void drawAtlasCell(Canvas c,int cell,RectF dst){if(atlas==null){p.setColor(Color.MAGENTA);c.drawRoundRect(dst,20,20,p);return;}int col=cell%4,row=cell/4;Rect src=new Rect(col*128,row*128,col*128+128,row*128+128);c.drawBitmap(atlas,src,dst,p);}
 private void drawPlayer(Canvas c,long now){if(now<invulnerableUntil&&((now/90)%2==0))return;int cell;if(ponyActive)cell=6;else if(kicking)cell=4;else if(!onGround)cell=3;else cell=((now/120)%2==0)?1:2;float scale=ponyActive?1.55f:1f;RectF dst=new RectF(playerX-(ponyActive?playerW*.08f:0),playerY-(ponyActive?playerH*.12f:0),playerX+playerW*scale,playerY+playerH);p.setShadowLayer(12*density,0,7*density,Color.argb(110,0,0,0));drawAtlasCell(c,cell,dst);p.clearShadowLayer();}
 private void drawEnemy(Canvas c,Enemy e){RectF dst=new RectF(e.x,groundY-e.h,e.x+e.w,groundY);p.setShadowLayer(8*density,0,5*density,Color.argb(100,0,0,0));drawAtlasCell(c,7,dst);p.clearShadowLayer();}
 private void drawBark(Canvas c,Bark b){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7*density);p.setColor(Color.rgb(70,185,255));p.setShadowLayer(14*density,0,0,Color.CYAN);for(int i=0;i<3;i++){float dx=i*28*density;c.drawArc(new RectF(b.x+dx,b.y-b.h*.55f,b.x+b.w+dx,b.y+b.h*.55f),-58,116,false,p);}p.clearShadowLayer();p.setStyle(Paint.Style.FILL);}

 private void drawHud(Canvas c,long now){
  p.setColor(Color.argb(175,12,22,30));c.drawRoundRect(new RectF(12*density,12*density,getWidth()*.38f,76*density),16*density,16*density,p);
  p.setColor(Color.WHITE);p.setTextSize(18*density);c.drawText("PUNTOS "+score,22*density,34*density,p);
  float lx=22*density,ly=44*density,lw=getWidth()*.28f,lh=18*density;p.setColor(Color.rgb(45,50,55));c.drawRoundRect(new RectF(lx,ly,lx+lw,ly+lh),9*density,9*density,p);
  LinearGradient lifeG=new LinearGradient(lx,0,lx+lw,0,Color.rgb(210,35,35),Color.rgb(255,95,45),Shader.TileMode.CLAMP);p.setShader(lifeG);c.drawRoundRect(new RectF(lx,ly,lx+lw*life/100f,ly+lh),9*density,9*density,p);p.setShader(null);
  p.setColor(Color.WHITE);p.setTextSize(13*density);c.drawText("VIDA "+life+" / 100",lx+7*density,ly+14*density,p);
  drawButton(c,ponyButton(),ponyActive?"PONY "+Math.max(0,(ponyUntil-now+999)/1000)+"s":now<ponyCooldownUntil?"PONY "+Math.max(0,(ponyCooldownUntil-now+999)/1000)+"s":"INVOCAR\nPONY",ponyActive?Color.rgb(235,168,36):now<ponyCooldownUntil?Color.rgb(85,90,100):Color.rgb(232,157,28));
  drawButton(c,attackButton(),ponyActive?(now<barkCooldownUntil?"LADRIDO\nRECARGA":"MEGA\nLADRIDO"):(now<kickCooldownUntil?"PATADA\nRECARGA":"PATADA"),ponyActive?Color.rgb(55,125,220):Color.rgb(205,80,42));
 }
 private void drawButton(Canvas c,RectF b,String text,int color){p.setShadowLayer(9*density,0,4*density,Color.argb(120,0,0,0));p.setColor(color);c.drawOval(b,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*density);p.setColor(Color.WHITE);c.drawOval(b,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(13*density);p.setColor(Color.WHITE);String[] lines=text.split("\\n");if(lines.length==1)c.drawText(lines[0],b.centerX(),b.centerY()+5*density,p);else{c.drawText(lines[0],b.centerX(),b.centerY()-2*density,p);c.drawText(lines[1],b.centerX(),b.centerY()+16*density,p);}p.setTextAlign(Paint.Align.LEFT);}
 private void message(Canvas c,String a,String b){p.setColor(Color.argb(200,11,24,34));RectF box=new RectF(getWidth()*.22f,getHeight()*.22f,getWidth()*.78f,getHeight()*.62f);p.setShadowLayer(20*density,0,8*density,Color.argb(160,0,0,0));c.drawRoundRect(box,28*density,28*density,p);p.clearShadowLayer();p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(getHeight()*.075f);c.drawText(a,getWidth()/2f,getHeight()*.39f,p);p.setTextSize(getHeight()*.04f);c.drawText(b,getWidth()/2f,getHeight()*.52f,p);p.setTextAlign(Paint.Align.LEFT);}
 private static class Enemy{float x,w,h;int type;boolean alive;} private static class Bark{float x,y,w,h;}
}
