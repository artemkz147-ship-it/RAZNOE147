package ru.filemaster.offline;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;

public class SignatureActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Нарисуйте подпись");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 28, 36));
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Подпись будет добавлена на последнюю страницу PDF внизу справа.");
        hint.setTextSize(15);
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, 0, 0, dp(18));
        root.addView(hint);

        SignatureView pad = new SignatureView(this);
        pad.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(pad, padParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(16), 0, 0);

        Button clear = new Button(this);
        clear.setText("Очистить");
        clear.setOnClickListener(v -> pad.clear());
        actions.addView(clear);

        Button save = new Button(this);
        save.setText("Подписать");
        save.setOnClickListener(v -> {
            try {
                File out = File.createTempFile("signature_", ".png", getCacheDir());
                Bitmap bitmap = pad.toBitmap();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                bitmap.recycle();
                Intent result = new Intent();
                result.putExtra("signature_path", out.getAbsolutePath());
                setResult(RESULT_OK, result);
                finish();
            } catch (Exception e) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        actions.addView(save);
        root.addView(actions);

        setContentView(root);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class SignatureView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        SignatureView(android.content.Context context) {
            super(context);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(6f * getResources().getDisplayMetrics().density);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    path.moveTo(x, y);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    path.lineTo(x, y);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP -> {
                    path.lineTo(x, y);
                    invalidate();
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        void clear() {
            path.reset();
            invalidate();
        }

        Bitmap toBitmap() {
            int w = Math.max(getWidth(), 1);
            int h = Math.max(getHeight(), 1);
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT);
            canvas.drawPath(path, paint);
            return bitmap;
        }
    }
}
