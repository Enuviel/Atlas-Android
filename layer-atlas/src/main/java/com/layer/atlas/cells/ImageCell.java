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
package com.layer.atlas.cells;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.layer.atlas.Atlas;
import com.layer.atlas.Atlas.ImageLoader;
import com.layer.atlas.Atlas.ImageLoader.ImageSpec;
import com.layer.atlas.Atlas.MessagePartStreamProvider;
import com.layer.atlas.Atlas.Tools;
import com.layer.atlas.AtlasImageView;
import com.layer.atlas.AtlasMessagesList;
import com.layer.atlas.AtlasMessagesList.Cell;
import com.layer.atlas.AtlasProgressView;
import com.layer.atlas.R;
import com.layer.atlas.ShapedFrameLayout;
import com.layer.sdk.listeners.LayerProgressListener;
import com.layer.sdk.messaging.MessagePart;

/**
 * 
 * @author Oleg Orlov
 * @since  13 May 2015
 */
public class ImageCell extends Cell implements LayerProgressListener, ImageLoader.ImageLoadListener {
    private static final String TAG = ImageCell.class.getSimpleName();
    private static final boolean debug = false;
    
    public final MessagePart previewPart;
    public final MessagePart fullPart;
    public final int declaredWidth;
    public final int declaredHeight;
    public final int orientation;
    protected ImageLoader.ImageSpec imageSpec;
    
    /** if more than 0 - download is in progress */
    volatile long downloadProgressBytes = -1;
    
    final AtlasMessagesList messagesList;
    
    /** Raw bitmap: as needed (0deg). Dimensions: respect bitmap: cell: 3264x2448@0, bitmap: 1632x1224 */
    public static final int ORIENTATION_NORMAL = 0;
    /** Raw bitmap: upside-down (180deg).   Dimensions: flipped: cell: 2448x3264@1, bitmap: 1632x1224 */
    public static final int ORIENTATION_1_CW_180 = 1;
    /** Raw bitmap: rotated (90 clockwise). Dimensions: flipped: cell: 2448x3264@2, bitmap: 1632x1224 */
    public static final int ORIENTATION_2_CW_90 = 2;
    /** Raw bitmap: rotated (90 Counter-clockwise). Dimensions: respect. cell: 3264x2448@3, bitmap: 1632x1224 */
    public static final int ORIENTATION_3_CCW_90 = 3;
    
    public ImageCell(MessagePart fullImagePart, AtlasMessagesList messagesList) {
        this(fullImagePart, null, 0, 0, 0, messagesList);
    }
    
