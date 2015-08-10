package com.example.androidtest;

import android.opengl.GLSurfaceView.Renderer
import javax.microedition.khronos.opengles.GL10
import scala.collection.mutable.ListBuffer
import android.opengl.GLES20
import android.util.Log
import android.os.SystemClock
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig

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
    //	    gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
    //	    gl.glPointSize(15.0f)
    //	    gl.glEnable(GL10.GL_POINT_SMOOTH)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    GLES20.glEnable(GLES20.GL_BLEND);

    Log.e("com.example.androidtest", "2compiling shader");
    Shaders.program = ShaderUtils.createProgram(Shaders.vertexSource, Shaders.fragmentSource)
    GLES20.glUseProgram(Shaders.program)
    Log.e("com.example.androidtest", "2done compiling shader");
  }

  var elapsedTime = 0.0

  def onDrawFrame(gl: GL10) = {

    val startTime = SystemClock.elapsedRealtime

    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    // Get handle to shape's transformation matrix
    val mtrxhandle = GLES20.glGetUniformLocation(Shaders.program, "uMVPMatrix");

    // Apply the projection and view transformation
    GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, mtrxProjectionAndView, 0);

    if (Flags.startNewSystem) {
      val newSystem = ParticleSystem(1000)
      Model.particleSystems += newSystem
      newSystem.freeFall(Flags.newSystemX, Flags.newSystemY)
      Flags.startNewSystem = false
    }

    var index = 0
    val numSystems = Model.particleSystems.size
    val systemsToErase = ListBuffer[ParticleSystem]()

    while (index < numSystems) {
      val system = Model.particleSystems(index)
      if (!system.isInit) {
        system.init
      }
      system.draw(gl, Shaders.program)

      system.run(elapsedTime)

      if (system.isDone) {
        systemsToErase += system
      }
      index += 1
    }

    index = 0

    while (index < systemsToErase.size) {
      Model.particleSystems -= systemsToErase(index)
      index += 1
    }

    elapsedTime = (SystemClock.elapsedRealtime - startTime) / 1000.0
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
    for (i <- (0 until 16)) {
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
