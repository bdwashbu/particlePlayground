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

case class ParticleSystem(numParticles: Int) {
  
  var isInit = false;
  var vertexHandle = Array(1)
  
  val vbb = ByteBuffer.allocateDirect(numParticles * 7 * 4)
  vbb.order(ByteOrder.nativeOrder()) // Use native byte order
  val vertexBuffer = vbb.asFloatBuffer() // Convert from byte to float
  
  var colorAttribute = 0
  var mPositionHandle = 0
  var sizeAttribute = 0
  var fence: Int = 0
  var isDone = false
  
  def init = {
    GLES20.glGenBuffers(1, vertexHandle, 0)

    colorAttribute = GLES20.glGetAttribLocation(Shaders.program, "vColor");
    mPositionHandle = GLES20.glGetAttribLocation(Shaders.program, "vPosition");
    sizeAttribute = GLES20.glGetAttribLocation(Shaders.program, "pointSize");
    isInit = true
  }

  def draw(gl: GL10, program: Int) = {
     GLES20.glEnableVertexAttribArray(mPositionHandle);
     GLES20.glEnableVertexAttribArray(colorAttribute);
     GLES20.glEnableVertexAttribArray(sizeAttribute);
      
     GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexHandle(0));
     GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,  numParticles * 7 * 4, null, GLES20.GL_DYNAMIC_DRAW);
     val buffer = GLES30.glMapBufferRange(GLES20.GL_ARRAY_BUFFER, 0, numParticles * 7 * 4, GLES30.GL_MAP_WRITE_BIT | GLES30.GL_MAP_FLUSH_EXPLICIT_BIT |
        GLES30.GL_MAP_UNSYNCHRONIZED_BIT).asInstanceOf[ByteBuffer].order(ByteOrder.nativeOrder).asFloatBuffer()
     //GLES30.glClientWaitSync(fence, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, GLES30.GL_TIMEOUT_IGNORED);
//     buffer.order(ByteOrder.nativeOrder())
//     val byteBuffer = ByteBuffer.allocate(numParticles * 7 * 4);
//     byteBuffer.asFloatBuffer().put(vertexBuffer);
     buffer.put(vertexBuffer).position (0)
     //GLES30.glFlushMappedBufferRange(GLES20.GL_ARRAY_BUFFER, 0, numParticles * 7 * 4)
     GLES30.glUnmapBuffer(GLES20.GL_ARRAY_BUFFER)
     GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 28, 0);
     GLES20.glVertexAttribPointer(colorAttribute, 4, GLES20.GL_FLOAT, false, 28, 8);
     GLES20.glVertexAttribPointer(sizeAttribute, 1, GLES20.GL_FLOAT, false, 28, 24);
      
     gl.glDrawArrays(GLES20.GL_POINTS, 0, numParticles)

     // Disable vertex array
     GLES20.glDisableVertexAttribArray(mPositionHandle);
     GLES20.glDisableVertexAttribArray(colorAttribute);
     GLES20.glDisableVertexAttribArray(sizeAttribute);
     
    // val x = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE.toInt, 0.toInt)
     
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
  
  def freeFall(x: Double, y: Double) = {
      particles.clear
      for(part <- 0 until numParticles) {
        val rand = new Random()
        val speed = rand.nextDouble() * 600.0 + 60.0
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
  
  def run(elapsedTime: Double) = {
      var index = 0
      var areAllDone = false
    
      vertexBuffer.position(0)
      while (index < numParticles) {
        val particle = particles(index)
        
        if (index == 0 && particle.currentLife <= 0.0) {
          areAllDone = true
        } else {
          areAllDone &= particle.currentLife <= 0.0
        }
        
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
        
        vertexBuffer.put(Array(
            particle.x.toFloat, particle.y.toFloat,
            1.0f, 1.0f, 1.0f, (particle.currentLife / particle.totalLife).toFloat,
            particle.size.toFloat * (particle.currentLife / particle.totalLife).toFloat
        ))
        
        particle.next(particle, elapsedTime)
        index += 1
      }
      
      vertexBuffer.position(0)
      isDone = areAllDone
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

