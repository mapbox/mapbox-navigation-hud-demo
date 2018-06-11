package com.mapbox.navhud.display;

import android.animation.ObjectAnimator;
import android.location.Location;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.api.directions.v5.models.StepManeuver;
import com.mapbox.geojson.Point;
import com.mapbox.navhud.ManeuverMap;
import com.mapbox.navhud.R;
import com.mapbox.services.android.navigation.v5.milestone.Milestone;
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationTimeFormat;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.utils.DistanceUtils;
import com.mapbox.services.android.navigation.v5.utils.time.TimeUtils;

import java.util.Calendar;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.mapbox.android.core.location.LocationEnginePriority.HIGH_ACCURACY;
import static com.mapbox.navhud.Constants.MAPBOX_ACCESS_TOKEN;
import static com.mapbox.navhud.Constants.MPH_DOUBLE;
import static com.mapbox.navhud.Constants.PLACE_LOCATION_EXTRA;
import static com.mapbox.services.android.navigation.v5.utils.time.TimeUtils.formatTimeRemaining;

public class DisplayActivity extends AppCompatActivity implements LocationEngineListener,
  ProgressChangeListener, MilestoneEventListener, Callback<DirectionsResponse>, TextToSpeech.OnInitListener {

  private static final String TAG = DisplayActivity.class.getSimpleName();

  @BindView(R.id.stepText)
  TextView stepText;
  @BindView(R.id.mphText)
  TextView mphText;
  @BindView(R.id.distanceText)
  TextView stepDistanceText;
  @BindView(R.id.distanceRemainingText)
  TextView routeDistanceText;
  @BindView(R.id.timeRemainingText)
  TextView timeRemainingText;
  @BindView(R.id.arrivalText)
  TextView arrivalText;
  @BindView(R.id.maneuverImage)
  ImageView maneuverImage;
  @BindView(R.id.stepProgressBar)
  ProgressBar stepProgressBar;

  private Point currentUserPoint;
  private MapboxNavigation navigation;
  private LocationEngine locationEngine;
  private boolean mirroring;
  private TextToSpeech tts;
  private DistanceUtils distanceUtils;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_display);
    ButterKnife.bind(this);
    hideNavigationFullscreen();

    tts = new TextToSpeech(this, this);

    activateLocationEngine();
    initMapboxNavigation();
    initDistanceUtils();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    navigation.onDestroy();
    deactivateLocationEngine();
  }

  @OnClick(R.id.fabMirrorView)
  public void onMirrorClick() {
    View contentView = findViewById(android.R.id.content);
    if (mirroring) {
      contentView.setScaleY(1);
      mirroring = false;
    } else {
      contentView.setScaleY(-1);
      mirroring = true;
    }
    hideNavigationFullscreen();
  }

  @SuppressWarnings( {"MissingPermission"})
  @Override
  public void onConnected() {
    locationEngine.requestLocationUpdates();

    if (locationEngine.getLastLocation() != null) {
      Location lastLocation = locationEngine.getLastLocation();
      currentUserPoint = Point.fromLngLat(lastLocation.getLongitude(), lastLocation.getLatitude());
      checkIntentExtras();
    }
  }

  @Override
  public void onLocationChanged(Location location) {
    currentUserPoint = Point.fromLngLat(location.getLongitude(), location.getLatitude());
    calculateMph(location);
  }

  @Override
  public void onProgressChange(Location location, RouteProgress routeProgress) {
    Log.d(TAG, "onProgressChange called");
    updateUi(routeProgress);
  }

  @Override
  public void onMilestoneEvent(RouteProgress routeProgress, String instruction, Milestone milestone) {
    tts.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null);
  }

  @Override
  public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
    if (response.body() != null && response.body().routes().size() > 0) {
      DirectionsRoute currentRoute = response.body().routes().get(0);
      navigation.setLocationEngine(locationEngine);
      navigation.startNavigation(currentRoute);
    }
  }

  @Override
  public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
    Log.e(TAG, throwable.getMessage());
  }

  @Override
  public void onInit(int status) {
    tts.setLanguage(Locale.getDefault());
  }

  private void hideNavigationFullscreen() {
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
      | View.SYSTEM_UI_FLAG_FULLSCREEN);
  }

  private void activateLocationEngine() {
    LocationEngineProvider locationEngineProvider = new LocationEngineProvider(this);
    locationEngine = locationEngineProvider.obtainBestLocationEngineAvailable();
    locationEngine.setPriority(HIGH_ACCURACY);
    locationEngine.setInterval(0);
    locationEngine.setFastestInterval(1000);
    locationEngine.addLocationEngineListener(this);
    locationEngine.activate();
  }

  private void deactivateLocationEngine() {
    locationEngine.removeLocationUpdates();
    locationEngine.removeLocationEngineListener(this);
    locationEngine.deactivate();
  }

  private void initMapboxNavigation() {
    navigation = new MapboxNavigation(this, MAPBOX_ACCESS_TOKEN);
    navigation.addProgressChangeListener(this);
    navigation.addMilestoneEventListener(this);
  }

  private void initDistanceUtils() {
    String language = getResources().getConfiguration().locale.getLanguage();
    distanceUtils = new DistanceUtils(this, language, DirectionsCriteria.IMPERIAL);
  }

  private void checkIntentExtras() {
    if (getIntent().hasExtra(PLACE_LOCATION_EXTRA)) {
      Location placeLocation = getIntent().getParcelableExtra(PLACE_LOCATION_EXTRA);
      Point destination = Point.fromLngLat(placeLocation.getLongitude(), placeLocation.getLatitude());

      if (currentUserPoint != null) {
        NavigationRoute.builder(this)
          .accessToken(MAPBOX_ACCESS_TOKEN)
          .origin(currentUserPoint)
          .destination(destination)
          .build().getRoute(this);
      } else {
        Toast.makeText(this, "Current Location is null", Toast.LENGTH_LONG).show();
      }
    }
  }

  private void calculateMph(Location location) {
    if (location.hasSpeed()) {
      int speed = (int) (location.getSpeed() * MPH_DOUBLE);
      mphText.setText(String.valueOf(speed));
    } else {
      mphText.setText(String.valueOf(0));
    }
  }

  private void updateUi(RouteProgress progress) {
    extractLegStep(progress);
    stepDistanceText.setText(formatStepDistanceRemaining(progress.currentLegProgress()
      .currentStepProgress().distanceRemaining()));
    routeDistanceText.setText(formatRouteDistanceRemaining(progress.distanceRemaining()));
    timeRemainingText.setText(formatTimeRemaining(progress.durationRemaining()));
    arrivalText.setText(formatArrivalTime(progress.durationRemaining()));
    setStepProgressBar(progress.currentLegProgress().currentStepProgress().fractionTraveled());
  }

  private void extractLegStep(RouteProgress progress) {
    LegStep upComingStep = progress.currentLegProgress().upComingStep();
    if (upComingStep != null) {
      maneuverImage.setImageResource(obtainManeuverResource(upComingStep));
      if (!TextUtils.isEmpty(upComingStep.name())) {
        stepText.setText(upComingStep.name());
      } else if (!TextUtils.isEmpty(upComingStep.maneuver().instruction())) {
        stepText.setText(upComingStep.maneuver().instruction());
      }
    }
  }

  private static int obtainManeuverResource(LegStep step) {
    ManeuverMap maneuverMap = new ManeuverMap();
    if (step != null) {
      StepManeuver maneuver = step.maneuver();
      if (!TextUtils.isEmpty(maneuver.modifier())) {
        return maneuverMap.getManeuverResource(maneuver.type() + maneuver.modifier());
      } else {
        return maneuverMap.getManeuverResource(maneuver.type());
      }
    }
    return R.drawable.maneuver_starting;
  }

  public String formatArrivalTime(double routeDurationRemaining) {
    Calendar calendar = Calendar.getInstance();
    boolean isTwentyFourHourFormat = DateFormat.is24HourFormat(this);
    return TimeUtils.formatTime(calendar, routeDurationRemaining,
      NavigationTimeFormat.TWELVE_HOURS, isTwentyFourHourFormat);
  }

  private SpannableString formatRouteDistanceRemaining(double routeDistanceRemaining) {
    return distanceUtils.formatDistance(routeDistanceRemaining);
  }

  private SpannableString formatStepDistanceRemaining(double distance) {
    return distanceUtils.formatDistance(distance);
  }

  private void setStepProgressBar(float fractionRemaining) {
    ObjectAnimator animation = ObjectAnimator.ofInt(stepProgressBar, "progress",
      Math.round(fractionRemaining * 10000));
    animation.setInterpolator(new LinearInterpolator());
    animation.setDuration(1000);
    animation.start();
  }
}
