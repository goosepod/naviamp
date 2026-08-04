@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSError
import platform.Metal.MTLBlendFactorOne
import platform.Metal.MTLBlendFactorOneMinusSourceAlpha
import platform.Metal.MTLBlendFactorSourceAlpha
import platform.Metal.MTLClearColorMake
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLLoadActionClear
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.Metal.MTLPixelFormatR32Float
import platform.Metal.MTLPixelFormatRGBA8Unorm
import platform.Metal.MTLPrimitiveTypeTriangle
import platform.Metal.MTLRegionMake2D
import platform.Metal.MTLRenderPassDescriptor
import platform.Metal.MTLRenderPipelineDescriptor
import platform.Metal.MTLRenderPipelineStateProtocol
import platform.Metal.MTLSamplerAddressModeClampToEdge
import platform.Metal.MTLSamplerDescriptor
import platform.Metal.MTLSamplerMinMagFilterLinear
import platform.Metal.MTLSamplerMipFilterNotMipmapped
import platform.Metal.MTLSamplerStateProtocol
import platform.Metal.MTLStorageModeShared
import platform.Metal.MTLStoreActionStore
import platform.Metal.MTLTextureDescriptor
import platform.Metal.MTLTextureProtocol
import platform.Metal.MTLTextureUsageRenderTarget
import platform.Metal.MTLTextureUsageShaderRead

