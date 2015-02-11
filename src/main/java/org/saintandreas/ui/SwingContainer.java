package org.saintandreas.ui;

import static org.lwjgl.opengl.GL11.*;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.ByteBuffer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultFocusManager;
import javax.swing.JFrame;
import javax.swing.RepaintManager;

import org.lwjgl.opengl.GL11;
import org.saintandreas.gl.textures.Texture;

public class SwingContainer implements Runnable {

  private volatile boolean dirty = false;
  private ByteBuffer imageBuffer;
  private int width, height;
  private JFrame frame = new JFrame();
  private EventQueue eventQueue; 
//  private MyEventQueue eventQueue = new MyEventQueue();
  private MyFocusManager focusManager = new MyFocusManager();

  class MyFocusManager extends DefaultFocusManager {
    @Override
    protected void setGlobalFocusOwner(Component focusOwner) {
      super.setGlobalFocusOwner(focusOwner);
    }
    
    @Override
    protected void setGlobalFocusedWindow(Window window) {
      super.setGlobalFocusedWindow(window);
    }
    
    protected void hack() {
      setGlobalFocusOwner(frame.getMostRecentFocusOwner());
      setGlobalFocusedWindow(frame);
    }
  }
  
  class MyEventQueue extends EventQueue {

    @Override
    protected void dispatchEvent( final AWTEvent event ) {
      if (event instanceof KeyEvent) {
        System.out.println(((KeyEvent) event).getComponent());
        System.out.println(((KeyEvent) event).paramString());
      }
      super.dispatchEvent( event );
    }
  }

  // Now the code to post the events becomes:
  
  private class GlRepaintManager extends RepaintManager {
    GlRepaintManager() {
      this.setDoubleBufferingEnabled(false);
    }
    
    @Override
    public void paintDirtyRegions() {
      synchronized (SwingContainer.this) {
        super.paintDirtyRegions();
        dirty = true;
      }
    }
  }

  @SuppressWarnings("restriction")
  public SwingContainer() {
    System.setProperty("awt.nativeDoubleBuffering", "true");
    RepaintManager.setCurrentManager(new GlRepaintManager());
    frame.setUndecorated(true);
    eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
//    Toolkit.getDefaultToolkit().getSystemEventQueue().push( eventQueue );
    Logger.getLogger("java.awt.event.Component").setLevel(Level.FINEST);
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager);
    focusManager.addPropertyChangeListener(
        new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                String prop = e.getPropertyName();
                if (("focusOwner".equals(prop))) {
                  Component newFocus = (Component) e.getNewValue();
                  if (null != newFocus && newFocus.toString().contains("Metal")) {
                    System.out.println(newFocus.toString());
                  }
                }
            }
        });
    sun.util.logging.PlatformLogger.getLogger("java.awt.event.*").setLevel(sun.util.logging.PlatformLogger.Level.FINEST);
    sun.util.logging.PlatformLogger.getLogger("java.awt.event.Component").setLevel(sun.util.logging.PlatformLogger.Level.FINEST);
    sun.util.logging.PlatformLogger.getLogger("java.awt.event.Container").setLevel(sun.util.logging.PlatformLogger.Level.FINEST);
//    sun.util.logging.PlatformLogger.getLogger("java.awt.focus.Component").setLevel(sun.util.logging.PlatformLogger.Level.FINEST);
    Logger.getGlobal().setLevel(Level.FINEST);
    Handler handler = new ConsoleHandler();
    handler.setLevel(Level.FINEST);
    Logger.getGlobal().addHandler(handler);
    for (Handler h : Logger.getGlobal().getHandlers()) {
      h.setLevel(Level.FINEST);
    }
    Logger.getLogger("java.awt.focus.Component").setLevel(Level.FINEST);
    
    

//    MouseEvent pressEvent = new MouseEvent(target, MouseEvent.MOUSE_PRESSED, ...)
//    MouseEvent releaseEvent = new MouseEvent(target, MouseEvent.MOUSE_RELEASED, ...)
//    MouseEvent clickEvent = new MouseEvent(target, MouseEvent.MOUSE_CLICKED, ...)
//
//    eventQueue.scheduleEvent( clickEvent, releaseEvent );
//    eventQueue.scheduleEvent( releaseEvent, pressEvent );
//    eventQueue.postEvent( pressEvent );

