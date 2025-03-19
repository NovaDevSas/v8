var exec = require('cordova/exec');

var BeaconDetector = {
    /**
     * Initialize the beacon detector with beacon data
     * @param {Array} beaconData - Array of beacon objects with uuid, major, minor, and url
     * @param {Function} successCallback - Success callback
     * @param {Function} errorCallback - Error callback
     */
    initialize: function(beaconData, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'initialize', [beaconData]);
    },
    
    /**
     * Start scanning for beacons
     * @param {Function} successCallback - Success callback
     * @param {Function} errorCallback - Error callback
     */
    startScanning: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'startScanning', []);
    },
    
    /**
     * Stop scanning for beacons
     * @param {Function} successCallback - Success callback
     * @param {Function} errorCallback - Error callback
     */
    stopScanning: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'stopScanning', []);
    },
    
    /**
     * Set callback for beacon detection events
     * @param {Function} callback - Callback function that receives beacon data
     */
    onBeaconDetected: function(callback) {
        exec(callback, function(error) {
            console.error('Error in beacon detection callback: ' + error);
        }, 'BeaconDetector', 'onBeaconDetected', []);
    }
};

module.exports = BeaconDetector;