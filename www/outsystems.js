// Inicializa el valor de Message para que nunca esté vacío
$parameters.Message = "Inicializando detector de beacons...";
// Actualiza el elemento de la interfaz
$actions.UpdateFeedbackMessage("Inicializando detector de beacons...");

// Asegúrate de que el dispositivo esté listo antes de usar el plugin
if (typeof cordova !== 'undefined') {
    $parameters.Message = "Cordova detectado, esperando a que el dispositivo esté listo...";
    $actions.UpdateFeedbackMessage("Cordova detectado, esperando a que el dispositivo esté listo...");
    
    document.addEventListener('deviceready', function() {
        $parameters.Message = "Dispositivo listo, verificando disponibilidad del plugin...";
        $actions.UpdateFeedbackMessage("Dispositivo listo, verificando disponibilidad del plugin...");
        
        // Verifica que BeaconDetectorPlugin esté disponible después de que BeaconDetectorInit.js lo haya inicializado
        setTimeout(function() {
            if (window.BeaconDetectorPlugin) {
                $parameters.Message = "Plugin detectado, verificando compatibilidad...";
                $actions.UpdateFeedbackMessage("Plugin detectado, verificando compatibilidad...");
                
                // Verificar compatibilidad del dispositivo primero
                BeaconDetectorPlugin.checkCompatibility()
                    .then(function(result) {
                        if (!result.isCompatible) {
                            throw new Error("El dispositivo no es compatible con la detección de beacons: " + 
                                  (result.bluetoothSupport ? "" : "No soporta Bluetooth. ") +
                                  (result.bluetoothEnabled ? "" : "Bluetooth desactivado. ") +
                                  (result.locationPermissions ? "" : "Permisos de ubicación no concedidos. ") +
                                  (result.bluetoothPermissions ? "" : "Permisos de Bluetooth no concedidos."));
                        }
                        
                        $parameters.Message = "Dispositivo compatible, preparando datos de beacons...";
                        $actions.UpdateFeedbackMessage("Dispositivo compatible, preparando datos de beacons...");
                        
                        // Define tus datos de beacons
                        var beaconData = [
                          {
                            "title": "Audio",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 11,
                            "url": "https://miportal.entel.cl/personas/catalogo/accesorios/audio"
                          },
                          {
                            "title": "Energía y protección para tú equipo",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 12,
                            "url": "https://miportal.entel.cl/personas/catalogo/accesorios/_/N-1z140lnZ1z140faZ1z0of6i"
                          },
                          {
                            "title": "Dispositivos Apple",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 13,
                            "url": "https://miportal.entel.cl/personas/catalogo/celulares/_/N-1z141c1"
                          },
                          {
                            "title": "Dispositivos Samsung",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 14,
                            "url": "https://miportal.entel.cl/personas/catalogo/celulares/_/N-1z1416i"
                          }
                        ];

                        $parameters.Message = "Datos de beacons preparados, inicializando plugin...";
                        $actions.UpdateFeedbackMessage("Datos de beacons preparados, inicializando plugin...");
                        
                        // Inicializa el plugin
                        return BeaconDetectorPlugin.initialize(beaconData);
                    })
                    .then(function() {
                        $parameters.Message = "Plugin inicializado correctamente, comenzando escaneo...";
                        $actions.UpdateFeedbackMessage("Plugin inicializado correctamente, comenzando escaneo...");
                        
                        // Comienza a escanear beacons SOLO UNA VEZ
                        return BeaconDetectorPlugin.startScanning();
                    })
                    .then(function() {
                        $parameters.Message = "Escaneo de beacons iniciado correctamente. Esperando detección...";
                        $actions.UpdateFeedbackMessage("Escaneo de beacons iniciado correctamente. Esperando detección...");
                        
                        // Variable para controlar si ya se ha encontrado un beacon coincidente
                        var beaconFound = false;
                        
                        // Añadir un temporizador para verificar periódicamente los beacons detectados
                        var checkInterval = setInterval(function() {
                            // Usamos listDetectedBeacons para obtener los beacons actuales sin iniciar un nuevo escaneo
                            BeaconDetectorPlugin.listDetectedBeacons()
                                .then(function(beacons) {
                                    if (beacons && beacons.length > 0) {
                                        // Mostrar información detallada de cada beacon encontrado
                                        var beaconInfo = "Se encontraron " + beacons.length + " beacons cercanos:\n";
                                        
                                        beacons.forEach(function(beacon, index) {
                                            // Destacar UUID, Major y Minor en el mensaje
                                            beaconInfo += "\n" + (index + 1) + ". UUID: " + beacon.uuid + 
                                                ", Major: " + beacon.major + 
                                                ", Minor: " + beacon.minor +
                                                (beacon.title ? " (" + beacon.title + ")" : "") +
                                                ", Distancia: " + (beacon.distance ? beacon.distance.toFixed(2) + "m" : "desconocida");
                                        });
                                        
                                        $parameters.Message = beaconInfo;
                                        $actions.UpdateFeedbackMessage(beaconInfo);
                                        console.log("Beacons detectados:", JSON.stringify(beacons));
                                        
                                        // Si hay un beacon que coincide con nuestros datos configurados, redirigir
                                        var matchedBeacon = null;
                                        for (var i = 0; i < beacons.length; i++) {
                                            var detectedBeacon = beacons[i];
                                            
                                            // Buscar en nuestros datos configurados
                                            for (var j = 0; j < beaconData.length; j++) {
                                                var configBeacon = beaconData[j];
                                                
                                                if (detectedBeacon.uuid && 
                                                    detectedBeacon.uuid.toLowerCase() === configBeacon.uuid.toLowerCase() &&
                                                    parseInt(detectedBeacon.major) === parseInt(configBeacon.major) &&
                                                    parseInt(detectedBeacon.minor) === parseInt(configBeacon.minor)) {
                                                    
                                                    console.log("¡Coincidencia encontrada! Beacon detectado:", detectedBeacon);
                                                    console.log("Beacon configurado:", configBeacon);
                                                    
                                                    // Encontramos una coincidencia, usar la URL del beacon configurado
                                                    matchedBeacon = configBeacon;
                                                    
                                                    // Enviar el UUID del beacon a OutSystems como parámetro
                                                    $parameters.BeaconUUID = detectedBeacon.uuid;
                                                    $parameters.BeaconMajor = detectedBeacon.major;
                                                    $parameters.BeaconMinor = detectedBeacon.minor;
                                                    $parameters.BeaconTitle = configBeacon.title;
                                                    $parameters.BeaconURL = configBeacon.url;
                                                    
                                                    break;
                                                }
                                            }
                                            
                                            if (matchedBeacon) break;
                                        }
                                        
                                        if (matchedBeacon && matchedBeacon.url) {
                                            // Detener el intervalo de verificación cuando se detecta un beacon con URL
                                            clearInterval(checkInterval);
                                            
                                            // Muestra mensaje de redirección
                                            var redirectMsg = "Beacon coincidente encontrado: " + matchedBeacon.title + 
                                                             ". Redirigiendo a: " + matchedBeacon.url;
                                            $parameters.Message = redirectMsg;
                                            $actions.UpdateFeedbackMessage(redirectMsg);
                                            console.log("Redirigiendo a beacon:", JSON.stringify(matchedBeacon));
                                            
                                            // Pequeña pausa antes de redireccionar
                                            setTimeout(function() {
                                                window.location.href = matchedBeacon.url;
                                            }, 3000);
                                        }
                                    } else {
                                        console.log("No se detectaron beacons en esta verificación");
                                    }
                                })
                                .catch(function(error) {
                                    console.error("Error al listar beacons:", error);
                                });
                        }, 5000); // Verificar cada 5 segundos
                        
                        // Configura el callback de detección de beacons - SOLO SE CONFIGURA UNA VEZ
                        BeaconDetectorPlugin.onBeaconDetected(function(beacon) {
                            // Detener el intervalo de verificación cuando se detecta un beacon
                            clearInterval(checkInterval);
                            
                            // Mensaje enfocado en UUID, Major y Minor
                            var mensaje = "Beacon detectado - UUID: " + beacon.uuid + ", Major: " + beacon.major + ", Minor: " + beacon.minor;
                            // Reemplazar cualquier código de redirección automática con solo manejo de datos
                            function handleBeaconDetection(beacon) {
                            // Actualizar el mensaje con información del beacon
                            $parameters.Message = "Beacon detectado: " + (beacon.title || "Desconocido");
                            $actions.UpdateFeedbackMessage("Beacon detectado: " + (beacon.title || "Desconocido"));
                            
                            // Guardar la información del beacon para uso posterior
                            $parameters.DetectedBeacon = beacon;
                            
                            // Notificar a la aplicación sobre el beacon detectado (si es necesario)
                            if (typeof $actions.OnBeaconDetected === 'function') {
                                $actions.OnBeaconDetected(beacon);
                            }
                            
                            // No realizar redirección automática
                            }
                            $parameters.BeaconUUID = beacon.uuid;
                            $parameters.BeaconMajor = beacon.major;
                            $parameters.BeaconMinor = beacon.minor;
                            $parameters.BeaconTitle = beacon.title;
                            $parameters.BeaconURL = beacon.url;
                            
                            // Muestra mensaje de redirección que incluye los identificadores del beacon
                            $parameters.Message = "UUID: " + beacon.uuid + ", Major: " + beacon.major + ", Minor: " + beacon.minor + " - Redirigiendo a: " + beacon.url;
                            $actions.UpdateFeedbackMessage("UUID: " + beacon.uuid + ", Major: " + beacon.major + ", Minor: " + beacon.minor + " - Redirigiendo a: " + beacon.url);
                            
                            // Pequeña pausa antes de redireccionar para que el usuario pueda ver el mensaje
                            setTimeout(function() {
                                // Redirecciona a la URL
                                window.location.href = beacon.url;
                            }, 1500);
                        });
                    })
                    .catch(function(error) {
                        var errorMsg = "Error en la detección de beacons: " + error;
                        $parameters.Message = errorMsg;
                        $actions.UpdateFeedbackMessage(errorMsg);
                        
                        // Añadir diagnóstico adicional
                        if (window.BeaconDetectorPlugin && window.BeaconDetectorPlugin.debugBeaconScanner) {
                            BeaconDetectorPlugin.debugBeaconScanner()
                                .then(function(debugInfo) {
                                    console.log("Información de diagnóstico:", JSON.stringify(debugInfo));
                                    $parameters.Message = errorMsg + " (Ver consola para diagnóstico)";
                                    $actions.UpdateFeedbackMessage(errorMsg + " (Ver consola para diagnóstico)");
                                })
                                .catch(function(debugError) {
                                    console.error("Error en diagnóstico:", debugError);
                                });
                        }
                    });
            } else {
                var errorMsg = "BeaconDetectorPlugin no está disponible. Asegúrate de que el plugin esté correctamente instalado.";
                $parameters.Message = errorMsg;
                $actions.UpdateFeedbackMessage(errorMsg);
                console.error("Objetos disponibles en window:", Object.keys(window).filter(key => key.toLowerCase().includes('beacon')));
            }
        }, 500); // Pequeño retraso para asegurar que BeaconDetectorInit.js haya terminado
    }, false);
} else {
    var errorMsg = "Cordova no está disponible. Esta acción solo funciona en dispositivos móviles.";
    $parameters.Message = errorMsg;
    $actions.UpdateFeedbackMessage(errorMsg);
}
