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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import com.layer.atlas.Atlas.DownloadQueue.CompleteListener;
import com.layer.atlas.cells.GIFCell;
import com.layer.atlas.cells.GeoCell;
import com.layer.atlas.cells.ImageCell;
import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;

/**
 * @author Oleg Orlov
 * @since 12 May 2015
 */
public class Atlas {
    
    private static final String TAG = Atlas.class.getSimpleName();
    private static final boolean debug = false;

    public static final String METADATA_KEY_CONVERSATION_TITLE = "conversationName";
    
    public static final String MIME_TYPE_ATLAS_LOCATION = "location/coordinate";
    public static final String MIME_TYPE_TEXT = "text/plain";
    public static final String MIME_TYPE_IMAGE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_IMAGE_JPEG_PREVIEW = "image/jpeg+preview";
    public static final String MIME_TYPE_IMAGE_PNG = "image/png";
    public static final String MIME_TYPE_IMAGE_PNG_PREVIEW = "image/png+preview";
    public static final String MIME_TYPE_IMAGE_GIF = "image/gif";
    public static final String MIME_TYPE_IMAGE_GIF_PREVIEW = "image/gif+preview";
    public static final String MIME_TYPE_IMAGE_DIMENSIONS = "application/json+imageSize";

    public static final ImageLoader imageLoader = new ImageLoader();

    public static final Atlas.DownloadQueue downloadQueue = new DownloadQueue(2);