/** Executes Core's translated Metal visualizers through the native iOS Metal command API. */
internal class IosMetalVisualizerRenderer(
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
) : AutoCloseable {
    private var resources: IosMetalResources? = createMetalResources(visualizer)
    private val uniformBands = FloatArray(VisualizerFrameBandCount)
    private val smoothBands = FloatArray(VisualizerFrameBandCount)
    private var renderTexture: MTLTextureProtocol? = null
    private var renderWidth = 0
    private var renderHeight = 0

    fun renderImage(
        width: Int,
        height: Int,
        bands: List<Float>,
        active: Boolean,
        visualizerColors: NaviampPlayerColors,
        colors: NaviampColors,
        timeSeconds: Float,
        tempoBpm: Int?,
    ): Image? {
        val resources = resources ?: return null
        if (width <= 0 || height <= 0) return null
        smoothVisualizerBands(bands, smoothBands, uniformBands)
        val frame = buildVisualizerFrameInput(
            width = width.toFloat(),
            height = height.toFloat(),
            bands = bands,
            visibleBands = bands.size.coerceAtLeast(1),
            active = active,
            timeSeconds = timeSeconds,
            tempoBpm = tempoBpm,
            uniformBands = uniformBands,
        )
        val target = ensureRenderTexture(resources.device, width, height)
        uniformBands.usePinned { pinnedBands ->
            resources.frequencyTexture.replaceRegion(
                region = MTLRegionMake2D(0u, 0u, VisualizerFrameBandCount.toULong(), 1u),
                mipmapLevel = 0u,
                withBytes = pinnedBands.addressOf(0),
                bytesPerRow = (VisualizerFrameBandCount * Float.SIZE_BYTES).toULong(),
            )
        }

        memScoped {
            val uniformStorage = allocArray<IntVar>(MetalUniformSlotCount)
            val floatStorage = uniformStorage.reinterpret<FloatVar>()
            floatStorage[0] = frame.timeSeconds
            floatStorage[1] = frame.width
            floatStorage[2] = frame.height
            floatStorage[3] = frame.energy.energy
            floatStorage[4] = frame.energy.bass
            floatStorage[5] = frame.energy.mids
            floatStorage[6] = frame.energy.highs
            floatStorage[7] = frame.energy.spectralCentroid
            floatStorage[8] = frame.tempoBpm
            floatStorage[9] = frame.energy.beatDetected
            floatStorage[10] = if (frame.active) 1f else 0f
            floatStorage[11] = visualizer.nativeVisualizerRenderScale(renderPolicy)
            uniformStorage[12] = visualizer.nativeVisualizerMaxRaymarchSteps(renderPolicy)
            floatStorage.writeColor(13, visualizerColors.accent)
            floatStorage.writeColor(17, colors.primaryText)
            floatStorage.writeOpaqueColor(21, visualizerColors.backgroundStart)
            floatStorage.writeOpaqueColor(25, visualizerColors.backgroundMid)
            floatStorage.writeOpaqueColor(29, visualizerColors.backgroundEnd)
            floatStorage[33] = 1f
            floatStorage[34] = 1f

            val pass = MTLRenderPassDescriptor.renderPassDescriptor()
            pass.colorAttachments.objectAtIndexedSubscript(0u).apply {
                texture = target
                loadAction = MTLLoadActionClear
                storeAction = MTLStoreActionStore
                clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0)
            }
            val commandBuffer = resources.commandQueue.commandBuffer() ?: return null
            val encoder = commandBuffer.renderCommandEncoderWithDescriptor(pass) ?: return null
            encoder.setRenderPipelineState(resources.pipeline)
            encoder.setFragmentBytes(uniformStorage, MetalUniformByteCount.toULong(), 0u)
            encoder.setFragmentTexture(resources.frequencyTexture, 0u)
            encoder.setFragmentTexture(resources.albumArtTexture, 1u)
            encoder.setFragmentSamplerState(resources.sampler, 0u)
            encoder.drawPrimitives(MTLPrimitiveTypeTriangle, 0u, 3u)
            encoder.endEncoding()
            commandBuffer.commit()
            commandBuffer.waitUntilCompleted()
        }

        val pixels = ByteArray(width * height * 4)
        pixels.usePinned { pinnedPixels ->
            target.getBytes(
                pixelBytes = pinnedPixels.addressOf(0),
                bytesPerRow = (width * 4).toULong(),
                fromRegion = MTLRegionMake2D(0u, 0u, width.toULong(), height.toULong()),
                mipmapLevel = 0u,
            )
        }
        return Image.makeRaster(
            ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL),
            pixels,
            width * 4,
        )
    }

    private fun ensureRenderTexture(device: MTLDeviceProtocol, width: Int, height: Int): MTLTextureProtocol {
        val current = renderTexture
        if (current != null && renderWidth == width && renderHeight == height) return current
        renderWidth = width
        renderHeight = height
        return createTexture(
            device = device,
            width = width,
            height = height,
            pixelFormat = MTLPixelFormatBGRA8Unorm,
            usage = MTLTextureUsageRenderTarget or MTLTextureUsageShaderRead,
            shared = true,
        ).also { renderTexture = it }
    }

    override fun close() {
        renderTexture = null
        resources = null
    }

    companion object {
        private val available: Boolean by lazy { MTLCreateSystemDefaultDevice() != null }

        fun isAvailable(): Boolean = available
    }
}

private class IosMetalResources(
    val device: MTLDeviceProtocol,
    val commandQueue: MTLCommandQueueProtocol,
    val pipeline: MTLRenderPipelineStateProtocol,
    val sampler: MTLSamplerStateProtocol,
    val frequencyTexture: MTLTextureProtocol,
    val albumArtTexture: MTLTextureProtocol,
)

private fun createMetalResources(visualizer: NaviampVisualizer): IosMetalResources {
    val device = requireNotNull(MTLCreateSystemDefaultDevice())
    val albumArtTexture = createTexture(
        device = device,
        width = 1,
        height = 1,
        pixelFormat = MTLPixelFormatRGBA8Unorm,
        usage = MTLTextureUsageShaderRead,
    )
    byteArrayOf(-1, -1, -1, -1).usePinned { white ->
        albumArtTexture.replaceRegion(
            region = MTLRegionMake2D(0u, 0u, 1u, 1u),
            mipmapLevel = 0u,
            withBytes = white.addressOf(0),
            bytesPerRow = 4u,
        )
    }
    return IosMetalResources(
        device = device,
        commandQueue = requireNotNull(device.newCommandQueue()),
        pipeline = createPipeline(device, visualizer),
        sampler = createSampler(device),
        frequencyTexture = createTexture(
            device = device,
            width = VisualizerFrameBandCount,
            height = 1,
            pixelFormat = MTLPixelFormatR32Float,
            usage = MTLTextureUsageShaderRead,
        ),
        albumArtTexture = albumArtTexture,
    )
}

