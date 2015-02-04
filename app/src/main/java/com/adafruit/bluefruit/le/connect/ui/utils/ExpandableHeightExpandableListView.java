// Based on ExpandedHeightGridView from http://stackoverflow.com/questions/8481844/gridview-height-gets-cut

package com.adafruit.bluefruit.le.connect.ui.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ScrollView;

public class ExpandableHeightExpandableListView extends ExpandableListView {

    private boolean mExpanded = false;

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
        return mExpanded;
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
        mExpanded = expanded;
    }


    public void scrollToGroup(int groupPosition, View view, ScrollView parentScrollView){
        final float baseY = getY();
        final float currentGroupPosY = baseY + view.getY();
        final int currentScrollY = parentScrollView.getScrollY();
        final View nextGroupView = findViewWithTag(groupPosition+1);

        if (currentScrollY > currentGroupPosY) {
            parentScrollView.smoothScrollTo(parentScrollView.getScrollX(),  view.getTop());
        }
        else if (nextGroupView != null) {
            final float nextGroupPosY = baseY + nextGroupView.getY();
            if (currentScrollY + parentScrollView.getHeight() < nextGroupPosY) {
                parentScrollView.smoothScrollTo(0,  nextGroupView.getBottom());
            }
        }
        else {
            parentScrollView.smoothScrollTo(parentScrollView.getScrollX(), getBottom()-parentScrollView.getHeight());
        }
    }
}