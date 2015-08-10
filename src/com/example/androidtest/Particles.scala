package com.example.androidtest

import java.nio.ByteBuffer
import android.opengl.GLES20
import scala.util.Random
import scala.collection.mutable.ListBuffer
import android.opengl.GLES30
import java.nio.ByteOrder
import javax.microedition.khronos.opengles.GL10
import java.nio.FloatBuffer

case class ParticleSystem(numParticles: Int) {

  var isInit = false;
  var vertexHandle = Array(1)

  val numBatches = 10
  val particlesPerBatch = numParticles / numBatches

  val vbos = ListBuffer[FloatBuffer]()

  for (i <- 0 until numBatches) {
    val vbb = ByteBuffer.allocateDirect(particlesPerBatch * 7 * 4)
    vbb.order(ByteOrder.nativeOrder()) // Use native byte order
    vbos += vbb.asFloatBuffer() // Convert from byte to float
  }

  var colorAttribute = 0
  var mPositionHandle = 0
  var sizeAttribute = 0
  var fence: Int = 0
  var isSystemDone = false

  def init = {
    GLES20.glGenBuffers(1, vertexHandle, 0)

    colorAttribute = GLES20.glGetAttribLocation(Shaders.program, "vColor");
    mPositionHandle = GLES20.glGetAttribLocation(Shaders.program, "vPosition");
    sizeAttribute = GLES20.glGetAttribLocation(Shaders.program, "pointSize");
    isInit = true
  }

  def draw(gl: GL10, program: Int, elapsedTime: Double) = {

    var index = 0

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexHandle(0));
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, numParticles * 7 * 4, null, GLES20.GL_DYNAMIC_DRAW);

    GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 28, 0);
    GLES20.glVertexAttribPointer(colorAttribute, 4, GLES20.GL_FLOAT, false, 28, 8);
    GLES20.glVertexAttribPointer(sizeAttribute, 1, GLES20.GL_FLOAT, false, 28, 24);
    GLES20.glEnableVertexAttribArray(mPositionHandle);
    GLES20.glEnableVertexAttribArray(colorAttribute);
    GLES20.glEnableVertexAttribArray(sizeAttribute);

    while (index < numBatches) {
      val offset = numParticles / numBatches * index * 7 * 4

      val buffer = GLES30.glMapBufferRange(GLES20.GL_ARRAY_BUFFER, offset, particlesPerBatch * 7 * 4, GLES30.GL_MAP_WRITE_BIT | GLES30.GL_MAP_UNSYNCHRONIZED_BIT).asInstanceOf[ByteBuffer].order(ByteOrder.nativeOrder).asFloatBuffer()
      //GLES30.glClientWaitSync(fence, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, GLES30.GL_TIMEOUT_IGNORED);
      //     buffer.order(ByteOrder.nativeOrder())
      //     val byteBuffer = ByteBuffer.allocate(numParticles * 7 * 4);
      //     byteBuffer.asFloatBuffer().put(vertexBuffer);
      run(elapsedTime, index)
      buffer.put(vbos(index)).position(0)
      //GLES30.glFlushMappedBufferRange(GLES20.GL_ARRAY_BUFFER, 0, numParticles * 7 * 4)
      GLES30.glUnmapBuffer(GLES20.GL_ARRAY_BUFFER)

      gl.glDrawArrays(GLES20.GL_POINTS, numParticles / numBatches * index, particlesPerBatch)

      index += 1
      // val x = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE.toInt, 0.toInt)
    }
    // Disable vertex array
//    GLES20.glDisableVertexAttribArray(mPositionHandle);
//    GLES20.glDisableVertexAttribArray(colorAttribute);
//    GLES20.glDisableVertexAttribArray(sizeAttribute);

  }

  abstract case class Particle(var x: Double,
                               var y: Double,
                               var xVel: Double,
                               var yVel: Double,
                               var size: Double,
                               var red: Double,
                               var green: Double,
                               var blue: Double,
                               totalLife: Double) {
    var currentLife: Double = totalLife

    def next(particle: Particle, elapsedTime: Double)
    def onCollide(particle: Particle, value: Double): Double
  }

  var particles: Array[Particle] = Array.fill(numParticles)(null)//ListBuffer[Particle]()

  def explode(x: Double, y: Double) = {
     var index = 0
      
      while (index < numParticles) {
      val rand = new Random()
      val newVelX = rand.nextDouble() * 2.0 - 1.0
      val newVelY = rand.nextDouble() * 2.0 - 1.0
      val dist = math.sqrt(newVelX * newVelX + newVelY * newVelY)
      val speed = rand.nextDouble() * 50.0 + 10.0
      val normVelX = newVelX / dist
      val normVelY = newVelY / dist
      val size = rand.nextDouble() * 11.0 + 4.0
      val totalLife = rand.nextDouble() * 5.0 + 2.0
     
        particles(index) = new Particle(x, y, normVelX, normVelY, size, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), totalLife) {
          def next(particle: Particle, elapsedTime: Double) = {
            var speed = Math.sqrt(particle.xVel * particle.xVel + particle.yVel * particle.yVel)
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
        index += 1
    }
  }

  def freeFall(x: Double, y: Double) = {
    var index = 0
    while (index < numParticles) {
      val rand = new Random()
      val speed = rand.nextDouble() * 300.0 + 60.0
      val newVelX = rand.nextDouble() * 2.0 - 1.0
      val newVelY = rand.nextDouble() * 2.0 - 1.0
      val dist = math.sqrt(newVelX * newVelX + newVelY * newVelY)

      val normVelX = newVelX / dist * speed
      val normVelY = newVelY / dist * speed
      val size = rand.nextDouble() * 3.0 + 4.0
      val totalLife = rand.nextDouble() * 8.0 + 2.0

      
      
      
        particles(index) = new Particle(x, y, normVelX, normVelY, size, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), totalLife) {
          def next(particle: Particle, elapsedTime: Double) = {
            particle.currentLife -= elapsedTime * 3.0
            particle.xVel += -Force.gravityX * elapsedTime * 600.0
            particle.yVel += -Force.gravityY * elapsedTime * 600.0
            particle.xVel += -Force.forceX * elapsedTime * 4300.0
            particle.yVel += -Force.forceY * elapsedTime * 4300.0
            particle.xVel *= 1.0 + 0.1 * elapsedTime
            particle.yVel *= 1.0 + 0.1 * elapsedTime
          }
  
          def onCollide(particle: Particle, value: Double) = {
            value * -1.0 * (particle.size / 20.0)
          }
        }
      index += 1
    }
  }

  def run(elapsedTime: Double, index: Int) = {
    var particleIndex = particlesPerBatch * index
    var particleCount = 0

    while (particleCount < particlesPerBatch) {
      val particle = particles(particleIndex + particleCount)
      vbos(index).position(particleCount * 7)
      if (particle.currentLife > 0.0) {
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

        vbos(index).put(Array(
          particle.x.toFloat, particle.y.toFloat,
          particle.red.toFloat, particle.green.toFloat, particle.blue.toFloat, 1.0f, //(particle.currentLife / particle.totalLife).toFloat,
          particle.size.toFloat * (particle.currentLife / particle.totalLife).toFloat))

        particle.next(particle, elapsedTime)
      } else {
        vbos(index).position(particleCount * 7 + 5)
        vbos(index).put(0.0f)
      }

      particleCount += 1
    }

    vbos(index).position(0)
  }

  def isDone = {
    var index = 0

    var areAllDone = true
    while (index < numParticles) {
      areAllDone &= particles(index).currentLife <= 0.0
      index += 1
    }

    areAllDone
  }
}