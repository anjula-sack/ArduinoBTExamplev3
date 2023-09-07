package com.sarmale.arduinobtexample_v3;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class LoadingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        Intent mainIntent = new Intent(LoadingActivity.this, MainActivity.class);
                        startActivity(mainIntent);
                        finish();
                    }
                },
                2000
        );
    }
}
