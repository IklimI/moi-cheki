package ru.klimov.receipts;
import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
/** Only installed in the disposable emulator, never shipped inside the APK. */
public class DesignCapture extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){try{
  Context c=getTargetContext();SQLiteDatabase db=c.openOrCreateDatabase("receipts.db",0,null);
  db.execSQL("CREATE TABLE IF NOT EXISTS receipts(id INTEGER PRIMARY KEY, month TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL, photo TEXT NOT NULL, created TEXT NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS advances(id INTEGER PRIMARY KEY, month TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL)");
  String month=YearMonth.now().toString();String date=LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
  db.execSQL("DELETE FROM receipts");db.execSQL("DELETE FROM advances");db.execSQL("INSERT INTO advances(month,amount,note) VALUES(?,?,?)",new Object[]{month,3000000,"Аванс"});
  String[] notes={"Инструмент","Материалы","Топливо"};long[] amounts={140000,780000,325000};
  for(int i=0;i<3;i++)db.execSQL("INSERT INTO receipts(month,amount,note,photo,created) VALUES(?,?,?,?,?)",new Object[]{month,amounts[i],notes[i],"/missing.jpg",i==0?LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")):date});db.close();
  for(int theme=0;theme<4;theme++){
   c.getSharedPreferences("settings",0).edit().putInt("theme",theme).commit();Intent in=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);Activity a=startActivitySync(in);waitForIdleSync();Thread.sleep(1200);
   Bitmap shot=getUiAutomation().takeScreenshot();if(shot==null)throw new IOException("Screenshot unavailable");try(FileOutputStream out=c.openFileOutput("design-"+theme+".png",0)){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();runOnMainSync(()->a.finish());waitForIdleSync();
  }
  Bundle result=new Bundle();result.putString("stream","Captured four actual app themes");finish(Activity.RESULT_OK,result);
 }catch(Exception e){Bundle b=new Bundle();b.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,b);}}
}
