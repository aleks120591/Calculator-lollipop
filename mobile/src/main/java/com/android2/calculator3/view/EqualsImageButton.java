/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android2.calculator3.view;

import android.content.Context;
import android.util.AttributeSet;

import com.android2.calculator3.R;

public class EqualsImageButton extends ImageButton {
    private static final int[] STATE_EQUALS = { R.attr.state_equals };
    private static final int[] STATE_NEXT = { R.attr.state_next };

    public enum State {
        EQUALS, NEXT;
    }

    private State mState = State.EQUALS;

    public EqualsImageButton(Context context) {
        super(context);
        setup();
    }

    public EqualsImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public EqualsImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    public EqualsImageButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup();
    }

    private void setup() {
        setState(State.EQUALS);
    }

    public void setState(State state) {
        mState = state;
        refreshDrawableState();
    }

    public State getState() {
        return mState;
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        if(mState == null) mState = State.EQUALS;

        switch(mState) {
            case EQUALS:
                mergeDrawableStates(state, STATE_EQUALS);
                break;
            case NEXT:
                mergeDrawableStates(state, STATE_NEXT);
                break;
        }
        return state;
    }
}
