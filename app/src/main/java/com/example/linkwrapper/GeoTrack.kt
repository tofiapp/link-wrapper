package com.example.linkwrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Častější GPS, dokud stránka volá `watchPosition`.
 * Chromium jinak posílá polohu řídce; tady 1 s, high accuracy.
 */
internal object GeoTrack {

    const val INTERVAL_MS = 1_000L
    const val MIN_INTERVAL_MS = 500L

    @SuppressLint("MissingPermission")
    fun startUpdates(context: Context, listener: LocationListener): LocationManager? {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { name ->
            try {
                lm.isProviderEnabled(name)
            } catch (_: Exception) {
                false
            }
        }
        if (providers.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val request = LocationRequest.Builder(INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
                .setMinUpdateDistanceMeters(0f)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .build()
            providers.forEach { name ->
                try {
                    lm.requestLocationUpdates(
                        name,
                        request,
                        ContextCompat.getMainExecutor(context),
                        listener
                    )
                } catch (_: Exception) {
                }
            }
        } else {
            providers.forEach { name ->
                try {
                    lm.requestLocationUpdates(
                        name,
                        INTERVAL_MS,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                } catch (_: Exception) {
                }
            }
        }
        return lm
    }

    fun stopUpdates(manager: LocationManager?, listener: LocationListener) {
        try {
            manager?.removeUpdates(listener)
        } catch (_: Exception) {
        }
    }

    const val BOOTSTRAP_JS = """
(function(){
  if (window.__obalkaGeo) return;
  window.__obalkaGeo = 1;
  var geo = navigator.geolocation;
  if (!geo) return;
  var watches = {};
  var last = null;
  var origWatch = geo.watchPosition.bind(geo);
  var origClear = geo.clearWatch.bind(geo);
  var origGet = geo.getCurrentPosition.bind(geo);
  function high(o){
    var x = {};
    if (o) { for (var k in o) x[k] = o[k]; }
    x.enableHighAccuracy = true;
    x.maximumAge = 0;
    if (x.timeout == null) x.timeout = 15000;
    return x;
  }
  window.__obalkaGeoPush = function(lat, lng, acc, alt, spd, hdg, t){
    last = {
      coords: {
        latitude: lat,
        longitude: lng,
        accuracy: acc,
        altitude: alt,
        altitudeAccuracy: null,
        heading: hdg,
        speed: spd
      },
      timestamp: t || Date.now()
    };
    for (var id in watches) {
      try { watches[id](last); } catch (e) {}
    }
  };
  geo.watchPosition = function(success, error, options){
    var id = origWatch(function(pos){
      last = pos;
      if (typeof success === 'function') success(pos);
    }, error, high(options));
    if (typeof success === 'function') watches[id] = success;
    try { if (window.ObalkaGeo) window.ObalkaGeo.watchStart(); } catch (e) {}
    return id;
  };
  geo.clearWatch = function(id){
    delete watches[id];
    origClear(id);
    try { if (window.ObalkaGeo) window.ObalkaGeo.watchStop(); } catch (e) {}
  };
  geo.getCurrentPosition = function(success, error, options){
    origGet(success, error, high(options));
  };
})();
"""
}
