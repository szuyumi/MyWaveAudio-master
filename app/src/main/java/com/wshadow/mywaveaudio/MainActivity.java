package com.wshadow.mywaveaudio;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;

import com.chrischen.waveview.WaveView;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    @BindView(R.id.bt_play_start)
    Button mPlay;
    @BindView(R.id.bt_record_start)
    Button mRecordStart;
    @BindView(R.id.bt_record_stop)
    Button mRecordStop;
    @BindView(R.id.wave)
    WaveView mWaveView;

    //配置
    private final File PATH = App.getInstance().getExternalFilesDir("recordtest");//储存路径
    private final String FILE_NAME = "recordSwitch";//文件名
    private final int mFreq = 44100;// 采样率
    private final int mRecordChannelConfig = AudioFormat.CHANNEL_IN_STEREO;// 设置立体声声道
    private final int mPlayChannelConfig = AudioFormat.CHANNEL_IN_STEREO;// 设置立体声声道
    private final int mAudioEncoding = AudioFormat.ENCODING_PCM_16BIT;// 量化编码

    private File mAudioFile = null;
    private boolean mIsRecording, mIsPlaying;
    private PlayTask mPlayer;
    private RecordTask mRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIsRecording = false;
        mIsPlaying = false;
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    /** 
     * 权限请求回调，提示用户之后，用户点击“允许”或者“拒绝”之后调用此方法 
     *
     * @param requestCode  定义的权限编码 
     * @param permissions  权限名称 
     * @param grantResults 允许/拒绝 
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);

    }

    //录制音频
    @OnClick({R.id.bt_record_start, R.id.bt_record_stop})
    void recordSwitch(Button bt) {
        switch (bt.getId()) {
            case R.id.bt_record_start:
                MainActivityPermissionsDispatcher.startAudioRecordWithPermissionCheck(this);
                break;
            case R.id.bt_record_stop:
                stopAudioRecord();
                break;
        }
    }

    //播放音频
    @OnClick({R.id.bt_play_start, R.id.bt_play_stop})
    void playSwitch(Button bt) {
        switch (bt.getId()) {
            case R.id.bt_play_start:
                MainActivityPermissionsDispatcher.startAudioPlayWithPermissionCheck(this);
                break;
            case R.id.bt_play_stop:
                stopAudioPlay();
                break;
        }

    }

    /** 
     * 如果用户拥有该权限执行的方法 
     */
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO})
    void startAudioRecord() {
        mWaveView.clear();
        LogUtils.d("::start recordSwitch");
        PATH.mkdirs();// 创建文件夹
        try {
            mAudioFile = File.createTempFile(FILE_NAME, ".pcm", PATH); // 创建临时文件
        } catch (IOException e) {
            e.printStackTrace();
        }
        mIsRecording = true;
        mRecorder = new RecordTask();
        mRecorder.execute();
    }

    private void stopAudioRecord() {
        LogUtils.d("::stop recordSwitch");
        mWaveView.setHasOver(true);
        mIsRecording = false;
    }

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO})
    void startAudioPlay() {
        mIsPlaying = true;
        mPlayer = new PlayTask();
        mPlayer.execute();
    }

    private void stopAudioPlay() {
        mIsPlaying = false;
    }

    class RecordTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... arg0) {

            try {
                // 开通输出流到指定的文件
                DataOutputStream os = new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(mAudioFile)));
                // 获取合适的缓冲大小
                int bufferSize = AudioRecord.getMinBufferSize(mFreq,
                        mRecordChannelConfig,
                        mAudioEncoding);
                // 实例化AudioRecord
                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        mFreq,
                        mRecordChannelConfig,
                        mAudioEncoding,
                        bufferSize);
                // 定义缓冲
                short[] buffer = new short[bufferSize];

                // 开始录制
                record.startRecording();

                // 定义循环，根据isRecording的值来判断是否继续录制
                while (mIsRecording) {
                    // 从bufferSize中读取字节，返回读取的short个数
                    // sizeInShorts的数值不能过大
                    int bufferReadResult = record.read(buffer, 0, buffer.length);                    // 循环将buffer中的音频数据写入到OutputStream中

                    for (int i = 0; i < bufferReadResult; i++) {
                        os.writeShort(buffer[i]);
                    }
                    publishProgress((int) buffer[0]); // 向UI线程报告当前读取的音频数值
                }
                // 录制结束
                record.stop();
                record.release();
                LogUtils.i("::" + mAudioFile.length());
                os.close();
            } catch (Exception e) {
                LogUtils.e("::" + e.getMessage());
            }
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
            mWaveView.putValue(progress[0]);

        }

        protected void onPostExecute(Void result) {

        }
    }

    class PlayTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            int bufferSize = AudioTrack.getMinBufferSize(mFreq,
                    mPlayChannelConfig,
                    mAudioEncoding);
            short[] buffer = new short[bufferSize / 4];
            try {
                // 定义输入流，将音频写入到AudioTrack类中，实现播放
                DataInputStream dis = new DataInputStream(
                        new FileInputStream(mAudioFile));
                // 实例AudioTrack
                AudioTrack track = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        mFreq,
                        mPlayChannelConfig,
                        mAudioEncoding,
                        bufferSize,
                        AudioTrack.MODE_STREAM);
                // 开始播放
                track.play();
                while (mIsPlaying && dis.available() > 0) {
                    int i = 0;
                    while (dis.available() > 0 && i < buffer.length) {
                        buffer[i] = dis.readShort();
                        i++;
                    }
                    // 然后将数据写入到AudioTrack中
                    track.write(buffer, 0, buffer.length);
                }
                // 播放结束
                track.stop();
                track.release();
                dis.close();
            } catch (Exception e) {
                LogUtils.e("slack", "error:" + e.getMessage());
            }
            return null;
        }

        protected void onPostExecute(Void result) {

        }

        protected void onPreExecute() {

        }
    }
}
