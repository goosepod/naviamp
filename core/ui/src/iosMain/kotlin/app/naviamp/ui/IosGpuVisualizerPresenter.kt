@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.NSError
import platform.Metal.*
import platform.QuartzCore.*
import platform.UIKit.UIScreen
import kotlin.concurrent.AtomicInt

/** Metal/CALayer resource adapter; Core supplies timing, uniforms, visibility and clipping. */
internal class IosGpuVisualizerPresenter(private val parent: () -> CALayer?) : NaviampGpuVisualizerPresenter {
    override fun create(shader: NaviampGpuVisualizerShader): NaviampGpuVisualizerRegion = IosGpuVisualizerRegion(parent, shader)
}

private class IosGpuVisualizerRegion(
    private val parent: () -> CALayer?, shader: NaviampGpuVisualizerShader,
) : NaviampGpuVisualizerRegion {
    private val device = requireNotNull(MTLCreateSystemDefaultDevice())
    private val queue = requireNotNull(device.newCommandQueue())
    private val pipeline = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val library = device.newLibraryWithSource(shader.metal + DirectMetalVertex, null, error.ptr)
            ?: error(error.value?.localizedDescription ?: "Metal shader compilation failed")
        val descriptor = MTLRenderPipelineDescriptor().apply {
            vertexFunction = library.newFunctionWithName("visualizerVertex")
            fragmentFunction = library.newFunctionWithName("visualizerFragment")
            colorAttachments.objectAtIndexedSubscript(0u).apply {
                pixelFormat = MTLPixelFormatBGRA8Unorm
                blendingEnabled = true
                sourceRGBBlendFactor = MTLBlendFactorOne
                destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha
                sourceAlphaBlendFactor = MTLBlendFactorOne
                destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha
            }
        }
        device.newRenderPipelineStateWithDescriptor(descriptor, error.ptr)
            ?: error(error.value?.localizedDescription ?: "Metal pipeline creation failed")
    }
    private val sampler = requireNotNull(device.newSamplerStateWithDescriptor(MTLSamplerDescriptor().apply {
        minFilter = MTLSamplerMinMagFilterLinear; magFilter = MTLSamplerMinMagFilterLinear
        sAddressMode = MTLSamplerAddressModeClampToEdge; tAddressMode = MTLSamplerAddressModeClampToEdge
    }))
    private fun texture(format: MTLPixelFormat, width: Int): MTLTextureProtocol =
        requireNotNull(device.newTextureWithDescriptor(MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
            format, width.toULong(), 1u, false).apply { usage = MTLTextureUsageShaderRead }))
    private val frequencies = texture(MTLPixelFormatR32Float, 32)
    private val artwork = texture(MTLPixelFormatRGBA8Unorm, 1).also { texture ->
        byteArrayOf(-1, -1, -1, -1).usePinned {
            texture.replaceRegion(MTLRegionMake2D(0u, 0u, 1u, 1u), 0u, it.addressOf(0), 4u)
        }
    }
    private val root = CALayer.layer().apply { masksToBounds = true; anchorPoint = CGPointMake(0.0, 0.0) }
    private val metal = CAMetalLayer().apply {
        this.device = this@IosGpuVisualizerRegion.device as objcnames.protocols.MTLDeviceProtocol
        pixelFormat = MTLPixelFormatBGRA8Unorm; framebufferOnly = true; opaque = false
        anchorPoint = CGPointMake(0.0, 0.0); maximumDrawableCount = 3u
    }
    private var rasterSize = IntSize.Zero
    private var attached = false
    private var visible = false
    private val busy = AtomicInt(0)
    private val failed = AtomicInt(0)
    init { root.addSublayer(metal) }
    override fun place(bounds: Rect, clip: Rect, cornerRadius: Float) {
        val parent = parent() ?: return
        if (!attached) { parent.addSublayer(root); attached = true }
        val scale = UIScreen.mainScreen.scale
        CATransaction.begin(); CATransaction.setDisableActions(true)
        root.frame = CGRectMake(clip.left / scale, clip.top / scale, clip.width / scale, clip.height / scale)
        root.cornerRadius = cornerRadius / scale
        metal.frame = CGRectMake((bounds.left - clip.left) / scale, (bounds.top - clip.top) / scale,
            bounds.width / scale, bounds.height / scale)
        metal.contentsScale = scale
        CATransaction.commit()
    }
    override fun setVisible(visible: Boolean) { this.visible = visible; root.hidden = !visible }
    override fun submit(frame: NaviampGpuVisualizerFrame): NaviampGpuSubmission {
        if (failed.value != 0) return NaviampGpuSubmission.Failed
        if (!attached || !visible) return NaviampGpuSubmission.NotReady
        if (!busy.compareAndSet(0, 1)) return NaviampGpuSubmission.Busy
        return try {
            if (rasterSize != frame.rasterSize) {
                rasterSize = frame.rasterSize
                metal.drawableSize = CGSizeMake(rasterSize.width.toDouble(), rasterSize.height.toDouble())
            }
            frame.bands.usePinned {
                frequencies.replaceRegion(MTLRegionMake2D(0u, 0u, 32u, 1u), 0u, it.addressOf(0), 128u)
            }
            val drawable = metal.nextDrawable()
            if (drawable == null) { busy.value = 0; return NaviampGpuSubmission.NotReady }
            val pass = MTLRenderPassDescriptor.renderPassDescriptor()
            pass.colorAttachments.objectAtIndexedSubscript(0u).apply {
                texture = drawable.texture as MTLTextureProtocol
                loadAction = MTLLoadActionClear; storeAction = MTLStoreActionStore
                clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0)
            }
            val command = requireNotNull(queue.commandBuffer())
            val encoder = requireNotNull(command.renderCommandEncoderWithDescriptor(pass))
            encoder.setRenderPipelineState(pipeline)
            frame.uniforms.usePinned { encoder.setFragmentBytes(it.addressOf(0), 156u, 0u) }
            encoder.setFragmentTexture(frequencies, 0u); encoder.setFragmentTexture(artwork, 1u)
            encoder.setFragmentSamplerState(sampler, 0u)
            encoder.drawPrimitives(MTLPrimitiveTypeTriangle, 0u, 3u); encoder.endEncoding()
            command.presentDrawable(drawable)
            command.addCompletedHandler { completed ->
                if (completed?.status == MTLCommandBufferStatusError) failed.value = 1
                busy.value = 0
            }
            command.commit()
            NaviampGpuSubmission.Accepted
        } catch (_: Throwable) { busy.value = 0; failed.value = 1; NaviampGpuSubmission.Failed }
    }
    override fun close() { visible = false; root.removeFromSuperlayer() }
}

// Fixed Metal vertex ABI; all effect shading comes from Core's canonical source.
private const val DirectMetalVertex = """
vertex NaviampRasterizerData visualizerVertex(uint vertexId [[vertex_id]]) {
    float2 positions[3] = {float2(-1,-1), float2(3,-1), float2(-1,3)};
    NaviampRasterizerData out;
    out.position = float4(positions[vertexId], 0, 1);
    out.uv = positions[vertexId] * 0.5 + 0.5;
    return out;
}
"""
