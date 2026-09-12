package ru.klimov.receipts;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.MediaStore.Downloads;
import android.content.ContentValues;
import android.content.ContentUris;
import android.os.Environment;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;

public class MainActivity extends Activity {
 SQLiteDatabase db; LinearLayout page, rows; TextView heading, totals;
 YearMonth month=YearMonth.now(); String pending, pendingMonth; android.content.SharedPreferences prefs;
 ArrayDeque<Uri> galleryQueue=new ArrayDeque<>(); File galleryFile; String galleryMonth;
 int[][] palettes={{0xff071120,0xff102442,0xffeaf3ff,0xff087fff},{0xff171a20,0xff252b34,0xfff2f4f8,0xff498af5},{0xff09182c,0xff123153,0xffe8f6ff,0xff00b8d4},{0xfff0f4fa,0xffffffff,0xff17263b,0xff2067c8}};
 int color(int n){return palettes[Math.max(0,Math.min(3,prefs.getInt("theme",1)))][n];}
 void appearance(){new AlertDialog.Builder(this).setTitle("Оформление").setSingleChoiceItems(new String[]{"Синий неон","Графит","Полночь","Светлая"},prefs.getInt("theme",1),(d,w)->{prefs.edit().putInt("theme",w).commit();d.dismiss();recreate();}).setNegativeButton("Закрыть",null).show();}
 void status(){String key="status_"+month;new AlertDialog.Builder(this).setTitle("Статус отчёта — "+month).setSingleChoiceItems(new String[]{"Собираю","Отправлен","Принят"},prefs.getInt(key,0),(d,w)->{prefs.edit().putInt(key,w).apply();d.dismiss();refresh();}).setNegativeButton("Отмена",null).show();}
 final Locale ru=new Locale("ru","RU");
 interface Save { void save(long amount,String note); }
 @Override public void onCreate(Bundle state){
  prefs=getSharedPreferences("settings",0); setTheme(prefs.getInt("theme",1)==3?android.R.style.Theme_Material_Light_NoActionBar:android.R.style.Theme_Material_NoActionBar); super.onCreate(state);
  db=openOrCreateDatabase("receipts.db",MODE_PRIVATE,null);
  db.execSQL("CREATE TABLE IF NOT EXISTS receipts(id INTEGER PRIMARY KEY, month TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL, photo TEXT NOT NULL, created TEXT NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS advances(id INTEGER PRIMARY KEY, month TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL)");
  restoreBackup();
  if(state!=null) month=YearMonth.parse(state.getString("month",month.toString()));
  pending=prefs.getString("pending",null); pendingMonth=prefs.getString("pendingMonth",null);
  build();
  if(state==null && pending!=null && new File(pending).length()>0) new AlertDialog.Builder(this).setTitle("Несохранённый снимок").setMessage("Добавить чек к отчёту?").setPositiveButton("Добавить",(d,w)->finishPhoto()).setNegativeButton("Удалить",(d,w)->discard()).setCancelable(false).show();
 }
 @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("month",month.toString());}
 int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
 TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color(2));v.setPadding(0,dp(8),0,dp(8));return v;}
 Button button(String text,Runnable fn){Button b=new Button(this);b.setText(text);b.setTextColor(color(2));b.setAllCaps(false);b.setPadding(dp(12),dp(8),dp(12),dp(8));GradientDrawable bg=new GradientDrawable();bg.setColor(color(1));bg.setCornerRadius(dp(14));bg.setStroke(dp(1),color(3));b.setBackground(bg);b.setElevation(dp(5));if(Build.VERSION.SDK_INT>=28){b.setOutlineAmbientShadowColor(color(3));b.setOutlineSpotShadowColor(color(3));}LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,dp(5),0,dp(5));b.setLayoutParams(bp);b.setOnClickListener(v->fn.run());return b;}
 void build(){
  ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(20),dp(20),dp(20),dp(30));page.setBackgroundColor(color(0));scroll.addView(page);setContentView(scroll);
  page.addView(label("Мои чеки",30));page.addView(label("Авансы и расходы — всё под рукой",14));
  LinearLayout nav=new LinearLayout(this);nav.addView(button("‹",()->{month=month.minusMonths(1);refresh();}),new LinearLayout.LayoutParams(dp(48),dp(48)));heading=label("",19);heading.setGravity(Gravity.CENTER);nav.addView(heading,new LinearLayout.LayoutParams(0,-2,1));nav.addView(button("›",()->{month=month.plusMonths(1);refresh();}),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(nav);
  totals=label("",20);page.addView(totals);
  page.addView(button("＋ Сфотографировать чек",()->camera()));
  page.addView(button("＋ Загрузить чек из галереи",()->gallery()));
  page.addView(button("＋ Получен аванс",()->editor("Получен аванс",0,"",(amount,note)->{db.execSQL("INSERT INTO advances(month,amount,note) VALUES(?,?,?)",new Object[]{month.toString(),amount,note});saveBackup();refresh();})));
  page.addView(button("Отправить чеки за месяц",()->share()));
  page.addView(button("Настройки",()->new AlertDialog.Builder(this).setTitle("Настройки").setItems(new String[]{"Оформление","Почта получателя"},(d,w)->{if(w==0)appearance();else email();}).show()));
  page.addView(button("Статус отчёта",()->status()));
  page.addView(label("Нажми на чек, чтобы открыть фото. Удерживай запись для изменения или удаления.",13));
  rows=new LinearLayout(this);rows.setOrientation(1);page.addView(rows);refresh();
 }
 String money(long value){return String.format(ru,"%,.2f ₽",value/100.0);}
 long sum(String table){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM "+table+" WHERE month=?",new String[]{month.toString()})){c.moveToFirst();return c.getLong(0);}}
 void refresh(){
  heading.setText(month.format(DateTimeFormatter.ofPattern("LLLL yyyy",ru)));long advance=sum("advances"),expense=sum("receipts");totals.setText("Получено: "+money(advance)+"\nПо чекам: "+money(expense)+"\nОстаток: "+money(advance-expense));rows.removeAllViews();rows.addView(label("Статус: "+new String[]{"Собираю","Отправлен","Принят"}[prefs.getInt("status_"+month,0)],16));
  try(Cursor c=db.rawQuery("SELECT id,amount,note,photo,created FROM receipts WHERE month=? ORDER BY id DESC",new String[]{month.toString()})){
   if(c.getCount()==0)rows.addView(label("В этом месяце пока нет чеков",17));
   while(c.moveToNext()){long id=c.getLong(0),amount=c.getLong(1);String note=c.getString(2),photo=c.getString(3),date=c.getString(4);TextView row=label("🧾 "+money(amount)+"  ·  "+date+"\n"+(note.isEmpty()?"Чек без описания":note),17);row.setOnClickListener(v->viewPhoto(photo));row.setOnLongClickListener(v->{actions("receipts",id,amount,note,photo);return true;});rows.addView(row);}
  }
  rows.addView(label("Авансы",21));
  try(Cursor c=db.rawQuery("SELECT id,amount,note FROM advances WHERE month=? ORDER BY id DESC",new String[]{month.toString()})){while(c.moveToNext()){long id=c.getLong(0),amount=c.getLong(1);String note=c.getString(2);TextView row=label(money(amount)+"  "+note,16);row.setOnLongClickListener(v->{actions("advances",id,amount,note,null);return true;});rows.addView(row);}}
 }
 String b64(String s){return Base64.encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8),Base64.NO_WRAP);}
 String unb64(String s){try{return new String(Base64.decode(s,Base64.NO_WRAP),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "";}}
 Uri sharedImage(InputStream in,String name,String mime) throws IOException{
  ContentValues cv=new ContentValues();cv.put(MediaStore.Images.Media.DISPLAY_NAME,name);cv.put(MediaStore.Images.Media.MIME_TYPE,mime);if(Build.VERSION.SDK_INT>=29)cv.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/MoiCheki");
  Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);if(u==null)throw new IOException("Не удалось создать файл в общей папке");
  try(OutputStream out=getContentResolver().openOutputStream(u)){if(out==null)throw new IOException("Не удалось открыть файл");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return u;
 }
 String sharedCopy(File source,String name){try(InputStream in=new FileInputStream(source)){String ext=source.getName().toLowerCase(Locale.ROOT);String mime=ext.endsWith(".png")?"image/png":ext.endsWith(".webp")?"image/webp":"image/jpeg";return sharedImage(in,name.substring(0,name.lastIndexOf('.'))+(mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":".jpg"),mime).toString();}catch(Exception e){toast("Не удалось сохранить фото в общей папке");return source.getAbsolutePath();}}
 Uri photoUri(String path){return path!=null&&path.startsWith("content://")?Uri.parse(path):uri(new File(path));}
 void deletePhoto(String path){try{if(path!=null&&path.startsWith("content://"))getContentResolver().delete(Uri.parse(path),null,null);else if(path!=null)new File(path).delete();}catch(Exception ignored){}}
 Uri backupUri(){if(Build.VERSION.SDK_INT<29)return null;try(Cursor c=getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Downloads._ID},MediaStore.Downloads.DISPLAY_NAME+"=?",new String[]{"moi_cheki_backup.txt"},null)){if(c!=null&&c.moveToFirst())return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,c.getLong(0));}catch(Exception ignored){}return null;}
 void saveBackup(){if(Build.VERSION.SDK_INT<29)return;try{StringBuilder s=new StringBuilder();try(Cursor c=db.rawQuery("SELECT month,amount,note,photo,created FROM receipts ORDER BY id",null)){while(c.moveToNext())s.append("R|").append(b64(c.getString(0))).append('|').append(c.getLong(1)).append('|').append(b64(c.getString(2))).append('|').append(b64(c.getString(3))).append('|').append(b64(c.getString(4))).append('\n');}try(Cursor c=db.rawQuery("SELECT month,amount,note FROM advances ORDER BY id",null)){while(c.moveToNext())s.append("A|").append(b64(c.getString(0))).append('|').append(c.getLong(1)).append('|').append(b64(c.getString(2))).append('\n');}Uri u=backupUri();ContentValues cv=new ContentValues();cv.put(MediaStore.Downloads.DISPLAY_NAME,"moi_cheki_backup.txt");cv.put(MediaStore.Downloads.MIME_TYPE,"text/plain");if(Build.VERSION.SDK_INT>=29)cv.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MoiCheki");if(u==null)u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);if(u!=null){try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){out.write(s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}}catch(Exception ignored){}}
 void restoreBackup(){if(sumSafe("receipts")>0||sumSafe("advances")>0)return;Uri u=backupUri();if(u==null)return;try(InputStream in=getContentResolver().openInputStream(u);BufferedReader r=new BufferedReader(new InputStreamReader(in))){String line;while((line=r.readLine())!=null){String[] p=line.split("\\|",-1);if(p.length>=4&&p[0].equals("A"))db.execSQL("INSERT INTO advances(month,amount,note) VALUES(?,?,?)",new Object[]{unb64(p[1]),Long.parseLong(p[2]),unb64(p[3])});else if(p.length>=6&&p[0].equals("R"))db.execSQL("INSERT INTO receipts(month,amount,note,photo,created) VALUES(?,?,?,?,?)",new Object[]{unb64(p[1]),Long.parseLong(p[2]),unb64(p[3]),unb64(p[4]),unb64(p[5])});}}catch(Exception ignored){}}
 long sumSafe(String table){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}catch(Exception e){return 0;}}
 void editor(String title,long initial,String description,Save save){
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),0);EditText amount=new EditText(this);amount.setHint("Сумма, ₽");amount.setInputType(8194);if(initial>0)amount.setText(BigDecimal.valueOf(initial,2).toPlainString());box.addView(amount);EditText note=new EditText(this);note.setHint("Описание покупки / примечание");note.setText(description);box.addView(note);
  File editing=title.equals("Новый чек")&&pending!=null?new File(pending):title.equals("Новый чек из галереи")?galleryFile:null;
  if(editing!=null){box.addView(button("Обрезать / улучшить фото",()->PhotoTools.edit(this,editing,()->toast("Фото обработано"))));Button scan=button("Распознать суммы",()->{});box.addView(scan);scan.setOnClickListener(v->{scan.setEnabled(false);scan.setText("Распознаю…");PhotoTools.recognize(this,editing,values->{scan.setEnabled(true);scan.setText("Распознать суммы");if(values.length==0){toast("Суммы не найдены. Введите вручную.");return;}new AlertDialog.Builder(this).setTitle("Выберите итоговую сумму и проверьте её").setItems(values,(d,w)->amount.setText(values[w])).setNegativeButton("Отмена",null).show();});});}
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",(d,w)->{if(title.equals("Новый чек"))discard();if(title.equals("Новый чек из галереи"))cancelGallery();}).create();dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->{if(title.equals("Новый чек"))discard();if(title.equals("Новый чек из галереи"))cancelGallery();});
  dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{long cents=new BigDecimal(amount.getText().toString().trim().replace(',','.')).movePointRight(2).longValueExact();if(cents<=0 || cents>100000000000L)throw new IllegalArgumentException();save.save(cents,note.getText().toString().trim());dialog.dismiss();}catch(ArithmeticException|IllegalArgumentException e){amount.setError("Введи положительную сумму, до двух знаков после запятой");}}));dialog.show();
 }
 Uri uri(File f){return FileProvider.getUriForFile(this,"ru.klimov.receipts.files",f);}
 void camera(){
  if(pending!=null && new File(pending).length()>0){finishPhoto();return;}
  File directory=new File(getFilesDir(),"checks/"+month);if(!directory.exists()&&!directory.mkdirs()){toast("Не удалось создать папку");return;}
  File file=new File(directory,UUID.randomUUID()+".jpg");pending=file.getAbsolutePath();pendingMonth=month.toString();prefs.edit().putString("pending",pending).putString("pendingMonth",pendingMonth).commit();
  Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);Uri output=uri(file);intent.putExtra(MediaStore.EXTRA_OUTPUT,output);intent.setClipData(ClipData.newRawUri("Чек",output));intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
  try{startActivityForResult(intent,10);}catch(ActivityNotFoundException e){discard();toast("На телефоне не найдено приложение камеры");}
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==10){if(result==RESULT_OK && pending!=null && new File(pending).length()>0)finishPhoto();else discard();}
  if(req==11){if(result==RESULT_OK&&data!=null){if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)galleryQueue.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)galleryQueue.add(data.getData());galleryMonth=month.toString();galleryNext();}else galleryQueue.clear();}
 }
 void gallery(){
  if(!galleryQueue.isEmpty()){toast("Уже обрабатываю выбранные фотографии");return;}
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  try{startActivityForResult(i,11);}catch(ActivityNotFoundException e){toast("На телефоне нет выбора изображений");}
 }
 void galleryNext(){
  if(galleryQueue.isEmpty()){galleryFile=null;refresh();toast("Загрузка из галереи завершена");return;}
  Uri source=galleryQueue.removeFirst();File directory=new File(getFilesDir(),"checks/"+galleryMonth);if(!directory.exists()&&!directory.mkdirs()){galleryQueue.clear();toast("Не удалось создать папку");return;}
  String type=getContentResolver().getType(source);String ext=type!=null&&type.toLowerCase(Locale.ROOT).contains("png")?".png":type!=null&&type.toLowerCase(Locale.ROOT).contains("webp")?".webp":".jpg";galleryFile=new File(directory,UUID.randomUUID()+ext);
  try(InputStream in=getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(galleryFile)){if(in==null)throw new IOException("Фотография недоступна");byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>25*1024*1024)throw new IOException("Файл больше 25 МБ");out.write(b,0,n);}}
  catch(Exception e){if(galleryFile!=null)galleryFile.delete();galleryFile=null;galleryQueue.clear();toast("Не удалось загрузить фотографию: "+e.getMessage());return;}
  editor("Новый чек из галереи",0,"",(amount,note)->{String shared=sharedCopy(galleryFile, "check_"+UUID.randomUUID()+".jpg");db.execSQL("INSERT INTO receipts(month,amount,note,photo,created) VALUES(?,?,?,?,?)",new Object[]{galleryMonth,amount,note,shared,LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))});if(shared.startsWith("content://"))galleryFile.delete();galleryFile=null;saveBackup();refresh();galleryNext();});
 }
 void cancelGallery(){if(galleryFile!=null)galleryFile.delete();galleryFile=null;galleryQueue.clear();refresh();toast("Загрузка из галереи отменена");}
 void finishPhoto(){if(pending==null)return;editor("Новый чек",0,"",(amount,note)->{String shared=sharedCopy(new File(pending),"check_"+UUID.randomUUID()+".jpg");db.execSQL("INSERT INTO receipts(month,amount,note,photo,created) VALUES(?,?,?,?,?)",new Object[]{pendingMonth,amount,note,shared,LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))});clearPending();refresh();saveBackup();});}
 void clearPending(){pending=null;pendingMonth=null;prefs.edit().remove("pending").remove("pendingMonth").commit();}
 void discard(){if(pending!=null)new File(pending).delete();clearPending();}
 void viewPhoto(String path){String type=path.toLowerCase(Locale.ROOT).endsWith(".png")?"image/png":path.toLowerCase(Locale.ROOT).endsWith(".webp")?"image/webp":"image/jpeg";Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(photoUri(path),type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(i);}catch(ActivityNotFoundException e){toast("Не найден просмотрщик фотографий");}}
 void actions(String table,long id,long amount,String note,String photo){new AlertDialog.Builder(this).setTitle("Запись").setItems(new String[]{"Изменить","Удалить"},(d,which)->{if(which==0)editor("Изменить запись",amount,note,(a,n)->{db.execSQL("UPDATE "+table+" SET amount=?,note=? WHERE id=?",new Object[]{a,n,id});saveBackup();refresh();});else new AlertDialog.Builder(this).setTitle("Удалить запись?").setMessage("Отменить удаление нельзя.").setPositiveButton("Удалить",(x,y)->{db.delete(table,"id=?",new String[]{Long.toString(id)});deletePhoto(photo);saveBackup();refresh();}).setNegativeButton("Отмена",null).show();}).show();}
 void email(){EditText e=new EditText(this);e.setInputType(33);e.setHint("example@company.ru");e.setText(prefs.getString("email",""));new AlertDialog.Builder(this).setTitle("Почта для отчётов").setView(e).setPositiveButton("Сохранить",(d,w)->prefs.edit().putString("email",e.getText().toString().trim()).apply()).setNegativeButton("Отмена",null).show();}
 void share(){
  ArrayList<Uri> photos=new ArrayList<>();StringBuilder body=new StringBuilder("Чеки за "+month+"\nПолучено: "+money(sum("advances"))+"\nРасходы: "+money(sum("receipts"))+"\nОстаток: "+money(sum("advances")-sum("receipts"))+"\n\n");long bytes=0;
  try(Cursor c=db.rawQuery("SELECT amount,note,photo,created FROM receipts WHERE month=? ORDER BY id",new String[]{month.toString()})){while(c.moveToNext()){String p=c.getString(2);File f=p.startsWith("content://")?null:new File(p);if((f==null&&photoUri(p)==null)||(f!=null&&!f.isFile())){toast("Фотография одного из чеков отсутствует");return;}photos.add(photoUri(p));bytes+=f!=null?f.length():1024*1024;body.append(c.getString(3)).append(" — ").append(money(c.getLong(0))).append(" — ").append(c.getString(1)).append('\n');}}
  if(photos.isEmpty()){toast("Сначала добавь чек");return;}
  Intent i=new Intent(Intent.ACTION_SEND_MULTIPLE);i.setType("image/*");i.putParcelableArrayListExtra(Intent.EXTRA_STREAM,photos);i.putExtra(Intent.EXTRA_EMAIL,new String[]{prefs.getString("email","")});i.putExtra(Intent.EXTRA_SUBJECT,"Авансовый отчёт — "+month);i.putExtra(Intent.EXTRA_TEXT,body.toString());ClipData clip=ClipData.newRawUri("Чеки",photos.get(0));for(int n=1;n<photos.size();n++)clip.addItem(new ClipData.Item(photos.get(n)));i.setClipData(clip);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  Runnable send=()->{try{startActivity(Intent.createChooser(i,"Отправить через почту"));}catch(ActivityNotFoundException e){toast("Установи почтовое приложение");}};
  if(bytes>18*1024*1024)new AlertDialog.Builder(this).setTitle("Большой объём вложений").setMessage("Фотографии занимают "+bytes/1024/1024+" МБ. Почтовый сервис может отклонить письмо.").setPositiveButton("Открыть письмо",(d,w)->send.run()).setNegativeButton("Отмена",null).show();else send.run();
 }
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
