/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.core.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.keyboard.MoreKeysPanel;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.suggestions.MoreSuggestionsView.MoreSuggestionsListener;
import com.android.inputmethod.latin.utils.ImportantNoticeUtils;

import java.util.ArrayList;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        public void showImportantNoticeContents();
        public void pickSuggestionManually(SuggestedWordInfo word);
        public void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);
    }

    static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;

    private final ViewGroup mSuggestionsStrip;
    private final ImageButton mVoiceKey;
    // BUMBOARD: shortcuts while there are no suggestions; Traditional/Simplified on the left.
    private final app.bumboard.toolbar.ToolbarView mBumToolbar;
    private final TextView mBumScriptKey;
    private final TextView mBumZhongKey;
    private boolean mBumHasSuggestions;
    private boolean mBumCandidatesShowing;
    private boolean mBumVoiceEnabled;
    private final View mImportantNoticeStrip;
    MainKeyboardView mMainKeyboardView;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = new ArrayList<>();
    private final ArrayList<TextView> mDebugInfoViews = new ArrayList<>();
    private final ArrayList<View> mDividerViews = new ArrayList<>();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private int mStartIndexOfMoreSuggestions;

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;

    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;
        private final View mImportantNoticeStrip;

        public StripVisibilityGroup(final View suggestionStripView,
                final ViewGroup suggestionsStrip, final View importantNoticeStrip) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            mImportantNoticeStrip = importantNoticeStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL
                    : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionStripView, layoutDirection);
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mImportantNoticeStrip, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(VISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
        }

        public void showImportantNoticeStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(VISIBLE);
        }

        public boolean isShowingImportantNoticeStrip() {
            return mImportantNoticeStrip.getVisibility() == VISIBLE;
        }
    }

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     * @param context
     * @param attrs
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = (ViewGroup)findViewById(R.id.suggestions_strip);
        mVoiceKey = (ImageButton)findViewById(R.id.suggestions_strip_voice_key);
        mBumToolbar = (app.bumboard.toolbar.ToolbarView)findViewById(R.id.bum_toolbar);
        mBumScriptKey = (TextView)findViewById(R.id.bum_script_key);
        mBumScriptKey.setBackground(bumRoundRaisedButton(context,
                context.getColor(R.color.bum_m3_primary_container)));
        mBumScriptKey.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        mBumScriptKey.setTextColor(context.getColor(R.color.bum_m3_on_primary_container));
        mBumScriptKey.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mBumScriptKey.setIncludeFontPadding(false);
        mBumScriptKey.setPadding(0, 0, 0, (int) (getResources().getDisplayMetrics().density * 2));
        mBumZhongKey = (TextView)findViewById(R.id.bum_zhong_key);
        mBumZhongKey.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        mBumZhongKey.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mBumZhongKey.setIncludeFontPadding(false);
        mBumZhongKey.setText("\u4E2D");
        mBumZhongKey.setPadding(0, 0, 0, (int) (getResources().getDisplayMetrics().density * 2));
        mImportantNoticeStrip = findViewById(R.id.important_notice_strip);
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip,
                mImportantNoticeStrip);

        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            word.setContentDescription(getResources().getString(R.string.spoken_empty_suggestion));
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(Color.WHITE);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = (MoreSuggestionsView)mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(
                context, mMoreSuggestionsSlidingListener);

        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        final Drawable iconVoice = keyboardAttr.getDrawable(R.styleable.Keyboard_iconShortcutKey);
        keyboardAttr.recycle();
        mVoiceKey.setImageDrawable(iconVoice);
        mVoiceKey.setOnClickListener(this);
        // BUMBOARD: the mic icon is a white Material Symbol, tinted to suit the strip.
        mVoiceKey.setImageTintList(android.content.res.ColorStateList.valueOf(
                context.getColor(R.color.bum_m3_on_key_variant)));
        setBumScriptKey(null, null);
    }

    // BUMBOARD ---------------------------------------------------------------------------

    public app.bumboard.toolbar.ToolbarView getBumToolbar() {
        return mBumToolbar;
    }

    /** While Chinese candidates show in the middle of the strip, the strip's own middle hides. */
    public void setBumCandidatesShowing(final boolean showing) {
        mBumCandidatesShowing = showing;
        updateBumMiddle();
    }

    /**
     * Shows the locked Traditional/Simplified toggle (null hides it, on keyboards that are not
     * Chinese, and the shortcuts and suggestions then start at the left edge). It is a filled
     * chip reading 繁/简 with the script in use emphasised, so it never looks like a character
     * to type.
     */
    public void setBumScriptKey(final Boolean simplified, final OnClickListener onClick) {
        final boolean shown = simplified != null;
        mBumScriptKey.setVisibility(shown ? VISIBLE : GONE);
        mBumScriptKey.setOnClickListener(onClick);
        if (shown) {
            // the script in use, one character on a round raised button; a tap switches
            mBumScriptKey.setText(simplified ? "\u7B80" : "\u7E41");
            mBumScriptKey.setContentDescription(getContext().getString(simplified
                    ? R.string.bum_script_key_simplified : R.string.bum_script_key_traditional));
        }
        mBumScriptShown = shown;
        updateBumMiddle();
    }

    private boolean mBumScriptShown;
    private boolean mBumZhongShown;

    /**
     * The 中 key left of 繁/简 (null hides it): when [active], English words typed are looked
     * up and come back as Chinese candidates; it is then filled in the accent colour.
     */
    public void setBumZhongKey(final Boolean active, final OnClickListener onClick) {
        final boolean shown = active != null;
        mBumZhongKey.setVisibility(shown ? VISIBLE : GONE);
        mBumZhongKey.setOnClickListener(onClick);
        if (shown) {
            final Context context = getContext();
            mBumZhongKey.setBackground(bumRoundRaisedButton(context, context.getColor(active
                    ? R.color.bum_m3_primary : R.color.bum_m3_key)));
            mBumZhongKey.setTextColor(context.getColor(active
                    ? R.color.bum_m3_on_primary : R.color.bum_m3_on_key));
            mBumZhongKey.setContentDescription(context.getString(active
                    ? R.string.bum_zhong_key_on : R.string.bum_zhong_key_off));
        }
        mBumZhongShown = shown;
        updateBumMiddle();
    }

    /** How much of the strip's right edge the locked keys (中, 繁/简) take. */
    public int getBumEdgeWidth() {
        final int key = getResources().getDimensionPixelSize(R.dimen.bum_script_key_width);
        return (mBumScriptShown ? key : 0) + (mBumZhongShown ? key : 0);
    }

    /** A round key with a soft shadow under it, like the raised keys. */
    private static android.graphics.drawable.Drawable bumRoundRaisedButton(final Context context,
            final int color) {
        final float density = context.getResources().getDisplayMetrics().density;
        final android.graphics.drawable.GradientDrawable shadow =
                new android.graphics.drawable.GradientDrawable();
        shadow.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shadow.setColor(context.getColor(R.color.bum_m3_shadow));
        final android.graphics.drawable.GradientDrawable face =
                new android.graphics.drawable.GradientDrawable();
        face.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        face.setColor(color);
        final android.graphics.drawable.LayerDrawable button =
                new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[] { shadow, face });
        final int d = (int) (36 * density);
        final int lift = (int) (2 * density);
        button.setLayerSize(0, d, d);
        button.setLayerSize(1, d, d);
        button.setLayerGravity(0, android.view.Gravity.CENTER);
        button.setLayerGravity(1, android.view.Gravity.CENTER);
        button.setLayerInsetTop(0, lift);
        button.setLayerInsetBottom(1, lift);
        return button;
    }

    /**
     * The bar shows shortcuts and the mic while idle; suggestions or Chinese candidates take
     * their place and the whole width (the Traditional/Simplified chip stays).
     */
    private static void setBumMargins(final View view, final int left, final int right) {
        final MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        if (lp.leftMargin != left || lp.rightMargin != right) {
            lp.leftMargin = left;
            lp.rightMargin = right;
            view.setLayoutParams(lp);
        }
    }

    /** Called when the bar's mode changes (a gallery or panel title showing, or not). */
    public void bumRefresh() {
        updateBumMiddle();
    }

    private void updateBumMiddle() {
        final boolean notice = mStripVisibilityGroup.isShowingImportantNoticeStrip();
        // a panel's title (shortcuts gallery, text editing) takes the bar over suggestions
        final boolean panel = mBumToolbar.getMode() != app.bumboard.toolbar.ToolbarView.Mode.NORMAL;
        final boolean idle = panel || (!mBumHasSuggestions && !mBumCandidatesShowing && !notice);
        mBumToolbar.setVisibility(idle ? VISIBLE : INVISIBLE);
        final boolean voice = idle && !panel && mBumVoiceEnabled;
        mVoiceKey.setVisibility(voice ? VISIBLE : GONE);
        if (!notice) {
            mSuggestionsStrip.setVisibility(mBumHasSuggestions && !mBumCandidatesShowing && !panel
                    ? VISIBLE : INVISIBLE);
        }
        // the right edge: the 繁/简 button (Chinese keyboards), and the mic while idle
        final int script = getBumEdgeWidth();
        final int mic = getResources().getDimensionPixelSize(
                R.dimen.config_suggestions_strip_edge_key_width);
        setBumMargins(mBumToolbar, 0, script + (voice ? mic : 0));
        setBumMargins(mSuggestionsStrip, 0, script);
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = (MainKeyboardView)inputView.findViewById(R.id.keyboard_view);
    }

    public void updateVisibility(final boolean shouldBeVisible, final boolean isFullscreenMode) {
        final int visibility = shouldBeVisible ? VISIBLE : (isFullscreenMode ? GONE : INVISIBLE);
        setVisibility(visibility);
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        // BUMBOARD: the mic shows only while the bar is idle (see updateBumMiddle)
        mBumVoiceEnabled = currentSettingsValues.mShowsVoiceInputKey;
        updateBumMiddle();
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, this);
        mStripVisibilityGroup.showSuggestionsStrip();
        // BUMBOARD: suggestions only while a word is being typed; next-word predictions and
        // punctuation "suggestions" give way to the shortcuts.
        mBumHasSuggestions = !suggestedWords.isEmpty() && !suggestedWords.isPunctuationSuggestions()
                && !suggestedWords.isPrediction();
        updateBumMiddle();
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    // This method checks if we should show the important notice (checks on permanent storage if
    // it has been shown once already or not, and if in the setup wizard). If applicable, it shows
    // the notice. In all cases, it returns true if it was shown, false otherwise.
    public boolean maybeShowImportantNoticeTitle() {
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        if (!ImportantNoticeUtils.shouldShowImportantNotice(getContext(), currentSettingsValues)) {
            return false;
        }
        if (getWidth() <= 0) {
            return false;
        }
        final String importantNoticeTitle = ImportantNoticeUtils.getSuggestContactsNoticeTitle(
                getContext());
        if (TextUtils.isEmpty(importantNoticeTitle)) {
            return false;
        }
        if (isShowingMoreSuggestionPanel()) {
            dismissMoreSuggestionsPanel();
        }
        mLayoutHelper.layoutImportantNotice(mImportantNoticeStrip, importantNoticeTitle);
        mStripVisibilityGroup.showImportantNoticeStrip();
        mImportantNoticeStrip.setOnClickListener(this);
        return true;
    }

    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllDebugInfoViews();
        mStripVisibilityGroup.showSuggestionsStrip();
        dismissMoreSuggestionsPanel();
        mBumHasSuggestions = false;
        updateBumMiddle();
    }

    private void removeAllDebugInfoViews() {
        // The debug info views may be placed as children views of this {@link SuggestionStripView}.
        for (final View debugInfoView : mDebugInfoViews) {
            final ViewParent parent = debugInfoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup)parent).removeView(debugInfoView);
            }
        }
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(wordInfo);
            dismissMoreSuggestionsPanel();
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestionsPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel() {
            mMainKeyboardView.onDismissMoreKeysPanel();
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel() {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.NOT_A_CODE, this);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
        final Keyboard parentKeyboard = mMainKeyboardView.getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (mSuggestedWords.size() <= mStartIndexOfMoreSuggestions) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, mStartIndexOfMoreSuggestions, maxWidth,
                (int)(maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < mStartIndexOfMoreSuggestions; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for {@link onInterceptTouchEvent(MotionEvent)} and
    // {@link onTouchEvent(MotionEvent)}.
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private boolean mNeedsToTransformTouchEventToHoverEvent;
    private boolean mIsDispatchingHoverEventToMoreSuggestions;
    private final GestureDetector mMoreSuggestionsSlidingDetector;
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            if (down == null) {
                return false;
            }
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        if (mStripVisibilityGroup.isShowingImportantNoticeStrip()) {
            return false;
        }
        // Detecting sliding up finger to show {@link MoreSuggestionsView}.
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            return mMoreSuggestionsSlidingDetector.onTouchEvent(me);
        }
        if (mMoreSuggestionsView.isInModalMode()) {
            return false;
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                || mOriginY - y >= mMoreSuggestionsModalTolerance) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further {@link MotionEvent}s will be delivered to
            // {@link #onTouchEvent(MotionEvent)}.
            mNeedsToTransformTouchEventToHoverEvent =
                    AccessibilityUtils.getInstance().isTouchExplorationEnabled();
            mIsDispatchingHoverEventToMoreSuggestions = false;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            // Decided to be in the modal input mode.
            mMoreSuggestionsView.setModalMode();
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with suggested words and voice key.
        return true;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            // Ignore any touch event while more suggestions panel hasn't been shown.
            // Detecting sliding up is done at {@link #onInterceptTouchEvent}.
            return true;
        }
        // In the sliding input mode. {@link MotionEvent} should be forwarded to
        // {@link MoreSuggestionsView}.
        final int index = me.getActionIndex();
        final int x = mMoreSuggestionsView.translateX((int)me.getX(index));
        final int y = mMoreSuggestionsView.translateY((int)me.getY(index));
        me.setLocation(x, y);
        if (!mNeedsToTransformTouchEventToHoverEvent) {
            mMoreSuggestionsView.onTouchEvent(me);
            return true;
        }
        // In sliding suggestion mode with accessibility mode on, a touch event should be
        // transformed to a hover event.
        final int width = mMoreSuggestionsView.getWidth();
        final int height = mMoreSuggestionsView.getHeight();
        final boolean onMoreSuggestions = (x >= 0 && x < width && y >= 0 && y < height);
        if (!onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Just drop this touch event because dispatching hover event isn't started yet and
            // the touch event isn't on {@link MoreSuggestionsView}.
            return true;
        }
        final int hoverAction;
        if (onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Transform this touch event to a hover enter event and start dispatching a hover
            // event to {@link MoreSuggestionsView}.
            mIsDispatchingHoverEventToMoreSuggestions = true;
            hoverAction = MotionEvent.ACTION_HOVER_ENTER;
        } else if (me.getActionMasked() == MotionEvent.ACTION_UP) {
            // Transform this touch event to a hover exit event and stop dispatching a hover event
            // after this.
            mIsDispatchingHoverEventToMoreSuggestions = false;
            mNeedsToTransformTouchEventToHoverEvent = false;
            hoverAction = MotionEvent.ACTION_HOVER_EXIT;
        } else {
            // Transform this touch event to a hover move event.
            hoverAction = MotionEvent.ACTION_HOVER_MOVE;
        }
        me.setAction(hoverAction);
        mMoreSuggestionsView.onHoverEvent(me);
        return true;
    }

    @Override
    public void onClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.CODE_UNSPECIFIED, this);
        if (view == mImportantNoticeStrip) {
            mListener.showImportantNoticeContents();
            return;
        }
        if (view == mVoiceKey) {
            mListener.onCodeInput(Constants.CODE_SHORTCUT,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }

        final Object tag = view.getTag();
        // {@link Integer} tag is set at
        // {@link SuggestionStripLayoutHelper#setupWordViewsTextAndColor(SuggestedWords,int)} and
        // {@link SuggestionStripLayoutHelper#layoutPunctuationSuggestions(SuggestedWords,ViewGroup}
        if (tag instanceof Integer) {
            final int index = (Integer) tag;
            if (index >= mSuggestedWords.size()) {
                return;
            }
            final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
            mListener.pickSuggestionManually(wordInfo);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
        if (oldw <= 0 && w > 0) {
            maybeShowImportantNoticeTitle();
        }
    }
}
