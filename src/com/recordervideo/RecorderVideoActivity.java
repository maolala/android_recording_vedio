package com.recordervideo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import com.captchure.CacheUtil;
import com.captchure.CommonUtils;
import com.captchure.Utils;
import com.recordingvideo.R;

/**
 * 
 * @ClassName: RecorderVideoActivity 
 * @Description: 视频录制(倒计时)
 * @author WangFusheng 
 * @date 2015年12月4日 上午9:48:18
 */
public class RecorderVideoActivity extends FragmentActivity implements OnClickListener, SurfaceHolder.Callback, OnErrorListener,OnInfoListener {
	private final static String CLASS_LABEL = "RecordActivity";
	private PowerManager.WakeLock mWakeLock;
	private ImageView btnStart;// 开始录制按钮
	private ImageView btnStop;// 停止录制按钮
	
	private MediaRecorder mediaRecorder;// 录制视频的类
	private VideoView mVideoView;// 显示视频的控件
	
	String localPath = "";// 录制的视频路径
	
	private Camera mCamera;
	
	// 预览的宽高
	private int previewWidth = 480;
	private int previewHeight = 480;
	
	private int frontCamera = 0;// 0是后置摄像头，1是前置摄像头
	private Button btnSwitch;//前后摄像头切换
	
	Parameters cameraParameters = null;
	private SurfaceHolder mSurfaceHolder;
	int defaultVideoFrameRate = -1;
	
	private MyCount myCount;
	TextView tv;//显示倒计时数字

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
		// 选择支持半透明模式，在有surfaceview的activity中使用
//		getWindow().setFormat(PixelFormat.TRANSLUCENT);
		setContentView(R.layout.recorder_activity);
		
