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

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.layer.atlas.Atlas.Tools;

/**
 * @author Oleg Orlov
 * @since  15 Jun 2015
 */
public class AtlasImageView2 extends View {
    private static final String TAG = AtlasImageView2.class.getSimpleName();
    private static final boolean debug = false;
    private static final boolean debugOutline = false;
    
    /** Backup for type that could be switched to SOFTWARE in order to render GIFs. */
    private int defaultLayerType;
    
    private Drawable drawable;
    
    private int contentWidth;
    private int contentHeight;
    private float angle;
    
    /** content width  to be used by {@link #onDraw(Canvas)} (before angle applied) */
    private int contentWorkWidth;
    /** content height to be used by {@link #onDraw(Canvas)} (before angle applied) */
    private int contentWorkHeight;
    
    private final Position pos = new Position();
    
    // TODO: 
    // - support contentDimensions: 0x0
    // - support contentDimensions + MeasureSpec.EXACT sizes 
    // - support boundaries + drawable instead of contentDimensions + drawable 
    
    //----------------------------------------------------------------------------
    public AtlasImageView2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setupPaints();
    }

    public AtlasImageView2(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupPaints();
    }

    public AtlasImageView2(Context context) {
        super(context);
        setupPaints();
    }
    
    protected void onMeasure(int widthSpec, int heightSpec) {
        int mWidthBefore  = getMeasuredWidth();
        int mHeightBefore = getMeasuredHeight();
        super.onMeasure(widthSpec, heightSpec);
        int mWidthAfter = getMeasuredWidth();
        int mHeightAfter = getMeasuredHeight();

        if (debug) Log.w(TAG, "onMeasure() before: " + mWidthBefore + "x" + mHeightBefore
                + ", spec: " + Tools.toStringSpec(widthSpec, heightSpec)
                + ", after: " + mWidthAfter + "x" + mHeightAfter
                + ", content: " + contentWidth + "x" + contentHeight + " h/w: " + (1.0f * contentHeight / contentWidth)
                );

        int widthMode = MeasureSpec.getMode(widthSpec);
        int heightMode = MeasureSpec.getMode(heightSpec);
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        
        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            if (debug) Log.w(TAG, "onMeasure() exact dimensions, skipping " + Tools.toStringSpec(widthSpec, heightSpec)); 
        } else if (widthMode == MeasureSpec.UNSPECIFIED && heightMode == MeasureSpec.UNSPECIFIED) {
            if (debug) Log.w(TAG, "onMeasure() first pass, skipping " + Tools.toStringSpec(widthSpec, heightSpec));
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            if (widthMode == MeasureSpec.EXACTLY) {
                setMeasuredDimension(w, (int)(1.0 * w * contentHeight / contentWidth));
            }
            if (widthMode == MeasureSpec.AT_MOST) {
                if (contentWidth >= w) {
                    setMeasuredDimension(w, (int)(1.0 * w * contentHeight / contentWidth));
                } else {
                    setMeasuredDimension(contentWidth, contentHeight);
                }
            }
        } else {
            if (debug) Log.w(TAG, "onMeasure() unchanged. " + Tools.toStringSpec(widthSpec, heightSpec));
        }
                
        if (debug) Log.w(TAG, "onMeasure() final: " + getMeasuredWidth() + "x" + getMeasuredHeight());
    }
    
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (debug) Log.d(TAG, "onLayout() changed: " + changed+ " left: " + left+ " top: " + top+ " right: " + right+ " bottom: " + bottom);
        setContentWorkDimensions();
    }
    
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (debug) Log.w(TAG, "onSizeChanged() w: " + w + " h: " + h+ " oldw: " + oldw+ " oldh: " + oldh);
        setContentWorkDimensions();
    }

    private void setupPaints() {
        this.defaultLayerType = getLayerType();
        debugTextPaint.setTextSize(Tools.getPxFromDp(10, getContext()));
    }
    
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        
        if (getWidth() != getMeasuredWidth() || getHeight() != getMeasuredHeight()) {
            if (debug) Log.w(TAG, "onDraw() actual: " + getWidth() + "x" + getHeight()
                    + ", measured: " + getMeasuredWidth() + "x" + getMeasuredHeight());
        }
        
        // handle Move 
        if (currentMove != null) {
            boolean stillInProgress = currentMove.handleMove(pos, System.currentTimeMillis(), currentMoveStartedAt);
            if (stillInProgress) {
                invalidate();
            } else {
                currentMove = null;
                currentMoveStartedAt = 0;
            }
        }
        
        if (drawable == null) return;
        
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bmpDrw = (BitmapDrawable) drawable;
            if (bmpDrw.getBitmap() != null) {
                if (debug) Log.w(TAG, "onDraw() bitmap: " + bmpDrw.getBitmap().getWidth() + "x" + bmpDrw.getBitmap().getHeight());
            } else {
                if (debug) Log.w(TAG, "onDraw() bitmap: null");
            }
        }
        
        if (debug) Log.w(TAG, 
                    "onDraw() content: " + contentWidth + "x" + contentHeight + ", angle: " + angle 
                    +   ", instrinsic: " + drawable.getIntrinsicWidth() + "x" + drawable.getIntrinsicHeight());
        
        int viewWidth  = getWidth();
        int viewHeight = getHeight();
        setContentWorkDimensions();
        
        int imgWidth  = contentWorkWidth;
        int imgHeight = contentWorkHeight;
        if (imgWidth == 0 || imgHeight == 0) Log.e(TAG, "onDraw() dimensions are undefined. " + contentWorkWidth + "x" + contentWorkHeight);
        
        float zoomedWidth  = (int) (imgWidth * pos.zoom);
        float zoomedHeight = (int) (imgHeight * pos.zoom);
        int left = (int) ((viewWidth  - zoomedWidth) / 2);
        int top  = (int) ((viewHeight - zoomedHeight) / 2);
        int right = (int) (left + zoomedWidth);
        int bottom = (int) (top + zoomedHeight);
        if (debug) Log.w(TAG, "onDraw() workSize: " + imgWidth + "x" + imgHeight + " pos: " + pos + ", drawable: " + left + "x" + top + " -> " + right + "x" + bottom);
        drawable.setBounds(left, top, right, bottom);

        if (!useBitmapBuffer && buffer != null) {
            buffer = null;
            bufferCanvas = null;
        }
        if (useBitmapBuffer && (buffer == null || buffer.getWidth() != viewWidth || buffer.getHeight() != viewHeight)) {
            buffer = Bitmap.createBitmap(viewWidth, viewHeight, Config.ARGB_8888);
            bufferCanvas = new Canvas(buffer);
        }
        
        Canvas workCanvas = useBitmapBuffer ? bufferCanvas : canvas;
        if (useBitmapBuffer) {          
            workCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);            // clean before using
        }
        
        if (debug) Log.w(TAG, "onDraw() useBitmapBuffer: " + useBitmapBuffer + ", buffer: " + (buffer == null ? "null" : buffer.getWidth() + "x" + buffer.getHeight()) );
        int saved = workCanvas.save();
        workCanvas.translate(pos.x, pos.y);
        if (debugOutline) workCanvas.drawRect(left, top, right, bottom, debugFillPaint);
        workCanvas.rotate(angle, 0.5f * viewWidth , 0.5f * viewHeight);
        drawable.draw(workCanvas);
        if (debugOutline) Tools.drawX(drawable.getBounds(), debugGreenPaint, workCanvas);
        workCanvas.restoreToCount(saved);
        if (useBitmapBuffer) {
            canvas.drawBitmap(buffer, 0, 0, bitmapPaint);
        }
        
        if (debugOutline) Tools.drawPlus(0, 0, getWidth(), getHeight(), debugGrayPaint, canvas);
        
        if (debugOutline && showMarker) {
            canvas.drawLine(0, lastTouch1y, getWidth(), lastTouch1y, debugRedPaint);
            canvas.drawLine(lastTouch1x, 0, lastTouch1x, getHeight(), debugRedPaint);
            canvas.drawLine(0, lastTouch2y, getWidth(), lastTouch2y, debugBluePaint);
            canvas.drawLine(lastTouch2x, 0, lastTouch2x, getHeight(), debugBluePaint);
        }
        if (debugOutline) {
            float lineHeight = debugTextPaint.getFontMetrics().descent - debugTextPaint.getFontMetrics().ascent;

            float x = Tools.getPxFromDp(20, getContext());
            float y = getHeight() - lineHeight * 5;
            
            canvas.drawText(String.format("1: %.1fx%.1f", lastTouch1x, lastTouch1y), x, y, debugTextPaint); y += lineHeight;
            canvas.drawText(String.format("2: %.1fx%.1f", lastTouch2x, lastTouch2y), x, y, debugTextPaint); y += lineHeight;
            canvas.drawText(pos.toString(), x, y, debugTextPaint); y += lineHeight;
        }
        if (debugOutline) {
            Tools.drawPlusCircle(0.5f * (lastTouch1x + lastTouch2x), 0.5f * (lastTouch1y + lastTouch2y), 10, debugRedPaint, canvas);
            Tools.drawPlusCircle(0.5f * (zoomTouch1x + zoomTouch2x), 0.5f * (zoomTouch1y + zoomTouch2y), 10, debugBluePaint, canvas);
        }
        
    }
    
    private void setContentWorkDimensions() {
        contentWorkWidth = contentWidth;
        contentWorkHeight = contentHeight;
        if (contentWorkWidth != 0 && contentWorkWidth != 0) {       // everything is set from user's dimensions
            if (debug) Log.w(TAG, "checkContentWorkWidth() set from user's dimension: " + contentWorkWidth + contentWorkHeight);
            return; 
        }
        
        if (drawable == null) {
            if (debug) Log.w(TAG, "setContentWorkWidth() no user's dimensions, no drawable");
            return;
        }
        int defaultWidth    = drawable.getIntrinsicWidth();
        int defaultHeight   = drawable.getIntrinsicHeight();

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth != 0 && viewHeight != 0) {                    // set from zoom2Fit 
            boolean flippedDimensions = flippedDimensions();
            double zoomToFitHor = 1.0 * viewWidth  / (flippedDimensions ? defaultHeight : defaultWidth);
            double zoomToFitVer = 1.0 * viewHeight / (flippedDimensions ? defaultWidth : defaultHeight);
            double defaultZoom = Math.min(zoomToFitHor, zoomToFitVer);  // both dimensions should fit
            
            if (contentWorkWidth == 0) {
                contentWorkWidth = (int) (defaultWidth * defaultZoom);
            }
            if (contentWorkHeight == 0) {
                contentWorkHeight = (int) (defaultHeight * defaultZoom);
            }
            if (debug) Log.w(TAG, "checkContentWorkWidth() set from fit2width: " + contentWorkWidth + "x" + contentWorkHeight);
        } else {
            if (contentWorkWidth == 0) {
                contentWorkWidth = defaultWidth;
            }
            if (contentWorkHeight == 0) {
                contentWorkHeight = defaultHeight;
            }
            if (debug) Log.w(TAG, "checkContentWorkWidth() set from drawable: " + contentWorkWidth + "x" + contentWorkHeight);
        }
    }

    private boolean flippedDimensions() {
        boolean flipDimensions = ((90 + ((int)angle)) % 180 == 0) ? true : false;
        return flipDimensions;
    }

    /** takes into account content:0x0 & angle calculation */
    public float getZoomToFit() {
        setContentWorkDimensions();
        
        double zoomToFitWidth  = 1.0 * getWidth()  / (flippedDimensions() ? contentWorkHeight : contentWorkWidth);
        double zoomToFitHeight = 1.0 * getHeight() / (flippedDimensions() ? contentWorkWidth : contentWorkHeight);
        
        float zoomToFit = (float) Math.min(zoomToFitWidth, zoomToFitHeight);
        if (debug) Log.d(TAG, "getZoomToFit() zoomToFit: " + zoomToFit + ", " + contentWorkWidth + "x" + contentWorkHeight);
        return zoomToFit;
    }
    
    public float getZoomToFill() {
        setContentWorkDimensions();
        
        double zoomToFitWidth = 1.0 * getWidth()   / (flippedDimensions() ? contentWorkHeight : contentWorkWidth);
        double zoomToFitHeight = 1.0 * getHeight() / (flippedDimensions() ? contentWorkWidth : contentWorkHeight);
        
        float zoomToFill = (float) Math.max(zoomToFitWidth, zoomToFitHeight);
        if (debug) Log.d(TAG, "getZoomToFit() zoomToFill: " + zoomToFill + ", " + contentWorkWidth + "x" + contentWorkHeight);
        return zoomToFill;
    }
    
    private final static Paint debugRedPaint   = new Paint();
    private final static Paint debugGreenPaint  = new Paint();
    private final static Paint debugBluePaint  = new Paint();
    private final static Paint debugGrayPaint  = new Paint();
    private final static Paint debugWhitePaint  = new Paint();
    private final static TextPaint debugTextPaint = new TextPaint();
    private final static Paint debugFillPaint = new Paint();

    static {
        debugRedPaint.setStyle(Paint.Style.STROKE);
        debugRedPaint.setPathEffect(new DashPathEffect(new float[]{10, 20f}, 0));
        debugRedPaint.setColor(Color.rgb(200, 0, 0));
        debugGreenPaint.setStyle(Paint.Style.STROKE);
        debugGreenPaint.setColor(Color.rgb(0, 200, 0));
        debugBluePaint.setStyle(Paint.Style.STROKE);
        debugBluePaint.setColor(Color.rgb(0, 0, 200));
        debugGrayPaint.setStyle(Paint.Style.STROKE);
        debugGrayPaint.setColor(Color.rgb(200, 200, 200));
        debugWhitePaint.setStyle(Paint.Style.STROKE);
        debugWhitePaint.setColor(Color.rgb(233, 233, 233));
        debugTextPaint.setColor(Color.RED);
        debugFillPaint.setStyle(Style.FILL_AND_STROKE);
        debugFillPaint.setColor(Color.argb(100, 233, 233, 233));
        
    }
    
    private float lastTouch1x, lastTouch2x;
    private float lastTouch1y, lastTouch2y;
    
    private boolean showMarker;
    
    private float dragTouch1x, dragTouch1y;
    private Position dragStart;
    
    private float zoomTouch1x, zoomTouch1y, zoomTouch2x, zoomTouch2y;
    private Position zoomStart;
    
    private GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
        @Override
        public void onLongPress(MotionEvent e) {
            performLongClick();
        }
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            boolean result = performClick();
            return result;
        }
        
    });
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean gestureDetectorResult = gestureDetector.onTouchEvent(event);
        if (debug) Log.w(TAG, "onTouchEvent() super result: " + gestureDetectorResult);
        
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE : break;
            default : if (debug) Log.d(TAG, "onTouch() event: " + Tools.toString(event));
        }
        
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_UP              : { 
                showMarker = false;
                checkBoundaries();
                break;  
            }
            case MotionEvent.ACTION_DOWN            : {
                showMarker = true; 
                dragTouch1x = event.getX(0);
                dragTouch1y = event.getY(0);
                dragStart = pos.copy();
                break;
            }
            
            case MotionEvent.ACTION_POINTER_UP: { 
                final int releasedPointer = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                dragTouch1x = event.getX(releasedPointer == 0 ? 1 : 0);
                dragTouch1y = event.getY(releasedPointer == 0 ? 1 : 0);
                dragStart = pos.copy();
                break;
            }
            
            case MotionEvent.ACTION_POINTER_DOWN    : {
                if (event.getPointerCount() == 2) {
                    zoomTouch1x = event.getX(0);
                    zoomTouch1y = event.getY(0);
                    zoomTouch2x = event.getX(1);
                    zoomTouch2y = event.getY(1);
                    zoomStart = new Position(pos);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE            : {
                if (event.getPointerCount() < 2) {
                    // drag
                    pos.x = dragStart.x + event.getX() - dragTouch1x;
                    pos.y = dragStart.y + event.getY() - dragTouch1y;
                } else { 
                    // calculate zoom
                    
                    double distanceXbefore = zoomTouch2x - zoomTouch1x;
                    double distanceYbefore = zoomTouch2y - zoomTouch1y;
                    double distanceBefore = Math.sqrt(distanceXbefore * distanceXbefore + distanceYbefore * distanceYbefore);

                    double distanceXNow = event.getX(1) - event.getX(0);
                    double distanceYNow = event.getY(1) - event.getY(0);
                    double distanceAfter  = Math.sqrt(distanceXNow    * distanceXNow    + distanceYNow    * distanceYNow);
                    
                    double newZoom = 1.0 * zoomStart.zoom * distanceAfter / distanceBefore;
                    pos.zoom = (float) newZoom;
                    
                    double centerXbefore = 0.5 * (zoomTouch1x + zoomTouch2x);
                    double centerYbefore = 0.5 * (zoomTouch1y + zoomTouch2y);
                    double centerXafter  = 0.5 * (event.getX(0) + event.getX(1));
                    double centerYafter  = 0.5 * (event.getY(0) + event.getY(1));
                    
                    double centerXcontentBefore = getContentX(centerXbefore, zoomStart);
                    double centerYcontentBefore = getContentY(centerYbefore, zoomStart);
                    
                    pos.x = (float) (centerXafter - centerXcontentBefore * newZoom - 0.5f * getWidth());
                    pos.y = (float) (centerYafter - centerYcontentBefore * newZoom - 0.5f * getHeight());
                    
                }
                break;
            }
        }
        trackLastTouch(event);
        
        invalidate();
        return true;
    }
    
    /** Ensures drawable fit in boundaries for current position and launches adjacting Move if not */
    private void checkBoundaries() {
        Position moveTo = pos.copy();
        boolean move = false;
        float minZoom = getZoomToFit();
        float maxZoom = minZoom * 3;
        if (maxZoom < getZoomToFill()) maxZoom = getZoomToFill();
        
        if (pos.zoom < minZoom) {
            moveTo = new Position(minZoom, 0, 0);
            move = true;
        } else { 
            if (pos.zoom > maxZoom) {
                moveTo.zoom = maxZoom;
                
                float viewXcenter = 0.5f * getWidth();
                float viewYcenter = 0.5f * getHeight();
                double centerXcontentBefore = getContentX(viewXcenter, pos);
                double centerYcontentBefore = getContentY(viewYcenter, pos);
                moveTo.x = (float) (viewXcenter - centerXcontentBefore * maxZoom - 0.5f * getWidth());
                moveTo.y = (float) (viewYcenter - centerYcontentBefore * maxZoom - 0.5f * getHeight());
    
                move = true;
            }
            
            RectF imageBounds = new RectF();
            float zoomedWidth  = (flippedDimensions() ? contentWorkHeight : contentWorkWidth) * moveTo.zoom;
            float zoomedHeight = (flippedDimensions() ? contentWorkWidth : contentWorkHeight) * moveTo.zoom;
            imageBounds.left   = -0.5f * zoomedWidth + moveTo.x;
            imageBounds.right  =  0.5f * zoomedWidth + moveTo.x;
            imageBounds.top    = -0.5f * zoomedHeight + moveTo.y;
            imageBounds.bottom =  0.5f * zoomedHeight + moveTo.y;
            
            if (debug) Log.w(TAG, "checkBoundaries() current image bounds: " + imageBounds.left + "x" + imageBounds.top + " -> " + imageBounds.right + "x" + imageBounds.bottom);
            
            // adjust offset to fit in boundaries
            RectF offsetBounds = new RectF();
            if (imageBounds.width() - getWidth() > 0) {
                offsetBounds.left   = -0.5f * (imageBounds.width() - getWidth());
                offsetBounds.right  =  0.5f * (imageBounds.width() - getWidth());
            }
            if (imageBounds.height() - getHeight() > 0) {
                offsetBounds.top    = -0.5f * (imageBounds.height() - getHeight());
                offsetBounds.bottom =  0.5f * (imageBounds.height() - getHeight());
            }
            if (imageBounds.centerX() < offsetBounds.left) { 
                moveTo.x = offsetBounds.left; 
                move = true;
            } else if (imageBounds.centerX() > offsetBounds.right) {
                moveTo.x = offsetBounds.right;
                move = true;
            } 
 
            if (imageBounds.centerY() < offsetBounds.top)   { 
                moveTo.y = offsetBounds.top;   
                move = true; 
            } else if (imageBounds.centerY() > offsetBounds.bottom)   { 
                moveTo.y = offsetBounds.bottom;   
                move = true; 
            }
        }
        
        if (move) {
            final Position finalMoveTo = moveTo;
            move(new Move() {
                Position startPos = new Position(pos);
                Position toPos = finalMoveTo;
                long durationMs = 200;
                public boolean handleMove(Position result, long currentTime, long startedAt) {
                    if (currentTime - startedAt > durationMs) {
                        result.set(toPos);
                        return false;
                    }
                    
                    double remainingProgress = 1.0 - (1.0 * (currentTime - startedAt) / durationMs);
                    result.zoom = (float) (toPos.zoom + (startPos.zoom - toPos.zoom) * remainingProgress); 
                    result.x    = (float) (toPos.x + (startPos.x - toPos.x) * remainingProgress);
                    result.y    = (float) (toPos.y + (startPos.y - toPos.y) * remainingProgress);
                    return true;
                }
            });
        }
        if (debug) Log.i(TAG, "checkBoundaries() " + (move ? "moveTo: " + moveTo : "ok"));
        
    }
    
    /** @return x in content space: zoom = 1.0 and 0x0 is center of drawable */
    private double getContentX(double viewX, Position pos) {
        return ( viewX - pos.x - 0.5 * getWidth()) / pos.zoom;
    }
    /** @return y in content space: zoom = 1.0 and 0x0 is center of drawable */
    private double getContentY(double viewY, Position pos) {
        return ( viewY - pos.y - 0.5 * getHeight() ) / pos.zoom;
    }
    /** @return x in view coordinates for given content coordinate and view position */
    private double getViewX(double contentX, Position pos) {
        return contentX * pos.zoom + 0.5 * getWidth() + pos.x;
    }
    /** @return y in view coordinates for given content coordinate and view position */
    private double getViewY(double contentY, Position pos) {
        return contentY * pos.zoom + 0.5 * getHeight() + pos.y;
    }

    private void trackLastTouch(MotionEvent event) {
        lastTouch1x = event.getX(0);
        lastTouch1y = event.getY(0);
        if (event.getPointerCount() > 1) {
            lastTouch2x = event.getX(1);
            lastTouch2y = event.getY(1);
        }
        if (debug) Log.d(TAG, "trackLastTouch() 0: " + lastTouch1x + "x" + lastTouch1y + ", 1: " + lastTouch2x + "x" + lastTouch2y);
    }
    
    @Override
    protected boolean verifyDrawable(Drawable who) {
        if (who == this.drawable) return true;
        return super.verifyDrawable(who);
    }

    public void setBitmap(Bitmap bmp) {
        setDrawable(new BitmapDrawable(bmp));
    }
    
    public void setDrawable(Drawable drawable) {
        if (this.drawable != null) {
            this.drawable.setCallback(null);
        }
        this.drawable = drawable;
        if (drawable != null) {
            this.drawable.setCallback(this);
        }
        if (drawable instanceof GIFDrawable) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        } else {
            setLayerType(defaultLayerType, null);
        }
        setContentWorkDimensions();
        invalidate();
    }
    
    public void setContentDimensions(int contentWidth, int contentHeight) {
        if (debug) Log.w(TAG, "setContentDimensions() new: " + contentWidth + "x" + contentHeight + ", old: " + this.contentWidth + "x" + this.contentHeight);
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        
        setContentWorkDimensions();
        requestLayout();
        invalidate();
    }
    
    boolean useBitmapBuffer;
    private Bitmap buffer;
    private Canvas bufferCanvas;
    private static final Paint bitmapPaint = new Paint();

    /** 
     * Big bitmaps may not fit into GL_MAX_TEXTURE_SIZE boundaries (2048x2048 for Nexus S).
     * To draw such images, buffer bitmap needs to be created
     * TODO: understand it automatically
     */
    public void setUseBitmapBuffer(boolean useBitmapBuffer) {
        this.useBitmapBuffer = useBitmapBuffer;
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
        invalidate();
    }

    public float getZoom() {
        return pos.zoom;
    }

    /** zoom value for drawable. Default value is 1.0f when 1 contentPixel is equal to 1 viewPixel */
    public void setZoom(float zoomValue) {
        pos.zoom = zoomValue;
        invalidate();
    }
    
    public float getXOffset() {
        return pos.x;
    }
    
    /** horizontal difference between center of view and center of drawable. Default is 0. */
    public void setXOffset(float xOffset) {
        pos.x = xOffset;
        invalidate();
    }
    
    public float getYOffset() {
        return pos.y;
    }
    /** vertical difference between center of view and center of drawable. Default is 0. */
    public void setYOffset(float yOffset) {
        pos.y = yOffset;
        invalidate();
    }
    
    public void setPosition(Position pos) {
        pos.set(pos);
        invalidate();
    }
    
    /** @return copy of view's content position */
    public Position getPosition() {
        return pos.copy();
    }
    
    private Move currentMove;
    private long currentMoveStartedAt;
    
    /** Use {@link MoveTo} to animate content between 2 positions or subclass {@link Move} */
    public void move(Move move) {
        this.currentMove = move;
        this.currentMoveStartedAt = System.currentTimeMillis();
        invalidate();
    }
    
    public static abstract class Move {
        
        /** 
         * Use [ (currentTime - startedAt) / duration ] to calculate progress
         * 
         * @param result - put result values of offsets and zoom here
         * @param currentTime - System.currentTimeMillis()
         * @param startedAt   - when started  
         */
        public abstract boolean handleMove(Position result, long currentTime, long startedAt);
    }
    
    public static class Position {
        
        private float zoom = 1.0f;
        private float x    = 0.0f;
        private float y    = 0.0f;
        
        public Position() {}
        
        public Position(Position src) {
            this.zoom = src.zoom;
            this.x    = src.x;
            this.y    = src.y;
        }

        public Position(float zoom, float x, float y) {
            this.zoom = zoom;
            this.x = x;
            this.y = y;
        }
        
        public Position copy() {
            return new Position(zoom, x, y);
        }
        
        public void set(Position from) {
            this.zoom   = from.zoom;
            this.x      = from.x;
            this.y      = from.y;
        }
        
        public Position xOffset(float x) {
            this.x = x;
            return this;
        }
        
        public Position yOffset(float y) {
            this.y = y;
            return this;
        }
        
        public Position zoom(float zoom) {
            this.zoom = zoom;
            return this;
        }
        
        public float getX() {
            return x;
        }
        
        public float getY() {
            return y;
        }
        
        public float getZoom() {
            return zoom;
        }
        
        @Override
        public String toString() {
            return String.format("Zoom: %.2f at: %.1fx%.1f", zoom, x, y);
        }
        
    }
    
    public static class MoveTo extends Move {
        
        private Position from;
        private Position to;
        private long duration;
        private TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();
        
        public boolean handleMove(Position result, long currentTime, long startedAt) {
            
            float progressTime = 1.0f * (currentTime - startedAt) / duration;
            if (progressTime > 1.0f) progressTime = 1.0f;
            float progress = interpolator.getInterpolation(progressTime);
            
            float zoom = from.getZoom() + (to.getZoom() - from.getZoom()) * progress;
            float x = from.getX() + (to.getX() - from.getX()) * progress;
            float y = from.getY() + (to.getY() - from.getY()) * progress;
            
            if (debug) Log.w(TAG, "handleMove() new: " + x + ", " + y + ", time: " + (currentTime - startedAt) 
                    + ", progress: " + progress + ", " + progressTime + ", from: " + from + " -> " + to);
            
            result.xOffset(x).yOffset(y).zoom(zoom);
            
            return currentTime - startedAt < duration;
        }
        
        public MoveTo from(Position from) {
            this.from = from;
            return this;
        }
        
        public MoveTo to(Position to) {
            this.to = to;
            return this;
        }
        
        public MoveTo setDuration(long duration) {
            this.duration = duration;
            return this;
        }
        
        public MoveTo setInterpolator(TimeInterpolator interpolator) {
            this.interpolator = interpolator;
            return this;
        }
    }
    
}
