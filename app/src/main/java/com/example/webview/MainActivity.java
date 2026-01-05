package com.example.webview;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.*;

public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        WebView webView = new WebView(this);
        setContentView(webView);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        
        webView.addJavascriptInterface(new BatteryBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }
    
    public class BatteryBridge {
        
        @JavascriptInterface
        public String getBatteryData() {
            try {
                JSONObject data = new JSONObject();
                
                data.put("capacity", readSysFile("/sys/class/power_supply/battery/capacity", "0"));
                data.put("status", readSysFile("/sys/class/power_supply/battery/status", "Unknown"));
                data.put("current_now", readSysFile("/sys/class/power_supply/battery/current_now", "0"));
                data.put("temp", readSysFile("/sys/class/power_supply/battery/temp", "0"));
                data.put("voltage", tryReadVoltage());
                
                return data.toString();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        private String tryReadVoltage() {
            String[] paths = {
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/battery/batt_vol"
            };
            
            for (String path : paths) {
                String value = readSysFile(path, null);
                if (value != null && !value.equals("0")) return value;
            }
            return "0";
        }
        
        private String readSysFile(String path, String defaultValue) {
            try {
                File file = new File(path);
                if (!file.exists() || !file.canRead()) return defaultValue;
                
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                reader.close();
                
                return (line != null) ? line.trim() : defaultValue;
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }
    
    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}