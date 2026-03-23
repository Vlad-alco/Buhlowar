#ifndef CLOUD_MANAGER_H
#define CLOUD_MANAGER_H

#include <WiFiClientSecure.h>
#include <HTTPClient.h>

class CloudManager {
private:
    String serverUrl;
    String apiKey;
    WiFiClientSecure secureClient;
    HTTPClient http;
    unsigned long lastTelemetryMs = 0;
    unsigned long lastCommandCheckMs = 0;
    unsigned long lastSettingsCheckMs = 0;
    unsigned long telemetryIntervalMs = 1000;
    unsigned long commandCheckIntervalMs = 1000;
    unsigned long settingsCheckIntervalMs = 30000;
    unsigned long settingsLastUpdate = 0;
    bool skipCertCheck = true;
    
    // Callback for processing commands from cloud
    typedef void (*CommandCallback)(const String& command, const String& params);
    CommandCallback onCommand = nullptr;
    
    // Callback for processing settings from cloud
    typedef void (*SettingsCallback)(const String& settingsJson);
    SettingsCallback onSettings = nullptr;
    
public:
    CloudManager() : serverUrl(""), apiKey("") {}
    
    void begin(const String& url, const String& key) {
        serverUrl = url;
        apiKey = key;
        if (skipCertCheck) {
            secureClient.setInsecure();
        }
        Serial.println(F("[Cloud] Initialized"));
    }
    
    void onCommandReceived(CommandCallback callback) {
        onCommand = callback;
    }
    
    void onSettingsReceived(SettingsCallback callback) {
        onSettings = callback;
    }
    
    void setTelemetryInterval(unsigned long ms) {
        telemetryIntervalMs = ms;
    }
    
    void setCommandCheckInterval(unsigned long ms) {
        commandCheckIntervalMs = ms;
    }
    
    bool isConfigured() {
        return serverUrl.length() > 0 && apiKey.length() > 0;
    }
    
    // Send telemetry data to cloud
    bool sendTelemetry(const String& jsonData) {
        if (!isConfigured()) return false;
        
        String url = serverUrl + F("?telemetry=1");
        
        http.begin(secureClient, url);
        http.setTimeout(5000);
        http.addHeader(F("Content-Type"), F("application/json"));
        http.addHeader(F("Authorization"), (String(F("Bearer ")) + apiKey).c_str());
        
        int httpCode = http.POST(jsonData);
        
        if (httpCode == 200) {
            lastTelemetryMs = millis();
            http.end();
            return true;
        } else {
            Serial.printf(F("[Cloud] Telemetry failed: %d\n"), httpCode);
            http.end();
            return false;
        }
    }
    
    // Check for pending commands from cloud
    bool checkCommands() {
        if (!isConfigured()) return false;
        
        String url = serverUrl + F("?commands=1");
        
        http.begin(secureClient, url);
        http.setTimeout(5000);
        http.addHeader(F("Authorization"), (String(F("Bearer ")) + apiKey).c_str());
        
        int httpCode = http.GET();
        
        if (httpCode == 200) {
            String response = http.getString();
            http.end();
            
            if (response.indexOf(F("\"commands\"")) != -1) {
                parseCommands(response);
                return true;
            }
        } else {
            Serial.printf(F("[Cloud] Command check failed: %d\n"), httpCode);
            http.end();
        }
        return false;
    }
    
    // Check for settings update from cloud
    bool checkSettings() {
        if (!isConfigured() || onSettings == nullptr) return false;
        
        String url = serverUrl + F("?settings=1");
        
        http.begin(secureClient, url);
        http.setTimeout(5000);
        http.addHeader(F("Authorization"), (String(F("Bearer ")) + apiKey).c_str());
        
        int httpCode = http.GET();
        
        if (httpCode == 200) {
            String response = http.getString();
            http.end();
            
            if (response.indexOf(F("\"settings\"")) != -1) {
                int tsPos = response.indexOf(F("\"settings_last_update\":"));
                unsigned long cloudTs = 0;
                if (tsPos != -1) {
                    int start = tsPos + 22;
                    int end = response.indexOf(",", start);
                    if (end == -1) end = response.indexOf("}", start);
                    String tsStr = response.substring(start, end);
                    cloudTs = tsStr.toInt();
                }
                
                if (cloudTs > settingsLastUpdate) {
                    settingsLastUpdate = cloudTs;
                    Serial.printf(F("[Cloud] Settings updated from cloud (ts: %lu)\n"), cloudTs);
                    
                    int settingsStart = response.indexOf(F("{\"settings\":"));
                    if (settingsStart != -1) {
                        int jsonStart = response.indexOf("{", settingsStart);
                        int depth = 0;
                        int jsonEnd = jsonStart;
                        for (int i = jsonStart; i < response.length(); i++) {
                            if (response.charAt(i) == '{') depth++;
                            else if (response.charAt(i) == '}') {
                                depth--;
                                if (depth == 0) {
                                    jsonEnd = i + 1;
                                    break;
                                }
                            }
                        }
                        String settingsJson = response.substring(jsonStart, jsonEnd);
                        onSettings(settingsJson);
                    }
                    return true;
                }
            }
        } else {
            Serial.printf(F("[Cloud] Settings check failed: %d\n"), httpCode);
            http.end();
        }
        return false;
    }
    
    void setSettingsLastUpdate(unsigned long ts) {
        settingsLastUpdate = ts;
    }
    
    // Call this periodically from main loop
    void update(const String& telemetryJson) {
        if (!isConfigured()) return;
        
        unsigned long now = millis();
        
        // Send telemetry
        if (now - lastTelemetryMs >= telemetryIntervalMs) {
            sendTelemetry(telemetryJson);
        }
        
        // Check commands
        if (now - lastCommandCheckMs >= commandCheckIntervalMs) {
            checkCommands();
        }
        
        // Check settings
        if (now - lastSettingsCheckMs >= settingsCheckIntervalMs) {
            checkSettings();
        }
    }
    
    // Get server connection status
    bool isConnected() {
        return WiFi.status() == WL_CONNECTED && isConfigured();
    }
    
private:
    void parseCommands(const String& response) {
        // Simple JSON parsing for commands
        // Looking for: {"commands":[{"command":"NAME","params":{...}},...]}
        
        int cmdsStart = response.indexOf(F("\"commands\""));
        if (cmdsStart == -1) return;
        
        int arrStart = response.indexOf("[", cmdsStart);
        if (arrStart == -1) return;
        
        // Find matching ] by counting brackets depth
        int depth = 1;
        int pos = arrStart + 1;
        while (pos < response.length() && depth > 0) {
            char c = response.charAt(pos);
            if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
            pos++;
        }
        int arrEnd = pos - 1;
        
        String commands = response.substring(arrStart + 1, arrEnd);
        
        // Extract each command - find all {"command": patterns
        int cmdPos = 0;
        while (cmdPos < commands.length()) {
            int cmdStart = commands.indexOf(F("{\"command\":"), cmdPos);
            if (cmdStart == -1) break;
            
            // Find command name
            int nameStart = commands.indexOf("\"", cmdStart + 11) + 1;
            int nameEnd = commands.indexOf("\"", nameStart);
            if (nameEnd == -1 || nameEnd <= nameStart) break;
            
            String cmdName = commands.substring(nameStart, nameEnd);
            
            // Call callback for each command
            if (onCommand) {
                onCommand(cmdName, "{}");
                Serial.printf("[Cloud] Command parsed: %s\n", cmdName.c_str());
            }
            
            cmdPos = nameEnd + 1;
        }
    }
};

#endif
