package fr.free.nrw.commons.location;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;

import timber.log.Timber;

public class LocationServiceManager implements LocationListener {
    public static final int LOCATION_REQUEST = 1;

    private static final long MIN_LOCATION_UPDATE_REQUEST_TIME_IN_MILLIS = 2 * 60 * 1000;
    private static final long MIN_LOCATION_UPDATE_REQUEST_DISTANCE_IN_METERS = 10;

    private Context context;
    private LocationManager locationManager;
    private Location lastLocation;
    private final List<LocationUpdateListener> locationListeners = new CopyOnWriteArrayList<>();
    private boolean isLocationManagerRegistered = false;

    public LocationServiceManager(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean isProviderEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    public boolean isLocationPermissionGranted() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermissions(Activity activity) {
        if (activity.isFinishing()) {
            return;
        }
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_REQUEST);
    }

    public boolean isPermissionExplanationRequired(Activity activity) {
        if (activity.isFinishing()) {
            return false;
        }
        return ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public LatLng getLastLocation() {
        if (lastLocation == null) {
            return null;
        }
        return LatLng.from(lastLocation);
    }

    /** Registers a LocationManager to listen for current location.
     */
    public void registerLocationManager() {
        if (!isLocationManagerRegistered)
            isLocationManagerRegistered = requestLocationUpdatesFromProvider(LocationManager.NETWORK_PROVIDER)
                    && requestLocationUpdatesFromProvider(LocationManager.GPS_PROVIDER);
    }

    private boolean requestLocationUpdatesFromProvider(String locationProvider) {
        try {
            locationManager.requestLocationUpdates(locationProvider,
                    MIN_LOCATION_UPDATE_REQUEST_TIME_IN_MILLIS,
                    MIN_LOCATION_UPDATE_REQUEST_DISTANCE_IN_METERS,
                    this);
            return true;
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Illegal argument exception");
            return false;
        } catch (SecurityException e) {
            Timber.e(e, "Security exception");
            return false;
        }
    }

    protected LocationChangeType isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return LocationChangeType.LOCATION_SIGNIFICANTLY_CHANGED;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > MIN_LOCATION_UPDATE_REQUEST_TIME_IN_MILLIS;
        boolean isSignificantlyOlder = timeDelta < -MIN_LOCATION_UPDATE_REQUEST_TIME_IN_MILLIS;
        boolean isNewer = timeDelta > 0;

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        float[] results = new float[5];
        Location.distanceBetween(
                        currentBestLocation.getLatitude(),
                        currentBestLocation.getLongitude(),
                        location.getLatitude(),
                        location.getLongitude(),
                        results);

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer
                || isMoreAccurate
                || (isNewer && !isLessAccurate)
                || (isNewer && !isSignificantlyLessAccurate && isFromSameProvider)) {
            Log.d("deneme","distance:"+results[0]);
            if (results[0] < 1000) { // Means change is smaller than 1000 meter
                return LocationChangeType.LOCATION_SLIGHTLY_CHANGED;
            } else {
                return LocationChangeType.LOCATION_SIGNIFICANTLY_CHANGED;
            }
            // If the new location is more than two minutes older, it must be worse
        } else{
            Log.d("deneme","distance:"+results[0]);
            return LocationChangeType.LOCATION_NOT_CHANGED;
        }

    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    /** Unregisters location manager.
     */
    public void unregisterLocationManager() {
        isLocationManagerRegistered = false;
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            Timber.e(e, "Security exception");
        }
    }

    public void addLocationListener(LocationUpdateListener listener) {
        if (!locationListeners.contains(listener)) {
            locationListeners.add(listener);
        }
    }

    public void removeLocationListener(LocationUpdateListener listener) {
        locationListeners.remove(listener);
    }

    @Override
    public void onLocationChanged(Location location) {
            Log.d("deneme","location changed");
            if (isBetterLocation(location, lastLocation)
                    .equals(LocationChangeType.LOCATION_SIGNIFICANTLY_CHANGED)) {
                Log.d("deneme","location changed better location");
                lastLocation = location;
                for (LocationUpdateListener listener : locationListeners) {
                    listener.onLocationChangedSignificantly(LatLng.from(lastLocation));
                }
            } else if (isBetterLocation(location, lastLocation)
                    .equals(LocationChangeType.LOCATION_SLIGHTLY_CHANGED)) {
                Log.d("deneme","location changed better location");
                lastLocation = location;
                for (LocationUpdateListener listener : locationListeners) {
                    listener.onLocationChangedSlightly(LatLng.from(lastLocation));
                }
            }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Timber.d("%s's status changed to %d", provider, status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Timber.d("Provider %s enabled", provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Timber.d("Provider %s disabled", provider);
    }

    public enum LocationChangeType{
        LOCATION_SIGNIFICANTLY_CHANGED,
        LOCATION_SLIGHTLY_CHANGED,
        LOCATION_NOT_CHANGED
    }
}
