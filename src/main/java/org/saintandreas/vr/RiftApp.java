package org.saintandreas.vr;

import static com.oculusvr.capi.OvrLibrary.ovrProjectionModifier.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.Rectangle;

import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.saintandreas.gl.FrameBuffer;
import org.saintandreas.gl.MatrixStack;
import org.saintandreas.gl.SceneHelpers;
import org.saintandreas.gl.app.LwjglApp;
import org.saintandreas.math.Matrix4f;

import com.oculusvr.capi.EyeRenderDesc;
import com.oculusvr.capi.FovPort;
import com.oculusvr.capi.Hmd;
import com.oculusvr.capi.HmdDesc;
import com.oculusvr.capi.LayerEyeFov;
import com.oculusvr.capi.MirrorTexture;
import com.oculusvr.capi.MirrorTextureDesc;
import com.oculusvr.capi.OvrLibrary;
import com.oculusvr.capi.OvrMatrix4f;
import com.oculusvr.capi.OvrRecti;
import com.oculusvr.capi.OvrSizei;
import com.oculusvr.capi.OvrVector3f;
import com.oculusvr.capi.Posef;
import com.oculusvr.capi.TextureSwapChain;
import com.oculusvr.capi.TextureSwapChainDesc;
import com.oculusvr.capi.ViewScaleDesc;
import com.sun.jna.Pointer;

public abstract class RiftApp extends LwjglApp {
  protected final Hmd hmd;
  protected final HmdDesc hmdDesc;
  private final FovPort[] fovPorts = FovPort.buildPair();
  protected final Posef[] poses = Posef.buildPair();
  private final Matrix4f[] projections = new Matrix4f[2];
  private final OvrVector3f[] eyeOffsets = OvrVector3f.buildPair();
  private final OvrSizei[] textureSizes = new OvrSizei[2];
  private final ViewScaleDesc viewScaleDesc = new ViewScaleDesc();
  private FrameBuffer frameBuffer = null;
  private int frameCount = -1;
  private TextureSwapChain swapTexture = null;
  private MirrorTexture mirrorTexture = null;
  private LayerEyeFov layer = new LayerEyeFov();

  public RiftApp() {
    super();
    Hmd.initialize();
    try {
      Thread.sleep(400);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }

    Hmd tempHmd;
//    try {
        tempHmd = Hmd.create();
//    } catch (IllegalStateException e){
//        tempHmd = Hmd.createDebug(ovrHmdType.ovrHmd_DK2);
//    }
    hmd = tempHmd;
    
    hmdDesc = hmd.getDesc();

    Pointer version = OvrLibrary.INSTANCE.ovr_GetVersionString();
    System.out.println(version.getString(0)); 
      
    for (int eye = 0; eye < 2; ++eye) {
      fovPorts[eye] = hmdDesc.DefaultEyeFov[eye];
      OvrMatrix4f m = Hmd.getPerspectiveProjection(fovPorts[eye], 0.1f, 1000000f, ovrProjection_ClipRangeOpenGL);
      projections[eye] = RiftUtils.toMatrix4f(m);
      textureSizes[eye] = hmd.getFovTextureSize(eye, fovPorts[eye], 1.0f);
    }
  }

  @Override
  protected void onDestroy() {
    hmd.destroy();
    Hmd.shutdown();
  }

  @Override
  protected void setupContext() {
    contextAttributes = new ContextAttribs(4, 1).withProfileCore(true).withDebug(true);
  }

  @Override
  protected final void setupDisplay() {
    width = hmdDesc.Resolution.w;
    height = hmdDesc.Resolution.h;
    setupDisplay(new Rectangle(100, 100, width, height));
  }

