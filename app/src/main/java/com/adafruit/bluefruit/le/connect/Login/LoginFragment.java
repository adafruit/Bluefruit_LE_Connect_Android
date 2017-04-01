package com.adafruit.bluefruit.le.connect.Login;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.adafruit.bluefruit.le.connect.PT.PTActivity;
import com.adafruit.bluefruit.le.connect.Patient.PatientActivity;
import com.adafruit.bluefruit.le.connect.R;


public class LoginFragment extends Fragment {

    private View view;
    private Button patientButton;
    private Button therapistButton;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_login, container, false);

        patientButton = (Button) view.findViewById(R.id.login_patient_button);
        therapistButton = (Button) view.findViewById(R.id.login_therapist_button);

        patientButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), PatientActivity.class));
            }
        });

        therapistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), PTActivity.class));
            }
        });

        return view;
    }



}
