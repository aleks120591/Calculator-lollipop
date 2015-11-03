/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android2.calculator3;

import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Toast;

import com.android2.calculator3.drawable.AnimatingDrawable;
import com.android2.calculator3.view.CalculatorPadLayout;
import com.xlythe.floatingview.AnimationFinishedListener;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;

/**
 * Controls the fab and what pages are shown / hidden.
 * */
public abstract class PanelSwitchingCalculator extends BasicCalculator {

    // instance state keys
    private static final String KEY_PANEL = NAME + "_panel";

    private enum Panel {
        Advanced, Hex, Matrix
    }

    private ViewGroup mOverlay;
    private FloatingActionButton mFab;
    private View mTray;

    @Override
    protected void initialize(Bundle savedInstanceState) {
        super.initialize(savedInstanceState);

        mOverlay = (ViewGroup) findViewById(R.id.overlay);
        mTray = findViewById(R.id.tray);
        mFab = (FloatingActionButton) findViewById(R.id.fab);

        mFab.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT >= 16) {
                    mFab.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    mFab.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                initializeLayout();
            }
        });
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTray();
                hideFab();
            }
        });
        setupTray(savedInstanceState);
        if (findViewById(R.id.pad_pager) == null) {
            showFab();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        final View advancedPad = findViewById(R.id.pad_advanced);
        final View hexPad = findViewById(R.id.pad_hex);
        final View matrixPad = findViewById(R.id.pad_matrix);

        Panel panel = null;
        if (advancedPad.getVisibility() == View.VISIBLE) {
            panel = Panel.Advanced;
        } else if (hexPad.getVisibility() == View.VISIBLE) {
            panel = Panel.Hex;
        } else if (matrixPad.getVisibility() == View.VISIBLE) {
            panel = Panel.Matrix;
        }
        outState.putSerializable(KEY_PANEL, panel);
    }

    /**
     * Sets up the height / position of the fab and tray
     *
     * Returns true if it requires a relayout
     * */
    protected void initializeLayout() {
        CalculatorPadLayout layout = (CalculatorPadLayout) findViewById(R.id.pad_advanced);
        int rows = layout.getRows();
        int columns = layout.getColumns();

        View parent = (View) mFab.getParent();
        mFab.setTranslationX((mFab.getWidth() - parent.getWidth() / columns) / 2);
        mFab.setTranslationY((mFab.getHeight() - parent.getHeight() / rows) / 2);
    }

    public void showFab() {
        mFab.setVisibility(View.VISIBLE);
        mFab.setScaleX(0.65f);
        mFab.setScaleY(0.65f);
        mFab.animate().scaleX(1f).scaleY(1f).setDuration(100).setListener(null);
        mFab.setImageDrawable(new AnimatingDrawable.Builder(getBaseContext())
                        .frames(
                                R.drawable.fab_open_1,
                                R.drawable.fab_open_2,
                                R.drawable.fab_open_3,
                                R.drawable.fab_open_4,
                                R.drawable.fab_open_5)
                        .build()
        );
        ((Animatable) mFab.getDrawable()).start();
    }

    public void hideFab() {
        if (mFab.getVisibility() == View.VISIBLE) {
            mFab.animate().scaleX(0.65f).scaleY(0.65f).setDuration(100).setListener(new AnimationFinishedListener() {
                @Override
                public void onAnimationFinished() {
                    mFab.setVisibility(View.GONE);
                }
            });
            mFab.setImageDrawable(new AnimatingDrawable.Builder(getBaseContext())
                            .frames(
                                    R.drawable.fab_close_1,
                                    R.drawable.fab_close_2,
                                    R.drawable.fab_close_3,
                                    R.drawable.fab_close_4,
                                    R.drawable.fab_close_5)
                            .build()
            );
            ((Animatable) mFab.getDrawable()).start();
        }
    }

    public void showTray() {
        revealTray(false);
    }

    public void hideTray() {
        if (mTray.getVisibility() != View.VISIBLE) {
            return;
        }
        revealTray(true);
    }

    private void revealTray(boolean reverse) {
        View sourceView = mFab;
        mTray.setVisibility(View.VISIBLE);

        final SupportAnimator revealAnimator;
        final int[] clearLocation = new int[2];
        sourceView.getLocationInWindow(clearLocation);
        clearLocation[0] += sourceView.getWidth() / 2;
        clearLocation[1] += sourceView.getHeight() / 2;
        final int revealCenterX = clearLocation[0] - mTray.getLeft();
        final int revealCenterY = clearLocation[1] - mTray.getTop();
        final double x1_2 = Math.pow(mTray.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(mTray.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(mTray.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

        float start = reverse ? revealRadius : 0;
        float end = reverse ? 0 : revealRadius;
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            // The lollipop reveal uses local cords, so use tray height / 2
            revealAnimator =
                    ViewAnimationUtils.createCircularReveal(mTray,
                            revealCenterX, mTray.getHeight() / 2, start, end);
        } else {
            // The legacy support doesn't work with gravity bottom, so use the global cords
            revealAnimator =
                    ViewAnimationUtils.createCircularReveal(mTray,
                            revealCenterX, revealCenterY, start, end);
        }
        revealAnimator.setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime));
        if (reverse) {
            revealAnimator.addListener(new AnimationFinishedListener() {
                @Override
                public void onAnimationFinished() {
                    mTray.setVisibility(View.INVISIBLE);
                }
            });
        }
        play(revealAnimator);
    }

    private void setupTray(Bundle savedInstanceState) {
        final View advancedPad = findViewById(R.id.pad_advanced);
        final View hexPad = findViewById(R.id.pad_hex);
        final View matrixPad = findViewById(R.id.pad_matrix);
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View layout = null;
                switch (v.getId()) {
                    case R.id.btn_advanced:
                        layout = advancedPad;
                        break;
                    case R.id.btn_hex:
                        layout = hexPad;
                        break;
                    case R.id.btn_matrix:
                        layout = matrixPad;
                        break;
                    case R.id.btn_close:
                        // Special case. This button just closes the tray.
                        showFab();
                        hideTray();
                        return;
                }
                showPage(layout);
                showFab();
                hideTray();
            }
        };
        View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast toast = Toast.makeText(v.getContext(), v.getContentDescription(), Toast.LENGTH_SHORT);
                // Adjust the toast so it's centered over the button
                positionToast(toast, v, getWindow(), 0, (int) (getResources().getDisplayMetrics().density * -5));
                toast.show();
                return true;
            }

            public void positionToast(Toast toast, View view, Window window, int offsetX, int offsetY) {
                // toasts are positioned relatively to decor view, views relatively to their parents, we have to gather additional data to have a common coordinate system
                Rect rect = new Rect();
                window.getDecorView().getWindowVisibleDisplayFrame(rect);
                // covert anchor view absolute position to a position which is relative to decor view
                int[] viewLocation = new int[2];
                view.getLocationInWindow(viewLocation);
                int viewLeft = viewLocation[0] - rect.left;
                int viewTop = viewLocation[1] - rect.top;

                // measure toast to center it relatively to the anchor view
                DisplayMetrics metrics = new DisplayMetrics();
                window.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.UNSPECIFIED);
                int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.UNSPECIFIED);
                toast.getView().measure(widthMeasureSpec, heightMeasureSpec);
                int toastWidth = toast.getView().getMeasuredWidth();

                // compute toast offsets
                int toastX = viewLeft + (view.getWidth() - toastWidth) / 2 + offsetX;
                int toastY = viewTop - toast.getView().getMeasuredHeight() + offsetY;

                toast.setGravity(Gravity.LEFT | Gravity.TOP, toastX, toastY);
            }
        };

        int[] buttons = {R.id.btn_advanced, R.id.btn_hex, R.id.btn_matrix, R.id.btn_close};
        for (int resId : buttons) {
            View button = mTray.findViewById(resId);
            button.setOnClickListener(listener);
            button.setOnLongClickListener(longClickListener);
        }
        Panel panel = (Panel) savedInstanceState.getSerializable(KEY_PANEL);
        if (panel != null) {
            switch (panel) {
                case Advanced:
                    showPage(advancedPad);
                    break;
                case Hex:
                    showPage(hexPad);
                    break;
                case Matrix:
                    showPage(matrixPad);
                    break;
            }
        } else {
            showPage(advancedPad);
        }
    }

    private void showPage(View layout) {
        ViewGroup baseOverlay = mOverlay;
        for (int i = 0; i < baseOverlay.getChildCount(); i++) {
            View child = baseOverlay.getChildAt(i);
            if (child != layout) {
                child.setVisibility(View.GONE);
            } else {
                child.setVisibility(View.VISIBLE);
            }
        }
    }
}
