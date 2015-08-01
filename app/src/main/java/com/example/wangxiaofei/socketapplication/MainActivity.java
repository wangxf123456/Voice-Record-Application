package com.example.wangxiaofei.socketapplication;

import android.app.Activity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.media.*;
import android.os.Environment;
import android.widget.Button;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.app.ProgressDialog;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;

import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseObject;
import com.parse.SaveCallback;

public class MainActivity extends Activity {

    private static final int RECORDER_SAMPLERATE = 4096;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORDER_CHANNELS_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private int CHUNK_SIZE = 2048;
    private boolean sflag = true;

    private ProgressDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setButtonHandlers();
        enableButtons(false);

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    }

    private void setButtonHandlers() {
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
    }

    private void enableButton(int id, boolean isEnable) {
        ((Button) findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording() {

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                record_thread();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void record_thread() {
        Socket socket = null;
        try {
            String HOSTNAMEANDPORT = getIpAddr();
            dialog.dismiss();
            System.out.println("get host name: " + HOSTNAMEANDPORT);
            if (HOSTNAMEANDPORT.length() < 1) {
                sflag = false;
                stopRecording();
                return;
            }
            String[] separated = HOSTNAMEANDPORT.split(":");
            String HOSTNAME = separated[0];
            int PORT = Integer.parseInt(separated[1]);

            System.out.println("Connecting to server...");
            socket = new Socket(HOSTNAME, PORT);
            System.out.println("Connected to server at " + socket.getInetAddress());

            PrintStream out = new PrintStream(socket.getOutputStream(), true);

            recordAndSendToSocket(out);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void recordAndSendToSocket(OutputStream outStream) {
        File root = Environment.getExternalStorageDirectory();
        String filePath = root.toString() + "/voice8K16bitmono.pcm";

        System.out.println(root.toString());
        File file = new File(root, "voice8K16bitmono.pcm");
//
//        if (file.exists()) {
//            try {
//                RandomAccessFile raf = new RandomAccessFile(file, "rw");
//                raf.setLength(0);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("file: " + filePath + " created");
        short sData[] = new short[BufferElements2Rec];

        System.out.println("start recording");
        while (isRecording) {

            recorder.read(sData, 0, BufferElements2Rec);

            try {
                byte bData[] = short2byte(sData);
                outStream.write(bData, 0, BufferElements2Rec * BytesPerElement);
                System.out.println("send audio data: " + bData);
                os.write(bData, 0, BufferElements2Rec * BytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uploadFileToCloud() {
        dialog = ProgressDialog.show(MainActivity.this, "wait...", "Uploading file");
        File root = Environment.getExternalStorageDirectory();
        System.out.println(root.toString());
        File file = new File(root, "voice8K16bitmono.pcm");

        String content = "";
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            content = new String(chars);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(reader !=null){
                try {
                    reader.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("content is:" + content);
        byte[] data = content.getBytes();
        ParseObject record = new ParseObject("HeartRateRecord");
        record.put("userid", "qk7KRdygva");
        ParseFile pFile = new ParseFile("heartRate.pcm", data);
        record.put("heartRateFile", pFile);
        record.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                dialog.dismiss();
            }
        });
    }
    private void stopRecording() {
        // stops the recording activity
        if (recorder != null) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;

        }
    }

    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    private static String convertStreamToString(InputStream inputStream) throws IOException {
        if (inputStream != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"),1024);
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                inputStream.close();
            }
            return writer.toString();
        } else {
            return "";
        }
    }

    private String getIpAddr() {

        HttpClient httpclient = new DefaultHttpClient();
        HttpGet request = new HttpGet("http://52.25.63.79/api/voice");
        HttpResponse response;
        String content = "";
        try {
            response = httpclient.execute(request);
            content = convertStreamToString(response.getEntity().getContent());
            if (response.getStatusLine().getStatusCode() == 404) {
                return "";
            }
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("get content: " + content);
        return content;
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    enableButtons(true);
                    dialog = ProgressDialog.show(MainActivity.this, "wait...", "Retrieving ip and connecting");
                    startRecording();
                    break;
                }
                case R.id.btnStop: {
                    enableButtons(false);
                    stopRecording();
                    if (sflag) {
//                        uploadFileToCloud();
                    }
                    break;
                }
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
