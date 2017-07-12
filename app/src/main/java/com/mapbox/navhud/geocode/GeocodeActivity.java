package com.mapbox.navhud.geocode;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.mapbox.navhud.R;
import com.mapbox.navhud.display.DisplayActivity;

import static com.mapbox.navhud.Constants.PLACE_LOCATION_EXTRA;

public class GeocodeActivity extends AppCompatActivity {

  private static final String TAG = GeocodeActivity.class.getSimpleName();
  private static final int REQUEST_LOCATION_PERMISSION = 2;
  private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_geocode);

    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        launchSearchAutocomplete();
      }
    });

    requestLocationPermission();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
      if (resultCode == RESULT_OK) {
        Place place = PlaceAutocomplete.getPlace(this, data);
        Toast.makeText(this, "Place picked: " + place.getName(), Toast.LENGTH_SHORT).show();
        launchDisplay(place);
      } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
        Status status = PlaceAutocomplete.getStatus(this, data);
        Log.i(TAG, status.getStatusMessage());
      }
    }
  }

  private void requestLocationPermission() {
    if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
      != PackageManager.PERMISSION_GRANTED) ||
      (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED)) {

      ActivityCompat.requestPermissions(this,
        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION},
        REQUEST_LOCATION_PERMISSION);
    }
  }

  private void launchSearchAutocomplete() {
    try {
      Intent intent =
        new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
          .build(this);
      startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
    } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
      Log.e(TAG, e.getMessage());
    }
  }

  private void launchDisplay(Place place) {
    Intent displayActivity = new Intent(this, DisplayActivity.class);
    Location placeLocation = new Location(TAG);
    placeLocation.setLatitude(place.getLatLng().latitude);
    placeLocation.setLongitude(place.getLatLng().longitude);
    displayActivity.putExtra(PLACE_LOCATION_EXTRA, placeLocation);
    startActivity(displayActivity);
  }
}
