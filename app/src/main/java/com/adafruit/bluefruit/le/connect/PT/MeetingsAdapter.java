package com.adafruit.bluefruit.le.connect.PT;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;

import java.util.ArrayList;

/**
 * Created by yhuang on 2/21/2017.
 */

public class MeetingsAdapter extends ArrayAdapter<Meeting> {
    private PTActivity mActivity = new PTActivity();

    public MeetingsAdapter(Context context, ArrayList<Meeting> meetings) {
        super(context, 0, meetings);
        mActivity = (PTActivity) context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // get the item from this position
        final Meeting meeting = getItem(position);
        // check if a view is being used, else inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.meeting_pt, parent, false);
        }
        // Lookup the view to populate items
        TextView name = (TextView) convertView.findViewById(R.id.meeting_name);
        TextView time = (TextView) convertView.findViewById(R.id.meeting_time);
        // Populate the data into the template view using the data object
        name.setText(meeting.getPatientName());
        time.setText(meeting.getStartTime());

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MeetingSummaryFragment f = new MeetingSummaryFragment();
                // supply patient info
                Bundle args = new Bundle();
                args.putString("name", meeting.getPatientName());
                args.putInt("age", meeting.getPatientAge());
                args.putInt("weight", meeting.getPatientWeight());
                args.putInt("height", meeting.getPatientHeight());
                f.setArguments(args);

                mActivity.transitionToFragment(f);
            }
        });

        // Return completed view to render on screen
        return convertView;
    }
}
