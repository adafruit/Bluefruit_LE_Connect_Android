package com.adafruit.bluefruit.le.connect.PT;

import java.sql.Date;
import java.sql.Time;

/**
 * Created by yhuang on 2/21/2017.
 */

public class Meeting {
    private Patient patient;
    private Date date;
    private Time start;
    private Time end;

    public Meeting(Patient patient, Date date, Time start, Time end) {
        this.patient = patient;
        this.date = date;
        this.start = start;
        this.end = end;
    }

    public String getPatientName() {
        return this.patient.getName();
    }

    public Integer getPatientAge() {
        return this.patient.getAge();
    }

    public Integer getPatientWeight() {
        return this.patient.getWeight();
    }

    public Integer getPatientHeight() {
        return this.patient.getHeight();
    }

    public String getStartTime() {
        return this.start.toString();
    }
}
