package com.example.react_native_cn_tts;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.media.AudioManager;
import com.baidu.tts.auth.AuthInfo;
import com.baidu.tts.client.SpeechError;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;
import android.content.pm.PackageManager;
import android.Manifest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;

/**
 * Created by Symous on 2017-07-25.
 */

public class BaiduTTS implements SpeechSynthesizerListener{

    private TtsMode ttsMode = TtsMode.ONLINE;
     // ================选择TtsMode.ONLINE  不需要设置以下参数; 选择TtsMode.MIX 需要设置下面2个离线资源文件的路径
    private static final String TEMP_DIR = "/sdcard/baiduTTS"; // 重要！请手动将assets目录下的3个dat 文件复制到该目录

    // 请确保该PATH下有这个文件
    private static final String TEXT_FILENAME = TEMP_DIR + "/" + "bd_etts_text.dat";

    // 请确保该PATH下有这个文件 ，m15是离线男声
    private static final String MODEL_FILENAME =
            TEMP_DIR + "/" + "bd_etts_common_speech_m15_mand_eng_high_am-mix_v3.0.0_20170505.dat";
    //String appId = "10716869",apiKey="5xuBepa3nHKnNhygRi0HfHlp",secKey="e0ee91d8555e2b15a64110d8c4440d74";
    // 语音合成客户端
    private SpeechSynthesizer mSpeechSynthesizer;
    String path = Environment.getExternalStorageDirectory().toString()+"/CNTTS/语音文件/";

    private SpeechSynthesizerListener callBack;

    public BaiduTTS(Context context, SpeechSynthesizerListener callBack) throws IOException {
        boolean isMix = ttsMode.equals(TtsMode.MIX);
        boolean isSuccess;
        if (isMix) {
            // 检查2个离线资源是否可读
            isSuccess = checkOfflineResources();
            if (!isSuccess) {
                return;
            } else {
                System.out.println("离线资源存在并且可读, 目录：" + TEMP_DIR);
            }
        }
        // 获取语音合成对象实例
        mSpeechSynthesizer = SpeechSynthesizer.getInstance();
        // 设置context
        mSpeechSynthesizer.setContext(context);
        // 设置语音合成状态监听器
        mSpeechSynthesizer.setSpeechSynthesizerListener(this);
        this.callBack = callBack;
    }

    /**
     * 检查 TEXT_FILENAME, MODEL_FILENAME 这2个文件是否存在，不存在请自行从assets目录里手动复制
     *
     * @return
     */
    private boolean checkOfflineResources() {
        String[] filenames = {TEXT_FILENAME, MODEL_FILENAME};
        for (String path : filenames) {
            File f = new File(path);
            if (!f.canRead()) {
                return false;
            }
        }
        return true;
    }

    public void speak(String content){
        if (mSpeechSynthesizer == null) {
            return;
        }
        int result = mSpeechSynthesizer.speak(content);
        checkResult(result, "speak");
    }
    public void pause(){mSpeechSynthesizer.pause();}
    public void resume(){mSpeechSynthesizer.resume();}
    public void stop(){ mSpeechSynthesizer.stop();}


    public void initTts(String appID,String apiKey,String secKey){
        boolean isMix = ttsMode.equals(TtsMode.MIX);
        boolean isSuccess;
        // 设置离线语音合成授权，需要填入从百度语音官网申请的app_id
        int result = mSpeechSynthesizer.setAppId(appID);
        checkResult(result, "setAppId");
        // 设置在线语音合成授权，需要填入从百度语音官网申请的api_key和secret_key
        result = mSpeechSynthesizer.setApiKey(apiKey,secKey);
        checkResult(result, "setApiKey");
        // 4. 支持离线的话，需要设置离线模型
        if (isMix) {
            // 检查离线授权文件是否下载成功，离线授权文件联网时SDK自动下载管理，有效期3年，3年后的最后一个月自动更新。
            isSuccess = checkAuth();
            if (!isSuccess) {
                return;
            }
            // 文本模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, TEXT_FILENAME);
            // 声学模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, MODEL_FILENAME);
        }
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        mSpeechSynthesizer.setAudioStreamType(AudioManager.MODE_IN_CALL);
         // x. 额外 ： 自动so文件是否复制正确及上面设置的参数
        Map<String, String> params = new HashMap<>();
        // 复制下上面的 mSpeechSynthesizer.setParam参数
        // 上线时请删除AutoCheck的调用
        if (isMix) {
            params.put(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, TEXT_FILENAME);
            params.put(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, MODEL_FILENAME);
        }
        result = mSpeechSynthesizer.initTts(ttsMode);
        checkResult(result, "initTts");
    }

    private void checkResult(int result, String method) {
        if (result != 0) {
            System.out.println("error code :" + result + " method:" + method + ", 错误码文档:http://yuyin.baidu.com/docs/tts/122 ");
        }
    }

    /**
     * 检查appId ak sk 是否填写正确，另外检查官网应用内设置的包名是否与运行时的包名一致。本demo的包名定义在build.gradle文件中
     *
     * @return
     */
    private boolean checkAuth() {
        AuthInfo authInfo = mSpeechSynthesizer.auth(ttsMode);
        if (!authInfo.isSuccess()) {
            // 离线授权需要网站上的应用填写包名。本demo的包名是com.baidu.tts.sample，定义在build.gradle中
            String errorMsg = authInfo.getTtsError().getDetailMessage();
            return false;
        } else {
            return true;
        }
    }


    @Override
    public void onSynthesizeStart(String s) {
        callBack.onSynthesizeStart(s);
    }

    @Override
    public void onSynthesizeDataArrived(String s, byte[] bytes, int i) {
        callBack.onSynthesizeDataArrived(s, bytes, i);
    }

    @Override
    public void onSynthesizeFinish(String s) {
        callBack.onSynthesizeFinish(s);
    }

    @Override
    public void onSpeechStart(String s) {
        callBack.onSpeechStart(s);
    }

    @Override
    public void onSpeechProgressChanged(String s, int i) {
        callBack.onSpeechProgressChanged(s, i);
    }

    @Override
    public void onSpeechFinish(String s) {
        callBack.onSpeechFinish(s);
    }

    @Override
    public void onError(String s, SpeechError speechError) {

    }

}