    public static String getInitials(Participant p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getFirstName() != null && p.getFirstName().trim().length() > 0 ? p.getFirstName().trim().charAt(0) : "");
        sb.append(p.getLastName() != null && p.getLastName().trim().length() > 0 ? p.getLastName().trim().charAt(0) : "");
        return sb.toString();
    }

    public static String getFirstNameLastInitial(Participant p) {
        StringBuilder sb = new StringBuilder();
        if (p.getFirstName() != null && p.getFirstName().trim().length() > 0) {
            sb.append(p.getFirstName().trim());
        }
        if (p.getLastName() != null && p.getLastName().trim().length() > 0) {
            sb.append(" ").append(p.getLastName().trim().charAt(0));
            sb.append(".");
        }
        return sb.toString();
    }

    public static String getFullName(Participant p) {
        StringBuilder sb = new StringBuilder();
        if (p.getFirstName() != null && p.getFirstName().trim().length() > 0) {
            sb.append(p.getFirstName().trim());
    }
        if (p.getLastName() != null && p.getLastName().trim().length() > 0) {
            sb.append(" ").append(p.getLastName().trim());
        }
        return sb.toString();
    }

    public static void setTitle(Conversation conversation, String title) {
        conversation.putMetadataAtKeyPath(Atlas.METADATA_KEY_CONVERSATION_TITLE, title);
    }

    public static String getTitle(Conversation conversation) {
        return (String) conversation.getMetadata().get(Atlas.METADATA_KEY_CONVERSATION_TITLE);
    }

    public static String getTitle(Conversation conversation, ParticipantProvider provider, String userId) {
        String conversationTitle = getTitle(conversation);
        if (conversationTitle != null && conversationTitle.trim().length() > 0) return conversationTitle.trim();

        StringBuilder sb = new StringBuilder();
        for (String participantId : conversation.getParticipants()) {
            if (participantId.equals(userId)) continue;
            Participant participant = provider.getParticipant(participantId);
            if (participant == null) continue;
            String initials = conversation.getParticipants().size() > 2 ? getFirstNameLastInitial(participant) : getFullName(participant);
            if (sb.length() > 0) sb.append(", ");
            sb.append(initials);
        }
        return sb.toString().trim();
    }

    public static Drawable imageFromUrl(String url) {
        Drawable result = new Drawable(url);
        downloadQueue.schedule(url, null, result);
        return result;
    }

    public static Drawable imageFromUrlOrFile(String url, File imageFile) {
        Drawable result;
        if (imageFile != null && imageFile.exists() && !imageFile.isDirectory()) {
            result = new Drawable(url, imageFile);
            result.requestInflate();
        } else {
            result = new Drawable(url);
            downloadQueue.schedule(url, imageFile, result);
        }
        return result;
    }

    /**
     * ImageLoader/DownloadQueue backed drawable. Use {@link #imageFromUrl(String)} or {@link #imageFromUrlOrFile(String, File)}
     */
    public static class Drawable extends android.graphics.drawable.Drawable implements DownloadQueue.CompleteListener, ImageLoader.ImageLoadListener {
        private static final String TAG = Drawable.class.getSimpleName();
        private static final boolean debug = false;
        private static final boolean debugDraw = false;
        private static final Paint debugPaintDwnld = new Paint();
        private static final Paint debugPaintInflt = new Paint();
        private static final Paint debugPaintCmplt = new Paint();
        private static final Paint debugPaintStroke = new Paint();

        static {
            debugPaintDwnld.setStyle(Style.FILL);
            debugPaintDwnld.setColor(Color.rgb(176, 190, 197));

            debugPaintInflt.setStyle(Style.FILL);
            debugPaintInflt.setColor(Color.rgb(255, 150, 0));

            debugPaintCmplt.setStyle(Style.FILL);
            debugPaintCmplt.setColor(Color.rgb(36, 155, 34));
            
            debugPaintStroke.setStyle(Style.STROKE);
            debugPaintStroke.setColor(Color.BLACK);
        }
        
        private static final Handler mainHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case 0: {
                        Drawable drawable = (Drawable) msg.obj;
                        //if (debug) Log.w(TAG, "handleMessage() callback: " + drawable.getCallback() + ", msg: " + msg);
                        if (drawable.getCallback() instanceof ImageView) {
                            ((ImageView)drawable.getCallback()).setImageState(null, true);
                        }
                        drawable.invalidateSelf();
                        break;
                    }
                }
            }
        };
        
        /** Is used to address bitmaps in cache */
        private String id;
        
        /** is used while image original width  is not available (during download and inflating)*/
        private int defaultWidth    = 1;
        /** is used while image original height is not available (during download and inflating)*/
        private int defaultHeight   = 1;
        
        File from;
        FileStreamProvider fileStreamProvider;
        ImageLoader.ImageSpec spec;
        Paint workPaint = new Paint(); 
        long inflatedAt = 0;

        private static final int FADING_MILLIS = 333;

        /** 
         * Creates {@link Drawable} ready to download and display.
         * {@link #schedule(DownloadQueue)} needs to be called to download file in order to display it.
         * <p>If file exists, use {@link Drawable#Drawable(String, File)} to avoid download
         * 
         * @param {@link #id} - must be specified for correct work 
         */
        public Drawable(String id){
            this.id = id; 
        }
        
        /** 
         * Creates {@link Drawable} from local file
         * 
         * @param imageId - is used to address objects in cache 
         */
        public Drawable(String imageId, File from) {
            if (imageId == null) throw new IllegalArgumentException("Drawable .id cannot be null");
            if (from == null) throw new IllegalArgumentException("file must not be null");
            if (from.exists() && from.isDirectory()) throw new IllegalArgumentException("Specified file is directory and cannot be used [" + from.getAbsolutePath()+ "]");

            this.id = imageId;
            this.from = from;
            this.fileStreamProvider = new FileStreamProvider(from);
        }
        
        public Drawable defaultSize(int width, int height) {
            this.defaultWidth  = width;
            this.defaultHeight = height;
            return this;
        }
        
        /** schedule download file to default location */
        public Drawable schedule(DownloadQueue queue) {
            queue.schedule(id, this);
            return this;
        }
        
        /** schedule download to specific location */
        public Drawable schedule(DownloadQueue queue, File toFile) {
            queue.schedule(id, toFile, this);
            return this;
        }

        @Override
        public void onDownloadComplete(String url, File file) {
            this.from = file;
            this.fileStreamProvider = new FileStreamProvider(from);
            requestInflate();
            
            invalidate();
        }

        @Override
        public void onImageLoaded(ImageLoader.ImageSpec spec) {
            this.spec = spec;
            long now = System.currentTimeMillis();
            if (debug) Log.w(TAG, "onImageLoaded()      " 
                                  + (inflatedAt != 0 ? "inlatedAt: [" + (now - inflatedAt) + " -> 0]" : "")  
                                  + " spec: " + spec + ", callback: " + getCallback());
            //if (debug) Log.w(TAG, "onImageLoaded()      spec: " + spec + ", callback: " + getCallback());
            if (inflatedAt == 0) this.inflatedAt = now;
            invalidate();
        }
        
        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            if (debug) Log.w(TAG, "setBounds()          " + left+ "," + top + " -> " + right+ "," + bottom + (spec == null ? (", id: " + id) : ", spec: " + spec) + ", from: " + Dt.printStackTrace(7));
            super.setBounds(left, top, right, bottom);
        }

        @Override
        public void setBounds(Rect bounds) {
            if (debug) Log.w(TAG, "setBounds()          " + bounds + (spec == null ? (", id: " + id) : ", spec: " + spec) + ", from: " + Dt.printStackTrace(7));
            super.setBounds(bounds);
        }

        @Override
        public boolean setState(int[] stateSet) {
            if (debug) Log.w(TAG, "setState()           " + Dt.toString(stateSet) + (spec == null ? (", id: " + id) : ", spec: " + spec) + ", from: " + Dt.printStackTrace(7));
            return super.setState(stateSet);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            if (debug) Log.w(TAG, "onBoundsChange()     " + bounds + (spec == null ? (", id: " + id) : ", spec: " + spec) + ", from: " + Dt.printStackTrace(7));
            super.onBoundsChange(bounds);
        }
        
        /** 
         * Return original size of image or 1 if image is not loaded
         * <p><b>NOTE:</b> if intrinsic dimensions are 0x0 - ImageView doesn't pass control to {@link Drawable#draw(Canvas)} 
         */
        @Override
        public int getIntrinsicWidth() {
            int width = 0;
            if (spec != null) width = spec.originalWidth; 
            if (width == 0) width = imageLoader.getOriginalImageWidth(id);
            if (width == 0) width = defaultWidth;   // fallback to defailt
            
            if (debug) Log.w(TAG, "getIntrinsicWidth()  " + width + (spec == null ? (", id: " + id) : ", spec: " + spec) + " from: " + Dt.printStackTrace(7));
            return width;
        }

        /** 
         * Return original size of image or 1 if image is not loaded
         * <p><b>Note:</b> if intrinsic dimensions are 0x0 - ImageView doesn't pass control to {@link Drawable#draw(Canvas)} 
         */
        @Override
        public int getIntrinsicHeight() {
            int height = 0;
            if (spec != null) height = spec.originalHeight;
            if (height == 0) height = imageLoader.getOriginalImageHeight(id);
            if (height == 0) height = defaultHeight;
            if (debug) Log.w(TAG, "getIntrinsicHeight() " + height + (spec == null ? (", id: " + id) : ", spec: " + spec)  + " from: " + Dt.printStackTrace(7));
            return height;
        }
        
        @Override
        public void draw(Canvas canvas) {
            if (debug) Log.d(TAG, "draw() id: " + id + ", callback: " + getCallback());
            Bitmap bmp = (Bitmap) imageLoader.getImageFromCache(id);
            if (bmp != null) {
                long now = System.currentTimeMillis();
                
                // bmp may be available when drawable is new (inflatedAt = 0), so set "inflated" somewhen in the past
                if (inflatedAt == 0) inflatedAt = now - (FADING_MILLIS * 2);   
                
                long age = now - inflatedAt;
                int alpha = (int) (255 * 1.0f * Math.min(age, FADING_MILLIS) / FADING_MILLIS);
                workPaint.setAlpha(alpha);
                if (debug) Log.d(TAG, "draw() age: " + age + ", alpha: " + alpha);
                canvas.drawBitmap(bmp, null, getBounds(), workPaint );
                if (        (getBounds().width() > bmp.getWidth()   && spec != null && bmp.getWidth() < spec.originalWidth)
                        ||  (getBounds().height() > bmp.getHeight() && spec != null && bmp.getHeight() < spec.originalHeight)) {
                    requestInflate();
                }
                if (age < FADING_MILLIS) invalidateSelf();
            } else {
                if (debug) Log.d(TAG, "draw() no bitmap, request id: " + id);
                
                // if we draw empty bitmap, need to animate appeareance after it is inflated
                inflatedAt = 0;
                
                requestInflate();
            }
            if (debugDraw) {
                if (bmp != null) {
                    Tools.drawRect(getBounds().left, getBounds().top, 10, 10, debugPaintCmplt, debugPaintStroke, canvas);
                } else if (fileStreamProvider == null) {
                    Tools.drawRect(getBounds().left, getBounds().top, 10, 10, debugPaintDwnld, debugPaintStroke, canvas);
                } else {
                    Tools.drawRect(getBounds().left, getBounds().top, 10, 10, debugPaintInflt, debugPaintStroke, canvas);
                }
            }
        }

        /** only when file is fetched and not scheduled yet. supports following scenarios: 
         * - drawable is new, bmp is not available non-scheduled
         * - drawable is new, bmp is not available,    scheduled
         * - drawable is new, bmp is already inflated  
         * - drawable has bmp before, but now bmp is not available, non-scheduled
         * - drawable has bmp before, but now bmp is not available,     scheduled
         */
        private void requestInflate() {
            // if already scheduled - don't schedule again. 
            // XXX: if two drawables schedule same image, one of them wouldn't be notified
            
            if (fileStreamProvider != null) {
                int requiredWidth = getBounds().width();
                int requiredHeight = getBounds().height();
                
                if (requiredWidth == defaultWidth || requiredHeight == defaultHeight)  {
                    if (debug) Log.w(TAG, "requestInflate() small boundaries: " + requiredWidth + "x" + requiredHeight + ", \t id: " + id);
                    imageLoader.requestImage(id, fileStreamProvider, this);
                } else {
                    if (debug) Log.w(TAG, "requestInflate()       boundaries: " + requiredWidth + "x" + requiredHeight + ", \t id: " + id);
                    imageLoader.requestImage(id, fileStreamProvider, requiredWidth, requiredHeight, false, this);
                }
            }
        }
        
        protected void invalidate() {
            if  (Looper.getMainLooper() == Looper.myLooper()) invalidateSelf();
            else mainHandler.obtainMessage(0, this).sendToTarget();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("id: ").append(id)
                .append(", from: ").append(from)
                .append(", spec: ").append(spec)
                .append(", inflatedAt: ").append(inflatedAt);
            return builder.toString();
        }
        
        public String getId() {
            return id;
        }
        
        public ImageLoader.ImageSpec getSpec() {
            return spec;
        }
        
        public File getFile() {
            return from;
        }

    }

    /**
     * @param imageFile   - to create a preview of
     * @param layerClient - required to create {@link MessagePart} 
     * @param tempDir     - required to store preview file until it is picked by LayerClient
     * @return MessagePart[] {previewBytes, json_with_dimensions} or null if preview cannot be built
     */
    public static MessagePart[] buildPreviewAndSize(final File imageFile, final LayerClient layerClient, File tempDir) throws IOException {
        if (imageFile == null) throw new IllegalArgumentException("imageFile cannot be null");
        if (layerClient == null) throw new IllegalArgumentException("layerClient cannot be null");
        if (tempDir == null) throw new IllegalArgumentException("tempDir cannot be null");
        if (!tempDir.exists()) throw new IllegalArgumentException("tempDir doesn't exist");
        if (!tempDir.isDirectory()) throw new IllegalArgumentException("tempDir must be a directory");
        
        // prepare preview
        BitmapFactory.Options optOriginal = new BitmapFactory.Options();
        optOriginal.inJustDecodeBounds = true;
        //BitmapFactory.decodeFile(photoFile.getAbsolutePath(), optOriginal);
        BitmapFactory.decodeStream(new FileInputStream(imageFile), null, optOriginal);
        if (debug) Log.w(TAG, "buildPreviewAndSize() original: " + optOriginal.outWidth + "x" + optOriginal.outHeight);
        int previewWidthMax = 512;
        int previewHeightMax = 512;
        int previewWidth;
        int previewHeight;
        int sampleSize;
        if (optOriginal.outWidth > optOriginal.outHeight) {
            sampleSize = optOriginal.outWidth / previewWidthMax;
            previewWidth = previewWidthMax;
            previewHeight = (int) (1.0 * previewWidth * optOriginal.outHeight / optOriginal.outWidth);
            if (debug) Log.w(TAG, "buildPreviewAndSize() sampleSize: " + sampleSize + ", orig: " + optOriginal.outWidth + "x" + optOriginal.outHeight + ", preview: " + previewWidth + "x" + previewHeight);
        } else {
            sampleSize = optOriginal.outHeight / previewHeightMax;
            previewHeight = previewHeightMax;
            previewWidth = (int) (1.0 * previewHeight * optOriginal.outWidth / optOriginal.outHeight);
            if (debug) Log.w(TAG, "buildPreviewAndSize() sampleSize: " + sampleSize + ", orig: " + optOriginal.outWidth + "x" + optOriginal.outHeight + ", preview: " + previewWidth + "x" + previewHeight);
        }
        
        BitmapFactory.Options optsPreview = new BitmapFactory.Options();
        optsPreview.inSampleSize = sampleSize;
        //Bitmap decodedBmp = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), optsPreview);
        Bitmap decodedBmp = BitmapFactory.decodeStream(new FileInputStream(imageFile), null, optsPreview);
        if (decodedBmp == null) {
            if (debug) Log.w(TAG, "buildPreviewAndSize() taking photo, but photo file cannot be decoded: " + imageFile.getPath());
            return null;
        }
        if (debug) Log.w(TAG, "buildPreviewAndSize() decoded bitmap: " + decodedBmp.getWidth() + "x" + decodedBmp.getHeight() + ", " + decodedBmp.getByteCount() + " bytes ");
        Bitmap bmp = Bitmap.createScaledBitmap(decodedBmp, previewWidth, previewHeight, false);
        if (debug) Log.w(TAG, "buildPreviewAndSize() preview bitmap: " + bmp.getWidth() + "x" + bmp.getHeight() + ", " + bmp.getByteCount() + " bytes ");
        
        String fileName = "atlasPreview" + System.currentTimeMillis() + ".jpg";
        final File previewFile = new File(tempDir, fileName); 
        FileOutputStream fos = new FileOutputStream(previewFile);
        bmp.compress(Bitmap.CompressFormat.JPEG, 50, fos);
        fos.close();
        
        FileInputStream fisPreview = new FileInputStream(previewFile) {
            public void close() throws IOException {
                super.close();
                boolean deleted = previewFile.delete();
                if (debug) Log.w(TAG, "buildPreviewAndSize() preview file is" + (!deleted ? " not" : "") + " removed: " + previewFile.getName());
            }
        };
        final MessagePart previewPart = layerClient.newMessagePart(MIME_TYPE_IMAGE_JPEG_PREVIEW, fisPreview, previewFile.length());
        
        // prepare dimensions
        JSONObject joDimensions = new JSONObject();
        try {
            joDimensions.put("width", optOriginal.outWidth);
            joDimensions.put("height", optOriginal.outHeight);
            joDimensions.put("orientation", 0);
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot create JSON Object", e);
        }
        if (debug) Log.w(TAG, "buildPreviewAndSize() dimensions: " + joDimensions);
        final MessagePart dimensionsPart = layerClient.newMessagePart(MIME_TYPE_IMAGE_DIMENSIONS, joDimensions.toString().getBytes() );
        MessagePart[] previewAndSize = new MessagePart[] {previewPart, dimensionsPart};
        return previewAndSize;
    }

    /** @return if Today: time. If Yesterday: "Yesterday", if within one week: day of week, otherwise: dateFormat.format() */
    public static String formatTimeShort(Date dateTime, DateFormat timeFormat, DateFormat dateFormat) {
    
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long todayMidnight = cal.getTimeInMillis();
        long yesterMidnight = todayMidnight - Tools.TIME_HOURS_24;
        long weekAgoMidnight = todayMidnight - Tools.TIME_HOURS_24 * 7;
        
        String timeText = null;
        if (dateTime.getTime() > todayMidnight) {
            timeText = timeFormat.format(dateTime.getTime()); 
        } else if (dateTime.getTime() > yesterMidnight) {
            timeText = "Yesterday";
        } else if (dateTime.getTime() > weekAgoMidnight){
            cal.setTime(dateTime);
            timeText = Tools.TIME_WEEKDAYS_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } else {
            timeText = dateFormat.format(dateTime);
        }
        return timeText;
    }

    /** Today, Yesterday, Weekday or Weekday + date */
    public static String formatTimeDay(Date sentAt) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long todayMidnight = cal.getTimeInMillis();
        long yesterMidnight = todayMidnight - Tools.TIME_HOURS_24;
        long weekAgoMidnight = todayMidnight - Tools.TIME_HOURS_24 * 7;
        
        String timeBarDayText = null;
        if (sentAt.getTime() > todayMidnight) {
            timeBarDayText = "Today"; 
        } else if (sentAt.getTime() > yesterMidnight) {
            timeBarDayText = "Yesterday";
        } else if (sentAt.getTime() > weekAgoMidnight) {
            cal.setTime(sentAt);
            timeBarDayText = Tools.TIME_WEEKDAYS_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } else {
            timeBarDayText = Tools.sdfDayOfWeek.format(sentAt);
        }
        return timeBarDayText;
    }

    public static final class Tools {
        /** Millis in 24 Hours */
        public static final int TIME_HOURS_24 = 24 * 60 * 60 * 1000;
        // TODO: localization required to all time based constants below
        public static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm a"); 
        public static final SimpleDateFormat sdfDayOfWeek = new SimpleDateFormat("EEE, LLL dd,");
        /** Ensure you decrease value returned by Calendar.get(Calendar.DAY_OF_WEEK) by 1. Calendar's days starts from 1. */
        public static final String[] TIME_WEEKDAYS_NAMES = new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        public static final BitmapDrawable EMPTY_DRAWABLE = new BitmapDrawable(Bitmap.createBitmap(new int[] { Color.TRANSPARENT }, 1, 1, Bitmap.Config.ALPHA_8));
        
        public static String toString(Message msg) {
            StringBuilder sb = new StringBuilder();
            for (MessagePart mp : msg.getMessageParts()) {
                if (MIME_TYPE_TEXT.equals(mp.getMimeType())) {
                    sb.append(new String(mp.getData()));
                } else if (MIME_TYPE_ATLAS_LOCATION.equals(mp.getMimeType())){
                    sb.append("Attachment: Location");
                } else {
                    sb.append("Attachment: Image");
                    break;
                }
            }
            return sb.toString();
        }

        public static String toString(MotionEvent event) {
            StringBuilder sb = new StringBuilder();
            
            sb.append("action: ");
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN    : sb.append("DOWN"); break; 
                case MotionEvent.ACTION_UP      : sb.append("UP  "); break; 
                case MotionEvent.ACTION_MOVE    : sb.append("MOVE"); break; 
                case MotionEvent.ACTION_CANCEL  : sb.append("CANCEL"); break; 
                case MotionEvent.ACTION_SCROLL  : sb.append("SCROLL"); break; 
                case MotionEvent.ACTION_POINTER_UP     : {
                    sb.append("ACTION_POINTER_UP"); 
                    final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    sb.append(" pointer: ").append(pointerIndex);
                    break; 
                }
                case MotionEvent.ACTION_POINTER_DOWN   : {
                    sb.append("ACTION_POINTER_DOWN");  
                    final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    sb.append(" pointer: ").append(pointerIndex);
                    break; 
                }
                default                         : sb.append(event.getAction()); break; 
            }
            sb.append(", pts: [");
            for (int i = 0; i < event.getPointerCount(); i++) {
                sb.append(i > 0 ? ", ":"");
                sb.append(i).append(": ").append(String.format("%.1fx%.1f", event.getX(i), event.getY(i)));
            }
            sb.append("]");
            return sb.toString();
        }

        public static float[] getRoundRectRadii(float[] cornerRadiusDp, final DisplayMetrics displayMetrics) {
            float[] result = new float[8];
            return getRoundRectRadii(cornerRadiusDp, displayMetrics, result);
        }

        public static float[] getRoundRectRadii(float[] cornerRadiusDp, final DisplayMetrics displayMetrics, float[] result) {
            if (result.length < cornerRadiusDp.length * 2) throw new IllegalArgumentException("result[] is shorter than required. result: " + result.length + ", required: " + cornerRadiusDp.length * 2);
            for (int i = 0; i < cornerRadiusDp.length; i++) {
                result[i * 2] = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cornerRadiusDp[i], displayMetrics);
                result[i * 2 + 1] = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cornerRadiusDp[i], displayMetrics);
            }
            return result;
        }
        
        public static float getPxFromDp(float dp, Context context) {
            return getPxFromDp(dp, context.getResources().getDisplayMetrics());
        }
        
        public static float getPxFromDp(float dp, DisplayMetrics displayMetrics) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics);
        }
        
        public static View findChildById(ViewGroup group, int id) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getId() == id) return child;
            }
            return null;
        }
        
        public static void closeQuietly(InputStream stream) {
            if (stream == null) return;
            try {
                stream.close();
            } catch (Throwable ignoredQueitly) {}
        }
        
        public static void closeQuietly(OutputStream stream) {
            if (stream == null) return;
            try {
                stream.close();
            } catch (Throwable ignoredQueitly) {}
        }

        /**
         * @return number of copied bytes
         */
        public static int streamCopyAndClose(InputStream from, OutputStream to) throws IOException {
            int totalBytes = streamCopy(from, to);
            from.close();
            to.close();
            return totalBytes;
        }

        /**
         * @return number of copied bytes
         */
        public static int streamCopy(InputStream from, OutputStream to) throws IOException {
            byte[] buffer = new byte[65536];
            int bytesRead = 0;
            int totalBytes = 0;
            for (; (bytesRead = from.read(buffer)) != -1; totalBytes += bytesRead) {
                to.write(buffer, 0, bytesRead);
            }
            return totalBytes;
        }

        public static String toStringSpec(int measureSpec) {
            switch (MeasureSpec.getMode(measureSpec)) {
                case MeasureSpec.AT_MOST : return "" + MeasureSpec.getSize(measureSpec) + ".A";  
                case MeasureSpec.EXACTLY : return "" + MeasureSpec.getSize(measureSpec) + ".E";
                default                  : return "" + MeasureSpec.getSize(measureSpec) + ".U";
            }
        }
        
        public static String toStringSpec(int widthSpec, int heightSpec) {
            return toStringSpec(widthSpec) + "|" + toStringSpec(heightSpec);
        }
        
        public static final String HTTP_GET = "GET";
        public static final String HTTP_POST = "POST";
        
        public static boolean downloadHttpToFile(String url, File file, SSLSocketFactory sslFactory) {
            return downloadHttpToFile(url, file, HTTP_GET, null, sslFactory); 
        }
        
        public static boolean downloadHttpToFile(String url, File file, String method, byte[] body, SSLSocketFactory sslFactory) {
            if (url == null) Log.e(TAG, "downloadHttpToFile() url is null, file: " + file);
            
            int timeout = 8000;
            
            HttpURLConnection httpConn = null;
            try {
                URL uRL = new URL(url);
                
                if (url.startsWith("https://")) {
                    httpConn = (HttpsURLConnection) uRL.openConnection();
                    ((HttpsURLConnection) httpConn).setSSLSocketFactory(sslFactory);                    
                } else {
                    httpConn = (HttpURLConnection) uRL.openConnection();
                }
                httpConn.setConnectTimeout(timeout);
                httpConn.setReadTimeout(timeout);
                httpConn.setRequestMethod(method);
                httpConn.setDoOutput(true);
                if (HTTP_POST.equals(method) && body != null) {
                    httpConn.setDoInput(true);
                    OutputStream os = httpConn.getOutputStream();
                    os.write(body);
                    os.close();
                }

                int responseCode = httpConn.getResponseCode();
                if (responseCode == 301) {
                    String location = httpConn.getHeaderField("Location");
                    Log.e(TAG, "Redirect [" + location + "]" + " from url: [" + url + "]");
                }
                if (responseCode >= 300 || responseCode < 200) {
                    Log.e(TAG, "Expected status 200, but got " + responseCode + ", message: " + httpConn.getResponseMessage() + ", url: [" + url + "]");
                    return false;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream(httpConn.getInputStream().available());
                
            } catch (IOException e) {
                Log.e(TAG, "downloadToFile() cannot execute http request, url: [" + url + "]", e);
                return false;
            }
            
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Could not create folders, url: [" + url + "] dir: " + dir.getAbsolutePath());
                return false;
            }
            
            File tempFile = new File(file.getAbsolutePath() + ".download");
            
            try {
                streamCopyAndClose(httpConn.getInputStream(), new FileOutputStream(tempFile, false));
            } catch (IOException e) {
                if (debug) Log.e(TAG, "downloadToFile() cannot extract content from http response for [" + url + "]", e);
            }
        
            if (tempFile.length() != httpConn.getContentLength()) {
                tempFile.delete();
                Log.e(TAG, "downloadToFile() File size mismatch for [" + url + "] "
                         + " expected: " + httpConn.getContentLength() 
                         + " actual: " + tempFile.length()
                         + " path: " + tempFile.getAbsolutePath());
                return false;
            }
            
            // last step
            if (tempFile.renameTo(file)) {
                if (debug) Log.w(TAG, "downloadToFile() Successfully downloaded file: " + file.getAbsolutePath());
                return true;
            } else {
                Log.e(TAG, "downloadToFile() Could not rename temp file: " + tempFile.getAbsolutePath() + " to: " + file.getAbsolutePath());
                return false;
            }
            
        }

        public static boolean downloadHttpToFile(String url, File file) {
            if (url == null) Log.e(TAG, "downloadHttpToFile() url is null, file: " + file);
            HttpGet get = new HttpGet(url);
            HttpResponse response;
            try {
                DefaultHttpClient httpClient = new DefaultHttpClient();
                response = httpClient.execute(get);
                if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode()) {
                    Log.e(TAG, "Expected status 200, but got " + response.getStatusLine().getStatusCode() + ", url: [" + url + "]");
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "downloadToFile() cannot execute http request, url: [" + url + "]", e);
                return false;
            }
        
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Could not create folders, url: [" + url + "] dir: " + dir.getAbsolutePath());
                return false;
            }
            
            File tempFile = new File(file.getAbsolutePath() + ".download");
            
            try {
                streamCopyAndClose(response.getEntity().getContent(), new FileOutputStream(tempFile, false));
                response.getEntity().consumeContent();
            } catch (IOException e) {
                if (debug) Log.e(TAG, "downloadToFile() cannot extract content from http response for [" + url + "]", e);
            }
        
            if (tempFile.length() != response.getEntity().getContentLength()) {
                tempFile.delete();
                Log.e(TAG, "downloadToFile() File size mismatch for [" + url + "] "
                         + " expected: " + response.getEntity().getContentLength() 
                         + " actual: " + tempFile.length()
                         + " path: " + tempFile.getAbsolutePath());
                return false;
            }
            
            // last step
            if (tempFile.renameTo(file)) {
                if (debug) Log.w(TAG, "downloadToFile() Successfully downloaded file: " + file.getAbsolutePath());
                return true;
            } else {
                Log.e(TAG, "downloadToFile() Could not rename temp file: " + tempFile.getAbsolutePath() + " to: " + file.getAbsolutePath());
                return false;
            }
            
        }
        
        /** 
         * @param dumpPathPrefix - final path is constructed as <code>dumpPathPrefix + path from partId</code> 
         */
        public static void dumpPart(MessagePart part, String dumpPathPrefix) {
            try {
                String path = dumpPathPrefix + escapePath(part.getId().toString());
                if (debug) Log.w(TAG, "onProgressComplete() dumping part into " + path + ", " + part.getMimeType() + ", id: " + part.getId());
                Tools.streamCopyAndClose(part.getDataStream(), new FileOutputStream(path));
            } catch (Exception e) {
                Log.e(TAG, "onProgressComplete() cannot dump part: " + part.getMimeType() + ", id: " + part.getId(), e);
            }
        }
        
        /** escape characters of part.id if they are invalid for filePath */
        public static String escapePath(String partId) {
            return partId.replaceAll("[:/\\+]", "_");
        }

        /** draws lines between opposite corners of provided rect */
        public static void drawX(float left, float top, float right, float bottom, Paint paint, Canvas canvas) {
            canvas.drawLine(left, top, right, bottom, paint);
            canvas.drawLine(left, bottom, right, top, paint);
        }
        /** @see #drawX(float, float, float, float, Paint, Canvas)*/
        public static void drawX(Rect rect, Paint paint, Canvas canvas) {
            drawX(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }
        /** @see #drawX(float, float, float, float, Paint, Canvas)*/
        public static void drawX(RectF rect, Paint paint, Canvas canvas) {
            drawX(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }
        
        public static void drawPlus(float left, float top, float right, float bottom, Paint paint, Canvas canvas) {
            canvas.drawLine(0.5f * (left + right), top, 0.5f * (left + right), bottom, paint);
            canvas.drawLine(left, 0.5f * (top + bottom),  right, 0.5f * (top + bottom), paint);
        }
        
        public static void drawPlus(Rect rect, Paint paint, Canvas canvas) {
            drawPlus(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }
        
        public static void drawPlus(RectF rect, Paint paint, Canvas canvas) {
            drawPlus(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }

        public static void drawPlus(float xCenter, float yCenter, Canvas canvas, Paint paint) {
            canvas.drawLine(xCenter, -10000, xCenter, 10000, paint);
            canvas.drawLine(-10000, yCenter, 10000, yCenter, paint);
        }

        public static void drawPlusCircle(float xCenter, float yCenter, float radius, Paint paint, Canvas canvas) {
            drawPlus(xCenter - 1.1f * radius, yCenter - 1.1f * radius, xCenter + 1.1f * radius, yCenter + 1.1f * radius, paint, canvas);
            canvas.drawCircle(xCenter, yCenter, radius, paint);
        }
        
        public static void drawRect(float left, float top, float width, float height, Paint p, Canvas canvas) {
            canvas.drawRect(left, top, left + width, top + height, p);
        }
        public static void drawRect(float left, float top, float width, float height, Paint p, Paint strokeP, Canvas canvas) {
            drawRect(left, top, width, height, p, canvas);
            drawRect(left, top, width, height, strokeP, canvas);
        }

        /** Window flags for translucency are available on Android 5.0+ */
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
        public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;

        /** Changes Status Bar color On Android 5.0+ devices. Do nothing on devices without translucency support */
        public static void setStatusBarColor(Window wnd, int color) {
            try {
                final Method mthd_setStatusBarColor = wnd.getClass().getMethod("setStatusBarColor", int.class);
                if (mthd_setStatusBarColor != null) {
                    mthd_setStatusBarColor.invoke(wnd, color);
                    wnd.addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    wnd.clearFlags(FLAG_TRANSLUCENT_STATUS);
                }
            } catch (Exception ignored) {}
        }

        public static Rect getViewRectOnScreen(View of, Rect to) {
            int[] locationOnScreen = new int[2];
            of.getLocationOnScreen(locationOnScreen);
            
            Rect result = to;
            
            if (to == null) result = new Rect();
            
            result.left = locationOnScreen[0];
            result.top  = locationOnScreen[1];
            result.right = locationOnScreen[0] + of.getWidth();
            result.bottom = locationOnScreen[1] + of.getHeight();
            
            return result;
        }

        public static Rect getDrawableRectOnScreen(ImageView view) {
            android.graphics.drawable.Drawable d = view.getDrawable();
            if (d == null) return null;
            
            Rect vRect = getViewRectOnScreen(view, null);
            switch (view.getScaleType()) {
                case CENTER_CROP :
                    float dW = d.getIntrinsicWidth();
                    float dH = d.getIntrinsicHeight();
                    float imgW = view.getWidth();
                    float imgH = view.getHeight();
                    
                    float dRatio = dW / dH;
                    float iRatio = imgW / imgH;
                    
                    float scale;
                    if (dRatio < iRatio) {              // view wider, use width
                        scale = 1.0f * imgW / dW;       //  scale * drawableW -> imgWidth
                    } else {                            // drawable wider, use height
                        scale = 1.0f * imgH / dH;       //  scale * drawableH -> imgHeight
                    }
                    
                    
                    int scaledW = (int) (dW * scale);
                    int scaledH = (int) (dH * scale);
                    
                    Rect dRect = new Rect();
                    dRect.left  = vRect.left  - (scaledW - vRect.width()) / 2;
                    dRect.top   = vRect.top  - (scaledH - vRect.height()) / 2;
                    dRect.right = vRect.right + (scaledW - vRect.width()) / 2;
                    dRect.bottom  = vRect.bottom  + (scaledH - vRect.height()) / 2;
                    
                    return dRect;
                    
            }
            return vRect;
        }

    }

    /**
     * Participant allows Atlas classes to display information about users, like Message senders,
     * Conversation participants, TypingIndicator users, etc.
     */
    public interface Participant {
        /**
         * Returns the first name of this Participant.
         * 
         * @return The first name of this Participant
         */
        String getFirstName();

        /**
         * Returns the last name of this Participant.
         *
         * @return The last name of this Participant
         */
        String getLastName();
        
        /**
         * Returns drawable to be used as paprticipant's avatar in Atlas Views.
         * If undefined, initials would be used instead.
         * 
         * @return drawable, or null 
         */
        android.graphics.drawable.Drawable getAvatarDrawable();
        
        public static Comparator<Participant> COMPARATOR = new FilteringComparator("");
    }

    /**
     * ParticipantProvider provides Atlas classes with Participant data.
     */
    public interface ParticipantProvider {
        /**
         * Returns a map of all Participants by their unique ID who match the provided `filter`, or
         * all Participants if `filter` is `null`.  If `result` is provided, it is operated on and
         * returned.  If `result` is `null`, a new Map is created and returned.
         *
         * @param filter - <b>null</b> or filter to apply to Participants (generally text from quick-search box)
         * @param result - <b>null</b> map to place results. If null, new instance needs to be created
         * @return result - map of all matching Participants keyed by userId
         *
         * <p>TODO: drop "return value", use not-null "result" map everywhere
         * Why: re-use user-generated Map instance may be helpfull for full-contact list, but it would be
         * modified by user's code during next getParticipants call with filter
         * </p>
         */
        Map<String, Participant> getParticipants(String filter, Map<String, Participant> result);

        /**
         * Returns the Participant with the given ID, or `null` if the participant is not yet
         * available.
         *
         * @return The Participant with the given ID, or `null` if not available.
         */
        Atlas.Participant getParticipant(String userId);
    }

    public static final class FilteringComparator implements Comparator<Atlas.Participant> {
        private final String filter;
    
        /**
         * @param filter - the less indexOf(filter) the less order of participant
         */
        public FilteringComparator(String filter) {
            this.filter = filter;
        }
    
        @Override
        public int compare(Atlas.Participant lhs, Atlas.Participant rhs) {
            int result = subCompareCaseInsensitive(lhs.getFirstName(), rhs.getFirstName());
            if (result != 0) return result;
            return subCompareCaseInsensitive(lhs.getLastName(), rhs.getLastName());
        }
    
        private int subCompareCaseInsensitive(String lhs, String rhs) {
            int left = lhs != null ? lhs.toLowerCase().indexOf(filter) : -1;
            int right = rhs != null ? rhs.toLowerCase().indexOf(filter) : -1;
    
            if (left == -1 && right == -1) return 0;
            if (left != -1 && right == -1) return -1;
            if (left == -1 && right != -1) return 1;
            if (left - right != 0) return left - right;
            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    }

    /**
     * TODO: 
     * 
     * - imageCache should accept any "Downloader" that download something with progress 
     * - imageCache should reschedule image if decoding failed
     * - imageCache should reschedule image if decoded width was cut due to OOM (-> sampleSize > 1) 
     * - maximum retries should be configurable
     * 
     */
    public static class ImageLoader {
        private static final String TAG = Atlas.ImageLoader.class.getSimpleName();
        private static final boolean debug = false;
        
        private static final int BITMAP_DECODE_RETRIES = 10;
        private static final double MEMORY_THRESHOLD = 0.7;
        
        private volatile boolean shutdownLoader = false;
        private final Thread processingThread;
        private final Object loaderMonitor = new Object();
        private final ArrayList<ImageSpec> queue = new ArrayList<ImageSpec>();
        private ImageSpec inProgress = null;
        
        /** image_id -> Bitmap | Movie */
        private LinkedHashMap<Object, ImageCacheEntry> cache = new LinkedHashMap<Object, ImageCacheEntry>(40, 1f, true) {
            private static final long serialVersionUID = 1L;
            protected boolean removeEldestEntry(Entry<Object, ImageCacheEntry> eldest) {
                // calculate available memory
                long maxMemory = Runtime.getRuntime().maxMemory();
                long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                boolean cleaningRequired = 1.0 * usedMemory / maxMemory > MEMORY_THRESHOLD; 
                
                final Object id = eldest.getKey();
                if (cleaningRequired) if (debug) Log.w(TAG, "removeEldestEntry() cleaning bitmap for: " + id + ", size: " + cache.size() + ", queue: " + queue.size());
                else                  if (debug) Log.w(TAG, "removeEldestEntry() " + " nothing, size: " + cache.size() + ", queue: " + queue.size());                    
    
                return cleaningRequired;
            }
        };
    
        public ImageLoader() {
            // launching thread
            processingThread = new Decoder("AtlasImageLoader"); 
            processingThread.start();
        }
        
        private final class Decoder extends Thread {
            public Decoder(String threadName) {
                super(threadName);
            }
            public void run() {
                if (debug) Log.w(TAG, "ImageLoader.run() started");
                while (!shutdownLoader) {
   
                    ImageSpec spec = null;
                    // search bitmap ready to inflate
                    // wait for queue
                    synchronized (loaderMonitor) {
                        while (!shutdownLoader && (spec = nextSpec()) == null) {
                            try {
                                loaderMonitor.wait();
                            } catch (InterruptedException e) {}
                        }
                        if (shutdownLoader) return;
                    }
                    
                    inProgress = spec;
                    Object bitmapOrMovie = null;
                    if (spec.gif) {
                        InputStream is = spec.inputStreamProvider.getInputStream();
                        Movie mov = Movie.decodeStream(is);
                        if (debug) Log.w(TAG, "decodeImage() decoded GIF " + mov.width() + "x" + mov.height() + ":" + mov.duration() + "ms");
                        Tools.closeQuietly(is);
                        bitmapOrMovie = mov;
                        if (mov != null) {
                            spec.originalHeight = mov.height();
                            spec.originalWidth  = mov.width();
                        }
                    } else {
                        // decode dimensions
                        long started = System.currentTimeMillis();
                        InputStream streamForBounds = spec.inputStreamProvider.getInputStream();
                        if (streamForBounds == null) { 
                            Log.e(TAG, "decodeImage() stream is null! Request cancelled. Spec: " + spec.id + ", provider: " + spec.inputStreamProvider.getClass().getSimpleName()); return; 
                        }
                        BitmapFactory.Options originalOpts = new BitmapFactory.Options();
                        originalOpts.inJustDecodeBounds = true;
                        BitmapFactory.decodeStream(streamForBounds, null, originalOpts);
                        Tools.closeQuietly(streamForBounds);
                        // update spec if width and height are unknown
                        spec.originalWidth = originalOpts.outWidth;
                        spec.originalHeight = originalOpts.outHeight;
                        
                        // if required dimensions are not defined or bigger than original - use original dimensions
                        int requiredWidth  = spec.requiredWidth  > 0 ? Math.min(spec.requiredWidth,  originalOpts.outWidth)  : originalOpts.outWidth;
                        int requiredHeight = spec.requiredHeight > 0 ? Math.min(spec.requiredHeight, originalOpts.outHeight) : originalOpts.outHeight;
                        int sampleSize = 1;
                        // Use dimension with higher quality to meet both requirements
                        float widthSampleSize  = sampleSize(originalOpts.outWidth,  requiredWidth);
                        float heightSampleSize = sampleSize(originalOpts.outHeight, requiredHeight);
                        sampleSize = (int)Math.min(widthSampleSize, heightSampleSize);
                        if (debug) Log.w(TAG, "decodeImage() sampleSize: " + sampleSize + ", original: " + spec.originalWidth + "x" + spec.originalHeight
                                + " required: " + spec.requiredWidth + "x" + spec.requiredHeight);
                        
                        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                        decodeOpts.inSampleSize = sampleSize;
                        Bitmap bmp = null;
                        InputStream streamForBitmap = spec.inputStreamProvider.getInputStream();
                        try {
                            bmp = BitmapFactory.decodeStream(streamForBitmap, null, decodeOpts);
                        } catch (OutOfMemoryError e) {
                            long requiredBytes = 4 * originalOpts.outWidth * originalOpts.outHeight / sampleSize;
                            if (debug) Log.w(TAG, "decodeImage() out of memory, need " + requiredBytes 
                                    + " bytes for " + requiredWidth + "x" + requiredHeight
                                    + " orig " + originalOpts.outWidth + "x" + originalOpts.outHeight + " ss: " + sampleSize
                                    + " images: " + cache.size() + ", remove eldest. id: " + spec.id);
                            int bytesClean = removeEldest(requiredBytes);
                            if (true) Log.w(TAG, "decodeImage()         bytes clean " + bytesClean);
                            System.gc();
                        }
                        Tools.closeQuietly(streamForBitmap);
                        if (bmp != null) {
                            if (debug) Log.d(TAG, "decodeImage() decoded " + bmp.getWidth() + "x" + bmp.getHeight() 
                                    + " " + bmp.getByteCount() + " bytes" 
                                    + " req: " + spec.requiredWidth + "x" + spec.requiredHeight 
                                    + " original: " + originalOpts.outWidth + "x" + originalOpts.outHeight 
                                    + " sampleSize: " + sampleSize
                                    + " in " +(System.currentTimeMillis() - started) + "ms from: " + spec.id);
                        } else {
                            if (debug) Log.d(TAG, "decodeImage() not decoded " + " req: " + requiredWidth + "x" + requiredHeight 
                                    + " in " +(System.currentTimeMillis() - started) + "ms from: " + spec.id);
                        }
                        bitmapOrMovie = bmp;
                    }

                    // decoded
                    synchronized (loaderMonitor) {
                        if (bitmapOrMovie != null) {
                            ImageCacheEntry imageCore = new ImageCacheEntry(bitmapOrMovie, spec.originalWidth, spec.originalHeight, spec.inputStreamProvider);
                            cache.put(spec.id, imageCore);
                            if (spec.listener != null) spec.listener.onImageLoaded(spec);
                        } else if (spec.retries < BITMAP_DECODE_RETRIES) {
                            spec.retries++;
                            queue.add(0, spec);         // schedule retry
                            loaderMonitor.notifyAll();
                        } /*else {
                            forget about this image, never put it back in queue 
                        }*/
                        inProgress = null;
                    }
   
                    if (debug) Log.w(TAG, "decodeImage()   cache: " + cache.size() + ", queue: " + queue.size() + ", id: " + spec.id);
                }
            }
        }
        
        /**
         *
         * Return maximum possible sampleSize to decode bitmap with dimensions >= minRequired
         * 
         * <p>
         * Despite {@link BitmapFactory.Options#inSampleSize} documentation, sampleSize 
         * handles properly only 2^n values. Other values are handled as nearest lower 2^n.<p> 
         * I.e. result bitmap with <br>
         * <code> 
         *      opts.sampleSize = 3 is the same as opts.sampleSize = 2<br>
         *      opts.sampleSize = 5 is the same as opts.sampleSize = 4 
         * </code>
         * 
         * @return bitmap sampleSize values [1, 2, 4, 8, .. 2^n]
         */
        private static int sampleSize(int originalDimension, int minRequiredDimension) {
            int sampleSize = 1;
            while (originalDimension / (sampleSize * 2) > minRequiredDimension) {
                sampleSize *= 2;
                if (sampleSize >= 32) break;
            }
            return sampleSize;
        }
    
        public Object getImageFromCache(Object id) {
            ImageCacheEntry imageEntry = cache.get(id);
            if (imageEntry == null) return null;
            return imageEntry.bitmapOrMovie;
        }
        
        /** @return originalImageWidth if image is in cache, 0 otherwise */
        public int getOriginalImageWidth(Object id) {
            ImageCacheEntry imageEntry = cache.get(id);
            if (imageEntry == null) return 0;
            return imageEntry.originalWidth;
        }
        
        /** @return originalImageHeight if image is in cache, 0 otherwise */
        public int getOriginalImageHeight(Object id) {
            ImageCacheEntry imageEntry = cache.get(id);
            if (imageEntry == null) return 0;
            return imageEntry.originalHeight;
        }
                
        /**
         * @return - byteCount of removed bitmap if bitmap found. <bold>-1</bold> otherwise
         */
        private int removeEldest() {
            synchronized (loaderMonitor) {
                if (cache.size() > 0) {
                    Map.Entry<Object, ImageCacheEntry> entry = cache.entrySet().iterator().next();
                    Object bmp = entry.getValue().bitmapOrMovie;
                    cache.remove(entry.getKey());
                    int releasedBytes = (bmp instanceof Bitmap) ? ((Bitmap) bmp).getByteCount() : 0; /*((Movie)bmp).byteCount(); */
                    if (debug) Log.w(TAG, "removeEldest() id: " + entry.getKey() + ", bytes: " + releasedBytes);
                    return releasedBytes;
                } else {
                    if (debug) Log.w(TAG, "removeEldest() nothing to remove...");
                    return -1;
                }
            }
        }
        
        private int removeEldest(long bytesToFree) {
            synchronized (loaderMonitor) {
                int totalClean = 0;
                while (cache.size() > 0) {
                    int clean = removeEldest();
                    if (clean < 1) return totalClean;
                    totalClean += clean;
                    if (totalClean > bytesToFree) return totalClean;
                }
                return totalClean;
            }
            
        }
                
        /**
         * @see #requestImage(Object, InputStreamProvider, int, int, boolean, ImageLoadListener) 
         */
        public ImageSpec requestImage(Object id, InputStreamProvider streamProvider, ImageLoader.ImageLoadListener loadListener) {
            return requestImage(id, streamProvider, 0, 0, false, loadListener);
        }
        
        /**
         * @see #requestImage(Object, InputStreamProvider, int, int, boolean, ImageLoadListener) 
         */
        public ImageSpec requestImage(Object id, InputStreamProvider streamProvider, boolean gif, ImageLoader.ImageLoadListener loadListener) {
            return requestImage(id, streamProvider, 0, 0, gif, loadListener);
        }
        
        /** 
         * Most recently requested images would be inflated first to provide the quickest response 
         * (i.e. if user scroll 100 images back and force, the most important one is that he stopped at, 
         * and it is the last requested in general scenario)
         * 
         * @param id                - something you will use to get image from cache later
         * @param streamProvider    - something that provides raw bytes. See {@link Atlas.FileStreamProvider} or {@link Atlas.MessagePartStreamProvider}
         * @param requiredWidth     - 
         * @param requiredHeight    - provide image dimensions you need to save memory if original dimensions are bigger
         * @param gif               - android.graphics.Movie would be decoded instead of Bitmap. <b>Warning!</b> {@link Atlas.MessagePartBufferedStreamProvider} must be used 
         * @param loadListener      - something you can use to be notified when image is loaded
         */
        public ImageSpec requestImage(Object id, InputStreamProvider streamProvider, int requiredWidth, int requiredHeight, boolean gif, ImageLoader.ImageLoadListener loadListener) {
            ImageSpec spec = null;
            synchronized (loaderMonitor) {
                for (int i = 0; i < queue.size(); i++) {        // remove from deep deep blue 
                    if (queue.get(i).id.equals(id)) {
                        spec = queue.remove(i);
                        if (debug) Log.w(TAG, "requestImage() found scheduled: " + spec + ", from: " + Dt.printStackTrace());
                        break;
                    }
                }
                if (spec == null) {
                    spec = new ImageSpec();
                    spec.id = id;
                    spec.inputStreamProvider = streamProvider;
                    spec.requiredHeight = requiredHeight;
                    spec.requiredWidth = requiredWidth;
                    spec.listener = loadListener;
                    spec.gif = gif;
                }
                // check something we have in memory for such id
                ImageCacheEntry imageEntry = cache.get(id);
                if (imageEntry != null && imageEntry.inputStreamProvider.equals(streamProvider)) {
                    if (debug) Log.w(TAG, "requestImage() wow, we already inflated one: [" + imageEntry.originalWidth + "x" + imageEntry.originalHeight + "] put it in spec");
                    spec.originalWidth = imageEntry.originalWidth;
                    spec.originalHeight = imageEntry.originalHeight;
                }
                queue.add(0, spec);                             // and put it to the surface in front of all 
                loaderMonitor.notifyAll();
            }
            if (debug) Log.w(TAG, "requestBitmap() cache: " + cache.size() + ", queue: " + queue.size() + ", id: " + id + ", reqs: " + requiredWidth + "x" + requiredHeight);
            return spec;
        }
        
        /** pick first spec in queue that has inputstream ready */
        private ImageSpec nextSpec() {
            synchronized (loaderMonitor) {
                // picking from queue
                for (int i = 0; i < queue.size(); i++) {
                    ImageSpec imageSpec = queue.get(i);
                    if (imageSpec.inputStreamProvider.ready()) { // ready to inflate
                        return queue.remove(i);
                    }
                }
                return null;
            }
        }
        
        /** @return imageSpec if image is scheduled, null otherwise */
        public ImageSpec getScheduled(Object id) { 
            synchronized (loaderMonitor) {
                if (inProgress != null && inProgress.id.equals(id)) return inProgress;
                // picking from queue
                for (int i = 0; i < queue.size(); i++) {
                    ImageSpec imageSpec = queue.get(i);
                    if (imageSpec.id.equals(id)) { // ready to inflate
                        return imageSpec;
                    }
                }
                return null;
            }
        }
        
        /** 
         * a) contains link to actual image, and is used as cache entry<br> 
         * b) contains image's original dimensions. Allows to return filled ImageSpec before it is inflated 
         */
        private static class ImageCacheEntry {
            public final int originalWidth;
            public final int originalHeight;
            public final InputStreamProvider inputStreamProvider;
            public final Object bitmapOrMovie;
            
            public ImageCacheEntry(Object bitmapOrMovie, int originalWidth, int originalHeight, InputStreamProvider inputStreamProvider) {
                this.originalWidth = originalWidth;
                this.originalHeight = originalHeight;
                this.inputStreamProvider = inputStreamProvider;
                this.bitmapOrMovie = bitmapOrMovie;
            }
        }

        /**
         * Everything you need to know about image even if it is not in memory 
         */
        public static class ImageSpec {
            public Object id;
            public InputStreamProvider inputStreamProvider;
            public int requiredWidth;
            public int requiredHeight;
            public int originalWidth;
            public int originalHeight;
            public boolean gif;
            public int downloadProgress;
            public int retries = 0;
            public ImageLoader.ImageLoadListener listener;
            
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append(" .o[").append(originalWidth).append("x").append(originalHeight).append("]");
                if (requiredWidth != 0 || requiredHeight != 0) sb.append(", .r[").append(requiredWidth).append("x").append(requiredHeight).append("]");
                sb.append(" .id: ").append(id);
                sb.append(gif ? ", gif" : "");
                sb.append(", progress: ").append(downloadProgress);
                return sb.toString();
            }
            
        }

        public interface ImageLoadListener {
            public void onImageLoaded(ImageSpec spec);
        }
        
        public static abstract class InputStreamProvider {
            public abstract InputStream getInputStream();
            public abstract boolean ready();
        }
    }

    public static class DownloadQueue {
        private static final String TAG = DownloadQueue.class.getSimpleName();
        
        private final Object queueMonitor = new Object();
        private final LinkedList<Entry> queue = new LinkedList<Atlas.DownloadQueue.Entry>();
        private final HashMap<String, Entry> scheduledEntries = new HashMap<String, Atlas.DownloadQueue.Entry>();
        private final HashMap<String, Entry> inProgress = new HashMap<String, Atlas.DownloadQueue.Entry>();
        private Thread[] workers;
        
        private SSLSocketFactory sslSocketFactory = null;
        
        public DownloadQueue() {
            this(1);
        }
        public DownloadQueue(int workers) {
            this.workers = new Thread[workers];
            for (int i = 0; i < workers; i++) {
                Thread workingThread = new Thread(new Worker());
                workingThread.setDaemon(true);
                workingThread.setName(i == 0 ? "Atlas-HttpLoader" : ("Atlas-HttpLoader-" + i)); 
                workingThread.start();
            }
        }
        
        /** 
         * {@link #schedule(String, File, CompleteListener)} with <code>File == null</code> 
         */
        public void schedule(String url, CompleteListener onComplete) {
            schedule(url, null, onComplete);
        }
        
        /** 
         * {@link #schedule(String, File, CompleteListener, first)} with <code>first == false</code> 
         */
        public void schedule(String url, File toFile, CompleteListener onComplete) {
            schedule(url, toFile, onComplete, false);
        }
        
        /**
         * Schedule download of content from specified url to file
         * 
         * @param toFile - if <b>null</b> queue will create temp file and pass it to {@link CompleteListener#onDownloadComplete(String, File)}
         * @param first  - add in the beginning of the queue 
         */
        public void schedule(String url, File toFile, CompleteListener onComplete, boolean first) {
            if (debug) Log.d(TAG, "schedule() url: " + url + " toFile: " + toFile + " onComplete: " + onComplete);
            if (url == null || url.isEmpty()) throw new IllegalArgumentException("url must be defined: [" + url + "], file: " + toFile + ", onComplete: " + onComplete);
            
            // if url and destination file both are similar to something scheduled - just attach another listener
            // otherwise schedule to download
            synchronized (queueMonitor) {
                Entry scheduled = scheduled(url);
                if (scheduled != null && isSame(scheduled.file, toFile)) {
                    if (onComplete != null) {
                        scheduled.completeListeners.add(onComplete);
                    }
                    if (first && inProgress(url) == null && queue.getFirst() != scheduled) {
                        queue.remove(scheduled);
                        queue.addFirst(scheduled);
                        queueMonitor.notifyAll();
                    }
                } else {
                    Entry toSchedule = new Entry(url, toFile, onComplete);
                    if (first) {
                        queue.addFirst(toSchedule);
                    } else {
                        queue.add(toSchedule);
                    }
                    scheduledEntries.put(toSchedule.url, toSchedule);
                    queueMonitor.notifyAll();
                }
            }
        }
        
        /** 
         * Picks first available entry from queue and fetch it.
         * In progress entries could be found in {@link #inProgress} set
         */
        private final class Worker implements Runnable {
            public void run() {
                while (true) {
                    Entry next = null;
                    synchronized (queueMonitor) {
                        while (queue.size() == 0) {
                            try {
                                queueMonitor.wait();
                            } catch (InterruptedException ignored) {}
                        }
                        next = queue.removeFirst();
                        scheduledEntries.remove(next.url);
                        // onStart
                        inProgress.put(next.url, next);
                    }
                    try {
                        File downloadTo = next.file;
                        if (downloadTo == null) {
                            downloadTo = File.createTempFile(String.valueOf(System.currentTimeMillis()), ".tmp");
                            next.file = downloadTo;
                        }
                        
                        boolean downloaded = false;
                        
                        if (sslSocketFactory != null) {
                            downloaded = Tools.downloadHttpToFile(next.url, downloadTo, sslSocketFactory);
                        } else {
                            downloaded = Tools.downloadHttpToFile(next.url, downloadTo);
                        }

                        if (downloaded) {
                            for (CompleteListener onComplete : next.completeListeners) {
                                onComplete.onDownloadComplete(next.url, downloadTo);
                            }
                        };
                    } catch (Throwable e) {
                        Log.e(TAG, "onComplete() thrown an exception for: " + next.url, e);
                    }
                    // onComplete
                    synchronized (queue) {
                        inProgress.remove(next.url);
                    }
                }
            }
        }
        
        /** @return true if inProgress or scheduled */
        private Entry scheduled(String url) {
            synchronized (queueMonitor) {
                Entry entry = scheduledEntries.get(url);
                if (entry != null) return entry;
                entry = inProgress.get(url);
                return entry;
            }
        }
        
        /** @return true if inProgress or scheduled */
        private Entry inProgress(String url) {
            synchronized (queueMonitor) {
                Entry entry = inProgress.get(url);
                return entry;
            }
        }
        
        public void setSSLSocketFactory(SSLSocketFactory sslFactory) {
            synchronized (queueMonitor) {
                this.sslSocketFactory = sslFactory;
            }
        }
        
        private static boolean isSame(Object left, Object right) {
            if (left == right) return true;
            if (left == null && right == null) return true;
            if (left != null && right != null && left.equals(right)) return true;
            return false;
        }
        
        private static class Entry {
            String url;
            File file;
            ArrayList<CompleteListener> completeListeners = new ArrayList<Atlas.DownloadQueue.CompleteListener>(3);
            /** @param file - if null DownloadQueue will create tempFile using {@link File#createTempFile(String, String)} }*/
            public Entry(String url, File file, CompleteListener listener) {
                if (url == null) throw new IllegalArgumentException("url cannot be null");
                this.url = url;
                this.file = file;
                this.completeListeners.add(listener);
            }
        }
        
        public interface CompleteListener {
            public void onDownloadComplete(String url, File file);
        }
    }

    public static class MessagePartStreamProvider extends ImageLoader.InputStreamProvider {
        public final MessagePart part;
        public MessagePartStreamProvider(MessagePart part) {
            if (part == null) throw new IllegalStateException("MessagePart cannot be null");
            this.part = part;
        }
        public InputStream getInputStream() {
            return part.getDataStream();
        }
        public boolean ready() {
            return part.isContentReady();
        }
    }
    
    /** 
     * Provides BufferedInputStream on top of messagePart.dataStream, with 16k buffer 
     * and mark set to 0 with 16k read limit. <p>
     * 
     * Used for GIF purposes, because it calls <code>.reset()</code> stream during execution
     */
    public static class MessagePartBufferedStreamProvider extends ImageLoader.InputStreamProvider {
        public final MessagePart part;
        public MessagePartBufferedStreamProvider(MessagePart part) {
            if (part == null) throw new IllegalStateException("MessagePart cannot be null");
            this.part = part;
        }
        public InputStream getInputStream() {
            BufferedInputStream stream = new BufferedInputStream(part.getDataStream(), 16 * 1024);
            stream.mark(16 * 1024);
            return stream;
        }
        public boolean ready() {
            return part.isContentReady();
        }
    }

    public static class FileStreamProvider extends ImageLoader.InputStreamProvider {
        final File file;
        public FileStreamProvider(File file) {
            if (file == null) throw new IllegalStateException("File cannot be null");
            if (!file.exists()) throw new IllegalStateException("File must exist!");
            this.file = file;
        }
        public InputStream getInputStream() {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                Log.e(ImageLoader.TAG, "FileStreamProvider.getStream() cannot open file. file: " + file, e);
                return null;
            }
        }
        public boolean ready() {
            if (ImageLoader.debug) Log.w(ImageLoader.TAG, "ready() FileStreamProvider, file ready: " + file.getAbsolutePath());
            return true;
        }
        public boolean equals(Object o) {
            return file.equals(o);
        }
        public int hashCode() {
            return file.hashCode();
        }
    }

    /** 
     * Basic {@link AtlasMessagesList.CellFactory} supports mime-types
     * <li> {@link Atlas#MIME_TYPE_TEXT}
     * <li> {@link Atlas#MIME_TYPE_ATLAS_LOCATION}
     * <li> {@link Atlas#MIME_TYPE_IMAGE_JPEG}
     * <li> {@link Atlas#MIME_TYPE_IMAGE_GIF}
     * <li> {@link Atlas#MIME_TYPE_IMAGE_PNG}
     * ... including 3-part images with preview and dimensions
     */
    public static class DefaultCellFactory extends AtlasMessagesList.CellFactory {
        public final AtlasMessagesList messagesList;
        
        public DefaultCellFactory(AtlasMessagesList messagesList) {
            this.messagesList = messagesList;
        }
    
        /** 
         * Scan message and messageParts and build corresponding Cell(s). Put them into result list
         * @param msg           - message to build Cell(s) for
         * @param destination   - result list of Cells
         */
        public void buildCellForMessage(Message msg, List<AtlasMessagesList.Cell> destination) {
            final ArrayList<MessagePart> parts = new ArrayList<MessagePart>(msg.getMessageParts());
            
            for (int partNo = 0; partNo < parts.size(); partNo++ ) {
                final MessagePart part = parts.get(partNo);
                final String mimeType = part.getMimeType();
                
                if (MIME_TYPE_IMAGE_PNG.equals(mimeType) 
                        || MIME_TYPE_IMAGE_JPEG.equals(mimeType)
                        || MIME_TYPE_IMAGE_GIF.equals(mimeType)
                        ) {
                        
                    // 3 parts image support
                    if ((partNo + 2 < parts.size()) && MIME_TYPE_IMAGE_DIMENSIONS.equals(parts.get(partNo + 2).getMimeType())) {
                        String jsonDimensions = new String(parts.get(partNo + 2).getData());
                        try {
                            JSONObject jo = new JSONObject(jsonDimensions);
                            int orientation = jo.getInt("orientation");
                            int width = jo.getInt("width");
                            int height = jo.getInt("height");
                            if (orientation == 1 || orientation == 3) {
                                width = jo.getInt("height");
                                height = jo.getInt("width");
                            }
                            AtlasMessagesList.Cell imageCell = mimeType.equals(MIME_TYPE_IMAGE_GIF)  
                                    ? new GIFCell(part, parts.get(partNo + 1), width, height, orientation, messagesList)
                                    : new ImageCell(part, parts.get(partNo + 1), width, height, orientation, messagesList);
                            destination.add(imageCell);
                            if (debug) Log.w(TAG, "cellForMessage() 3-image part found at partNo: " + partNo + ", " + width + "x" + height + "@" + orientation);
                            partNo++; // skip preview
                            partNo++; // skip dimensions part
                        } catch (JSONException e) {
                            Log.e(TAG, "cellForMessage() cannot parse 3-part image", e);
                        }
                    } else {
                        // regular image
                        AtlasMessagesList.Cell cell = mimeType.equals(MIME_TYPE_IMAGE_GIF) 
                                ? new GIFCell(part, messagesList)
                                : new ImageCell(part, messagesList);
                        destination.add(cell);
                        if (debug) Log.w(TAG, "cellForMessage() single-image part found at partNo: " + partNo);
                    }
                
                } else if (MIME_TYPE_ATLAS_LOCATION.equals(part.getMimeType())){
                    destination.add(new GeoCell(part, messagesList));
                } else {
                    AtlasMessagesList.Cell cellData = new AtlasMessagesList.TextCell(part, messagesList);
                    destination.add(cellData);
                }
            }
        }
    }
    
}
