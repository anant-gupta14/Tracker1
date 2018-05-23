package com.dev.infinitoz.tracker1.historyRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.dev.infinitoz.tracker1.HistorySingleActivity;
import com.dev.infinitoz.tracker1.R;

public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener{

    public TextView rideId;
    public TextView time;

    public HistoryViewHolders(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);
        rideId = itemView.findViewById(R.id.rideId);
        time = itemView.findViewById(R.id.time);
    }

    @Override
    public void onClick(View view) {
        Intent intent = new Intent(view.getContext(),HistorySingleActivity.class);
        Bundle b = new Bundle();
        b.putString("rideId",rideId.getText().toString());
        intent.putExtras(b);
        view.getContext().startActivity(intent);

    }
}
