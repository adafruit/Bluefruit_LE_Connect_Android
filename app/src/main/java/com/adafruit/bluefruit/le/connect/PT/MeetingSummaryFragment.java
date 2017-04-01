package com.adafruit.bluefruit.le.connect.PT;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;

public class MeetingSummaryFragment extends Fragment {
    private View view;

    private String name;
    private Integer age;
    private Integer weight;
    private Integer height;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        name = args.getString("name");
        age = args.getInt("age");
        weight = args.getInt("weight");
        height = args.getInt("height");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_meeting_summary, container, false);

        TextView nameTag = (TextView) view.findViewById(R.id.patient_name);
        TextView summaryTag = (TextView) view.findViewById(R.id.patient_summary);

        nameTag.setText(name);
        summaryTag.setText(Integer.toString(age) + " years old, " + Integer.toString(weight) + " lbs, " + Integer.toString(height) + " feet");

        return view;
    }
}
