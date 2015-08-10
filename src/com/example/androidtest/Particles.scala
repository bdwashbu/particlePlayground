package com.example.androidtest

import java.nio.ByteBuffer
import android.opengl.GLES20
import scala.util.Random
import scala.collection.mutable.ListBuffer
import android.opengl.GLES30
import java.nio.ByteOrder
import javax.microedition.khronos.opengles.GL10

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
                      var red: Double,
                      var green: Double,
                      var blue: Double,
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
        
        
        particles += new Particle(x, y, normVelX, normVelY, size, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), totalLife) {
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
        
        
        particles += new Particle(x, y, normVelX, normVelY, size, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), totalLife) {
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
            particle.red.toFloat, particle.green.toFloat, particle.blue.toFloat, (particle.currentLife / particle.totalLife).toFloat,
            particle.size.toFloat * (particle.currentLife / particle.totalLife).toFloat
        ))
        
        particle.next(particle, elapsedTime)
        index += 1
      }
      
      vertexBuffer.position(0)
      isDone = areAllDone
  }
}