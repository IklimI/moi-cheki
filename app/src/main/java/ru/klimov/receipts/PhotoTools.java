package ru.klimov.receipts;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import androidx.exifinterface.media.ExifInterface;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/** Local, opt-in photo processing. Writes a new file before replacing the working copy. */
public final class PhotoTools {
 interface Amounts { void ready(String[] values); }
 static Bitmap load(File file)throws IOException{
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);o.inSampleSize=1;
  while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>2400)o.inSampleSize*=2;o.inJustDecodeBounds=false;
  Bitmap b=BitmapFactory.decodeFile(file.getPath(),o);if(b==null)throw new IOException("Формат изображения не поддерживается");
  ExifInterface ex=new ExifInterface(file);Matrix m=new Matrix();m.postRotate(ex.getRotationDegrees());if(ex.isFlipped())m.postScale(-1,1);
  return Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);
 }
 static void recognize(Activity a,File f,Amounts callback){
  final Bitmap b;try{b=load(f);}catch(Exception e){callback.ready(new String[0]);return;}
  com.google.mlkit.vision.text.TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  recognizer.process(InputImage.fromBitmap(b,0)).addOnSuccessListener(text->{
   LinkedHashSet<String> amounts=new LinkedHashSet<>();Matcher m=Pattern.compile("(?<![0-9])([0-9]{1,7}[.,][0-9]{2})(?![0-9])").matcher(text.getText());
   while(m.find()&&amounts.size()<80)amounts.add(m.group(1).replace(',','.'));
   if(!a.isFinishing()&&!a.isDestroyed())callback.ready(amounts.toArray(new String[0]));
  }).addOnFailureListener(e->{if(!a.isFinishing()&&!a.isDestroyed())callback.ready(new String[0]);}).addOnCompleteListener(t->{recognizer.close();b.recycle();});
 }
 static void edit(MainActivity a,File file,Runnable done){
  final Bitmap original;try{original=load(file);}catch(Exception e){a.toast("Не удалось открыть фото: "+e.getMessage());return;}
  LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.setPadding(a.dp(12),0,a.dp(12),0);
  box.addView(a.label("Потяните углы рамки. Изменения применятся после сохранения.",14));
  CropView view=new CropView(a,original);box.addView(view,new LinearLayout.LayoutParams(-1,a.dp(300)));
  CheckBox improve=new CheckBox(a);improve.setText("Чёрно-белое / повысить контраст");improve.setTextColor(a.color(2));box.addView(improve);improve.setOnCheckedChangeListener((v,on)->{view.enhance=on;view.invalidate();});
  box.addView(a.button("Повернуть на 90°",()->{Matrix m=new Matrix();m.postRotate(90);view.bitmap=Bitmap.createBitmap(view.bitmap,0,0,view.bitmap.getWidth(),view.bitmap.getHeight(),m,true);view.reset();}));
  box.addView(a.button("Сбросить",()->{view.bitmap=original;improve.setChecked(false);view.reset();}));
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Обработка фото").setView(box).setPositiveButton("Применить",null).setNegativeButton("Отмена",null).create();
  dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
   File tmp=new File(file.getPath()+".processing");
   try{Bitmap result=view.result();String name=file.getName().toLowerCase(Locale.ROOT);Bitmap.CompressFormat format=name.endsWith(".png")?Bitmap.CompressFormat.PNG:name.endsWith(".webp")?Bitmap.CompressFormat.WEBP:Bitmap.CompressFormat.JPEG;
    try(FileOutputStream out=new FileOutputStream(tmp)){if(!result.compress(format,94,out))throw new IOException("Ошибка кодирования");out.getFD().sync();}
    if(!tmp.renameTo(file))throw new IOException("Не удалось заменить рабочее фото");dialog.dismiss();done.run();
   }catch(Exception e){tmp.delete();a.toast("Фото не изменено: "+e.getMessage());}
  }));dialog.show();
 }
 static class CropView extends View {
  Bitmap bitmap;boolean enhance;RectF crop=new RectF(.03f,.03f,.97f,.97f),bounds=new RectF();int corner;Paint paint=new Paint(3);
  CropView(Activity a,Bitmap b){super(a);bitmap=b;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  void reset(){crop.set(.03f,.03f,.97f,.97f);invalidate();}
  ColorMatrixColorFilter filter(){ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);ColorMatrix contrast=new ColorMatrix(new float[]{1.4f,0,0,0,-35,0,1.4f,0,0,-35,0,0,1.4f,0,-35,0,0,0,1,0});contrast.postConcat(gray);return new ColorMatrixColorFilter(contrast);}
  protected void onDraw(Canvas c){super.onDraw(c);float scale=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;bounds.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);paint.setColorFilter(enhance?filter():null);paint.setStyle(Paint.Style.FILL);c.drawBitmap(bitmap,null,bounds,paint);paint.setColorFilter(null);paint.setColor(0xff27a4ff);paint.setStrokeWidth(4);paint.setStyle(Paint.Style.STROKE);RectF r=new RectF(bounds.left+crop.left*w,bounds.top+crop.top*h,bounds.left+crop.right*w,bounds.top+crop.bottom*h);c.drawRect(r,paint);paint.setStyle(Paint.Style.FILL);for(float x:new float[]{r.left,r.right})for(float y:new float[]{r.top,r.bottom})c.drawCircle(x,y,12,paint);}
  public boolean onTouchEvent(MotionEvent e){if(bounds.width()==0)return true;float x=Math.max(0,Math.min(1,(e.getX()-bounds.left)/bounds.width())),y=Math.max(0,Math.min(1,(e.getY()-bounds.top)/bounds.height()));if(e.getAction()==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);corner=(x>(crop.left+crop.right)/2?1:0)+(y>(crop.top+crop.bottom)/2?2:0);}if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){if((corner&1)==0)crop.left=Math.min(x,crop.right-.05f);else crop.right=Math.max(x,crop.left+.05f);if((corner&2)==0)crop.top=Math.min(y,crop.bottom-.05f);else crop.bottom=Math.max(y,crop.top+.05f);invalidate();}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);return true;}
  Bitmap result(){int x=(int)(crop.left*bitmap.getWidth()),y=(int)(crop.top*bitmap.getHeight());Bitmap cut=Bitmap.createBitmap(bitmap,x,y,Math.max(1,(int)((crop.right-crop.left)*bitmap.getWidth())),Math.max(1,(int)((crop.bottom-crop.top)*bitmap.getHeight())));if(!enhance)return cut;Bitmap out=Bitmap.createBitmap(cut.getWidth(),cut.getHeight(),Bitmap.Config.ARGB_8888);Paint p=new Paint(3);p.setColorFilter(filter());new Canvas(out).drawBitmap(cut,0,0,p);return out;}
 }
}
