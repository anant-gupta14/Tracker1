package com.dev.infinitoz.tracker1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;

import android.nfc.Tag;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final int LOCATION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    GoogleApiClient mGoogleaAiClient;
    Location mlastLocation;
    LocationRequest mlocationRequest;
    private Button mLogout;
    private Button mRequest,mSettings,mHistory;
    private  LatLng pickupLocation;
    private Boolean requestBol = false;
    private  Marker pickupMarker;
    private String destination;
    private SupportMapFragment mapFragment;

    private LinearLayout mDriverInfo;
    private ImageView mDriverProfileImage;
    private TextView mDriverName, mDriverPhone,mDriverCar;
    private String requestService;
    private RadioGroup mRadioGroup;
    private LatLng destinationLatLng;
    private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedListener;

    private RatingBar mRatingBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.s
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
        }
        else {
            mapFragment.getMapAsync(this);
        }

        destinationLatLng = new LatLng(0.0,0.0);


        mLogout = findViewById(R.id.logout);
        mSettings = findViewById(R.id.settings);
        mRequest = findViewById(R.id.request);
        mDriverInfo = findViewById(R.id.driverInfo);
        mDriverProfileImage = findViewById(R.id.driverProfileImage);
        mDriverName = findViewById(R.id.driverName);
        mDriverPhone = findViewById(R.id.driverPhone);
        mDriverCar = findViewById(R.id.driverCar);
        mRadioGroup = findViewById(R.id.radioGroup);
        mRadioGroup.check(R.id.uberX);
        mHistory = findViewById(R.id.history);
        mRatingBar = findViewById(R.id.ratingBar);

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CustomerMapActivity.this,HistoryActivity.class);
                intent.putExtra("customerOrDriver","Customers");
                startActivity(intent);
                return;
            }
        });


        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this,MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (requestBol){
                    endRide();
                }
                else {
                    int selectId = mRadioGroup.getCheckedRadioButtonId();

                    final RadioButton radioButton = findViewById(selectId);

                    if(radioButton.getText() == null){
                        return;
                    }

                    requestService = radioButton.getText().toString();
                    requestBol = true;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mlastLocation.getLatitude(), mlastLocation.getLongitude()));

                    pickupLocation = new LatLng(mlastLocation.getLatitude(), mlastLocation.getLongitude());
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title("pick up here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pickup)));

                    mRequest.setText("Getting your drivers...");

                    getClossestDrivers();
                }
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CustomerMapActivity.this,CustomerSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination = place.getName().toString();
                destinationLatLng = place.getLatLng();
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
            }
        });
    }
    private int radius = 1;
    private boolean driverFound = false;
    private  String driverFoundID;

    GeoQuery geoQuery;

    private void getClossestDrivers() {
       final DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");

       GeoFire geofire = new GeoFire(driverLocation);

        geoQuery = geofire.queryAtLocation(new GeoLocation(pickupLocation.latitude,pickupLocation.longitude),radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(!driverFound && requestBol) {
                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(key);
                    mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                                Map<String,Object> driverMap = (Map<String, Object>) dataSnapshot.getValue();
                                if(driverFound){
                                    return;
                                }

                                if(driverMap.get("service").equals(requestService)){

                                    driverFound = true;
                                    driverFoundID = dataSnapshot.getKey();

                                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
                                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                    HashMap map = new HashMap();
                                    map.put("customerRideId",customerId);
                                    map.put("destination",destination);
                                    map.put("destinationLat",destinationLatLng.latitude);
                                    map.put("destinationLng",destinationLatLng.longitude);
                                    driverRef.updateChildren(map);

                                    getDriverLocation();
                                    getDriverInfo();
                                    getHasRideEnded();
                                    mRequest.setText("Looking for driver location..");
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });


                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
            if(!driverFound){
                radius++;
                getClossestDrivers();
            }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

    }
    private Marker mDriverMarker;
    private  DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;

    private void getDriverLocation() {
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversWorking").child(driverFoundID).child("l");
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && requestBol){
                    List<Object> map = (List<Object> )dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    mRequest.setText("Driver : found");
                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }

                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(locationLat,locationLng);
                    if(mDriverMarker != null){
                        mDriverMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);

                    if(distance<100){
                        mRequest.setText("Driver is here");
                    }
                    else{
                        mRequest.setText("Driver found :"+ String.valueOf(distance));
                    }

                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("your dirver").icon(BitmapDescriptorFactory.fromResource(R.mipmap.car)));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getDriverInfo() {
        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
        mCustomerDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String,Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name") != null){
                        mDriverName.setText(map.get("name").toString());
                    }
                    if(map.get("phone") != null){
                        mDriverPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("car") != null){
                        mDriverCar.setText(map.get("car").toString());
                    }
                    if(map.get("profileImageUrl") != null){
                        Glide.with(getApplicationContext()).load(map.get("profileImageUrl")).into(mDriverProfileImage);
                    }
                    int ratingSum = 0;
                    float ratingsTotal = 0;
                    float ratingAvg = 0;
                    for(DataSnapshot child:dataSnapshot.child("rating").getChildren()){
                        ratingSum = ratingSum+Integer.valueOf(child.getValue().toString());
                        ratingsTotal++;
                    }
                    if(ratingsTotal !=0){
                        ratingAvg = ratingSum/ratingsTotal;
                        mRatingBar.setRating(ratingAvg);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getHasRideEnded() {
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        driveHasEndedRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest").child("customerRideId");
        driverLocationRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){

                }
                else {
                    endRide();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    private void endRide() {
        requestBol = false;
        geoQuery.removeAllListeners();
        driverLocationRef.removeEventListener(driverLocationRefListener);
        driveHasEndedRef.removeEventListener(driverLocationRefListener);
        if(driverFoundID != null){
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
            driverRef.removeValue();
            driverFoundID = null;
        }
        driverFound = false;
        radius = 1;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        if(pickupMarker != null){
            pickupMarker.remove();
        }
        mRequest.setText("call cab");
        mDriverInfo.setVisibility(View.GONE);
        mDriverName.setText("");
        mDriverPhone.setText("");
        mDriverCar.setText("");
        mDriverProfileImage.setImageResource(R.mipmap.ic_user);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
            //
//            return;
        }
        buildGogoleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGogoleApiClient() {
        mGoogleaAiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
        mGoogleaAiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        mlastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mlocationRequest = new LocationRequest();
        mlocationRequest.setInterval(1000);
        mlocationRequest.setFastestInterval(1000);
        mlocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleaAiClient, mlocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onStop() {
        super.onStop();

        /*String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("driversAvailable");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);*/
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case LOCATION_REQUEST_CODE:
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                }
                else{
                    Toast.makeText(getApplicationContext(),"Please provide the GPS permission",Toast.LENGTH_LONG).show();
                }
        }
    }
}
