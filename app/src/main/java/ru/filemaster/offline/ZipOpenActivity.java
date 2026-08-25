package ru.filemaster.offline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Opens ZIP files from Android file managers directly in the FileMaster ZIP editor. */
public class ZipOpenActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        if (uri != null) {
            Intent editor = new Intent(this, ZipEditorActivity.class);
            editor.putExtra("zip_uri", uri.toString());
            editor.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(editor);
        }
        finish();
    }
}
