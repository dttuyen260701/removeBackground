// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.codelab.mlkit;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import java.util.Stack;

import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.objects.DetectedObject;
//import com.google.mlkit.vision.objects.ObjectDetection;
//import com.google.mlkit.vision.objects.ObjectDetector;
//import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
//import com.google.mlkit.vision.objects.defaults.PredefinedCategory;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = "MainActivity";
    private ImageView mImageView, easy_view;
    private Button mTextButton, btn_zoom, btn_save;
    private Button mFaceButton, btn_Restore, btn_Remove, btn_reset;
    private int REQUEST_CODE_FOLDER = 456;
    private static final int ACTION_REQUEST_EDITIMAGE = 9;
    private Bitmap mSelectedImage, mask;
    private SeekBar seekBar, seekbar_size;
    private ImageView btnUndo, btnRedo;
    private ConstraintLayout constran;
    private ArrayList<Integer> array_for = new ArrayList<>();
    private int stroke_size = 10;
    private boolean mode = false; // false la xoa
    private boolean zoom = false;
    private int zoom_size = 1;
    private int old_zoom = 1;
    private Stack<Bitmap> undo, redo;
    private long last_click = 0;
    private float lastX = 0, lastY = 0, last_Hori_Bias = 0, last_Ver_Bias = 0;
    private int max_zoom = 7, min_zoom = 1;
