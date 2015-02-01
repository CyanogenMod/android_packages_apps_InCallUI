/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.incallui;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;

public class GifMovieView extends View {

    private static final int DEFAULT_MOVIEW_DURATION = 1000;
    private static final String TAG = "GifMovieView";
    private int mMovieResourceId;
    private String mMovieString;
    private Movie mMovie;
    private long mMovieStart;
    private int mCurrentAnimationTime = 0;
    private float mLeft;
    private float mTop;
    private float mScale;
    private int mMeasuredMovieWidth;
    private int mMeasuredMovieHeight;
    private volatile boolean mPaused = false;
    private boolean mVisible = true;

    public GifMovieView(Context context) {
        this(context, null);
    }

    public GifMovieView(Context context, AttributeSet attrs) {
        this(context, attrs, R.styleable.CustomTheme_gifMoviewViewStyle);
    }

    public GifMovieView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setViewAttributes(context, attrs, defStyle);
    }

    @SuppressLint("NewApi")
    private void setViewAttributes(Context context, AttributeSet attrs, int defStyle) {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        final TypedArray array = context.obtainStyledAttributes(attrs,
                R.styleable.GifMoviewView, defStyle,
                R.style.Widget_GifMoviewView);

        mMovieResourceId = array.getResourceId(R.styleable.GifMoviewView_gif, -1);
        mPaused = array.getBoolean(R.styleable.GifMoviewView_paused, false);

        array.recycle();

        if (mMovieResourceId != -1) {
            mMovie = Movie.decodeFile(mMovieString);
        }
    }

    public void setMovieResource(String path) {
        this.mMovieString = path;
        Log.d(TAG,path);
        BufferedInputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(path));
        } catch (FileNotFoundException e) {
            Log.d(TAG,"FileNotFoundException");
            e.printStackTrace();
        }
        Log.d(TAG,is.toString());
        mMovie = Movie.decodeStream(is);
        requestLayout();
     }


    public void setMovie(Movie movie) {
        this.mMovie = movie;
        requestLayout();
    }

    public Movie getMovie() {
        return mMovie;
    }

    public void setMovieTime(int time) {
        mCurrentAnimationTime = time;
        invalidate();
    }

    public void setPaused(boolean paused) {
        this.mPaused = paused;


        if (!paused) {
            mMovieStart = android.os.SystemClock.uptimeMillis() - mCurrentAnimationTime;
        }

        invalidate();
    }

    public boolean isPaused() {
        return this.mPaused;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        if (mMovie != null) {
            int movieWidth = mMovie.width();
            int movieHeight = mMovie.height();

            /*
             * Calculate horizontal scaling
             */
            float scaleH = 1f;
            int measureModeWidth = MeasureSpec.getMode(widthMeasureSpec);

            if (measureModeWidth != MeasureSpec.UNSPECIFIED) {
                int maximumWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (movieWidth > maximumWidth) {
                    scaleH = (float) movieWidth / (float) maximumWidth;
                }
            }

            /*
             * calculate vertical scaling
             */
            float scaleW = 1f;
            int measureModeHeight = MeasureSpec.getMode(heightMeasureSpec);

            if (measureModeHeight != MeasureSpec.UNSPECIFIED) {
                int maximumHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (movieHeight > maximumHeight) {
                    scaleW = (float) movieHeight / (float) maximumHeight;
                }
            }

            /*
             * calculate overall scale
             */
            mScale = 2.3f / Math.max(scaleH, scaleW);

            mMeasuredMovieWidth = (int) (movieWidth * mScale);
            mMeasuredMovieHeight = (int) (movieHeight * mScale);

            setMeasuredDimension(mMeasuredMovieWidth, mMeasuredMovieHeight);

        } else {
            /*
             * No movie set, just set minimum available size.
             */
            setMeasuredDimension(getSuggestedMinimumWidth(), getSuggestedMinimumHeight());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        /*
         * Calculate left / top for drawing in center
         */
        mLeft = (getWidth() - mMeasuredMovieWidth) / 2f;
        mTop = (getHeight() - mMeasuredMovieHeight) / 2f;
        mVisible = getVisibility() == View.VISIBLE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mMovie != null) {
            if (!mPaused) {
                updateAnimationTime();
                drawMovieFrame(canvas);
                invalidateView();
            } else {
                drawMovieFrame(canvas);
            }
        }
    }

    @SuppressLint("NewApi")
    private void invalidateView() {
        if(mVisible) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                postInvalidateOnAnimation();
            } else {
                invalidate();
            }
        }
    }


    private void updateAnimationTime() {
        long now = android.os.SystemClock.uptimeMillis();

        if (mMovieStart == 0) {
            mMovieStart = now;
        }

        int dur = mMovie.duration();

        if (dur == 0) {
            dur = DEFAULT_MOVIEW_DURATION;
        }

        mCurrentAnimationTime = (int) ((now - mMovieStart) % dur);
    }


    private void drawMovieFrame(Canvas canvas) {

        mMovie.setTime(mCurrentAnimationTime);

        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.scale(mScale, mScale);
        mMovie.draw(canvas, mLeft / mScale, mTop / mScale);
        canvas.restore();
    }

    @SuppressLint("NewApi")
    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        mVisible = screenState == SCREEN_STATE_ON;
        invalidateView();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        mVisible = visibility == View.VISIBLE;
        invalidateView();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == View.VISIBLE;
        invalidateView();
    }
}
