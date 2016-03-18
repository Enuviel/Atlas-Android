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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.layer.atlas.Atlas.Participant;
import com.layer.atlas.Atlas.Tools;
import com.layer.sdk.LayerClient;
import com.layer.sdk.changes.LayerChange;
import com.layer.sdk.changes.LayerChangeEvent;
import com.layer.sdk.exceptions.LayerException;
import com.layer.sdk.listeners.LayerAuthenticationListener;
import com.layer.sdk.listeners.LayerChangeEventListener;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.LayerObject;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.Message.RecipientStatus;
import com.layer.sdk.query.Query;

/**
 * @author Oleg Orlov
 * @since 14 May 2015
 */
public class AtlasConversationsList extends FrameLayout implements LayerChangeEventListener.MainThread {
    
    private static final String TAG = AtlasConversationsList.class.getSimpleName();
    private static final boolean debug = false;

    private ListView conversationsList;
    private BaseAdapter conversationsAdapter;

    private ArrayList<Conversation> conversations = new ArrayList<Conversation>();
    
    private LayerClient layerClient;
    private Query<Conversation> query;
    
    private ConversationClickListener clickListener;
    private ConversationLongClickListener longClickListener;
    
    //styles
    private int titleTextColor;
    private int titleTextStyle;
    private Typeface titleTextTypeface;
    private int titleUnreadTextColor;
    private int titleUnreadTextStyle;
    private Typeface titleUnreadTextTypeface;
    private int subtitleTextColor;
    private int subtitleTextStyle;
    private Typeface subtitleTextTypeface;
    private int subtitleUnreadTextColor;
    private int subtitleUnreadTextStyle;
    private Typeface subtitleUnreadTextTypeface;
    private int cellBackgroundColor;
    private int cellUnreadBackgroundColor;
    private int dateTextColor;
    private int avatarTextColor;
    private int avatarBackgroundColor;
    
    // date 
    private final DateFormat dateFormat;
    private final DateFormat timeFormat;

    public AtlasConversationsList(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        parseStyle(context, attrs, defStyle);
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
        this.timeFormat = android.text.format.DateFormat.getTimeFormat(context);
    }

