package com.friday.assistant

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.google.android.filament.VertexBuffer
import io.github.sceneview.EngineDestroyQueue
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.Node
import io.github.sceneview.splat.SplatBuffers
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val PARTICLE_COUNT = 2800
private const val CLOUD_RADIUS = 0.76f
private const val TWO_PI = 6.2831855f

private data class CloudParticle(
    val x: Float,
    val y: Float,
    val z: Float,
    val axisX: Float,
    val axisY: Float,
    val axisZ: Float,
    val orbitSpeed: Float,
    val orbitPhase: Float,
    val driftPhase: Float,
    val driftSpeed: Float,
    val driftAmount: Float,
    val size: Float,
    val brightness: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
    val opacity: Float
)

private fun createCloudParticles(): Array<CloudParticle> {
    val random = Random(94_217)
    val palette = arrayOf(
        floatArrayOf(0.52f, 0.39f, 0.75f),
        floatArrayOf(0.68f, 0.57f, 0.84f),
        floatArrayOf(0.78f, 0.75f, 0.86f),
        floatArrayOf(0.61f, 0.49f, 0.78f),
        floatArrayOf(0.91f, 0.89f, 0.98f)
    )

    return Array(PARTICLE_COUNT) {
        val directionZ = random.nextFloat() * 2f - 1f
        val azimuth = random.nextFloat() * TWO_PI
        val planarDistance = sqrt(1f - directionZ * directionZ)
        val radiusFraction = if (random.nextFloat() < 0.28f) {
            0.78f + random.nextFloat() * 0.22f
        } else {
            Math.cbrt(random.nextDouble()).toFloat()
        }
        val radius = CLOUD_RADIUS * radiusFraction
        val axisZ = random.nextFloat() * 2f - 1f
        val axisAzimuth = random.nextFloat() * TWO_PI
        val axisPlanarDistance = sqrt(1f - axisZ * axisZ)
        val color = palette[random.nextInt(palette.size)]
        val speed = 0.08f + random.nextFloat() * 0.24f

        CloudParticle(
            x = radius * planarDistance * cos(azimuth),
            y = radius * planarDistance * sin(azimuth),
            z = radius * directionZ,
            axisX = axisPlanarDistance * cos(axisAzimuth),
            axisY = axisPlanarDistance * sin(axisAzimuth),
            axisZ = axisZ,
            orbitSpeed = if (random.nextBoolean()) speed else -speed,
            orbitPhase = random.nextFloat() * TWO_PI,
            driftPhase = random.nextFloat() * TWO_PI,
            driftSpeed = 0.35f + random.nextFloat() * 0.8f,
            driftAmount = 0.006f + random.nextFloat() * 0.016f,
            size = 0.002f + random.nextFloat() * 0.003f,
            brightness = 0.84f + random.nextFloat() * 0.16f,
            red = color[0],
            green = color[1],
            blue = color[2],
            opacity = (0.055f + random.nextFloat() * 0.13f) *
                if (radius < CLOUD_RADIUS * 0.42f) 0.48f else 1f
        )
    }
}

