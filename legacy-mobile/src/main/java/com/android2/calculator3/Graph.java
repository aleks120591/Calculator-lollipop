/**
 * Copyright (C) 2009, 2010 SC 4ViewSoft SRL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android2.calculator3;

import android.content.Context;
import android.util.Log;
import android.view.ViewTreeObserver;

import com.android2.calculator3.view.GraphView;
import com.xlythe.engine.theme.Theme;
import com.xlythe.math.Point;

import java.util.LinkedList;
import java.util.List;

public class Graph {
    private final Logic mLogic;
    private GraphView mGraphView;
    private List<Point> mData = new LinkedList<Point>();

    public Graph(Logic l) {
        mLogic = l;
    }

    public GraphView createGraph(Context context) {
        mLogic.setGraph(this);

        mGraphView = new GraphView(context);
        mGraphView.setPanListener(new GraphView.PanListener() {
            @Override
            public void panApplied() {
                mLogic.setDomain(mGraphView.getXAxisMin(), mGraphView.getXAxisMax());
                mLogic.setRange(mGraphView.getYAxisMin(), mGraphView.getYAxisMax());
                mLogic.graph();
            }
        });
        mGraphView.setZoomListener(new GraphView.ZoomListener() {
            @Override
            public void zoomApplied(float level) {
                mLogic.setZoomLevel(level);
                mLogic.graph();
            }
        });

        mGraphView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mLogic.setDomain(mGraphView.getXAxisMin(), mGraphView.getXAxisMax());
                mLogic.setRange(mGraphView.getYAxisMin(), mGraphView.getYAxisMax());
            }
        });
        mGraphView.setBackgroundColor(Theme.getColor(context, R.color.graph_background));
        mGraphView.setTextColor(Theme.getColor(context, R.color.graph_labels_color));
        mGraphView.setGridColor(Theme.getColor(context, R.color.graph_grid_color));
        mGraphView.setGraphColor(Theme.getColor(context, R.color.graph_color));
        return mGraphView;
    }

    public List<Point> getData() {
        return mData;
    }

    public void setData(List<Point> data) {
        mData = data;
        mGraphView.setData(mData);
    }
}