    public AtlasConversationsList(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AtlasConversationsList(Context context) {
        super(context);
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
        this.timeFormat = android.text.format.DateFormat.getTimeFormat(context);
    }

    public void init(final LayerClient layerClient, final Atlas.ParticipantProvider participantProvider) {
        if (layerClient == null) throw new IllegalArgumentException("LayerClient cannot be null");
        if (participantProvider == null) throw new IllegalArgumentException("ParticipantProvider cannot be null");
        if (conversationsList != null) throw new IllegalStateException("AtlasConversationList is already initialized!");
        
        this.layerClient = layerClient;
        
        // inflate children:
        LayoutInflater.from(getContext()).inflate(R.layout.atlas_conversations_list, this);
        
        this.conversationsList = (ListView) findViewById(R.id.atlas_conversations_view);
        this.conversationsList.setAdapter(conversationsAdapter = new BaseAdapter() {
            
            /** to draw right avatar with mask separately */
            Bitmap tmpBmp = Bitmap.createBitmap((int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()), Config.ARGB_8888);
            Bitmap maskSingleBmp     = Bitmap.createBitmap((int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()), Config.ARGB_8888);
            Bitmap maskMultiLeftBmp  = Bitmap.createBitmap((int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()), Config.ARGB_8888);
            Bitmap maskMultiRightBmp = Bitmap.createBitmap((int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()), Config.ARGB_8888);
            Bitmap maskMultiBmp = Bitmap.createBitmap((int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()), Config.ARGB_8888);
            Paint avatarPaint = new Paint();
            Paint maskPaint = new Paint();
            {
                avatarPaint.setAntiAlias(true);
                avatarPaint.setDither(true);
                
                maskPaint.setAntiAlias(true);
                maskPaint.setDither(true);
                maskPaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
                
                Paint paintCircle = new Paint();
                paintCircle.setStyle(Style.FILL_AND_STROKE);
                paintCircle.setColor(Color.CYAN);
                paintCircle.setAntiAlias(true);
                
                Canvas maskSingleCanvas = new Canvas(maskSingleBmp);
                maskSingleCanvas.drawCircle(0.5f * maskSingleBmp.getWidth(), 0.5f * maskSingleBmp.getHeight(), 0.5f * maskSingleBmp.getWidth(), paintCircle);
                
                Paint paintErase = new Paint();
                paintErase.setAntiAlias(true);
                paintErase.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
                
                float radiusOne = Tools.getPxFromDp(13f, getContext());
                float centerTwo = Tools.getPxFromDp(27f, getContext());
                float spacingRadius = Tools.getPxFromDp(14.3f, getContext());

                Canvas maskLeftCanvas = new Canvas(maskMultiLeftBmp);
                maskLeftCanvas.drawCircle(radiusOne, radiusOne, radiusOne, paintCircle);
                maskLeftCanvas.drawCircle(centerTwo, centerTwo, spacingRadius, paintErase); // cut right-bottom
                
                Canvas maskRightCanvas = new Canvas(maskMultiRightBmp);
                maskRightCanvas.drawCircle(centerTwo, centerTwo, radiusOne, paintCircle);

                Canvas maskMultiCanvas = new Canvas(maskMultiBmp);
                maskMultiCanvas.drawCircle(radiusOne, radiusOne, radiusOne, paintCircle);
                maskMultiCanvas.drawCircle(centerTwo, centerTwo, spacingRadius, paintErase);// cut right-bottom
                maskMultiCanvas.drawCircle(centerTwo, centerTwo, radiusOne, paintCircle);
            }
            
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.atlas_view_conversations_list_convert, parent, false);
                }
                
                Uri convId = conversations.get(position).getId();
                Conversation conv = layerClient.getConversation(convId);
                
                ArrayList<String> allButMe = new ArrayList<String>(conv.getParticipants());
                allButMe.remove(layerClient.getAuthenticatedUserId());
                
                TextView textTitle = (TextView) convertView.findViewById(R.id.atlas_conversation_view_convert_participant);
                String conversationTitle = Atlas.getTitle(conv, participantProvider, layerClient.getAuthenticatedUserId());
                textTitle.setText(conversationTitle);
                
                // avatar icons... 
                TextView textInitials = (TextView) convertView.findViewById(R.id.atlas_view_conversations_list_convert_avatar_single_text);
                View avatarSingle = convertView.findViewById(R.id.atlas_view_conversations_list_convert_avatar_single);
                View avatarMulti = convertView.findViewById(R.id.atlas_view_conversations_list_convert_avatar_multi);
                ImageView avatarImgView = (ImageView) convertView.findViewById(R.id.atlas_view_conversations_list_convert_avatar_img);
                Bitmap avatarBmp = null;
                if (avatarImgView.getDrawable() instanceof BitmapDrawable){
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) avatarImgView.getDrawable();
                    avatarBmp = bitmapDrawable.getBitmap();
                } else {
                    avatarBmp = Bitmap.createBitmap((int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()), Config.ARGB_8888);
                    avatarImgView.setImageBitmap(avatarBmp);
                }
                Canvas canvas = new Canvas(avatarBmp);
                canvas.drawColor(0, Mode.CLEAR);
                canvas.drawColor(avatarBackgroundColor);
                if (allButMe.size() < 2) {
                    String conterpartyUserId = allButMe.get(0);
                    Atlas.Participant participant = participantProvider.getParticipant(conterpartyUserId);
                    if (participant == null || participant.getAvatarDrawable() == null) {
                        textInitials.setText(participant == null ? "?" : Atlas.getInitials(participant));
                        textInitials.setTextColor(avatarTextColor);
                        avatarSingle.setVisibility(View.VISIBLE);
                    } else {
                        Drawable drawable = participant.getAvatarDrawable();
                        drawable.setBounds(0, 0, (int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()));
                        drawable.draw(canvas);
                        avatarSingle.setVisibility(View.GONE);
                    }
                    canvas.drawBitmap(maskSingleBmp, 0, 0, maskPaint);
                    avatarMulti.setVisibility(View.GONE);
                } else {
                    Participant leftParticipant = null;
                    Participant rightParticipant = null;
                    for (Iterator<String> itUserId = allButMe.iterator(); itUserId.hasNext();) {
                        String userId = itUserId.next();
                        Participant p = participantProvider.getParticipant(userId);
                        if (p == null) continue;
                        
                        if (leftParticipant == null) {
                            leftParticipant = p;
                        } else {
                            rightParticipant = p;
                            break;
                        }
                    }
                    
                    TextView textInitialsLeft  = (TextView) convertView.findViewById(R.id.atlas_view_conversations_list_convert_avatar_multi_left);
                    TextView textInitialsRight = (TextView) convertView.findViewById(R.id.atlas_view_conversations_list_convert_avatar_multi_right);
                    Canvas tmpCanvas = new Canvas(tmpBmp);
                    if (leftParticipant == null || leftParticipant.getAvatarDrawable() == null) {
                        textInitialsLeft.setText(leftParticipant == null ? "?" : Atlas.getInitials(leftParticipant));
                        textInitialsLeft.setTextColor(avatarTextColor);
                        textInitialsLeft.setVisibility(View.VISIBLE);
                    } else {
                        tmpCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
                        Drawable leftDrawable = leftParticipant.getAvatarDrawable();
                        leftDrawable.setBounds(0, 0, (int)Tools.getPxFromDp(26, getContext()), (int)Tools.getPxFromDp(26, getContext()));
                        leftDrawable.draw(tmpCanvas);
                        tmpCanvas.drawBitmap(maskMultiLeftBmp, 0, 0, maskPaint);
                        canvas.drawBitmap(tmpBmp, 0, 0, avatarPaint);
                        textInitialsLeft.setVisibility(View.GONE);
                    }
                    if (rightParticipant == null || rightParticipant.getAvatarDrawable() == null) {
                        textInitialsRight.setText(rightParticipant == null ? "?" : Atlas.getInitials(rightParticipant));
                        textInitialsRight.setTextColor(avatarTextColor);
                        textInitialsRight.setVisibility(View.VISIBLE);
                    } else {
                        tmpCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
                        Drawable drawable = rightParticipant.getAvatarDrawable();
                        drawable.setBounds((int)Tools.getPxFromDp(14, getContext()), (int)Tools.getPxFromDp(14, getContext()), (int)Tools.getPxFromDp(40, getContext()), (int)Tools.getPxFromDp(40, getContext()));
                        drawable.draw(tmpCanvas);
                        tmpCanvas.drawBitmap(maskMultiRightBmp, 0, 0, maskPaint);
                        canvas.drawBitmap(tmpBmp, 0, 0, avatarPaint);
                        textInitialsRight.setVisibility(View.GONE);
                    }
                    
                    canvas.drawBitmap(maskMultiBmp, 0, 0, maskPaint);               // always apply mask 
                    avatarSingle.setVisibility(View.GONE);
                    avatarMulti.setVisibility(View.VISIBLE);
                }
                
                TextView textLastMessage = (TextView) convertView.findViewById(R.id.atlas_conversation_view_last_message);
                TextView timeView = (TextView) convertView.findViewById(R.id.atlas_conversation_view_convert_time);
                if (conv.getLastMessage() != null ) {
                    Message last = conv.getLastMessage();
                    String lastMessageText = Atlas.Tools.toString(last);
                    
                    textLastMessage.setText(lastMessageText);
                    
                    Date sentAt = last.getSentAt();
                    if (sentAt == null) timeView.setText("...");
                    else                timeView.setText(formatTime(sentAt));

                    String userId = last.getSender().getUserId();                   // could be null for system messages 
                    String myId = layerClient.getAuthenticatedUserId();
                    if ((userId != null) && !userId.equals(myId) && last.getRecipientStatus(myId) != RecipientStatus.READ) {
                        textTitle.setTextColor(titleUnreadTextColor);
                        textTitle.setTypeface(titleUnreadTextTypeface, titleUnreadTextStyle);
                        textLastMessage.setTypeface(subtitleUnreadTextTypeface, subtitleUnreadTextStyle);
                        textLastMessage.setTextColor(subtitleUnreadTextColor);
                        convertView.setBackgroundColor(cellUnreadBackgroundColor);
                    } else {
                        textTitle.setTextColor(titleTextColor);
                        textTitle.setTypeface(titleTextTypeface, titleTextStyle);
                        textLastMessage.setTypeface(subtitleTextTypeface, subtitleTextStyle);
                        textLastMessage.setTextColor(subtitleTextColor);
                        convertView.setBackgroundColor(cellBackgroundColor);
                    }
                } else {
                    timeView.setText("...");
                    textLastMessage.setText("");
                    textTitle.setTextColor(titleTextColor);
                    textTitle.setTypeface(titleTextTypeface, titleTextStyle);
                    textLastMessage.setTypeface(subtitleTextTypeface, subtitleTextStyle);
                    textLastMessage.setTextColor(subtitleTextColor);
                    convertView.setBackgroundColor(cellBackgroundColor);
                }
                timeView.setTextColor(dateTextColor);
                return convertView;
            }
            public long getItemId(int position) {
                return position;
            }
            public Object getItem(int position) {
                return conversations.get(position);
            }
            public int getCount() {
                return conversations.size();
            }
        });
        
        conversationsList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation conv = conversations.get(position);
                if (clickListener != null) clickListener.onItemClick(conv);
            }
        });
        conversationsList.setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation conv = conversations.get(position);
                if (longClickListener != null) longClickListener.onItemLongClick(conv);
                return true;
            }
        });
        
        // clean everything if deathenticated (client will explode on .getConversation())
        // and rebuilt everything back after successful authentication  
        layerClient.registerAuthenticationListener(new LayerAuthenticationListener() {
            public void onDeauthenticated(LayerClient client) {
                if (debug) Log.w(TAG, "onDeauthenticated() ");
                updateValues();
            }
            public void onAuthenticated(LayerClient client, String userId) {
                updateValues();
            }
            public void onAuthenticationError(LayerClient client, LayerException exception) {}
            public void onAuthenticationChallenge(LayerClient client, String nonce) {}
        });
        
        applyStyle();

        updateValues();
    }
    
    public void updateValues() {
        if (conversationsAdapter == null) return; // never initialized

        conversations.clear(); // always clean, rebuild if authenticated 
        conversationsAdapter.notifyDataSetChanged();

        if (!layerClient.isAuthenticated()) return;

        List<Conversation> convs = null;
        if (query != null) {
            convs = (List<Conversation>) layerClient.executeQueryForObjects(query);
            if (debug) Log.d(TAG, "updateValues() conv from query: " + convs.size());
            conversations.addAll(convs);
        } else {
            convs = layerClient.getConversations();
            if (debug) Log.d(TAG, "updateValues() conv: " + convs.size());
            
            for (Conversation conv : convs) {
                // no participants means we are removed from conversation (disconnected conversation)
                if (conv.getParticipants().size() == 0) continue;
                // only ourselves in participant list is possible to happen, but there is nothing to do with it
                // behave like conversation is disconnected
                if (conv.getParticipants().size() == 1 && conv.getParticipants().contains(layerClient.getAuthenticatedUserId())) continue;

                conversations.add(conv);
            }
            
            // the bigger .time the highest in the list
            Collections.sort(conversations, new Comparator<Conversation>() {
                public int compare(Conversation lhs, Conversation rhs) {
                    long leftSentAt = 0;
                    Message leftLastMessage = lhs.getLastMessage();
                    if (leftLastMessage != null && leftLastMessage.getSentAt() != null) {
                        leftSentAt = leftLastMessage.getSentAt().getTime();
                    }
                    long rightSentAt = 0;
                    Message rightLastMessage = rhs.getLastMessage();
                    if (rightLastMessage != null && rightLastMessage.getSentAt() != null) {
                        rightSentAt = rightLastMessage.getSentAt().getTime();
                    }
                    long result = rightSentAt - leftSentAt;
                    if (result == 0L) return 0;
                    return result < 0L ? -1 : 1;
                }
            });
        }
    }

    private volatile boolean refreshScheduled = false;

    public void requestRefresh() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        conversationsList.post(INVALIDATE_VIEW);
    }

    private final Runnable INVALIDATE_VIEW = new Runnable() {
        public void run() {
            conversationsList.invalidateViews();
            refreshScheduled = false;
        }
    };

    public void setQuery(Query<Conversation> query) {
        if (debug) Log.w(TAG, "setQuery() query: " + query);
        // check
        if ( query != null && ! Conversation.class.equals(query.getQueryClass())) {
            throw new IllegalArgumentException("Query must return Conversation object. Actual class: " + query.getQueryClass());
        }
        // 
        this.query = query;
        updateValues();
    }

    private void parseStyle(Context context, AttributeSet attrs, int defStyle) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AtlasConversationList, R.attr.AtlasConversationList, defStyle);
        this.titleTextColor = ta.getColor(R.styleable.AtlasConversationList_cellTitleTextColor, context.getResources().getColor(R.color.atlas_text_black));
        this.titleTextStyle = ta.getInt(R.styleable.AtlasConversationList_cellTitleTextStyle, Typeface.NORMAL);
        String titleTextTypefaceName = ta.getString(R.styleable.AtlasConversationList_cellTitleTextTypeface);
        this.titleTextTypeface = titleTextTypefaceName != null ? Typeface.create(titleTextTypefaceName, titleTextStyle) : null;

        this.titleUnreadTextColor = ta.getColor(R.styleable.AtlasConversationList_cellTitleUnreadTextColor, context.getResources().getColor(R.color.atlas_text_black));
        this.titleUnreadTextStyle = ta.getInt(R.styleable.AtlasConversationList_cellTitleUnreadTextStyle, Typeface.BOLD);
        String titleUnreadTextTypefaceName = ta.getString(R.styleable.AtlasConversationList_cellTitleUnreadTextTypeface);
        this.titleUnreadTextTypeface = titleUnreadTextTypefaceName != null ? Typeface.create(titleUnreadTextTypefaceName, titleUnreadTextStyle) : null;

        this.subtitleTextColor = ta.getColor(R.styleable.AtlasConversationList_cellSubtitleTextColor, context.getResources().getColor(R.color.atlas_text_black));
        this.subtitleTextStyle = ta.getInt(R.styleable.AtlasConversationList_cellSubtitleTextStyle, Typeface.NORMAL);
        String subtitleTextTypefaceName = ta.getString(R.styleable.AtlasConversationList_cellSubtitleTextTypeface);
        this.subtitleTextTypeface = subtitleTextTypefaceName != null ? Typeface.create(subtitleTextTypefaceName, subtitleTextStyle) : null;

        this.subtitleUnreadTextColor = ta.getColor(R.styleable.AtlasConversationList_cellSubtitleUnreadTextColor, context.getResources().getColor(R.color.atlas_text_black));
        this.subtitleUnreadTextStyle = ta.getInt(R.styleable.AtlasConversationList_cellSubtitleUnreadTextStyle, Typeface.NORMAL);
        String subtitleUnreadTextTypefaceName = ta.getString(R.styleable.AtlasConversationList_cellSubtitleUnreadTextTypeface);
        this.subtitleUnreadTextTypeface = subtitleUnreadTextTypefaceName != null ? Typeface.create(subtitleUnreadTextTypefaceName, subtitleUnreadTextStyle) : null;

        this.cellBackgroundColor = ta.getColor(R.styleable.AtlasConversationList_cellBackgroundColor, Color.TRANSPARENT);
        this.cellUnreadBackgroundColor = ta.getColor(R.styleable.AtlasConversationList_cellUnreadBackgroundColor, Color.TRANSPARENT);
        this.dateTextColor = ta.getColor(R.styleable.AtlasConversationList_dateTextColor, context.getResources().getColor(R.color.atlas_text_black));
        this.avatarTextColor = ta.getColor(R.styleable.AtlasConversationList_avatarTextColor, context.getResources().getColor(R.color.atlas_text_black));
        this.avatarBackgroundColor = ta.getColor(R.styleable.AtlasConversationList_avatarBackgroundColor, context.getResources().getColor(R.color.atlas_shape_avatar_gray));
        ta.recycle();
    }
    
    private void applyStyle() {
        conversationsAdapter.notifyDataSetChanged();
    }
    
    public String formatTime(Date sentAt) {
        if (sentAt == null) sentAt = new Date();
        return Atlas.formatTimeShort(sentAt, timeFormat, dateFormat);
    }

    @Override
    public void onEventMainThread(LayerChangeEvent event) {
        for (LayerChange change : event.getChanges()) {
            if (change.getObjectType() == LayerObject.Type.CONVERSATION
                    || change.getObjectType() == LayerObject.Type.MESSAGE) {
                updateValues();
                return;
            }
        }
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (debug) Log.w(TAG, "onAttachedToWindow() ready for updates from Layer. called from: " + Dt.printStackTrace());
        if (layerClient != null) {
            layerClient.registerEventListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (debug) Log.w(TAG, "onDetachedFromWindow() unsubscribe from Layer. called from: " + Dt.printStackTrace());
        layerClient.unregisterEventListener(this);
    }

    public ConversationClickListener getClickListener() {
        return clickListener;
    }

    public void setClickListener(ConversationClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public ConversationLongClickListener getLongClickListener() {
        return longClickListener;
    }

    public void setLongClickListener(ConversationLongClickListener conversationLongClickListener) {
        this.longClickListener = conversationLongClickListener;
    }

    public interface ConversationClickListener {
        void onItemClick(Conversation conversation);
    }
    
    public interface ConversationLongClickListener {
        void onItemLongClick(Conversation conversation);
    }
}
