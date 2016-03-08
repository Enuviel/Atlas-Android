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

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.layer.atlas.Atlas;
import com.layer.atlas.Atlas.ImageLoader;
import com.layer.atlas.Atlas.ImageLoader.ImageSpec;
import com.layer.atlas.Atlas.Tools;
import com.layer.atlas.AtlasMessagesList;
import com.layer.atlas.AtlasMessagesList.Cell;
import com.layer.atlas.R;
import com.layer.atlas.ShapedFrameLayout;
import com.layer.sdk.messaging.MessagePart;

/**
 * @author Oleg Orlov
 * @since  13 May 2015
 */
public class GeoCell extends Cell implements Atlas.DownloadQueue.CompleteListener, ImageLoader.ImageLoadListener {
    private static final String TAG = GeoCell.class.getSimpleName();
    private static final boolean debug = false;
    
    double lon;
    double lat;
    
    ImageSpec spec;
    
    final AtlasMessagesList messagesList;

    public GeoCell(MessagePart messagePart, AtlasMessagesList messagesList) {
        super(messagePart);
        this.messagesList = messagesList;
        
        String jsonLonLat = new String(messagePart.getData());
        try {
            JSONObject json = new JSONObject(jsonLonLat);
            this.lon = json.getDouble("lon");
            this.lat = json.getDouble("lat");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Wrong geoJSON format: " + jsonLonLat, e);
        }
    }

    @Override
    public View onBind(final ViewGroup cellContainer) {
        
        ViewGroup cellRoot = (ViewGroup) Tools.findChildById(cellContainer, R.id.atlas_view_messages_cell_geo);
        if (cellRoot == null) {
            cellRoot = (ViewGroup) LayoutInflater.from(cellContainer.getContext()).inflate(R.layout.atlas_view_messages_cell_geo, cellContainer, false);
            if (debug) Log.w(TAG, "geo.onBind() inflated geo cell");
        }

        ImageView geoImageMy    = (ImageView) cellRoot.findViewById(R.id.atlas_view_messages_cell_geo_image_my);
        ImageView geoImageTheir = (ImageView) cellRoot.findViewById(R.id.atlas_view_messages_cell_geo_image_their);
        View containerMy    = cellRoot.findViewById(R.id.atlas_view_messages_cell_geo_container_my);
        View containerTheir = cellRoot.findViewById(R.id.atlas_view_messages_cell_geo_container_their);
        
        boolean myMessage = messagesList.getLayerClient().getAuthenticatedUserId().equals(messagePart.getMessage().getSender().getUserId()); 
        if (myMessage) {
            containerMy.setVisibility(View.VISIBLE);
            containerTheir.setVisibility(View.GONE);
        } else {
            containerMy.setVisibility(View.GONE);
            containerTheir.setVisibility(View.VISIBLE);
        }
        ImageView geoImage = myMessage ? geoImageMy : geoImageTheir; 
        ShapedFrameLayout cellCustom = (ShapedFrameLayout) (myMessage ? containerMy : containerTheir);
        
        Object imageId = messagePart.getId();
        Bitmap bmp = (Bitmap) Atlas.imageLoader.getImageFromCache(imageId);
        if (bmp != null) {
            if (debug) Log.d(TAG, "geo.onBind() bitmap: " + bmp.getWidth() + "x" + bmp.getHeight());
            geoImage.setImageBitmap(bmp);
        } else {
            if (debug) Log.d(TAG, "geo.onBind() spec: " + spec);
            geoImage.setImageDrawable(Tools.EMPTY_DRAWABLE);
            // schedule image
            File tileFile = getTileFile(cellContainer.getContext());
            if (tileFile.exists()) {
                if (debug) Log.d(TAG, "geo.onBind() decodeImage: " + tileFile);
                // request decoding
                spec = Atlas.imageLoader.requestImage(imageId
                        , new Atlas.FileStreamProvider(tileFile)
                        , (int)Tools.getPxFromDp(150, cellContainer.getContext())
                        , (int)Tools.getPxFromDp(150, cellContainer.getContext()), false, this, false);
            } else {
                int width = 300;
                int height = 300;
                int zoom = 16;
                final String url = new StringBuilder()
                        .append("https://maps.googleapis.com/maps/api/staticmap?")
                        .append("format=png32&")
                        .append("center=").append(lat).append(",").append(lon).append("&")
                        .append("zoom=").append(zoom).append("&")
                        .append("size=").append(width).append("x").append(height).append("&")
                        .append("maptype=roadmap&")
                        .append("markers=color:red%7C").append(lat).append(",").append(lon)
                        .toString();
                
                Atlas.downloadQueue.schedule(url, tileFile, this);
                
                if (debug) Log.d(TAG, "geo.onBind() show stub and download image: " + tileFile);
            }
        }
        
        // clustering
        cellCustom.setCornerRadiusDp(16, 16, 16, 16);
        if (AtlasMessagesList.CLUSTERED_BUBBLES) {
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
        }

        return cellRoot;
    }
    
    private File getTileFile(Context context) {
        String fileDir = context.getCacheDir() + File.separator + "geo";
        String fileName = String.format("%f_%f.png", lat, lon);
        return new File(fileDir, fileName);
    }

    @Override
    public String toString() {
        final String text = "Location:\nlon: " + lon + "\nlat: " + lat;
        return text + " part: " + super.toString();
    }

    @Override
    public void onDownloadComplete(String url, final File file) {
        messagesList.requestRefresh();
    }

    @Override
    public void onImageLoaded(ImageSpec spec) {
        messagesList.requestRefresh();
    }
}