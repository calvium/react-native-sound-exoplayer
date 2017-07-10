package com.zmxv.RNSound;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;

import com.devbrackets.android.exomedia.AudioPlayer;
import com.devbrackets.android.exomedia.listener.OnCompletionListener;
import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.devbrackets.android.exomedia.listener.OnSeekCompletionListener;
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

  /**
   * Holds player and additional info about it
   */
  static class PlayerInfo {
    public AudioPlayer audioPlayer;
    // true if should loop
    public boolean looping = false;
    // Used to prevent issues when re-starting due to a loop.
    // There's an unlikely but possible case where stopped audio could be re-looped unexpectedly.
    // This flag is used to prevent that.
    public boolean playing = true;
  }

  @SuppressLint("UseSparseArrays")
  Map<Integer, PlayerInfo> playerPool = new HashMap<>();
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
    PlayerInfo info = new PlayerInfo();
    info.audioPlayer = player;
    this.playerPool.put(key, info);
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
    final PlayerInfo info = this.playerPool.get(key);
    if (info == null || info.audioPlayer == null) {
      callback.invoke(false);
      return;
    }

    final AudioPlayer player = info.audioPlayer;

    // Allow trying to play if already playing - quite safe
    info.playing = true;

    if (player.isPlaying()) {
      return;
    }
    player.setOnCompletionListener(new OnCompletionListener() {
      @Override
      public void onCompletion() {

        if (info.looping) {
          player.pause();

          // Calling .start() immediately here doesn't work.
          // Need to wait for the seek to complete
          player.setOnSeekCompletionListener(new OnSeekCompletionListener() {
            @Override
            public void onSeekComplete() {
              // Just in case we've been stopped *while* waiting for the seek to complete
              if (info.playing) {
                player.start();
                player.setOnSeekCompletionListener(null);
              } else {
                Log.w(TAG, "Audio stopping while waiting for loop to re-start");
              }
            }
          });
          player.seekTo(0);
          return;
        }

        // https://github.com/brianwernick/ExoMedia/issues/382
        // 'rewind' so we can play it again later
        player.pause();
        player.seekTo(0);

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
    PlayerInfo info = this.playerPool.get(key);
    if (info == null) {
      return;
    }
    info.playing = false;
    AudioPlayer player = info.audioPlayer;
    if (player != null && player.isPlaying()) {
      player.pause();
    }
  }

  @ReactMethod
  public void stop(final Integer key) {
    PlayerInfo info = this.playerPool.get(key);
    if (info == null) {
      return;
    }
    AudioPlayer player = info.audioPlayer;
    info.playing = false;
    if (player != null && player.isPlaying()) {
      player.pause();
      player.seekTo(0);
    }
  }

  @ReactMethod
  public void release(final Integer key) {
    PlayerInfo info = this.playerPool.get(key);
    if (info == null) {
      return;
    }
    info.playing = false;
    AudioPlayer player = info.audioPlayer;
    if (player != null) {
      player.release();
      this.playerPool.remove(key);
    }
  }

  @ReactMethod
  public void setVolume(final Integer key, final Float left, final Float right) {
    PlayerInfo info = this.playerPool.get(key);
    if (info == null) {
      return;
    }
    AudioPlayer player = info.audioPlayer;
    if (player != null) {
      player.setVolume(left, right);
    }
  }

  @ReactMethod
  public void setLooping(final Integer key, final Boolean looping) {
    PlayerInfo info = this.playerPool.get(key);
    if (info == null) {
      return;
    }
    info.looping = true;
  }

  @ReactMethod
  public void setCurrentTime(final Integer key, final Float sec) {
    PlayerInfo info = this.playerPool.get(key);
    if (info == null) {
      return;
    }
    AudioPlayer player = info.audioPlayer;
    if (player != null) {
      player.seekTo(Math.round(sec * 1000));
    }
  }

  @ReactMethod
  public void getCurrentTime(final Integer key, final Callback callback) {
    PlayerInfo info = this.playerPool.get(key);
    if (info == null) {
      return;
    }
    AudioPlayer player = info.audioPlayer;
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

    Set<Map.Entry<Integer, PlayerInfo>> entries = playerPool.entrySet();
    for (Map.Entry<Integer, PlayerInfo> entry : entries) {
      PlayerInfo info = entry.getValue();
      if (info == null) {
        continue;
      }
      AudioPlayer player = info.audioPlayer;
      if (player == null) {
        continue;
      }
      try {
        player.setOnCompletionListener(null);
        player.setOnPreparedListener(null);
        player.setOnErrorListener(null);
        if (player.isPlaying()) {
          player.pause();
        }
        player.reset();
        player.release();
      } catch (Exception ex) {
        Log.e("RNSoundModule", "Exception when closing audios during app exit. ", ex);
      }
    }
    entries.clear();
  }
}
