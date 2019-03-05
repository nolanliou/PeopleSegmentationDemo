package com.nolan.macesegmentationdemo;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.DexterError;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.PermissionRequestErrorListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.nolan.macesegmentationdemo.common.Configuration;
import com.nolan.macesegmentationdemo.common.ModelConfig;
import com.nolan.macesegmentationdemo.segmentation.ModelSelector;
import com.nolan.macesegmentationdemo.segmentation.MaskProcessor;
import com.nolan.macesegmentationdemo.common.MessageEvent;
import com.nolan.macesegmentationdemo.segmentation.Segmenter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.Button;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {
    private static final String STORAGE_DIRECTORY = "mace_segmentation_demo";
    private static final String PASCAL_LABELS_FILE = "file:///android_asset/pascal_voc_labels_list.txt";
    private int GALLERY = 1, CAMERA = 2;

    private Button btn;
    private ImageView imageView;
    private TextView infoTextView;
    private AlertDialog inProgressDialog;

    private Handler runThread;
    private int initFlag = 0; // -1 not available, 0 not ready, 1 ready.
    private Lock lock = new ReentrantLock();

    private String storagePath;
    private String photoPicPath;
    private int inputSize;
    private Bitmap inputImage;
    private Segmenter.Segmentation segmentation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestMultiplePermissions();

        btn = (Button) findViewById(R.id.btn);
        imageView = (ImageView)findViewById(R.id.image_view);
        infoTextView = (TextView)findViewById(R.id.info_text);

        HandlerThread thread = new HandlerThread("initThread");
        thread.start();
        runThread = new Handler(thread.getLooper());

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPictureDialog();
            }
        });

        inProgressDialog = new AlertDialog
                .Builder(this, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
                .setTitle("Running...")
                .setView(R.layout.progress_bar)
                .create();

        storagePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + STORAGE_DIRECTORY;
        File file = new File(storagePath);
        if (!file.exists()) {
            file.mkdirs();
        }
        initMace();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    private float scaleRatio(int inHeight, int inWidth, int outHeight, int outWidth) {
        float ratio;
        if (outHeight > inHeight || outWidth > inWidth) {
            float heightRatio = (float) outHeight / (float) inHeight;
            float widthRatio = (float) outWidth / (float) inWidth;
            ratio = Math.min(heightRatio, widthRatio);
        } else {
            float heightRatio = (float) inHeight / (float) outHeight;
            float widthRatio = (float) inWidth / (float) outWidth;
            ratio = Math.max(heightRatio, widthRatio);
        }
        return ratio;
    }


    private void initMace() {
        runThread.post(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                // select proper model
                ModelSelector modelSelector = new ModelSelector();
                ModelConfig modelConfig = modelSelector.select();
                if (modelConfig == null) {
                    Log.e("Segmentation", "Initialize failed: there is no proper model to use");
                    initFlag = -1;
                } else {
                    try {
                        MaskProcessor maskProcessor = new MaskProcessor(getAssets(),
                                PASCAL_LABELS_FILE);
                        Configuration config = new Configuration(STORAGE_DIRECTORY,
                                modelConfig, maskProcessor);
                        // Init MaceEngine
                        int status = Segmenter.instance.initialize(getAssets(), config);
                        if (status != 0) {
                            Log.e("Segmentation", "Initialize failed: create engine failed");
                            initFlag = -1;
                        } else {
                            Log.i("Segmentation", "Initialize successful");
                            initFlag = 1;
                        }
                        inputSize = modelConfig.getModelInputShape(0)[1];
                    } catch (IOException e) {
                        Log.e("Initialization", e.getMessage());
                        initFlag = -1;
                    }
                }
                lock.unlock();
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSegEvent(MessageEvent.SegEvent result) {
        if (result != null && inputImage != null) {
            final Matrix matrix = new Matrix();
            Bitmap overlayBitmap = inputImage.copy(inputImage.getConfig(), true);
            inputImage.recycle();
            if (segmentation != null && segmentation.isSuccessful()) {
                Canvas canvas = new Canvas(overlayBitmap);
                float ratio = scaleRatio(segmentation.getSegBitmap().getHeight(),
                        segmentation.getSegBitmap().getWidth(),
                        inputImage.getHeight(),
                        inputImage.getWidth());
                matrix.postScale(ratio, ratio);
                canvas.drawBitmap(segmentation.getSegBitmap(), matrix,
                        new Paint(Paint.FILTER_BITMAP_FLAG));
            } else {
                Log.i("Segmentation", "segmentation failed");
            }
            imageView.setImageBitmap(overlayBitmap);

            // show information
            Configuration config = Segmenter.instance.getConfig();
            String runTimeInfo = "<html><body>";
            if (initFlag == -1) {
                runTimeInfo += "<b>There is no proper model for the phone because of " +
                        "it's weak computing capability.<b>";
            } else if (segmentation == null || !segmentation.isSuccessful()) {
                runTimeInfo += "<b>Segmentation failed.<b>";
            } else {
                runTimeInfo += "Model name: <b>" + config.getModelConfig().getModelName() + "</b><br>";
                runTimeInfo += "Device: <b>" + config.getModelConfig().getDeviceType() + "</b><br>";
                runTimeInfo += "Input size: <b>" + Integer.toString(inputSize) + "</b><br>";
                runTimeInfo += "Preprocess time: <b>" + Long.toString(segmentation.getPreProcessTime())
                        + "ms</b><br>";
                runTimeInfo += "Run time: <b>" + Long.toString(segmentation.getInferenceTime())
                        + "ms</b><br>";
                runTimeInfo += "Postprocess time: <b>" + Long.toString(segmentation.getPostProcessTime())
                        + "ms</b><br>";
                runTimeInfo += "labels: <b>" + Arrays.toString(segmentation.getLabels()) + "</b><br>";
            }
            runTimeInfo += "</body></html>";
            infoTextView.setText(Html.fromHtml(runTimeInfo));

            if (inProgressDialog.isShowing()) {
                inProgressDialog.dismiss();
            }
        }
    }

    private void showPictureDialog() {
        AlertDialog.Builder pictureDialog = new AlertDialog.Builder(this);
        pictureDialog.setTitle("Actions");
        String[] pictureDialogItems = {
                "Select photo from gallery",
                "Capture photo from camera"};
        pictureDialog.setItems(pictureDialogItems,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                choosePhotoFromGallary();
                                break;
                            case 1:
                                takePhotoFromCamera();
                                break;
                        }
                    }
                });
        pictureDialog.show();
    }

    public void choosePhotoFromGallary() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(galleryIntent, GALLERY);
    }

    private void takePhotoFromCamera() {
        photoPicPath = storagePath + File.separator
                + Calendar.getInstance().getTimeInMillis() + ".jpg";
        File file = new File(photoPicPath);
        Uri photoUri = FileProvider.getUriForFile(
                getApplicationContext(),
                getPackageName() + ".provider",
                file);

        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

        startActivityForResult(intent, CAMERA);
    }

    private Bitmap getBitmap(Uri uri, int height, int width) throws IOException {
        InputStream input = this.getContentResolver().openInputStream(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;//optional
        BitmapFactory.decodeStream(input, null, options);
        input.close();
        if ((options.outWidth == -1) || (options.outHeight == -1))
            return null;
        options.inJustDecodeBounds = false;
        options.inSampleSize = (int)(Math.ceil(scaleRatio(options.outHeight, options.outWidth,
                height, width)));
        input = this.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
        input.close();
        return bitmap;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == this.RESULT_CANCELED) {
            return;
        }
        Uri contentURI = null;
        if (requestCode == GALLERY) {
            if (data != null) {
                contentURI = data.getData();
            }
        } else if (requestCode == CAMERA) {
            contentURI = Uri.fromFile(new File(photoPicPath));
        } else {
            return;
        }
        try {
            inputImage = getBitmap(contentURI,
                    imageView.getHeight(),
                    imageView.getWidth());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Failed!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // run segmentation on background thread
        runThread.post(new Runnable() {
            @Override
            public void run() {
                Log.i("Segmentation", "start run " + Integer.toString(initFlag));
                lock.lock();
                if (initFlag == 1) {
                    Bitmap resizedBitmap = preProcess(inputImage);
                    segmentation = Segmenter.instance.segment(resizedBitmap);
                    if (resizedBitmap != inputImage) {
                        resizedBitmap.recycle();
                    }
                }
                lock.unlock();
                EventBus.getDefault().post(new MessageEvent.SegEvent());
                Log.i("Segmentation", "end run " + Integer.toString(initFlag));
            }
        });
        if (!inProgressDialog.isShowing()) {
            inProgressDialog.show();
        }
    }

    private Bitmap preProcess(Bitmap inBitmap) {
        int width = inBitmap.getWidth();
        int height = inBitmap.getHeight();
        int newWidth = width;
        int newHeight = height;

        if (width > height && width > inputSize) {
            newWidth = inputSize;
            newHeight = (int) (height * (float) newWidth / width);
        }

        if (width > height && width <= inputSize) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return inBitmap;
        }

        if (width < height && height > inputSize) {
            newHeight = inputSize;
            newWidth = (int) (width * (float) newHeight / height);
        }

        if (width < height && height <= inputSize) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return inBitmap;
        }

        if (width == height && width > inputSize) {
            newWidth = inputSize;
            newHeight = newWidth;
        }

        if (width == height && width <= inputSize) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return inBitmap;
        }
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);

        return Bitmap.createBitmap(
                inBitmap, 0, 0, width, height, matrix, false);
    }

    private void requestMultiplePermissions() {
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).
                withErrorListener(new PermissionRequestErrorListener() {
                    @Override
                    public void onError(DexterError error) {
                        Toast.makeText(getApplicationContext(), "Some Error! ", Toast.LENGTH_SHORT).show();
                    }
                })
                .onSameThread()
                .check();
    }

}

