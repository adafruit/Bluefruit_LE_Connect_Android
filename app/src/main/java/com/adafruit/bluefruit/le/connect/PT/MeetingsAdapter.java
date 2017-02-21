package com.adafruit.bluefruit.le.connect.PT;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

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
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.exercise_patient, parent, false);
        }
        // Lookup the view to populate items
        TextView name = (TextView) convertView.findViewById(R.id.meeting_name);
        // Populate the data into the template view using the data object
        name.setText(meeting.getPatientName());

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivity.transitionToFragment(new PTActivityFragment());
            }
        });

        // Return completed view to render on screen
        return convertView;
    }
}
