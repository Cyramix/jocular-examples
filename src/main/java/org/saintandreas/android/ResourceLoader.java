package org.saintandreas.android;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;

import org.saintandreas.android.ui.Menu;
import org.saintandreas.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

public class ResourceLoader {
  public enum Type {
    DRAWABLE,
    STRING,
    MENU,
    ID,;

    public static Type parseType(String type) {
      switch (type) {
      case "drawable": return DRAWABLE;
      case "string": return STRING;
      case "menu": return MENU;
      case "id": return ID;
      }
      throw new IllegalStateException();
    }
    
    public static String typePath(Type type) {
      switch (type) {
      case DRAWABLE: return "drawable";
      case STRING: return "string";
      case MENU: return "menu";
      case ID: return "id";
      }
      throw new IllegalStateException();
    }
  };

  private class ResourceData {
    final int id;
    final Type type;
    final String name;
    String value; 

    ResourceData(int id, Type type, String name) {
      this.id = id;
      this.type = type;
      this.name = name;
    }

  }

  private final Map<Integer, ResourceData> resourceMap;
//  private final Map<String, String> stringMap;

  private ResourceLoader() {
    try {
      Document doc = XmlUtil.parseXmlResource("android-resources.xml");
      ImmutableMap.Builder<Integer, ResourceData> b = ImmutableMap.builder();
      for (Node n : XmlUtil.toNodeArray(doc.getDocumentElement().getChildNodes())) {
        if (Node.ELEMENT_NODE == n.getNodeType()) {
          ResourceData r = parseNode(n);
          b.put(r.id, r);
        }
      }
      resourceMap = b.build();
//      doc = XmlUtil.parseXmlResource("android/values/strings.xml");
//      for (Node n : XmlUtil.toNodeArray(doc.getDocumentElement().getChildNodes())) {
//        Map<String, String> attrs = XmlUtil.getAttributeMap(n);
//        attrs.get("name");
//      }
//      stringMap = ImmutableMap.of();
    } catch (SAXException | IOException | ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }
  
  private ResourceData parseNode(Node n) {
    Map<String, String> attrs = XmlUtil.getAttributeMap(n);
    int id = Integer.parseInt(attrs.get("id").substring(2), 16);
    Type type = Type.parseType(attrs.get("type"));
    String name = attrs.get("name");
    return new ResourceData(id, type, name);  
  }

  private static final ResourceLoader INSTANCE = new ResourceLoader();

  public static ResourceLoader get() {
    return INSTANCE;
  }

  private ResourceData getResourceData(int id) {
    ResourceData r = resourceMap.get(id);
    if (null == r) {
      throw new IllegalStateException("Unknown resource");
    }
    return r;
  }

  public BufferedImage loadDrawable(int id) {
    ResourceData r = getResourceData(id);
    if (Type.DRAWABLE != r.type) {
      throw new IllegalStateException("Not a drawable resource");
    }
    try (InputStream is = getResourceInputStream(r)) {
      return ImageIO.read(is);
    } catch (IOException e) {
      throw new IllegalStateException();
    }
  }
  
  public Menu loadMenu(int id) {
//    ResourceData r = getResourceData(id);
//    Document d = getResourceDocument(r);
    
    return null;
  }
  
  public String loadString(int id) {
    ResourceData r = getResourceData(id);
    if (Type.STRING != r.type) {
      throw new IllegalStateException("Not a string resource");
    }
    return r.value;
  }

  private static String getPath(ResourceData r) {
    return "android/" + Type.typePath(r.type) + "/" + r.name + ".png";
  }
  
  private static URL getResourceUrl(ResourceData r) {
    return Resources.getResource(getPath(r));
  }
  
  public static byte[] getResourceBytes(ResourceData r) throws IOException {
    return Resources.toByteArray(getResourceUrl(r));
  }

//  private static String getResourceString(ResourceData r) throws IOException {
//    return new String(getResourceBytes(r), Charsets.UTF_8);
//  }

//  private static Document getResourceDocument(ResourceData r) {
//    try {
//      return XmlUtil.parseXmlStream(getResourceInputStream(r));
//    } catch (SAXException | IOException | ParserConfigurationException e) {
//      throw new IllegalStateException("Unable to load XML document", e);
//    }
//  }
  
  private static InputStream getResourceInputStream(ResourceData r) throws IOException {
    return new ByteArrayInputStream(getResourceBytes(r));
  }
}
