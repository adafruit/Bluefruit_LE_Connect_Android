package com.adafruit.bluefruit.le.connect.ui.keyboard;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;

public class CustomEditTextFormatter {

    public static void attachToEditText(final EditText editText, final int maxNumCharacters, final String separator, final int groupCharactersCount) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();

                String newText = formatText(text, maxNumCharacters, separator, groupCharactersCount);

                if (!text.equals(newText)) {
                    editText.setText(newText);
                    editText.setSelection(newText.length());
                }
            }
        });
    }

    public static String formatText(String text, int maxNumCharacters, String separator, int groupCharactersCount) {
        // Split the string into character groups
        String mergedText = text.replaceAll(separator, "");

        if (mergedText.length() > maxNumCharacters) {
            mergedText = mergedText.substring(0, maxNumCharacters);
        }

        String[] characterGroups = splitStringEvery(mergedText, groupCharactersCount);
        String newText = TextUtils.join(separator, characterGroups);

        return newText;
    }


    private static String[] splitStringEvery(String s, int interval) {         // based on: http://stackoverflow.com/questions/12295711/split-a-string-at-every-nth-position
        int arrayLength = (int) Math.ceil(((s.length() / (double) interval)));
        String[] result = new String[arrayLength];

        int j = 0;
        int lastIndex = result.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            result[i] = s.substring(j, j + interval);
            j += interval;
        }
        if (lastIndex >= 0) {
            result[lastIndex] = s.substring(j);
        }

        return result;
    }


}
