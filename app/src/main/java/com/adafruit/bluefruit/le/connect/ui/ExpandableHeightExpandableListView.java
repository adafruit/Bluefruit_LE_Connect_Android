// ExpandedHeightGridView from http://stackoverflow.com/questions/8481844/gridview-height-gets-cut

package com.adafruit.bluefruit.le.connect.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.GridView;

public class ExpandableHeightExpandableListView extends ExpandableListView {

    boolean expanded = false;

    public ExpandableHeightExpandableListView(Context context) {
        super(context);
    }

    public ExpandableHeightExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExpandableHeightExpandableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // HACK! TAKE THAT ANDROID!
        if (isExpanded()) {
            // Calculate entire height by providing a very large height hint.
            // View.MEASURED_SIZE_MASK represents the largest height possible.
            int expandSpec = MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, expandSpec);

            ViewGroup.LayoutParams params = getLayoutParams();
            params.height = getMeasuredHeight();
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}