		initViews();
	}

	private void initViews() {
		btnSwitch = (Button) findViewById(R.id.switch_btn);
		btnSwitch.setOnClickListener(this);
		btnSwitch.setVisibility(View.VISIBLE);
		
		mVideoView = (VideoView) findViewById(R.id.mVideoView);
		
		btnStart = (ImageView) findViewById(R.id.recorder_start);
		btnStop = (ImageView) findViewById(R.id.recorder_stop);
		tv = (TextView) findViewById(R.id.tv);
		
		btnStart.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		
		mSurfaceHolder = mVideoView.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}


	@Override
	protected void onResume() {
		super.onResume();
		keepWakeLock();
		 if (!initCamera()) {
		 showFailDialog();
		 }
	}

	private void keepWakeLock() {
		if (mWakeLock == null) {
			// 获取唤醒锁,保持屏幕常亮
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,CLASS_LABEL);
			mWakeLock.acquire();
		}
	}

	@SuppressLint("NewApi")
	private boolean initCamera() {
		try {
			if (frontCamera == 0) {
				mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
			} else {
				mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
			}
			Camera.Parameters camParams = mCamera.getParameters();
			mCamera.lock();
			mSurfaceHolder = mVideoView.getHolder();
			mSurfaceHolder.addCallback(this);
			mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			mCamera.setDisplayOrientation(90);

		} catch (RuntimeException ex) {
			return false;
		}
		return true;
	}

	private void handleSurfaceChanged() {
		if (mCamera == null) {
			finish();
			return;
		}
		boolean hasSupportRate = false;
		List<Integer> supportedPreviewFrameRates = mCamera.getParameters().getSupportedPreviewFrameRates();
		if (supportedPreviewFrameRates != null&& supportedPreviewFrameRates.size() > 0) {
			Collections.sort(supportedPreviewFrameRates);
			for (int i = 0; i < supportedPreviewFrameRates.size(); i++) {
				int supportRate = supportedPreviewFrameRates.get(i);

				if (supportRate == 15) {
					hasSupportRate = true;
				}

			}
			if (hasSupportRate) {
				defaultVideoFrameRate = 15;
			} else {
				defaultVideoFrameRate = supportedPreviewFrameRates.get(0);
			}

		}
		// 获取摄像头的所有支持的分辨率
		List<Camera.Size> resolutionList = Utils.getResolutionList(mCamera);
		if (resolutionList != null && resolutionList.size() > 0) {
			Collections.sort(resolutionList, new Utils.ResolutionComparator());
			Camera.Size previewSize = null;
			boolean hasSize = false;
			// 如果摄像头支持640*480，那么强制设为640*480
			for (int i = 0; i < resolutionList.size(); i++) {
				Size size = resolutionList.get(i);
				if (size != null && size.width == 640 && size.height == 480) {
					previewSize = size;
					previewWidth = previewSize.width;
					previewHeight = previewSize.height;
					hasSize = true;
					break;
				}
			}
			// 如果不支持设为中间的那个
			if (!hasSize) {
				int mediumResolution = resolutionList.size() / 2;
				if (mediumResolution >= resolutionList.size())
					mediumResolution = resolutionList.size() - 1;
				previewSize = resolutionList.get(mediumResolution);
				previewWidth = previewSize.width;
				previewHeight = previewSize.height;

			}

		}

	}

	@Override
	protected void onStart() {
		super.onStart();
		myCount = new MyCount(7 * 1000, 1000);
	}

	@Override
	protected void onStop() {
		super.onStop();
		myCount.cancel();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.switch_btn:
			switchCamera();
			break;
		case R.id.recorder_start:
			// 开始录制
			if (!startRecording())return;
			Toast.makeText(this, R.string.The_video_to_start,Toast.LENGTH_SHORT).show();
			btnSwitch.setVisibility(View.INVISIBLE);
			btnStart.setVisibility(View.INVISIBLE);
			btnStop.setVisibility(View.VISIBLE);
			// 重置其他
			myCount.start();
			break;
		case R.id.recorder_stop:
			// 停止拍摄
			myCount.cancel();
			tv.setText("");
			stopRecording();
			btnSwitch.setVisibility(View.VISIBLE);
			btnStart.setVisibility(View.VISIBLE);
			btnStop.setVisibility(View.INVISIBLE);
			String st3 = getResources().getString(R.string.Whether_to_send);
			new AlertDialog.Builder(RecorderVideoActivity.this)
					.setMessage(st3)
					.setPositiveButton(R.string.ok,new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface arg0,
										int arg1) {
									arg0.dismiss();
									sendVideo(null);

								}
							})
					.setNegativeButton(R.string.cancel,new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,int which) {
									dialog.dismiss();
									if (mCamera == null) {
										initCamera();
									}
									try {
										mCamera.setPreviewDisplay(mSurfaceHolder);
										mCamera.startPreview();
										handleSurfaceChanged();
									} catch (IOException e1) {

									}
								}
							})
					 .setCancelable(false).show();
			break;

		default:
			break;
		}
		
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {
		// 将holder，这个holder为开始在oncreat里面取得的holder，将它赋给surfaceHolder
		mSurfaceHolder = holder;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (mCamera == null) {
			if (!initCamera()) {
				showFailDialog();
				return;
			}

		}
		try {
			mCamera.setPreviewDisplay(mSurfaceHolder);
			mCamera.startPreview();
			handleSurfaceChanged();
		} catch (Exception e1) {
			showFailDialog();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
	}

	public boolean startRecording() {
		if (mediaRecorder == null) {
			if (!initRecorder())
				return false;
		}
		mediaRecorder.setOnInfoListener(this);
		mediaRecorder.setOnErrorListener(this);
		mediaRecorder.start();
		return true;
	}

	@SuppressLint("NewApi")
	private boolean initRecorder() {
		if (!CommonUtils.isExitsSdcard()) {
			showNoSDCardDialog();
			return false;
		}

		if (mCamera == null) {
			if (!initCamera()) {
				showFailDialog();
				return false;
			}
		}
		mVideoView.setVisibility(View.VISIBLE);
		mCamera.stopPreview();
		mediaRecorder = new MediaRecorder();
		mCamera.unlock();
		mediaRecorder.setCamera(mCamera);
		mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		// 设置录制视频源为Camera（相机）
		mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		if (frontCamera == 1) {
			mediaRecorder.setOrientationHint(270);
		} else {
			mediaRecorder.setOrientationHint(90);
		}
		// 设置录制完成后视频的封装格式THREE_GPP为3gp.MPEG_4为mp4
		mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		// 设置录制的视频编码h263 h264
		mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		// 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错
		mediaRecorder.setVideoSize(previewWidth, previewHeight);
		// 设置视频的比特率
		mediaRecorder.setVideoEncodingBitRate(384 * 1024);
		// // 设置录制的视频帧率。必须放在设置编码和格式的后面，否则报错
		if (defaultVideoFrameRate != -1) {
			mediaRecorder.setVideoFrameRate(defaultVideoFrameRate);
		}
		// 设置视频文件输出的路径
		localPath = CacheUtil.getSDCacheDir("video") + "/"+ System.currentTimeMillis() + ".mp4";
		mediaRecorder.setOutputFile(localPath);
		//设置最大录制视频时间7秒
		mediaRecorder.setMaxDuration(7000);
		mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		try {
			mediaRecorder.prepare();
		} catch (IllegalStateException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;

	}
	//停止录制
	public void stopRecording() {
		if (mediaRecorder != null) {
			mediaRecorder.setOnErrorListener(null);
			mediaRecorder.setOnInfoListener(null);
			try {
				mediaRecorder.stop();
			} catch (IllegalStateException e) {
			}
		}
		releaseRecorder();
		if (mCamera != null) {
			mCamera.stopPreview();
			releaseCamera();
		}
	}
	//释放
	private void releaseRecorder() {
		if (mediaRecorder != null) {
			mediaRecorder.release();
			mediaRecorder = null;
		}
	}
	//释放摄像头
	protected void releaseCamera() {
		try {
			if (mCamera != null) {
				mCamera.stopPreview();
				mCamera.release();
				mCamera = null;
			}
		} catch (Exception e) {
		}
	}

	@SuppressLint("NewApi")
	public void switchCamera() {

		if (mCamera == null) {
			return;
		}
		if (Camera.getNumberOfCameras() >= 2) {
			btnSwitch.setEnabled(false);
			if (mCamera != null) {
				mCamera.stopPreview();
				mCamera.release();
				mCamera = null;
			}

			switch (frontCamera) {
			case 0:
				mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
				frontCamera = 1;
				break;
			case 1:
				mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
				frontCamera = 0;
				break;
			}
			try {
				mCamera.lock();
				mCamera.setDisplayOrientation(90);
				mCamera.setPreviewDisplay(mVideoView.getHolder());
				mCamera.startPreview();
			} catch (IOException e) {
				mCamera.release();
				mCamera = null;
			}
			btnSwitch.setEnabled(true);

		}

	}

	MediaScannerConnection msc = null;
	ProgressDialog progressDialog = null;

	public void sendVideo(View view) {
		if (TextUtils.isEmpty(localPath)) {
			return;
		}
		if (msc == null)
			msc = new MediaScannerConnection(this,
					new MediaScannerConnectionClient() {
						@Override
						public void onScanCompleted(String path, Uri uri) {
							msc.disconnect();
							progressDialog.dismiss();
							Log.d("path", "localPaht:" + localPath
									+ "&&&&&&path:" + path + "&&&&&&&&&&uri:"
									+ uri.getPath());
							// Toast.makeText(RecorderVideoActivity.this,
							// path+"##"+uri.getPath(), 3000).show();
							// setResult(RESULT_OK, getIntent().putExtra("uri",
							// uri));
							setResult(RESULT_OK,getIntent().putExtra("path", path));
							RecorderVideoActivity.this.finish();
						}

						@Override
						public void onMediaScannerConnected() {
							msc.scanFile(localPath, "video/*");
						}
					});

		if (progressDialog == null) {
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("保存中...");
			progressDialog.setCancelable(false);
		}
		progressDialog.show();
		msc.connect();

	}

	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
			stopRecording();
			btnSwitch.setVisibility(View.VISIBLE);
			btnStart.setVisibility(View.VISIBLE);
			btnStop.setVisibility(View.INVISIBLE);
			if (localPath == null) {
				return;
			}

		}

	}

	@Override
	public void onError(MediaRecorder mr, int what, int extra) {
		stopRecording();
		Toast.makeText(this,"Recording error has occurred. Stopping the recording",Toast.LENGTH_SHORT).show();

	}

	public void saveBitmapFile(Bitmap bitmap) {
		File file = new File(Environment.getExternalStorageDirectory(), "a.jpg");
		try {
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
			bos.flush();
			bos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		releaseCamera();

		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}

	}

	@Override
	public void onBackPressed() {
		releaseRecorder();
		releaseCamera();
		finish();
	}
	
	private void showFailDialog() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.prompt)
				.setMessage(R.string.Open_the_equipment_failure)
				.setPositiveButton(R.string.ok,new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog,int which) {
							finish();
						}
				 })
				.setCancelable(false)
				.show();
	}

	private void showNoSDCardDialog() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.prompt)
				.setMessage("No sd card!")
				.setPositiveButton(R.string.ok,new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,int which) {
								finish();

							}
						})
				.setCancelable(false)
				.show();
	}
	//倒计时
	class MyCount extends CountDownTimer {
		public MyCount(long millisInFuture, long countDownInterval) {
			super(millisInFuture, countDownInterval);
		}

		@Override
		public void onFinish() {
			tv.setText("");
			String st3 = getResources().getString(R.string.Whether_to_send);
			new AlertDialog.Builder(RecorderVideoActivity.this)
					.setMessage(st3)
					.setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface arg0,
										int arg1) {
									arg0.dismiss();
									sendVideo(null);

								}
							})
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,int which) {
									dialog.dismiss();
									if (mCamera == null) {
										initCamera();
									}
									try {
										mCamera.setPreviewDisplay(mSurfaceHolder);
										mCamera.startPreview();
										handleSurfaceChanged();
									} catch (IOException e1) {
									}
								}
							})
					 .setCancelable(false).show();

		}

		@Override
		public void onTick(long millisUntilFinished) {
			tv.setText((millisUntilFinished / 1000) + "");
		}
	}
}
