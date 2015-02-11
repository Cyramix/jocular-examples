package org.saintandreas.gl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.saintandreas.ExampleResource.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.saintandreas.ExampleResource;
import org.saintandreas.gl.shaders.Program;
import org.saintandreas.gl.shaders.StandardAttribute;
import org.saintandreas.gl.textures.Texture;
import org.saintandreas.math.Vector4f;
import org.saintandreas.resources.BasicResource;
import org.saintandreas.resources.Resource;
import org.saintandreas.transforms.MatrixStack;

import com.oculusvr.capi.Hmd;

public class SceneHelpers {
  private static IndexedGeometry cubeGeometry;
  private static IndexedGeometry floorGeometry;
  private static Texture floorTexture;
  private static Texture skyboxTexture;



  public static void renderFloor() {
    if (null == floorGeometry) {
      float size = 100.0f;
      List<Vector4f> vertices = new ArrayList<>();
      vertices.add(new Vector4f(size, 0, size, 1));
      vertices.add(new Vector4f(size, size, 0, 0));
      vertices.add(new Vector4f(size, 0, -size, 1));
      vertices.add(new Vector4f(size, -size, 0, 0));
      vertices.add(new Vector4f(-size, 0, size, 1));
      vertices.add(new Vector4f(-size, size, 0, 0));
      vertices.add(new Vector4f(-size, 0, -size, 1));
      vertices.add(new Vector4f(-size, -size, 0, 0));
      List<Short> indices = new ArrayList<>();
      indices.add((short) 0); // LL
      indices.add((short) 1); // LR
      indices.add((short) 2); // UL
      indices.add((short) 3); // UR
      IndexedGeometry.Builder builder = new IndexedGeometry.Builder(indices, vertices);
      builder.withDrawType(GL_TRIANGLE_STRIP).withAttribute(StandardAttribute.POSITION).withAttribute(StandardAttribute.TEX);
      floorGeometry = builder.build();
    }
    if (null == floorTexture) {
      try {
        floorTexture = Texture.loadImage(IMAGES_FLOOR_PNG);
      } catch (IOException ex) {
        throw new IllegalStateException("Unable to load floor texture", ex);
      }
      floorTexture.bind();
      floorTexture.parameter(GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      floorTexture.parameter(GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
      glGenerateMipmap(GL_TEXTURE_2D);
      floorTexture.unbind();
    }
    Program floorProgram = OpenGL.getProgram(
        SHADERS_TEXTURED_VS, 
        new BasicResource("shaders/Floor.fs"));
    floorProgram.use();
    floorProgram.setUniform("Time", (float)Hmd.getTimeInSeconds());
    OpenGL.bindAll(floorProgram);
    floorTexture.bind();
    floorGeometry.bind();
    floorGeometry.draw();
    Texture.unbind(GL_TEXTURE_2D);
    Program.clear();
    floorGeometry.unbind();
  }
  
  public static void renderColorCube() {
    if (null == cubeGeometry) {
      cubeGeometry = OpenGL.makeColorCube();
    }
    Program cubeProgram = OpenGL.getProgram(
        ExampleResource.SHADERS_COLORED_VS, 
        ExampleResource.SHADERS_COLORED_FS);
    glPrimitiveRestartIndex(Short.MAX_VALUE);
    glEnable(GL_PRIMITIVE_RESTART);

    cubeProgram.use();
    OpenGL.bindAll(cubeProgram);
    cubeGeometry.bindVertexArray();
    cubeGeometry.draw();
    Program.clear();
    cubeGeometry.unbind();
  }

  // @formatter:off
  private static final Resource SKYBOX[] = {
    IMAGES_SKY_CITY_XNEG_PNG,
    IMAGES_SKY_CITY_XPOS_PNG,
    IMAGES_SKY_CITY_YNEG_PNG,
    IMAGES_SKY_CITY_YPOS_PNG,
    IMAGES_SKY_CITY_ZNEG_PNG,
    IMAGES_SKY_CITY_ZPOS_PNG
  };
  // @formatter:on

  public static void renderSkybox() {
    if (null == cubeGeometry) {
      cubeGeometry = OpenGL.makeColorCube();
    }

    Program skyboxProgram = OpenGL.getProgram(
        SHADERS_CUBEMAP_VS, 
        SHADERS_CUBEMAP_FS);

    if (null == skyboxTexture) {
      skyboxTexture = OpenGL.getCubemapTexture(SKYBOX);
    }

    MatrixStack mv = MatrixStack.MODELVIEW;
    cubeGeometry.bindVertexArray();
    mv.push();
    {
      skyboxProgram.use();
      OpenGL.bindAll(skyboxProgram);
      glCullFace(GL_FRONT);
      skyboxTexture.bind();
      glDisable(GL_DEPTH_TEST);
      cubeGeometry.draw();
      glEnable(GL_DEPTH_TEST);
      skyboxTexture.unbind();
      glCullFace(GL_BACK);
    }
    mv.pop();
  }
  
  public static void renderProceduralSkybox() {
    if (null == cubeGeometry) {
      cubeGeometry = OpenGL.makeColorCube();
    }
    Program proceduralSkyboxProgram = OpenGL.getProgram(
        SHADERS_CUBEMAP_VS, 
        new BasicResource("shaders/Floor.fs"));

    MatrixStack mv = MatrixStack.MODELVIEW;
    cubeGeometry.bindVertexArray();
    mv.push();
    {
      proceduralSkyboxProgram.use();
      proceduralSkyboxProgram.setUniform("Time", (float)Hmd.getTimeInSeconds());
      OpenGL.bindAll(proceduralSkyboxProgram);
      glCullFace(GL_FRONT);
      glDisable(GL_DEPTH_TEST);
      cubeGeometry.draw();
      glEnable(GL_DEPTH_TEST);
      glCullFace(GL_BACK);
    }
    mv.pop();
 }

}