package com.dev.infinitoz.tracker1;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.dev.infinitoz.tracker1.historyRecyclerView.HistoryAdapter;
import com.dev.infinitoz.tracker1.historyRecyclerView.HistoryObject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView mHistoryRecycleView;
    private RecyclerView.Adapter mHistoryAdapter;
    private RecyclerView.LayoutManager mHistoryLayoutManager;
    private String customerOrDriver,userId;

    private List<HistoryObject> resultHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        mHistoryRecycleView = findViewById(R.id.historyRecylerView);
        mHistoryRecycleView.setNestedScrollingEnabled(false);
        mHistoryRecycleView.setHasFixedSize(true);

        mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);
        mHistoryRecycleView.setLayoutManager(mHistoryLayoutManager);
        mHistoryAdapter = new HistoryAdapter(getDataSetHistory(),HistoryActivity.this);
        mHistoryRecycleView.setAdapter(mHistoryAdapter);

        customerOrDriver = getIntent().getExtras().getString("customerOrDriver");
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        getUserHistoryIds();


    }

    private void getUserHistoryIds() {
        DatabaseReference userHistoryDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child(customerOrDriver).child(userId).child("history");
        userHistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    for(DataSnapshot history: dataSnapshot.getChildren()){
                        FetchRideInformation(history.getKey());
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void FetchRideInformation(String rideKey) {
        DatabaseReference historyDatabase = FirebaseDatabase.getInstance().getReference().child("history").child(rideKey);
        historyDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    String rideId = dataSnapshot.getKey();
                    Long timestamp = 0L;
                    for(DataSnapshot child: dataSnapshot.getChildren()){
                        if(child.getKey().equals("timestamp")){
                            timestamp = Long.valueOf(child.getValue().toString());
                        }
                    }
                    HistoryObject obj = new HistoryObject(rideId,getDate(timestamp));
                    resultHistory.add(obj);
                    mHistoryAdapter.notifyDataSetChanged();

                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });

    }

    private String getDate(Long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp*1000);
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy hh:mm");
        String date = df.format(cal.getTime());
        return date;
    }

    private List<HistoryObject> getDataSetHistory() {
        return resultHistory;
    }
}
