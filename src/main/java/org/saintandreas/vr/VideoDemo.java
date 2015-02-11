package org.saintandreas.vr;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.saintandreas.ExampleResource;
import org.saintandreas.android.ResourceLoader;
import org.saintandreas.gl.Geometry;
import org.saintandreas.gl.IndexedGeometry;
import org.saintandreas.gl.OpenGL;
import org.saintandreas.gl.SceneHelpers;
import org.saintandreas.gl.buffers.VertexArray;
import org.saintandreas.gl.shaders.Program;
import org.saintandreas.gl.textures.Texture;
import org.saintandreas.math.Vector3f;
import org.saintandreas.resources.BasicResource;
import org.saintandreas.resources.FilesystemResourceProvider;
import org.saintandreas.resources.ResourceManager;
import org.saintandreas.scene.RootNode;
import org.saintandreas.scene.SceneNode;
import org.saintandreas.scene.TransformNode;
import org.saintandreas.transforms.MatrixStack;
import org.saintandreas.ui.SwingContainer;
import org.saintandreas.video.StereoscopicMode;
import org.saintandreas.video.VideoHelper;
import org.saintandreas.video.VideoToGl;
import org.saintandreas.vr.vlc.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.util.logging.PlatformLogger;
import uk.co.caprica.vlcj.binding.LibVlc;
import uk.co.caprica.vlcj.binding.LibVlcFactory;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

import com.oculusvr.capi.Hmd;
import com.oculusvr.capi.OvrLibrary;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

public class VideoDemo extends RiftApp  {
  
  public static void main(String[] args) {
    for (Handler h : LogManager.getLogManager().getLogger("").getHandlers()) {
      h.setLevel(Level.FINEST);
    }
    PlatformLogger focusLog = PlatformLogger.getLogger("java.awt.focus.Component");
    focusLog.setLevel(PlatformLogger.Level.FINEST);
    focusLog.info("Info");
    focusLog.fine("Fine");

    ResourceManager.setProvider(new FilesystemResourceProvider(
        new File("C:\\Users\\bdavis\\Git\\OculusRiftExamples\\resources"),
        new File("C:\\Users\\bdavis\\Git\\OculusRiftExamples\\examples\\java\\src\\main\\resources")
        ));
    new VideoDemo().run();
  }

