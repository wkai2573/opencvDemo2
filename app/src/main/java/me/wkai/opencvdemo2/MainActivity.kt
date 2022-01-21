package me.wkai.opencvdemo2

import android.Manifest.permission.*
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.OrientationEventListener
import android.view.SurfaceView
import android.view.WindowManager.LayoutParams.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Core.*
import org.opencv.imgproc.Imgproc.resize
import java.util.*


private const val REQUEST_CODE_PERMISSIONS = 111
private val REQUIRED_PERMISSIONS = arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, RECORD_AUDIO, ACCESS_FINE_LOCATION)

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

	private val viewFinder:JavaCamera2View by lazy { findViewById(R.id.cameraView) }
	private val rotation_tv:TextView by lazy { findViewById(R.id.rotation_tv) }

	// image storage
	lateinit var imageMat: Mat
	lateinit var grayMat: Mat

	// 人臉庫 face library
	var faceDetector: CascadeClassifier? = null
	lateinit var faceDir:File
	var imageRatio = 0.0 // scale down ratio
	var screenRotation = 0

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
		viewFinder.setCameraPermissionGranted() //新手機必須，否則黑屏

		//方向事件監聽器
		val mOrientationEventListener = object : OrientationEventListener(this) {
			override fun onOrientationChanged(orientation:Int) {
				//監控方向值以確定目標旋轉值 Monitors orientation values to determine the target rotation value
				when (orientation) {
					in 45..134 -> {
						rotation_tv.text = getString(R.string.n_270_degree)
						screenRotation = 270
					}
					in 135..224 -> {
						rotation_tv.text = getString(R.string.n_180_degree)
						screenRotation = 180
					}
					in 225..314 -> {
						rotation_tv.text = getString(R.string.n_90_degree)
						screenRotation = 90
					}
					else -> {
						rotation_tv.text = getString(R.string.n_0_degree)
						screenRotation = 0
					}
				}
			}
		}
		if (mOrientationEventListener.canDetectOrientation()) {
			mOrientationEventListener.enable();
		} else {
			mOrientationEventListener.disable();
		}

		callFaceDetector()
	}

	//調用人臉檢測器
	private fun callFaceDetector() {
		try {
			lgi(OPENCV_SUCCESSFUL)

			loadFaceLib()

			if (faceDetector!!.empty()) {
				faceDetector = null
			} else {
				faceDir.delete()
			}
			viewFinder.enableView()
		} catch (e:IOException) {
			lge(OPENCV_FAIL)
			shortMsg(this@MainActivity, OPENCV_FAIL)
			e.printStackTrace()
		}
	}

	//加載人臉庫
	private fun loadFaceLib() {
		try {
			val modelInputStream = resources.openRawResource(R.raw.haarcascade_frontalface_alt2)

			// create a temp directory
			faceDir = getDir(FACE_DIR, Context.MODE_PRIVATE)

			// create a model file
			val faceModel = File(faceDir, FACE_MODEL)

			if (!faceModel.exists()) { // copy model
				// copy model to new face library
				val modelOutputStream = FileOutputStream(faceModel)

				val buffer = ByteArray(byteSize)
				var byteRead = modelInputStream.read(buffer)
				while (byteRead != -1) {
					modelOutputStream.write(buffer, 0, byteRead)
					byteRead = modelInputStream.read(buffer)
				}

				modelInputStream.close()
				modelOutputStream.close()
			}

			faceDetector = CascadeClassifier(faceModel.absolutePath)
		} catch (e:IOException) {
			lge("Error loading cascade face model...$e")
		}
	}

	/** 繪製臉部矩形 Draw rectangle of the face:
   * x: x-coor of upper corner
   * y: y-coor of upper corner
   * w: x-coor of opposite corner
   * h: y-coor of opposite corner
   * color: RGB
   */
	fun rectFace(x: Double, y: Double, w: Double, h: Double, color:Scalar) {
		Imgproc.rectangle(
			imageMat, // image
			Point(x, y), // upper corner
			Point(w, h),  // opposite corner
			color  // RGB
		)
	}

	/** 畫一個點 Draw a dot:
   * x: x-coor of center
   * y: y-coor of center
   * color: RGB
   */
	fun drawDot(x: Double, y:Double, color:Scalar) {
		Imgproc.circle(
			imageMat, // image
			Point(x, y),  // center
			4, // radius
			color, // RGB
			-1, // thickness: -1 = filled in
			8 // line type
		)
	}

	//圖片縮小，並依據手機角度 調整圖片方向 以適應openCv偵測
	fun get480Image(src: Mat): Mat {
		val imageSize = Size(src.width().toDouble(), src.height().toDouble())
		imageRatio = ratioTo480(imageSize)

		// Downsize image
		val dst = Mat()
		val dstSize = Size(imageSize.width * imageRatio, imageSize.height * imageRatio)
		resize(src, dst, dstSize)

		//依據手機角度調整圖片 Check rotation
		when (screenRotation) {
			0 -> {
				rotate(dst, dst, ROTATE_90_CLOCKWISE)
				flip(dst, dst, 1) //水平翻轉
			}
			180 -> {
				rotate(dst, dst, ROTATE_90_COUNTERCLOCKWISE)
			}
			270 -> {
				flip(dst, dst, 0) //垂直翻轉
			}
		}
		return dst
	}

	fun ratioTo480(size: Size):Double {
		return 480 / kotlin.math.min(size.width, size.height)
	}

	//畫臉矩形
	fun drawFaceRectangle() {
		val faceRects = MatOfRect()
		faceDetector!!.detectMultiScale(get480Image(grayMat), faceRects) //從圖片偵測人臉 取得人臉位置矩形

		val scrW = imageMat.width().toDouble()
		val scrH = imageMat.height().toDouble()

		for (rect in faceRects.toArray()) {
			var x = rect.x.toDouble()
			var y = rect.y.toDouble()
			var w = 0.0
			var h = 0.0
			var rw = rect.width.toDouble() // rectangle width
			var rh = rect.height.toDouble() // rectangle height

			if (imageRatio.equals(1.0)) {
				w = x + rw
				h = y + rh
			} else {
				x /= imageRatio
				y /= imageRatio
				rw /= imageRatio
				rh /= imageRatio
				w = x + rw
				h = y + rh
			}

			//依手機角度 調整繪製位置
			when (screenRotation) {
				90 -> {
					rectFace(x, y, w, h, RED)
					drawDot(x, y, GREEN)
				}
				0 -> {
					rectFace(y, x, h, w, RED)
					drawDot(y, x, GREEN)
				}
				180 -> {
					rectFace(y, x, h, w, RED)
					drawDot(y, x, GREEN)
					// fix height
					val yFix = scrH - y + 150 //不知道是什麼，手機底部似乎多了一段距離，要加上高度約150
					val hFix = yFix - rh
					rectFace(yFix, x, hFix, w, YELLOW)
					drawDot(yFix, x, BLUE)
				}
				270 -> {
					val yFix = scrH - y
					val hFix = yFix - rh
					rectFace(x, yFix, w, hFix, RED)
					drawDot(x, yFix, GREEN)
				}
			}
		}
	}

	//檢查openCV
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

		// Face model
		private const val FACE_DIR = "facelib"
		private const val FACE_MODEL = "haarcascade_frontalface_alt2.xml"
		private const val byteSize = 4096 // buffer size

		// RGB
		private val YELLOW = Scalar(255.0, 255.0, 0.0)
		private val BLUE = Scalar(0.0, 0.0, 255.0)
		private val RED = Scalar(255.0, 0.0, 0.0)
		private val GREEN = Scalar(0.0, 255.0, 0.0)
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
//		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, cvBaseLoaderCallback)
		checkOpenCV(this)
		viewFinder?.let { viewFinder.enableView() }
	}

	override fun onPause() {
		super.onPause()
		viewFinder?.let { viewFinder.disableView() }
	}

	override fun onDestroy() {
		super.onDestroy()
		viewFinder?.let { viewFinder.disableView() }
		if (faceDir.exists()) faceDir.delete()
	}


	//相機繼承
	override fun onCameraViewStarted(width: Int, height: Int) {
		imageMat = Mat(width, height, CvType.CV_8UC4)
		grayMat = Mat(width, height, CvType.CV_8UC4)
	}
	override fun onCameraViewStopped() {
		imageMat.release()
		grayMat.release()
	}
	override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
		imageMat = inputFrame!!.rgba()
		grayMat = inputFrame.gray()
		imageRatio = 1.0
		// detect face rectangle
		drawFaceRectangle()
		return imageMat
	}
}