  @Override
  protected void initGl() {
    super.initGl();
    Display.setVSyncEnabled(false);
    OvrSizei doubleSize = new OvrSizei();
    doubleSize.w = textureSizes[0].w + textureSizes[1].w;
    doubleSize.h = textureSizes[0].h;
    
    TextureSwapChainDesc textureSwapChainDesc = new TextureSwapChainDesc();
    textureSwapChainDesc.Type = OvrLibrary.ovrTextureType.ovrTexture_2D;
    textureSwapChainDesc.Format = OvrLibrary.ovrTextureFormat.OVR_FORMAT_R8G8B8A8_UNORM_SRGB;
    textureSwapChainDesc.Width = doubleSize.w;
    textureSwapChainDesc.Height = doubleSize.h;
    
    
    swapTexture = hmd.createSwapTextureChain(textureSwapChainDesc);
    
    MirrorTextureDesc mirrorDesc = new MirrorTextureDesc();
    mirrorDesc.Format = OvrLibrary.ovrTextureFormat.OVR_FORMAT_R8G8B8A8_UNORM_SRGB;
    mirrorDesc.Width = width;
    mirrorDesc.Height = height;
    
    mirrorTexture = hmd.createMirrorTexture(mirrorDesc);

    layer.Header.Type = OvrLibrary.ovrLayerType.ovrLayerType_EyeFov;
    layer.ColorTexure[0] = swapTexture;
    layer.Fov = fovPorts;
    layer.RenderPose = poses;
    for (int eye = 0; eye < 2; ++eye) {
      layer.Viewport[eye].Size = textureSizes[eye];
      layer.Viewport[eye].Size = textureSizes[eye];
    }
    layer.Viewport[1].Pos.x = layer.Viewport[1].Size.w;
    frameBuffer = new FrameBuffer(doubleSize.w, doubleSize.h);

    for (int eye = 0; eye < 2; ++eye) {
      EyeRenderDesc eyeRenderDesc = hmd.getRenderDesc(eye, fovPorts[eye]);
      this.eyeOffsets[eye].x = eyeRenderDesc.HmdToEyeOffset.x;
      this.eyeOffsets[eye].y = eyeRenderDesc.HmdToEyeOffset.y;
      this.eyeOffsets[eye].z = eyeRenderDesc.HmdToEyeOffset.z;
    }
    viewScaleDesc.HmdSpaceToWorldScaleInMeters = 1.0f;
  }

  @Override
  public final void drawFrame() {
    width = hmdDesc.Resolution.w;
    height = hmdDesc.Resolution.h;

    ++frameCount;
    Posef eyePoses[] = hmd.getEyePoses(frameCount, eyeOffsets);
    frameBuffer.activate();

    MatrixStack pr = MatrixStack.PROJECTION;
    MatrixStack mv = MatrixStack.MODELVIEW;
    int texture = swapTexture.getTextureId(swapTexture.getCurrentIndex());
    
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    for (int eye = 0; eye < 2; ++eye) {
      OvrRecti vp = layer.Viewport[eye];
      glScissor(vp.Pos.x, vp.Pos.y, vp.Size.w, vp.Size.h);
      glViewport(vp.Pos.x, vp.Pos.y, vp.Size.w, vp.Size.h);
      pr.set(projections[eye]);
      Posef pose = eyePoses[eye];
      // This doesn't work as it breaks the contiguous nature of the array
      // FIXME there has to be a better way to do this
      poses[eye].Orientation = pose.Orientation;
      poses[eye].Position = pose.Position;
      mv.push().preTranslate(RiftUtils.toVector3f(poses[eye].Position).mult(-1))
          .preRotate(RiftUtils.toQuaternion(poses[eye].Orientation).inverse());
      renderScene();
      mv.pop();
    }
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        frameBuffer.deactivate();

    swapTexture.commit();
    
    hmd.submitFrame(frameCount, layer);

    // FIXME Copy the layer to the main window using a mirror texture
    glScissor(0, 0, width, height);
    glViewport(0, 0, width, height);
    glClearColor(0.5f, 0.5f, System.currentTimeMillis() % 1000 / 1000.0f, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    SceneHelpers.renderTexturedQuad(mirrorTexture.getTextureId());
  }

  @Override
  protected void finishFrame() {
    // // Display update combines both input processing and
    // // buffer swapping. We want only the input processing
    // // so we have to call processMessages.
    // Display.processMessages();
    Display.update();
  }

  protected abstract void renderScene();
}
