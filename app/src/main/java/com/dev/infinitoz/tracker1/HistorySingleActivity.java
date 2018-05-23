package com.dev.infinitoz.tracker1;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback ,RoutingListener {

    private String rideId,currentUserId;
    private TextView rideLocation,rideDistance,rideDate;
    private ImageView userImage;
    private TextView userName,userPhone;

    private GoogleMap mMap;
    private SupportMapFragment mMapFragment;

    private DatabaseReference historyRideInfoDb;
    private String customerId;
    private String userDriverOrCustomer;
    private String driverId;
    private LatLng destinationLngLng, pickupLatLng;
    private RatingBar mRatingBar;

    private String distance;
    private Double ridePrice;
    private Button mPay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_single);

        Intent intent = new Intent(this,PayPalService.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION,config);
        startService(intent);
        polylines = new ArrayList<>();

        rideId = getIntent().getExtras().get("rideId").toString();
        mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mMapFragment.getMapAsync(this);

        rideLocation = findViewById(R.id.rideLocation);
        rideDistance = findViewById(R.id.rideDistance);

        rideDate = findViewById(R.id.rideDate);
        userName = findViewById(R.id.userName);
        userPhone = findViewById(R.id.userPhone);

        userImage = findViewById(R.id.userImage);
        mRatingBar = findViewById(R.id.ratingBar);
        mPay = findViewById(R.id.pay);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        historyRideInfoDb = FirebaseDatabase.getInstance().getReference().child("history").child(rideId);
        getRideInformation();

    }

    private void getRideInformation() {
        historyRideInfoDb.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    for(DataSnapshot child:dataSnapshot.getChildren()){
                        if(child.getKey().equals("customer")){
                            customerId = child.getValue().toString();
                            if(!customerId.equals(currentUserId)){
                                userDriverOrCustomer = "Drivers";
                                getUserInformation("Customers",customerId);
                            }
                        }
                        if(child.getKey().equals("driver")){
                            driverId = child.getValue().toString();
                            if(!driverId.equals(currentUserId)){
                                userDriverOrCustomer = "Customers";
                                getUserInformation("Drivers",driverId);
                                displayCustomerRelatedObjects();
                            }
                        }
                        if(child.getKey().equals("timestamp")){
                            rideDate.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }
                        if(child.getKey().equals("rating")){
                            mRatingBar.setRating(Integer.valueOf(child.getValue().toString()));
                        }
                        if(child.getKey().equals("distance")){
                            distance = child.getValue().toString();
                            rideDistance.setText(distance.substring(0,Math.min(distance.length(),5))+" km");
                            ridePrice = Double.valueOf(distance)*0.5;
                        }
                        if(child.getKey().equals("destination")){
                            rideLocation.setText(child.getValue().toString());
                        }
                        if(child.getKey().equals("location")){
                            pickupLatLng = new LatLng(Double.valueOf(child.child("from").child("lat").getValue().toString()),Double.valueOf(child.child("from").child("lng").getValue().toString()));
                            destinationLngLng = new LatLng(Double.valueOf(child.child("to").child("lat").getValue().toString()),Double.valueOf(child.child("to").child("lng").getValue().toString()));
                            if(destinationLngLng != new LatLng(0.0,0.0)){
                                getRouteToMarker();
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void displayCustomerRelatedObjects() {
        mRatingBar.setVisibility(View.VISIBLE);
        mPay.setVisibility(View.VISIBLE);
        mRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean b) {
                historyRideInfoDb.child("rating").setValue(rating);
                DatabaseReference mDriverRatingDb = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("rating");
                mDriverRatingDb.child(rideId).setValue(rating);
            }
        });
        mPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                payPalPayment();
            }
        });
    }

    private int PAYPAL_REQUEST_CODE = 1;
    private static PayPalConfiguration  config = new PayPalConfiguration().environment(PayPalConfiguration.ENVIRONMENT_SANDBOX)
            .clientId(PayPalConfig.PAYPAL_CLIENT_ID);

    private void payPalPayment() {
        PayPalPayment payPalPayment = new PayPalPayment(new BigDecimal(ridePrice),"USD","Tracker",PayPalPayment.PAYMENT_INTENT_SALE);
        Intent intent = new Intent(HistorySingleActivity.this,PaymentActivity.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION,config);
        intent.putExtra(PaymentActivity.EXTRA_PAYMENT,payPalPayment);
        startActivityForResult(intent,PAYPAL_REQUEST_CODE);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==PAYPAL_REQUEST_CODE){
            if(resultCode == Activity.RESULT_OK){

            }
            else{
                Toast.makeText(getApplicationContext(),"payment unsuccessful",Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this,PayPalService.class));
    }

    private void getUserInformation(String otherUserDriverOrCustomer, String otherUserId) {
        DatabaseReference mOtherUserDb = FirebaseDatabase.getInstance().getReference().child("Users").child(otherUserDriverOrCustomer).child(otherUserId);
        mOtherUserDb.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    Map<String,Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name") != null){
                        userName.setText(map.get("name").toString());
                    }
                    if(map.get("phone") != null){
                        userPhone.setText(map.get("phone").toString());
                    }if(map.get("profileImageUrl") != null){
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(userImage);
                    }

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    private String getDate(Long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp*1000);
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy hh:mm");
        String date = df.format(cal.getTime());
        return date;
    }

    private void getRouteToMarker(){

        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(pickupLatLng,destinationLngLng)
                .build();
        routing.execute();

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

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(pickupLatLng);
        builder.include(destinationLngLng);

        LatLngBounds bounds = builder.build();
        int width = getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (width*0.2);

        CameraUpdate  cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds,padding);
        mMap.animateCamera(cameraUpdate);
        mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pickup)));
        mMap.addMarker(new MarkerOptions().position(destinationLngLng).title("destination"));
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
