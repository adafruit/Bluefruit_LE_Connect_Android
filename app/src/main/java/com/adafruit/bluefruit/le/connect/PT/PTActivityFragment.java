package com.adafruit.bluefruit.le.connect.PT;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.adafruit.bluefruit.le.connect.R;

import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class PTActivityFragment extends Fragment {

    private View view;
    private MeetingsAdapter meetingsAdapter;
    private ListView meetingListView;
    private ArrayList<Meeting> meetings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_pt, container, false);

        meetings = new ArrayList<>();
        meetingsAdapter = new MeetingsAdapter(getContext(), meetings);
        meetingListView = (ListView) view.findViewById(R.id.meeting_list);
        meetingListView.setAdapter(meetingsAdapter);

        // add an example appointment
        meetings.add(new Meeting(new Patient('Wilson'), new Date(2017,3,20), new Time(10,0,0), new Time(11,0,0)));
        meetingsAdapter.notifyDataSetChanged();

        return view;
    }
}