internal class FilamentParticleCloudNode(
    engine: Engine,
    private val materialLoader: MaterialLoader
) : Node(engine) {
    private val particles = createCloudParticles()
    private val textureSize = SplatBuffers.textureSize(PARTICLE_COUNT)
    private val positionScaleTexture = createDataTexture(engine)
    private val colorOpacityTexture = createDataTexture(engine)
    private val materialInstance = materialLoader.createSplatInstance(
        positionScaleTexture,
        colorOpacityTexture,
        textureSize,
        0
    )
    private val vertexBuffer = createQuadVertexBuffer(engine)
    private val indexBuffer = createQuadIndexBuffer(engine)
    private val positionData = createTextureBuffer(textureSize)
    private val colorData = createTextureBuffer(textureSize)
    private var startTimeNanos = 0L
    private var resourcesDestroyed = false

    init {
        RenderableManager.Builder(1)
            .boundingBox(Box(-1f, -1f, -1f, 1f, 1f, 1f))
            .geometry(
                0,
                RenderableManager.PrimitiveType.TRIANGLES,
                vertexBuffer,
                indexBuffer
            )
            .material(0, materialInstance)
            .instances(PARTICLE_COUNT)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, entity)

        updateTextures(0f)
    }

    override fun onFrame(frameTimeNanos: Long) {
        super.onFrame(frameTimeNanos)
        if (startTimeNanos == 0L) {
            startTimeNanos = frameTimeNanos
        }

        val elapsedSeconds = (frameTimeNanos - startTimeNanos) * 1e-9f
        updateTextures(elapsedSeconds)
    }

    override fun destroy() {
        if (!resourcesDestroyed) {
            resourcesDestroyed = true
            engine.renderableManager.destroy(entity)
            materialLoader.destroyMaterialInstance(materialInstance)
            engine.destroyVertexBuffer(vertexBuffer)
            engine.destroyIndexBuffer(indexBuffer)
            EngineDestroyQueue.of(engine).enqueueTexture(positionScaleTexture)
            EngineDestroyQueue.of(engine).enqueueTexture(colorOpacityTexture)
        }
        super.destroy()
    }

    private fun updateTextures(time: Float) {
        positionData.clear()
        colorData.clear()

        for (particle in particles) {
            val angle = particle.orbitPhase + time * particle.orbitSpeed
            val cosine = cos(angle)
            val sine = sin(angle)
            val axisDotPosition =
                particle.axisX * particle.x +
                    particle.axisY * particle.y +
                    particle.axisZ * particle.z
            val oneMinusCosine = 1f - cosine

            var x = particle.x * cosine +
                (particle.axisY * particle.z - particle.axisZ * particle.y) * sine +
                particle.axisX * axisDotPosition * oneMinusCosine
            var y = particle.y * cosine +
                (particle.axisZ * particle.x - particle.axisX * particle.z) * sine +
                particle.axisY * axisDotPosition * oneMinusCosine
            var z = particle.z * cosine +
                (particle.axisX * particle.y - particle.axisY * particle.x) * sine +
                particle.axisZ * axisDotPosition * oneMinusCosine

            val drift = particle.driftAmount *
                sin(time * particle.driftSpeed + particle.driftPhase)
            val radialDrift = 1f + drift * 0.55f
            x = x * radialDrift + drift * cos(particle.driftPhase)
            y = y * radialDrift + drift * sin(particle.driftPhase * 1.7f)
            z *= radialDrift

            positionData.put(x).put(y).put(z).put(particle.size * 2f)

            val shimmer = 0.94f +
                0.06f * sin(time * (0.45f + particle.driftSpeed * 0.2f) + particle.driftPhase)
            colorData.put(particle.red * particle.brightness * shimmer)
                .put(particle.green * particle.brightness * shimmer)
                .put(particle.blue * particle.brightness * shimmer)
                .put(particle.opacity * shimmer)
        }

        while (positionData.hasRemaining()) {
            positionData.put(0f)
        }
        while (colorData.hasRemaining()) {
            colorData.put(0f)
        }

        positionData.rewind()
        colorData.rewind()
        positionScaleTexture.setImage(
            engine,
            0,
            Texture.PixelBufferDescriptor(positionData, Texture.Format.RGBA, Texture.Type.FLOAT)
        )
        colorOpacityTexture.setImage(
            engine,
            0,
            Texture.PixelBufferDescriptor(colorData, Texture.Format.RGBA, Texture.Type.FLOAT)
        )
    }

    private companion object {
        fun createDataTexture(engine: Engine): Texture =
            Texture.Builder()
                .width(SplatBuffers.textureSize(PARTICLE_COUNT))
                .height(SplatBuffers.textureSize(PARTICLE_COUNT))
                .levels(1)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.RGBA16F)
                .build(engine)

        fun createTextureBuffer(size: Int): FloatBuffer =
            ByteBuffer.allocateDirect(size * size * 4 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()

        fun createQuadVertexBuffer(engine: Engine): VertexBuffer {
            val vertices = ByteBuffer.allocateDirect(12 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(
                    floatArrayOf(
                        -1f, -1f, 0f,
                        1f, -1f, 0f,
                        1f, 1f, 0f,
                        -1f, 1f, 0f
                    )
                )
                .apply { rewind() }

            return VertexBuffer.Builder()
                .bufferCount(1)
                .vertexCount(4)
                .attribute(
                    VertexBuffer.VertexAttribute.POSITION,
                    0,
                    VertexBuffer.AttributeType.FLOAT3,
                    0,
                    12
                )
                .build(engine)
                .also { it.setBufferAt(engine, 0, vertices) }
        }

        fun createQuadIndexBuffer(engine: Engine): IndexBuffer {
            val indices = ByteBuffer.allocateDirect(6 * Short.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .put(shortArrayOf(0, 1, 2, 0, 2, 3))
                .apply { rewind() }

            return IndexBuffer.Builder()
                .indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine)
                .also { it.setBuffer(engine, indices) }
        }
    }
}
