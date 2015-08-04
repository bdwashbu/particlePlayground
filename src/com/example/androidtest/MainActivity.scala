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

object ParticleSystem {
  var particles = new ListBuffer[Particle]()
  
  
  def explode(x: Double, y: Double) = {
    ParticleSystem synchronized {
      for(part <- 0 to 100) {
        val rand = new Random()
        val newVelX = rand.nextDouble() * 2.0 - 1.0
        val newVelY = rand.nextDouble() * 2.0 - 1.0
        val dist = math.sqrt(newVelX * newVelX + newVelY * newVelY)
        val speed = rand.nextDouble() * 400.0
        val normVelX = newVelX / dist * speed
        val normVelY = newVelY / dist * speed
        val size = rand.nextDouble() * 14.0
        val life = rand.nextDouble() * 5.0
        particles += Particle(x, y, normVelX, normVelY, size, life)
      }
    }
  }
  
  def run(elapsedTime: Double) = {
    ParticleSystem synchronized {
      particles.foreach { particle =>
        particle.x += particle.xVel * elapsedTime
        particle.y += particle.yVel * elapsedTime
        particle.life -= elapsedTime
      }
      
      particles = particles.filter(particle => particle.life > 0.0)
    }
  }
  
  def getVertexArray: Array[Float] = {
    ParticleSystem synchronized {
      particles.flatMap{particle => List(particle.x.toFloat, particle.y.toFloat)}.toArray
    }
  }
  
  
}

case class Particle(var x: Double, var y: Double, var xVel: Double, var yVel: Double, size: Double, var life: Double)

class MainScala extends Activity {
  
  

  override def onTouchEvent(event: MotionEvent) = {

    val action = MotionEventCompat.getActionMasked(event)
        
    action match {
        case (MotionEvent.ACTION_DOWN) =>
          
            ParticleSystem.explode(event.getX, 1920 - event.getY)
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
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
  }

}

class OpenGLRenderer extends Renderer {

  def onSurfaceCreated(gl: GL10, config: EGLConfig) {
    gl.glShadeModel(GL10.GL_SMOOTH); //Enable Smooth Shading
    gl.glClearColor(0.0f, 1.0f, 0.0f, 0.5f); //Black Background
    gl.glClearDepthf(1.0f); //Depth Buffer Setup
    gl.glDisable(GL10.GL_DEPTH_TEST); //Enables Depth Testing
    gl.glDepthFunc(GL10.GL_LEQUAL); //The Type Of Depth Testing To Do

    //Really Nice Perspective Calculations
    gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
  }

  

  def onDrawFrame(gl: GL10) = {

    val startTime = SystemClock.elapsedRealtime
    
    gl.glClear(GL10.GL_COLOR_BUFFER_BIT |
      GL10.GL_DEPTH_BUFFER_BIT);
    gl.glColor4f(1.0f, 0.0f, 0.0f, 0.0f)
    gl.glLoadIdentity()
    gl.glPointSize(15.0f)

    val array = ParticleSystem.getVertexArray
    
    val vbb = ByteBuffer.allocateDirect(array.length * 4);
    vbb.order(ByteOrder.nativeOrder()); // Use native byte order
    val vertexBuffer = vbb.asFloatBuffer(); // Convert from byte to float
    vertexBuffer.put(array); // Copy data into buffer
    vertexBuffer.position(0); // Rewind

    gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertexBuffer);

    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glDrawArrays(GL10.GL_POINTS, 0, array.size / 2);

    gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
    
    val elapsedTime = (SystemClock.elapsedRealtime - startTime) / 1000.0
    
    ParticleSystem.run(elapsedTime)
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