// BeaconDetectorInit.js
document.addEventListener('deviceready', function() {
    // Verificar si el plugin está disponible
    if (typeof window.beaconDetector === 'undefined') {
        console.error("El plugin beaconDetector no está disponible. Asegúrate de que el plugin esté correctamente instalado.");
        console.log("Objetos disponibles en window:", Object.keys(window).filter(key => key.toLowerCase().includes('beacon')));
        return;
    }
    
    // Define la interfaz BeaconDetectorPlugin
    window.BeaconDetectorPlugin = {
        initialize: function(beaconData) {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.initialize(beaconData, resolve, reject);
            });
        },
        
        startScanning: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.startScanning(resolve, reject);
            });
        },
        
        stopScanning: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.stopScanning(resolve, reject);
            });
        },
        
        onBeaconDetected: function(callback) {
            console.log("Configurando callback de detección de beacons");
            // Envolvemos el callback original para agregar logs
            const wrappedCallback = function(beacon) {
                console.log("Beacon detectado:", JSON.stringify(beacon));
                callback(beacon);
            };
            window.beaconDetector.onBeaconDetected(wrappedCallback);
        },
        
        isPluginAvailable: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.isPluginAvailable(resolve, reject);
            });
        },
        
        checkCompatibility: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.checkCompatibility(resolve, reject);
            });
        },
        
        listDetectedBeacons: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.listDetectedBeacons(resolve, reject);
            });
        }
    };
    
    console.log("BeaconDetectorPlugin interface initialized successfully");
    
    // Verificar compatibilidad automáticamente
    window.BeaconDetectorPlugin.checkCompatibility()
        .then(function(result) {
            console.log("Compatibilidad del dispositivo:", result);
            if (!result.isCompatible) {
                console.warn("El dispositivo no es totalmente compatible con la detección de beacons");
            }
        })
        .catch(function(error) {
            console.error("Error al verificar compatibilidad:", error);
        });
}, false);