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

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;

public class MainActivity extends Activity{

    private static final int RECORDER_SAMPLERATE = 4096;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORDER_CHANNELS_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private Thread listenThread = null;
    private boolean isRecording = false;

    private int PORT = 3333;
    private int CHUNK_SIZE = 2048;
    private String HOSTNAME = "127.0.0.1";
    private boolean server_continue = true;
    private boolean client_continue = true;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listenThread = new Thread(new Runnable() {
            public void run() {
                server_thread();
            }
        }, "Listening Thread");
        listenThread.start();

        setButtonHandlers();
        enableButtons(false);

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    }

    private void server_thread() {
        Socket socket = null;

        try {
            ServerSocket server = new ServerSocket(PORT);
            while (true) {
                System.out.println("Server waiting for connection...");
                socket = server.accept();
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                int pos = 0;
                InputStream socket_in = socket.getInputStream();
                while ((bytesRead = socket_in.read(buffer, 0, CHUNK_SIZE)) >= 0) {
                    pos += bytesRead;
                    System.out.println(pos + " bytes (" + bytesRead + " bytes read)");
                    PlayViaAudioTrack(buffer);
                    if (!server_continue) {
                        return;
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println("Can't setup server on this port number. ");
        }
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
                client_thread();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void client_thread() {
        Socket socket = null;
        try {
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
        short sData[] = new short[BufferElements2Rec];

        System.out.println("start recording");
        while (isRecording) {

            recorder.read(sData, 0, BufferElements2Rec);

            try {
                byte bData[] = short2byte(sData);
                System.out.println("send audio data: " + bData);
                outStream.write(bData, 0, BufferElements2Rec * BytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }

    private void PlayViaAudioTrack(byte[] byteData) throws IOException{
        // Set and push to audio track..
        int intSize = android.media.AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS_OUT, RECORDER_AUDIO_ENCODING);

        AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS_OUT, RECORDER_AUDIO_ENCODING, intSize, AudioTrack.MODE_STREAM);
        if (at != null) {
            at.play();
            // Write the byte array to the track
            System.out.println("play write length: " + byteData.length);
            at.write(byteData, 0, byteData.length);
            at.stop();
            at.release();
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

    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    enableButtons(true);
                    startRecording();
                    break;
                }
                case R.id.btnStop: {
                    enableButtons(false);
                    stopRecording();
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
