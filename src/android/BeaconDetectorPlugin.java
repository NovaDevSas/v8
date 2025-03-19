package com.example;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeaconDetectorPlugin extends CordovaPlugin implements RangeNotifier, MonitorNotifier {
    private static final String TAG = "BeaconDetectorPlugin";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;
    private static final int PERMISSION_REQUEST_BLUETOOTH_SCAN = 3;
    private static final int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 4;

    private BeaconManager beaconManager;
    private List<Map<String, Object>> beaconData;
    private CallbackContext beaconDetectionCallback;
    private Region region;
    private boolean isScanning = false;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        
        Activity activity = cordova.getActivity();
        beaconManager = BeaconManager.getInstanceForApplication(activity.getApplicationContext());
        
        // Add support for iBeacon format
        beaconManager.getBeaconParsers().add(new BeaconParser()
                .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        
        beaconManager.addRangeNotifier(this);
        beaconManager.addMonitorNotifier(this);
        
        beaconData = new ArrayList<>();
        
        Log.d(TAG, "BeaconDetectorPlugin initialized");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("initialize".equals(action)) {
            initialize(args.getJSONArray(0), callbackContext);
            return true;
        } else if ("startScanning".equals(action)) {
            startScanning(callbackContext);
            return true;
        } else if ("stopScanning".equals(action)) {
            stopScanning(callbackContext);
            return true;
        } else if ("onBeaconDetected".equals(action)) {
            this.beaconDetectionCallback = callbackContext;
            return true;
        } else if ("isAvailable".equals(action)) {
            callbackContext.success("Plugin is available");
            return true;
        } else if ("checkCompatibility".equals(action)) {
            checkCompatibility(callbackContext);
            return true;
        } else if ("listDetectedBeacons".equals(action)) {
            listDetectedBeacons(callbackContext);
            return true;
        } else if ("debugBeaconScanner".equals(action)) {
            debugBeaconScanner(callbackContext);
            return true;
        }

        return false;
    }

    private void initialize(JSONArray beaconDataArray, CallbackContext callbackContext) {
        try {
            beaconData.clear();
            
            for (int i = 0; i < beaconDataArray.length(); i++) {
                JSONObject beaconObj = beaconDataArray.getJSONObject(i);
                Map<String, Object> beacon = new HashMap<>();
                
                beacon.put("title", beaconObj.getString("title"));
                beacon.put("uuid", beaconObj.getString("uuid"));
                beacon.put("major", beaconObj.getInt("major"));
                beacon.put("minor", beaconObj.getInt("minor"));
                beacon.put("url", beaconObj.getString("url"));
                
                beaconData.add(beacon);
            }
            
            Log.d(TAG, "Initialized with " + beaconData.size() + " beacons");
            callbackContext.success("Beacon data initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing beacon data", e);
            callbackContext.error("Error initializing beacon data: " + e.getMessage());
        }
    }

    private void startScanning(CallbackContext callbackContext) {
        if (isScanning) {
            callbackContext.success("Already scanning");
            return;
        }
        
        cordova.getThreadPool().execute(() -> {
            try {
                // Check and request permissions
                if (!checkAndRequestPermissions()) {
                    callbackContext.error("Required permissions not granted");
                    return;
                }
                
                // Use null identifiers to detect all beacons
                region = new Region("AllBeaconsRegion", null, null, null);
                Log.d(TAG, "Created region to scan for all beacons");
                
                // Start ranging beacons
                beaconManager.startRangingBeacons(region);
                beaconManager.startMonitoringBeaconsInRegion(region);
                
                isScanning = true;
                Log.d(TAG, "Started scanning for beacons");
                callbackContext.success("Started scanning for beacons");
            } catch (Exception e) {
                Log.e(TAG, "Error starting beacon scanning", e);
                callbackContext.error("Error starting beacon scanning: " + e.getMessage());
            }
        });
    }

    private void stopScanning(CallbackContext callbackContext) {
        if (!isScanning) {
            callbackContext.success("Not scanning");
            return;
        }
        
        cordova.getThreadPool().execute(() -> {
            try {
                if (region != null) {
                    beaconManager.stopRangingBeacons(region);
                    beaconManager.stopMonitoringBeaconsInRegion(region);
                }
                
                isScanning = false;
                Log.d(TAG, "Stopped scanning for beacons");
                callbackContext.success("Stopped scanning for beacons");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping beacon scanning", e);
                callbackContext.error("Error stopping beacon scanning: " + e.getMessage());
            }
        });
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        Log.d(TAG, "didRangeBeaconsInRegion called with " + beacons.size() + " beacons");
        
        if (beaconDetectionCallback == null) {
            Log.e(TAG, "Beacon detection callback is null");
            return;
        }
        
        if (beacons.isEmpty()) {
            Log.d(TAG, "No beacons detected in region");
            return;
        }
        
        for (Beacon beacon : beacons) {
            String uuid = beacon.getId1().toString();
            int major = beacon.getId2().toInt();
            int minor = beacon.getId3().toInt();
            
            Log.d(TAG, "Raw beacon detected: UUID=" + uuid + ", Major=" + major + ", Minor=" + minor + ", RSSI=" + beacon.getRssi());
            
            // Find matching beacon in our data
            boolean foundMatch = false;
            for (Map<String, Object> data : beaconData) {
                if (uuid.equalsIgnoreCase((String) data.get("uuid")) &&
                    major == (int) data.get("major") &&
                    minor == (int) data.get("minor")) {
                    
                    try {
                        JSONObject result = new JSONObject();
                        result.put("title", data.get("title"));
                        result.put("uuid", uuid);
                        result.put("major", major);
                        result.put("minor", minor);
                        result.put("url", data.get("url"));
                        result.put("distance", beacon.getDistance());
                        result.put("rssi", beacon.getRssi());
                        
                        Log.d(TAG, "Matched beacon: " + result.toString());
                        
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                        pluginResult.setKeepCallback(true);
                        beaconDetectionCallback.sendPluginResult(pluginResult);
                        foundMatch = true;
                        break;
                    } catch (JSONException e) {
                        Log.e(TAG, "Error creating JSON result", e);
                    }
                }
            }
            
            if (!foundMatch) {
                Log.d(TAG, "Detected beacon does not match any configured beacons: UUID=" + uuid + ", Major=" + major + ", Minor=" + minor);
            }
        }
    }

    @Override
    public void didEnterRegion(Region region) {
        Log.d(TAG, "Entered region: " + region.getUniqueId());
    }

    @Override
    public void didExitRegion(Region region) {
        Log.d(TAG, "Exited region: " + region.getUniqueId());
    }

    @Override
    public void didDetermineStateForRegion(int state, Region region) {
        Log.d(TAG, "Region state changed: " + state + " for region " + region.getUniqueId());
    }

    private boolean checkAndRequestPermissions() {
        Activity activity = cordova.getActivity();
        boolean needsPermissions = false;
        
        // Check location permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                cordova.requestPermission(this, PERMISSION_REQUEST_COARSE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
                needsPermissions = true;
            }
            
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                cordova.requestPermission(this, PERMISSION_REQUEST_FINE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
                needsPermissions = true;
            }
            
            // For Android 12 (API 31) and above, we need BLUETOOTH_SCAN and BLUETOOTH_CONNECT permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    cordova.requestPermission(this, PERMISSION_REQUEST_BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_SCAN);
                    needsPermissions = true;
                }
                
                if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    cordova.requestPermission(this, PERMISSION_REQUEST_BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_CONNECT);
                    needsPermissions = true;
                }
            }
        }
        
        return !needsPermissions;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted for request code: " + requestCode);
        } else {
            Log.e(TAG, "Permission denied for request code: " + requestCode);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isScanning && region != null) {
            try {
                beaconManager.stopRangingBeacons(region);
                beaconManager.stopMonitoringBeaconsInRegion(region);
                isScanning = false;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping beacon scanning on destroy", e);
            }
        }
    }

    /**
     * Check if the device is compatible with beacon detection
     */
    private void checkCompatibility(CallbackContext callbackContext) {
        try {
            Activity activity = cordova.getActivity();
            JSONObject result = new JSONObject();
            
            // Check Bluetooth support
            boolean hasBluetoothSupport = activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
            result.put("bluetoothSupport", hasBluetoothSupport);
            
            // Check if Bluetooth is enabled
            boolean isBluetoothEnabled = false;
            try {
                android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                isBluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
            } catch (Exception e) {
                Log.e(TAG, "Error checking Bluetooth state", e);
            }
            result.put("bluetoothEnabled", isBluetoothEnabled);
            
            // Check location permissions
            boolean hasLocationPermissions = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hasLocationPermissions = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            }
            result.put("locationPermissions", hasLocationPermissions);
            
            // Check Bluetooth permissions for Android 12+
            boolean hasBluetoothPermissions = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hasBluetoothPermissions = activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                        && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            }
            result.put("bluetoothPermissions", hasBluetoothPermissions);
            
            // Overall compatibility
            boolean isCompatible = hasBluetoothSupport && isBluetoothEnabled && hasLocationPermissions && hasBluetoothPermissions;
            result.put("isCompatible", isCompatible);
            
            callbackContext.success(result);
        } catch (Exception e) {
            Log.e(TAG, "Error checking compatibility", e);
            callbackContext.error("Error checking compatibility: " + e.getMessage());
        }
    }

    /**
     * List currently detected beacons without starting continuous scanning
     */
    private void listDetectedBeacons(CallbackContext callbackContext) {
        if (beaconData.isEmpty()) {
            callbackContext.error("No beacon data initialized. Call initialize() first.");
            return;
        }
        
        cordova.getThreadPool().execute(() -> {
            try {
                // Check permissions
                if (!checkAndRequestPermissions()) {
                    callbackContext.error("Required permissions not granted");
                    return;
                }
                
                // Create a temporary region for a single scan
                Region tempRegion = new Region("TempScanRegion", null, null, null);
                
                // Create a temporary callback for this scan
                RangeNotifier tempRangeNotifier = new RangeNotifier() {
                    @Override
                    public void didRangeBeaconsInRegion(Collection<Beacon> detectedBeacons, Region region) {
                        try {
                            JSONArray beaconArray = new JSONArray();
                            
                            for (Beacon beacon : detectedBeacons) {
                                String uuid = beacon.getId1().toString();
                                int major = beacon.getId2().toInt();
                                int minor = beacon.getId3().toInt();
                                
                                JSONObject beaconObj = new JSONObject();
                                beaconObj.put("uuid", uuid);
                                beaconObj.put("major", major);
                                beaconObj.put("minor", minor);
                                beaconObj.put("distance", beacon.getDistance());
                                beaconObj.put("rssi", beacon.getRssi());
                                
                                // Find matching beacon in our data
                                for (Map<String, Object> data : beaconData) {
                                    if (uuid.equalsIgnoreCase((String) data.get("uuid")) &&
                                        major == (int) data.get("major") &&
                                        minor == (int) data.get("minor")) {
                                        
                                        beaconObj.put("title", data.get("title"));
                                        beaconObj.put("url", data.get("url"));
                                        break;
                                    }
                                }
                                
                                beaconArray.put(beaconObj);
                            }
                            
                            // Stop ranging after getting results
                            beaconManager.stopRangingBeacons(tempRegion);
                            
                            callbackContext.success(beaconArray);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing beacon list", e);
                            callbackContext.error("Error processing beacon list: " + e.getMessage());
                        }
                    }
                };
                
                // Set the temporary range notifier
                beaconManager.addRangeNotifier(tempRangeNotifier);
                
                // Start a single scan
                beaconManager.startRangingBeacons(tempRegion);
                
                // Set a timeout to ensure we return something even if no beacons are found
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.postDelayed(() -> {
                    try {
                        beaconManager.stopRangingBeacons(tempRegion);
                        beaconManager.removeRangeNotifier(tempRangeNotifier);
                        
                        // If callback hasn't been invoked yet, return empty array
                        if (!callbackContext.isFinished()) {
                            callbackContext.success(new JSONArray());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in scan timeout", e);
                    }
                }, 5000); // 5 second timeout
                
            } catch (Exception e) {
                Log.e(TAG, "Error listing beacons", e);
                callbackContext.error("Error listing beacons: " + e.getMessage());
            }
        });
    }
    
    /**
     * Debug method to check beacon scanner status
     */
    private void debugBeaconScanner(CallbackContext callbackContext) {
        JSONObject debug = new JSONObject();
        try {
            debug.put("isScanning", isScanning);
            debug.put("beaconDataCount", beaconData.size());
            debug.put("hasCallback", beaconDetectionCallback != null);
            debug.put("beaconManagerActive", beaconManager != null);
            
            // Check if Bluetooth is enabled
            android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            boolean bluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
            debug.put("bluetoothEnabled", bluetoothEnabled);
            
            // Check permissions
            Activity activity = cordova.getActivity();
            boolean hasLocationPermission = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
            debug.put("hasLocationPermission", hasLocationPermission);
            
            callbackContext.success(debug);
        } catch (Exception e) {
            Log.e(TAG, "Error in debug method", e);
            callbackContext.error("Debug error: " + e.getMessage());
        }
    }
}