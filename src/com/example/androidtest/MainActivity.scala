package com.example.androidtest

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLU

import android.view.Window
import android.view.WindowManager._
import android.view.WindowManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import android.view.MotionEvent
import android.view.GestureDetector.OnGestureListener
import android.util.Log
import android.support.v4.view.GestureDetectorCompat
import android.view.GestureDetector
import android.view.GestureDetector._
import android.content.pm.ActivityInfo
import scala.collection.mutable.ListBuffer
import android.view.View.OnTouchListener
import android.view.View
import android.support.v4.view.MotionEventCompat
import android.os.SystemClock
import scala.util.Random
import javax.microedition.khronos.opengles.GL11
import android.util.DisplayMetrics
import android.hardware.SensorEventListener
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.content.Context
import android.hardware.SensorManager
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix

object Screen {
  var width = 0
  var height = 0
}

object Force {
  var gravityX = 0.0
  var gravityY = 0.0
  var gravityZ = 0.0
  var forceX = 0.0
  var forceY = 0.0
  var forceZ = 0.0
}

object Flags {
  @volatile var startNewSystem = false
  var newSystemX = 0.0f
  var newSystemY = 0.0f
}

object Shaders {
  
  val vertexSource = """
        uniform    mat4 uMVPMatrix;
        attribute  vec4 vPosition;
        attribute float pointSize;
        attribute vec4 vColor;
        
        varying vec4 varColor;
        
        void main() {
          gl_Position = uMVPMatrix * vPosition;
          gl_PointSize = pointSize;
          varColor = vColor;
        }"""

  val fragmentSource = """
        precision mediump float;
        varying vec4 varColor;
        void main() {
          gl_FragColor = varColor;
        }"""
  
  var program = 0
}

object ShaderUtils {
  
  def checkGlError(op: String) = {
        var error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("blah", op + ": glError " + error);
            error = GLES20.glGetError()
            throw new RuntimeException(op + ": glError " + error);
        }
        
    }
  
  def createProgram(vertexSource: String , fragmentSource: String ) = {
    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

    var program = GLES20.glCreateProgram();
    if (program != 0) {
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachVertexShader")
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachPixelShader")
        GLES20.glLinkProgram(program)
        val linkStatus: Array[Int] = Array(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus(0) != GLES20.GL_TRUE) {
            Log.e("blah", "Could not link program: ")
            Log.e("blah", GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }
    program
  }

  def loadShader(shaderType: Int, source: String) = {
      var shader = GLES20.glCreateShader(shaderType)
      if (shader != 0) {
          GLES20.glShaderSource(shader, source)
          GLES20.glCompileShader(shader)
          val compiled = Array[Int](1)
          GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
          if (compiled(0) == 0) {
              Log.e("blah", "Could not compile shader " + shaderType + ":")
              Log.e("blah", GLES20.glGetShaderInfoLog(shader))
              GLES20.glDeleteShader(shader)
              shader = 0
          }
      }
      shader
  }
}

object Model {
  val particleSystems = new ListBuffer[ParticleSystem]()// = ParticleSystem(500)
}

class MainScala extends Activity with SensorEventListener {
  
  
  
  override def onAccuracyChanged(sensor: Sensor, accuracy: Int ) = {
      // not in use
  }
  
  override def onSensorChanged(event: SensorEvent) = {
    if (event.sensor.getType == Sensor.TYPE_GRAVITY) {
      Force.gravityX = event.values(0)
      Force.gravityY = event.values(1)
    } else if (event.sensor.getType == Sensor.TYPE_LINEAR_ACCELERATION) {
      Force.forceX = event.values(0)
      Force.forceY = event.values(1)
    }
  }

  override def onTouchEvent(event: MotionEvent) = {

    val action = MotionEventCompat.getActionMasked(event)
        
    action match {
        case (MotionEvent.ACTION_DOWN) =>
          Flags.startNewSystem = true
          Flags.newSystemX = event.getX
          Flags.newSystemY = 1920 - event.getY
            //ParticleSystem.explode(event.getX, 1920 - event.getY)
          
            true
        case (MotionEvent.ACTION_MOVE) =>
          
//            for (h <- 0 until event.getHistorySize;
//                 p <- 0 until event.getPointerCount) {
//              Model.vertices += event.getHistoricalX(p, h)
//              Model.vertices += 1920 - event.getHistoricalY(p, h)
//            }         
            
            true
        case _ =>
          true
    }
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    
    this.requestWindowFeature(Window.FEATURE_NO_TITLE)
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    val view = new GLSurfaceView(this)
    view.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    val renderer = new OpenGLRenderer()
    view.setEGLContextClientVersion(3)
    view.setRenderer(renderer)
    
    setContentView(view);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
    
    val displaymetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
    Screen.height = displaymetrics.heightPixels;
    Screen.width = displaymetrics.widthPixels;
    val mSensorManager = getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager];
    mSensorManager.registerListener(this,mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_FASTEST);
    mSensorManager.registerListener(this,mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_FASTEST);
  }

}

