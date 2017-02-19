package com.adafruit.bluefruit.le.connect.Patient;

/**
 * Created by Jordan on 2/19/17.
 */

public class Exercise {
    private String name;
    private Boolean completed;

    public Exercise(String name, Boolean completed) {
        this.name = name;
        this.completed = completed;
    }

    public Boolean getCompleted() {
        return this.completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
