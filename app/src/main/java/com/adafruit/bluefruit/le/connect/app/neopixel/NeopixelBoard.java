package com.adafruit.bluefruit.le.connect.app.neopixel;


import android.content.Context;

import com.adafruit.bluefruit.le.connect.FileUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NeopixelBoard {
    static final short kDefaultType = 82;

    String name;
    byte width, height;
    byte components;
    byte stride;
    short type;

    public NeopixelBoard(String name, byte width, byte height, byte components, byte stride, short type) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.components = components;
        this.stride = stride;
        this.type = type;
    }

    public NeopixelBoard(Context context, int standardIndex, short type) {

        String boardsJsonString = FileUtils.readAssetsFile("neopixel" + File.separator + "NeopixelBoards.json", context.getAssets());
        try {
            JSONArray boardsArray = new JSONArray(boardsJsonString);
            JSONObject boardJson = boardsArray.getJSONObject(standardIndex);

            name = boardJson.getString("name");
            width = (byte) boardJson.getInt("width");
            height = (byte) boardJson.getInt("height");
            components = (byte) boardJson.getInt("components");
            stride = (byte) boardJson.getInt("stride");
            this.type = type;

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

