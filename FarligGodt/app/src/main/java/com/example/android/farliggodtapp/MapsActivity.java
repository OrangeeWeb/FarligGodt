package com.example.android.farliggodtapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.farliggodtapp.api.Api;
import com.example.android.farliggodtapp.api.ApiCallback;
import com.example.android.farliggodtapp.api.BlacklistCallback;
import com.example.android.farliggodtapp.api.CheckVersionApi;
import com.example.android.farliggodtapp.api.FetchBlacklist;
import com.example.android.farliggodtapp.api.Specie;
import com.example.android.farliggodtapp.api.Taxon;
import com.example.android.farliggodtapp.api.TaxonLocations;
import com.example.android.farliggodtapp.api.VersionCallback;
import com.example.android.farliggodtapp.database.DatabaseHelper;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Arrays;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, ApiCallback, BlacklistCallback, VersionCallback {

    private GoogleMap mMap = null;

    public double longitude, latitude;

    public Api taxonApi;
    public FetchBlacklist blacklist;
    public CheckVersionApi versionApi;
    public TaxonLocations taxonLocationsApi;

    private DatabaseHelper db;

    static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private ProgressDialog apiProgress;

    private Location location;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private boolean customLocation = false;

    private String[] speciesList;

    private AutoCompleteTextView searchbar;

    private Taxon[] speciesSaveList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        db = new DatabaseHelper(this);
        taxonApi = new Api(this);
        taxonLocationsApi = new TaxonLocations(this);
        blacklist = new FetchBlacklist(this);
        versionApi = new CheckVersionApi(this, getApplicationContext());

        versionApi.checkVersion();

        if (db.fetchType("radius") == null) {
            db.updateOrInsert("radius", "25");
        }
        if(db.fetchType("version") == null){
            db.updateOrInsert("version", "0");
        }
        apiProgress = new ProgressDialog(this);
        apiProgress.setMessage(getString(R.string.loadTaxonInit));
        apiProgress.show();



        String lastLat = db.fetchType("lastLat");
        String lastLng = db.fetchType("lastLng");

        if(lastLat != null && lastLng != null){
            latitude = Double.parseDouble(lastLat);
            longitude = Double.parseDouble(lastLng);
            taxonApi.refreshQuery(latitude, longitude, db.fetchType("radius"));
            changeCamera(latitude, longitude);
        }

        requestFineLocationPermit();


        searchbar = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView1);
        setAutoCompleteSearch();


        searchbar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch(searchbar.getText().toString());
                    handled = true;
                }
                return handled;
            }
        });

    }

    public void doSearch(String text){
        String taxonID = db.getTaxonByName(text);
        if(taxonID == null){
            Toast.makeText(getBaseContext(), "No Result for " + text, Toast.LENGTH_LONG).show();
            return;
        }
        taxonLocationsApi.refreshQuery(taxonID);
        LatLng current = new LatLng(latitude, longitude);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 5));
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        Log.v("blacklist", "you searched for " + text + " got id: " + taxonID);
    }

    public void setAutoCompleteSearch(){
        speciesList = db.getBlacklistString();
        if(speciesList != null){

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, speciesList);

            searchbar.setAdapter(adapter);

            searchbar.setThreshold(1);
        }
    }

    /**
     * when menu is created
     * @param menu Menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * when a menu item is selected/clicked
     * @param item MenuItem
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, Settings.class);
                startActivity(i);
                return true;

            case R.id.action_search:
                taxonApi.refreshQuery(latitude, longitude, db.fetchType("radius"));
                return true;
            case android.R.id.home:
                // app icon in action bar clicked; goto parent activity.
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Permission request for Android 6.0 and later
     */
    public void requestFineLocationPermit(){
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            requestLoc();
        }
    }

    /**
     * Permission request for Android 6.0 and later
     * @param requestCode int
     * @param permissions string array
     * @param grantResults int array
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Register the listener with the Location Manager to receive location updates
                    requestLoc();

                } else {
                    requestFineLocationPermit();
                }
            }
        }
    }

    /**
     * request location updates
     */
    public void requestLoc(){

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Location lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if(lastLoc != null){
            latitude = lastLoc.getLatitude();
            longitude = lastLoc.getLongitude();
            taxonApi.refreshQuery(latitude, longitude, db.fetchType("radius"));
        }

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                if(!customLocation) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    taxonApi.refreshQuery(latitude, longitude, db.fetchType("radius"));
                   // db.updateOrInsert("lastLat", Double.toString(latitude));
                   // db.updateOrInsert("lastLng", Double.toString(longitude));
                    changeCamera(latitude, longitude);
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
                Toast.makeText(getBaseContext(), "GPS enabled", Toast.LENGTH_SHORT).show();
            }

            public void onProviderDisabled(String provider) {
                Toast.makeText(getBaseContext(), "GPS disabled", Toast.LENGTH_SHORT).show();
            }
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 60000, locationListener);

    }

    public void changeCamera(double lat, double lng){
        if(mMap == null) return;

        LatLng current = new LatLng(lat, lng);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 12));


    }


    /**
     * Google maps init
     * @param googleMap mMap
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    /**
     * Api successfully got data
     * @param taxons array of Taxon
     */
    @Override
    public void serviceSuccess(Taxon[] taxons) {
        if(mMap == null) return;

        Log.v("blacklist" , Arrays.toString(taxons));
        mMap.clear();
        apiProgress.hide();
        apiProgress.dismiss();
        speciesSaveList = taxons;
        for (Taxon taxon : taxons) {

            LatLng taxonLatLng = new LatLng(taxon.getLat(), taxon.getLng());

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(taxonLatLng)
                    .title(taxon.getName())
                    .snippet("Tap for more info")
            );

           marker.setTag(taxon.getTaxonID());

            mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                @Override
                public void onInfoWindowClick(final Marker marker) {

                    Intent i = new Intent(getApplicationContext(), SpeciesActivity.class);
                    i.putExtra("taxonID", marker.getTag().toString());
                    startActivity(i);

                }

            });
            //.icon(BitmapDescriptorFactory.fromResource(R.drawable.filnavn))
        }

    }

    /**
     * Api service failed to get data
     * @param exc error msg
     */
    @Override
    public void serviceFailed(Exception exc) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.errorAlert)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.tryAgain), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        taxonApi.refreshQuery(latitude, longitude, db.fetchType("radius"));
                    }
                })
                .setNegativeButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }


    @Override
    public void blacklistSuccess(Specie[] species, String version) {

        db.updateBlacklist(species, version);
        setAutoCompleteSearch();
        Toast.makeText(getBaseContext(), "Fetching Blacklist Success", Toast.LENGTH_LONG).show();
    }

    @Override
    public void blacklistFailed(Exception exc) {
        Log.v("blacklist", "blacklistFailed: " + exc.getMessage());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Failed to fetch blacklist data")
                .setCancelable(false)
                .setPositiveButton(getString(R.string.tryAgain), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        blacklist.fetch(db.fetchType("version"));
                    }
                })
                .setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // do nothing
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onOldVersion(String version) {
        Log.v("blacklist", "onOldVersion: " + version);

        blacklist.fetch(version);
    }

    @Override
    public void onUptoDateVersion() {
        Toast.makeText(getBaseContext(), "Blacklist Up to date", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void versionAPIFailed(Exception exc) {

        Log.v("blacklist", "versionAPIFailed: " + exc.getMessage());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Failed to fetch version data")
                .setCancelable(false)
                .setPositiveButton(getString(R.string.tryAgain), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        versionApi.checkVersion();
                    }
                })
                .setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                       // do nothing
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void versionAPISuccess(String version) {

    }
}
