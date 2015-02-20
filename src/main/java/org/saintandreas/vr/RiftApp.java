package org.saintandreas.vr;

import static com.oculusvr.capi.OvrLibrary.ovrDistortionCaps.*;
import static com.oculusvr.capi.OvrLibrary.ovrHmdCaps.*;
import static com.oculusvr.capi.OvrLibrary.ovrHmdType.*;
import static com.oculusvr.capi.OvrLibrary.ovrRenderAPIType.*;
import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.*;
import static org.lwjgl.glfw.GLFW.*;

import java.awt.Rectangle;

import org.saintandreas.gl.FrameBuffer;
import org.saintandreas.gl.app.LwjglApp;
import org.saintandreas.math.Matrix4f;
import org.saintandreas.transforms.MatrixStack;

import com.oculusvr.capi.EyeRenderDesc;
import com.oculusvr.capi.FovPort;
import com.oculusvr.capi.GLTexture;
import com.oculusvr.capi.GLTextureData;
import com.oculusvr.capi.HSWDisplayState;
import com.oculusvr.capi.Hmd;
import com.oculusvr.capi.OvrLibrary;
import com.oculusvr.capi.OvrVector2i;
import com.oculusvr.capi.OvrVector3f;
import com.oculusvr.capi.Posef;
import com.oculusvr.capi.RenderAPIConfig;
import com.oculusvr.capi.TextureHeader;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public abstract class RiftApp extends LwjglApp {

  protected final Hmd hmd;
  private EyeRenderDesc eyeRenderDescs[] = null;
  private final FovPort fovPorts[] =
      (FovPort[])new FovPort().toArray(2);
  private final GLTexture eyeTextures[] =
      (GLTexture[])new GLTexture().toArray(2);
//  private final Posef[] poses = 
//      (Posef[])new Posef().toArray(2);
  private final OvrVector3f[] eyeOffsets = 
      (OvrVector3f[])new OvrVector3f().toArray(2);;
  private final FrameBuffer frameBuffers[] =
      new FrameBuffer[2];
  private final Matrix4f projections[] =
      new Matrix4f[2];
  private int frameCount = -1;
  private int currentEye;


  private static Hmd openFirstHmd() {
    Hmd hmd = Hmd.create(0);
    if (null == hmd) {
      hmd = Hmd.createDebug(ovrHmd_DK1);
      hmd.WindowsPos.y = -1080;
    }
    return hmd;
  }

  public RiftApp() {
    super();

    Hmd.initialize();

    try {
      Thread.sleep(400);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }

    hmd = openFirstHmd();
    if (null == hmd) {
      throw new IllegalStateException(
          "Unable to initialize HMD");
    }

    if (0 == hmd.configureTracking(
        ovrTrackingCap_Orientation | 
        ovrTrackingCap_Position, 0)) {
      throw new IllegalStateException(
          "Unable to start the sensor");
    }

    for (int eye = 0; eye < 2; ++eye) {
      fovPorts[eye] = hmd.DefaultEyeFov[eye];
      projections[eye] = RiftUtils.toMatrix4f(
          Hmd.getPerspectiveProjection(
              fovPorts[eye], 0.1f, 1000000f, true));

      GLTexture texture = eyeTextures[eye];
      GLTextureData header = texture.ogl;
      header.Header.API = ovrRenderAPI_OpenGL;
      header.Header.TextureSize = hmd.getFovTextureSize(
          eye, fovPorts[eye], 1.0f);
      header.Header.RenderViewport.Size = header.Header.TextureSize; 
      header.Header.RenderViewport.Pos = new OvrVector2i(0, 0);
    }
  }

  @Override
  protected void onDestroy() {
    hmd.destroy();
    Hmd.shutdown();
  }

  @Override
  protected void setupContext() {
    // Bug in LWJGL on OSX returns a 2.1 context if you ask for 3.3, but returns 4.1 if you ask for 3.2
    String osName = System.getProperty("os.name");
    glfwWindowHint(GLFW_DEPTH_BITS, 16);
    glfwWindowHint(GLFW_DECORATED, 0);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    if (osName.startsWith("Mac") || osName.startsWith("Darwin")) {
      // Without this line we get
      // FATAL (86): NSGL: The targeted version of OS X only supports OpenGL 3.2 and later versions if they are forward-compatible
      glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
      glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
    } else {
      glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
      glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 4);
    }
  }

  public interface User32 extends Library {
     User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);
     Pointer GetForegroundWindow();  
     int GetWindowTextA(Pointer hWnd, byte[] lpString, int nMaxCount);
  }

  @Override
  protected final void setupDisplay() {
    System.setProperty(
        "org.lwjgl.opengl.Window.undecorated", "true");

    Rectangle targetRect = new Rectangle(
        hmd.WindowsPos.x, hmd.WindowsPos.y, 
        hmd.Resolution.w, hmd.Resolution.h);
    
    boolean directHmdMode = (0 == (ovrHmdCap_ExtendDesktop & hmd.HmdCaps));

    if (directHmdMode) {
      targetRect.x = 0;
      targetRect.y = -1080;
    }

    setupDisplay(targetRect);

    Pointer hwnd = User32.INSTANCE.GetForegroundWindow(); // then you can call it!
    OvrLibrary.INSTANCE.ovrHmd_AttachToWindow(hmd, hwnd, null, null);
  }

  @Override
  protected void initGl() {
    super.initGl();
    for (int eye = 0; eye < 2; ++eye) {
      TextureHeader eth = eyeTextures[eye].ogl.Header;
      frameBuffers[eye] = new FrameBuffer(
          eth.TextureSize.w, eth.TextureSize.h);
      eyeTextures[eye].ogl.TexId = frameBuffers[eye].getTexture().id;
    }

    RenderAPIConfig rc = new RenderAPIConfig();
    rc.Header.BackBufferSize = hmd.Resolution;
    rc.Header.Multisample = 1;

    int distortionCaps = 
      ovrDistortionCap_Chromatic |
      ovrDistortionCap_TimeWarp |
      ovrDistortionCap_Vignette;

    eyeRenderDescs = hmd.configureRendering(
        rc, distortionCaps, hmd.DefaultEyeFov);
    
    for (int eye = 0; eye < 2; ++eye) {
      eyeOffsets[eye].x = eyeRenderDescs[eye].HmdToEyeViewOffset.x;
      eyeOffsets[eye].y = eyeRenderDescs[eye].HmdToEyeViewOffset.y;
      eyeOffsets[eye].z = eyeRenderDescs[eye].HmdToEyeViewOffset.z;
    }

  }


  boolean hswDismissed = false;
  @Override
  public final void drawFrame() {
    ++frameCount;
    if (!hswDismissed) {
      HSWDisplayState hswState = hmd.getHSWDisplayState();
      if (hswState.Displayed != 0) {
        hmd.dismissHSWDisplay();
      } else {
        hswDismissed = true;
      }
    }
    Posef[] poses = hmd.getEyePoses(frameCount, eyeOffsets);
    hmd.beginFrame(frameCount);
    for (int i = 0; i < 2; ++i) {
      currentEye = hmd.EyeRenderOrder[i];
      MatrixStack.PROJECTION.set(projections[currentEye]);
      // This doesn't work as it breaks the contiguous nature of the array
      // FIXME there has to be a better way to do this
      Posef pose = poses[currentEye];
      poses[currentEye].Orientation = pose.Orientation;
      poses[currentEye].Position = pose.Position;

      MatrixStack mv = MatrixStack.MODELVIEW;
      mv.push();
      {
        mv.preTranslate(
          RiftUtils.toVector3f(
            poses[currentEye].Position).mult(-1));
        mv.preRotate(
          RiftUtils.toQuaternion(
            poses[currentEye].Orientation).inverse());
        frameBuffers[currentEye].activate();
        renderScene();
        frameBuffers[currentEye].deactivate();
      }
      mv.pop();
    }
    currentEye = -1;
    hmd.endFrame(poses, eyeTextures);
  }

  @Override
  protected void finishFrame() {
//    Display.processMessages();
//    Display.update();
  }
  
  long lastFrameReport = 0;
  int lastFrameCount = 0;
  
  @Override
  protected void update() {
    super.update();
    long now = System.currentTimeMillis();
    if (0 == lastFrameReport) {
      lastFrameReport = now;
      return;
    }

    if (now - lastFrameReport > 2000) {
      float fps = frameCount - lastFrameCount;
      fps /= now - lastFrameReport;
      System.out.println(String.format("%3f", fps * 1000.0f));
      lastFrameCount = frameCount;
      lastFrameReport = now;
    }
  }

  protected abstract void renderScene();

  public int getCurrentEye() {
    return currentEye;
  }
}
