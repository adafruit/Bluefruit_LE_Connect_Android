package com.adafruit.bluefruit.le.connect;

import android.content.res.AssetManager;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    public static String readAssetsFile(String filename, AssetManager assetManager) {
        String result = null;

        try {
            InputStream is = assetManager.open(filename);
            int size = is.available();

            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            result = new String(buffer, "UTF-8");

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String copyAssetFile(AssetManager assetManager, String inputFilename, String outputFilename) {

        String outPath = null;

        try {
            InputStream input = assetManager.open(inputFilename);

            // Create new file to copy into.
            outPath = Environment.getExternalStorageDirectory() + java.io.File.separator + outputFilename;
            File outFile = new File(outPath);
            OutputStream output = new FileOutputStream(outFile);
            copyFile(input, output);
            input.close();
            output.flush();
            output.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return outPath;
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}