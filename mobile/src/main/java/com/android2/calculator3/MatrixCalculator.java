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

import android.os.Bundle;
import android.view.View;

import com.android2.calculator3.view.CalculatorEditText;
import com.android2.calculator3.view.MatrixComponent;

/**
 * Adds graphing and base switching to the basic calculator.
 * */
public abstract class MatrixCalculator extends GraphingCalculator {

    private CalculatorEditText mFormulaEditText;

    protected void initialize(Bundle savedInstanceState) {
        super.initialize(savedInstanceState);
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
    }

    @Override
    public void onButtonClick(View view) {
        switch (view.getId()) {
            case R.id.matrix:
                insert(MatrixComponent.getPattern());
                mFormulaEditText.setSelection(mFormulaEditText.getSelectionStart() - MatrixComponent.getPattern().length() + 2);
                return;
            case R.id.plus_col:
            case R.id.plus_row:
            case R.id.minus_col:
            case R.id.minus_row:
                return;
        }
        super.onButtonClick(view);
    }
}
