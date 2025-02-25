package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.ArrayList;
import java.util.List;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi
    private static DisplayMetrics sCachedDisplayMetrics;
    protected static boolean sIsInPipMode;
    private ScreensaverManager mScreensaverManager;
    private List<OnPermissions> mOnPermissions;
    // Disabled by default to fix IllegalStateException: Can not perform this action after onSaveInstanceState
    private boolean mSaveStateEnabled;

    public interface OnPermissions {
        void onPermissions(int requestCode, String[] permissions, int[] grantResults);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Fixing: Only fullscreen opaque activities can request orientation (api 26)
        // NOTE: You should remove 'screenOrientation' from the manifest.
        // NOTE: Possible side effect: initDpi() won't work: "When you setRequestedOrientation() the view may be restarted"
        //if (VERSION.SDK_INT != 26) {
        //    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //}
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting %s...", this.getClass().getSimpleName());

        initDpi();
        initTheme();

        mScreensaverManager = new ScreensaverManager(this);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchTouchEvent(event);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean isKeepScreenOff = mScreensaverManager.isScreenOff() && Helpers.equalsAny(event.getKeyCode(),
                    new int[]{KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN});
            if (!isKeepScreenOff) {
                mScreensaverManager.enable();
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) { // shortcut for closing PIP
            PlaybackPresenter.instance(this).forceFinish();
            return true;
        }

        boolean result = super.onKeyDown(keyCode, event);

        // Fix buggy G20s menu key (focus lost on key press)
        return KeyHelpers.isMenuKey(keyCode) || result;
    }

    public void finishReally() {
        try {
            super.finish();
        } catch (Exception e) {
            // TextView not attached to window manager (IllegalArgumentException)
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Context contextWrapper = LocaleContextWrapper.wrap(newBase, LocaleUpdater.getSavedLocale(newBase));

        super.attachBaseContext(contextWrapper);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 4K fix with AFR
        applyCustomConfig();
        // Most of the fullscreen tweaks could be performed in styles but not all.
        // E.g. Hide bottom navigation bar (couldn't be done in styles).
        Helpers.makeActivityFullscreen(this);

        mScreensaverManager.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        mScreensaverManager.disable();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        applyCustomConfig();
    }

    public ScreensaverManager getScreensaverManager() {
        return mScreensaverManager;
    }

    protected void initTheme() {
        int rootThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (rootThemeResId > 0) {
            setTheme(rootThemeResId);
        }
    }

    private void initDpi() {
        getResources().getDisplayMetrics().setTo(getDisplayMetrics());
    }

    //private DisplayMetrics getDisplayMetrics() {
    //    DisplayMetrics displayMetrics = new DisplayMetrics();
    //    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    //
    //    // To adapt to resolution change (e.g. on AFR) check old width.
    //    if (sCachedDisplayMetrics == null || sCachedDisplayMetrics.widthPixels != displayMetrics.widthPixels) {
    //        float uiScale = MainUIData.instance(this).getUIScale();
    //        float widthRatio = DEFAULT_WIDTH / displayMetrics.widthPixels;
    //        float density = DEFAULT_DENSITY / widthRatio * uiScale;
    //        displayMetrics.density = density;
    //        displayMetrics.scaledDensity = density;
    //        sCachedDisplayMetrics = displayMetrics;
    //    }
    //
    //    return sCachedDisplayMetrics;
    //}

    private DisplayMetrics getDisplayMetrics() {
        // BUG: adapt to resolution change (e.g. on AFR)
        // Don't disable caching or you will experience weird sizes on cards in video suggestions (e.g. after exit from PIP)!
        if (sCachedDisplayMetrics == null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float uiScale = MainUIData.instance(this).getUIScale();
            float widthRatio = DEFAULT_WIDTH / displayMetrics.widthPixels;
            float density = DEFAULT_DENSITY / widthRatio * uiScale;
            displayMetrics.density = density;
            displayMetrics.scaledDensity = density;
            sCachedDisplayMetrics = displayMetrics;
        }

        return sCachedDisplayMetrics;
    }

    private void applyCustomConfig() {
        // NOTE: dpi should come after locale update to prevent resources overriding.

        // Fix sudden language change.
        // Could happen when screen goes off or after PIP mode.
        LocaleUpdater.applySavedLocale(this);

        // Fix sudden dpi change.
        // Could happen when screen goes off or after PIP mode.
        initDpi();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mOnPermissions != null) {
            for (OnPermissions callback : mOnPermissions) {
                callback.onPermissions(requestCode, permissions, grantResults);
            }
            mOnPermissions.clear();
            mOnPermissions = null;
        }
    }

    /**
     * NOTE: When enabled, you could face IllegalStateException: Can not perform this action after onSaveInstanceState<br/>
     * https://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-wit?page=1&tab=scoredesc#tab-top
     */
    public void enableSaveState(boolean enable) {
        mSaveStateEnabled = enable;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // No call for super(). Bug on API Level > 11.
        if (mSaveStateEnabled) {
            super.onSaveInstanceState(outState);
        }
    }

    public void addOnPermissions(OnPermissions onPermissions) {
        if (mOnPermissions == null) {
            mOnPermissions = new ArrayList<>();
        }

        mOnPermissions.add(onPermissions);
    }

    /**
     * Use this method only upon exiting from the app.<br/>
     * Big troubles with AFR resolution switch!
     */
    public static void invalidate() {
        sCachedDisplayMetrics = null;
        sIsInPipMode = false;
    }
}