  // private static final String MEDIA_URL = "http://192.168.0.4/sc2a.mp4";
  // private static final String MEDIA_URL = "http://192.168.0.4/Videos/South.Park.S17E09.HDTV.x264-ASAP.%5bVTV%5d.mp4";
  // private static final String MEDIA_URL = "http://192.168.0.4/Videos/3D/Gravity.2013.1080p%203D.HDTV.x264.DTS-RARBG.mkv";
  // private static final String MEDIA_URL = "http://192.168.0.4/Videos/Movies/g/Ghost.In.The.Shell.3.Solid.State.Soceity.2006.dvdrip.ac3-atilla82.avi";
  // private static final String MEDIA_URL = "http://192.168.0.4/Videos/3D/TRON%20LEGACY%203D.mkv";
  private static final String MEDIA_URL = "http://192.168.0.4/Videos/3D/Guardians.of.the.Galaxy.2014.1080p.3D.HSBS.BluRay.x264.YIFY.mp4";
  // private static final String MEDIA_URL = "http://192.168.0.4/Videos/3D/Man.Of.Steel.3D.2013.1080p.BluRay.Half-OU.DTS.x264-PublicHD.mkv";

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VideoDemo.class);

  private static final LibVlc LIB_VLC; 
  static {
    NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "/dev/vlc-2.1.5");
    Native.loadLibrary(RuntimeUtil.getLibVlcLibraryName(), LibVlc.class);
    LIB_VLC = LibVlcFactory.factory().log().create();
  }

  IndexedGeometry eyeMeshes[];
  VideoToGl videoTransport;
  MediaPlayer player;
  Texture videoTexture;
  private RootNode root = new RootNode();

  private SwingContainer swingContainer = new SwingContainer();
  private ConcurrentLinkedQueue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();

  private float videoAspect;
  private boolean swapEyes = false;
  private StereoscopicMode stereoMode = StereoscopicMode.SIDE_BY_SIDE;
  private Texture swingTexture;


  static final String HELP =
    "Type Ctrl-0 to get a screenshot of the current GUI.\n" +
    "The screenshot will be saved to the current " +
    "directory as 'screenshot.png'.";

  public VideoDemo() {
    resetCamera();
  }

  @Override
  protected void initGl() {
    super.initGl();

    root.addChild(new SceneNode(()->{
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
      SceneHelpers.renderProceduralSkybox();
    }));
    root.addChild(getScreenNode());
//    root.addChild(getSwingUiNode());
    root.addChild(getUiNode());

    swingTexture = new Texture(GL_TEXTURE_2D);
    swingTexture.withBound(()->{
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    });
    videoTexture = new Texture(GL_TEXTURE_2D);
    videoTexture.withBound(()->{
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    });

    videoTransport = new VideoToGl();
    videoTransport.setVideoChangedCallback((int width, int height)->{
      videoAspect = (float) width / (float) height;
      taskQueue.add(()->{
        eyeMeshes = VideoHelper.get3dEyeMeshes(videoAspect, stereoMode);
      });
    });

    player = new MediaPlayerFactory(LIB_VLC).newDirectMediaPlayer(videoTransport, videoTransport);
    player.playMedia(MEDIA_URL);
//    hmd.enableHswDisplay(false);
    glEnable(GL_VERTEX_PROGRAM_POINT_SIZE);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glEnable(GL_PRIMITIVE_RESTART);
    glPrimitiveRestartIndex(Short.MAX_VALUE);
    glPointParameteri(GL_POINT_SPRITE_COORD_ORIGIN, GL_LOWER_LEFT);
  }

  public static final float TAU = (float)(Math.PI * 2.0);
  public static final float TAU_2 = (float)(Math.PI);
  public static final float TAU_4 = (float)(Math.PI / 2.0);

  private static int[] ICONS = {
    R.drawable.ic_gear,
    R.drawable.ic_heart,
    R.drawable.ic_home,
    R.drawable.ic_eye,
    R.drawable.ic_video,
    R.drawable.ic_chat,
  };

  protected SceneNode f() {
//    JFrame f = swingContainer.getFrame();
//    JFileChooser fc = new JFileChooser("m:\\Videos\\3D");
//    fc.setControlButtonsAreShown(false);
//    f.add(fc, BorderLayout.CENTER);
//    JButton button = new JButton("Choose");
//    button.addActionListener(new ActionListener() {
//       @Override
//      public void actionPerformed(ActionEvent e) {
//         player.playMedia(fc.getSelectedFile().toString());
//       }
//    });
//    JPanel panel = new JPanel();
//    panel.add(button);
    
//    f.add(panel, BorderLayout.SOUTH);
//    f.pack();
//    f.setLocationRelativeTo(null);
//    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//    f.setVisible(true);
//    f.setSize(600, 400);

    MatrixStack mv = MatrixStack.MODELVIEW;
    IndexedGeometry quad = OpenGL.makeTexturedQuad();

    TransformNode transformNode = new TransformNode(()->{
      mv.translate(new Vector3f(0, OvrLibrary.OVR_DEFAULT_EYE_HEIGHT - 0.25f, -0.3f));
    }, mv);


    transformNode.addChild(new SceneNode( ()->{
      final Program program = OpenGL.getProgram(
          ExampleResource.SHADERS_TEXTURED_VS, 
          ExampleResource.SHADERS_TEXTURED_FS);
      program.use();
      swingTexture.bind();
      quad.bind();
      mv.withPush(()->{
        MatrixStack.bindAll(program);
        quad.draw();
      });
      VertexArray.clear();
      Program.clear();
    } ));
    return transformNode; 
  }
  
  protected SceneNode getUiNode() {
    MatrixStack mv = MatrixStack.MODELVIEW;
    IndexedGeometry quad = OpenGL.makeTexturedQuad();
    Geometry point = OpenGL.makePoint();
    Texture icons[] = new Texture[ICONS.length];
    
    for (int i = 0; i < ICONS.length; ++i) {
      icons[i] = Texture.loadImage(ResourceLoader.get().loadDrawable(ICONS[i]));
      icons[i].bind();
      icons[i].parameter(GL_TEXTURE_MAG_FILTER, GL_LINEAR);
      icons[i].parameter(GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    }

    TransformNode transformNode = new TransformNode(()->{
      mv.translate(new Vector3f(0, OvrLibrary.OVR_DEFAULT_EYE_HEIGHT - 0.25f, -0.3f));
      mv.rotate((float)Math.PI / 2.0f, Vector3f.UNIT_X);
//      mv.scale(1.5f);
    }, mv);

    transformNode.addChild(new SceneNode( ()->{
      glEnable(GL_BLEND);
      glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
      final Program circleProgram = OpenGL.getProgram(
          ExampleResource.SHADERS_TEXTURED_VS, 
          new BasicResource("shaders/Disc.fs"));
      circleProgram.use();
      quad.bind();
      mv.withPush(()->{
        for (int i = 0; i < 3; ++i) {
          circleProgram.setUniform("Time", (float)Hmd.getTimeInSeconds() * (5 - i) * 0.4f);
          MatrixStack.bindAll(circleProgram);
          quad.draw();
          mv.translate(Vector3f.UNIT_Z.mult(0.15f));
        }
      });
      final Program iconProgram = OpenGL.getProgram(
          new BasicResource("shaders/Icon.vs"), 
          new BasicResource("shaders/Icon.fs"));
      iconProgram.use();
      //float depth = (length(eyePosition - position*temp) - 1.0) / 49.0;
      
      iconProgram.setUniform("EyePosition", mv.getTranslation().mult(-1.0f));
      point.bind();
      for (int i = 0; i < ICONS.length; ++i) {
        icons[i].bind();
        final float angle = (float)(Math.PI / 2.0f) + TAU / ICONS.length * i;
        mv.withPush(()->{
          mv.rotate(angle, Vector3f.UNIT_Z);
          mv.translate(Vector3f.UNIT_Z.mult(-0.08f));
          mv.translate(Vector3f.UNIT_X.mult(0.4f));
          mv.unrotate();
          MatrixStack.bindAll(iconProgram);
        });
        point.draw();
      }
      VertexArray.clear();
      Program.clear();
    } ));
    
    return transformNode;
  }
  
  protected SceneNode getScreenNode() {
    Program textureProgram = OpenGL.getProgram(
        ExampleResource.SHADERS_TEXTURED_VS, 
        ExampleResource.SHADERS_TEXTURED_FS);
    MatrixStack mv = MatrixStack.MODELVIEW;

    TransformNode transformNode = new TransformNode(()->{
      mv.translate(new Vector3f(0, 0, -10.0f));
      mv.scale(16);
      mv.translate(new Vector3f(0, 0.3f, 0));
    }, mv);
    transformNode.addChild(new SceneNode( ()->{
      if (null == eyeMeshes) {
        return;
      }
      textureProgram.use();
      MatrixStack.bindAll(textureProgram);
      videoTexture.bind();
      int currentEye = getCurrentEye();
      if (swapEyes) {
        ++currentEye;
        currentEye %= 2;
      }
      IndexedGeometry eyeMesh = eyeMeshes[currentEye]; 
      eyeMesh.bindVertexArray();
      eyeMesh.draw();
      videoTexture.unbind();
    } ));
    transformNode.addChild(new TransformNode( ()->{
      mv.translate(new Vector3f(0, 0, -0.002f));
      mv.scale(1.2f);
    }, mv).addChild(new SceneNode( ()->{
      if (null == eyeMeshes) {
        return;
      }
      textureProgram.use();
      MatrixStack.bindAll(textureProgram);
//      videoTexture.bind();
      int currentEye = getCurrentEye();
      if (swapEyes) {
        ++currentEye;
        currentEye %= 2;
      }
      IndexedGeometry eyeMesh = eyeMeshes[currentEye]; 
      eyeMesh.bindVertexArray();
      eyeMesh.draw();
      videoTexture.unbind();
    } )));

    return transformNode;
  }

  protected void setTimeDelta(float time) {
    player.setTime(player.getTime() + (int)(time * 1000));
  }

//  @Override
//  protected boolean onKeyboardEvent() {
//    if (swingContainer.onKeyboardEvent()) {
//      return true;
//    }
//    if (super.onKeyboardEvent()) {
//      return true;
//    }
//    // We only care about presses, not releases
//    if (!Keyboard.getEventKeyState()) {
//      return false;
//    }
//
//    switch (Keyboard.getEventKey()) {
//    case Keyboard.KEY_R:
//      resetCamera();
//      return true;
//    case Keyboard.KEY_S:
//      swapEyes = !swapEyes;
//      return true;
//    case Keyboard.KEY_LEFT:
//      if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
//        setTimeDelta(-5);
//      } else {
//        setTimeDelta(-30);
//      }
//      return true;
//    case Keyboard.KEY_RIGHT:
//      if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
//        setTimeDelta(5);
//      } else {
//        setTimeDelta(30);
//      }
//      return true;
//
//    case Keyboard.KEY_UP:
//      setTimeDelta(5 * 60);
//      return true;
//
//    case Keyboard.KEY_DOWN:
//      setTimeDelta(-5 * 60);
//      return true;
//
//    case Keyboard.KEY_SPACE:
//      player.pause();
//      return true;
//
//    }
//    return false;
//  }

//  @Override
//  protected boolean onControllerEvent() {
//    if (super.onControllerEvent()) {
//      return true;
//    }
//
//    Controller c = Controllers.getEventSource();
//    System.out.println(c.getName());
//    int controlIndex = Controllers.getEventControlIndex();
//    if (Controllers.isEventButton()) {
//      boolean buttonState = Controllers.getEventButtonState();
//      System.out.println(c.getButtonName(controlIndex));
//      System.out.println(String.format("%d %d", controlIndex, buttonState ? 1 : 0));
//    }
//    if (Controllers.isEventAxis()) {
//      System.out.println(c.getAxisName(controlIndex));
//      System.out.println(String.format("%d %f", controlIndex, c.getAxisValue(controlIndex)));
//    }
//    if (Controllers.isEventPovX()) {
//      System.out.println(String.format("PovX %d %f", controlIndex, c.getPovX()));
//    }
//
//    if (Controllers.isEventPovY()) {
//      System.out.println(String.format("PovY %d %f", controlIndex, c.getPovY()));
//    }
//
//    return false;
//  }

  protected void resetCamera() {
    MatrixStack.MODELVIEW.lookat(
        new Vector3f(0, OvrLibrary.OVR_DEFAULT_EYE_HEIGHT, 0.8f), 
        new Vector3f(0, OvrLibrary.OVR_DEFAULT_EYE_HEIGHT, 0), 
        new Vector3f(0, 1, 0));
    hmd.recenterPose();
  }

  @Override
  protected void update() {
    super.update();
    Runnable task;
    while (null != (task = taskQueue.poll())) {
      task.run();
    }

    swingContainer.updateTexture(swingTexture);
    videoTransport.updateTexture(videoTexture);
  }

  @Override
  protected void onDestroy() {
    player.stop();
    player.release();
  }

  @Override
  protected void renderScene() {
    glClear(GL_DEPTH_BUFFER_BIT);
    glEnable(GL_TEXTURE_2D);
    glDisable(GL_BLEND);
    root.render();

  }

}
