package com.example.imagepro;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;


import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CameraActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "CameraActivity";

    private static final double FRAME_RATE = 0.5;
    private long lastFrameTime = 0;
    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private objectDetectorClass objectDetectorClass;
    private Net net;
    OkHttpClient client = new OkHttpClient();


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV is loaded");
                    mOpenCvCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int MY_PERMISSIONS_REQUEST_CAMERA = 123;
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        setContentView(R.layout.activity_camera);
        mOpenCvCameraView = findViewById(R.id.frame_Surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

//        try {
//            // Initialize object detector
//            objectDetectorClass = new objectDetectorClass(getAssets(), "detect.tflite", "jdidd.txt", 320);
//            objectDetectorClass.logTensorShapes(); // This will print the shapes of input and output tensors
//
//            Toast.makeText(this, "Model loaded succefully", Toast.LENGTH_SHORT).show();
//
//        } catch (Exception e) {
//            Toast.makeText(this, "error loadning the model "+ e.getMessage(), Toast.LENGTH_SHORT).show();
//            e.printStackTrace();
//        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (OpenCVLoader.initDebug()) {
            // OpenCV loaded successfully
            Toast.makeText(this, "OpenCV initialization is done", Toast.LENGTH_SHORT).show();
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            // OpenCV not loaded, try again
            Toast.makeText(this, "OpenCV is not loaded  try againg", Toast.LENGTH_SHORT).show();
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        }



    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);

    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime < 2000 / FRAME_RATE) {
            return inputFrame.rgba();
        }

        byte[] frameBytes = convertMatToBytes(inputFrame.rgba());
        String jsonResponse = sendFrameToServer(frameBytes);
        lastFrameTime = currentTime;

        if (jsonResponse != null) {
            try {
                JSONObject jsonObject = new JSONObject(jsonResponse);
                if (jsonObject.has("detected_objects")) {
                    JSONArray detectedObjects = jsonObject.getJSONArray("detected_objects");

                    for (int i = 0; i < detectedObjects.length(); i++) {
                        Log.d("hna","fin hna");
                        JSONObject object = detectedObjects.getJSONObject(i);

                        // Extract the bounding box coordinates
                        double ymin = object.getDouble("ymin");
                        double xmin = object.getDouble("xmin");
                        double ymax = object.getDouble("ymax");
                        double xmax = object.getDouble("xmax");


                        // Convert normalized coordinates to pixel coordinates based on frame size
                        int imgWidth = inputFrame.rgba().cols();
                        int imgHeight = inputFrame.rgba().rows();
                        int left = (int) (xmin * imgWidth);
                        int top = (int) (ymin * imgHeight);
                        int right = (int) (xmax * imgWidth);
                        int bottom = (int) (ymax * imgHeight);

                        Log.d("Object " + i, "Left: " + left + ", Top: " + top + ", Right: " + right + ", Bottom: " + bottom);


                        // Draw the rectangle on the frame
                        Imgproc.rectangle(inputFrame.rgba(), new Point(left, top), new Point(right, bottom), new Scalar(0, 255, 0), 2);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return inputFrame.rgba();
    }

    private byte[] convertMatToBytes(Mat frame) {
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", frame, matOfByte);
        return matOfByte.toArray();
    }
    private String sendFrameToServer(byte[] frameBytes) {
        AtomicReference<String> responseData = new AtomicReference<>("");

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("frame", "frame.jpg", RequestBody.create(MediaType.parse("image/jpeg"), frameBytes));

        RequestBody requestBody = multipartBuilder.build();
        Request request = new Request.Builder()
                .url("http://192.168.1.124:5001/detect_objects")
                .post(requestBody)
                .build();
        CountDownLatch latch = new CountDownLatch(1);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(() -> Toast.makeText(CameraActivity.this, "Server down: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                String responseBody = response.body().string();
                responseData.set(responseBody);
                Log.d("Response", responseBody);
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return responseData.get();
    }


}
