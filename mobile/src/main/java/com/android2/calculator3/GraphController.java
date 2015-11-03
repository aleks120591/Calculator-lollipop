package com.android2.calculator3;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.ViewTreeObserver;

import com.android2.calculator3.view.GraphView;
import com.android2.calculator3.view.GraphView.PanListener;
import com.android2.calculator3.view.GraphView.ZoomListener;
import com.xlythe.math.GraphModule;
import com.xlythe.math.GraphModule.OnGraphUpdatedListener;
import com.xlythe.math.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphController implements PanListener, ZoomListener {
    private static final String TAG = GraphController.class.getSimpleName();
    private static final int MAX_CACHE_SIZE = 10;
    private static final int GRAPH_COLOR = 0xff00bcd4; // Cyan

    private final GraphModule mGraphModule;
    private final GraphView mMainGraphView;

    private final List<AsyncTask> mGraphTasks = new ArrayList<>();

    private GraphView.Graph mMostRecentGraph;
    private AsyncTask mMostRecentGraphTask;

    private final Handler mHandler = new Handler();

    private static final Map<String, List<Point>> mCachedEquations = new LinkedHashMap<String, List<Point>>(MAX_CACHE_SIZE, 1f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Point>> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public GraphController(GraphModule module, GraphView view) {
        mGraphModule = module;
        mMainGraphView = view;

        mMainGraphView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT < 16) {
                    mMainGraphView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    mMainGraphView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                invalidateGraph();
            }
        });
        mMainGraphView.addPanListener(this);
        mMainGraphView.addZoomListener(this);
    }

    public void addNewGraph(String equation) {
        mMostRecentGraph = new GraphView.Graph(equation, GRAPH_COLOR, Collections.EMPTY_LIST);
        mMainGraphView.addGraph(mMostRecentGraph);
        layoutBeforeGraphing(mMostRecentGraph);
    }

    public void changeLatestGraph(String equation) {
        if (mMostRecentGraphTask != null) {
            mMostRecentGraphTask.cancel(true);
        }
        mMostRecentGraph.setFormula(equation);
        layoutBeforeGraphing(mMostRecentGraph);
    }

    private void layoutBeforeGraphing(final GraphView.Graph graph) {
        if (mMainGraphView.getWidth() == 0) {
            Log.d(TAG, "This view hasn't been laid out yet. Will delay graphing " + graph.getFormula());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    AsyncTask task = mMostRecentGraphTask = drawGraph(graph);
                    if (task != null) {
                        mGraphTasks.add(task);
                    }
                }
            });
        } else {
            AsyncTask task = mMostRecentGraphTask = drawGraph(graph);
            if (task != null) {
                mGraphTasks.add(task);
            }
        }
    }

    public List<GraphView.Graph> getGraphs() {
        return mMainGraphView.getGraphs();
    }

    public void remove(GraphView.Graph graph) {
        getGraphs().remove(graph);
        mMainGraphView.postInvalidate();
    }

    public AsyncTask drawGraph(final GraphView.Graph graph) {
        // If we've already asked this before, quick quick show the result again
        if (mCachedEquations.containsKey(graph.getFormula())) {
            graph.setData(mCachedEquations.get(graph.getFormula()));
            mMainGraphView.postInvalidate();
        }

        invalidateModule();
        return mGraphModule.updateGraph(graph.getFormula(), new OnGraphUpdatedListener() {
            @Override
            public void onGraphUpdated(List<Point> result) {
                mCachedEquations.put(graph.getFormula(), result);
                graph.setData(mCachedEquations.get(graph.getFormula()));
                mMainGraphView.postInvalidate();
            }
        });
    }

    public void clear() {
        mMainGraphView.getGraphs().clear();
    }

    private void invalidateModule() {
        mGraphModule.setDomain(mMainGraphView.getXAxisMin(), mMainGraphView.getXAxisMax());
        mGraphModule.setRange(mMainGraphView.getYAxisMin(), mMainGraphView.getYAxisMax());
        mGraphModule.setZoomLevel(mMainGraphView.getZoomLevel());
        }

@Override
public void panApplied() {
        invalidateGraph();
    }

    @Override
    public void zoomApplied(float level) {
        invalidateGraph();
    }

    private void invalidateGraph() {
        invalidateModule();
        for (AsyncTask task : mGraphTasks) {
            task.cancel(true);
        }
        mGraphTasks.clear();
        for (GraphView.Graph graph : getGraphs()) {
            AsyncTask task = drawGraph(graph);
            if (task != null) {
                mGraphTasks.add(task);
            }
        }
    }

    public void destroy() {
        for (AsyncTask task : mGraphTasks) {
            task.cancel(true);
        }
        mGraphTasks.clear();
    }
}
