package com.adafruit.bluefruit.le.connect.Patient;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.adafruit.bluefruit.le.connect.R;

import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class PatientHomeFragment extends Fragment {

    private View view;
    private ExercisesAdapter exercisesAdapter;
    private ListView exercisesListView;
    private ArrayList<Exercise> exercises;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_patient_home, container, false);

        exercises = new ArrayList<>();
        exercisesAdapter = new ExercisesAdapter(getContext(), exercises);
        exercisesListView = (ListView) view.findViewById(R.id.exercise_list);
        exercisesListView.setAdapter(exercisesAdapter);
        exercises.add(new Exercise("squat", false));
        exercisesAdapter.notifyDataSetChanged();


        return view;
    }
}
