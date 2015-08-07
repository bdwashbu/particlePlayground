package com.example.androidtest

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLU
import android.opengl.GLSurfaceView.Renderer
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

object Shaders {
  
  val vertexSource = """
        uniform    mat4 uMVPMatrix;
        attribute  vec4 vPosition;
        attribute float pointSize;
        
        void main() {
          gl_Position = uMVPMatrix * vPosition;
          gl_PointSize = pointSize;
        }"""

  val fragmentSource = """
        precision mediump float;
        void main() {
          gl_FragColor = vec4(0.0, 0.0, 1.0, 1.0);
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

case class ParticleSystem(numParticles: Int) {
  
  var isInit = false;
  var vertexHandle = Array(1)
  var sizeHandle = Array(1)
  var colorHandle = Array(1)
  
  val vbb = ByteBuffer.allocateDirect(numParticles * 2 * 4)
  vbb.order(ByteOrder.nativeOrder()) // Use native byte order
  val vertexBuffer = vbb.asFloatBuffer() // Convert from byte to float
  
  val sizebb = ByteBuffer.allocateDirect(numParticles * 4)
  sizebb.order(ByteOrder.nativeOrder()) // Use native byte order
  val sizeBuffer = sizebb.asFloatBuffer() // Convert from byte to float
  
  val colorbb = ByteBuffer.allocateDirect(numParticles * 4 * 4)
  colorbb.order(ByteOrder.nativeOrder()) // Use native byte order
  val colorBuffer = colorbb.asFloatBuffer() // Convert from byte to float
  var vertices = Array[Float]()
  var vertexBufferTri: FloatBuffer = null
  
  def init = {
    GLES20.glGenBuffers(1, vertexHandle, 0)
    GLES20.glGenBuffers(1, sizeHandle, 0)
    GLES20.glGenBuffers(1, colorHandle, 0)
    
   SetupTriangle
    
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexHandle(0));
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,  6 * 4, vertexBufferTri, GLES20.GL_DYNAMIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sizeHandle(0));
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,  numParticles * 1 * 4, sizeBuffer, GLES20.GL_DYNAMIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorHandle(0));
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,  numParticles * 4 * 4, colorBuffer, GLES20.GL_DYNAMIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    isInit = true
  }
  
  
  
  def SetupTriangle = {
        // We have create the vertices of our view.
        vertices = Array(
                   10.0f, 200f, 
                    10.0f, 100f, 
                    100f, 100f)
        
        // The vertex buffer.
        val bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBufferTri = bb.asFloatBuffer();
        vertexBufferTri.put(vertices);
        vertexBufferTri.position(0); 
    }
  
  def ReloadTriangle = {
        // We have create the vertices of our view.
 
        vertexBufferTri.position(0);
        vertexBufferTri.put(vertices);
        vertexBufferTri.position(0);
    }
  
  def draw(gl: GL10, program: Int) = {
      getVertexArray
       ReloadTriangle
//    getSizeArray
//    
//    val vertexPositionAttribute = GLES20.glGetAttribLocation(program, "vertexPosition");
//    //Log.e("com.example.androidtest", s"vertexPosition: $vertexPositionAttribute");
//    GLES20.glEnableVertexAttribArray(vertexPositionAttribute);
//    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexHandle(0));
//    GLES20.glVertexAttribPointer(vertexPositionAttribute, 2, GLES20.GL_FLOAT, false, 0, 0);
//
//    val sizeAttribute = GLES20.glGetAttribLocation(program, "pointSize");
//    //Log.e("com.example.androidtest", s"pointSize: $sizeAttribute");
//    GLES20.glEnableVertexAttribArray(sizeAttribute);
//    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sizeHandle(0));
//    GLES20.glVertexAttribPointer(sizeAttribute, 1, GLES20.GL_FLOAT, false, 0, 0);
//    
////    val colorAttribute = GLES20.glGetAttribLocation(program, "color");
////    Log.e("com.example.androidtest", s"color: $colorAttribute");
////    GLES20.glEnableVertexAttribArray(colorAttribute);
////    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorHandle(0));
////    GLES20.glVertexAttribPointer(colorAttribute, 4, GLES20.GL_FLOAT, false, 0, 0);
//    
//
//    gl.glDrawArrays(GLES20.GL_POINTS, 0, 1000)
    
    val mPositionHandle = GLES20.glGetAttribLocation(Shaders.program, "vPosition");
 
      // Enable generic vertex attribute array
      
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexHandle(0));
      GLES20.glEnableVertexAttribArray(mPositionHandle);
      GLES20.glVertexAttribPointer(mPositionHandle, 2,
                                     GLES20.GL_FLOAT, false,
                                     0, 0);
 
        
 
     // GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexHandle(0));
      //GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
      //          GLES20.GL_UNSIGNED_SHORT, 0);
      gl.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)

      // Disable vertex array
      GLES20.glDisableVertexAttribArray(mPositionHandle);
  }
  
  abstract case class Particle(var x: Double,
                      var y: Double,
                      var xVel: Double,
                      var yVel: Double,
                      var size: Double,
                      totalLife: Double) {
    var currentLife: Double = totalLife
    
    def next(particle: Particle, elapsedTime: Double)
    def onCollide(particle: Particle, value: Double): Double
  }

  var particles = new ListBuffer[Particle]()
  
  def explode(x: Double, y: Double) = {
    this synchronized {
      particles.clear
      for(part <- 0 until numParticles) {
        val rand = new Random()
        val newVelX = rand.nextDouble() * 2.0 - 1.0
        val newVelY = rand.nextDouble() * 2.0 - 1.0
        val dist = math.sqrt(newVelX * newVelX + newVelY * newVelY)
        val speed = rand.nextDouble() * 50.0 + 10.0
        val normVelX = newVelX / dist
        val normVelY = newVelY / dist
        val size = rand.nextDouble() * 11.0 + 4.0
        val totalLife = rand.nextDouble() * 5.0 + 2.0
        
        
        particles += new Particle(x, y, normVelX, normVelY, size, totalLife) {
          def next(particle: Particle, elapsedTime: Double) = {
            var speed = Math.sqrt(particle.xVel*particle.xVel + particle.yVel*particle.yVel)
            speed += 15000.0f / particle.size * elapsedTime
            
            particle.xVel *= speed
            particle.yVel *= speed
            
            particle.currentLife -= speed * 0.005 * elapsedTime
            particle.size = Math.max(particle.size * (particle.currentLife / particle.totalLife).toFloat, 1.0)
          }
          
          def onCollide(particle: Particle, value: Double) = {
            value * -1.0
          }
        }
      }
    }
  }
  
  def freeFall(x: Double, y: Double) = {
    this synchronized {
      particles.clear
      for(part <- 0 until numParticles) {
        val rand = new Random()
        val speed = rand.nextDouble() * 300.0 + 60.0
        val newVelX = rand.nextDouble() * 2.0 - 1.0
        val newVelY = rand.nextDouble() * 2.0 - 1.0
        val dist = math.sqrt(newVelX * newVelX + newVelY * newVelY)
        
        val normVelX = newVelX / dist * speed
        val normVelY = newVelY / dist * speed
        val size = rand.nextDouble() * 3.0 + 4.0
        val totalLife = rand.nextDouble() * 8.0 + 2.0
        
        
        particles += new Particle(x, y, normVelX, normVelY, size, totalLife) {
          def next(particle: Particle, elapsedTime: Double) = {
            particle.currentLife -= elapsedTime * 1.0
            particle.xVel += -Force.gravityX * elapsedTime * 600.0
            particle.yVel += -Force.gravityY * elapsedTime * 600.0
            particle.xVel += -Force.forceX * elapsedTime * 4300.0
            particle.yVel += -Force.forceY * elapsedTime * 4300.0
          }
          
          def onCollide(particle: Particle, value: Double) = {
            value * -1.0 * (particle.size / 20.0)
          }
        }
      }
    }
  }
  
  def run(elapsedTime: Double) = {
      particles.foreach { particle =>
        
        var nextX = particle.x + particle.xVel * elapsedTime
        var nextY = particle.y + particle.yVel * elapsedTime
        
        if (nextX < 0.0) {
          nextX = 0.0
          particle.xVel = particle.onCollide(particle, particle.xVel)
        } else if (nextX > Screen.width) {
          nextX = Screen.width
          particle.xVel = particle.onCollide(particle, particle.xVel)
        }
        
        if (nextY < 0.0) {
          nextY = 0.0
          particle.yVel = particle.onCollide(particle, particle.yVel)
        } else if (nextY > Screen.height) {
          nextY = Screen.height
          particle.yVel = particle.onCollide(particle, particle.yVel)
        }
        
        particle.x += particle.xVel * elapsedTime
        particle.y += particle.yVel * elapsedTime

        particle.next(particle, elapsedTime)
      }
  }
  
  def isDone = {
    particles.forall(particle => particle.currentLife <= 0.0)
  }
  
  def getVertexArray: FloatBuffer = {
      vertexBuffer.clear()
      vertexBuffer.put(particles.flatMap{particle => List(particle.x.toFloat, particle.y.toFloat)}.toArray) // Copy data into buffer
      vertexBuffer.position(0) // Rewind
      vertexBuffer
  }
  
  def getColorArray: FloatBuffer = {
      colorBuffer.clear
      colorBuffer.put(particles.flatMap { particle => 
        List(1.0f, 1.0f, 1.0f, (particle.currentLife / particle.totalLife).toFloat)
      }.toArray) // Copy data into buffer
      colorBuffer.position(0) // Rewind
      colorBuffer
  }
  
  def getSizeArray: FloatBuffer = {
      sizeBuffer.clear
      sizeBuffer.put(particles.map{particle => particle.size.toFloat * (particle.currentLife / particle.totalLife).toFloat}.toArray) // Copy data into buffer
      sizeBuffer.position(0) // Rewind
      sizeBuffer
  }
  
  
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
          
            //ParticleSystem.explode(event.getX, 1920 - event.getY)
          Model synchronized {
            val newSystem = ParticleSystem(1000)
            Model.particleSystems += newSystem
            newSystem.freeFall(event.getX, 1920 - event.getY)
          }
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
    view.setEGLContextClientVersion(2) // assume Opengl 2.0
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

class OpenGLRenderer extends Renderer {

  var mtrxProjectionAndView = Array.fill(16)(0.0f)
  
  
  
  def onSurfaceCreated(gl: GL10, config: EGLConfig) {
    //gl.glShadeModel(GL10.GL_SMOOTH); //Enable Smooth Shading
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f); //Black Background
    GLES20.glClearDepthf(1.0f); //Depth Buffer Setup
    GLES20.glDepthMask(false)
    GLES20.glDisable(GLES20.GL_DEPTH_TEST); //Enables Depth Testing
    GLES20.glDepthFunc(GLES20.GL_LEQUAL); //The Type Of Depth Testing To Do

    //Really Nice Perspective Calculations
//    gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
//    gl.glPointSize(15.0f)
//    gl.glEnable(GL10.GL_POINT_SMOOTH)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    GLES20.glEnable(GLES20.GL_BLEND);
    
    Log.e("com.example.androidtest", "2compiling shader");
    Shaders.program = ShaderUtils.createProgram(Shaders.vertexSource, Shaders.fragmentSource)
    GLES20.glUseProgram(Shaders.program)
    Log.e("com.example.androidtest", "2done compiling shader");
  }

  

  def onDrawFrame(gl: GL10) = {

    val startTime = SystemClock.elapsedRealtime
    
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
  // Get handle to shape's transformation matrix
    val mtrxhandle = GLES20.glGetUniformLocation(Shaders.program, "uMVPMatrix");
 
        // Apply the projection and view transformation
    GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, mtrxProjectionAndView, 0);

    Model synchronized {
      
      Model.particleSystems.foreach{ system =>
        if (!system.isInit) {
          system.init
        }
        system.draw(gl, Shaders.program)
      }

      val elapsedTime = (SystemClock.elapsedRealtime - startTime) / 1000.0
    
      Model.particleSystems.par.foreach{ system =>
        system.run(elapsedTime)
      }
      Model.particleSystems.foreach{ system =>
        if (system.isDone) {
          Model.particleSystems -= system
        }
      }
    }      
  }

  def onSurfaceChanged(gl: GL10, width: Int, height: Int) {
    // We need to know the current width and height.
        Screen.width = width;
        Screen.height = height;
 
        // Redo the Viewport, making it fullscreen.
        GLES20.glViewport(0, 0, Screen.width, Screen.height);
        
        val mtrxProjection = Array.fill(16)(0.0f)
        val mtrxView = Array.fill(16)(0.0f)
        
 
        // Clear our matrices
        for(i <- (0 until 16))
        {
            mtrxProjection(i) = 0.0f
            mtrxView(i) = 0.0f
            mtrxProjectionAndView(i) = 0.0f
        }
 
        // Setup our screen width and height for normal sprite translation.
        Matrix.orthoM(mtrxProjection, 0, 0f, width, 0.0f, height, 0, 50);
 
        // Set the camera position (View matrix)
        Matrix.setLookAtM(mtrxView, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
 
        // Calculate the projection and view transformation
        Matrix.multiplyMM(mtrxProjectionAndView, 0, mtrxProjection, 0, mtrxView, 0);
        
        
  }

}