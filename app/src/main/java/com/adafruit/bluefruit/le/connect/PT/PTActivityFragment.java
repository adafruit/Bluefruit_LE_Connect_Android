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
        meetings.add(new Meeting(new Patient("Wilson", 65, 152, 5), new Date(2017,4,20), new Time(8,0,0), new Time(9,0,0)));
        meetings.add(new Meeting(new Patient("Trent", 72, 160, 6), new Date(2017,4,22), new Time(9,0,0), new Time(10,0,0)));
        meetings.add(new Meeting(new Patient("Jordan", 62, 158, 7), new Date(2017,4,25), new Time(12,0,0), new Time(13,0,0)));
        meetings.add(new Meeting(new Patient("Yuzhong", 61, 148, 5), new Date(2017,4,28), new Time(14,0,0), new Time(15,0,0)));
        meetings.add(new Meeting(new Patient("Issac", 55, 136, 6), new Date(2017,4,30), new Time(15,0,0), new Time(16,0,0)));
        meetingsAdapter.notifyDataSetChanged();

        return view;
    }
}