    public ImageCell(MessagePart fullImagePart, MessagePart previewImagePart, int width, int height, int orientation, AtlasMessagesList messagesList) {
        super(fullImagePart);
        this.fullPart = fullImagePart;
        this.previewPart = previewImagePart;
        this.declaredWidth = width;
        this.declaredHeight = height;
        this.orientation = orientation;
        this.messagesList = messagesList;
    }
    @Override
    public View onBind(final ViewGroup cellContainer) {
        View rootView = Tools.findChildById(cellContainer, R.id.atlas_view_messages_cell_image);
        if (rootView == null) {
            rootView = LayoutInflater.from(cellContainer.getContext()).inflate(R.layout.atlas_view_messages_cell_image, cellContainer, false); 
        }
        
        boolean myMessage = messagesList.getLayerClient().getAuthenticatedUserId().equals(messagePart.getMessage().getSender().getUserId());
        
        View imageContainerMy = rootView.findViewById(R.id.atlas_view_messages_cell_image_container_my);
        View imageContainerTheir = rootView.findViewById(R.id.atlas_view_messages_cell_image_container_their);
        AtlasImageView imageViewMy = (AtlasImageView) imageContainerMy.findViewById(R.id.atlas_view_messages_cell_image_my);
        AtlasImageView imageViewTheir = (AtlasImageView) imageContainerTheir.findViewById(R.id.atlas_view_messages_cell_image_their);
        AtlasImageView imageView = myMessage ? imageViewMy : imageViewTheir;
        View imageContainer = myMessage ? imageContainerMy : imageContainerTheir;
        
        if (myMessage) {
            imageContainerMy.setVisibility(View.VISIBLE);
            imageContainerTheir.setVisibility(View.GONE);
        } else {
            imageContainerMy.setVisibility(View.GONE);
            imageContainerTheir.setVisibility(View.VISIBLE);
        }

        // understanging image's dimensions
        int imgWidth  = this.declaredWidth;
        int imgHeight = this.declaredHeight;
        if (debug) Log.w(TAG, "img.onBind() declared image: " + declaredWidth + "x" + declaredHeight);
        
        // no declared dimensions? go to imageSpec!
        if ((imgWidth == 0 || imgHeight == 0) && imageSpec != null && imageSpec.originalWidth != 0) {
            if (debug) Log.w(TAG, "img.onBind() using imgSize from spec:   " + imageSpec.originalWidth + "x" + imageSpec.originalHeight);
            imgWidth  = imageSpec.originalWidth;
            imgHeight = imageSpec.originalHeight;
        }
        
        MessagePart workingPart = getWorkingPart();
        Drawable drawable = getDrawable(workingPart);

        // still no size known? fallback to bitmap's size
        if ((imgWidth == 0 || imgHeight == 0) && drawable != null) {
            if (debug) Log.w(TAG, "img.onBind() using imgSize from drawable: " + drawable.getIntrinsicWidth() + "x" + drawable.getIntrinsicHeight());
            imgWidth  = drawable.getIntrinsicWidth();
            imgHeight = drawable.getIntrinsicHeight();
        }

        // calculate appropriate View size. If image dimensions are unknown, use default size 192dp
        int viewWidth  = (int) (imgWidth  != 0 ? imgWidth  : Tools.getPxFromDp(192, imageContainer.getContext()));
        int viewHeight = (int) (imgHeight != 0 ? imgHeight : Tools.getPxFromDp(192, imageContainer.getContext()));
        if (orientation == ImageCell.ORIENTATION_1_CW_180 || orientation == ImageCell.ORIENTATION_3_CCW_90) {
             int oldWidth = viewWidth;
             viewWidth = viewHeight;
             viewHeight = oldWidth;
        }
        
        if (debug) Log.w(TAG, "img.onBind() image: " + imgWidth + "x" + imgHeight + " into view: " + viewWidth + "x" + viewHeight + ", orientation: " + orientation
                + ", container: " + (myMessage ? "my " : "their ") + imageContainer.getWidth() + "x" + imageContainer.getHeight() 
                + ", cell: " + cellContainer.getWidth() + "x" + cellContainer.getHeight());
        
        int widthToFit;
        if (cellContainer.getWidth() != 0) {
            if (debug) Log.w(TAG, "img.onBind() widthToFit from cellContainer: " + cellContainer.getWidth());
            widthToFit = cellContainer.getWidth();
        } else {
            if (debug) Log.w(TAG, "img.onBind() widthToFit from  messagesList:  " + messagesList.getWidth());
            widthToFit = messagesList.getWidth();
        }
        
        if (viewWidth > widthToFit) {
            int oldWidth  = viewWidth;
            viewHeight = (int) (1.0 * viewHeight * widthToFit / viewWidth);
            viewWidth = widthToFit;
            if (debug) Log.w(TAG, "img.onBind() viewWidth > widthToFit: " + oldWidth + " > " + widthToFit + " -> view: " + viewWidth + "x" + viewHeight);
        }
        
        if (viewHeight > messagesList.getHeight() && messagesList.getHeight() > 0) {
            int oldHeight = viewHeight;
            viewWidth = (int)(1.0 * viewWidth * messagesList.getHeight() / viewHeight);
            viewHeight = messagesList.getHeight();
            if (debug) Log.w(TAG, "img.onBind() viewHeight > messagesList.height: " + oldHeight + " > " + messagesList.getHeight() + " -> view: " + viewWidth + "x" + viewHeight);
        }
        
        if (debug) Log.w(TAG, "img.onBind() image: " + imgWidth + "x" + imgHeight + " set"
                + "  view: " + viewWidth + "x" + viewHeight + ", h/w: " + (1.0f * viewHeight / viewWidth) 
                );
        
        imageView.setContentDimensions(viewWidth, viewHeight);
        imageView.orientation = orientation;

        if (drawable != null) {
            imageView.setDrawable(drawable);
        } else {
            imageView.setDrawable(Tools.EMPTY_DRAWABLE);
            if ( ! workingPart.isContentReady() && downloadProgressBytes == -1) {
                workingPart.download(this);
            }
        }
        
        AtlasProgressView progressMy = (AtlasProgressView) rootView.findViewById(R.id.atlas_view_messages_cell_image_my_progress);
        AtlasProgressView progressTheir = (AtlasProgressView) rootView.findViewById(R.id.atlas_view_messages_cell_image_their_progress);
        AtlasProgressView progressView = myMessage ? progressMy : progressTheir;
        if (downloadProgressBytes > 0) {
            float progress = 1.0f * downloadProgressBytes / workingPart.getSize();
            if (debug) Log.w(TAG, "img.onBind() showing progress: " + progress);
            progressView.setVisibility(View.VISIBLE);
            progressView.setProgress(progress);
        } else {
            if (debug) Log.w(TAG, "img.onBind() no progressView. bytes: " + downloadProgressBytes);
            progressView.setVisibility(View.GONE);
        }
        
        ShapedFrameLayout cellCustom = (ShapedFrameLayout) (myMessage ? imageContainerMy : imageContainerTheir);
        // clustering
        cellCustom.setCornerRadiusDp(16, 16, 16, 16);
        if (!AtlasMessagesList.CLUSTERED_BUBBLES) return rootView;
        if (myMessage) {
            if (this.clusterHeadItemId == this.clusterItemId && !this.clusterTail) {
                cellCustom.setCornerRadiusDp(16, 16, 2, 16);
            } else if (this.clusterTail && this.clusterHeadItemId != this.clusterItemId) {
                cellCustom.setCornerRadiusDp(16, 2, 16, 16);
            } else if (this.clusterHeadItemId != this.clusterItemId && !this.clusterTail) {
                cellCustom.setCornerRadiusDp(16, 2, 2, 16);
            }
        } else {
            if (this.clusterHeadItemId == this.clusterItemId && !this.clusterTail) {
                cellCustom.setCornerRadiusDp(16, 16, 16, 2);
            } else if (this.clusterTail && this.clusterHeadItemId != this.clusterItemId) {
                cellCustom.setCornerRadiusDp(2, 16, 16, 16);
            } else if (this.clusterHeadItemId != this.clusterItemId && !this.clusterTail) {
                cellCustom.setCornerRadiusDp(2, 16, 16, 2);
            }
        }
        return rootView;
    }
    
