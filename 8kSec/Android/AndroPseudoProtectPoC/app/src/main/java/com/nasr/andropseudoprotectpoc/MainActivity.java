package com.nasr.andropseudoprotectpoc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_PACKAGE = "com.eightksec.andropseudoprotect";
    private static final String TARGET_SERVICE =
            "com.eightksec.andropseudoprotect.SecurityService";

    private static final String ACTION_START =
            "com.eightksec.andropseudoprotect.START_SECURITY";

    private static final String ACTION_STOP =
            "com.eightksec.andropseudoprotect.STOP_SECURITY";

    private static final String TOKEN =
            "8ksec_S3cr3tT0k3n_D0N0tSh4r3";

    private TextView txtStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        txtStatus = findViewById(R.id.txtStatus);

        btnStart.setOnClickListener(v -> startEncryption());
        btnStop.setOnClickListener(v -> stopEncryption());
    }

    private void startEncryption() {
        try {
            Intent intent = new Intent();
            intent.setClassName(TARGET_PACKAGE, TARGET_SERVICE);
            intent.setAction(ACTION_START);
            intent.putExtra("security_token", TOKEN);

            startService(intent);

            txtStatus.setText("Status: Encryption Triggered");
            Toast.makeText(this,
                    "START_SECURITY sent successfully",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            txtStatus.setText("Status: Failed");
            Toast.makeText(this,
                    "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void stopEncryption() {
        try {
            Intent intent = new Intent();
            intent.setClassName(TARGET_PACKAGE, TARGET_SERVICE);
            intent.setAction(ACTION_STOP);
            intent.putExtra("security_token", TOKEN);

            startService(intent);

            txtStatus.setText("Status: Decryption Triggered");
            Toast.makeText(this,
                    "STOP_SECURITY sent successfully",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            txtStatus.setText("Status: Failed");
            Toast.makeText(this,
                    "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}