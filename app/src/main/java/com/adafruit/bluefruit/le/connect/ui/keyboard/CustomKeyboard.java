package com.adafruit.bluefruit.le.connect.ui.keyboard;

import android.app.Activity;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;

import com.adafruit.bluefruit.le.connect.R;

public class CustomKeyboard {

    // Keys constants
    private static final int kKeyDelete = -1;
    private static final int kKeyReturn = -2;

    // Data
    private KeyboardView mKeyboardView;
    private Activity mActivity;
    private int mCurrentKeyboardId;

    public CustomKeyboard(Activity activity) {
        mActivity = activity;

        // Create the keyboard view
        mKeyboardView = new KeyboardView(activity, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

        // Select a parent for the keyboard. First search for a R.id.keyboardContainer viewgroup. If not found, use the rootWindow as parent
        ViewGroup keyboardContainer = (ViewGroup) activity.findViewById(R.id.keyboardContainer);
        ViewGroup parentViewGroup;
        if (keyboardContainer != null) {
            parentViewGroup = keyboardContainer;
        } else {
            int currentapiVersion = android.os.Build.VERSION.SDK_INT;
            // Get the root view to add the keyboard subview
            ViewGroup rootView;
            if (currentapiVersion > Build.VERSION_CODES.KITKAT) {
                // Workaround for devices with softkeys. We cant not use  getRootView() because the keyboard would be below the softkeys.
                rootView = (ViewGroup) activity.findViewById(android.R.id.content);
            } else {
                rootView = (ViewGroup) activity.getWindow().getDecorView().getRootView();
            }

            // Create a dummy relative layout to align the keyboardView to the bottom
            ViewGroup relativeLayout = new RelativeLayout(activity);
            relativeLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            rootView.addView(relativeLayout);

            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);     // Align to the bottom of the relativelayout
            parentViewGroup = relativeLayout;
        }


        mKeyboardView.setLayoutParams(params);
        mKeyboardView.setFocusable(true);
        mKeyboardView.setFocusableInTouchMode(true);
        mKeyboardView.setVisibility(View.GONE);

        parentViewGroup.addView(mKeyboardView);

        // Configure keyboard view
        mKeyboardView.setPreviewEnabled(false);
        KeyboardView.OnKeyboardActionListener mOnKeyboardActionListener = new KeyboardView.OnKeyboardActionListener() {
            @Override
            public void onKey(int primaryCode, int[] keyCodes) {
                View focusCurrent = mActivity.getWindow().getCurrentFocus();
                if (focusCurrent != null && (focusCurrent instanceof EditText)) {
                    EditText edittext = (EditText) focusCurrent;
                    Editable editable = edittext.getText();
                    int start = edittext.getSelectionStart();

                    if (primaryCode == kKeyDelete) {
                        if (editable != null && start > 0) editable.delete(start - 1, start);
                    } else if (primaryCode == kKeyReturn) {
                        View nextFocusView = edittext.focusSearch(View.FOCUS_DOWN);
                        if (nextFocusView != null && (nextFocusView instanceof EditText)) {
                            nextFocusView.requestFocus();
                        } else {
                            hideCustomKeyboard();
                        }
                    } else {
                        editable.insert(start, Character.toString((char) primaryCode));
                    }
                }
            }

            @Override
            public void onPress(int arg0) {
            }

            @Override
            public void onRelease(int primaryCode) {
            }

            @Override
            public void onText(CharSequence text) {
            }

            @Override
            public void swipeDown() {
            }

            @Override
            public void swipeLeft() {
            }

            @Override
            public void swipeRight() {
            }

            @Override
            public void swipeUp() {
            }
        };
        mKeyboardView.setOnKeyboardActionListener(mOnKeyboardActionListener);

        // Hide the standard keyboard initially
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    public void attachToEditText(final EditText editText, int keyboardLayoutId) {
        // Keyboard layout is saved into the editText tag (to reuse the same keyboardView with different keyboard layouts)
        editText.setTag(keyboardLayoutId);

        // Attach custom keyboard to onFocusChange
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    showCustomKeyboard(view);
                } else {
                    hideCustomKeyboard();
                }
            }
        });

        // Attach custom keyboard to onClick
        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCustomKeyboard(view);
            }
        });

        // Fix for cursor movement (based on http://forum.xda-developers.com/showthread.php?t=2497237)
        editText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (!isCustomKeyboardVisible() || mCurrentKeyboardId != (Integer)(view.getTag())) {
                    view.requestFocus();
                    showCustomKeyboard(view);
                }


                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        EditText editText = (EditText) view;
                        Layout layout = ((EditText) view).getLayout();
                        if (layout != null) {
                            float x = event.getX() + editText.getScrollX();
                            int offset = layout.getOffsetForHorizontal(0, x);
                            if (offset > 0) {
                                if (x > layout.getLineMax(0))
                                    editText.setSelection(offset);
                                else
                                    editText.setSelection(offset - 1);
                            }
                        }
                        break;

                }

                /*
                int inType = editText.getInputType();       // Backup the input type
                editText.setInputType(InputType.TYPE_NULL); // Disable standard keyboard
                editText.onTouchEvent(event);               // Call native handler
                editText.setInputType(inType);              // Restore input type
*/

                return true;
            }
        });

        // Disable suggestions
        editText.setInputType(editText.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }


    public void hideCustomKeyboard() {
        mKeyboardView.setVisibility(View.GONE);
        mKeyboardView.setEnabled(false);
    }

    public void showCustomKeyboard(View view) {
        EditText editText = (EditText) view;
        final int keyboardId = (Integer) editText.getTag();
        if (mCurrentKeyboardId != keyboardId) {
            Keyboard keyboard = new Keyboard(mActivity, keyboardId);
            mKeyboardView.setKeyboard(keyboard);
            mCurrentKeyboardId = keyboardId;
        }
        mKeyboardView.setVisibility(View.VISIBLE);
        mKeyboardView.setEnabled(true);
        ((InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public boolean isCustomKeyboardVisible() {
        return mKeyboardView.getVisibility() == View.VISIBLE;
    }

}