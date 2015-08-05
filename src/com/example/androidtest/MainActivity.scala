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

object Model {
  val particleSystem = ParticleSystem(500)
}

case class ParticleSystem(numParticles: Int) {
  
  val vbb = ByteBuffer.allocateDirect(numParticles * 2 * 4)
  vbb.order(ByteOrder.nativeOrder()) // Use native byte order
  val vertexBuffer = vbb.asFloatBuffer() // Convert from byte to float
  
  val sizebb = ByteBuffer.allocateDirect(numParticles * 4)
  sizebb.order(ByteOrder.nativeOrder()) // Use native byte order
  val sizeBuffer = sizebb.asFloatBuffer() // Convert from byte to float
  
  val colorbb = ByteBuffer.allocateDirect(numParticles * 4 * 4)
  colorbb.order(ByteOrder.nativeOrder()) // Use native byte order
  val colorBuffer = colorbb.asFloatBuffer() // Convert from byte to float
  
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
        val size = rand.nextDouble() * 5.0 + 4.0
        val totalLife = rand.nextDouble() * 5.0 + 2.0
        
        
        particles += new Particle(x, y, normVelX, normVelY, size, totalLife) {
          def next(particle: Particle, elapsedTime: Double) = {
            particle.currentLife -= elapsedTime * 1.0
            particle.xVel += -Force.gravityX * elapsedTime * 600.0
            particle.yVel += -Force.gravityY * elapsedTime * 600.0
            particle.xVel += -Force.forceX * elapsedTime * 6300.0
            particle.yVel += -Force.forceY * elapsedTime * 6300.0
          }
          
          def onCollide(particle: Particle, value: Double) = {
            value * -1.0 * (particle.size / 20.0)
          }
        }
      }
    }
  }
  
  def run(elapsedTime: Double) = {
    this synchronized {
      particles.par.foreach { particle =>
        
        var nextX = particle.x + particle.xVel * elapsedTime
        var nextY = particle.y + particle.yVel * elapsedTime
        
        if (nextX < 0.0) {
          nextX = 0.0
          particle.xVel = particle.onCollide(particle, particle.xVel)
        }
        
        if (nextX > Screen.width) {
          nextX = Screen.width
          particle.xVel = particle.onCollide(particle, particle.xVel)
        }
        
        if (nextY < 0.0) {
          nextY = 0.0
          particle.yVel = particle.onCollide(particle, particle.yVel)
        }
        
        if (nextY > Screen.height) {
          nextY = Screen.height
          particle.yVel = particle.onCollide(particle, particle.yVel)
        }
        
        particle.x += particle.xVel * elapsedTime
        particle.y += particle.yVel * elapsedTime

        particle.next(particle, elapsedTime)
      }
      
      particles = particles.filter(particle => particle.currentLife > 0.0)
    }
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
      //Log.v("blah", event.values(0).toString);
      Force.gravityX = event.values(0)
      Force.gravityY = event.values(1)
//        gravity[0] = event.values[0];
//        gravity[1] = event.values[1];
//        gravity[2] = event.values[2];
    } else if (event.sensor.getType == Sensor.TYPE_LINEAR_ACCELERATION) {
      //Log.v("blah", event.values(0).toString);
      Force.forceX = event.values(0)
      Force.forceY = event.values(1)
//        gravity[0] = event.values[0];
//        gravity[1] = event.values[1];
//        gravity[2] = event.values[2];
    }
  }

  override def onTouchEvent(event: MotionEvent) = {

    val action = MotionEventCompat.getActionMasked(event)
        
    action match {
        case (MotionEvent.ACTION_DOWN) =>
          
            //ParticleSystem.explode(event.getX, 1920 - event.getY)
            Model.particleSystem.freeFall(event.getX, 1920 - event.getY)
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
    super.onCreate(savedInstanceState);
    this.requestWindowFeature(Window.FEATURE_NO_TITLE); // (NEW)
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN); // (NEW)
    val view = new GLSurfaceView(this);
    view.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    view.setRenderer(new OpenGLRenderer());
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

  def onSurfaceCreated(gl: GL10, config: EGLConfig) {
    gl.glShadeModel(GL10.GL_SMOOTH); //Enable Smooth Shading
    gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f); //Black Background
    gl.glClearDepthf(1.0f); //Depth Buffer Setup
    gl.glDisable(GL10.GL_DEPTH_TEST); //Enables Depth Testing
    gl.glDepthFunc(GL10.GL_LEQUAL); //The Type Of Depth Testing To Do

    //Really Nice Perspective Calculations
    gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
    gl.glPointSize(15.0f)
    gl.glEnable(GL10.GL_POINT_SMOOTH)
    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
    gl.glEnable(GL10.GL_BLEND);
  }

  

  def onDrawFrame(gl: GL10) = {

    val startTime = SystemClock.elapsedRealtime
    
    gl.glClear(GL10.GL_COLOR_BUFFER_BIT |
      GL10.GL_DEPTH_BUFFER_BIT);
    gl.glColor4f(0.0f, 0.0f, 1.0f, 1.0f)
    gl.glLoadIdentity()
    
    
    var vertexArray: FloatBuffer = null
    var sizeArray: FloatBuffer = null
    var colorArray: FloatBuffer = null

    Model.particleSystem synchronized {
      vertexArray = Model.particleSystem.getVertexArray
      sizeArray = Model.particleSystem.getSizeArray
      colorArray = Model.particleSystem.getColorArray
    }

    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
    gl.glEnableClientState(GL11.GL_POINT_SIZE_ARRAY_OES)
    gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
    
    gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertexArray)
    gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorArray)
    gl.asInstanceOf[GL11].glPointSizePointerOES(GL10.GL_FLOAT, 0, sizeArray)
    gl.glDrawArrays(GL10.GL_POINTS, 0, 500)

    gl.glDisableClientState(GL11.GL_POINT_SIZE_ARRAY_OES)
    gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
    
    val elapsedTime = (SystemClock.elapsedRealtime - startTime) / 1000.0
    
    Model.particleSystem.run(elapsedTime)
  }

  def onSurfaceChanged(gl: GL10, width: Int, height: Int) {
    gl.glViewport(0, 0, width, height); //Reset The Current Viewport
    gl.glMatrixMode(GL10.GL_PROJECTION); //Select The Projection Matrix
    gl.glLoadIdentity(); //Reset The Projection Matrix

    //Calculate The Aspect Ratio Of The Window
    GLU.gluOrtho2D(gl, 0, width, 0, height);
    gl.glMatrixMode(GL10.GL_MODELVIEW); //Select The Modelview Matrix
    gl.glLoadIdentity(); //Reset The Modelview Matrix
  }

}