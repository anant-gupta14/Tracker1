package com.dev.infinitoz.tracker1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.LocationListener;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener,RoutingListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleaAiClient;
    Location mlastLocation;
    LocationRequest mlocationRequest;
    private Button mLogout,mSettings,mRideStatus;
    private Switch mWorkingSwitch;
    private String customerId = "",destination;
    private boolean isLogginOut = false;

    private SupportMapFragment mapFragment;
    private LinearLayout mCustomerInfo;
    private ImageView mCustomerProfileImage;
    private TextView mCustomerName, mCustomerPhone,mCustometDestination;

    private int status = 0;
    private LatLng destinationLatLng;
    private LatLng pickupLatLng;


    private DatabaseReference mCustomerDatabase;

    private float rideDistance;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        polylines = new ArrayList<>();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
        }
        else {
            mapFragment.getMapAsync(this);
        }
        mCustomerInfo = findViewById(R.id.customerInfo);
        mCustomerProfileImage = findViewById(R.id.customerProfileImage);
        mCustomerName = findViewById(R.id.customerName);
        mCustomerPhone = findViewById(R.id.customerPhone);
        mCustometDestination = findViewById(R.id.customerDestination);
        mRideStatus = findViewById(R.id.rideStatus);
        mWorkingSwitch = findViewById(R.id.workingSwitch);
        mWorkingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    connectDriver();
                }
                else{
                    disconnectDriver();
                }
            }
        });


        mSettings = findViewById(R.id.settings);
        mLogout = findViewById(R.id.logout);
        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (status){
                    case 1:
                        status =2;
                        erasePolylines();
                        if(destinationLatLng.latitude!=0.0 && destinationLatLng.longitude!=0.0){
                            getRouteToMarker(destinationLatLng);
                        }
                        mRideStatus.setText("drive completed");
                        break;
                    case 2:
                        recordRide();
                        endRide();
                        break;
                }
            }
        });
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isLogginOut = true;
                disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(DriverMapActivity.this,MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DriverMapActivity.this,DriverSettingsAcivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        getAssignedCustomer();
    }

    private void recordRide() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("history");
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("history");
        String requestId = historyRef.push().getKey();
        driverRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        Map<String,Object> map = new HashMap();
        map.put("driver",userId);
        map.put("customer",customerId);
        map.put("rating",0);
        map.put("timestamp",getCurrentTimestamp());
        map.put("destination",destination);
        map.put("location/from/lat",pickupLatLng.latitude);
        map.put("location/from/lng",pickupLatLng.longitude);

        map.put("location/to/lat",destinationLatLng.latitude);
        map.put("location/to/lng",destinationLatLng.latitude);
        map.put("distance",rideDistance);

        historyRef.child(requestId).updateChildren(map);



    }

    private void connectDriver(){

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(DriverMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleaAiClient, mlocationRequest, this);
    }

    private Long getCurrentTimestamp() {
        Long timestamp = System.currentTimeMillis()/1000;
        return timestamp;
    }

    private void endRide() {
        mRideStatus.setText("picked customer");
        erasePolylines();
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("customerRequest");
        driverRef.removeValue();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(customerId);
        customerId = "";
        rideDistance = 0;

        if(pickupMarker != null){
            pickupMarker.remove();
        }
        if (assignedCustPickupLocRefListner != null) {
            assignedCustPickupLocRef.removeEventListener(assignedCustPickupLocRefListner);
        }
        mCustomerInfo.setVisibility(View.GONE);
        mCustomerName.setText("");
        mCustomerPhone.setText("");
        mCustometDestination.setText("Destination: --");
        mCustomerProfileImage.setImageResource(R.mipmap.ic_user);

    }

    private void getAssignedCustomer() {
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest").child("customerRideId");
        assignedCustRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
           if(dataSnapshot.exists()){
               status = 1;
               customerId = dataSnapshot.getValue().toString();
                   getAssignedCustomerPickupLocation();
                   getAssignedCustomerDestination();
                   getAssignedCustomerInfo();
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


    private void getAssignedCustomerDestination() {
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest");
        assignedCustRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

                    String destination = dataSnapshot.getValue().toString();

                    if (map.get("destination") != null) {
                        destination = map.get("destination").toString();
                        mCustometDestination.setText("Destination: " + destination);
                    } else {
                        mCustometDestination.setText("Destination:-- ");
                    }

                    Double destinationLat = 0.0;
                    Double destinationLng = 0.0;
                    if(map.get("destiinationLat")!=null){
                        destinationLat = Double.parseDouble(map.get("destiinationLat").toString());
                    }
                    if(map.get("destiinationLng")!=null){
                        destinationLng = Double.parseDouble(map.get("destiinationLng").toString());
                    }
                    destinationLatLng = new LatLng(destinationLat,destinationLng);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    private void getAssignedCustomerInfo() {
        mCustomerInfo.setVisibility(View.VISIBLE);
        mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);
        mCustomerDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String,Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name") != null){
                        mCustomerName.setText(map.get("name").toString());
                    }
                    if(map.get("phone") != null){
                        mCustomerPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("profileImageUrl") != null){
                        Glide.with(getApplicationContext()).load(map.get("profileImageUrl")).into(mCustomerProfileImage);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    Marker pickupMarker;
    private DatabaseReference assignedCustPickupLocRef;
    private ValueEventListener assignedCustPickupLocRefListner;

    private void getAssignedCustomerPickupLocation() {
        assignedCustPickupLocRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId);
        assignedCustPickupLocRefListner = assignedCustPickupLocRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && !customerId.equals("")){
                    Map map = (Map) dataSnapshot.getValue();
                    List list = (List) map.get("l");
                double locationLat = 0;
                double locationLng = 0;
                if(list.get(0) != null){
                    locationLat = (double) list.get(0);
                }

                if(list.get(1) != null){
                    locationLng = (double)list.get(1);
                }
                pickupLatLng = new LatLng(locationLat,locationLng);
                pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pickup)));
                getRouteToMarker(pickupLatLng);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getRouteToMarker(LatLng pickupLatLng){

        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mlastLocation.getLatitude(),mlastLocation.getLongitude()), pickupLatLng)
                .build();
        routing.execute();

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
            // for ActivityCompat#requestPermissions for more details.
            return;
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
        if(getApplicationContext() != null){


            if(!customerId.equals("")){
                rideDistance += mlastLocation.distanceTo(location)/1000;
            }

            mlastLocation = location;
            LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");
            GeoFire geoFireAvailble = new GeoFire(refAvailable);
            GeoFire geoFireWorking = new GeoFire(refWorking);

            switch(customerId){
                case "":
                    geoFireWorking.removeLocation(userId);
                    geoFireAvailble.setLocation(userId,new GeoLocation(location.getLatitude(),location.getLongitude()));
                    break;

                 default:
                     geoFireAvailble.removeLocation(userId);
                     geoFireWorking.setLocation(userId,new GeoLocation(location.getLatitude(),location.getLongitude()));
                     break;
            }
        }

    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mlocationRequest = new LocationRequest();
        mlocationRequest.setInterval(1000);
        mlocationRequest.setFastestInterval(1000);
        mlocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    final int LOCATION_REQUEST_CODE = 1;
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

   /* @Override
    protected void onStop() {
        super.onStop();
        if(!isLogginOut) {
            disconnectDriver();
        }
    }
*/
    private void disconnectDriver() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleaAiClient, this);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("driversAvailable");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }

    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if (polylines.size() > 0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i < route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(), "Route " + (i + 1) + ": distance - " + route.get(i).getDistanceValue() + ": duration - " + route.get(i).getDurationValue(), Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRoutingCancelled() {

    }

    private void erasePolylines(){
        for(Polyline polyline : polylines){
            polyline.remove();
        }
        polylines.clear();
    }

}