    protected MessagePart getWorkingPart() {
        return previewPart != null ? previewPart : fullPart;
    }

    /** 
     * @return drawable to set into imageView. Assumed that it return real drawable finally 
     */
    protected Drawable getDrawable(MessagePart workingPart) {
        // TODO: calculate properly with rotation
        int requiredWidth  = messagesList.getWidth();
        int requiredHeight = messagesList.getHeight();
        
        Bitmap bmp = (Bitmap) Atlas.imageLoader.getImageFromCache(workingPart.getId());
        if (bmp != null) {
            if (debug) Log.i(TAG, "img.onBind() returned from cache! " + bmp.getWidth() + "x" + bmp.getHeight() 
                    + " " + bmp.getByteCount() + " bytes, req: " + requiredWidth + "x" + requiredHeight + " for " + workingPart.getId());
            return new BitmapDrawable(bmp);
        } else if (workingPart.isContentReady()){
            final Uri id = workingPart.getId();
            final MessagePartStreamProvider streamProvider = new MessagePartStreamProvider(workingPart);
            imageSpec = Atlas.imageLoader.requestImage(id, streamProvider, requiredWidth, requiredHeight, false, this, false);
        }
        return null;
    }

    // LayerDownloadListener (when downloading part)
    public void onProgressStart(MessagePart part, Operation operation) {
    }
    public void onProgressUpdate(MessagePart part, Operation operation, long transferredBytes) {
        MessagePart workingPart = previewPart != null ? previewPart : fullPart;
        if (debug) Log.w(TAG, "onProgressUpdate() transferred: " + transferredBytes + " of " + workingPart.getSize() + ", progress: " + (1.0f * transferredBytes / workingPart.getSize()));
        downloadProgressBytes = transferredBytes;
        messagesList.requestRefresh();
    }
    public void onProgressError(MessagePart part, Operation operation, Throwable cause) {
        downloadProgressBytes = -1;
        messagesList.requestRefresh();
    }
    public void onProgressComplete(MessagePart part, Operation operation) {
        downloadProgressBytes = -1;
        messagesList.requestRefresh();
    }
    
    @Override
    public void onImageLoaded(ImageSpec spec) {
        messagesList.requestRefresh();
    }

    @Override
    public String toString() {
        return "size: " + declaredWidth + "x" + declaredHeight + "@" + orientation + ", " + super.toString();
    }
    
}