// BeaconDetectorInit.js
document.addEventListener('deviceready', function() {
    // Verificar si el plugin está disponible
    if (typeof window.beaconDetector === 'undefined') {
        console.error("El plugin beaconDetector no está disponible. Asegúrate de que el plugin esté correctamente instalado.");
        console.log("Objetos disponibles en window:", Object.keys(window).filter(key => key.toLowerCase().includes('beacon')));
        return;
    }
    
    // Add debouncing functionality
    function debounce(func, wait) {
        let timeout;
        return function() {
            const context = this;
            const args = arguments;
            clearTimeout(timeout);
            timeout = setTimeout(function() {
                func.apply(context, args);
            }, wait);
        };
    }
    
    // Define la interfaz BeaconDetectorPlugin
    window.BeaconDetectorPlugin = {
        // Add a flag to track if operations are in progress
        _operationInProgress: false,
        
        initialize: function(beaconData) {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.initialize(beaconData, resolve, reject);
            });
        },
        
        startScanning: debounce(function() {
            if (this._operationInProgress) {
                console.warn("Operation already in progress, please wait");
                return Promise.reject("Operation already in progress");
            }
            
            this._operationInProgress = true;
            return new Promise((resolve, reject) => {
                window.beaconDetector.startScanning(
                    (result) => {
                        this._operationInProgress = false;
                        resolve(result);
                    },
                    (error) => {
                        this._operationInProgress = false;
                        reject(error);
                    }
                );
            });
        }, 5000), // 5 second debounce
        
        stopScanning: debounce(function() {
            if (this._operationInProgress) {
                console.warn("Operation already in progress, please wait");
                return Promise.reject("Operation already in progress");
            }
            
            this._operationInProgress = true;
            return new Promise((resolve, reject) => {
                window.beaconDetector.stopScanning(
                    (result) => {
                        this._operationInProgress = false;
                        resolve(result);
                    },
                    (error) => {
                        this._operationInProgress = false;
                        reject(error);
                    }
                );
            });
        }, 5000), // 5 second debounce
        
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
        },
        
        // Añadido coma para corregir el error de sintaxis
        debugBeaconScanner: function() {
            return new Promise(function(resolve, reject) {
                // Corregido para usar el método correcto
                window.beaconDetector.exec(function(result) {
                    console.log("Debug scanner result:", result);
                    resolve(result);
                }, function(error) {
                    console.error("Debug scanner error:", error);
                    reject(error);
                }, 'BeaconDetector', 'debugBeaconScanner', []);
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