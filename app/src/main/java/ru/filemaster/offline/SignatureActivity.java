package ru.filemaster.offline;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;

public class SignatureActivity extends AppCompatActivity {
    private static final int BG=Color.rgb(247,248,252),TEXT=Color.rgb(25,28,36),MUTED=Color.rgb(93,99,112),BLUE=Color.rgb(49,87,213);
    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(18));root.setBackgroundColor(BG);ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(16),dp(12)+safe.top,dp(16),dp(18)+safe.bottom);return insets;});LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=label("←",30,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(label("Нарисуйте подпись",25,TEXT,true));TextView small=label("После этого вы расставите её по PDF",14,MUTED,false);small.setPadding(0,dp(2),0,0);names.addView(small);top.addView(names,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));root.addView(top);TextView hint=label("Нарисуйте один образец. На следующем экране подпись можно будет перетаскивать, менять размер, копировать и ставить на разных страницах.",14,MUTED,false);hint.setPadding(dp(4),dp(8),dp(4),dp(14));root.addView(hint);SignatureView pad=new SignatureView(this);GradientDrawable padBg=rounded(Color.WHITE,dp(18));padBg.setStroke(dp(1),Color.rgb(222,225,234));pad.setBackground(padBg);pad.setPadding(dp(10),dp(10),dp(10),dp(10));LinearLayout.LayoutParams padParams=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f);padParams.setMargins(0,0,0,dp(14));root.addView(pad,padParams);LinearLayout actions=new LinearLayout(this);TextView clear=action("Очистить",Color.WHITE,TEXT,Color.rgb(227,230,237));clear.setOnClickListener(v->pad.clear());LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(54),1f);left.setMargins(0,0,dp(6),0);actions.addView(clear,left);TextView save=action("Далее к размещению",BLUE,Color.WHITE,BLUE);save.setOnClickListener(v->{if(!pad.hasInk()){Toast.makeText(this,"Сначала нарисуйте подпись",Toast.LENGTH_SHORT).show();return;}try{File out=File.createTempFile("signature_",".png",getCacheDir());Bitmap bitmap=pad.toBitmap();try(FileOutputStream fos=new FileOutputStream(out)){bitmap.compress(Bitmap.CompressFormat.PNG,100,fos);}bitmap.recycle();Intent result=new Intent();result.putExtra("signature_path",out.getAbsolutePath());setResult(RESULT_OK,result);finish();}catch(Exception e){Toast.makeText(this,"Не удалось сохранить подпись",Toast.LENGTH_LONG).show();}});LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(54),1.55f);right.setMargins(dp(6),0,0,0);actions.addView(save,right);root.addView(actions);setContentView(root);}
    private TextView action(String value,int bg,int fg,int stroke){TextView v=label(value,15,fg,true);v.setGravity(Gravity.CENTER);GradientDrawable shape=rounded(bg,dp(15));shape.setStroke(dp(1),stroke);v.setBackground(shape);v.setClickable(true);v.setFocusable(true);return v;}private TextView label(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}private GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
    static class SignatureView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private boolean hasInk;
        SignatureView(android.content.Context context){super(context);paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(5f*getResources().getDisplayMetrics().density);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawPath(path,paint);}
        @Override public boolean onTouchEvent(MotionEvent event){float x=event.getX(),y=event.getY();switch(event.getAction()){case MotionEvent.ACTION_DOWN->{path.moveTo(x,y);hasInk=true;invalidate();return true;}case MotionEvent.ACTION_MOVE->{path.lineTo(x,y);invalidate();return true;}case MotionEvent.ACTION_UP->{path.lineTo(x,y);invalidate();return true;}}return super.onTouchEvent(event);}
        void clear(){path.reset();hasInk=false;invalidate();}boolean hasInk(){return hasInk;}
        Bitmap toBitmap(){RectF bounds=new RectF();path.computeBounds(bounds,true);float margin=Math.max(paint.getStrokeWidth()*2f,8f*getResources().getDisplayMetrics().density);int w=Math.max(1,(int)Math.ceil(bounds.width()+margin*2f));int h=Math.max(1,(int)Math.ceil(bounds.height()+margin*2f));Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.TRANSPARENT);canvas.translate(-bounds.left+margin,-bounds.top+margin);canvas.drawPath(path,paint);return bitmap;}
    }
}
