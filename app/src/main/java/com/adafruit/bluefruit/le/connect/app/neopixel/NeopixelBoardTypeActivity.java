package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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

public class NeopixelBoardTypeActivity extends AppCompatActivity {
    // Log
    private final static String TAG = NeopixelBoardTypeActivity.class.getSimpleName();

    // Result return
    public static final String kActivityParameter_CurrentType = "kActivityParameter_CurrentType";
    public static final String kActivityResult_BoardTypeResultKey = "kActivityResult_BoardTypeResultKey";

    // UI
    private EditText mValueEditText;

    // Data
    private class NeopixelType {
        public String name;
        public short value;
    }
    private List<NeopixelType> mDefaultTypes;
    private short mCurrentType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_neopixel_boardtype);

        mCurrentType = getIntent().getShortExtra(kActivityParameter_CurrentType, NeopixelBoard.kDefaultType);

        // Read standard board types data
        String boardsJsonString = FileUtils.readAssetsFile("neopixel" + File.separator + "NeopixelTypes.json", getAssets());
        try {
            mDefaultTypes = new ArrayList<>();
            JSONArray boardsArray = new JSONArray(boardsJsonString);
            for (int i = 0; i < boardsArray.length(); i++) {
                JSONObject boardJsonObject = boardsArray.getJSONObject(i);
                NeopixelType type = new NeopixelType();
                type.name = boardJsonObject.getString("name");
                type.value = (short)boardJsonObject.getInt("value");

                mDefaultTypes.add(type);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error decoding default boards");
        }

        // UI
        mValueEditText = (EditText) findViewById(R.id.valueEditText);
        mValueEditText.setText(String.valueOf(mCurrentType));

        RecyclerView standardSizesRecyclerView = (RecyclerView) findViewById(R.id.standardTypesRecyclerView);
        if (standardSizesRecyclerView != null) {
            standardSizesRecyclerView.setHasFixedSize(true);
            standardSizesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
            RecyclerView.Adapter standardBoardSizesAdapter = new StandardBoardTypesAdapter();
            standardSizesRecyclerView.setAdapter(standardBoardSizesAdapter);
        }

    }

    public void onClickSetValue(View view) {
        String valueString = String.valueOf(mValueEditText.getText());
        short value = NeopixelBoard.kDefaultType;
        try {
            value = Short.parseShort(valueString);
        } catch (Exception e) {
            Log.d(TAG, "Cannot parse value");
        }
        mCurrentType = value;

        Intent output = new Intent();
        output.putExtra(kActivityResult_BoardTypeResultKey, mCurrentType);
        setResult(RESULT_OK, output);
        finish();
    }

    // region BoardTypesAdapter
    public class StandardBoardTypesAdapter extends RecyclerView.Adapter<StandardBoardTypesAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            public Button mItem;
            public View mCheckboxView;

            public ViewHolder(ViewGroup view) {
                super(view);
                mItem = (Button) view.findViewById(R.id.itemView);
                mCheckboxView = view.findViewById(R.id.itemCheckBox);
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

            final NeopixelType type = mDefaultTypes.get(position);
            holder.mItem.setText(type.name);
            final boolean isCurrentType = type.value == mCurrentType;
            holder.mCheckboxView.setVisibility(isCurrentType ? View.VISIBLE: View.GONE);

            holder.mItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    mCurrentType = type.value;
                    mValueEditText.setText(String.valueOf(mCurrentType));
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDefaultTypes.size();
        }
    }

    // endregion
}
