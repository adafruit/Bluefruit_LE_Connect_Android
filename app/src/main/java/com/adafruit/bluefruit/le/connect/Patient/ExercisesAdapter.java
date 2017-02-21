package com.adafruit.bluefruit.le.connect.Patient;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;

import java.util.ArrayList;

/**
 * Created by Jordan on 2/19/17.
 */

public class ExercisesAdapter extends ArrayAdapter<Exercise> {

    private PatientActivity patientActivity;

    public ExercisesAdapter(Context context, ArrayList<Exercise> exercises) {
        super(context, 0, exercises);
        patientActivity = (PatientActivity) context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // get the item from this position
        final Exercise exercise = getItem(position);
        // check if a view is being used, else inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.exercise_patient, parent, false);
        }
        // Lookup the view to populate items
        TextView name = (TextView) convertView.findViewById(R.id.exercise_name);
        // Populate the data into the template view using the data object
        name.setText(exercise.getName());

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                patientActivity.transitionToFragment(new ExerciseSummaryFragment());
            }
        });

        // Return completed view to render on screen
        return convertView;
    }
}
