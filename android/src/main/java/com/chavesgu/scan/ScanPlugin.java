package com.chavesgu.scan;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import static android.content.Context.VIBRATOR_SERVICE;

/** ScanPlugin */
public class ScanPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  private MethodChannel channel;
  private Activity activity;
  private FlutterPluginBinding flutterPluginBinding;
  private Result _result;

  // Executor thay cho AsyncTask
  private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor();
  private Future<?> parseFuture;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    this.flutterPluginBinding = flutterPluginBinding;
  }

  private void configChannel(ActivityPluginBinding binding) {
    activity = binding.getActivity();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "chavesgu/scan");
    channel.setMethodCallHandler(this);
    flutterPluginBinding.getPlatformViewRegistry()
            .registerViewFactory("chavesgu/scan_view", new ScanViewFactory(
                    flutterPluginBinding.getBinaryMessenger(),
                    flutterPluginBinding.getApplicationContext(),
                    activity,
                    binding
            ));
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    configChannel(binding);
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    configChannel(binding);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
  }
  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    this.flutterPluginBinding = null;
    if (parseExecutor != null && !parseExecutor.isShutdown()) {
      parseExecutor.shutdownNow();
    }
  }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
    if (channel != null) channel.setMethodCallHandler(null);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    _result = result;
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if (call.method.equals("parse")) {
      // parse image from path asynchronously
      final String path = (String) call.arguments;
      if (parseFuture != null && !parseFuture.isDone()) {
        parseFuture.cancel(true);
      }
      final WeakReference<ScanPlugin> weakThis = new WeakReference<>(this);
      parseFuture = parseExecutor.submit(() -> {
        ScanPlugin plugin = weakThis.get();
        if (plugin == null || plugin.flutterPluginBinding == null) return;
        String out = QRCodeDecoder.decodeQRCode(plugin.flutterPluginBinding.getApplicationContext(), path);
        // return result on main/UI thread
        new Handler(Looper.getMainLooper()).post(() -> {
          try {
            result.success(out);
          } catch (Exception e) {
            try { result.error("decode_error", e.getMessage(), null); } catch (Exception ignored) {}
          }
          // vibrate if output not null
          if (out != null) {
            try {
              Vibrator myVib = (Vibrator) plugin.flutterPluginBinding.getApplicationContext().getSystemService(VIBRATOR_SERVICE);
              if (myVib != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                  myVib.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                  myVib.vibrate(50);
                }
              }
            } catch (Exception ignored) {}
          }
        });
      });
    } else {
      result.notImplemented();
    }
  }
}
