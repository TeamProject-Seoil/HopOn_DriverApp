// app/src/main/java/com/example/driver_bus_info/start/StartLoding.java
package com.example.driver_bus_info.start;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import com.example.driver_bus_info.R;
import com.example.driver_bus_info.activity.SplashActivity;

public class StartLoding extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        new Handler().postDelayed(() -> {
            Intent i = new Intent(StartLoding.this, SplashActivity.class);
            startActivity(i);
            finish();
        }, 2000);
    }
}
