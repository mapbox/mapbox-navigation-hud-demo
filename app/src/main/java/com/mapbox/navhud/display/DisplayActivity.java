package com.mapbox.navhud.display;

import android.animation.ObjectAnimator;
import android.location.Location;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.navhud.Constants;
import com.mapbox.navhud.ManeuverMap;
import com.mapbox.navhud.R;
import com.mapbox.navhud.location.GoogleLocationEngine;
import com.mapbox.services.android.navigation.v5.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.api.directions.v5.models.DirectionsResponse;
import com.mapbox.services.api.directions.v5.models.DirectionsRoute;
import com.mapbox.services.api.directions.v5.models.LegStep;
import com.mapbox.services.api.directions.v5.models.StepManeuver;
import com.mapbox.services.api.utils.turf.TurfConstants;
import com.mapbox.services.api.utils.turf.TurfHelpers;
import com.mapbox.services.commons.models.Position;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.mapbox.navhud.Constants.MAPBOX_ACCESS_TOKEN;
import static com.mapbox.navhud.Constants.MPH_DOUBLE;
import static com.mapbox.navhud.Constants.PLACE_LOCATION_EXTRA;
import static com.mapbox.services.android.telemetry.location.LocationEnginePriority.HIGH_ACCURACY;

public class DisplayActivity extends AppCompatActivity implements LocationEngineListener,
  ProgressChangeListener, MilestoneEventListener, Callback<DirectionsResponse>, TextToSpeech.OnInitListener {

  private static final String TAG = DisplayActivity.class.getSimpleName();
  private static final String ARRIVAL_STRING_FORMAT = "%tl:%tM %tp%n";
  private static final String DECIMAL_FORMAT = "###.#";
  private static final String MILES_STRING_FORMAT = "%s miles";
  private static final String FEET_STRING_FORMAT = "%s feet";

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

  private Position currentUserPosition;
  private MapboxNavigation navigation;
  private LocationEngine locationEngine;
  private boolean mirroring;
  private TextToSpeech tts;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_display);
    ButterKnife.bind(this);
    hideNavigationFullscreen();

    tts = new TextToSpeech(this, this);

    activateLocationEngine();
    initMapboxNavigation();
  }

  @Override
  protected void onStop() {
    super.onStop();
    navigation.onStop();
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

  @SuppressWarnings({"MissingPermission"})
  @Override
  public void onConnected() {
    locationEngine.requestLocationUpdates();

    if (locationEngine.getLastLocation() != null) {
      Location lastLocation = locationEngine.getLastLocation();
      currentUserPosition = Position.fromLngLat(lastLocation.getLongitude(), lastLocation.getLatitude());
      checkIntentExtras();
    }
  }

  @Override
  public void onLocationChanged(Location location) {
    currentUserPosition = Position.fromLngLat(location.getLongitude(), location.getLatitude());
    calculateMph(location);
  }

  @Override
  public void onProgressChange(Location location, RouteProgress routeProgress) {
    Log.d(TAG, "onProgressChange called");
    updateUi(routeProgress);
  }

  @Override
  public void onMilestoneEvent(RouteProgress routeProgress, String instruction, int identifier) {
    tts.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null);
  }

  @Override
  public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
    if (response.body() != null &&
      response.body().getRoutes() != null &&
      response.body().getRoutes().size() > 0) {
      DirectionsRoute currentRoute = response.body().getRoutes().get(0);
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
    locationEngine = GoogleLocationEngine.getLocationEngine(this);
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

  private void checkIntentExtras() {
    if (getIntent().hasExtra(PLACE_LOCATION_EXTRA)) {
      Location placeLocation = getIntent().getParcelableExtra(PLACE_LOCATION_EXTRA);
      Position destination = Position.fromLngLat(placeLocation.getLongitude(), placeLocation.getLatitude());

      if (currentUserPosition != null) {
        navigation.getRoute(currentUserPosition, destination, this);
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
    stepDistanceText.setText(formatStepDistanceRemaining(progress.getCurrentLegProgress()
      .getCurrentStepProgress().getDistanceRemaining()));
    routeDistanceText.setText(formatRouteDistanceRemaining(progress.getDistanceRemaining()));
    timeRemainingText.setText(formatTimeRemaining(progress.getDurationRemaining()));
    arrivalText.setText(formatArrivalTime(progress.getDurationRemaining()));
    setStepProgressBar(progress.getCurrentLegProgress().getCurrentStepProgress().getFractionTraveled());
  }

  private void extractLegStep(RouteProgress progress) {
    LegStep upComingStep = progress.getCurrentLegProgress().getUpComingStep();
    if (upComingStep != null) {
      maneuverImage.setImageResource(obtainManeuverResource(upComingStep));
      if (upComingStep.getManeuver() != null) {
        if (!TextUtils.isEmpty(upComingStep.getName())) {
          stepText.setText(upComingStep.getName());
        } else if (!TextUtils.isEmpty(upComingStep.getManeuver().getInstruction())) {
          stepText.setText(upComingStep.getManeuver().getInstruction());
        }
      }
    }
  }

  private static int obtainManeuverResource(LegStep step) {
    ManeuverMap maneuverMap = new ManeuverMap();
    if (step != null && step.getManeuver() != null) {
      StepManeuver maneuver = step.getManeuver();
      if (!TextUtils.isEmpty(maneuver.getModifier())) {
        return maneuverMap.getManeuverResource(maneuver.getType() + maneuver.getModifier());
      } else {
        return maneuverMap.getManeuverResource(maneuver.getType());
      }
    }
    return R.drawable.maneuver_starting;
  }

  private static String formatTimeRemaining(double routeDurationRemaining) {
    long seconds = (long) routeDurationRemaining;

    if (seconds < 0) {
      throw new IllegalArgumentException(Constants.DURATION_ILLEGAL_ARGUMENT);
    }

    long days = TimeUnit.SECONDS.toDays(seconds);
    seconds -= TimeUnit.DAYS.toSeconds(days);
    long hours = TimeUnit.SECONDS.toHours(seconds);
    seconds -= TimeUnit.HOURS.toSeconds(hours);
    long minutes = TimeUnit.SECONDS.toMinutes(seconds);
    seconds -= TimeUnit.MINUTES.toSeconds(minutes);
    long sec = TimeUnit.SECONDS.toSeconds(seconds);

    if (seconds >= 30) {
      minutes = minutes + 1;
    }

    StringBuilder sb = new StringBuilder(Constants.STRING_BUILDER_CAPACITY);
    if (days != 0) {
      sb.append(days);
      sb.append(Constants.DAYS);
    }
    if (hours != 0) {
      sb.append(hours);
      sb.append(Constants.HOUR);
    }
    if (minutes != 0) {
      sb.append(minutes);
      sb.append(Constants.MINUTE);
    }
    if (days == 0 && hours == 0 && minutes == 0) {
      sb.append(sec);
      sb.append(Constants.SECONDS);
    }

    return ("Time: " + sb.toString());
  }

  public static String formatArrivalTime(double routeDurationRemaining) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.SECOND, (int) routeDurationRemaining);

    return "Arrival: " + String.format(Locale.US, ARRIVAL_STRING_FORMAT,
      calendar, calendar, calendar);
  }

  private static String formatRouteDistanceRemaining(double routeDistanceRemaining) {
    double miles = routeDistanceRemaining * Constants.METER_MULTIPLIER;
    DecimalFormat df = new DecimalFormat(DECIMAL_FORMAT);
    miles = Double.valueOf(df.format(miles));

    return ("Distance: " + miles + Constants.MILES);
  }

  private static String formatStepDistanceRemaining(double distance) {
    String formattedString;
    if (TurfHelpers.convertDistance(distance, TurfConstants.UNIT_METERS, TurfConstants.UNIT_FEET) > 1099) {
      distance = TurfHelpers.convertDistance(distance, TurfConstants.UNIT_METERS, TurfConstants.UNIT_MILES);
      DecimalFormat df = new DecimalFormat(DECIMAL_FORMAT);
      double roundedNumber = (distance / 100 * 100);
      formattedString = String.format(Locale.US, MILES_STRING_FORMAT, df.format(roundedNumber));
    } else {
      distance = TurfHelpers.convertDistance(distance, TurfConstants.UNIT_METERS, TurfConstants.UNIT_FEET);
      int roundedNumber = ((int) Math.round(distance)) / 100 * 100;
      formattedString = String.format(Locale.US, FEET_STRING_FORMAT, roundedNumber);
    }
    return "In " + formattedString;
  }

  private void setStepProgressBar(float fractionRemaining) {
    ObjectAnimator animation = ObjectAnimator.ofInt(stepProgressBar, "progress",
      Math.round(fractionRemaining * 10000));
    animation.setInterpolator(new LinearInterpolator());
    animation.setDuration(1000);
    animation.start();
  }
}
