package me.wkai.opencvdemo2

import android.Manifest.permission.*
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager.LayoutParams.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.*
import org.opencv.core.CvType
import org.opencv.core.Mat

private const val REQUEST_CODE_PERMISSIONS = 111
private val REQUIRED_PERMISSIONS = arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, RECORD_AUDIO, ACCESS_FINE_LOCATION)

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

	private val viewFinder by lazy { findViewById<JavaCamera2View>(R.id.cameraView) }
	lateinit var cvBaseLoaderCallback:BaseLoaderCallback
	lateinit var imageMat: Mat // 圖像存儲 image storage

	override fun onCreate(savedInstanceState:Bundle?) {
		super.onCreate(savedInstanceState)
		window.clearFlags(FLAG_FORCE_NOT_FULLSCREEN)
		window.setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN)
		window.addFlags(FLAG_KEEP_SCREEN_ON)
		setContentView(R.layout.activity_main)

		// 請求相機權限 Request camera permissions
		if (allPermissionsGranted()) {
			checkOpenCV(this)
		} else {
			ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
		}

		viewFinder.visibility = SurfaceView.VISIBLE
		viewFinder.setCameraIndex(CameraCharacteristics.LENS_FACING_FRONT)
		viewFinder.setCvCameraViewListener(this)

		cvBaseLoaderCallback = object : BaseLoaderCallback(this) {
			override fun onManagerConnected(status: Int) {
				when (status) {
					SUCCESS -> {
						lgi(OPENCV_SUCCESSFUL)
						shortMsg(this@MainActivity, OPENCV_SUCCESSFUL)
						viewFinder.enableView()
					}
					else -> super.onManagerConnected(status)
				}
			}
		}
	}

//	private fun callFaceDetector() {
//		try {
//			lgi(OPENCV_SUCCESSFUL)
//
//			loadFaceLib()
//
//			if (faceDetector!!.empty()) {
//				faceDetector = null
//			} else {
//				faceDir.delete()
//			}
//			viewFinder.enableView()
//		} catch (e:IOException) {
//			lge(OPENCV_FAIL)
//			shortMsg(this@MainActivity, OPENCV_FAIL)
//			e.printStackTrace()
//		}
//	}
//
//	private fun loadFaceLib() {
//		try {
//			val modelInputStream =
//				resources.openRawResource(R.raw.haarcascade_frontalface_alt2)
//
//			// create a temp directory
//			faceDir = getDir(FACE_DIR, Context.MODE_PRIVATE)
//
//			// create a model file
//			val faceModel = File(faceDir, FACE_MODEL)
//
//			if (!faceModel.exists()) { // copy model
//				// copy model to new face library
//				val modelOutputStream = FileOutputStream(faceModel)
//
//				val buffer = ByteArray(byteSize)
//				var byteRead = modelInputStream.read(buffer)
//				while (byteRead != -1) {
//					modelOutputStream.write(buffer, 0, byteRead)
//					byteRead = modelInputStream.read(buffer)
//				}
//
//				modelInputStream.close()
//				modelOutputStream.close()
//			}
//
//			faceDetector = CascadeClassifier(faceModel.absolutePath)
//		} catch (e: IOException) {
//			lge("Error loading cascade face model...$e")
//		}
//	}

	private fun checkOpenCV(context: Context) {
		if (OpenCVLoader.initDebug()) {
			shortMsg(context, OPENCV_SUCCESSFUL)
			lgd("OpenCV started...")
		} else {
			lge("OPENCV_PROBLEM")
		}
	}

	companion object {
		val TAG = "MYLOG " + MainActivity::class.java.simpleName
		fun lgd(s: String) = Log.d(TAG, s)
		fun lge(s: String) = Log.e(TAG, s)
		fun lgi(s: String) = Log.i(TAG, s)

		fun shortMsg(context: Context, s: String) =
			Toast.makeText(context, s, Toast.LENGTH_SHORT).show()

		// messages:
		private const val OPENCV_SUCCESSFUL = "OpenCV Loaded Successfully!"
		private const val OPENCV_FAIL = "Could not load OpenCV!!!"
		private const val OPENCV_PROBLEM = "There's a problem in OpenCV."
		private const val PERMISSION_NOT_GRANTED = "Permissions not granted by the user."
	}

	/**
	 * 權限請求對話框的處理結果，請求是否被授予？如果是，請啟動相機。否則顯示吐司
	 * Process result from permission request dialog box, has the request
	 * been granted? If yes, start Camera. Otherwise display a toast
	 */
	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == REQUEST_CODE_PERMISSIONS) {
			if (allPermissionsGranted()) {
				checkOpenCV(this)
			} else {
				shortMsg(this, PERMISSION_NOT_GRANTED)
				finish()
			}
		}
	}

	/**
	 * 檢查清單中指定的所有權限是否已被授予
	 * Check if all permission specified in the manifest have been granted
	 */
	private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
		ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
	}


	override fun onResume() {
		super.onResume()
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, cvBaseLoaderCallback)
//		checkOpenCV(this)
//		viewFinder?.let { viewFinder.enableView() }
	}

	override fun onPause() {
		super.onPause()
		viewFinder?.let { viewFinder.disableView() }
	}

	override fun onDestroy() {
		super.onDestroy()
		viewFinder?.let { viewFinder.disableView() }
	}


	//相機繼承
	override fun onCameraViewStarted(width: Int, height: Int) {
		imageMat = Mat(width, height, CvType.CV_8UC4)
	}
	override fun onCameraViewStopped() {
		imageMat.release()
	}
	override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
		imageMat = inputFrame!!.rgba()
		return imageMat
	}
}