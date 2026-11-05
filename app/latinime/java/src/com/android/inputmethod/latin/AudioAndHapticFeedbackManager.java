/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.media.AudioManager;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsValues;

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;

    private static final AudioAndHapticFeedbackManager sInstance =
            new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        // LYCHEE: Lychee's own key sounds, when one is chosen in Settings
        app.lychee.ui.KeySounds.init(context);
        // LYCHEE: sound and vibration follow Settings at once, not from the next text field
        mResources = context.getResources();
        final android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs = prefs;
        readLycheeFeedback(prefs);
        prefs.registerOnSharedPreferenceChangeListener(mFeedbackListener);
    }

    // LYCHEE: the sound and vibration settings, read live
    private android.content.SharedPreferences mPrefs;
    private android.content.res.Resources mResources;
    private boolean mLycheeVibrateOn;
    private boolean mLycheeSoundOn;
    private int mLycheeVibrationMs = -1;
    private float mLycheeVolume = -1f;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener mFeedbackListener =
            (prefs, key) -> {
                if (key == null || key.equals(Settings.PREF_VIBRATE_ON) || key.equals(Settings.PREF_SOUND_ON)
                        || key.equals(Settings.PREF_VIBRATION_DURATION_SETTINGS)
                        || key.equals(Settings.PREF_KEYPRESS_SOUND_VOLUME)) {
                    readLycheeFeedback(prefs);
                }
            };

    private void readLycheeFeedback(final android.content.SharedPreferences prefs) {
        mLycheeVibrateOn = Settings.readVibrationEnabled(prefs, mResources);
        mLycheeSoundOn = Settings.readKeypressSoundEnabled(prefs, mResources);
        mLycheeVibrationMs = Settings.readKeypressVibrationDuration(prefs, mResources);
        mLycheeVolume = Settings.readKeypressSoundVolume(prefs, mResources);
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void performHapticAndAudioFeedback(final int code,
            final View viewToPerformHapticFeedbackOn) {
        performHapticFeedback(viewToPerformHapticFeedbackOn);
        performAudioFeedback(code);
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    public void vibrate(final long milliseconds) {
        if (mVibrator == null) {
            return;
        }
        // LYCHEE: a vibration of Lychee's own, so it works whatever the phone's touch feedback
        // setting is
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mVibrator.vibrate(android.os.VibrationEffect.createOneShot(milliseconds,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate(milliseconds);
        }
    }

    private boolean reevaluateIfSoundIsOn() {
        if (!mLycheeSoundOn || mAudioManager == null) {
            return false;
        }
        return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    public void performAudioFeedback(final int code) {
        // if mAudioManager is null, we can't play a sound anyway, so return
        if (mAudioManager == null) {
            return;
        }
        if (!mSoundOn) {
            return;
        }
        // LYCHEE: Lychee's own key sounds (Settings > Preferences > Key sound)
        final int lycheeKind = code == Constants.CODE_SPACE ? 1 : code == Constants.CODE_ENTER ? 2
                : code == Constants.CODE_DELETE ? 3 : 0;
        if (app.lychee.ui.KeySounds.play(lycheeKind, mLycheeVolume)) {
            return;
        }
        final int sound;
        switch (code) {
        case Constants.CODE_DELETE:
            sound = AudioManager.FX_KEYPRESS_DELETE;
            break;
        case Constants.CODE_ENTER:
            sound = AudioManager.FX_KEYPRESS_RETURN;
            break;
        case Constants.CODE_SPACE:
            sound = AudioManager.FX_KEYPRESS_SPACEBAR;
            break;
        default:
            sound = AudioManager.FX_KEYPRESS_STANDARD;
            break;
        }
        mAudioManager.playSoundEffect(sound, mLycheeVolume);
    }

    public void performHapticFeedback(final View viewToPerformHapticFeedbackOn) {
        if (!mLycheeVibrateOn) {
            return;
        }
        // LYCHEE: always Lychee's own tap (the system's KEYBOARD_TAP is silent when the
        // phone's touch feedback is off): the phone's crisp predefined click when no length is
        // set, like Gboard's, else a short pulse of the chosen length
        if (mLycheeVibrationMs < 0 && mVibrator != null
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            mVibrator.vibrate(android.os.VibrationEffect.createPredefined(
                    android.os.VibrationEffect.EFFECT_CLICK));
            return;
        }
        vibrate(mLycheeVibrationMs >= 0 ? mLycheeVibrationMs : DEFAULT_VIBRATION_MS);
    }

    // LYCHEE: a light tap, like Gboard's
    private static final int DEFAULT_VIBRATION_MS = 12;

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        if (mPrefs != null) readLycheeFeedback(mPrefs);
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void onRingerModeChanged() {
        mSoundOn = reevaluateIfSoundIsOn();
    }
}
