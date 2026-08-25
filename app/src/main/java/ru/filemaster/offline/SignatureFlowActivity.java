package ru.filemaster.offline;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SignatureFlowActivity extends AppCompatActivity {
    private static final int CHOOSE=2401;
    private Uri pdfUri;
    @Override protected void onCreate(Bundle b){super.onCreate(b);String raw=getIntent().getStringExtra("pdf_uri");if(raw==null||raw.isBlank()){finish();return;}pdfUri=Uri.parse(raw);Intent i=new Intent(this,SignatureLibraryActivity.class);i.putExtra("select_mode",true);startActivityForResult(i,CHOOSE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=CHOOSE)return;if(resultCode==RESULT_OK&&data!=null){String path=data.getStringExtra("signature_path");if(path!=null&&!path.isBlank()){Intent i=new Intent(this,SignaturePlacementActivity.class);i.putExtra("pdf_uri",pdfUri.toString());i.putExtra("signature_path",path);startActivity(i);}}finish();}
}
