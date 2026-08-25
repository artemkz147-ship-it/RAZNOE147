package ru.filemaster.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ResultDialogs {
    private ResultDialogs() {}

    static void show(Activity activity, String detail, Uri uri) {
        String folderLabel = ViewerIntents.outputFolderLabel(activity, uri);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(activity, 18);
        box.setPadding(p, dp(activity, 4), p, dp(activity, 10));

        TextView message = new TextView(activity);
        message.setText((detail == null || detail.isBlank() ? "Файл успешно сохранён." : detail) + "\n\nСохранено в папку:\n" + folderLabel);
        message.setTextSize(15);
        message.setTextColor(Color.rgb(35, 38, 47));
        message.setLineSpacing(0, 1.08f);
        box.addView(message);

        Button open = button(activity, "Открыть в Доки");
        open.setOnClickListener(v -> ViewerIntents.open(activity, uri));
        box.addView(open, lp(activity));

        Button folder = button(activity, "Открыть папку");
        folder.setOnClickListener(v -> ViewerIntents.openOutputFolder(activity, uri));
        box.addView(folder, lp(activity));

        Button share = button(activity, "Поделиться");
        share.setOnClickListener(v -> ViewerIntents.share(activity, uri));
        box.addView(share, lp(activity));

        new AlertDialog.Builder(activity)
                .setTitle("Готово")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private static Button button(Activity a, String text) {
        Button b = new Button(a); b.setText(text); b.setAllCaps(false); b.setTextSize(15); b.setGravity(Gravity.CENTER); return b;
    }
    private static LinearLayout.LayoutParams lp(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 50));
        p.setMargins(0, dp(a, 8), 0, 0); return p;
    }
    private static int dp(Activity a, int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
}
