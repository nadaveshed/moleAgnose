package omri.opencvdemo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

import android.content.DialogInterface;
import android.content.Intent;

import android.database.Cursor;
import android.graphics.Bitmap;

import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import android.provider.MediaStore;

import android.provider.Settings;
import android.support.v4.content.FileProvider;

import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;

import org.opencv.core.Core;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;

import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;


import org.opencv.imgproc.Imgproc;
import org.opencv.android.OpenCVLoader;
import org.opencv.imgproc.Moments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import com.bumptech.glide.Glide;
import com.theartofdev.edmodo.cropper.CropImage;

public class MainActivity extends AppCompatActivity {


    private ImageView mImageView;
    private Bitmap currentBitmap, calculatedBitmap, calculatedHistogram;
    private ImageButton browse_btn, camera_btn, analyze_btn, histogram_btn;
    private static final String TAG = "MainActivity";
    private String currentPhotoPath, currentGalleryPath;
    private static final int ACTION_IMAGE_CAPTURE = 1;
    private static final int ACTION_GET_CONTENT = 2;
    private static final int REQUEST_CAMERA = 100;
    private static int STATE = 3;
    private static final int SAMPLE_BLOB = 3;
    private static final int SAMPLE_SKIN = 4;
    private Uri photoURI;
    private ProgressBar pb;
    private Point seed, skin;
    private double[] seedRGB, skinRGB, seedAvgColor, skinAvgColor;
    private double threshold;
    private static int SCALING_DIVIDER = 2;
    private String imageName = "";
    private  double diff=0;


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "static initializer: failed to load openCV");
        } else {
            Log.d(TAG, "static initializer: openCV loaded");
        }
    }


    /*----------------------------------------------------------------------------*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pb = (ProgressBar) findViewById(R.id.progressbar_loading);
        pb.setVisibility(View.GONE);
        mImageView = (ImageView) findViewById(R.id.pic1);
        analyze_btn = (ImageButton) findViewById(R.id.analyze_btn);
        analyze_btn.setEnabled(false);
        histogram_btn = (ImageButton) findViewById(R.id.hostogram_btn);
        histogram_btn.setEnabled(false);


        //handling permissions in case of SDK >=23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }


    /*----------------------------------------------------------------------------*/

    /**
     * Returns a unique file for an image.
     *
     * @return the created file for saving an image.
     * @throws IOException in case of failure in file allocation
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        imageName = imageFileName;
        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    /*----------------------------------------------------------------------------*/

    /**
     * Creates an image capture intent, launching camera and after taking picture,
     * stores the image on internal storage.
     *
     * @param v current view.
     */
    public void launchCamera(View v) {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(getApplicationContext(), R.string.create_file_error, Toast.LENGTH_LONG).show();
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "com.omri.opencvdemo.fileprovider", photoFile);
                getBaseContext().grantUriPermission("com.omri.opencvdemo", photoURI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, ACTION_IMAGE_CAPTURE);
            }
        }
    }

     /*----------------------------------------------------------------------------*/


    public String getRealPathFromURI(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
/*-------------------------------------------------------------------*/

    /**
     * For handling different intents
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {

            histogram_btn.setEnabled(true);


            switch (requestCode) {
                case ACTION_IMAGE_CAPTURE: //in case user is taking a picture

                    try {

                        Intent intent = CropImage.activity(photoURI).getIntent(getBaseContext());
                        startActivityForResult(intent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.create_file_error, Toast.LENGTH_LONG).show();
                    }
                    break;

                case ACTION_GET_CONTENT: //in case user is loading picture from gallery
                    try {
                        photoURI = data.getData();
                        imageName = getRealPathFromURI(photoURI);
                        imageName = imageName.replaceFirst(".*/(\\w+).*", "$1");
                        CropImage.activity(photoURI).start(this);
                        //setPic(currentBitmap, photoURI);
                        //startActivityForResult(intent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.load_image_error, Toast.LENGTH_LONG).show();
                    }
                    break;
                case CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE: //cropping image
                    CropImage.ActivityResult result = CropImage.getActivityResult(data);
                    Uri resultUri = result.getUri();
                    photoURI = resultUri;
                    try {
                        Bitmap bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                        currentBitmap = bm;
                        setPic(bm, resultUri);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.crop_image_error, Toast.LENGTH_LONG).show();
                    }
                    break;


            }
        }
    }

    /*----------------------------------------------------------------------------*/

    /**
     * This method asks the user to click on relevant location on screen.
     * After that, saves the coordinates of that location.
     */
    private void getBlobCoordinates() {

        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this)
                .setMessage(MainActivity.this.getString(R.string.sample_from_blob))
                .setPositiveButton("GOT IT", null).show();

        //setting a listener on imageview for sampling points
        mImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {


                //for sampling the point from not (not blob)
                if (STATE == SAMPLE_SKIN) {
                    skin = new Point();
                    DecodeActionDownEvent(view, motionEvent, skin);
                    try {
                        mImageView.buildDrawingCache();
                        Bitmap bitmap = mImageView.getDrawingCache();
                        int pixel = bitmap.getPixel((int) skin.x, (int) skin.y);
                        skinRGB = new double[]{Color.red(pixel), Color.green(pixel), Color.blue(pixel)};
                        Log.i(TAG, "seed - r:" + seedRGB[2] + " ,g:" + seedRGB[1] + " b:" + seedRGB[0]);
                        Log.i(TAG, "skin - r:" + skinRGB[2] + " ,g:" + skinRGB[1] + " b:" + skinRGB[0]);
                        mImageView.setOnTouchListener(null);
                        skinRGB = null;
                        seedRGB = null;

                        STATE = SAMPLE_BLOB;
                        // drawPointsOnImage();


                        seedAvgColor = PixelCalc.avgSurround(seed, bitmap);
                        skinAvgColor = PixelCalc.avgSurround(skin, bitmap);
                        Log.i(TAG, "avgSeed - r:" + (int) seedAvgColor[0] + " ,g:" + (int) seedAvgColor[1] + " b:" + (int) seedAvgColor[2]);
                        Log.i(TAG, "avgSkin - r:" + (int) skinAvgColor[0] + " ,g:" + (int) skinAvgColor[1] + " b:" + (int) skinAvgColor[2]);

                        threshold = PixelCalc.calcDistance(seedAvgColor, skinAvgColor) / SCALING_DIVIDER;
                        Log.i(TAG, "Threshold is: " + threshold);
                        ImageButton b = (ImageButton) findViewById(R.id.analyze_btn);
                        b.setEnabled(false);

                        //uncomment this section to process image
                        MyAsyncTask work = new MyAsyncTask();
                        calculatedBitmap = currentBitmap;
                        Bitmap[] array = {calculatedBitmap, bitmap};
                        work.execute(array);


                        return false;

                    } catch (Exception e) {
                        e.printStackTrace();
                        //Toast.makeText(getBaseContext(), "Error while sampling colors", Toast.LENGTH_LONG).show();
                    }

                    return false;
                }

                //for sampling the point from suspicious blob
                if (STATE == SAMPLE_BLOB) {
                    seed = new Point();
                    DecodeActionDownEvent(view, motionEvent, seed);
                    try {
                        mImageView.buildDrawingCache();
                        Bitmap bitmap = mImageView.getDrawingCache();
                        int pixel = bitmap.getPixel((int) seed.x, (int) seed.y);
                        seedRGB = new double[]{Color.red(pixel), Color.green(pixel), Color.blue(pixel)};
                        STATE = SAMPLE_SKIN;
                        alertDialog.setMessage(MainActivity.this.getString(R.string.sample_from_skin));
                        alertDialog.show();

                        return false;
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Toast.makeText(getBaseContext(), "Error while sampling colors", Toast.LENGTH_LONG).show();
                    }

                }
                return false;

            }
        });
    }

    /*-----------------------------------------------------------------------------------*/

    /**
     * Calculate coordinates relative to Imageview based on click location.
     * After calculation, this method set the X,Y coordinates correspond to the given Point object.
     *
     * @param v  Current view.
     * @param ev Relevant click event.
     * @param p  A Point object that it's coordinates will update after calculation.
     */
    private void DecodeActionDownEvent(View v, MotionEvent ev, Point p) {
        Matrix inverse = new Matrix();
        mImageView.getImageMatrix().invert(inverse);
        float[] touchPoint = new float[]{ev.getX(), ev.getY()};
        // inverse.mapPoints(touchPoint);
        p.x = touchPoint[0];
        p.y = touchPoint[1];


    }

    /*------------------------------------------------------------------------------------------------------*/

    /**
     * This method draws the two clicked points by user.
     * In red color it is the suspicious blob.
     * In blue color it is the "clean" skin.
     */
    private void drawPointsOnImage() {

        Bitmap bitmap;
        bitmap = currentBitmap.copy(currentBitmap.getConfig(), true);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(bitmap, 0, 0, null);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawCircle((float) seed.x, (float) seed.y, 20, paint);
        paint.setColor(Color.BLUE);
        canvas.drawCircle((float) skin.x, (float) skin.y, 20, paint);
        mImageView.setImageDrawable(new BitmapDrawable(getBaseContext().getResources(), bitmap));
    }

    /*----------------------------------------------------------------------------*/

    /**
     * Setting picture using Glide library in the ImageView based on given Bitmap.
     *
     * @param bm        Given Bitmap to present.
     * @param resultUri Given Uri of an image to present
     */
    private void setPic(Bitmap bm, Uri resultUri) {

        mImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mImageView = (ImageView) findViewById(R.id.pic1);
        Glide
                .with(getBaseContext())
                .asBitmap().load(resultUri)
                .into(mImageView);
        analyze_btn.setEnabled(true);
    }


    /*----------------------------------------------------------------------------*/

    /**
     * Firing new  intent for browsing images from device.
     *
     * @param v Provided view.
     */
    public void onBrowseClick(View v) {

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        TextView t =(TextView)findViewById(R.id.textView);
        t.setText("");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), ACTION_GET_CONTENT);
    }
    /*----------------------------------------------------------------------------*/

    public void onAnalyzeClick(View v) {

        getBlobCoordinates();


    }
    /*----------------------------------------------------------------------------*/

    /**
     * The MyAsyncTask class implements the AsyncTask class.
     * It is used for a "heavy" image process
     */
    private class MyAsyncTask extends AsyncTask<Bitmap, Integer, Bitmap> {


        private Bitmap bm;
        private Bitmap flooded;

        /**
         * @return File that contain the name and the directory of the segment image file
         */

        private File getOutputSegmentFile() {//Give Name to the segment file.
            // To be safe, you should check that the SDCard is mounted
            // using Environment.getExternalStorageState() before doing this.
            File mediaStorageDir = new File(Environment.getExternalStorageDirectory()
                    + "/Android/data/"
                    + getApplicationContext().getPackageName()
                    + "/Files/SegmentFiles");

//            Log.i(TAG, "" + Environment.getExternalStorageDirectory()
//                    + "/Android/data/"
//                    + getApplicationContext().getPackageName()
//                    + "/Files");

            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    return null;
                }
            }
            // Create a media file name
            String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmm").format(new Date());
            File mediaFile;
            String mImageName = "MI_" + timeStamp + ".jpg";
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + imageName + "_seg.jpg");
            return mediaFile;
        }


        private void edgeTest(Bitmap bmp, Point p, int threshold, int replacementColor) {
            if (PixelCalc.calcDistance(seedAvgColor, new Point(p.x + 1, p.y), bmp) > threshold)//right neighbor
            {
                if (PixelCalc.calcDistance(seedAvgColor, new Point(p.x - 1, p.y), bmp) > threshold)//left neighbor
                {
                    if (PixelCalc.calcDistance(seedAvgColor, new Point(p.x, p.y - 1), bmp) > threshold)//up neighbor
                    {
                        if (PixelCalc.calcDistance(seedAvgColor, new Point(p.x, p.y + 1), bmp) > threshold)//down neighbor
                            bmp.setPixel((int) p.x, (int) p.y, Color.RED);
                    }
                }
            }
        }

        /*----------------------------------------------------------*/
        private void regionGrowing(Bitmap bmp, Point seed, int threshold, int replacementColor) {

            int x = (int) seed.x;
            int y = (int) seed.y;
            Queue<Point> q = new LinkedList<>();
            q.add(seed);
            while (q.size() > 0) {
                Point n = q.poll();//n is the head of list
                edgeTest(bmp, n, threshold, 1);
                if (PixelCalc.calcDistance(seedAvgColor, n, bmp) > threshold)//in case pixel does not belong
                    continue;

                Point e = new Point(n.x + 1, n.y);//right neighbor
                while ((n.x > 0) && (PixelCalc.calcDistance(seedAvgColor, n, bmp) <= threshold)) {
                    bmp.setPixel((int) n.x, (int) n.y, replacementColor);
                    if ((n.y > 0) && (PixelCalc.calcDistance(seedAvgColor, new Point(n.x, n.y - 1), bmp) <= threshold))//up
                        q.add(new Point(n.x, n.y - 1));
                    if ((n.y < bmp.getHeight() - 1) && (PixelCalc.calcDistance(seedAvgColor, new Point(n.x, n.y + 1), bmp) <= threshold))
                        q.add(new Point(n.x, n.y + 1));
                    n.x--;
                }
                while ((e.x < bmp.getWidth() - 1) && (PixelCalc.calcDistance(seedAvgColor, new Point(e.x, e.y), bmp) <= threshold)) {
                    bmp.setPixel((int) e.x, (int) e.y, replacementColor);

                    if ((e.y > 0) && (PixelCalc.calcDistance(seedAvgColor, new Point(e.x, e.y - 1), bmp) <= threshold))
                        q.add(new Point(e.x, e.y - 1));
                    if ((e.y < bmp.getHeight() - 1) && (PixelCalc.calcDistance(seedAvgColor, new Point(e.x, e.y + 1), bmp) <= threshold))
                        q.add(new Point(e.x, e.y + 1));
                    e.x++;
                }
            }
        }


        /*-----------------------------------------------------------*/


        /**
         * Set the view before image process.
         */
        @Override
        protected void onPreExecute() {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
            View v = findViewById(R.id.my_layout);
            v.setAlpha(.5f);
            pb.setVisibility(View.VISIBLE);
            pb.animate().setDuration(shortAnimTime).alpha(
                    1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pb.setVisibility(View.VISIBLE);

                }
            });
        }
        /*----------------------------------------------------------*/


        /**
         * After image process, this method stops the progress bar and present the processed image
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {

            pb.setVisibility(View.INVISIBLE);

            pb.animate().setDuration(0).alpha(
                    0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pb.setVisibility(View.INVISIBLE);

                }
            });
            View v = findViewById(R.id.my_layout);
            v.setAlpha(1f);

            /*Validation mechanism*/
            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
                    .setMessage(MainActivity.this.getString(R.string.validate_segment))
                    .setPositiveButton("YES", null)
                    .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            setPic(null, photoURI);
                            getBlobCoordinates();
                        }
                    });
            AlertDialog alert = alertDialog.create();
            ColorDrawable dialogColor = new ColorDrawable(0x88000000);
            //alert.getWindow().setGravity(Gravity.TOP);
            alert.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);//disable dimmed background
            alert.show();
            // calculatedBitmap = Bitmap.createBitmap(bitmap);//aliasing
            mImageView.setImageResource(0);
            mImageView.destroyDrawingCache();
            TextView t = (TextView)findViewById(R.id.textView);
            diff =Double.parseDouble(new DecimalFormat("##.##").format(diff));
            t.setText("difference is "+diff+"%");

            //---------- image saving-----------
            File pictureFile = null;
            BitmapFactory.Options myOptions = new BitmapFactory.Options();
            myOptions.inScaled = false;
            myOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;// important

            mImageView.setImageBitmap(bitmap);
            try {
                //pictureFile = createImageFile();
                pictureFile = getOutputSegmentFile();
            } catch (Exception e) {
                Toast.makeText(getBaseContext(), R.string.create_file_error, Toast.LENGTH_LONG).show();
            }

            if (pictureFile == null) {
                Log.d(TAG,
                        "Error creating media file, check storage permissions: ");// e.getMessage());
                return;
            }
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);

                calculatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        }


        /*-----------------------------------------------------*/

        /**
         * This method perform image process
         */
        @Override
        protected Bitmap doInBackground(Bitmap... bitmaps) {

            bm = bitmaps[0];
            Mat src = new Mat();
            Mat dest = new Mat();
            Utils.bitmapToMat(bm, src);
            flooded = bitmaps[1];
            int red = android.graphics.Color.rgb(255, 255, 255);
            regionGrowing(flooded, seed, (int) threshold, red);
            Utils.bitmapToMat(flooded, src);
            Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(src, src, 254, 254, Imgproc.THRESH_BINARY);
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();//for findContours calculation. Do not touch.

            /*finding the main contour*/
            Imgproc.findContours(src, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            Log.i(TAG, "doInBackground: num of contours: " + contours.size());
            Imgproc.cvtColor(src, src, Imgproc.COLOR_GRAY2BGR);
            Imgproc.drawContours(src, contours, -1, new Scalar(255, 0, 0), 6);

            /*draw centroid on contour*/
            List<Moments> mu = new ArrayList<>(contours.size());
            int x = 0;
            int y = 0;
            for (int i = 0; i < contours.size(); i++) {
                mu.add(i, Imgproc.moments(contours.get(i), false));
                Moments p = mu.get(i);
                x = (int) (p.get_m10() / p.get_m00());
                y = (int) (p.get_m01() / p.get_m00());
                Imgproc.circle(src, new Point(x, y), 10, new Scalar(0, 0, 255), 5);
            }


            Point[] points;
            points = contours.get(0).toArray();
            double maxRadius = 0;
            for (Point p:points) {
                double a = (x - p.x) * (x - p.x);
                double b = (y - p.y) * (y - p.y);
                double len = Math.sqrt((a + b));
                if (len > maxRadius) {
                    maxRadius = len;
                }
            }

           /*create a mask with only the circle - to compare later with matchShapes*/
            Mat circle = new Mat(src.rows(), src.cols(), CvType.CV_8UC1);
            Imgproc.circle(circle, new Point(x, y), (int) maxRadius, new Scalar(255, 255, 255), 5);
            List<MatOfPoint> contours1 = new ArrayList<>();
            Mat hierarchy1 = new Mat();//for findContours calculation. Do not touch.
            Imgproc.threshold(circle, circle, 254, 254, Imgproc.THRESH_BINARY);
            Imgproc.findContours(circle, contours1, hierarchy1, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_NONE);

            /*calculate the difference between contour and a the minimal enclosing circlea*/
            diff = Imgproc.matchShapes(contours.get(0), contours1.get(0), Imgproc.CV_CONTOURS_MATCH_I2, 0)*1000;
            Log.i(TAG, "doInBackground: diff: " + diff);
            Imgproc.circle(src, new Point(x, y), (int) maxRadius, new Scalar(0, 255, 0), 5);





            //this section is for masking the segment the mole in full color
            /*
            Mat original = new  Mat(1,1,CvType.CV_8UC3);//this is the original colored image
            Utils.bitmapToMat(bm,original);//loading original colored image to the matrix
            Imgproc.resize(original,original,new Size(src.width(),src.height()));//adapting and resizing the original to be same as src matrix dimentions
            Mat result = Mat.zeros(bm.getWidth(),bm.getHeight(),CvType.CV_8UC3);//creating result matrix full of zeros at the begining
            original.copyTo(result,src);//perform copy from original to result and using src matrix as mask
            */


            Bitmap bm = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(src, bm);
            // src.release();
            // dest.release();
            //  cloneDest.release();
            //  hierarchy.release();
            return bm;
        }
      /*----------------------------------------------------------*/

    }


    public void onHistogramClicked(View v) {
        Mat src = new Mat();
        Mat dest = new Mat();

        Utils.bitmapToMat(currentBitmap, src);
        Size rgbaSize = src.size();
        // Set the amount of bars in the histogram.
        int histSize = 256;
        MatOfInt histogramSize = new MatOfInt(histSize);

// Set the height of the histogram and width of the bar.
        int histogramHeight = (int) rgbaSize.height;
        int binWidth = 5;

// Set the value range.
        MatOfFloat histogramRange = new MatOfFloat(0f, 256f);

// Create two separate lists: one for colors and one for channels (these will be used as separate datasets).
        Scalar[] colorsRgb = new Scalar[]{new Scalar(200, 0, 0, 255), new Scalar(0, 200, 0, 255), new Scalar(0, 0, 200, 255)};
        MatOfInt[] channels = new MatOfInt[]{new MatOfInt(0), new MatOfInt(1), new MatOfInt(2)};

// Create an array to be saved in the histogram and a second array, on which the histogram chart will be drawn.
        Mat[] histograms = new Mat[]{new Mat(), new Mat(), new Mat()};
        Mat histMatBitmap = new Mat(rgbaSize, src.type());

        for (int i = 0; i < channels.length; i++) {
            Imgproc.calcHist(Collections.singletonList(src), channels[i], new Mat(), histograms[i], histogramSize, histogramRange);
            Core.normalize(histograms[i], histograms[i], histogramHeight, 0, Core.NORM_INF);
            for (int j = 0; j < histSize; j++) {
                Point p1 = new Point(binWidth * (j - 1), histogramHeight - Math.round(histograms[i].get(j - 1, 0)[0]));
                Point p2 = new Point(binWidth * j, histogramHeight - Math.round(histograms[i].get(j, 0)[0]));
                Imgproc.line(histMatBitmap, p1, p2, colorsRgb[i], 2, 8, 0);
            }
        }

        Bitmap bm = Bitmap.createBitmap(histMatBitmap.cols(), histMatBitmap.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(histMatBitmap, bm);
        mImageView.setImageBitmap(bm);
        ImageButton b = (ImageButton) findViewById(R.id.analyze_btn);
        b.setEnabled(true);
        src.release();
        dest.release();
        histogramSize.release();
        histMatBitmap.release();

        for (MatOfInt mat : channels) {
            mat.release();
        }
        for (Mat mat : histograms) {
            mat.release();
        }


    }
}
