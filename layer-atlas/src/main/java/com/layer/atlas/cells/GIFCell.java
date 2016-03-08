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

import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import com.layer.atlas.Atlas;
import com.layer.atlas.Atlas.ImageLoader.InputStreamProvider;
import com.layer.atlas.AtlasMessagesList;
import com.layer.atlas.GIFDrawable;
import com.layer.sdk.messaging.MessagePart;

/**
 * @author Oleg Orlov
 * @since  21 Jun 2015
 */
public class GIFCell extends ImageCell {
    private static final String TAG = GIFCell.class.getSimpleName();
    private static final boolean debug = false;
    
    public GIFCell(MessagePart fullImagePart, MessagePart previewImagePart, int width, int height, int orientation, AtlasMessagesList messagesList) {
        super(fullImagePart, previewImagePart, width, height, orientation, messagesList);
    }
    
    public GIFCell(MessagePart fullImagePart, AtlasMessagesList messagesList) {
        super(fullImagePart, messagesList);
    }

    @Override
    protected Drawable getDrawable(MessagePart workingPart) {
        Movie mov  = (Movie) Atlas.imageLoader.getImageFromCache(workingPart.getId());
        
        // TODO: calculate properly with rotation
        int requiredWidth  = messagesList.getWidth();
        int requiredHeight = messagesList.getHeight();
        
        if (mov != null) {
            if (debug) Log.i(TAG, "gif.onBind() returned from cache! " + mov.width() + "x" + mov.height() 
                    + ", req: " + requiredWidth + "x" + requiredHeight + " for " + workingPart.getId());
            return new GIFDrawable(mov);
        } else if (workingPart.isContentReady()){
            final Uri id = workingPart.getId();
            InputStreamProvider streamProvider = new Atlas.MessagePartBufferedStreamProvider(workingPart); 
            imageSpec = Atlas.imageLoader.requestImage(id, streamProvider, requiredWidth, requiredHeight, true, this, false);
        }
        return null;
    }

    @Override
    protected MessagePart getWorkingPart() {
        return fullPart;
    }
}
