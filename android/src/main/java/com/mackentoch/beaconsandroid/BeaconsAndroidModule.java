package com.mackentoch.beaconsandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeaconsAndroidModule extends ReactContextBaseJavaModule implements BeaconConsumer {
  private static final String LOG_TAG = "BeaconsAndroidModule";
  private static final int RUNNING_AVG_RSSI_FILTER = 0;
  private static final int ARMA_RSSI_FILTER = 1;
  private BeaconManager mBeaconManager;
  private Context mApplicationContext;
  private ReactApplicationContext mReactContext;

  private static final String NOTIFICATION_CHANNEL_ID = "BeaconsAndroidModule";
  private static final String NOTIFICATION_CHANNEL_NAME = "BeaconsAndroidModule";
  private static final String NOTIFICATION_CHANNEL_DESCRIPTION = "BeaconsAndroidModule";
  private static final int NOTIFICATION_FOREGROUND_SCANNING_ID = 456;
  private NotificationChannel notificationChannel = null;
  private Region MyRegion = null;
  private Notification.Builder builder = null;
  private String debugApi = null;
  private String requestToken = null;
  private String beaconRequestApi = null;
  private NotificationManager mNotificationManager;

  public BeaconsAndroidModule(ReactApplicationContext reactContext) {
    super(reactContext);
    Log.d(LOG_TAG, "BeaconsAndroidModule - started");
    this.mReactContext = reactContext;
  }

  @Override
  public void initialize() {
    this.mApplicationContext = this.mReactContext.getApplicationContext();
    this.mBeaconManager = BeaconManager.getInstanceForApplication(mApplicationContext);
    // need to bind at instantiation so that service loads (to test more)
    mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24")); // AltBeacon
    mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")); // IBeacon
    mNotificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
    this.builder = this.buildNotification();
    // mBeaconManager.enableForegroundServiceScanning(builder.build(), 456);
    // For the above foreground scanning service to be useful, you need to disable
    // JobScheduler-based scans (used on Android 8+) and set a fast background scan
    // cycle that would otherwise be disallowed by the operating system.
    //
    mBeaconManager.setEnableScheduledScanJobs(false);
    mBeaconManager.setBackgroundBetweenScanPeriod(0);
    mBeaconManager.setBackgroundScanPeriod(1100);

    bindManager();

    // Fix beacon empty when screen off
    ScanFilter.Builder builder = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder = new ScanFilter.Builder();
      builder.setManufacturerData(0x004c, new byte[]{});
      ScanFilter filter = builder.build();
    }
  }

  @Override
  public String getName() {
    return LOG_TAG;
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("SUPPORTED", BeaconTransmitter.SUPPORTED);
    constants.put("NOT_SUPPORTED_MIN_SDK", BeaconTransmitter.NOT_SUPPORTED_MIN_SDK);
    constants.put("NOT_SUPPORTED_BLE", BeaconTransmitter.NOT_SUPPORTED_BLE);
    constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS);
    constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER);
    constants.put("RUNNING_AVG_RSSI_FILTER", RUNNING_AVG_RSSI_FILTER);
    constants.put("ARMA_RSSI_FILTER", ARMA_RSSI_FILTER);
    return constants;
  }

  @ReactMethod
  public void setHardwareEqualityEnforced(Boolean e) {
    Beacon.setHardwareEqualityEnforced(e.booleanValue());
  }

  public void bindManager() {
    if (!mBeaconManager.isBound(this)) {
      Log.d(LOG_TAG, "BeaconsAndroidModule - bindManager: ");
      mBeaconManager.bind(this);
    }
  }

  public void unbindManager() {
    if (mBeaconManager.isBound(this)) {
      Log.d(LOG_TAG, "BeaconsAndroidModule - unbindManager: ");
      mBeaconManager.unbind(this);
    }
  }

  @ReactMethod
  public void addParser(String parser, Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - addParser: " + parser);
      unbindManager();
      mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
      bindManager();
      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void removeParser(String parser, Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - removeParser: " + parser);
      unbindManager();
      mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
      bindManager();
      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void addParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
    try {
      unbindManager();
      for (int i = 0; i < parsers.size(); i++) {
        String parser = parsers.getString(i);
        Log.d(LOG_TAG, "addParsersListToDetection - add parser: " + parser);
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
      }
      bindManager();
      resolve.invoke(parsers);
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void removeParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
    try {
      unbindManager();
      for (int i = 0; i < parsers.size(); i++) {
        String parser = parsers.getString(i);
        Log.d(LOG_TAG, "removeParsersListToDetection - remove parser: " + parser);
        mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
      }
      bindManager();
      resolve.invoke(parsers);
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void setBackgroundScanPeriod(int period) {
    mBeaconManager.setBackgroundScanPeriod((long) period);
  }

  @ReactMethod
  public void setBackgroundBetweenScanPeriod(int period) {
    mBeaconManager.setBackgroundBetweenScanPeriod((long) period);
  }

  @ReactMethod
  public void setForegroundScanPeriod(int period) {
    mBeaconManager.setForegroundScanPeriod((long) period);
  }

  @ReactMethod
  public void setForegroundBetweenScanPeriod(int period) {
    mBeaconManager.setForegroundBetweenScanPeriod((long) period);
  }

  @ReactMethod
  public void setRssiFilter(int filterType, double avgModifier) {
    String logMsg = "Could not set the rssi filter.";
    if (filterType == RUNNING_AVG_RSSI_FILTER) {
      logMsg = "Setting filter RUNNING_AVG";
      BeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
      if (avgModifier > 0) {
        RunningAverageRssiFilter.setSampleExpirationMilliseconds((long) avgModifier);
        logMsg += " with custom avg modifier";
      }
    } else if (filterType == ARMA_RSSI_FILTER) {
      logMsg = "Setting filter ARMA";
      BeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
      if (avgModifier > 0) {
        ArmaRssiFilter.setDEFAULT_ARMA_SPEED(avgModifier);
        logMsg += " with custom avg modifier";
      }
    }
    Log.d(LOG_TAG, logMsg);
  }

  @ReactMethod
  public void checkTransmissionSupported(Callback callback) {
    int result = BeaconTransmitter.checkTransmissionSupported(mReactContext);
    callback.invoke(result);
  }

  @ReactMethod
  public void getMonitoredRegions(Callback callback) {
    WritableArray array = new WritableNativeArray();
    for (Region region : mBeaconManager.getMonitoredRegions()) {
      WritableMap map = new WritableNativeMap();
      map.putString("identifier", region.getUniqueId());
      map.putString("uuid", region.getId1().toString());
      map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
      map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
      array.pushMap(map);
    }
    callback.invoke(array);
  }

  @ReactMethod
  public void getRangedRegions(Callback callback) {
    WritableArray array = new WritableNativeArray();
    for (Region region : mBeaconManager.getRangedRegions()) {
      WritableMap map = new WritableNativeMap();
      map.putString("region", region.getUniqueId());
      map.putString("uuid", region.getId1().toString());
      array.pushMap(map);
    }
    callback.invoke(array);
  }

  @Override
  public void onCatalystInstanceDestroy() {
    Log.d(LOG_TAG, "Destroyed");

    try {
      if (MyRegion != null) {
        mBeaconManager.stopMonitoringBeaconsInRegion(MyRegion);
        mBeaconManager.stopRangingBeaconsInRegion(MyRegion);
      }

      mBeaconManager.removeMonitorNotifier(mMonitorNotifier);
      mBeaconManager.removeRangeNotifier(mRangeNotifier);
      mBeaconManager.removeAllMonitorNotifiers();
      mBeaconManager.removeAllRangeNotifiers();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        mNotificationManager.deleteNotificationChannel(notificationChannel.getId());
        notificationChannel = null;
      }

      unbindManager();
      mBeaconManager.disableForegroundServiceScanning();
      mNotificationManager.cancel(NOTIFICATION_FOREGROUND_SCANNING_ID);

    } catch (RemoteException e) {
      e.printStackTrace();
    }

    super.onCatalystInstanceDestroy();
  }

  /***********************************************************************************************
   * BeaconConsumer
   **********************************************************************************************/
  @Override
  public void onBeaconServiceConnect() {
    Log.v(LOG_TAG, "onBeaconServiceConnect");

    // deprecated since v2.9 (see github: https://github.com/AltBeacon/android-beacon-library/releases/tag/2.9)
    // mBeaconManager.setMonitorNotifier(mMonitorNotifier);
    // mBeaconManager.setRangeNotifier(mRangeNotifier);
    mBeaconManager.addMonitorNotifier(mMonitorNotifier);
    mBeaconManager.addRangeNotifier(mRangeNotifier);
    sendEvent(mReactContext, "beaconServiceConnected", null);
  }

  @Override
  public Context getApplicationContext() {
    return mApplicationContext;
  }

  @Override
  public void unbindService(ServiceConnection serviceConnection) {
    mApplicationContext.unbindService(serviceConnection);
  }

  @Override
  public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
    return mApplicationContext.bindService(intent, serviceConnection, i);
  }

  /***********************************************************************************************
   * Monitoring
   **********************************************************************************************/
  @ReactMethod
  public void startMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
    Log.d(LOG_TAG, "startMonitoring, monitoringRegionId: " + regionId + ", monitoringBeaconUuid: " + beaconUuid + ", minor: " + minor + ", major: " + major);
    try {
      Region region = createRegion(
        regionId,
        beaconUuid,
        String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
        String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
      );
      mBeaconManager.startMonitoringBeaconsInRegion(region);
      MyRegion = region;
      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "startMonitoring, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  private MonitorNotifier mMonitorNotifier = new MonitorNotifier() {
    @Override
    public void didEnterRegion(Region region) {
      Log.i(LOG_TAG, "regionDidEnter");
      sendEvent(mReactContext, "regionDidEnter", createMonitoringResponse(region));

      sendDebug(new JSONObject() {{
        try {
          put("device", "android");
          put("message", "EnterRegion");
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }});

      try {
        mBeaconManager.startRangingBeaconsInRegion(MyRegion);
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void didExitRegion(Region region) {
      Log.i(LOG_TAG, "didExitRegion");
      sendEvent(mReactContext, "regionDidExit", createMonitoringResponse(region));

      sendDebug(new JSONObject() {{
        try {
          put("device", "android");
          put("message", "ExitRegion");
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }});

      sendBeacon(null);

      try {
        mBeaconManager.stopRangingBeaconsInRegion(MyRegion);
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void didDetermineStateForRegion(int i, Region region) {
      Log.i(LOG_TAG, "didDetermineStateForRegion");
      String state = "unknown";
      switch (i) {
        case MonitorNotifier.INSIDE:
          state = "inside";
          break;
        case MonitorNotifier.OUTSIDE:
          state = "outside";
          break;
        default:
          break;
      }
      WritableMap map = createMonitoringResponse(region);
      map.putString("state", state);
      sendEvent(mReactContext, "didDetermineState", map);

      sendDebug(new JSONObject() {{
        try {
          put("device", "android");
          put("message", "DetermineState");
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }});
    }
  };

  private WritableMap createMonitoringResponse(Region region) {
    WritableMap map = new WritableNativeMap();
    map.putString("identifier", region.getUniqueId());
    map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
    map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
    map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
    return map;
  }

  @ReactMethod
  public void stopMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
    Region region = createRegion(
      regionId,
      beaconUuid,
      String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
      String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
      // minor,
      // major
    );

    try {
      mBeaconManager.stopMonitoringBeaconsInRegion(region);
      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "stopMonitoring, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  /***********************************************************************************************
   * Ranging
   **********************************************************************************************/
  @ReactMethod
  public void startRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
    Log.d(LOG_TAG, "startRanging, rangingRegionId: " + regionId + ", rangingBeaconUuid: " + beaconUuid);
    try {
      Region region = createRegion(regionId, beaconUuid);
      mBeaconManager.startRangingBeaconsInRegion(region);
      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "startRanging, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  private RangeNotifier mRangeNotifier = new RangeNotifier() {
    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
      Log.d(LOG_TAG, "rangingConsumer didRangeBeaconsInRegion, beacons: " + beacons.toString());
      Log.d(LOG_TAG, "rangingConsumer didRangeBeaconsInRegion, region: " + region.toString());
      sendEvent(mReactContext, "beaconsDidRange", createRangingResponse(beacons, region));

      final JSONArray beaconArray = new JSONArray();
      for (final Beacon beacon : beacons) {
        beaconArray.put(new JSONObject() {{
          try {
            put("uuid", beacon.getId1() != null ? beacon.getId1().toString() : "");
            put("major", beacon.getId2() != null ? beacon.getId2().toInt() : 0);
            put("minor", beacon.getId3() != null ? beacon.getId3().toInt() : 0);
            put("rssi", beacon.getRssi());
            if (beacon.getDistance() == Double.POSITIVE_INFINITY
              || Double.isNaN(beacon.getDistance())
              || beacon.getDistance() == Double.NaN
              || beacon.getDistance() == Double.NEGATIVE_INFINITY) {
              put("distance", 999.0);
              put("proximity", "far");
            } else {
              put("distance", beacon.getDistance());
              put("proximity", getProximity(beacon.getDistance()));
            }
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }});
      }

      JSONArray sortedBeaconArray = null;
      try {
        sortedBeaconArray = this.sort(beaconArray, "distance", true);
      } catch (JSONException e) {
        e.printStackTrace();
      }

      if (sortedBeaconArray != null) {
        Log.d(LOG_TAG, "SortedBeaconArray: " + sortedBeaconArray.toString());
      } else {
        Log.d(LOG_TAG, "SortedBeaconArray: []");
      }


      final JSONArray finalSortedBeaconArray = sortedBeaconArray;
      sendDebug(new JSONObject() {{
        try {
          put("device", "android");
          put("message", "didRangeBeacons");
          put("beacons", finalSortedBeaconArray);
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }});

      JSONObject nearestBeacon = null;

      if (finalSortedBeaconArray != null) {
        try {
          nearestBeacon = (JSONObject) finalSortedBeaconArray.get(0);
        } catch (JSONException e) {
//                    e.printStackTrace();
        }
      }

      final JSONObject finalNearestBeacon = nearestBeacon;

      if (finalNearestBeacon != null) {
        sendBeacon(new JSONObject() {{
          try {
            put("uuid", finalNearestBeacon.get("uuid"));
            put("major", finalNearestBeacon.get("major"));
            put("minor", finalNearestBeacon.get("minor"));
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }});
      } else {
        sendBeacon(null);
      }

      sendDebug(new JSONObject() {{
        try {
          put("device", "android");
          put("message", "SendBeacon");
          put("beacon", finalNearestBeacon);
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }});
    }

    private JSONArray sort(JSONArray jsonArr, String sortBy, boolean sortOrder) throws JSONException {
      JSONArray sortedJsonArray = new JSONArray();

      List<JSONObject> jsonValues = new ArrayList();
      for (int i = 0; i < jsonArr.length(); i++) {
        jsonValues.add(jsonArr.getJSONObject(i));
      }
      final String KEY_NAME = sortBy;
      final Boolean SORT_ORDER = sortOrder;
      Collections.sort( jsonValues, new Comparator<JSONObject>() {

        @Override
        public int compare(JSONObject a, JSONObject b) {
          Double valA = new Double(-1);
          Double valB = new Double(-1);

          try {
            valA = (Double) a.get(KEY_NAME);
            valB = (Double) b.get(KEY_NAME);
          }
          catch (JSONException e) {
            //exception
          }
          if (SORT_ORDER) {
            return valA.compareTo(valB);
          } else {
            return -valA.compareTo(valB);
          }
        }
      });

      for (int i = 0; i < jsonArr.length(); i++) {
        sortedJsonArray.put(jsonValues.get(i));
      }

      return sortedJsonArray;
    }
  };

  private WritableMap createRangingResponse(Collection<Beacon> beacons, Region region) {
    WritableMap map = new WritableNativeMap();
    map.putString("identifier", region.getUniqueId());
    map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
//    WritableArray a = new WritableNativeArray();
    JSONArray a = new JSONArray();
    for (Beacon beacon : beacons) {
      JSONObject b = new JSONObject();
      try {
        b.put("uuid", beacon.getId1().toString());
        if (beacon.getIdentifiers().size() > 2) {
          b.put("major", beacon.getId2().toInt());
          b.put("minor", beacon.getId3().toInt());
        }
        b.put("rssi", beacon.getRssi());
        if (beacon.getDistance() == Double.POSITIVE_INFINITY
          || Double.isNaN(beacon.getDistance())
          || beacon.getDistance() == Double.NaN
          || beacon.getDistance() == Double.NEGATIVE_INFINITY) {
          b.put("distance", 999.0);
          b.put("proximity", "far");
        } else {
          b.put("distance", beacon.getDistance());
          b.put("proximity", getProximity(beacon.getDistance()));
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
      a.put(b);

//      WritableMap b = new WritableNativeMap();
//      b.putString("uuid", beacon.getId1().toString());
//      if (beacon.getIdentifiers().size() > 2) {
//        b.putInt("major", beacon.getId2().toInt());
//        b.putInt("minor", beacon.getId3().toInt());
//      }
//      b.putInt("rssi", beacon.getRssi());
//      if (beacon.getDistance() == Double.POSITIVE_INFINITY
//        || Double.isNaN(beacon.getDistance())
//        || beacon.getDistance() == Double.NaN
//        || beacon.getDistance() == Double.NEGATIVE_INFINITY) {
//        b.putDouble("distance", 999.0);
//        b.putString("proximity", "far");
//      } else {
//        b.putDouble("distance", beacon.getDistance());
//        b.putString("proximity", getProximity(beacon.getDistance()));
//      }
//      a.pushMap(b);
    }
//    map.putArray("beacons", a);
    map.putString("beacons", a.toString());
    return map;
  }

  private String getProximity(double distance) {
    if (distance == -1.0) {
      return "unknown";
    } else if (distance < 1) {
      return "immediate";
    } else if (distance < 3) {
      return "near";
    } else {
      return "far";
    }
  }

  @ReactMethod
  public void stopRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
    Region region = createRegion(regionId, beaconUuid);
    try {
      mBeaconManager.stopRangingBeaconsInRegion(region);
      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "stopRanging, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void requestStateForRegion(String regionId, String beaconUuid, int minor, int major) {
    Region region = createRegion(
      regionId,
      beaconUuid,
      String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
      String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
    );
    mBeaconManager.requestStateForRegion(region);
  }

  @ReactMethod
  public void setDebugApi(String debugApi) {
    Log.d(LOG_TAG, "setDebugApi " + debugApi);
    this.debugApi = debugApi;
  }

  @ReactMethod
  public void setRequestToken(String token) {
    Log.d(LOG_TAG, "setRequestToken " + token);
    this.requestToken = token;
  }

  @ReactMethod
  public void setBeaconRequestApi(String requestApi) {
    Log.d(LOG_TAG, "setBeaconRequestApi " + requestApi);
    this.beaconRequestApi = requestApi;
  }

  @ReactMethod
  public void setUserId(String userId) {
    Log.d(LOG_TAG, "setUserId " + userId);
  }

  @ReactMethod
  public void setNotificationDelay(Number notificationDelay) {
    Log.d(LOG_TAG, "setNotificationDelay " + notificationDelay);
  }

  @ReactMethod
  public void destroy() {
    Log.d(LOG_TAG, "destroy");
  }

  @ReactMethod
  public void startBeaconServices(final String regionId, final String beaconUuid, final int minor, final int major, final Callback resolve, final Callback reject) {
    Log.d(LOG_TAG, "startBeaconServices ");
//        this.builder = this.buildNotification();
    unbindManager(); // Prevent: may not be called after consumers are already bound beacon
    mBeaconManager.enableForegroundServiceScanning(this.builder.build(), NOTIFICATION_FOREGROUND_SCANNING_ID);
    bindManager();

    // Hack: delay a bit to wait beacon service connected to prevent error:
    // The BeaconManager is not bound to the service.  Call beaconManager.bind(BeaconConsumer consumer) and wait for a callback to onBeaconServiceConnect()
    new android.os.Handler().postDelayed(
      new Runnable() {
        public void run() {
          Region region = createRegion(
            regionId,
            beaconUuid,
            String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
            String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
          );
          try {
            mBeaconManager.startMonitoringBeaconsInRegion(region);
            mBeaconManager.startRangingBeaconsInRegion(region);
            MyRegion = region;
            resolve.invoke();
          } catch (Exception e) {
            Log.e(LOG_TAG, "stopBeaconServices, error: ", e);
            reject.invoke(e.getMessage());
          }
        }
      }, 500
    );
  }

  @ReactMethod
  public void stopBeaconServices(final String regionId, final String beaconUuid, final int minor, final int major, final Callback resolve, final Callback reject) {
    Log.d(LOG_TAG, "stopBeaconServices ");
    Region region = createRegion(
      regionId,
      beaconUuid,
      String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
      String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
    );
    try {
      mBeaconManager.stopMonitoringBeaconsInRegion(region);
      mBeaconManager.stopRangingBeaconsInRegion(region);
      unbindManager(); // Prevent: may not be called after consumers are already bound beacon
      mBeaconManager.disableForegroundServiceScanning();
      mNotificationManager.cancel(NOTIFICATION_FOREGROUND_SCANNING_ID);
      bindManager();
      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "stopBeaconServices, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void isStarted(final Callback resolve) {
    boolean started =  this.mBeaconManager.getForegroundServiceNotificationId() != -1;
    Log.d(LOG_TAG, "is beacon service started? " + Boolean.toString(started));
    resolve.invoke(started);
  }

  /***********************************************************************************************
   * Utils
   **********************************************************************************************/
  private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    if (reactContext.hasActiveCatalystInstance()) {
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
    }
  }

  private Region createRegion(String regionId, String beaconUuid) {
    Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
    return new Region(regionId, id1, null, null);
  }

  private Region createRegion(String regionId, String beaconUuid, String minor, String major) {
    Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
    return new Region(
      regionId,
      id1,
      major.length() > 0 ? Identifier.parse(major) : null,
      minor.length() > 0 ? Identifier.parse(minor) : null
    );
  }

  private Class getMainActivityClass() {
    String packageName = mApplicationContext.getPackageName();
    Intent launchIntent = mApplicationContext.getPackageManager().getLaunchIntentForPackage(packageName);
    String className = launchIntent.getComponent().getClassName();
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return null;
    }
  }

  private void sendDebug(JSONObject data) {
    final String debugApi = this.debugApi;
    new BeaconDebugRequest().execute(data, new JSONObject() {{
      try {
        put("debugApi", debugApi);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }});
  }

  private void sendBeacon(JSONObject data) {
    final String beaconRequestApi = this.beaconRequestApi;
    final String requestToken = this.requestToken;
    new BeaconRequest().execute(data, new JSONObject() {{
      try {
        put("beaconRequestApi", beaconRequestApi);
        put("requestToken", requestToken);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }});
  }

  private Notification.Builder buildNotification() {
    Notification.Builder builder = new Notification.Builder(mApplicationContext);
    builder.setSmallIcon(mApplicationContext.getResources().getIdentifier("ic_notification", "mipmap", mApplicationContext.getPackageName()));
    builder.setStyle(new Notification.BigTextStyle().setSummaryText("Ranging Beacons..."));

    Class intentClass = getMainActivityClass();
    Intent intent = new Intent(mApplicationContext, intentClass);
    PendingIntent pendingIntent = PendingIntent.getActivity(mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    builder.setContentIntent(pendingIntent);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
        NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN);
//      notificationChannel.setDescription(NOTIFICATION_CHANNEL_DESCRIPTION);
      mNotificationManager.createNotificationChannel(notificationChannel);
      builder.setChannelId(notificationChannel.getId());
    }

    return builder;
  }
}
