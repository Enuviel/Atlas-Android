/*
 * Copyright (c) 2015 Layer. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.layer.atlas;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.layer.atlas.Atlas.Tools;


/**
 * view
 * maskview
 * view
 * maskview
 * 
 * 
 * @author Oleg Orlov
 * @since  8 May 2015
 */
public class AtlasFrameLayout extends FrameLayout {

    private static final String TAG = AtlasFrameLayout.class.getSimpleName();
    private static final boolean debug = false;
    
    private float[] corners = new float[] { 0, 0, 0, 0 };
    private boolean refreshShape = true;
    
    private RectF pathRect = new RectF();
    private Path shaper = new Path();
    
    private Drawable maskDrawable;
    private Bitmap  maskBitmap;
    private Canvas  maskCanvas;
    private Paint   maskPaint;
    private Bitmap  surfaceBitmap;
    private Canvas  surfaceCanvas;
    private Paint   plainPaint;
    {
        plainPaint = new Paint();
        
        maskPaint = new Paint();
        maskPaint.setAntiAlias(true);
        maskPaint.setDither(true);
        maskPaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
    }
    
    public AtlasFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AtlasFrameLayout(Context context) {
        super(context);
        if (debug) Log.w(TAG, "AtlasFrameLayout() no attrs");
        prepareRendering();
    }

    public AtlasFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (debug) Log.w(TAG, "AtlasFrameLayout() attrs: " + attrs + ", defStyle: " + defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AtlasFrameLayout, defStyle, 0);
        try {
            int resourceId = a.getResourceId(R.styleable.AtlasFrameLayout_maskDrawable, 0);
            if (debug) Log.w(TAG, "AtlasFrameLayout() resourceId: " + resourceId);
            if (resourceId > 0) maskDrawable = getResources().getDrawable(resourceId);
        } finally {
            a.recycle();
        }
        
