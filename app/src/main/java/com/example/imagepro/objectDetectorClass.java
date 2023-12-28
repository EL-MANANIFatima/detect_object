package com.example.imagepro;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import org.checkerframework.checker.units.qual.A;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class objectDetectorClass {
    // should start from small letter

    // this is used to load model and predict
    private Interpreter interpreter;
    // store all label in array
    private List<String> labelList;
    private int INPUT_SIZE;
    private int PIXEL_SIZE=3; // for RGB
    private int IMAGE_MEAN=0;
    private  float IMAGE_STD=255.0f;
    // use to initialize gpu in app
    private GpuDelegate gpuDelegate;
    private int height=0;
    private  int width=0;

    objectDetectorClass(AssetManager assetManager,String modelPath, String labelPath,int inputSize) throws IOException{
        INPUT_SIZE=inputSize;
        // use to define gpu or cpu // no. of threads
        Interpreter.Options options=new Interpreter.Options();
        gpuDelegate=new GpuDelegate();
        options.addDelegate(gpuDelegate);
        options.setNumThreads(4); // set it according to your phone
        // loading model
        interpreter=new Interpreter(loadModelFile(assetManager,modelPath),options);
        // load labelmap
        labelList=loadLabelList(assetManager,labelPath);


    }

    private List<String> loadLabelList(AssetManager assetManager, String labelPath) {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("label list ","gdhsdsds");
        } finally {
            // Ensure to close the reader in the finally block to release resources
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.d("f finally ","sdsgdhsds");
                    e.printStackTrace(); // Print the stack trace
                }
            }
        }

        return labelList;
    }


    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        // use to get description of file
        AssetFileDescriptor fileDescriptor=assetManager.openFd(modelPath);
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset =fileDescriptor.getStartOffset();
        long declaredLength=fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);
    }
    // create new Mat function
    public Mat recognizeImage(Mat mat_image){

        // Rotate original image by 90 degree get get portrait frame

        // This change was done in video: Does Your App Keep Crashing? | Watch This Video For Solution.
        // This will fix crashing problem of the app

        Mat rotated_mat_image=new Mat();

        Mat a=mat_image.t();
        Core.flip(a,rotated_mat_image,1);
        // Release mat
        a.release();

        // if you do not do this process you will get improper prediction, less no. of object
        // now convert it to bitmap
        Bitmap bitmap=null;
        bitmap=Bitmap.createBitmap(rotated_mat_image.cols(),rotated_mat_image.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rotated_mat_image,bitmap);
        // define height and width
        height=bitmap.getHeight();
        width=bitmap.getWidth();

        // scale the bitmap to input size of model
         Bitmap scaledBitmap=Bitmap.createScaledBitmap(bitmap,INPUT_SIZE,INPUT_SIZE,false);

         // convert bitmap to bytebuffer as model input should be in it
        ByteBuffer byteBuffer=convertBitmapToByteBuffer(scaledBitmap);

        // defining output
        // 10: top 10 object detected
        // 4: there coordinate in image
      //  float[][][]result=new float[1][10][4];
        Object[] input=new Object[1];
        input[0]=byteBuffer;

        Map<Integer,Object> output_map=new TreeMap<>();
        // we are not going to use this method of output
        // instead we create treemap of three array (boxes,score,classes)

        float[][][]boxes =new float[1][10][4];
        float[][] scores=new float[1][10];
        float[][] classes=new float[1][10];


        output_map.put(1,boxes);
        output_map.put(3,classes);
        output_map.put(0,scores);

        interpreter.runForMultipleInputsOutputs(input, output_map);

        for (int i = 0; i < 10; i++) {

            float class_value = classes[0][i];
            float score_value = scores[0][i];

            if (score_value > 0.2) {

                float[] box1 = boxes[0][i];

                float top = box1[0] * height;
                float left = box1[1] * width;
                float bottom = box1[2] * height;
                float right = box1[3] * width;

                String label = labelList.get((int) class_value);

                Log.d("Debug", "Loop iteration: " + i);
                Log.d("Detection", "Class Value: " + class_value + ", Label: " + label + ", Score: " + score_value);
                Log.d("BoundingBox", "Top: " + top + ", Left: " + left + ", Bottom: " + bottom + ", Right: " + right);
            }
        }



        Mat b=rotated_mat_image.t();
        Core.flip(b,mat_image,0);
        b.release();
        return mat_image;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;
        // some model input should be quant=0  for some quant=1
        // for this quant=0
        // Change quant=1
        // As we are scaling image from 0-255 to 0-1
        int quant=0;
        int size_images=INPUT_SIZE;
        if(quant==1){
            byteBuffer=ByteBuffer.allocateDirect(1*size_images*size_images*3);
        }
        else {
            byteBuffer=ByteBuffer.allocateDirect(4*1*size_images*size_images*3);
        }
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_images*size_images];
        bitmap.getPixels(intValues,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        int pixel=0;

        // some error
        //now run
        for (int i=0;i<size_images;++i){
            for (int j=0;j<size_images;++j){
                final  int val=intValues[pixel++];
                if(quant==0){
                    byteBuffer.put((byte) ((val>>16)&0xFF));
                    byteBuffer.put((byte) ((val>>8)&0xFF));
                    byteBuffer.put((byte) (val&0xFF));
                }
                else {
                    byteBuffer.putFloat((((val >> 16) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val >> 8) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val) & 0xFF))/255.0f);
                }
            }
        }
    return byteBuffer;
    }
    public void logTensorShapes() {
        // Get the number of input and output tensors
        int inputTensorCount = interpreter.getInputTensorCount();
        int outputTensorCount = interpreter.getOutputTensorCount();

        // Log details about the input tensors (shapes, etc.)
        for (int i = 0; i < inputTensorCount; i++) {
            int[] inputTensorShape = interpreter.getInputTensor(i).shape();
            System.out.println("Input Tensor " + i + " Shape: " + Arrays.toString(inputTensorShape));
        }

        // Log details about the output tensors (shapes, etc.)
        for (int i = 0; i < outputTensorCount; i++) {
            int[] outputTensorShape = interpreter.getOutputTensor(i).shape();
            System.out.println("Output Tensor " + i + " Shape: " + Arrays.toString(outputTensorShape));

        }
    }
}
