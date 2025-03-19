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
                
                // Create a region to scan for all beacons
                region = new Region("AllBeaconsRegion", null, null, null);
                
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
        if (beaconDetectionCallback == null || beacons.isEmpty()) {
            return;
        }
        
        for (Beacon beacon : beacons) {
            String uuid = beacon.getId1().toString();
            int major = beacon.getId2().toInt();
            int minor = beacon.getId3().toInt();
            
            // Find matching beacon in our data
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
                        
                        Log.d(TAG, "Detected beacon: " + result.toString());
                        
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                        pluginResult.setKeepCallback(true);
                        beaconDetectionCallback.sendPluginResult(pluginResult);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error creating JSON result", e);
                    }
                }
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
}