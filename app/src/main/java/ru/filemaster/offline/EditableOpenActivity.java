package ru.filemaster.offline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class EditableOpenActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri=getIntent().getData();
        if(uri!=null){String name=FileStore.displayName(this,uri).toLowerCase();Intent i;if(name.endsWith(".xlsx")||name.endsWith(".csv")||name.endsWith(".tsv")){i=new Intent(this,TableEditorActivity.class);i.putExtra("table_uri",uri.toString());}else{i=new Intent(this,DocumentEditorActivity.class);i.putExtra("document_uri",uri.toString());}i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}finish();
    }
}
