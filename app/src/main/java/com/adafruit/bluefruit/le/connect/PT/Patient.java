package com.adafruit.bluefruit.le.connect.PT;

/**
 * Created by yhuang on 2/21/2017.
 */

public class Patient {
    private String name;
    private Integer age;
    private Integer weight;
    private Integer height;
    // TODO: add more info for patients

    public Patient(String name, Integer age, Integer weight, Integer height) {
        this.name = name;
        this.age = age;
        this.weight = weight;
        this.height = height;
    }

    public Integer getAge() {
        return this.age;
    }

    public Integer getWeight() {
        return this.weight;
    }

    public Integer getHeight() {
        return this.height;
    }

    public String getName() {
        return this.name;
    }
}