private fun createPipeline(
    device: MTLDeviceProtocol,
    visualizer: NaviampVisualizer,
): MTLRenderPipelineStateProtocol = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val source = requireNotNull(visualizer.nativeShaderDefinition)
        .fragmentSourceForDialect(NativeShaderDialect.MetalShadingLanguage) + MetalVertexShader
    val library = device.newLibraryWithSource(source, null, error.ptr)
        ?: kotlin.error(error.value?.localizedDescription ?: "Metal shader compilation failed")
    val descriptor = MTLRenderPipelineDescriptor().apply {
        vertexFunction = library.newFunctionWithName("visualizerVertex")
        fragmentFunction = library.newFunctionWithName("visualizerFragment")
        colorAttachments.objectAtIndexedSubscript(0u).apply {
            pixelFormat = MTLPixelFormatBGRA8Unorm
            blendingEnabled = true
            sourceRGBBlendFactor = MTLBlendFactorSourceAlpha
            destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha
            sourceAlphaBlendFactor = MTLBlendFactorOne
            destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha
        }
    }
    device.newRenderPipelineStateWithDescriptor(descriptor, error.ptr)
        ?: kotlin.error(error.value?.localizedDescription ?: "Metal pipeline creation failed")
}

private fun createSampler(device: MTLDeviceProtocol): MTLSamplerStateProtocol {
    val descriptor = MTLSamplerDescriptor().apply {
        minFilter = MTLSamplerMinMagFilterLinear
        magFilter = MTLSamplerMinMagFilterLinear
        mipFilter = MTLSamplerMipFilterNotMipmapped
        sAddressMode = MTLSamplerAddressModeClampToEdge
        tAddressMode = MTLSamplerAddressModeClampToEdge
    }
    return requireNotNull(device.newSamplerStateWithDescriptor(descriptor))
}

private fun createTexture(
    device: MTLDeviceProtocol,
    width: Int,
    height: Int,
    pixelFormat: ULong,
    usage: ULong,
    shared: Boolean = false,
): MTLTextureProtocol {
    val descriptor = MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
        pixelFormat = pixelFormat,
        width = width.coerceAtLeast(1).toULong(),
        height = height.coerceAtLeast(1).toULong(),
        mipmapped = false,
    ).apply {
        this.usage = usage
        if (shared) storageMode = MTLStorageModeShared
    }
    return requireNotNull(device.newTextureWithDescriptor(descriptor))
}

private fun kotlinx.cinterop.CPointer<FloatVar>.writeColor(offset: Int, color: Color) {
    this[offset] = color.red
    this[offset + 1] = color.green
    this[offset + 2] = color.blue
    this[offset + 3] = color.alpha
}

private fun kotlinx.cinterop.CPointer<FloatVar>.writeOpaqueColor(offset: Int, color: Color) {
    writeColor(offset, color.copy(alpha = 1f))
}

private const val MetalUniformSlotCount = 35
private const val MetalUniformByteCount = MetalUniformSlotCount * Int.SIZE_BYTES

private const val MetalVertexShader = """

vertex NaviampRasterizerData visualizerVertex(uint vertexId [[vertex_id]]) {
    float2 positions[3] = {
        float2(-1.0, -1.0),
        float2( 3.0, -1.0),
        float2(-1.0,  3.0)
    };
    NaviampRasterizerData out;
    out.position = float4(positions[vertexId], 0.0, 1.0);
    out.uv = positions[vertexId] * 0.5 + 0.5;
    return out;
}
"""