//    private int width = 0;
//    private int height = 0;

    /**
     * Number of results to show in the UI.
     */
    private static final int RESULTS_TO_SHOW = 3;
    /**
     * Dimensions of inputs.
     */
    private static final int DIM_IMG_SIZE_X = 224;
    private static final int DIM_IMG_SIZE_Y = 224;

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float>
                                o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        undo = new Stack<>();
        redo = new Stack<>();
        btn_zoom = findViewById(R.id.btn_zoom);
        constran = findViewById(R.id.constran);
        mImageView = findViewById(R.id.imageview);
        btn_Restore = (Button) findViewById(R.id.btn_Restore);
        btn_Remove = (Button) findViewById(R.id.btn_Remove);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekbar_size = (SeekBar) findViewById(R.id.seekbar_size);
        mTextButton = findViewById(R.id.button_text);
        mFaceButton = findViewById(R.id.button_face);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btn_reset = findViewById(R.id.btn_reset);
        easy_view = findViewById(R.id.easy_view);
        btn_save = findViewById(R.id.btn_save);

        btn_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoom_size = 1;
                stroke_size = seekbar_size.getProgress();
                ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(mImageView.getWidth() * zoom_size / old_zoom, mImageView.getHeight() * zoom_size / old_zoom);
                mImageView.setLayoutParams(layoutParams);
                easy_view.setLayoutParams(layoutParams);
                ConstraintSet cs = new ConstraintSet();
                cs.clone(constran);
                cs.connect(mImageView.getId(), ConstraintSet.BOTTOM, constran.getId(), ConstraintSet.BOTTOM);
                cs.connect(mImageView.getId(), ConstraintSet.END, constran.getId(), ConstraintSet.END);
                cs.connect(mImageView.getId(), ConstraintSet.START, constran.getId(), ConstraintSet.START);
                cs.connect(mImageView.getId(), ConstraintSet.TOP, constran.getId(), ConstraintSet.TOP);
                cs.setHorizontalBias(mImageView.getId(), last_Hori_Bias);
                cs.setVerticalBias(mImageView.getId(), last_Ver_Bias);
                cs.setDimensionRatio(mImageView.getId(), "1:1");

                cs.connect(easy_view.getId(), ConstraintSet.BOTTOM, constran.getId(), ConstraintSet.BOTTOM);
                cs.connect(easy_view.getId(), ConstraintSet.END, constran.getId(), ConstraintSet.END);
                cs.connect(easy_view.getId(), ConstraintSet.START, constran.getId(), ConstraintSet.START);
                cs.connect(easy_view.getId(), ConstraintSet.TOP, constran.getId(), ConstraintSet.TOP);
                cs.setHorizontalBias(easy_view.getId(), last_Hori_Bias);
                cs.setVerticalBias(easy_view.getId(), last_Ver_Bias);
                cs.setDimensionRatio(easy_view.getId(), "1:1");
                cs.applyTo(constran);
                old_zoom = zoom_size;
            }
        });

        btn_Remove.setBackgroundColor(getColor(android.R.color.holo_green_dark));
        btn_Remove.setTextColor(getColor(android.R.color.background_light));

        btn_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SaveImage saveImage = new SaveImage(((BitmapDrawable)mImageView.getDrawable()).getBitmap());
                saveImage.execute();
            }
        });

        btn_zoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zoom = true;
                btn_Remove.setBackgroundColor(getColor(android.R.color.background_light));
                btn_Remove.setTextColor(getColor(android.R.color.background_dark));
                btn_Restore.setBackgroundColor(getColor(android.R.color.background_light));
                btn_Restore.setTextColor(getColor(android.R.color.background_dark));
                btn_zoom.setBackgroundColor(getColor(android.R.color.holo_green_dark));
                btn_zoom.setTextColor(getColor(android.R.color.background_light));
            }
        });

        btn_Remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btn_Restore.setBackgroundColor(getColor(android.R.color.background_light));
                btn_Restore.setTextColor(getColor(android.R.color.background_dark));
                btn_Remove.setBackgroundColor(getColor(android.R.color.holo_green_dark));
                btn_Remove.setTextColor(getColor(android.R.color.background_light));
                btn_zoom.setBackgroundColor(getColor(android.R.color.background_light));
                btn_zoom.setTextColor(getColor(android.R.color.background_dark));
                zoom = false;
                mode = false;
            }
        });

        btn_Restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btn_Remove.setBackgroundColor(getColor(android.R.color.background_light));
                btn_Remove.setTextColor(getColor(android.R.color.background_dark));
                btn_Restore.setBackgroundColor(getColor(android.R.color.holo_green_dark));
                btn_Restore.setTextColor(getColor(android.R.color.background_light));
                btn_zoom.setBackgroundColor(getColor(android.R.color.background_light));
                btn_zoom.setTextColor(getColor(android.R.color.background_dark));
                zoom = false;
                mode = true;
            }
        });

        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                redo.push(((BitmapDrawable)mImageView.getDrawable()).getBitmap());
                Bitmap temp = undo.pop();
                mImageView.setImageBitmap(temp);
                btnRedo.setEnabled(true);
                if(undo.size() == 0){
                    btnUndo.setEnabled(false);
                }
            }
        });

        btnRedo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                undo.push(((BitmapDrawable)mImageView.getDrawable()).getBitmap());
                Bitmap temp = redo.pop();
                mImageView.setImageBitmap(temp);
                btnUndo.setEnabled(true);
                if(redo.size() == 0){
                    btnRedo.setEnabled(false);
                }
            }
        });

        btnUndo.setEnabled(false);
        btnRedo.setEnabled(false);

        seekbar_size.setMax(100);
        seekbar_size.setMin(10);
        seekbar_size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                stroke_size = seekbar_size.getProgress()/zoom_size;
            }
        });
        seekBar.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            seekBar.setMin(1);
        }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                runObjectSelfie((float)seekBar.getProgress()/100);
            }
        });
        mTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runObjectSelfie(1);
            }
        });
        mFaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityIfNeeded(Intent.createChooser(intent,
                        "Choose picture"), REQUEST_CODE_FOLDER);
            }
        });
        Spinner dropdown = findViewById(R.id.spinner);
        String[] items = new String[]{"Test Image 1 (Text)", "Test Image 2 (Face)", "3", "4"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout
                .simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);
        dropdown.setOnItemSelectedListener(this);
        easy_view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(zoom) {
                            if(SystemClock.elapsedRealtime() - last_click < 200) {
                                zoom_size = (zoom_size == max_zoom) ? min_zoom  : zoom_size + 2;
                                stroke_size = (seekbar_size.getProgress() / zoom_size < 10) ? 10 : Math.round(seekbar_size.getProgress() / zoom_size);
                                ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(mImageView.getWidth() * zoom_size / old_zoom, mImageView.getHeight() * zoom_size / old_zoom);
                                mImageView.setLayoutParams(layoutParams);
                                easy_view.setLayoutParams(layoutParams);
                                ConstraintSet cs = new ConstraintSet();

                                cs.clone(constran);
                                cs.connect(mImageView.getId(), ConstraintSet.BOTTOM, constran.getId(), ConstraintSet.BOTTOM);
                                cs.connect(mImageView.getId(), ConstraintSet.END, constran.getId(), ConstraintSet.END);
                                cs.connect(mImageView.getId(), ConstraintSet.START, constran.getId(), ConstraintSet.START);
                                cs.connect(mImageView.getId(), ConstraintSet.TOP, constran.getId(), ConstraintSet.TOP);
                                last_Hori_Bias = event.getX() /old_zoom / constran.getWidth();
                                last_Ver_Bias = event.getY()/ old_zoom / constran.getHeight();
                                cs.setHorizontalBias(mImageView.getId(), last_Hori_Bias);
                                cs.setVerticalBias(mImageView.getId(), last_Ver_Bias);
                                cs.setDimensionRatio(mImageView.getId(), "1:1");

                                cs.connect(easy_view.getId(), ConstraintSet.BOTTOM, constran.getId(), ConstraintSet.BOTTOM);
                                cs.connect(easy_view.getId(), ConstraintSet.END, constran.getId(), ConstraintSet.END);
                                cs.connect(easy_view.getId(), ConstraintSet.START, constran.getId(), ConstraintSet.START);
                                cs.connect(easy_view.getId(), ConstraintSet.TOP, constran.getId(), ConstraintSet.TOP);
                                cs.setHorizontalBias(easy_view.getId(), last_Hori_Bias);
                                cs.setVerticalBias(easy_view.getId(), last_Ver_Bias);
                                cs.setDimensionRatio(easy_view.getId(), "1:1");
                                cs.applyTo(constran);
                                old_zoom = zoom_size;
                                last_click = 0;

                                return true;
                            }
                            lastX = event.getX();
                            lastY = event.getY();
                            last_click = SystemClock.elapsedRealtime();
                            return true;
                        } else {
                            undo.push(((BitmapDrawable) mImageView.getDrawable()).getBitmap());
                            btnUndo.setEnabled(true);
                        }
                    case MotionEvent.ACTION_MOVE:
                        if(!zoom){
                            eraser_view(event.getX()/zoom_size, event.getY()/zoom_size, stroke_size);
                            if(mode) {
                                reStore(event.getX()/zoom_size, event.getY()/zoom_size, stroke_size);
                            } else {
                                reMove(event.getX()/zoom_size, event.getY()/zoom_size, stroke_size);
                            }
                        } else {
                            float hori_Bias = (event.getX() - lastX)/zoom_size/constran.getWidth();
                            float ver_Bias = (event.getY() - lastY)/zoom_size/constran.getHeight();
                            Log.e("BIAS", "onTouch: " + hori_Bias + " - " + ver_Bias);
                            if(last_Hori_Bias - hori_Bias < 0)
                                last_Hori_Bias = 0;
                            else if(last_Hori_Bias - hori_Bias > 1)
                                last_Hori_Bias = 1;
                            else
                                last_Hori_Bias = last_Hori_Bias - hori_Bias;
                            if(last_Ver_Bias - ver_Bias < 0)
                                last_Ver_Bias = 0;
                            else if(last_Ver_Bias - ver_Bias > 1)
                                last_Ver_Bias = 1;
                            else
                                last_Ver_Bias = last_Ver_Bias - ver_Bias;
                            ConstraintSet cs = new ConstraintSet();
                            cs.clone(constran);

                            cs.setHorizontalBias(mImageView.getId(), (float) last_Hori_Bias);
                            cs.setVerticalBias(mImageView.getId(), (float) last_Ver_Bias);

                            cs.setHorizontalBias(easy_view.getId(), (float) last_Hori_Bias);
                            cs.setVerticalBias(easy_view.getId(), (float) last_Ver_Bias);

                            cs.applyTo(constran);
                            lastX = event.getX();
                            lastY = event.getY();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:Bitmap oldbitmap = ((BitmapDrawable)mImageView.getDrawable()).getBitmap();
                        int width = oldbitmap.getWidth();
                        int height = oldbitmap.getHeight();
                        easy_view.setImageBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
                        return true;
                    default:
                        return false;
                }
            }
        });

    }

    private void eraser_view(float X, float Y, int size){
        Bitmap oldbitmap = ((BitmapDrawable)mImageView.getDrawable()).getBitmap();
        int width = oldbitmap.getWidth();
        int height = oldbitmap.getHeight();
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        background.getPixels(pixels, 0, width, 0, 0, width, height);
        int start_x = (int)X - size,
            end_x = (int)X + size,
            start_y = (int)Y - size,
            end_y = (int)Y + size;
        if((int)X - size < 0){
            start_x = 0;
        }
        if((int)X + size > mSelectedImage.getWidth()){
            end_x = mSelectedImage.getWidth();
        }
        if((int)Y - size < 0){
            start_y = 0;
        }
        if((int)Y + size > mSelectedImage.getHeight()){
            end_y = mSelectedImage.getHeight();
        }
        for(int y = start_y; y < end_y; ++y){
            for(int x = start_x; x < end_x; ++x){
                double value = Math.abs(Math.pow((y - Y), 2) + Math.pow((x - X), 2));
                if(value <=  Math.pow(size, 2))
                    pixels[y*width + x] = Color.RED;
            }
        }
        Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        newBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        easy_view.setImageBitmap(newBitmap);
    }

    private void reStore(float X, float Y, int size){
        Bitmap oldbitmap = ((BitmapDrawable)mImageView.getDrawable()).getBitmap();
        int width = oldbitmap.getWidth();
        int height = oldbitmap.getHeight();
        int[] pixels = new int[width * height];
        oldbitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] pixels_1 = new int[width * height];
        int start_x = (int)X - size,
            end_x = (int)X + size,
            start_y = (int)Y - size,
            end_y = (int)Y + size;
        if((int)X - size < 0){
            start_x = 0;
        }
        if((int)X + size > mSelectedImage.getWidth()){
            end_x = mSelectedImage.getWidth();
        }
        if((int)Y - size < 0){
            start_y = 0;
        }
        if((int)Y + size > mSelectedImage.getHeight()){
            end_y = mSelectedImage.getHeight();
        }
        mSelectedImage.getPixels(pixels_1, 0, width, 0, 0, width, height);
        for(int y = start_y; y < end_y; ++y){
            for(int x = start_x; x < end_x; ++x){
                if(Math.abs(Math.pow((y - Y),2) + Math.pow((x - X),2)) <= Math.pow(size, 2))
                    pixels[y*width + x] =
                            pixels_1[y*width + x];
            }
        }
        Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        newBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        mImageView.setImageBitmap(newBitmap);
    }

    private void reMove(float X, float Y, int size){
        Bitmap oldbitmap = ((BitmapDrawable)mImageView.getDrawable()).getBitmap();
        int width = oldbitmap.getWidth();
        int height = oldbitmap.getHeight();
        int[] pixels = new int[width * height];
        oldbitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int start_x = (int)X - size,
                end_x = (int)X + size,
                start_y = (int)Y - size,
                end_y = (int)Y + size;
        if((int)X - size < 0){
            start_x = 0;
        }
        if((int)X + size > mSelectedImage.getWidth()){
            end_x = mSelectedImage.getWidth();
        }
        if((int)Y - size < 0){
            start_y = 0;
        }
        if((int)Y + size > mSelectedImage.getHeight()){
            end_y = mSelectedImage.getHeight();
        }
        for(int y = start_y; y < end_y; ++y){
            for(int x = start_x; x < end_x; ++x){
                if(Math.abs(Math.pow((y - Y),2) + Math.pow((x - X),2)) <= Math.pow(size, 2))
                    pixels[y*width + x] = Color.TRANSPARENT;
            }
        }
        Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        newBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        mImageView.setImageBitmap(newBitmap);
    }

    private void runObjectSelfie(final float thread) {
        SelfieSegmenterOptions options =
                new SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
//                        .enableRawSizeMask()
                        .build();
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        Segmenter segmenter = Segmentation.getClient(options);
        Task<SegmentationMask> result =
                segmenter.process(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<SegmentationMask>() {
                                    @Override
                                    public void onSuccess(SegmentationMask mask) {
                                        ByteBuffer mask_rs_1 = mask.getBuffer();
                                        int maskWidth = mask.getWidth();
                                        int maskHeight = mask.getHeight();
                                        array_for.clear();
                                        for (int y = 0; y < maskHeight; y++) {
                                            for (int x = 0; x < maskWidth; x++) {
                                                // Gets the confidence of the (x,y) pixel in the mask being in the foreground.
                                                float foregroundConfidence = mask_rs_1.getFloat();
                                                if(foregroundConfidence < thread)
                                                    array_for.add(y*maskWidth + x);
//                                                Log.e("TTT", "onSuccess: " + foregroundConfidence);
//                                                if( [x*maskHeight +y] < foregroundConfidence)

//                                                Log.e("MMM", "runObjectSelfie: " + x + "-" + y  + "-" + foregroundConfidence);
                                            }
                                        }
                                        mask_rs_1.rewind();
                                        Bitmap rs = Bitmap.createBitmap(mSelectedImage.getWidth(), mSelectedImage.getHeight(),
                                                Bitmap.Config.ARGB_8888);
                                        rs.copyPixelsFromBuffer(mask_rs_1);
                                        MainActivity.this.mask = rs;
                                        UpdateAutomaticPixelClearingTask task = new UpdateAutomaticPixelClearingTask(mSelectedImage);
                                        task.execute();
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(MainActivity.this, "TOI", Toast.LENGTH_SHORT).show();
                                    }
                                });

    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Functions for loading images from app assets.

    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.

    // Gets the targeted width / height.

    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        switch (position) {
            case 0:
                mSelectedImage = getBitmapFromAsset(this, "Please_walk_on_the_grass.jpg");
                break;
            case 1:
                // Whatever you want to happen when the thrid item gets selected
                mSelectedImage = getBitmapFromAsset(this, "grace_hopper.jpg");
                break;
            case 2:
                mSelectedImage = getBitmapFromAsset(this, "ronaldo.jpg");
                break;
            case 3:
                mSelectedImage = getBitmapFromAsset(this, "test2.jpg");
                break;
        }
        if (mSelectedImage != null) {
            // Get the dimensions of the View

            // Determine how much to scale down the image

//            Bitmap resizedBitmap =
//                    Bitmap.createScaledBitmap(
//                            mSelectedImage,
//                            (int) (mSelectedImage.getWidth() / scaleFactor),
//                            (int) (mSelectedImage.getHeight() / scaleFactor),
//                            true);

            mImageView.setImageBitmap(mSelectedImage);
        }
    }

    class UpdateAutomaticPixelClearingTask extends AsyncTask<Void, Void, Bitmap> {

        private Bitmap bitmap;
        private int accuracy = 0;

        public UpdateAutomaticPixelClearingTask(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
//            //Toast.makeText(mTextImageFragment.getContext(), "Please wait a few seconds", Toast.LENGTH_LONG);
//            mTextImageFragment.updateStickerItem(bitmap, 100);
        }

        @Override
        protected Bitmap doInBackground(Void... Void) {
            Bitmap oldBitmap = mask;
            Bitmap test = bitmap;

            int width = oldBitmap.getWidth();
            int height = oldBitmap.getHeight();
//            int[] pixels = new int[width * height];
//            oldBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            int[] pixels_1 = new int[width * height];
            test.getPixels(pixels_1, 0, width, 0, 0, width, height);


            // iteration through pixels
            for(int i : array_for){
                pixels_1[i] = Color.TRANSPARENT;
            }

            Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            newBitmap.setPixels(pixels_1, 0, width, 0, 0, width, height);
            return newBitmap;
        }

        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            mImageView.setImageBitmap(result);
        }
    }

    class SaveImage extends AsyncTask<Void, Void, Bitmap> {

        private Bitmap bitmap;

        public SaveImage(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
//            //Toast.makeText(mTextImageFragment.getContext(), "Please wait a few seconds", Toast.LENGTH_LONG);
//            mTextImageFragment.updateStickerItem(bitmap, 100);
        }

        @Override
        protected Bitmap doInBackground(Void... Void) {
            Bitmap oldBitmap = bitmap;

            int width = oldBitmap.getWidth();
            int height = oldBitmap.getHeight();
            int[] pixels = new int[width * height];
            oldBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            int pixel;

            int firstX = 999999, firstY = 999999, lastX = 0, lastY = 0;
//
            // iteration through pixels
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    // get current index in 2D-matrix
                    int index = y * width + x;
                    pixel = pixels[index];
                    if (pixel != Color.TRANSPARENT) {
                        firstX = (x < firstX) ? x : firstX;
                        firstY = (y < firstY) ? y : firstY;
                        lastX = (x > lastX) ? x : lastX;
                        lastY = (y > lastY) ? y : lastY;
                    }
                }
            }

            Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            newBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            Bitmap resizedBmp = Bitmap.createBitmap(newBitmap, firstX, firstY, lastX - firstX, lastY - firstY);
            return resizedBmp;
        }

        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            mImageView.setImageBitmap(result);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }

    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream is;
        Bitmap bitmap = null;
        try {
            is = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    public Bitmap ConvetrSameSize(Bitmap originalImage, int mDisplayWidth, int mDisplayHeight, float x, float y) {
        Bitmap bitmap = originalImage;
        //Bitmap cs = bgBitmap.copy(bgBitmap.getConfig(), true);
        Bitmap cs = Bitmap.createBitmap(mImageView.getWidth(), mImageView.getHeight(), Bitmap.Config.ARGB_8888);
//        new Canvas(FastBlur.doBlur(cs, 25, true)).drawBitmap(newscaleBitmap(bitmap, mDisplayWidth, mDisplayHeight, x, y), 0.0f, 0.0f, new Paint());
        // cs = FastBlur.doBlur(cs,  1, true);
        new Canvas(cs).drawBitmap(newscaleBitmap(bitmap, mDisplayWidth, mDisplayHeight, x, y), 0.0f, 0.0f, new Paint());
        return cs;
    }

    private Bitmap newscaleBitmap(Bitmap originalImage, int width, int height, float x, float y) {
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float originalWidth = (float) originalImage.getWidth();
        float originalHeight = (float) originalImage.getHeight();
        Canvas canvas = new Canvas(background);
        float scale = ((float) width) / originalWidth;
        float scaleY = ((float) height) / originalHeight;
        float xTranslation = 0.0f;
        float yTranslation = (((float) height) - (originalHeight * scale)) / 2.0f;
        if (yTranslation < 0.0f) {
            yTranslation = 0.0f;
            scale = ((float) height) / originalHeight;
            xTranslation = (((float) width) - (originalWidth * scaleY)) / 2.0f;
        }
        Matrix transformation = new Matrix();
        transformation.postTranslate(xTranslation * x, yTranslation + y);
//        Log.d("translation", "xTranslation :" + xTranslation + " yTranslation :" + yTranslation);
        transformation.preScale(scale, scale);
        canvas.drawBitmap(originalImage, transformation, new Paint());
        return background;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //chọn hình trong file;
        if(requestCode == REQUEST_CODE_FOLDER && resultCode == RESULT_OK && data != null){
            Uri uri = data.getData();
            try {
                undo.clear();
                redo.clear();
                mSelectedImage = ((BitmapDrawable)mImageView.getDrawable()).getBitmap();
                InputStream inputStream = getContentResolver().openInputStream(uri);
                mSelectedImage  = BitmapFactory.decodeStream(inputStream);
//                Bitmap temp2 = scaleCenterCrop(mSelectedImage, mImageView.getWidth(), mImageView.getHeight());
                mSelectedImage = ConvetrSameSize(mSelectedImage, mImageView.getWidth(), mImageView.getHeight(), 1, 0.0f);
                mImageView.setImageBitmap(mSelectedImage);
                Bitmap oldbitmap = ((BitmapDrawable)mImageView.getDrawable()).getBitmap();

            } catch (FileNotFoundException e) {
                Toast.makeText(MainActivity.this, "Something wrong! You can not use this photo!", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }else if (requestCode == ACTION_REQUEST_EDITIMAGE && resultCode == RESULT_OK){
        }
    }
}
