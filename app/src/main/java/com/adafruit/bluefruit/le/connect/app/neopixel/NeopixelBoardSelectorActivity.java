package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.adafruit.bluefruit.le.connect.utils.FileUtils;
import com.adafruit.bluefruit.le.connect.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NeopixelBoardSelectorActivity extends AppCompatActivity {
    // Log
    private final static String TAG = NeopixelBoardSelectorActivity.class.getSimpleName();

    // Result return
    public static final String kActivityResult_BoardIndexResultKey = "kActivityResult_BoardIndexResultKey";
    public static final String kActivityResult_LineStripResultKey = "kActivityResult_LineStripResultKey";

    // Data
    private List<String> mDefaultBoards;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_neopixel_boardselector);

        // Read standard board size data
        String boardsJsonString = FileUtils.readAssetsFile("neopixel" + File.separator + "NeopixelBoards.json", getAssets());
        try {
            mDefaultBoards = new ArrayList<>();
            JSONArray boardsArray = new JSONArray(boardsJsonString);
            for (int i = 0; i < boardsArray.length(); i++) {
                JSONObject boardJsonObject = boardsArray.getJSONObject(i);
                String boardName = boardJsonObject.getString("name");
                mDefaultBoards.add(boardName);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error decoding default boards");
        }

        // UI
        RecyclerView standardSizesRecyclerView = (RecyclerView) findViewById(R.id.standardSizesRecyclerView);
        if (standardSizesRecyclerView != null) {
            standardSizesRecyclerView.setHasFixedSize(true);
            standardSizesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
            RecyclerView.Adapter standardBoardSizesAdapter = new StandardBoardSizesAdapter();
            standardSizesRecyclerView.setAdapter(standardBoardSizesAdapter);
        }
    }

    public void onClickLineStrip(View view) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.neopixel_boardselector_enterlinestrip_title);
        final EditText input = new EditText(this);
        input.setHint(R.string.neopixel_boardselector_enterlinestrip_hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);
        alert.setView(input);
        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String valueString = String.valueOf(input.getText());
                int value = 0;
                try {
                    value = Integer.parseInt(valueString);
                } catch (Exception e) {
                    Log.d(TAG, "Cannot parse value");
                }

                Intent output = new Intent();
                output.putExtra(kActivityResult_LineStripResultKey, value);
                setResult(RESULT_OK, output);
                finish();
            }
        });
        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });
        alert.show();
    }

    // region BoardSizesAdapter
    public class StandardBoardSizesAdapter extends RecyclerView.Adapter<StandardBoardSizesAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            public Button mItem;

            public ViewHolder(ViewGroup view) {
                super(view);
                mItem = (Button) view.findViewById(R.id.itemView);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_neopixel_list_item, parent, false);
            final ViewHolder viewHolder = new ViewHolder(view);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {

            String name = mDefaultBoards.get(position);
            holder.mItem.setText(name);

            final int index = holder.getAdapterPosition();
            holder.mItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Intent output = new Intent();
                    output.putExtra(kActivityResult_BoardIndexResultKey, index);
                    setResult(RESULT_OK, output);
                    finish();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDefaultBoards.size();
        }
    }

    // endregion
}