//    eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
//    eventQueue.push(new MyEventQueue());
//    try {
//      //robot = new Robot();
//    } catch (AWTException e) {
//      throw new IllegalStateException(e);
//    }
    new Thread(this).start();
  }


  public void updateTexture(Texture texture) {
    if (null != imageBuffer) {
      synchronized (this) {
        if (null != imageBuffer) {
          texture.withBound(()->{
            glTexImage2D(GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width,
                height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                imageBuffer);
          });
          imageBuffer = null;
        }
      }
    }
  }

  @Override
  public void run() {
    while (true) {
      if (dirty) {
        BufferedImage image;
        synchronized (this) {
          image = ComponentImageCapture.getScreenShot(frame);
          width = image.getWidth();
          height = image.getHeight();
          dirty = false;

          AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
          tx.translate(0, -image.getHeight(null));
          AffineTransformOp op = new AffineTransformOp(tx,
              AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
          image = op.filter(image, null);
          imageBuffer = Texture.convertImageData(image);
        }
      } else {
        try { Thread.sleep(100); } catch (InterruptedException e) { }
      }
    }
  }


  public JFrame getFrame() {
    return frame;
  }

  public static int getKeycode() {
    return 0;
//    switch (Keyboard.getEventKey()) {
//      case Keyboard.KEY_ESCAPE: return KeyEvent.VK_ESCAPE;
//      case Keyboard.KEY_1:
//
//      case Keyboard.KEY_2: return KeyEvent.VK_2;
//
//      case Keyboard.KEY_3: return KeyEvent.VK_3;
//
//      case Keyboard.KEY_4: return KeyEvent.VK_4;
//
//      case Keyboard.KEY_5: return KeyEvent.VK_5;
//
//      case Keyboard.KEY_6: return KeyEvent.VK_6;
//
//      case Keyboard.KEY_7: return KeyEvent.VK_7;
//
//      case Keyboard.KEY_8: return KeyEvent.VK_8;
//
//      case Keyboard.KEY_9: return KeyEvent.VK_9;
//
//      case Keyboard.KEY_0: return KeyEvent.VK_0;
//
//      case Keyboard.KEY_MINUS: return KeyEvent.VK_MINUS;
//
//      case Keyboard.KEY_EQUALS: return KeyEvent.VK_EQUALS;
//
//      case Keyboard.KEY_TAB: return KeyEvent.VK_TAB;
//
//      case Keyboard.KEY_Q: return KeyEvent.VK_Q;
//
//      case Keyboard.KEY_W: return KeyEvent.VK_W;
//
//      case Keyboard.KEY_E: return KeyEvent.VK_E;
//
//      case Keyboard.KEY_R: return KeyEvent.VK_R;
//
//      case Keyboard.KEY_T: return KeyEvent.VK_T;
//
//      case Keyboard.KEY_Y: return KeyEvent.VK_Y;
//
//      case Keyboard.KEY_U: return KeyEvent.VK_U;
//
//      case Keyboard.KEY_I: return KeyEvent.VK_I;
//
//      case Keyboard.KEY_O: return KeyEvent.VK_O;
//
//      case Keyboard.KEY_P: return KeyEvent.VK_P;
//
//      case Keyboard.KEY_A: return KeyEvent.VK_A;
//
//      case Keyboard.KEY_S: return KeyEvent.VK_S;
//
//      case Keyboard.KEY_D: return KeyEvent.VK_D;
//
//      case Keyboard.KEY_F: return KeyEvent.VK_F;
//
//      case Keyboard.KEY_G: return KeyEvent.VK_G;
//
//      case Keyboard.KEY_H: return KeyEvent.VK_H;
//
//      case Keyboard.KEY_J: return KeyEvent.VK_J;
//
//      case Keyboard.KEY_K: return KeyEvent.VK_K;
//
//      case Keyboard.KEY_L: return KeyEvent.VK_L;
//
//      case Keyboard.KEY_SEMICOLON: return KeyEvent.VK_SEMICOLON;
//
//      case Keyboard.KEY_Z: return KeyEvent.VK_Z;
//
//      case Keyboard.KEY_X: return KeyEvent.VK_X;
//
//      case Keyboard.KEY_C: return KeyEvent.VK_C;
//
//      case Keyboard.KEY_V: return KeyEvent.VK_V;
//
//      case Keyboard.KEY_B: return KeyEvent.VK_B;
//
//      case Keyboard.KEY_N: return KeyEvent.VK_N;
//
//      case Keyboard.KEY_M: return KeyEvent.VK_M;
//
//      case Keyboard.KEY_COMMA: return KeyEvent.VK_COMMA;
//
//      case Keyboard.KEY_PERIOD: return KeyEvent.VK_PERIOD;
//
//      case Keyboard.KEY_SLASH: return KeyEvent.VK_SLASH;
//
//      case Keyboard.KEY_MULTIPLY: return KeyEvent.VK_MULTIPLY;
//
//      case Keyboard.KEY_SPACE: return KeyEvent.VK_SPACE;
//
//      case Keyboard.KEY_F1: return KeyEvent.VK_F1;
//
//      case Keyboard.KEY_F2: return KeyEvent.VK_F2;
//
//      case Keyboard.KEY_F3: return KeyEvent.VK_F3;
//
//      case Keyboard.KEY_F4: return KeyEvent.VK_F4;
//
//      case Keyboard.KEY_F5: return KeyEvent.VK_F5;
//
//      case Keyboard.KEY_F6: return KeyEvent.VK_F6;
//
//      case Keyboard.KEY_F7: return KeyEvent.VK_F7;
//
//      case Keyboard.KEY_F8: return KeyEvent.VK_F8;
//
//      case Keyboard.KEY_F9: return KeyEvent.VK_F9;
//
//      case Keyboard.KEY_F10: return KeyEvent.VK_F10;
//
//      case Keyboard.KEY_NUMPAD7: return KeyEvent.VK_NUMPAD7;
//
//      case Keyboard.KEY_NUMPAD8: return KeyEvent.VK_NUMPAD8;
//
//      case Keyboard.KEY_NUMPAD9: return KeyEvent.VK_NUMPAD9;
//
//      case Keyboard.KEY_SUBTRACT: return KeyEvent.VK_SUBTRACT;
//
//      case Keyboard.KEY_NUMPAD4: return KeyEvent.VK_NUMPAD4;
//
//      case Keyboard.KEY_NUMPAD5: return KeyEvent.VK_NUMPAD5;
//
//      case Keyboard.KEY_NUMPAD6: return KeyEvent.VK_NUMPAD6;
//
//      case Keyboard.KEY_ADD: return KeyEvent.VK_ADD;
//
//      case Keyboard.KEY_NUMPAD1: return KeyEvent.VK_NUMPAD1;
//
//      case Keyboard.KEY_NUMPAD2: return KeyEvent.VK_NUMPAD2;
//
//      case Keyboard.KEY_NUMPAD3: return KeyEvent.VK_NUMPAD3;
//
//      case Keyboard.KEY_NUMPAD0: return KeyEvent.VK_NUMPAD0;
//
//      case Keyboard.KEY_DECIMAL: return KeyEvent.VK_DECIMAL;
//
//      case Keyboard.KEY_F11: return KeyEvent.VK_F11;
//
//      case Keyboard.KEY_F12: return KeyEvent.VK_F12;
//
//      case Keyboard.KEY_F13: return KeyEvent.VK_F13;
//
//      case Keyboard.KEY_F14: return KeyEvent.VK_F14;
//
//      case Keyboard.KEY_F15: return KeyEvent.VK_F15;
//
//      case Keyboard.KEY_F16: return KeyEvent.VK_F16;
//
//      case Keyboard.KEY_F17: return KeyEvent.VK_F17;
//
//      case Keyboard.KEY_F18: return KeyEvent.VK_F18;
//
//      case Keyboard.KEY_KANA: return KeyEvent.VK_KANA;
//
//      case Keyboard.KEY_F19: return KeyEvent.VK_F19;
//
//      case Keyboard.KEY_CONVERT: return KeyEvent.VK_CONVERT;
//
//
//      case Keyboard.KEY_CIRCUMFLEX: return KeyEvent.VK_CIRCUMFLEX;
//
//      case Keyboard.KEY_AT: return KeyEvent.VK_AT;
//
//      case Keyboard.KEY_COLON: return KeyEvent.VK_COLON;
//
//
//      case Keyboard.KEY_KANJI: return KeyEvent.VK_KANJI;
//
//      case Keyboard.KEY_STOP: return KeyEvent.VK_STOP;
//
//
//      case Keyboard.KEY_DIVIDE: return KeyEvent.VK_DIVIDE;
//
//
//      case Keyboard.KEY_PAUSE: return KeyEvent.VK_PAUSE;
//
//      case Keyboard.KEY_HOME: return KeyEvent.VK_HOME;
//
//      case Keyboard.KEY_UP: return KeyEvent.VK_UP;
//
//      case Keyboard.KEY_LEFT: return KeyEvent.VK_LEFT;
//
//      case Keyboard.KEY_RIGHT: return KeyEvent.VK_RIGHT;
//
//      case Keyboard.KEY_END: return KeyEvent.VK_END;
//
//      case Keyboard.KEY_DOWN: return KeyEvent.VK_DOWN;
//
//
//      case Keyboard.KEY_INSERT: return KeyEvent.VK_INSERT;
//
//      case Keyboard.KEY_DELETE: return KeyEvent.VK_DELETE;
//
//      case Keyboard.KEY_CLEAR: return KeyEvent.VK_CLEAR;
//
//      default: return KeyEvent.VK_UNDEFINED;

//      case Keyboard.KEY_PRIOR: return KeyEvent.VK_PRIOR;
//
//      case Keyboard.KEY_NEXT: return KeyEvent.VK_NEXT;
//      case Keyboard.KEY_NOCONVERT: return KeyEvent.VK_NOCONVERT;
//
//      case Keyboard.KEY_YEN: return KeyEvent.VK_YEN;
//
//      case Keyboard.KEY_NUMPADEQUALS: return KeyEvent.VK_NUMPADEQUALS;
//      case Keyboard.KEY_UNDERLINE: return KeyEvent.VK_UNDERLINE;
//      case Keyboard.KEY_AX: return KeyEvent.VK_AX;
//
//      case Keyboard.KEY_UNLABELED: return KeyEvent.VK_UNLABELED;
//
//      case Keyboard.KEY_NUMPADENTER: return KeyEvent.VK_NUMPADENTER;
//
//      case Keyboard.KEY_RCONTROL: return KeyEvent.VK_RCONTROL;
//
//      case Keyboard.KEY_SECTION: return KeyEvent.VK_SECTION;
//
//      case Keyboard.KEY_NUMPADCOMMA: return KeyEvent.VK_NUMPADCOMMA;
//      case Keyboard.KEY_SYSRQ: return KeyEvent.VK_SYSRQ;
//
//      case Keyboard.KEY_RMENU: return KeyEvent.VK_RMENU;
//
//      case Keyboard.KEY_FUNCTION: return KeyEvent.VK_FUNCTION;
//      case Keyboard.KEY_LMETA: return KeyEvent.VK_LMETA;
//
//      case Keyboard.KEY_NUMLOCK: return KeyEvent.VK_NUMLOCK;
//
//      case Keyboard.KEY_SCROLL: return KeyEvent.VK_SCROLL;
//
//      case Keyboard.KEY_BACK: return KeyEvent.VK_BACK;
//      case Keyboard.KEY_LBRACKET: return KeyEvent.VK_LBRACKET;
//      case Keyboard.KEY_RBRACKET: return KeyEvent.VK_RBRACKET;
//
//      case Keyboard.KEY_RETURN: return KeyEvent.VK_RETURN;
//
//      case Keyboard.KEY_LCONTROL: return KeyEvent.VK_LCONTROL;
//      case Keyboard.KEY_APOSTROPHE: return KeyEvent.VK_APOSTROPHE;
//
//      case Keyboard.KEY_GRAVE: return KeyEvent.VK_GRAVE;
//
//      case Keyboard.KEY_LSHIFT: return KeyEvent.VK_LSHIFT;
//
//      case Keyboard.KEY_BACKSLASH: return KeyEvent.VK_BACKSLASH;
//
//      case Keyboard.KEY_RSHIFT: return KeyEvent.VK_RSHIFT;
//      case Keyboard.KEY_LMENU: return KeyEvent.VK_LMENU;
//      case Keyboard.KEY_CAPITAL: return KeyEvent.VK_CAPITAL;
//    }
  }

  static char LAST_CHARACTER;
//  public boolean onKeyboardEvent() {
//    focusManager.hack();
////    if (Keyboard.KEY_TAB == Keyboard.getEventKey()) {
////      if (!Keyboard.getEventKeyState()) {
////        System.out.println(frame.getMostRecentFocusOwner());
////        focusManager.focusNextComponent(frame.getMostRecentFocusOwner());
////        System.out.println(frame.getFocusOwner().toString());
////      }
////      return true;
////    }
//    Component focus = frame; //frame.getMostRecentFocusOwner();
//    if (null == focus) {
//      focus = frame;
//    }
//    char c = Keyboard.getEventCharacter();
//    if (0 == c) {
//      c = KeyEvent.CHAR_UNDEFINED;
//    }
//    int id = Keyboard.getEventKeyState() ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
//    if (KeyEvent.KEY_PRESSED == id) {
//      LAST_CHARACTER = c;
//    }
//    if (KeyEvent.KEY_RELEASED == id && KeyEvent.CHAR_UNDEFINED != LAST_CHARACTER) {
//      c = LAST_CHARACTER;
//    }
//    int vk = getKeycode();
//    KeyEvent ke = new KeyEvent(focus, id, System.currentTimeMillis(), 0, vk, c, KeyEvent.KEY_LOCATION_STANDARD);
//    eventQueue.postEvent(ke);
//    if (KeyEvent.KEY_PRESSED == id && KeyEvent.CHAR_UNDEFINED != c) {
//      ke = new KeyEvent(focus, KeyEvent.KEY_TYPED, System.currentTimeMillis() + 1, 0, KeyEvent.VK_UNDEFINED, c, KeyEvent.KEY_LOCATION_UNKNOWN);
//      eventQueue.postEvent(ke);
//    }
//    return true;
//  }
}
