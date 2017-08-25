package com.example.ephron.scheldegetijden;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.text.BidiFormatter;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class DisplayMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_message);

        //get text
        Intent intent = getIntent();
        String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);
        BidiFormatter bdf = BidiFormatter.getInstance();

        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText(bdf.unicodeWrap(message));
    }
}
