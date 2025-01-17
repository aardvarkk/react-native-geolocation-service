package com.agontuk.RNFusedLocation;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter;

import java.util.Collection;
import java.util.HashMap;

public class RNFusedLocationModule extends ReactContextBaseJavaModule implements ActivityEventListener {
  public static final String TAG = "RNFusedLocation";
  private int singleLocationProviderKeyCounter = 1;
  private final HashMap<String, LocationProvider> singleLocationProviders;
  @Nullable private LocationProvider continuousLocationProvider;

  public RNFusedLocationModule(ReactApplicationContext reactContext) {
    super(reactContext);

    reactContext.addActivityEventListener(this);
    this.singleLocationProviders = new HashMap<>();

    Log.i(TAG, TAG + " initialized");
  }

  @NonNull
  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    Log.i(TAG, "onActivityResult");

    if (continuousLocationProvider != null &&
      continuousLocationProvider.onActivityResult(requestCode, resultCode)
    ) {
      Log.i(TAG, "continuousLocationProvider");
      return;
    }

    Log.i(TAG, "singleLocationProviders");

    Collection<LocationProvider> providers = singleLocationProviders.values();

    for (LocationProvider locationProvider: providers) {
      if (locationProvider.onActivityResult(requestCode, resultCode)) {
        return;
      }
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    //
  }

  @ReactMethod
  public void getCurrentPosition(ReadableMap options, final Callback success, final Callback error) {
    ReactApplicationContext context = getContext();

    if (!LocationUtils.hasLocationPermission(context)) {
      error.invoke(LocationUtils.buildError(LocationError.PERMISSION_DENIED, null));
      return;
    }

    LocationOptions locationOptions = LocationOptions.fromReadableMap(options);
    final LocationProvider locationProvider = createLocationProvider(locationOptions.isForceLocationManager());

    final String key = "provider-" + singleLocationProviderKeyCounter;
    singleLocationProviders.put(key, locationProvider);
    singleLocationProviderKeyCounter++;

    locationProvider.getCurrentLocation(locationOptions, new LocationChangeListener() {
      @Override
      public void onLocationChange(Location location) {
        success.invoke(LocationUtils.locationToMap(location));
        singleLocationProviders.remove(key);
      }

      @Override
      public void onLocationError(LocationError locationError, @Nullable String message) {
        error.invoke(LocationUtils.buildError(locationError, message));
        singleLocationProviders.remove(key);
      }
    });
  }

  @ReactMethod
  public void startObserving(ReadableMap options) {
    ReactApplicationContext context = getContext();

    Log.i(TAG, "startObserving");

    if (!LocationUtils.hasLocationPermission(context)) {
      emitEvent(
        "geolocationError",
        LocationUtils.buildError(LocationError.PERMISSION_DENIED, null)
      );
      return;
    }

    LocationOptions locationOptions = LocationOptions.fromReadableMap(options);
    Log.i(TAG, options.toString());

    if (continuousLocationProvider == null) {
      continuousLocationProvider = createLocationProvider(locationOptions.isForceLocationManager());
      Log.i(TAG, "created continuousLocationProvider");
    }

    continuousLocationProvider.requestLocationUpdates(locationOptions, new LocationChangeListener() {
      @Override
      public void onLocationChange(Location location) {
        emitEvent("geolocationDidChange", LocationUtils.locationToMap(location));
      }

      @Override
      public void onLocationError(LocationError error, @Nullable String message) {
        emitEvent("geolocationError", LocationUtils.buildError(error, message));
      }
    });

    Log.i(TAG, "requestLocationUpdates");
  }

  @ReactMethod
  public void stopObserving() {
    if (continuousLocationProvider != null) {
      continuousLocationProvider.removeLocationUpdates();
      continuousLocationProvider = null;
    }
  }

  private LocationProvider createLocationProvider(boolean forceLocationManager) {
    ReactApplicationContext context = getContext();
    boolean playServicesAvailable = LocationUtils.isGooglePlayServicesAvailable(context);

    if (forceLocationManager || !playServicesAvailable) {
      Log.i(TAG, "force location or no play services");
      return new LocationManagerProvider(context);
    }

    Log.i(TAG, "fused location provider");
    return new FusedLocationProvider(context);
  }

  private void emitEvent(String eventName, WritableMap data) {
    getContext().getJSModule(RCTDeviceEventEmitter.class).emit(eventName, data);
  }

  private ReactApplicationContext getContext() {
    return getReactApplicationContext();
  }
}