        prepareRendering();
    }

    public void setCornersDp(float[] cornerRadii) {
        System.arraycopy(cornerRadii, 0, this.corners, 0, 4);
        refreshShape = true;
    }
    
    /** Mask is applied after all children combined. Background is not masked */
    public void setMask(Drawable maskDrawable) {
        this.maskDrawable = maskDrawable;
        refreshShape = true;
        invalidate();
    }
    
    /** SOFTWARE rendering is required for canvas.clipPath on Android API version lower 18 */
    private void prepareRendering() {
        // dispatchDraw() is not called with hardware layer when underlying view invalidates so it doesn't work with Atlas.Drawable
        if (true|| android.os.Build.VERSION.SDK_INT < 18) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            if (debug)  Log.d(TAG, "setSoftwareRendering() software rendering...");
        }
        // not sure if it has an effect, bitmap is created even if flag is off
        setDrawingCacheEnabled(false);
    }
    
    /** 
     * @deprecated use {@link #setMask(Drawable)} 
     * <p><b>Note:</b> may be worth to optimize bitmap usage for masks first 
     */
    public void setCornerRadiusDp(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        this.corners[0] = topLeft;
        this.corners[1] = topRight;
        this.corners[2] = bottomRight;
        this.corners[3] = bottomLeft;
    }
    
    private void checkMasks(int width, int height) {
        if (surfaceBitmap == null || surfaceBitmap.getWidth() != width || surfaceBitmap.getHeight() != height
                || refreshShape) {
            
            surfaceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            surfaceCanvas = new Canvas(surfaceBitmap);
            
            maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            maskCanvas = new Canvas(maskBitmap);
            
            if (debug) Log.w(TAG, "checkMasks() maskBitmap: [" + maskBitmap.getWidth() + ", " + maskBitmap.getHeight() + "], bytes: " + maskBitmap.getByteCount());
            refreshShape = false;
        }
        
        maskCanvas.drawColor(0, Mode.CLEAR);
        maskDrawable.setBounds(0, 0, width, height);
        maskDrawable.draw(maskCanvas);
        
    }
    
    /** 
     * TODO: utilize {@link #getDrawingCache()} created by {@link #buildDrawingCache()} before {@link #onDraw(Canvas)} 
     * to reuse this bitmap for masks (available only for {@link #LAYER_TYPE_SOFTWARE}  )
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // clipPath according to shape
        
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        
        if (debug)  Log.d(TAG, "dispatchDraw() drawSize: " + width + "x" + height 
                + ", measuredSize: " + getMeasuredWidth() + "x" + getMeasuredHeight()
                + ", size: " + getWidth() + "x" + getHeight()
                + ", resetShape: " + refreshShape + ", from: " + Dt.printStackTrace(7));
        
        if (maskDrawable != null) {
            
            checkMasks(width, height);
            surfaceCanvas.drawColor(0, Mode.CLEAR);
            super.dispatchDraw(surfaceCanvas);
            
            surfaceCanvas.drawBitmap(maskBitmap, 0, 0, maskPaint);
            
            canvas.drawBitmap(surfaceBitmap, 0, 0, plainPaint);
            
        } else {
            if (true) {
                super.dispatchDraw(canvas);
                return;
            }
            if (refreshShape || true) {
                shaper.reset();
                pathRect.set(0, 0, width, height);
                float[] roundRectRadii = Atlas.Tools.getRoundRectRadii(corners, getResources().getDisplayMetrics());
                shaper.addRoundRect(pathRect, roundRectRadii,  Direction.CW);
                
                refreshShape = false;
            }
            
            int saved = canvas.save();
            canvas.clipPath(shaper);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(saved);
        }
        
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshShape = true;
    }
    
    /**
     * Android's [match_parent;match_parent] behaves weird - it will finally set your's view size to [parent_width, 0].<br>
     * Non-zero results comes from minWidth/minHeight and background's padding, but not from parent. 
     * So if spec is UNSPECIFIED, we should never tell Android that [0, 0] is OK for us
     * <pre>
     * FrameLayout 
     *      view1 [match_parent; match_parent]  ->  [parent_width; 0]
     * 
     * FrameLayout 
     *      view1 [match_parent; match_parent]  ->  [parent_width; parent_height]
     *      view2 [match_parent; match_parent]  ->  [parent_width; parent_height]
     * 
     * FrameLayout 
     *      view1 [match_parent; match_parent] + min[30dp; 30dp]  ->  [parent_width; 30dp]
     * 
     * FrameLayout 
     *      view1 [match_parent; match_parent]  ->  [parent_width; 0]
     *      view2 [30dp; 30dp]                  ->  [30dp; 30dp]
     * 
     * FrameLayout 
     *      view1 [30dp; 30dp]                  ->  [30dp; 30dp]
     *      view2 [match_parent; match_parent]  ->  [parent_width; 0]
     * </pre>
     *
     * <p>
     * Caused by        <a href="https://code.google.com/p/android/issues/detail?id=77225">bug in FrameLayout</a><br>
     * 
     * Commit:          <a href="https://android.googlesource.com/platform/frameworks/base/+/a174d7a0d5475dbae2b48f7359abf1637a882896%5E%21/#F0">https://android.googlesource.com/platform/frameworks/base/+/a174d7a0d5475dbae2b48f7359abf1637a882896%5E%21/#F0</a> <br>
     * Duplicate issue: <a href="https://code.google.com/p/android/issues/detail?id=136131">https://code.google.com/p/android/issues/detail?id=136131</a> 
     */
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mWidthBefore  = getMeasuredWidth();
        int mHeightBefore = getMeasuredHeight();
        if (debug) Log.w(TAG, "onMeasure() before: " + mWidthBefore + "x" + mHeightBefore
                + ", spec: " + Tools.toStringSpec(widthMeasureSpec, heightMeasureSpec));
        
        //super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        superMeasure(widthMeasureSpec, heightMeasureSpec);
        
        int mWidthAfter = getMeasuredWidth();
        int mHeightAfter = getMeasuredHeight();
        int maxChildWidth = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getMeasuredWidth() > maxChildWidth) {
                if (debug) Log.w(TAG, "onMeasure() child: " + i + ", width: " + child.getMeasuredWidth() + " > maxWidth: " + maxChildWidth + ", visibility: " + child.getVisibility() + ", " + child);
                maxChildWidth = child.getMeasuredWidth();
            }
        }
        
        if (debug) Log.w(TAG, "onMeasure() after: " + mWidthAfter + "x" + mHeightAfter);
    }
    
    private final ArrayList<View> mMatchParentChildren = new ArrayList<View>(1);
    
    private void superMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();

        final boolean measureMatchParentChildren =
                MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
                MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
        mMatchParentChildren.clear();

        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;
        
        boolean mMeasureAllChildren = false;
        
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (mMeasureAllChildren || child.getVisibility() != GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                maxWidth  = Math.max(maxWidth,  child.getMeasuredWidth()  + lp.leftMargin + lp.rightMargin);
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.topMargin  + lp.bottomMargin);
                childState = combineMeasuredStates(childState, child.getMeasuredState());
                if (measureMatchParentChildren) {
                    if (lp.width == LayoutParams.MATCH_PARENT || lp.height == LayoutParams.MATCH_PARENT) {
                        mMatchParentChildren.add(child);
                    }
                }
            }
        }

        // Account for padding too
        maxWidth += getPaddingLeftWithForeground() + getPaddingRightWithForeground();
        maxHeight += getPaddingTopWithForeground() + getPaddingBottomWithForeground();

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        // Check against our foreground's minimum height and width
        final Drawable drawable = getForeground();
        if (drawable != null) {
            maxHeight = Math.max(maxHeight, drawable.getMinimumHeight());
            maxWidth = Math.max(maxWidth, drawable.getMinimumWidth());
        }

        setMeasuredDimension(
                resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT));

        count = mMatchParentChildren.size();
        for (int i = 0; i < count; i++) {
            final View child = mMatchParentChildren.get(i);

            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int childWidthMeasureSpec;
            int childHeightMeasureSpec;
            
            if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth() -
                        getPaddingLeftWithForeground() - getPaddingRightWithForeground() -
                        lp.leftMargin - lp.rightMargin,
                        MeasureSpec.EXACTLY);
            } else {
                childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        getPaddingLeftWithForeground() + getPaddingRightWithForeground() +
                        lp.leftMargin + lp.rightMargin,
                        lp.width);
            }
            
            if (lp.height == LayoutParams.MATCH_PARENT) {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight() -
                        getPaddingTopWithForeground() - getPaddingBottomWithForeground() -
                        lp.topMargin - lp.bottomMargin,
                        MeasureSpec.EXACTLY);
            } else {
                childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                        getPaddingTopWithForeground() + getPaddingBottomWithForeground() +
                        lp.topMargin + lp.bottomMargin,
                        lp.height);
            }

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    private int getPaddingBottomWithForeground() {
        return 0;
    }

    private int getPaddingTopWithForeground() {
        return 0;
    }

    private int getPaddingRightWithForeground() {
        return 0;
    }

    private int getPaddingLeftWithForeground() {
        return 0;
    }
    
    //-------------------  DEBUG PURPOSES STUFF  ----------------------------------
    public void draw(Canvas canvas) {
        if (debug) Log.d(TAG, "draw()      .layout from: " + Dt.printStackTrace(7));
        super.draw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (debug) Log.d(TAG, "onDraw()    .layout from: " + Dt.printStackTrace(7));
        super.onDraw(canvas);
    }
    
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (debug) Log.d(TAG, "drawChild() .layout from: " + Dt.printStackTrace(7));
        return super.drawChild(canvas, child, drawingTime);
    }
    
    @Override
    public void invalidate() {
        if (debug) Log.w(TAG, "invalidate() .layout from: " + Dt.printStackTrace(7));
        super.invalidate();
    }

    @Override
    public void addView(View view, int index, android.view.ViewGroup.LayoutParams params) {
        if (debug) Log.w(TAG, "addView() view: " + desc(view) + " index: " + index+ " params: " + params + ", - - - - " + view);
        super.addView(view, index, params);
    }

    @Override
    public void removeView(View view) {
        String viewDesc = view.getTag() == null ? view.getClass().getSimpleName() : "" + view.getTag();
        if (debug) Log.w(TAG, "removeView() view: " + desc(view) + ", - - - - " + view);
        super.removeView(view);
    }

    @Override
    public void removeViewInLayout(View view) {
        String viewDesc = view.getTag() == null ? view.getClass().getSimpleName() : "" + view.getTag();
        if (debug) Log.w(TAG, "removeViewInLayout() view: " + desc(view) + ", - - - - " + view);
        super.removeViewInLayout(view);
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        if (debug) Log.d(TAG, "removeViewsInLayout() start: " + start+ " count: " + count);
        super.removeViewsInLayout(start, count);
    }

    @Override
    public void removeViewAt(int index) {
        if (debug) Log.d(TAG, "removeViewAt() index: " + index);
        super.removeViewAt(index);
    }

    @Override
    public void removeViews(int start, int count) {
        if (debug) Log.d(TAG, "removeViews() start: " + start+ " count: " + count);
        super.removeViews(start, count);
    }
    
    private static String desc(View view) {
        String viewDesc = view.getTag() == null ? view.getClass().getSimpleName() : "" + view.getTag();
        return viewDesc;
    }
    
    ///------------
    
    
    
    

}
