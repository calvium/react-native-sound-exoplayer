package com.zmxv.RNSound;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;

import com.devbrackets.android.exomedia.AudioPlayer;
import com.devbrackets.android.exomedia.listener.OnCompletionListener;
import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RNSoundModule extends ReactContextBaseJavaModule {
  private static final String TAG = RNSoundModule.class.getSimpleName();
  
  @SuppressLint("UseSparseArrays")
  Map<Integer, AudioPlayer> playerPool = new HashMap<>();
  ReactApplicationContext context;
  final static Object NULL = null;

  public RNSoundModule(ReactApplicationContext context) {
    super(context);
    this.context = context;
  }

  @Override
  public String getName() {
    return "RNSound";
  }

  @ReactMethod
  public void prepare(final String fileName, final Integer key, final Callback callback) {
    final AudioPlayer player = createMediaPlayer(fileName);
    if (player == null) {
      WritableMap e = Arguments.createMap();
      e.putInt("code", -1);
      e.putString("message", "resource not found");
      callback.invoke(e);
      return;
    }
    try {
      player.setOnPreparedListener(new OnPreparedListener() {
        @Override
        public void onPrepared() {
          player.setOnPreparedListener(null);
          WritableMap props = Arguments.createMap();
          props.putDouble("duration", player.getDuration() * .001);
          callback.invoke(NULL, props);
        }
      });
      player.prepareAsync();
    } catch (Exception e) {
      Log.w(TAG, "Error preparing audio with " + e.getLocalizedMessage());
    }
    this.playerPool.put(key, player);
  }

  protected AudioPlayer createMediaPlayer(final String fileName) {
    int res = this.context.getResources().getIdentifier(fileName, "raw", this.context.getPackageName());
    if (res != 0) {
      Log.w(TAG, "Raw files are not supported by Android AudioPlayer");
    }
    File file = new File(fileName);
    if (file.exists()) {
      Uri uri = Uri.fromFile(file);
      AudioPlayer player = new AudioPlayer(this.context);
      player.setDataSource(uri); // Maybe here can put in the Looping media source?
      return player;
    }
    return null;
  }

  @ReactMethod
  public void play(final Integer key, final Callback callback) {
    AudioPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(false);
      return;
    }
    if (player.isPlaying()) {
      return;
    }
    player.setOnCompletionListener(new OnCompletionListener() {
      @Override
      public void onCompletion() {
          // No support for looping in AudioPlayer
          callback.invoke(true);
      }
    });
    player.setOnErrorListener(new OnErrorListener() {
      @Override
      public boolean onError(Exception ex) {
        callback.invoke(false); // TODO: pass exception details?
        return true;
      }
    });
    player.start();
  }

  @ReactMethod
  public void pause(final Integer key) {
    AudioPlayer player = this.playerPool.get(key);
    if (player != null && player.isPlaying()) {
      player.pause();
    }
  }

  @ReactMethod
  public void stop(final Integer key) {
    AudioPlayer player = this.playerPool.get(key);
    if (player != null && player.isPlaying()) {
      player.pause();
      player.seekTo(0);
    }
  }

  @ReactMethod
  public void release(final Integer key) {
    AudioPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.release();
      this.playerPool.remove(key);
    }
  }

  @ReactMethod
  public void setVolume(final Integer key, final Float left, final Float right) {
    AudioPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setVolume(left, right);
    }
  }

  @ReactMethod
  public void setLooping(final Integer key, final Boolean looping) {
    AudioPlayer player = this.playerPool.get(key);
    if (player != null) {
      Log.w(TAG, "AudioPlayer does not support looping");
    }
  }

  @ReactMethod
  public void setCurrentTime(final Integer key, final Float sec) {
    AudioPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.seekTo(Math.round(sec * 1000));
    }
  }

  @ReactMethod
  public void getCurrentTime(final Integer key, final Callback callback) {
    AudioPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(-1, false);
      return;
    }
    callback.invoke(player.getCurrentPosition() * .001, player.isPlaying());
  }

  @ReactMethod
  public void enable(final Boolean enabled) {
    // no op
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("IsAndroid", true);
    return constants;
  }

  /**
   * Ensure any audios that are playing when app exits are stopped and released
   */
  @Override
  public void onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy();

    Set<Map.Entry<Integer, AudioPlayer>> entries = playerPool.entrySet();
    for (Map.Entry<Integer, AudioPlayer> entry : entries) {
      AudioPlayer mp = entry.getValue();
      if (mp == null) {
        continue;
      }
      try {
        mp.setOnCompletionListener(null);
        mp.setOnPreparedListener(null);
        mp.setOnErrorListener(null);
        if (mp.isPlaying()) {
          mp.pause();
        }
        mp.reset();
        mp.release();
      } catch (Exception ex) {
        Log.e("RNSoundModule", "Exception when closing audios during app exit. ", ex);
      }
    }
    entries.clear();
  }
}
