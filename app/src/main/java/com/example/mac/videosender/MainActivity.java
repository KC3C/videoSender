package com.example.mac.videosender;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity{

    private byte[] byteArray;
    public static  Handler handler;
    public String clientIP;
    public static final int CLIENT_PORT = 8686;
    public static final int SERVER_PORT = 8080;

    private TextView ipTextView = (TextView) findViewById(R.id.ip);;
    private Button startButton = (Button) findViewById(R.id.start);
    private SurfaceView cameraSurfaceView = (SurfaceView) findViewById(R.id.camera_view);

    private android.hardware.Camera camera;
    private final static int PREVIEW_WIDTH = 200;
    private final static int PREVIEW_HEIGHT = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipTextView.setText(getIpAddress());

        Thread socketServerThread = new SocketServerThread();
        socketServerThread.start();
        final Thread videoThread = new VideoThread();

        handler=new Handler(){
            @Override
            public void handleMessage(Message msg) {
                Toast.makeText(MainActivity.this, msg.getData().getString("msg"), Toast.LENGTH_LONG).show();
                super.handleMessage(msg);
            }
        };

        cameraSurfaceView.setVisibility(View.GONE);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ipTextView.setVisibility(View.GONE);
                startButton.setVisibility(View.GONE);
                cameraSurfaceView.setVisibility(View.VISIBLE);
                videoThread.start(); //这里需要在tcp连接以后在点击录像按钮
            }
        });

    }

    private String getIpAddress() {
        //获得并返回本机的ip
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += inetAddress.getHostAddress() + "\n";
                    }
                }
            }
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }

    //发送一张临时图片
    public void SendImage(){
        ByteArrayOutputStream imageOutputstream = new ByteArrayOutputStream();
        Bitmap tempImage = BitmapFactory.decodeResource(getResources(), R.drawable.timg);
        tempImage.compress(Bitmap.CompressFormat.JPEG, 90, imageOutputstream);
        byte byteBuffer[] = new byte[1024];
        OutputStream imageOutSocket;

        try {
            if (clientIP != null) {
                clientIP = clientIP.substring(clientIP.lastIndexOf("/") + 1);
            }

            byteArray = imageOutputstream.toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);

            Socket tempSocket = new Socket(clientIP, CLIENT_PORT);
            imageOutSocket = tempSocket.getOutputStream();
            int amount = 0;
            while ((amount = inputStream.read(byteBuffer)) != -1) {
                imageOutSocket.write(byteBuffer, 0, amount);
            }
            imageOutputstream.flush();
            imageOutputstream.close();
            tempSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class SocketServerThread extends Thread {

        static final int SocketServerPORT = SERVER_PORT;
        private ServerSocket serverSocket;
        private Socket phoneSocket;

        @Override
        public void run() {

            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(SocketServerPORT));

                while (true) {
                    Message msg=new Message();
                    String message;
                    Bundle msgBundle = new Bundle();

                    //监听控制端的tcp连接
                    phoneSocket = serverSocket.accept();
                    clientIP = phoneSocket.getInetAddress().toString();
                    msgBundle.putString("msg", "" + clientIP);
                    SendImage();//发送临时图片

                    msg.setData(msgBundle);
                    handler.sendMessage(msg);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class VideoThread extends Thread{

        public SurfaceHolder surfaceHolder;

        @Override
        public void run() {
            surfaceHolder = cameraSurfaceView.getHolder();
            surfaceHolder.setKeepScreenOn(true);
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void  surfaceCreated(SurfaceHolder holder) {
                    Log.e("Surface", "created!!!!!!!!!!!!!");
                    initCamera();
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {}
            });
            if (camera == null) initCamera();

        }

        @SuppressWarnings("deprecation")
        private void initCamera(){
            try {
                camera = android.hardware.Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                Camera.Parameters params = camera.getParameters();
                android.hardware.Camera.Parameters parameters = camera.getParameters();
                parameters.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                parameters.setPreviewFpsRange(20, 30);
                parameters.setPictureFormat(ImageFormat.NV21);
                parameters.setPictureSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                camera.setDisplayOrientation(90);
                camera.setPreviewDisplay(surfaceHolder);
                camera.setParameters(params);
                camera.setPreviewCallback(new StreamImage());
                camera.startPreview();
                camera.autoFocus(null);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @SuppressWarnings("deprecation")
        class StreamImage implements android.hardware.Camera.PreviewCallback {
            @Override
            public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
                android.hardware.Camera.Size size = camera.getParameters().getPreviewSize();
                try {
                    YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                    if (image != null) {
                        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
                        image.compressToJpeg(new Rect(0, 0, size.width, size.height), 20, outstream);
                        Thread th = new SendVideoThread(outstream);
                        th.start();
                        outstream.flush();
                    }
                } catch (Exception ex) {
                    Log.e("Sys", "Error:......." + ex.getMessage());
                }
            }
        }

        class SendVideoThread extends Thread {
            private byte byteBuffer[] = new byte[1024];
            private OutputStream outsocket;
            private ByteArrayOutputStream myoutputstream;

            public SendVideoThread(ByteArrayOutputStream myoutputstream) {
                this.myoutputstream = myoutputstream;
                try {
                    myoutputstream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            public void run() {
                try {
                    if (clientIP != null) {
                        clientIP = clientIP.substring(clientIP.lastIndexOf("/") + 1);
                    }

                    byteArray = myoutputstream.toByteArray();
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);
                    Socket tempSocket = new Socket(clientIP, CLIENT_PORT);
                    outsocket = tempSocket.getOutputStream();
                    int amount, num = 0;
                    while ((amount = inputStream.read(byteBuffer)) != -1) {
                        outsocket.write(byteBuffer, 0, amount);
                    }
                    myoutputstream.flush();
                    myoutputstream.close();
                    tempSocket.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

