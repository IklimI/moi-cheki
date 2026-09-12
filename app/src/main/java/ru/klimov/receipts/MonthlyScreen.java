package ru.klimov.receipts;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Monthly dashboard: dates only distinguish receipt stripes, never split the report. */
final class MonthlyScreen {
 final MainActivity a;
 final String[] tabs={"Чеки","Авансы","Настройки"};
 final String[] statuses={"Собираю","Отправлен","Принят"};
 final ExecutorService images=Executors.newSingleThreadExecutor();
 int generation;
 MonthlyScreen(MainActivity a){this.a=a;}
 void close(){images.shutdownNow();}
 int dp(int v){return a.dp(v);}
 int muted(){return a.prefs.getInt("theme",1)==3?0xff627187:0xffa3afbf;}
 LinearLayout column(){LinearLayout l=new LinearLayout(a);l.setOrientation(1);return l;}
 TextView text(String s,int size,boolean bold){TextView v=new TextView(a);v.setText(s);v.setTextColor(a.color(2));v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 GradientDrawable bg(int color,int radius,boolean border){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(border)g.setStroke(dp(1),a.prefs.getInt("theme",1)==3?0xffd5dfed:0xff33465b);return g;}
 void gap(LinearLayout l,int h){l.addView(new View(a),new LinearLayout.LayoutParams(1,dp(h)));}
 LinearLayout card(){LinearLayout l=column();l.setPadding(dp(16),dp(16),dp(16),dp(16));l.setBackground(bg(a.color(1),18,true));l.setElevation(dp(3));if(Build.VERSION.SDK_INT>=28){l.setOutlineAmbientShadowColor(a.color(3));l.setOutlineSpotShadowColor(a.color(3));}return l;}
 TextView action(String title,boolean primary,Runnable click){TextView b=text(title,15,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(52));b.setPadding(dp(8),dp(12),dp(8),dp(12));b.setBackground(bg(primary?a.color(3):a.color(1),14,!primary));b.setTextColor(primary?Color.WHITE:a.color(3));b.setOnClickListener(v->click.run());b.setFocusable(true);b.setContentDescription(title);return b;}
 void render(){
  final int gen=++generation;
  LinearLayout root=column();root.setBackgroundColor(a.color(0));
  a.getWindow().setStatusBarColor(a.color(0));a.getWindow().setNavigationBarColor(a.color(0));a.getWindow().getDecorView().setSystemUiVisibility(a.prefs.getInt("theme",1)==3?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
  ScrollView sc=new ScrollView(a);sc.setFillViewport(true);LinearLayout content=column();content.setPadding(dp(18),dp(18),dp(18),dp(20));sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout title=new LinearLayout(a);title.setGravity(Gravity.CENTER_VERTICAL);Icon logo=new Icon(a,0,a.color(3));title.addView(logo,new LinearLayout.LayoutParams(dp(30),dp(36)));TextView name=text(a.screenTab==0?"Мои чеки":tabs[a.screenTab],27,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.leftMargin=dp(10);title.addView(name,np);content.addView(title);gap(content,18);
  if(a.screenTab!=2){
   LinearLayout nav=new LinearLayout(a);nav.setGravity(Gravity.CENTER_VERTICAL);
   nav.addView(action("‹",false,()->{a.month=a.month.minusMonths(1);render();}),new LinearLayout.LayoutParams(dp(44),dp(48)));
   String month=a.month.format(DateTimeFormatter.ofPattern("LLLL yyyy",a.ru));month=month.substring(0,1).toUpperCase(a.ru)+month.substring(1);
   TextView monthName=text(month,19,true);monthName.setGravity(Gravity.CENTER);nav.addView(monthName,new LinearLayout.LayoutParams(0,-2,1));nav.addView(action("›",false,()->{a.month=a.month.plusMonths(1);render();}),new LinearLayout.LayoutParams(dp(44),dp(48)));content.addView(nav);gap(content,16);
   long received=a.sum("advances"),spent=a.sum("receipts");
   ArrayList<Receipt> receipts=new ArrayList<>();try(Cursor c=a.db.rawQuery("SELECT id,amount,note,photo,created FROM receipts WHERE month=? ORDER BY substr(created,7,4)||substr(created,4,2)||substr(created,1,2) DESC,id DESC",new String[]{a.month.toString()})){while(c.moveToNext())receipts.add(new Receipt(c));}
   LinearLayout summary=card();summary.addView(text("Итоги месяца",17,true));gap(summary,14);
   // Stacked at narrow widths / large fonts so amounts stay readable.
   LinearLayout values=new LinearLayout(a);boolean wide=a.getResources().getConfiguration().screenWidthDp>=380&&a.getResources().getConfiguration().fontScale<=1.15f;values.setOrientation(wide?0:1);
   LinearLayout balance=column();balance.addView(text("Остаток",15,false));TextView money=text(a.money(received-spent),29,true);money.setAutoSizeTextTypeUniformWithConfiguration(18,29,1,android.util.TypedValue.COMPLEX_UNIT_SP);balance.addView(money,new LinearLayout.LayoutParams(-1,dp(45)));values.addView(balance,wide?new LinearLayout.LayoutParams(0,-2,1.1f):new LinearLayout.LayoutParams(-1,-2));
   LinearLayout totals=column();TextView receivedLabel=text("Получено",13,false);receivedLabel.setTextColor(muted());totals.addView(receivedLabel);totals.addView(text(a.money(received),17,true));gap(totals,8);TextView e=text("Потрачено",13,false);e.setTextColor(muted());totals.addView(e);totals.addView(text(a.money(spent),17,true));if(!wide)gap(balance,10);values.addView(totals,wide?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));summary.addView(values);gap(summary,12);View line=new View(a);line.setBackgroundColor(muted());summary.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));gap(summary,10);TextView count=text("Чеков за месяц: "+receipts.size(),13,false);count.setTextColor(muted());summary.addView(count);content.addView(summary);gap(content,14);
   if(a.screenTab==0){
    LinearLayout status=new LinearLayout(a);status.setPadding(dp(3),dp(3),dp(3),dp(3));status.setBackground(bg(a.color(1),16,true));int selected=a.prefs.getInt("status_"+a.month,0);
    for(int i=0;i<3;i++){final int n=i;TextView item=action(statuses[i],selected==i,()->{a.prefs.edit().putInt("status_"+a.month,n).apply();render();});item.setTextSize(12);if(i!=selected){item.setBackgroundColor(Color.TRANSPARENT);item.setTextColor(muted());}status.addView(item,new LinearLayout.LayoutParams(0,dp(46),1));}content.addView(status);gap(content,20);
    content.addView(text("Все чеки за "+a.month.format(DateTimeFormatter.ofPattern("LLLL",a.ru)),20,true));gap(content,12);
    if(receipts.isEmpty()){LinearLayout empty=card();empty.addView(text("В этом месяце пока нет чеков",16,true));gap(empty,8);TextView hint=text("Сфотографируйте чек или выберите фото из галереи.",14,false);hint.setTextColor(muted());empty.addView(hint);content.addView(empty);}
    Map<String,Integer> dates=new LinkedHashMap<>();int[] accents={a.color(3),0xff19b6b4,0xffa18ad8,0xffd99b45,0xffd4829d};
    for(Receipt r:receipts){if(!dates.containsKey(r.date))dates.put(r.date,dates.size());receipt(content,r,accents[dates.get(r.date)%accents.length],gen);gap(content,9);}
    gap(content,12);content.addView(action("＋  Добавить чек",true,()->new AlertDialog.Builder(a).setTitle("Добавить чек").setItems(new String[]{"Сфотографировать","Выбрать из галереи"},(d,w)->{if(w==0)a.camera();else a.gallery();}).setNegativeButton("Отмена",null).show()));gap(content,10);content.addView(action("Отправить отчёт за месяц",false,()->a.share()));
   }else{
    content.addView(text("Авансы за месяц",20,true));gap(content,12);try(Cursor c=a.db.rawQuery("SELECT id,amount,note FROM advances WHERE month=? ORDER BY id DESC",new String[]{a.month.toString()})){if(c.getCount()==0)content.addView(text("Авансов пока нет",15,false));while(c.moveToNext()){long id=c.getLong(0),amount=c.getLong(1);String note=c.getString(2);LinearLayout row=card();row.addView(text(a.money(amount),21,true));if(!note.isEmpty()){gap(row,5);row.addView(text(note,14,false));}row.setOnClickListener(v->a.actions("advances",id,amount,note,null));content.addView(row);gap(content,10);}}
    gap(content,12);content.addView(action("＋  Получен аванс",true,()->{String m=a.month.toString();a.editor("Получен аванс",0,"",(amount,note)->{a.db.execSQL("INSERT INTO advances(month,amount,note) VALUES(?,?,?)",new Object[]{m,amount,note});a.saveBackup();render();});}));
   }
  }else{
   LinearLayout settings=card();settings.addView(text("Оформление",19,true));gap(settings,8);settings.addView(text("Тема: "+new String[]{"Синий неон","Графит","Полночь","Светлая"}[a.prefs.getInt("theme",1)],15,false));gap(settings,12);settings.addView(action("Выбрать цветовую схему",false,()->a.appearance()));gap(settings,20);settings.addView(text("Отчёты",19,true));gap(settings,8);settings.addView(action("Почта получателя",false,()->a.email()));gap(settings,14);TextView info=text("Версия 0.3 • Статус отчёта отмечается вручную. Цветные полоски помогают найти чеки с одинаковой датой добавления.",13,false);info.setTextColor(muted());settings.addView(info);content.addView(settings);
  }
  LinearLayout bottom=new LinearLayout(a);bottom.setBackgroundColor(a.color(1));bottom.setPadding(dp(8),dp(7),dp(8),dp(7));
  for(int i=0;i<3;i++){final int n=i;LinearLayout tab=column();tab.setGravity(Gravity.CENTER);int tint=i==a.screenTab?a.color(3):muted();Icon icon=new Icon(a,i,tint);tab.addView(icon,new LinearLayout.LayoutParams(dp(25),dp(26)));gap(tab,4);TextView label=text(tabs[i],12,i==a.screenTab);label.setTextColor(tint);label.setGravity(Gravity.CENTER);tab.addView(label,new LinearLayout.LayoutParams(-1,-2));tab.setOnClickListener(v->{a.screenTab=n;render();});tab.setContentDescription(tabs[i]);tab.setFocusable(true);bottom.addView(tab,new LinearLayout.LayoutParams(0,dp(58),1));}
  root.addView(bottom);a.setContentView(root);
 }
 void receipt(LinearLayout list,Receipt r,int accent,int gen){
  LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(bg(a.color(1),14,true));row.setClipToOutline(true);row.addView(new View(a){{setBackgroundColor(accent);}},new LinearLayout.LayoutParams(dp(4),-1));
  ImageView image=new ImageView(a);image.setImageResource(ru.klimov.receipts.R.drawable.ic_receipt);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(bg(a.color(0),8,false));image.setClipToOutline(true);image.setContentDescription("Фотография чека");LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(48),dp(54));ip.setMargins(dp(10),dp(12),dp(10),dp(12));row.addView(image,ip);
  LinearLayout details=column();TextView note=text(r.note.isEmpty()?"Чек без описания":r.note,15,true);note.setMaxLines(2);note.setEllipsize(TextUtils.TruncateAt.END);details.addView(note);gap(details,5);TextView date=text(r.date,12,false);date.setTextColor(muted());details.addView(date);row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
  TextView amount=text(a.money(r.amount),16,true);amount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);amount.setMaxLines(1);amount.setAutoSizeTextTypeUniformWithConfiguration(10,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(105),dp(50));ap.setMargins(dp(5),0,dp(12),0);row.addView(amount,ap);row.setOnClickListener(v->a.viewPhoto(r.photo));row.setOnLongClickListener(v->{a.actions("receipts",r.id,r.amount,r.note,r.photo);return true;});row.setContentDescription((r.note.isEmpty()?"Чек":r.note)+", "+r.date+", "+a.money(r.amount)+". Удерживайте для изменения.");list.addView(row,new LinearLayout.LayoutParams(-1,-2));
  images.execute(()->{if(gen!=generation)return;try{
   BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=a.getContentResolver().openInputStream(a.photoUri(r.photo))){BitmapFactory.decodeStream(in,null,o);}o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>256)o.inSampleSize*=2;o.inJustDecodeBounds=false;Bitmap b;try(InputStream in=a.getContentResolver().openInputStream(a.photoUri(r.photo))){b=BitmapFactory.decodeStream(in,null,o);}if(b!=null)a.runOnUiThread(()->{if(gen==generation&&!a.isDestroyed())image.setImageBitmap(b);else b.recycle();});
  }catch(Exception ignored){}});
 }
 static class Receipt {long id,amount;String note,photo,date;Receipt(Cursor c){id=c.getLong(0);amount=c.getLong(1);note=c.getString(2);photo=c.getString(3);date=c.getString(4);}}
 static class Icon extends View {
  final Paint p=new Paint(3);final int kind,tint;
  Icon(Context c,int k,int t){super(c);kind=k;tint=t;}
  protected void onDraw(Canvas canvas){canvas.save();canvas.scale(getWidth()/32f,getHeight()/32f);p.setColor(tint);p.setStrokeWidth(2.2f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
   if(kind==0){Path path=new Path();path.moveTo(7,3);path.lineTo(25,3);path.lineTo(25,29);path.lineTo(22,26);path.lineTo(19,29);path.lineTo(16,26);path.lineTo(13,29);path.lineTo(10,26);path.lineTo(7,29);path.close();canvas.drawPath(path,p);canvas.drawLine(11,10,21,10,p);canvas.drawLine(11,15,21,15,p);canvas.drawLine(11,20,18,20,p);}
   else if(kind==1){canvas.drawRoundRect(4,8,28,26,3,3,p);canvas.drawLine(5,8,24,4,p);canvas.drawRoundRect(20,14,29,21,2,2,p);}
   else {canvas.drawCircle(16,16,8,p);canvas.drawCircle(16,16,3,p);for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,16,16);canvas.drawLine(16,3,16,7,p);canvas.restore();}}
   canvas.restore();
  }
 }
}
